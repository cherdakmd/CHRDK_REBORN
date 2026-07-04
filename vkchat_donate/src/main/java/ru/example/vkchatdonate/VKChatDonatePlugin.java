package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class VKChatDonatePlugin extends JavaPlugin {
    private static VKChatDonatePlugin instance;
    private DonateManager donateManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ru.example.vkchat.config.ConfigMigrationUtil.migrate(this, "config.yml");

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        donateManager = new DonateManager(this);
        if (getCommand("donate") != null)
            getCommand("donate").setExecutor(new DonateCommand(this));

        var statusSec = getConfig().getConfigurationSection("statuses");
        getLogger().info("VKChatDonate запущен! Статусов: " +
                (statusSec != null ? statusSec.getKeys(false).size() : 0));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (donateManager != null) donateManager.shutdown();
    }

    public static VKChatDonatePlugin getInstance() { return instance; }
    public DonateManager getDonateManager() { return donateManager; }
}
