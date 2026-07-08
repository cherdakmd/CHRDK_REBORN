package ru.example.vkchatmarket.data;

import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.concurrent.ConcurrentHashMap;

/**
 * PriceEngine — выделенный движок вычисления цен.
 * Отвечает за: текущую цену, цену покупки, тренды, множители событий.
 */
public class PriceEngine {

    private final VKChatMarketPlugin plugin;
    private final MarketManager marketManager;

    public PriceEngine(VKChatMarketPlugin plugin, MarketManager marketManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
    }

    /**
     * Текущая цена продажи с учётом всех множителей.
     */
    public double getCurrentSellPrice(String itemId) {
        double base = getBasePrice(itemId);
        double supplyMult = getSupplyMultiplier(itemId);
        double trendMult = getTrendMultiplier(itemId);
        double eventMult = getEventMultiplier(itemId);
        double cycleMult = getCycleMultiplier();

        double price = base * supplyMult * trendMult * eventMult * cycleMult;

        // Flash Sale
        if (plugin.getMarketFun().isFlashSaleActive(itemId)) {
            price *= (1.0 - plugin.getMarketFun().getFlashSaleDiscount());
        }

        // Минимальная цена
        double minPrice = plugin.getConfig().getDouble("items." + itemId + ".min-price", base * 0.1);
        price = Math.max(price, minPrice);

        // Максимальный множитель
        double maxMult = plugin.getConfig().getDouble("items." + itemId + ".max-multiplier", 10.0);
        price = Math.min(price, base * maxMult);

        return Math.max(0.01, price);
    }

    /**
     * Цена покупки (bid-ask spread).
     */
    public double getBuyPrice(String itemId) {
        double sellPrice = getCurrentSellPrice(itemId);
        double spread = plugin.getConfig().getDouble("settings.buy-spread", 0.20);
        return sellPrice * (1.0 + spread);
    }

    /**
     * Множитель спроса/предложения (экспоненциальная кривая).
     */
    private double getSupplyMultiplier(String itemId) {
        int stock = marketManager.getStock(itemId);
        double elasticity = plugin.getConfig().getDouble("items." + itemId + ".elasticity", 50.0);
        if (elasticity <= 0) elasticity = 50.0;
        return Math.pow(0.95, stock / elasticity);
    }

    /**
     * Дневной тренд (из dailyTrends).
     */
    private double getTrendMultiplier(String itemId) {
        return marketManager.getDailyTrend(itemId);
    }

    /**
     * Множитель активного события.
     */
    private double getEventMultiplier(String itemId) {
        MarketManager.MarketEvent event = marketManager.getStrongestEvent(itemId);
        return event != null ? event.multiplier : 1.0;
    }

    /**
     * Множитель рыночного цикла.
     */
    private double getCycleMultiplier() {
        int phase = marketManager.getMarketCyclePhase();
        return switch (phase) {
            case 1 -> 1.30; // БУМ
            case 2 -> 0.70; // КРАХ
            default -> 1.0;
        };
    }

    /**
     * Базовая цена из конфигурации.
     */
    private double getBasePrice(String itemId) {
        return plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
    }

    /**
     * Вычислить доход от продажи N предметов.
     * Учитывает нелинейность: цена падает при каждой проданной единице.
     */
    public int calculateSellRevenue(String itemId, int count, double donorMultiplier) {
        double total = 0;
        for (int i = 0; i < count; i++) {
            total += getCurrentSellPrice(itemId) * donorMultiplier;
            // Сдвигаем сток после каждой единицы
            marketManager.adjustStock(itemId, 1);
        }
        return Math.max(1, (int) Math.round(total));
    }

    /**
     * Вычислить стоимость покупки N предметов.
     */
    public int calculateBuyCost(String itemId, int count, double donorMultiplier) {
        double total = 0;
        for (int i = 0; i < count; i++) {
            total += getBuyPrice(itemId) * donorMultiplier;
        }
        return Math.max(1, (int) Math.round(total));
    }

    /**
     * Метка тренда для GUI.
     */
    public String getTrendLabel(String itemId) {
        double trend = marketManager.getDailyTrend(itemId);
        if (trend >= 1.3) return "🔥 Рост";
        if (trend >= 1.1) return "📈 Повышение";
        if (trend <= 0.7) return "❄ Падение";
        if (trend <= 0.9) return "📉 Снижение";
        return "➡ Стабильно";
    }

    /**
     * Процент отклонения от базовой цены.
     */
    public double getPriceDeltaPercent(String itemId) {
        double base = getBasePrice(itemId);
        if (base <= 0) return 0;
        return ((getCurrentSellPrice(itemId) - base) / base) * 100.0;
    }
}
