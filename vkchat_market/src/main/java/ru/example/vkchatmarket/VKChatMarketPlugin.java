package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.listener.MarketListener;
import ru.example.vkchatmarket.service.MarketService;
import ru.example.vkchatmarket.providers.MarketMotdProvider;
import ru.example.vkchat.api.MotdProviderRegistry;

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

        // Регистрация MOTD провайдера (без reflection)
        MotdProviderRegistry.register(new MarketMotdProvider(this));

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

        int eventInterval = getConfig().getInt("events.interval-minutes", 15) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> {
            marketService.prices().tryStartRandomEvent();
            if (marketService.prices().hasActiveEvent()) {
                String name = marketService.prices().getActiveEventName();
                long remaining = (marketService.prices().getActiveEventEnd() - System.currentTimeMillis()) / 1000;
                for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) {
                    pl.sendMessage("§6§lБИРЖА §8▸ " + name + " §7(§e" + (remaining / 60) + " мин.§7)");
                }
            }
            // Check expired
            if (!marketService.prices().hasActiveEvent() && marketService.prices().getActiveEventName() != null) {
                for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) {
                    pl.sendMessage("§6§lБИРЖА §8▸ §7Событие завершилось. Цены вернулись в норму.");
                }
                marketService.prices().clearEvent();
            }
        }, 200L, eventInterval);

        getLogger().info("VKChatMarket v4.2 запущен! Товаров: " + marketService.getAll().size());
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
