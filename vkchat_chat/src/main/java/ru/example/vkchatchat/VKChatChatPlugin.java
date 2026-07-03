package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class VKChatChatPlugin extends JavaPlugin {
    private static VKChatChatPlugin instance;
    private ChatListener chatListener;
    private TabManager tabManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        tabManager = new TabManager(this);

        ChatCommand chatCmd = new ChatCommand(this);
        getCommand("channel").setExecutor(chatCmd);
        getCommand("mute").setExecutor(chatCmd);
        getCommand("msg").setExecutor(chatCmd);
        getCommand("ignore").setExecutor(chatCmd);
        getCommand("cc").setExecutor(chatCmd);

        getLogger().info("VKChatChat запущен! Каналы: " +
                getConfig().getConfigurationSection("channels").getKeys(false).size());
    }

    public static VKChatChatPlugin getInstance() { return instance; }
    public ChatListener getChatListener() { return chatListener; }
}
