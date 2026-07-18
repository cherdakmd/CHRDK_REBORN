package ru.example.vkchatauction;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VKChatAuctionPlugin extends JavaPlugin {

    private static VKChatAuctionPlugin instance;
    private AuctionManager auctionManager;
    private final Map<UUID, Integer> pendingPayouts = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> pendingItems = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.auctionManager = new AuctionManager(this);
        AuctionDonateBridge.init();
        auctionManager.load();
        auctionManager.startAutoSave();

        getCommand("ah").setExecutor(new AuctionCommand(this));
        getCommand("ahadmin").setExecutor(new AuctionAdminCommand(this));

        getServer().getPluginManager().registerEvents(new AuctionListener(this), this);
        getServer().getPluginManager().registerEvents(new AuctionGuiListener(this), this);

        getLogger().info("VKChatAuction успешно запущен!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (auctionManager != null) {
            auctionManager.save();
        }
        instance = null;
    }

    public static VKChatAuctionPlugin getInstance() {
        return instance;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public Map<UUID, Integer> getPendingPayouts() {
        return pendingPayouts;
    }

    public Map<UUID, ItemStack> getPendingItems() {
        return pendingItems;
    }
}
