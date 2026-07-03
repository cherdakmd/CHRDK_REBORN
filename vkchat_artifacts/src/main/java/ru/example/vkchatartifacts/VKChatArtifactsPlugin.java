package ru.example.vkchatartifacts;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatartifacts.commands.ArtifactCommand;
import ru.example.vkchatartifacts.listeners.ArtifactListener;
import ru.example.vkchatartifacts.listeners.ConsumablesListener;
import ru.example.vkchatartifacts.bosses.BossManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VKChatArtifactsPlugin extends JavaPlugin {
    private static VKChatArtifactsPlugin instance;
    private BossManager bossManager;
    private final Map<UUID, Long> airdropCooldowns = new ConcurrentHashMap<>();
    private final Map<Location, Long> activeChests = new ConcurrentHashMap<>();
    private final Random rng = new Random();
    private final java.util.concurrent.atomic.AtomicLong totalArtifactsGenerated = new java.util.concurrent.atomic.AtomicLong(0);
    private final Map<UUID, Integer> playerArtifactCounts = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ru.example.vkchat.config.ConfigMigrationUtil.migrate(this, "config.yml");

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Аддон выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bossManager = new BossManager(this);

        getServer().getPluginManager().registerEvents(new ArtifactListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsumablesListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.example.vkchatartifacts.listeners.ArtifactShopListener(this), this);
        getServer().getPluginManager().registerEvents(bossManager, this);
        ArtifactCommand artifactCmd = new ArtifactCommand(this);
        getCommand("artifacts").setExecutor(artifactCmd);
        getCommand("artifacts").setTabCompleter(artifactCmd);

        if (getConfig().getBoolean("bosses.enabled", true)) {
            long interval = getConfig().getLong("bosses.spawn-interval", 43200) * 20L;
            bossManager.runTaskTimer(this, 1200L, interval);
        }

        if (getConfig().getBoolean("alchemist-airdrop.enabled", true)) {
            long airdropInterval = getConfig().getLong("alchemist-airdrop.interval-seconds", 14400) * 20L;
            getServer().getScheduler().runTaskTimer(this, this::runAlchemistAirdrop, 6000L, airdropInterval);
            // Очистка старых сундуков каждые 5 минут
            getServer().getScheduler().runTaskTimer(this, this::cleanupOldChests, 6000L, 6000L);
        }

        getLogger().info("VKChatArtifacts (Боссы, Артефакты, Свитки) успешно загружен!");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.clearBosses();
        }
    }

    public static VKChatArtifactsPlugin getInstance() {
        return instance;
    }
    
    public BossManager getBossManager() {
        return bossManager;
    }

    public long getTotalArtifactsGenerated() {
        return totalArtifactsGenerated.get();
    }

    public long incrementArtifactsGenerated() {
        return totalArtifactsGenerated.incrementAndGet();
    }

    public int getPlayerArtifactCount(UUID uuid) {
        return playerArtifactCounts.getOrDefault(uuid, 0);
    }

    public void setPlayerArtifactCount(UUID uuid, int count) {
        playerArtifactCounts.put(uuid, count);
    }

    public void incrementPlayerArtifactCount(UUID uuid) {
        playerArtifactCounts.merge(uuid, 1, Integer::sum);
    }

    // ==================== АЛХИМИЧЕСКИЙ ТАЙНИК ====================

    private void runAlchemistAirdrop() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;

        // Анти-абус: фильтруем игроков с кулдауном
        long cooldownMs = getConfig().getLong("alchemist-airdrop.player-cooldown-seconds", 86400) * 1000L;
        long now = System.currentTimeMillis();
        List<Player> candidates = new ArrayList<>();
        for (Player p : online) {
            Long lastDrop = airdropCooldowns.get(p.getUniqueId());
            if (lastDrop == null || now - lastDrop >= cooldownMs) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return;

        Player target = candidates.get(rng.nextInt(candidates.size()));
        airdropCooldowns.put(target.getUniqueId(), now);

        // Выбираем тир сундука
        String tier = rollTier();
        List<String> items = getConfig().getStringList("alchemist-airdrop.tiers." + tier + ".items");
        if (items.isEmpty()) items = getConfig().getStringList("alchemist-airdrop.tier.items");

        // Ищем безопасное место рядом с игроком
        Location safeLoc = findSafeLocation(target.getLocation());
        if (safeLoc == null) {
            safeLoc = target.getLocation().clone();
        }

        // Спавним сундук
        Block block = safeLoc.getBlock();
        block.setType(Material.CHEST);
        String chestName = getConfig().getString("alchemist-airdrop.tiers." + tier + ".name",
                getConfig().getString("alchemist-airdrop.tier.name", "&a🧪 Алхимический Тайник"));

        if (block.getState() instanceof Container) {
            Container chest = (Container) block.getState();
            // Устанавливаем имя сундука через reflection (1.16.5)
            try {
                Object nmsWorld = safeLoc.getWorld().getClass().getMethod("getHandle").invoke(safeLoc.getWorld());
                Object tileEntity = nmsWorld.getClass().getMethod("getTileEntity", org.bukkit.block.Block.class).invoke(nmsWorld, block);
                if (tileEntity != null) {
                    Object customName = Class.forName("net.minecraft.network.chat.IChatBaseComponent")
                            .getMethod("a", String.class).invoke(null, chestName);
                    tileEntity.getClass().getMethod("setCustomName", customName.getClass()).invoke(tileEntity, customName);
                }
            } catch (Exception ignored) {}

            // Заполняем предметами
            for (String entry : items) {
                String[] parts = entry.split(";");
                if (parts.length < 4) continue;
                try {
                    Material mat = Material.valueOf(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    int max = Integer.parseInt(parts[2]);
                    double chance = Double.parseDouble(parts[3]);
                    if (rng.nextDouble() * 100 < chance) {
                        int amount = min + rng.nextInt(Math.max(1, max - min + 1));
                        chest.getInventory().addItem(new ItemStack(mat, amount));
                    }
                } catch (Exception ignored) {}
            }

            // Добавляем случайный артефакт если есть шанс
            double artifactChance = getConfig().getDouble("alchemist-airdrop.artifact-chance", 15);
            if (rng.nextDouble() * 100 < artifactChance) {
                try {
                    ItemStack artifact = ru.example.vkchatartifacts.items.ArtifactFactory.generateArtifact(this, false);
                    if (artifact != null) chest.getInventory().addItem(artifact);
                } catch (Exception ignored) {}
            }

            // Запоминаем сундук для авто-очистки
            long despawnMs = getConfig().getLong("alchemist-airdrop.despawn-minutes", 30) * 60 * 1000L;
            activeChests.put(safeLoc.clone(), now + despawnMs);
        }

        // Координаты для игрока
        int x = safeLoc.getBlockX();
        int y = safeLoc.getBlockY();
        int z = safeLoc.getBlockZ();
        String worldName = safeLoc.getWorld().getName();

        // Уведомление игрока
        String msg = getConfig().getString("alchemist-airdrop.messages." + tier,
                getConfig().getString("alchemist-airdrop.message", "&d&l✦ Алхимик дарит тебе таинственный сундук!"));
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        target.sendMessage(ChatColor.GOLD + "📍 Координаты: " + ChatColor.WHITE + worldName + " " + x + ", " + y + ", " + z);
        target.sendMessage(ChatColor.GRAY + "Сундук исчезнет через " + getConfig().getInt("alchemist-airdrop.despawn-minutes", 30) + " мин.");

        // Показываем координаты как action bar
        String coordMsg = "§6✦ Сундук: §f" + x + ", " + y + ", " + z;
        target.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coordMsg));

        // Эффекты
        target.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, safeLoc.clone().add(0.5, 1.5, 0.5), 100, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().spawnParticle(Particle.PORTAL, safeLoc.clone().add(0.5, 1, 0.5), 60, 0.3, 0.5, 0.3, 0.1);
        target.playSound(safeLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        target.playSound(safeLoc, Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 0.8f);

        // VK уведомление
        if (getConfig().getBoolean("alchemist-airdrop.vk-announce", false)) {
            try {
                ru.example.vkchat.VKChatPlugin.getInstance().getApi().sendToMainChat(
                    "🧪 Алхимический Тайник получен игроком " + target.getName() + "!");
            } catch (Exception ignored) {}
        }

        getLogger().info("[Airdrop] " + tier + " chest spawned for " + target.getName() + " at " + worldName + " " + x + "," + y + "," + z);
    }

    /**
     * Выбирает тир сундука на основе шансов из конфига.
     */
    private String rollTier() {
        ConfigurationSection tiers = getConfig().getConfigurationSection("alchemist-airdrop.tiers");
        if (tiers == null) return "common";

        double roll = rng.nextDouble() * 100;
        double cumulative = 0;
        for (String key : tiers.getKeys(false)) {
            double chance = tiers.getDouble(key + ".chance", 100);
            cumulative += chance;
            if (roll < cumulative) return key;
        }
        return "common";
    }

    /**
     * Ищет безопасное место рядом с игроком (не лава, не воздух над пропастью).
     */
    private Location findSafeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;

        // Сначала пробуем под ногами игрока
        Location below = origin.clone().add(0, -1, 0);
        if (isSafeBlock(below)) return origin.clone();

        // Ищем в радиусе 5 блоков
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location check = origin.clone().add(dx, 0, dz);
                    if (isSafeBlock(check.clone().add(0, -1, 0)) && isSafeBlock(check)) {
                        return check;
                    }
                }
            }
        }
        return origin.clone(); // Фолбэк
    }

    private boolean isSafeBlock(Location loc) {
        Block block = loc.getBlock();
        Material type = block.getType();
        return type != Material.LAVA && type != Material.FIRE && type != Material.SOUL_FIRE
                && type != Material.CACTUS && type != Material.MAGMA_BLOCK
                && type != Material.AIR || !loc.clone().add(0, -1, 0).getBlock().getType().isAir();
    }

    /**
     * Очищает старые сундуки которые не были открыты.
     */
    private void cleanupOldChests() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, Long>> it = activeChests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, Long> entry = it.next();
            if (now >= entry.getValue()) {
                Location loc = entry.getKey();
                if (loc.getBlock().getType() == Material.CHEST) {
                    loc.getBlock().setType(Material.AIR);
                    loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                }
                it.remove();
            }
        }
    }
}
