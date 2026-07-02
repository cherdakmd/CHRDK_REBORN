package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.commands.StashCommand;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.listeners.OfflineListener;
import ru.example.vkchatoffline.managers.AdventureManager;

public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    private StashManager stashManager;
    private AdventureManager adventureManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        stashManager = new StashManager(this);
        adventureManager = new AdventureManager(this);
        adventureManager.loadStates();

        getServer().getPluginManager().registerEvents(adventureManager, this);
        getServer().getPluginManager().registerEvents(new OfflineListener(this), this);

        getCommand("stash").setExecutor(new StashCommand(this));

        getLogger().info("VKChatOffline v4.0 запущен!");
    }

    @Override
    public void onDisable() {
        if (adventureManager != null) adventureManager.saveAll();
        if (stashManager != null) stashManager.save();
    }

    public static VKChatOfflinePlugin getInstance() { return instance; }
    public StashManager getStashManager() { return stashManager; }
    public AdventureManager getAdventureManager() { return adventureManager; }
}
