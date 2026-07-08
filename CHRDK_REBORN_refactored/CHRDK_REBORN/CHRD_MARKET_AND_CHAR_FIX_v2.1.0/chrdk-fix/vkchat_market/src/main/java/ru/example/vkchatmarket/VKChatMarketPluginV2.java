package ru.example.vkchatmarket;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.config.ConfigMigrationUtil;
import ru.example.vkchatmarket.commands.MarketCommand;
import ru.example.vkchatmarket.data.BalancedMarketManager;
import ru.example.vkchatmarket.data.MarketFun;
import ru.example.vkchatmarket.listeners.MarketGuiListenerV2;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  VKChatMarketPluginV2 — переключатель V1↔V2 через System Property.
 *  ─────────────────────────────────────────────────────────────────────
 *  Чтобы включить V2 (с фиксами), установите в plugin.yml (или в JVM args):
 *      -Dvkchat.market.version=v2
 *  или запустите с флагом --market-version=v2.
 *
 *  По умолчанию: V1 (как сейчас), для безопасного rolling-update.
 *  Когда протестируете V2 — переключите флаг.
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class VKChatMarketPluginV2 extends JavaPlugin {
    private static VKChatMarketPluginV2 instance;
    private BalancedMarketManager marketManager;
    private MarketFun marketFun;
    private final boolean useV2 = isUseV2();

    private static boolean isUseV2() {
        String v = System.getProperty("vkchat.market.version");
        if (v == null) v = System.getenv("VKCHAT_MARKET_VERSION");
        return "v2".equalsIgnoreCase(v);
    }

    private void migrateConfigDefaults() {
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

        marketManager = new BalancedMarketManager(this);
        marketFun = new MarketFun(this);
        marketFun.load();
        MarketCommand marketCmd = new MarketCommand(this);
        if (getCommand("market") != null) {
            getCommand("market").setExecutor(marketCmd);
            getCommand("market").setTabCompleter(marketCmd);
        } else {
            getLogger().warning("Команда 'market' не найдена в plugin.yml");
        }
        // [V2] Используем V2 listener с исправлениями
        getServer().getPluginManager().registerEvents(new MarketGuiListenerV2(this), this);

        long interval = getConfig().getLong("settings.recovery-interval", 1200) * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            marketManager.recoverMarket();
            marketManager.cleanupCooldowns();
        }, interval, interval);
        getServer().getScheduler().runTaskTimer(this, () -> marketManager.checkForRandomEvent(), 1200L, 36000L);
        getServer().getScheduler().runTaskTimer(this, () -> marketFun.checkFlashSale(), 1200L, 12000L);
        marketFun.ensureDailyQuest();
        getLogger().info("VKChatMarket V" + (useV2 ? "2" : "1") + " успешно запущен! Конфликт-менеджер: " + (useV2 ? "✓" : "✗"));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (marketManager != null) marketManager.saveAll();
        if (marketFun != null) marketFun.save();
    }

    public static VKChatMarketPluginV2 getInstance() { return instance; }
    public BalancedMarketManager getMarketManager() { return marketManager; }
    public MarketFun getMarketFun() { return marketFun; }

    /**
     * Совместимость со старым API (для MarketCommand / MarketGuiListener).
     * Возвращает marketManager как Object.
     */
    public Object getMarketManagerCompat() { return marketManager; }
}
