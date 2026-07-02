package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.commands.StashCommand;
import ru.example.vkchatoffline.data.PlayerData;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.listeners.OfflineListener;
import ru.example.vkchatoffline.managers.AdventureManager;

public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    private PlayerData playerData;
    private StashManager stashManager;
    private AdventureManager adventureManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Плагин выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerData = new PlayerData(getDataFolder());
        stashManager = new StashManager(this);
        adventureManager = new AdventureManager(this);

        getServer().getPluginManager().registerEvents(new OfflineListener(this), this);
        getCommand("stash").setExecutor(new StashCommand(this));
        getCommand("stash").setTabCompleter(new StashCommand(this));

        // Тик событий приключений
        getServer().getScheduler().runTaskTimer(this, () -> adventureManager.tickEvents(),
                100L, getConfig().getInt("general.event-interval-ticks", 600));

        getLogger().info("VKChatOffline v3.0 запущен!");
        getLogger().info("Зоны: 6 | Классы: 5 | Сеты: 6 (24 части) | Враги: 24");
    }

    @Override
    public void onDisable() {
        if (playerData != null) playerData.save();
        if (stashManager != null) stashManager.save();
    }

    public static VKChatOfflinePlugin getInstance() { return instance; }
    public PlayerData getPlayerData() { return playerData; }
    public StashManager getStashManager() { return stashManager; }
    public AdventureManager getAdventureManager() { return adventureManager; }
}
