package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.config.ConfigMigrationUtil;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.data.MarketFun;
import ru.example.vkchatmarket.data.MarketManager;
import ru.example.vkchatmarket.listeners.MarketGuiListener;

public class VKChatMarketPlugin extends JavaPlugin {
    private static VKChatMarketPlugin instance;
    private MarketManager marketManager;
    private MarketFun marketFun;

    private void migrateConfigDefaults() {
        // Устаревшие ключи для удаления
        String[] obsoleteKeys = {
            "market2.volatility-factor",
            "market2.roulette.cost",
            "market2.roulette.cooldown-ms",
            "market2.roulette.gift-cost"
        };
        ConfigMigrationUtil.migrate(this, "config.yml", obsoleteKeys);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigDefaults();

        if (Bukkit.getPluginManager().getPlugin("VKChat") == null) {
            getLogger().severe("VKChat не найден! Модуль выключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        marketManager = new MarketManager(this);
        marketFun = new MarketFun(this);
        
        MarketCommand marketCmd = new MarketCommand(this);
        if (getCommand("market") != null) {
            getCommand("market").setExecutor(marketCmd);
            getCommand("market").setTabCompleter(marketCmd);
        } else {
            getLogger().warning("Команда 'market' не найдена в plugin.yml");
        }
        getServer().getPluginManager().registerEvents(new MarketGuiListener(this), this);

        long interval = getConfig().getLong("settings.recovery-interval", 1200) * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            marketManager.recoverMarket();
            marketManager.cleanupCooldowns();
        }, interval, interval);
        
        // Каждые 30 минут запускаем проверку случайных событий на бирже
        getServer().getScheduler().runTaskTimer(this, () -> marketManager.checkForRandomEvent(), 1200L, 36000L);
        
        // Каждые 10 минут проверяем Flash Sale
        getServer().getScheduler().runTaskTimer(this, () -> marketFun.checkFlashSale(), 1200L, 12000L);
        
        // Квест дня обновляется при старте
        marketFun.ensureDailyQuest();

        getLogger().info("VKChatMarket успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (marketManager != null) {
            marketManager.saveAll();
        }
    }

    public static VKChatMarketPlugin getInstance() {
        return instance;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public MarketFun getMarketFun() {
        return marketFun;
    }
}
