package ru.example.vkchatdonate;

import org.bukkit.Bukkit;
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
        getCommand("donate").setExecutor(new DonateCommand(this));

        getLogger().info("VKChatDonate запущен! Статусов: " +
                getConfig().getConfigurationSection("statuses").getKeys(false).size());
    }

    @Override
    public void onDisable() {
        if (donateManager != null) donateManager.shutdown();
    }

    public static VKChatDonatePlugin getInstance() { return instance; }
    public DonateManager getDonateManager() { return donateManager; }
}
