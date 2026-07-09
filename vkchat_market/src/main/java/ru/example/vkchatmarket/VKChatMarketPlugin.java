package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.listener.MarketListener;
import ru.example.vkchatmarket.service.MarketService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VKChatMarketPlugin extends JavaPlugin {
    private static VKChatMarketPlugin instance;
    private MarketService marketService;
    private final Map<UUID, String> searchPrompt = new ConcurrentHashMap<>();
    private final Map<UUID, String> customAmountPrompt = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        marketService = new MarketService(this);
        marketService.load();

        MarketListener listener = new MarketListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        MarketCommand marketCmd = new MarketCommand(this);
        if (getCommand("market") != null) {
            getCommand("market").setExecutor(marketCmd);
            getCommand("market").setTabCompleter(marketCmd);
        }

        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
                if (e.getRightClicked() instanceof org.bukkit.entity.Villager) {
                    org.bukkit.entity.Villager npc = (org.bukkit.entity.Villager) e.getRightClicked();
                    org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(VKChatMarketPlugin.this, "market_npc");
                    if (npc.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                        e.setCancelled(true);
                        MarketGui.openMainMenu(VKChatMarketPlugin.this, e.getPlayer());
                    }
                }
            }
        }, this);

        getLogger().info("VKChatMarket v4.1 запущен! Товаров: " + marketService.getAll().size());
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
    }

    public static VKChatMarketPlugin getInstance() { return instance; }
    public MarketService getMarketService() { return marketService; }
    public Map<UUID, String> getSearchPrompt() { return searchPrompt; }
    public Map<UUID, String> getCustomAmountPrompt() { return customAmountPrompt; }
}
