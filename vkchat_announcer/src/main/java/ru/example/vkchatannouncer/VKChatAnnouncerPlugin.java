package ru.example.vkchatannouncer;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class VKChatAnnouncerPlugin extends JavaPlugin {
    private static VKChatAnnouncerPlugin instance;
    private AnnounceTask task;


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
            getLogger().info("config.yml автоматически обновлён: недостающие ключи автосообщений добавлены.");
        } catch (Exception e) {
            getLogger().warning("Не удалось выполнить авто-миграцию config.yml: " + e.getMessage());
        }
    }

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

        int interval = getConfig().getInt("settings.interval", 300) * 20;
        task = new AnnounceTask(this);
        task.runTaskTimer(this, interval, interval);

        getServer().getPluginManager().registerEvents(new QuizListener(this), this);

        getLogger().info("VKChatAnnouncer успешно запущен!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (task != null) task.cancel();
    }

    public static VKChatAnnouncerPlugin getInstance() {
        return instance;
    }
}
