package ru.example.vkchatoffline;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatoffline.data.StashManager;
import ru.example.vkchatoffline.listeners.OfflineListener;
import ru.example.vkchatoffline.managers.ShiftManager;

public class VKChatOfflinePlugin extends JavaPlugin {
    private static VKChatOfflinePlugin instance;
    private ShiftManager shiftManager;
    private StashManager stashManager;

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
        shiftManager = new ShiftManager(this);
        shiftManager.loadShifts();

        getServer().getPluginManager().registerEvents(new OfflineListener(this), this);
        ru.example.vkchatoffline.commands.StashCommand stashCmd = new ru.example.vkchatoffline.commands.StashCommand(this);
        getCommand("stash").setExecutor(stashCmd);
        getCommand("stash").setTabCompleter(stashCmd);

        getCommand("shift").setExecutor(new ru.example.vkchatoffline.commands.ShiftCommand(this));

        getLogger().info("VKChatOffline v1.0 — шахтёрские смены запущены!");
    }

    @Override
    public void onDisable() {
        if (shiftManager != null) shiftManager.saveShifts();
        if (stashManager != null) stashManager.save();
    }

    public static VKChatOfflinePlugin getInstance() { return instance; }
    public ShiftManager getShiftManager() { return shiftManager; }
    public StashManager getStashManager() { return stashManager; }
}
