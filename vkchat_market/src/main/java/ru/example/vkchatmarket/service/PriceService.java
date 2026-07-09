package ru.example.vkchatmarket.service;

import org.bukkit.configuration.ConfigurationSection;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PriceService {
    private final VKChatMarketPlugin plugin;
    private final Map<String, Integer> buyVolume = new ConcurrentHashMap<>();
    private final Map<String, Integer> sellVolume = new ConcurrentHashMap<>();

    private String activeEventId;
    private String activeEventName;
    private long activeEventEnd;

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
        return (int) Math.round(entry.basePrice() * getMultiplier() * (1.0 + getBuySpread()) * dynamicFactor(entry, true) * eventFactor(entry, true));
    }

    public int getSellPrice(MarketEntry entry) {
        return (int) Math.round(entry.basePrice() * getMultiplier() * (1.0 - getSellSpread()) * dynamicFactor(entry, false) * eventFactor(entry, false));
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

    private double eventFactor(MarketEntry entry, boolean isBuy) {
        if (activeEventId == null || System.currentTimeMillis() > activeEventEnd) return 1.0;
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("events.list." + activeEventId);
        if (sec == null) return 1.0;
        String cat = sec.getString("category", null);
        if (cat != null && !entry.category().configKey().equals(cat)) return 1.0;
        return isBuy ? sec.getDouble("buy-mult", 1.0) : sec.getDouble("sell-mult", 1.0);
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

    // ═══ СОБЫТИЯ ═══

    public boolean hasActiveEvent() {
        return activeEventId != null && System.currentTimeMillis() <= activeEventEnd;
    }

    public String getActiveEventName() { return activeEventName; }

    public long getActiveEventEnd() { return activeEventEnd; }

    public void tryStartRandomEvent() {
        if (!plugin.getConfig().getBoolean("events.enabled", true)) return;
        if (hasActiveEvent()) return;

        double chance = plugin.getConfig().getDouble("events.chance", 0.20);
        if (Math.random() > chance) return;

        ConfigurationSection list = plugin.getConfig().getConfigurationSection("events.list");
        if (list == null) return;
        List<String> keys = new ArrayList<>(list.getKeys(false));
        if (keys.isEmpty()) return;
        String id = keys.get(new Random().nextInt(keys.size()));
        ConfigurationSection sec = list.getConfigurationSection(id);
        if (sec == null) return;

        activeEventId = id;
        activeEventName = sec.getString("name", id);
        int durationMin = plugin.getConfig().getInt("events.max-duration-minutes", 10);
        activeEventEnd = System.currentTimeMillis() + durationMin * 60_000L;
    }

    public void clearEvent() {
        activeEventId = null;
        activeEventName = null;
        activeEventEnd = 0;
    }
}
