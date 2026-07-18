package ru.example.vkchatmarket.dynamics;

import org.bukkit.configuration.ConfigurationSection;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MarketDynamics {
    private final VKChatMarketPlugin plugin;

    private final Map<String, List<TradeRecord>> volumeHistory = new ConcurrentHashMap<>();
    private final Map<String, Double> momentum = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> priceHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> priceAlerts = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 200;
    private static final int MAX_PRICE_HISTORY = 50;

    public MarketDynamics(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordTrade(MarketEntry entry, int amount, boolean isBuy) {
        long now = System.currentTimeMillis();
        List<TradeRecord> history = volumeHistory.computeIfAbsent(entry.id(), k -> new ArrayList<>());
        history.add(new TradeRecord(now, amount, isBuy));
        if (history.size() > MAX_HISTORY) history.remove(0);

        updateMomentum(entry, amount, isBuy);
    }

    private void updateMomentum(MarketEntry entry, int amount, boolean isBuy) {
        double oldMomentum = momentum.getOrDefault(entry.id(), 0.0);
        double momentumFactor = plugin.getSettingsConfig().getDouble("dynamics.momentum-factor", 0.15);
        double direction = isBuy ? 1.0 : -1.0;
        double magnitude = Math.min(1.0, amount / 32.0);
        double newMomentum = oldMomentum * (1.0 - momentumFactor) + direction * magnitude * momentumFactor;
        newMomentum = Math.max(-1.0, Math.min(1.0, newMomentum));
        momentum.put(entry.id(), newMomentum);
    }

    public double getDecayedBuyVolume(MarketEntry entry) {
        return getDecayedVolume(entry, true);
    }

    public double getDecayedSellVolume(MarketEntry entry) {
        return getDecayedVolume(entry, false);
    }

    private double getDecayedVolume(MarketEntry entry, boolean buySide) {
        List<TradeRecord> history = volumeHistory.getOrDefault(entry.id(), Collections.emptyList());
        if (history.isEmpty()) return 0;

        double halfLife = plugin.getSettingsConfig().getDouble("dynamics.decay-half-life-minutes", 30) * 60_000;
        double now = System.currentTimeMillis();
        double total = 0;

        for (TradeRecord record : history) {
            if (record.isBuy != buySide) continue;
            double age = now - record.timestamp;
            double decay = Math.pow(0.5, age / halfLife);
            total += record.amount * decay;
        }
        return total;
    }

    public double getNetDecayedVolume(MarketEntry entry) {
        return getDecayedBuyVolume(entry) - getDecayedSellVolume(entry);
    }

    public double getMeanReversionFactor(MarketEntry entry) {
        double reversionRate = plugin.getSettingsConfig().getDouble("dynamics.mean-reversion-rate", 0.05);
        double net = getNetDecayedVolume(entry);
        int elasticity = plugin.getSettingsConfig().getInt("settings.dynamic-elasticity", 50);
        double currentShift = net / elasticity;
        return 1.0 - (currentShift * reversionRate);
    }

    public double getBulkMultiplier(int amount) {
        double baseMultiplier = plugin.getSettingsConfig().getDouble("dynamics.bulk-base", 1.0);
        double bulkScale = plugin.getSettingsConfig().getDouble("dynamics.bulk-scale", 0.02);
        double bulkCap = plugin.getSettingsConfig().getDouble("dynamics.bulk-cap", 2.0);
        double mult = baseMultiplier + (Math.log(Math.max(1, amount)) / Math.log(2)) * bulkScale;
        return Math.min(bulkCap, mult);
    }

    public double getCategoryVolatility(MarketCategory category) {
        ConfigurationSection sec = plugin.getSettingsConfig().getConfigurationSection("dynamics.category-volatility");
        if (sec == null) return 1.0;
        return sec.getDouble(category.configKey(), 1.0);
    }

    public double getCategoryVolatility(String itemId) {
        MarketEntry entry = plugin.getMarketService().get(itemId);
        return entry != null ? getCategoryVolatility(entry.category()) : 1.0;
    }

    public double getMomentum(MarketEntry entry) {
        return momentum.getOrDefault(entry.id(), 0.0);
    }

    public String getMomentumArrow(MarketEntry entry) {
        double m = getMomentum(entry);
        if (m > 0.4) return " §a🔥▲";
        if (m > 0.15) return " §a▴";
        if (m < -0.4) return " §c🔥▼";
        if (m < -0.15) return " §c▾";
        return "";
    }

    public double getNoise() {
        double noiseLevel = plugin.getSettingsConfig().getDouble("dynamics.noise-level", 0.02);
        return 1.0 + (ThreadLocalRandom.current().nextGaussian() * noiseLevel);
    }

    public double getAsymmetryFactor(boolean isBuy) {
        double buyImpact = plugin.getSettingsConfig().getDouble("dynamics.buy-impact", 1.0);
        double sellImpact = plugin.getSettingsConfig().getDouble("dynamics.sell-impact", 1.2);
        return isBuy ? buyImpact : sellImpact;
    }

    public void setPriceAlert(UUID playerId, String itemId, int targetPrice) {
        priceAlerts.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(itemId, targetPrice);
    }

    public void removePriceAlert(UUID playerId, String itemId) {
        Map<String, Integer> alerts = priceAlerts.get(playerId);
        if (alerts != null) alerts.remove(itemId);
    }

    public Map<String, Integer> getPlayerAlerts(UUID playerId) {
        return priceAlerts.getOrDefault(playerId, Collections.emptyMap());
    }

    public List<String> checkPriceAlerts(MarketEntry entry, int currentPrice) {
        List<String> triggered = new ArrayList<>();
        for (var playerEntry : priceAlerts.entrySet()) {
            Integer target = playerEntry.getValue().get(entry.id());
            if (target != null && currentPrice >= target) {
                var player = plugin.getServer().getPlayer(playerEntry.getKey());
                if (player != null && player.isOnline()) {
                    player.sendMessage("§6§lБИРЖА §8▸ §e⚠ " + entry.displayName() + " §7достиг §f" + currentPrice + " реп. §7(цель: " + target + ")");
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                    triggered.add(playerEntry.getKey().toString());
                }
            }
        }
        return triggered;
    }

    public double getMarketHealth(MarketEntry entry) {
        double net = Math.abs(getNetDecayedVolume(entry));
        double momentumVal = Math.abs(getMomentum(entry));
        double volatility = getCategoryVolatility(entry.category());
        double health = 1.0 - (momentumVal * 0.5) - (Math.min(net, 100) / 200.0) * volatility;
        return Math.max(0.0, Math.min(1.0, health));
    }

    public String getMarketHealthBar(MarketEntry entry) {
        double health = getMarketHealth(entry);
        int filled = (int) Math.round(health * 5);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < 5; i++) {
            bar.append(i < filled ? "§a|" : "§8|");
        }
        bar.append("§7]");
        return bar.toString();
    }

    public String getMarketHealthText(MarketEntry entry) {
        double health = getMarketHealth(entry);
        if (health > 0.8) return "§aСтабилен";
        if (health > 0.5) return "§eНестабилен";
        if (health > 0.2) return "§6Волатилен";
        return "§cКрах!";
    }

    public boolean isWhaleTrade(int amount) {
        int threshold = plugin.getSettingsConfig().getInt("dynamics.whale-threshold", 64);
        return amount >= threshold;
    }

    public void announceWhaleTrade(String playerName, MarketEntry entry, int amount, boolean isBuy, int totalPrice) {
        String action = isBuy ? "закупает" : "сбрасывает";
        String color = isBuy ? "§c" : "§a";
        String msg = color + "🐋 " + playerName + " " + action + " §f" + amount + "x §f" + entry.displayName() + " §7за §e" + totalPrice + " реп.";
        for (var player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("vkchat.market.player")) {
                player.sendMessage(msg);
            }
        }
    }

    public void recordPrice(MarketEntry entry, int price) {
        List<Double> history = priceHistory.computeIfAbsent(entry.id(), k -> new ArrayList<>());
        history.add((double) price);
        if (history.size() > MAX_PRICE_HISTORY) history.remove(0);
    }

    public List<Double> getPriceHistory(MarketEntry entry) {
        return priceHistory.getOrDefault(entry.id(), Collections.emptyList());
    }

    public String getPriceSparkline(MarketEntry entry) {
        List<Double> history = getPriceHistory(entry);
        if (history.size() < 2) return "§7─";
        StringBuilder sb = new StringBuilder();
        int last = history.size() - 1;
        int first = Math.max(0, last - 7);
        for (int i = first; i <= last; i++) {
            double prev = i > first ? history.get(i - 1) : history.get(i);
            double curr = history.get(i);
            if (curr > prev * 1.05) sb.append("§a/");
            else if (curr < prev * 0.95) sb.append("§c\\");
            else sb.append("§7─");
        }
        return sb.toString();
    }

    public double getCategoryTrend(MarketCategory category) {
        double totalDelta = 0;
        int count = 0;
        for (MarketEntry entry : plugin.getMarketService().getByCategory(category)) {
            List<Double> history = getPriceHistory(entry);
            if (history.size() < 2) continue;
            int last = history.size() - 1;
            int first = Math.max(0, last - 10);
            double delta = (history.get(last) - history.get(first)) / history.get(first);
            totalDelta += delta;
            count++;
        }
        return count > 0 ? totalDelta / count : 0.0;
    }

    public int[] getPriceRange(MarketEntry entry) {
        List<Double> history = getPriceHistory(entry);
        if (history.isEmpty()) return new int[]{entry.basePrice(), entry.basePrice()};
        int min = Integer.MAX_VALUE, max = 0;
        for (Double d : history) {
            int v = (int) Math.round(d);
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return new int[]{min, max};
    }

    public double getSeasonalFactor() {
        if (!plugin.getSettingsConfig().getBoolean("dynamics.seasonal.enabled", false)) return 1.0;
        int hour = java.time.LocalTime.now().getHour();
        ConfigurationSection sec = plugin.getSettingsConfig().getConfigurationSection("dynamics.seasonal.hours");
        if (sec == null) return 1.0;
        if (hour >= 6 && hour < 12) return sec.getDouble("morning", 1.0);
        if (hour >= 12 && hour < 18) return sec.getDouble("afternoon", 1.0);
        if (hour >= 18 && hour < 23) return sec.getDouble("evening", 1.0);
        return sec.getDouble("night", 1.0);
    }

    public void clearExpiredHistory() {
        long maxAge = plugin.getSettingsConfig().getInt("dynamics.max-history-minutes", 120) * 60_000L;
        long now = System.currentTimeMillis();
        for (var entry : volumeHistory.entrySet()) {
            entry.getValue().removeIf(r -> now - r.timestamp > maxAge);
        }
    }

    public record TradeRecord(long timestamp, int amount, boolean isBuy) {}
}
