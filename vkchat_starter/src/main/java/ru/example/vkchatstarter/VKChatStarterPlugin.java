package ru.example.vkchatstarter;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.config.ConfigMigrationUtil;
import ru.example.vkchatstarter.listeners.JoinListener;
import ru.example.vkchatstarter.commands.QuestCommand;

import java.util.logging.Level;

public class VKChatStarterPlugin extends JavaPlugin {
    private static VKChatStarterPlugin instance;
    private QuestDataManager questDataManager;
    private int autoSaveTaskId = -1;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigMigrationUtil.migrate(this, "config.yml");

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        questDataManager = new QuestDataManager(this);

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.example.vkchatstarter.listeners.QuestListener(this, questDataManager), this);

        QuestCommand questCmd = new QuestCommand(this);
        getCommand("quest").setExecutor(questCmd);
        getCommand("quest").setTabCompleter(questCmd);

        // Auto-save every 5 minutes (6000 ticks = 300 seconds)
        autoSaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            questDataManager.save();
        }, 6000L, 6000L);

        getLogger().info("VKChatStarter успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (questDataManager != null) {
            questDataManager.save();
        }
        if (autoSaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        }
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        instance = null;
    }

    public QuestDataManager getQuestDataManager() {
        return questDataManager;
    }

    public static VKChatStarterPlugin getInstance() {
        return instance;
    }

    public void log(Level level, String message) {
        getLogger().log(level, message);
    }

    public void log(Level level, String message, Throwable thrown) {
        getLogger().log(level, message, thrown);
    }
}
