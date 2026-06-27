package ru.example.vkchatstreams;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class VKChatStreamsPlugin extends JavaPlugin {
    private static VKChatStreamsPlugin instance;
    private StreamManager streamManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigDefaults();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        streamManager = new StreamManager(this);
        StreamCommand command = new StreamCommand(this, streamManager);
        getCommand("stream").setExecutor(command);
        getServer().getPluginManager().registerEvents(command, this);
        getServer().getPluginManager().registerEvents(streamManager, this);

        long interval = getConfig().getLong("settings.check-interval", 60) * 20L;
        streamManager.runTaskTimerAsynchronously(this, 20L, interval);

        getLogger().info("VKChatStreams (Twitch/VK/YouTube + ручной режим + награды) успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (streamManager != null) streamManager.saveData();
        getServer().getScheduler().cancelTasks(this);
    }

    private void migrateConfigDefaults() {
        try {
            if (getConfig().getDefaults() == null) return;
            boolean missing = false;
            for (String key : getConfig().getDefaults().getKeys(true)) {
                if (!getConfig().isSet(key)) { missing = true; break; }
            }
            if (!missing) return;
            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                java.io.File backup = new java.io.File(getDataFolder(), "config.yml.bak-before-migration-" + stamp);
                java.nio.file.Files.copy(configFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Создан бэкап старого config.yml: " + backup.getName());
            }
            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
        } catch (Exception e) {
            getLogger().warning("Не удалось выполнить авто-миграцию config.yml: " + e.getMessage());
        }
    }

    public static VKChatStreamsPlugin getInstance() { return instance; }
    public StreamManager getStreamManager() { return streamManager; }
}
