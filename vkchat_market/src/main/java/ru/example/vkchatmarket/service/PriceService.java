package ru.example.vkchatmarket.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.DonateStatusResolver;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PriceService {
    private final VKChatMarketPlugin plugin;
    private final Map<String, Integer> buyVolume = new ConcurrentHashMap<>();
    private final Map<String, Integer> sellVolume = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private String activeEventId;
    private String activeEventName;
    private long activeEventEnd;
    private boolean eventExpiredNotified = true;

    private static final double[] BUY_DISCOUNTS = {0.90, 0.80, 0.65, 0.50, 0.35};
    private static final double[] SELL_BONUSES   = {1.10, 1.20, 1.35, 1.50, 1.70};

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

    public int getMinPrice() {
        return plugin.getConfig().getInt("settings.min-price", 1);
    }

    public int getMaxPrice() {
        return plugin.getConfig().getInt("settings.max-price", 10000);
    }

    public void recordBuy(MarketEntry entry, int amount) {
        if (!plugin.getConfig().getBoolean("settings.dynamic-pricing", true)) return;
        buyVolume.merge(entry.id(), amount, Integer::sum);
    }

    public void recordSell(MarketEntry entry, int amount) {
        if (!plugin.getConfig().getBoolean("settings.dynamic-pricing", true)) return;
        sellVolume.merge(entry.id(), amount, Integer::sum);
    }

    public int getBuyPrice(MarketEntry entry, Player player) {
        double raw = entry.basePrice() * getMultiplier() * (1.0 + getBuySpread())
                * dynamicFactor(entry, true) * eventFactor(entry, true) * donorBuyMult(player);
        return clamp((int) Math.round(raw));
    }

    public int getSellPrice(MarketEntry entry, Player player) {
        double raw = entry.basePrice() * getMultiplier() * (1.0 - getSellSpread())
                * dynamicFactor(entry, false) * eventFactor(entry, false) * donorSellMult(player);
        return clamp((int) Math.round(raw));
    }

    public int getMaxBuyable(Player player, MarketEntry entry) {
        int price = getBuyPrice(entry, player);
        if (price <= 0) return 64;
        int vkId = ru.example.vkchat.util.VKChatBridge.getLinkedVkId(player);
        int rep = vkId != -1 ? ru.example.vkchat.util.VKChatBridge.getReputation(vkId) : 0;
        return Math.min(64, rep / price);
    }

    public int getMaxSellable(Player player, MarketEntry entry) {
        return Math.min(64, plugin.getMarketService().countItems(player, entry));
    }

    private int clamp(int price) {
        return Math.max(getMinPrice(), Math.min(getMaxPrice(), price));
    }

    private double donorBuyMult(Player player) {
        int idx = DonateStatusResolver.getStatusIndex(player);
        return idx >= 0 && idx < BUY_DISCOUNTS.length ? BUY_DISCOUNTS[idx] : 1.0;
    }

    private double donorSellMult(Player player) {
        int idx = DonateStatusResolver.getStatusIndex(player);
        return idx >= 0 && idx < SELL_BONUSES.length ? SELL_BONUSES[idx] : 1.0;
    }

    public String donorTag(Player player) {
        int idx = DonateStatusResolver.getStatusIndex(player);
        String[] tags = {"§bИскра", "§6Пламя", "§eЗвезда", "§5Легенда", "§dВластелин"};
        return idx >= 0 && idx < tags.length ? tags[idx] : null;
    }

    public double donorBuyMultVisible(Player player) {
        return donorBuyMult(player);
    }

    public double donorSellMultVisible(Player player) {
        return donorSellMult(player);
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

    public Map<String, Integer> getBuyVolumes() { return Collections.unmodifiableMap(buyVolume); }
    public Map<String, Integer> getSellVolumes() { return Collections.unmodifiableMap(sellVolume); }

    // ═══ СОБЫТИЯ ═══

    public boolean hasActiveEvent() {
        return activeEventId != null && System.currentTimeMillis() <= activeEventEnd;
    }

    public boolean isEventExpiredJustNow() {
        if (activeEventId != null && !eventExpiredNotified && System.currentTimeMillis() > activeEventEnd) {
            eventExpiredNotified = true;
            return true;
        }
        return false;
    }

    public String getActiveEventName() { return activeEventName; }

    public long getActiveEventEnd() { return activeEventEnd; }

    public void tryStartRandomEvent() {
        if (!plugin.getConfig().getBoolean("events.enabled", true)) return;
        if (hasActiveEvent()) return;

        double chance = plugin.getConfig().getDouble("events.chance", 0.20);
        if (random.nextDouble() > chance) return;

        ConfigurationSection list = plugin.getConfig().getConfigurationSection("events.list");
        if (list == null) return;
        List<String> keys = new ArrayList<>(list.getKeys(false));
        if (keys.isEmpty()) return;
        String id = keys.get(random.nextInt(keys.size()));
        ConfigurationSection sec = list.getConfigurationSection(id);
        if (sec == null) return;

        activeEventId = id;
        activeEventName = sec.getString("name", id);
        int durationMin = plugin.getConfig().getInt("events.max-duration-minutes", 10);
        activeEventEnd = System.currentTimeMillis() + durationMin * 60_000L;
        eventExpiredNotified = false;
    }

    public void forceStartEvent(String id, String name) {
        activeEventId = id;
        activeEventName = name;
        int durationMin = plugin.getConfig().getInt("events.max-duration-minutes", 10);
        activeEventEnd = System.currentTimeMillis() + durationMin * 60_000L;
        eventExpiredNotified = false;
    }

    public void clearEvent() {
        activeEventId = null;
        activeEventName = null;
        activeEventEnd = 0;
        eventExpiredNotified = true;
    }

    public Map<String, Double> getAllDynamicFactors() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (MarketEntry entry : plugin.getMarketService().getAll()) {
            double factor = 1.0 + dynamicFactor(entry, true);
            result.put(entry.id(), Math.round(factor * 100.0) / 100.0);
        }
        return result;
    }
}
