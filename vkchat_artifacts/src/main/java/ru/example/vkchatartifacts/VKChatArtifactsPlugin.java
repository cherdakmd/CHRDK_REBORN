package ru.example.vkchatartifacts;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatartifacts.commands.ArtifactCommand;
import ru.example.vkchatartifacts.listeners.ArtifactListener;
import ru.example.vkchatartifacts.listeners.ConsumablesListener;
import ru.example.vkchatartifacts.bosses.BossManager;

public class VKChatArtifactsPlugin extends JavaPlugin {
    private static VKChatArtifactsPlugin instance;
    private BossManager bossManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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

    private void runAlchemistAirdrop() {
        java.util.List<? extends org.bukkit.entity.Player> online = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;

        org.bukkit.entity.Player target = online.get(new java.util.Random().nextInt(online.size()));
        org.bukkit.Location loc = target.getLocation();

        org.bukkit.block.Block block = loc.getWorld().getBlockAt(loc);
        block.setType(org.bukkit.Material.CHEST);

        if (block.getState() instanceof org.bukkit.block.Container) {
            org.bukkit.block.Container chest = (org.bukkit.block.Container) block.getState();
            java.util.List<String> items = getConfig().getStringList("alchemist-airdrop.tier.items");
            java.util.Random rng = new java.util.Random();
            for (String entry : items) {
                String[] parts = entry.split(";");
                if (parts.length < 4) continue;
                try {
                    org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                    int min = Integer.parseInt(parts[1]);
                    int max = Integer.parseInt(parts[2]);
                    double chance = Double.parseDouble(parts[3]);
                    if (rng.nextDouble() * 100 < chance) {
                        int amount = min + rng.nextInt(Math.max(1, max - min + 1));
                        chest.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
                    }
                } catch (Exception ignored) {}
            }
        }

        String msg = getConfig().getString("alchemist-airdrop.message", "&d&l✦ Алхимик дарит тебе таинственный сундук! Открой его!");
        target.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        target.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, loc.add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        target.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }
}
