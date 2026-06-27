package ru.example.vkchatstarter;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatstarter.listeners.JoinListener;

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

        getLogger().info("VKChatStarter успешно запущен!");
    }

    public static VKChatStarterPlugin getInstance() {
        return instance;
    }
}
