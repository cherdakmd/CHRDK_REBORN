package ru.example.vkchatmarket.service;

import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketEntry;

public class PriceService {
    private final VKChatMarketPlugin plugin;

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

    public int getBuyPrice(MarketEntry entry) {
        return (int) Math.round(entry.basePrice() * getMultiplier() * (1.0 + getBuySpread()));
    }

    public int getSellPrice(MarketEntry entry) {
        return (int) Math.round(entry.basePrice() * getMultiplier() * (1.0 - getSellSpread()));
    }
}
