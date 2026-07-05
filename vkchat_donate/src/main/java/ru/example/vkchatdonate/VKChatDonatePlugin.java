package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
        if (getCommand("donate") != null) {
            DonateCommand dc = new DonateCommand(this);
            getCommand("donate").setExecutor(dc);
            getCommand("donate").setTabCompleter(dc);
        }

        var statusSec = getConfig().getConfigurationSection("statuses");
        getLogger().info("VKChatDonate запущен! Статусов: " +
                (statusSec != null ? statusSec.getKeys(false).size() : 0));

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                if (donateManager != null) donateManager.addPlayerToFundraiser(e.getPlayer());
            }
        }, this);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll((Listener) this);
        Bukkit.getScheduler().cancelTasks(this);
        if (donateManager != null) donateManager.shutdown();
    }

    public static VKChatDonatePlugin getInstance() { return instance; }
    public DonateManager getDonateManager() { return donateManager; }
}
