package ru.example.vkchatmarket.service;

import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PriceService {
    private final VKChatMarketPlugin plugin;
    private final Map<String, Integer> buyVolume = new ConcurrentHashMap<>();
    private final Map<String, Integer> sellVolume = new ConcurrentHashMap<>();

    public PriceService(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public double getMultiplier() {
        return plugin.getConfig().getDouble("settings.price-multiplier", 1.0);
    }

    public double getBuySpread() {
        return plugin.getConfig().getDouble("settings.buy-spread", 0.20);
    }

    public double getSellSpread() {
        return plugin.getConfig().getDouble("settings.sell-spread", 0.10);
    }

    public void recordBuy(MarketEntry entry, int amount) {
        if (!plugin.getConfig().getBoolean("settings.dynamic-pricing", true)) return;
        buyVolume.merge(entry.id(), amount, Integer::sum);
    }

    public void recordSell(MarketEntry entry, int amount) {
        if (!plugin.getConfig().getBoolean("settings.dynamic-pricing", true)) return;
        sellVolume.merge(entry.id(), amount, Integer::sum);
    }

    public int getBuyPrice(MarketEntry entry) {
        return (int) Math.round(entry.basePrice() * getMultiplier() * (1.0 + getBuySpread()) * dynamicFactor(entry, true));
    }

    public int getSellPrice(MarketEntry entry) {
        return (int) Math.round(entry.basePrice() * getMultiplier() * (1.0 - getSellSpread()) * dynamicFactor(entry, false));
    }

    private double dynamicFactor(MarketEntry entry, boolean isBuy) {
        if (!plugin.getConfig().getBoolean("settings.dynamic-pricing", true)) return 1.0;
        int buys = buyVolume.getOrDefault(entry.id(), 0);
        int sells = sellVolume.getOrDefault(entry.id(), 0);
        int net = buys - sells;
        double maxShift = plugin.getConfig().getDouble("settings.max-dynamic-shift", 0.50);
        int elasticity = plugin.getConfig().getInt("settings.dynamic-elasticity", 50);
        double shift = Math.max(-maxShift, Math.min(maxShift, (double) net / elasticity));
        return isBuy ? 1.0 + shift : 1.0 - shift;
    }

    public String trendArrow(MarketEntry entry) {
        int buys = buyVolume.getOrDefault(entry.id(), 0);
        int sells = sellVolume.getOrDefault(entry.id(), 0);
        int net = buys - sells;
        if (net > 20) return " §a▲";
        if (net > 5)  return " §a▴";
        if (net < -20) return " §c▼";
        if (net < -5)  return " §c▾";
        return " §7─";
    }
}
