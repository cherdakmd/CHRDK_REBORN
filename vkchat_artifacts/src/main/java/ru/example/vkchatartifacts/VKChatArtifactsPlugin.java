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
        ArtifactCommand artifactCmd = new ArtifactCommand(this);
        getCommand("artifacts").setExecutor(artifactCmd);
        getCommand("artifacts").setTabCompleter(artifactCmd);

        if (getConfig().getBoolean("bosses.enabled", true)) {
            long interval = getConfig().getLong("bosses.spawn-interval", 43200) * 20L;
            bossManager.runTaskTimer(this, 1200L, interval);
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
}
