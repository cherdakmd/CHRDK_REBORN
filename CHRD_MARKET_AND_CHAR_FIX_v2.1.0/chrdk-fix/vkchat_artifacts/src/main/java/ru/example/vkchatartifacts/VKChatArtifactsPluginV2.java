package ru.example.vkchatartifacts;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.config.ConfigMigrationUtil;
import ru.example.vkchatartifacts.bosses.BossManager;
import ru.example.vkchatartifacts.commands.ArtifactCommand;
import ru.example.vkchatartifacts.items.ArtifactFactory;
import ru.example.vkchatartifacts.listeners.ArtifactListenerV2;
import ru.example.vkchatartifacts.listeners.ArtifactShopListener;
import ru.example.vkchatartifacts.listeners.ConsumablesListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  VKChatArtifactsPluginV2 — переключатель V1↔V2 для артефактов
 *  ─────────────────────────────────────────────────────────────────────
 *  Включение V2:
 *      -Dvkchat.artifacts.version=v2
 *  или env VKCHAT_ARTIFACTS_VERSION=v2
 *  По умолчанию: V1.
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class VKChatArtifactsPluginV2 extends JavaPlugin {
    private static VKChatArtifactsPluginV2 instance;
    private BossManager bossManager;
    private final Map<UUID, Long> airdropCooldowns = new ConcurrentHashMap<>();
    private final Map<Location, Long> activeChests = new ConcurrentHashMap<>();
    private final Random rng = new Random();
    private final java.util.concurrent.atomic.AtomicLong totalArtifactsGenerated = new java.util.concurrent.atomic.AtomicLong(0);
    private final Map<UUID, Integer> playerArtifactCounts = new ConcurrentHashMap<>();
    private final boolean useV2 = isUseV2();

    private static boolean isUseV2() {
        String v = System.getProperty("vkchat.artifacts.version");
        if (v == null) v = System.getenv("VKCHAT_ARTIFACTS_VERSION");
        return "v2".equalsIgnoreCase(v);
    }

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

        // [V2] Регистрируем V2 listener с фиксами конфликтов
        if (useV2) {
            getServer().getPluginManager().registerEvents(new ArtifactListenerV2(this), this);
        } else {
            getServer().getPluginManager().registerEvents(new ru.example.vkchatartifacts.listeners.ArtifactListener(this), this);
        }
        getServer().getPluginManager().registerEvents(new ConsumablesListener(this), this);
        getServer().getPluginManager().registerEvents(new ArtifactShopListener(this), this);
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
            getServer().getScheduler().runTaskTimer(this, this::cleanupOldChests, 6000L, 6000L);
        }

        getLogger().info("VKChatArtifacts V" + (useV2 ? "2" : "1") + " успешно загружен! " +
                "Реестр эффектов: " + (useV2 ? "✓ декларативный" : "✗ хардкод"));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (bossManager != null) bossManager.clearBosses();
    }

    public static VKChatArtifactsPluginV2 getInstance() { return instance; }
    public BossManager getBossManager() { return bossManager; }
    public long getTotalArtifactsGenerated() { return totalArtifactsGenerated.get(); }
    public long incrementArtifactsGenerated() { return totalArtifactsGenerated.incrementAndGet(); }
    public int getPlayerArtifactCount(UUID uuid) { return playerArtifactCounts.getOrDefault(uuid, 0); }
    public void setPlayerArtifactCount(UUID uuid, int count) { playerArtifactCounts.put(uuid, count); }
    public void incrementPlayerArtifactCount(UUID uuid) { playerArtifactCounts.merge(uuid, 1, Integer::sum); }

    private void runAlchemistAirdrop() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;
        long cooldownMs = getConfig().getLong("alchemist-airdrop.player-cooldown-seconds", 86400) * 1000L;
        long now = System.currentTimeMillis();
        List<Player> candidates = new ArrayList<>();
        for (Player p : online) {
            Long lastDrop = airdropCooldowns.get(p.getUniqueId());
            if (lastDrop == null || now - lastDrop >= cooldownMs) candidates.add(p);
        }
        if (candidates.isEmpty()) return;
        Player target = candidates.get(rng.nextInt(candidates.size()));
        airdropCooldowns.put(target.getUniqueId(), now);
        String tier = rollTier();
        List<String> items = getConfig().getStringList("alchemist-airdrop.tiers." + tier + ".items");
        if (items.isEmpty()) items = getConfig().getStringList("alchemist-airdrop.tier.items");
        Location safeLoc = findSafeLocation(target.getLocation());
        if (safeLoc == null) safeLoc = target.getLocation().clone();
        Block block = safeLoc.getBlock();
        block.setType(Material.CHEST);
        String chestName = getConfig().getString("alchemist-airdrop.tiers." + tier + ".name",
                getConfig().getString("alchemist-airdrop.tier.name", "&a🧪 Алхимический Тайник"));
        if (block.getState() instanceof Container) {
            Container chest = (Container) block.getState();
            try {
                Object nmsWorld = safeLoc.getWorld().getClass().getMethod("getHandle").invoke(safeLoc.getWorld());
                Object tileEntity = nmsWorld.getClass().getMethod("getTileEntity", org.bukkit.block.Block.class).invoke(nmsWorld, block);
                if (tileEntity != null) {
                    Object customName = Class.forName("net.minecraft.network.chat.IChatBaseComponent")
                            .getMethod("a", String.class).invoke(null, chestName);
                    tileEntity.getClass().getMethod("setCustomName", customName.getClass()).invoke(tileEntity, customName);
                }
            } catch (Exception ignored) {}
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
            double artifactChance = getConfig().getDouble("alchemist-airdrop.artifact-chance", 15);
            if (rng.nextDouble() * 100 < artifactChance) {
                try {
                    ItemStack artifact = ArtifactFactory.generateArtifact(this, false);
                    if (artifact != null) chest.getInventory().addItem(artifact);
                } catch (Exception ignored) {}
            }
            long despawnMs = getConfig().getLong("alchemist-airdrop.despawn-minutes", 30) * 60 * 1000L;
            activeChests.put(safeLoc.clone(), now + despawnMs);
        }
        int x = safeLoc.getBlockX();
        int y = safeLoc.getBlockY();
        int z = safeLoc.getBlockZ();
        String worldName = safeLoc.getWorld().getName();
        String msg = getConfig().getString("alchemist-airdrop.messages." + tier,
                getConfig().getString("alchemist-airdrop.message", "&d&l✦ Алхимик дарит тебе таинственный сундук!"));
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        target.sendMessage(ChatColor.GOLD + "📍 Координаты: " + ChatColor.WHITE + worldName + " " + x + ", " + y + ", " + z);
        target.sendMessage(ChatColor.GRAY + "Сундук исчезнет через " + getConfig().getInt("alchemist-airdrop.despawn-minutes", 30) + " мин.");
        String coordMsg = "§6✦ Сундук: §f" + x + ", " + y + ", " + z;
        target.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coordMsg));
        target.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, safeLoc.clone().add(0.5, 1.5, 0.5), 100, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().spawnParticle(Particle.PORTAL, safeLoc.clone().add(0.5, 1, 0.5), 60, 0.3, 0.5, 0.3, 0.1);
        target.playSound(safeLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        target.playSound(safeLoc, Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 0.8f);
        if (getConfig().getBoolean("alchemist-airdrop.vk-announce", false)) {
            try {
                ru.example.vkchat.VKChatPlugin.getInstance().getApi().sendToMainChat(
                        "🧪 Алхимический Тайник получен игроком " + target.getName() + "!");
            } catch (Exception ignored) {}
        }
        getLogger().info("[Airdrop] " + tier + " chest spawned for " + target.getName() + " at " + worldName + " " + x + "," + y + "," + z);
    }

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

    private Location findSafeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        Location below = origin.clone().add(0, -1, 0);
        if (isSafeBlock(below)) return origin.clone();
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location check = origin.clone().add(dx, 0, dz);
                    if (isSafeBlock(check.clone().add(0, -1, 0)) && isSafeBlock(check)) return check;
                }
            }
        }
        return origin.clone();
    }

    private boolean isSafeBlock(Location loc) {
        Block block = loc.getBlock();
        Material type = block.getType();
        return type != Material.LAVA && type != Material.FIRE && type != Material.SOUL_FIRE
                && type != Material.CACTUS && type != Material.MAGMA_BLOCK
                && type != Material.AIR || !loc.clone().add(0, -1, 0).getBlock().getType().isAir();
    }

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
