package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.core.ConfigMigrationUtil;

public class VKChatChatPlugin extends JavaPlugin {
    private static VKChatChatPlugin instance;
    private ChatListener chatListener;
    private TabManager tabManager;
    private BroadcastManager broadcastManager;
    private WordFilter wordFilter;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigMigrationUtil.migrate(this, "config.yml");

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        wordFilter = new WordFilter(this);

        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        tabManager = new TabManager(this);
        broadcastManager = new BroadcastManager(this);

        ChatCommand chatCmd = new ChatCommand(this);
        getCommand("channel").setExecutor(chatCmd);
        getCommand("mute").setExecutor(chatCmd); getCommand("mute").setTabCompleter(chatCmd);
        getCommand("msg").setExecutor(chatCmd); getCommand("msg").setTabCompleter(chatCmd);
        getCommand("ignore").setExecutor(chatCmd); getCommand("ignore").setTabCompleter(chatCmd);
        getCommand("cc").setExecutor(chatCmd);

        FilterCommand filterCmd = new FilterCommand(this);
        getCommand("filter").setExecutor(filterCmd);
        getCommand("filter").setTabCompleter(filterCmd);

        var chanSec = getConfig().getConfigurationSection("channels");
        getLogger().info("VKChatChat запущен! Каналов: " + (chanSec != null ? chanSec.getKeys(false).size() : 0));
        getLogger().info("Фильтр чата: " + (wordFilter.isEnabled() ? "включён (" + wordFilter.getForbiddenWords().size() + " слов)" : "выключен"));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        instance = null;
    }

    public static VKChatChatPlugin getInstance() { return instance; }
    public ChatListener getChatListener() { return chatListener; }
    public BroadcastManager getBroadcastManager() { return broadcastManager; }
    public WordFilter getWordFilter() { return wordFilter; }
}
