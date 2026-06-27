package ru.example.vkchatstarter;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatstarter.listeners.JoinListener;
import ru.example.vkchatstarter.commands.QuestCommand;

public class VKChatStarterPlugin extends JavaPlugin {
    private static VKChatStarterPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.example.vkchatstarter.listeners.QuestListener(this), this);

        QuestCommand questCmd = new QuestCommand(this);
        getCommand("quest").setExecutor(questCmd);
        getCommand("quest").setTabCompleter(questCmd);

        getLogger().info("VKChatStarter успешно запущен!");
    }

    public static VKChatStarterPlugin getInstance() {
        return instance;
    }
}
