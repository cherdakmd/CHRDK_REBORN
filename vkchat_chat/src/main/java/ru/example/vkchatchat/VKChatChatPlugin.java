package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class VKChatChatPlugin extends JavaPlugin {
    private static VKChatChatPlugin instance;
    private ChatListener chatListener;

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

        getCommand("channel").setExecutor(new ChatCommand(this));
        getCommand("mute").setExecutor(new ChatCommand(this));

        getLogger().info("VKChatChat запущен! Каналы: " +
                getConfig().getConfigurationSection("channels").getKeys(false).size());
    }

    public static VKChatChatPlugin getInstance() { return instance; }
    public ChatListener getChatListener() { return chatListener; }
}
