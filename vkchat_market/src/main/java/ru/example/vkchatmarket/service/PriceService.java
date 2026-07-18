package ru.example.vkchatmarket.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.DonateStatusResolver;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.dynamics.MarketDynamics;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PriceService {
    private final VKChatMarketPlugin plugin;
    private final MarketDynamics dynamics;
    private final Map<String, Integer> buyVolume = new ConcurrentHashMap<>();
    private final Map<String, Integer> sellVolume = new ConcurrentHashMap<>();

    private String activeEventId;
    private String activeEventName;
    private long activeEventEnd;
    private boolean eventExpiredNotified = true;

    private static final double[] BUY_DISCOUNTS = {0.90, 0.80, 0.65, 0.50, 0.35};
    private static final double[] SELL_BONUSES   = {1.10, 1.20, 1.35, 1.50, 1.70};

    public PriceService(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.dynamics = new MarketDynamics(plugin);
    }

    public MarketDynamics dynamics() { return dynamics; }

    public double getMultiplier() {
        return plugin.getSettingsConfig().getDouble("settings.price-multiplier", 1.0);
    }

    public double getBuySpread() {
        return plugin.getSettingsConfig().getDouble("settings.buy-spread", 0.20);
    }

    public double getSellSpread() {
        return plugin.getSettingsConfig().getDouble("settings.sell-spread", 0.10);
    }

    public int getMinPrice() {
        return plugin.getSettingsConfig().getInt("settings.min-price", 1);
    }

    public int getMaxPrice() {
        return plugin.getSettingsConfig().getInt("settings.max-price", 10000);
    }

    public void recordBuy(MarketEntry entry, int amount) {
        if (!plugin.getSettingsConfig().getBoolean("settings.dynamic-pricing", true)) return;
        buyVolume.merge(entry.id(), amount, Integer::sum);
        dynamics.recordTrade(entry, amount, true);
    }

    public void recordSell(MarketEntry entry, int amount) {
        if (!plugin.getSettingsConfig().getBoolean("settings.dynamic-pricing", true)) return;
        sellVolume.merge(entry.id(), amount, Integer::sum);
        dynamics.recordTrade(entry, amount, false);
    }

    public int getBuyPrice(MarketEntry entry, Player player) {
        double raw = entry.basePrice() * getMultiplier() * (1.0 + getBuySpread())
                * dynamicFactor(entry, true)
                * eventFactor(entry, true)
                * donorBuyMult(player)
                * dynamics.getCategoryVolatility(entry.category())
                * dynamics.getMeanReversionFactor(entry)
                * dynamics.getBulkMultiplier(1)
                * dynamics.getNoise()
                * dynamics.getAsymmetryFactor(true)
                * dynamics.getSeasonalFactor();
        int price = clamp((int) Math.round(raw));
        dynamics.recordPrice(entry, price);
        return price;
    }

    public int getSellPrice(MarketEntry entry, Player player) {
        double raw = entry.basePrice() * getMultiplier() * (1.0 - getSellSpread())
                * dynamicFactor(entry, false)
                * eventFactor(entry, false)
                * donorSellMult(player)
                * dynamics.getCategoryVolatility(entry.category())
                * dynamics.getMeanReversionFactor(entry)
                * dynamics.getBulkMultiplier(1)
                * dynamics.getNoise()
                * dynamics.getAsymmetryFactor(false)
                * dynamics.getSeasonalFactor();
        int price = clamp((int) Math.round(raw));
        dynamics.recordPrice(entry, price);
        return price;
    }

    public int getBuyPrice(MarketEntry entry, Player player, int amount) {
        double bulkDiscount = 1.0 - getBulkDiscount(amount);
        double raw = entry.basePrice() * getMultiplier() * (1.0 + getBuySpread())
                * bulkDiscount
                * dynamicFactor(entry, true)
                * eventFactor(entry, true)
                * donorBuyMult(player)
                * dynamics.getCategoryVolatility(entry.category())
                * dynamics.getMeanReversionFactor(entry)
                * dynamics.getBulkMultiplier(amount)
                * dynamics.getNoise()
                * dynamics.getAsymmetryFactor(true)
                * dynamics.getSeasonalFactor();
        return clamp((int) Math.round(raw));
    }

    public int getSellPrice(MarketEntry entry, Player player, int amount) {
        double raw = entry.basePrice() * getMultiplier() * (1.0 - getSellSpread())
                * bulkDiscount(amount)
                * dynamicFactor(entry, false)
                * eventFactor(entry, false)
                * donorSellMult(player)
                * dynamics.getCategoryVolatility(entry.category())
                * dynamics.getMeanReversionFactor(entry)
                * dynamics.getBulkMultiplier(amount)
                * dynamics.getNoise()
                * dynamics.getAsymmetryFactor(false)
                * dynamics.getSeasonalFactor();
        return clamp((int) Math.round(raw));
    }

    public int getMaxBuyable(Player player, MarketEntry entry) {
        int price = getBuyPrice(entry, player);
        if (price <= 0) return 64;
        int vkId = VKChatBridge.getLinkedVkId(player);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;
        return Math.min(64, rep / price);
    }

    public int getMaxSellable(Player player, MarketEntry entry) {
        return Math.min(64, plugin.getMarketService().countItems(player, entry));
    }

    private double getBulkDiscount(int amount) {
        int t3 = plugin.getSettingsConfig().getInt("settings.bulk-discount-threshold-3", 256);
        int t2 = plugin.getSettingsConfig().getInt("settings.bulk-discount-threshold-2", 128);
        int t1 = plugin.getSettingsConfig().getInt("settings.bulk-discount-threshold-1", 64);
        double d3 = plugin.getSettingsConfig().getDouble("settings.bulk-discount-3", 0.15);
        double d2 = plugin.getSettingsConfig().getDouble("settings.bulk-discount-2", 0.10);
        double d1 = plugin.getSettingsConfig().getDouble("settings.bulk-discount-1", 0.05);
        if (amount >= t3) return d3;
        if (amount >= t2) return d2;
        if (amount >= t1) return d1;
        return 0.0;
    }

    private double bulkDiscount(int amount) {
        return getBulkDiscount(amount);
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
        if (!plugin.getSettingsConfig().getBoolean("settings.dynamic-pricing", true)) return 1.0;
        double net = dynamics.getNetDecayedVolume(entry);
        double maxShift = plugin.getSettingsConfig().getDouble("settings.max-dynamic-shift", 0.50);
        int elasticity = plugin.getSettingsConfig().getInt("settings.dynamic-elasticity", 50);
        double shift = Math.max(-maxShift, Math.min(maxShift, net / elasticity));
        double momentumShift = dynamics.getMomentum(entry) * plugin.getSettingsConfig().getDouble("dynamics.momentum-price-impact", 0.1);
        shift += isBuy ? momentumShift : -momentumShift;
        return isBuy ? 1.0 + shift : 1.0 - shift;
    }

    private double eventFactor(MarketEntry entry, boolean isBuy) {
        if (activeEventId == null || System.currentTimeMillis() > activeEventEnd) return 1.0;
        ConfigurationSection sec = plugin.getEventsConfig().getConfigurationSection("events.list." + activeEventId);
        if (sec == null) return 1.0;
        String cat = sec.getString("category", null);
        if (cat != null && !entry.category().configKey().equals(cat)) return 1.0;
        return isBuy ? sec.getDouble("buy-mult", 1.0) : sec.getDouble("sell-mult", 1.0);
    }

    public String trendArrow(MarketEntry entry) {
        double net = dynamics.getNetDecayedVolume(entry);
        double momentum = dynamics.getMomentum(entry);
        if (net > 20 || momentum > 0.3) return " §a▲";
        if (net > 5 || momentum > 0.1)  return " §a▴";
        if (net < -20 || momentum < -0.3) return " §c▼";
        if (net < -5 || momentum < -0.1)  return " §c▾";
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
        if (!plugin.getEventsConfig().getBoolean("events.enabled", true)) return;
        if (hasActiveEvent()) return;

        double chance = plugin.getEventsConfig().getDouble("events.chance", 0.20);
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        ConfigurationSection list = plugin.getEventsConfig().getConfigurationSection("events.list");
        if (list == null) return;
        List<String> keys = new ArrayList<>(list.getKeys(false));
        if (keys.isEmpty()) return;
        String id = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        ConfigurationSection sec = list.getConfigurationSection(id);
        if (sec == null) return;

        activeEventId = id;
        activeEventName = sec.getString("name", id);
        int durationMin = plugin.getEventsConfig().getInt("events.max-duration-minutes", 10);
        activeEventEnd = System.currentTimeMillis() + durationMin * 60_000L;
        eventExpiredNotified = false;
    }

    public void forceStartEvent(String id, String name) {
        activeEventId = id;
        activeEventName = name;
        int durationMin = plugin.getEventsConfig().getInt("events.max-duration-minutes", 10);
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

    public void tickDecay() {
        dynamics.clearExpiredHistory();
    }
}
