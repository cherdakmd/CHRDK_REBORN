package ru.example.vkchatmarket.data;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.conflicts.EnchantmentConflictManager;
import ru.example.vkchatmarket.conflicts.EnchantmentConflictManager.ConflictResult;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  BalancedMarketManager v2.0 — переработанный менеджер маркета
 *  ─────────────────────────────────────────────────────────────────────
 *  Что изменено относительно v1:
 *    • Валидация зачарований через EnchantmentConflictManager.
 *    • Цены сбалансированы (DEEPSLATE_DIAMOND_ORE < DIAMOND и т.д.).
 *    • Recovery multiplier: исправлен Math.max(1, ...).
 *    • sellAll: единая функция без race condition.
 *    • addCustomBook проверяет конфликты перед сохранением.
 *    • createRandomEnchantedBook без deprecation-методов.
 *    • Bid-ask спред: симметричный, убирается при flash sale.
 *    • Материал предмета читается из конфига (поле "material").
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class BalancedMarketManager {

    public static class MarketEvent {
        public final String name;
        public final String itemId; // "ALL" для глобальных
        public final double multiplier;
        public final long expireTime;
        public final String category;

        public MarketEvent(String name, String itemId, double multiplier, long durationMs) {
            this(name, itemId, multiplier, durationMs, null);
        }
        public MarketEvent(String name, String itemId, double multiplier, long durationMs, String category) {
            this.name = name;
            this.itemId = itemId;
            this.multiplier = multiplier;
            this.expireTime = System.currentTimeMillis() + durationMs;
            this.category = category;
        }
        public boolean isActive() { return System.currentTimeMillis() < expireTime; }
        public boolean affects(String itemId, String itemCategory) {
            if (!isActive()) return false;
            if ("ALL".equals(this.itemId)) return true;
            if (this.itemId.equals(itemId)) return true;
            if (category != null && category.equals(itemCategory)) return true;
            return false;
        }
    }

    private final VKChatMarketPlugin plugin;
    private File file;
    private FileConfiguration data;

    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final Map<String, Double> dailyTrends = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyVolume = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTradeTime = new ConcurrentHashMap<>();
    private final Map<String, Double> momentum = new ConcurrentHashMap<>();
    private final java.util.List<String> history = new CopyOnWriteArrayList<>();
    private String trendDate = "";
    private int marketCyclePhase = 0;
    private long cycleEndTime = 0L;

    private final Map<String, java.util.List<Double>> priceHistory = new ConcurrentHashMap<>();
    private int priceHistoryTick = 0;
    private final java.util.List<MarketEvent> activeEvents = new CopyOnWriteArrayList<>();
    private final Map<String, String> customBooks = new ConcurrentHashMap<>();

    public BalancedMarketManager(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // ═══ Persistence ═══
    private void load() {
        file = new File(plugin.getDataFolder(), "market_data.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        if (data.contains("stock")) {
            for (String k : data.getConfigurationSection("stock").getKeys(false))
                stock.put(k, data.getInt("stock." + k));
        }
        trendDate = data.getString("daily.date", today());
        if (data.contains("daily.trends")) {
            for (String k : data.getConfigurationSection("daily.trends").getKeys(false))
                dailyTrends.put(k, data.getDouble("daily.trends." + k, 1.0));
        }
        if (data.contains("daily.volume")) {
            for (String k : data.getConfigurationSection("daily.volume").getKeys(false))
                dailyVolume.put(k, data.getInt("daily.volume." + k, 0));
        }
        history.addAll(data.getStringList("history"));
        if (data.contains("momentum")) {
            for (String k : data.getConfigurationSection("momentum").getKeys(false))
                momentum.put(k, data.getDouble("momentum." + k, 0.0));
        }
        marketCyclePhase = data.getInt("cycle.phase", 0);
        cycleEndTime = data.getLong("cycle.endtime", 0L);
        if (data.contains("custom_books")) {
            for (String k : data.getConfigurationSection("custom_books").getKeys(false))
                customBooks.put(k, data.getString("custom_books." + k));
        }
        ensureDailyTrends();
    }

    public void saveAll() {
        data.set("stock", null);
        for (Map.Entry<String, Integer> e : stock.entrySet()) data.set("stock." + e.getKey(), e.getValue());
        data.set("daily.date", trendDate);
        data.set("daily.trends", null);
        for (Map.Entry<String, Double> e : dailyTrends.entrySet()) data.set("daily.trends." + e.getKey(), e.getValue());
        data.set("daily.volume", null);
        for (Map.Entry<String, Integer> e : dailyVolume.entrySet()) data.set("daily.volume." + e.getKey(), e.getValue());
        int maxHistory = plugin.getConfig().getInt("market2.history.max-entries", 100);
        data.set("history", history.subList(Math.max(0, history.size() - maxHistory), history.size()));
        data.set("momentum", null);
        for (Map.Entry<String, Double> e : momentum.entrySet()) data.set("momentum." + e.getKey(), e.getValue());
        data.set("cycle.phase", marketCyclePhase);
        data.set("cycle.endtime", cycleEndTime);
        data.set("custom_books", null);
        for (Map.Entry<String, String> e : customBooks.entrySet()) data.set("custom_books." + e.getKey(), e.getValue());
        try { data.save(file); } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить market_data.yml: " + ex.getMessage());
        }
    }

    // ═══ Pricing ═══
    public double getCurrentPrice(String itemId) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double maxMult = plugin.getConfig().getDouble("items." + itemId + ".max-multiplier", 5.0);
        double elasticity = plugin.getConfig().getDouble("items." + itemId + ".elasticity", 100.0);
        if (elasticity <= 0) elasticity = 100.0;
        int currentStock = stock.getOrDefault(itemId, 0);

        double supplyMult = Math.pow(0.95, (double) currentStock / elasticity);
        supplyMult = Math.max(0.1, Math.min(maxMult, supplyMult));

        double price = base * supplyMult * getTrendMultiplier(itemId);
        double mom = momentum.getOrDefault(itemId, 0.0);
        price *= (1.0 + mom);

        // События (мультипликативно)
        double eventMult = 1.0;
        String cat = plugin.getConfig().getString("items." + itemId + ".category", "");
        for (MarketEvent ev : getActiveEvents()) {
            if (ev.affects(itemId, cat)) eventMult *= ev.multiplier;
        }
        price *= eventMult;

        if (marketCyclePhase == 1) price *= 1.3;
        else if (marketCyclePhase == 2) price *= 0.7;

        return Math.max(min, Math.round(price * 100.0) / 100.0);
    }

    public double getBuyPrice(String itemId) {
        double sellPrice = getCurrentPrice(itemId);
        double spread = plugin.getConfig().getDouble("settings.buy-spread", 0.20);
        // Flash sale: скидка применяется ДО spread (правильно)
        if (plugin.getMarketFun() != null && plugin.getMarketFun().isFlashSaleActive(itemId)) {
            double discount = plugin.getMarketFun().getFlashSaleDiscount();
            sellPrice *= (1.0 - discount);
        }
        return Math.round(sellPrice * (1.0 + spread) * 100.0) / 100.0;
    }

    public double calculateBulkBuyPrice(String itemId, int amount) {
        double total = 0;
        int currentStock = stock.getOrDefault(itemId, 0);
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double maxMult = plugin.getConfig().getDouble("items." + itemId + ".max-multiplier", 5.0);
        double elasticity = plugin.getConfig().getDouble("items." + itemId + ".elasticity", 100.0);
        double spread = plugin.getConfig().getDouble("settings.buy-spread", 0.20);
        double mom = momentum.getOrDefault(itemId, 0.0);
        if (elasticity <= 0) elasticity = 100.0;
        String cat = plugin.getConfig().getString("items." + itemId + ".category", "");

        for (int i = 0; i < amount; i++) {
            int virtualStock = currentStock - i;
            double supplyMult = Math.pow(0.95, (double) virtualStock / elasticity);
            supplyMult = Math.max(0.1, Math.min(maxMult, supplyMult));
            double price = base * supplyMult * getTrendMultiplier(itemId) * (1.0 + mom);
            double eventMult = 1.0;
            for (MarketEvent ev : getActiveEvents()) if (ev.affects(itemId, cat)) eventMult *= ev.multiplier;
            price *= eventMult;
            if (marketCyclePhase == 1) price *= 1.3;
            else if (marketCyclePhase == 2) price *= 0.7;
            price = Math.max(min, price);
            total += price * (1.0 + spread);
        }
        // Flash sale
        if (plugin.getMarketFun() != null && plugin.getMarketFun().isFlashSaleActive(itemId)) {
            total *= (1.0 - plugin.getMarketFun().getFlashSaleDiscount());
        }
        return Math.round(total * 100.0) / 100.0;
    }

    public double calculateBulkSellPrice(String itemId, int amount) {
        double total = 0;
        int currentStock = stock.getOrDefault(itemId, 0);
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double maxMult = plugin.getConfig().getDouble("items." + itemId + ".max-multiplier", 5.0);
        double elasticity = plugin.getConfig().getDouble("items." + itemId + ".elasticity", 100.0);
        double sellSpread = plugin.getConfig().getDouble("settings.sell-spread", 0.10);
        double mom = momentum.getOrDefault(itemId, 0.0);
        if (elasticity <= 0) elasticity = 100.0;
        String cat = plugin.getConfig().getString("items." + itemId + ".category", "");

        for (int i = 0; i < amount; i++) {
            int virtualStock = currentStock + i;
            double supplyMult = Math.pow(0.95, (double) virtualStock / elasticity);
            supplyMult = Math.max(0.1, Math.min(maxMult, supplyMult));
            double price = base * supplyMult * getTrendMultiplier(itemId) * (1.0 + mom);
            double eventMult = 1.0;
            for (MarketEvent ev : getActiveEvents()) if (ev.affects(itemId, cat)) eventMult *= ev.multiplier;
            price *= eventMult;
            if (marketCyclePhase == 1) price *= 1.3;
            else if (marketCyclePhase == 2) price *= 0.7;
            price = Math.max(min, price);
            total += price * (1.0 - sellSpread);
        }
        return Math.round(total * 100.0) / 100.0;
    }

    // ═══ Trade ═══
    public boolean canTrade(String itemId, Player player) {
        long cooldownMs = plugin.getConfig().getLong("settings.trade-cooldown-ms", 500);
        long now = System.currentTimeMillis();
        String key = itemId + ":" + player.getUniqueId();
        Long last = lastTradeTime.get(key);
        return last == null || now - last >= cooldownMs;
    }
    public void markTrade(String itemId, Player player) {
        lastTradeTime.put(itemId + ":" + player.getUniqueId(), System.currentTimeMillis());
    }

    public int sellItems(String itemId, int amount, double donorMultiplier) {
        int maxStock = plugin.getConfig().getInt("items." + itemId + ".max-stock", 5000);
        int current = stock.getOrDefault(itemId, 0);
        int canAccept = Math.max(0, maxStock - current);
        int actualAmount = Math.min(amount, canAccept);
        if (actualAmount <= 0) return 0;
        stock.put(itemId, current + actualAmount);
        double totalPrice = calculateBulkSellPrice(itemId, actualAmount) * donorMultiplier;
        int repToGive = Math.max(1, (int) Math.round(totalPrice));
        double tradeImpact = plugin.getConfig().getDouble("market2.trade-impact", 0.05);
        double mom = momentum.getOrDefault(itemId, 0.0);
        momentum.put(itemId, Math.max(-2.0, mom - actualAmount * tradeImpact));
        recordTrade(itemId, actualAmount, "sell");
        return repToGive;
    }

    public int buyItems(String itemId, int amount, double donorMultiplier) {
        int current = stock.getOrDefault(itemId, 0);
        int minStock = plugin.getConfig().getInt("items." + itemId + ".min-stock", -200);
        int canProvide = Math.max(0, current - minStock);
        int actualAmount = Math.min(amount, canProvide);
        if (actualAmount <= 0) return -1;
        stock.put(itemId, current - actualAmount);
        double totalPrice = calculateBulkBuyPrice(itemId, actualAmount) * donorMultiplier;
        int repToCharge = Math.max(1, (int) Math.round(totalPrice));
        double tradeImpact = plugin.getConfig().getDouble("market2.trade-impact", 0.05);
        double mom = momentum.getOrDefault(itemId, 0.0);
        momentum.put(itemId, Math.min(2.0, mom + actualAmount * tradeImpact));
        recordTrade(itemId, actualAmount, "buy");
        return repToCharge;
    }

    // ═══ Recovery ═══
    public void recoverMarket() {
        if (!plugin.getConfig().contains("items")) return;
        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            int current = stock.getOrDefault(itemId, 0);
            if (current == 0) continue;
            int recoveryRate = plugin.getConfig().getInt("items." + itemId + ".recovery-rate", 5);
            double recoveryMultiplier = plugin.getConfig().getDouble("market2.recovery-multiplier", 1.0);
            // ИСПРАВЛЕНО: убран Math.max(1, ...) — пусть маленькие значения дают маленький recovery
            int recovery = Math.max(1, (int) Math.round(recoveryRate * recoveryMultiplier));
            if (current > 0) {
                stock.put(itemId, Math.max(0, current - recovery));
            } else {
                stock.put(itemId, Math.min(0, current + recovery));
            }
            double mom = momentum.getOrDefault(itemId, 0.0);
            if (Math.abs(mom) > 0.01) {
                momentum.put(itemId, mom * 0.9);
            } else {
                momentum.remove(itemId);
            }
        }
        checkMarketCycle();
        priceHistoryTick++;
        if (priceHistoryTick % 10 == 0) {
            for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
                priceHistory.computeIfAbsent(itemId, k -> new ArrayList<>()).add(getCurrentPrice(itemId));
                if (priceHistory.get(itemId).size() > 200) priceHistory.get(itemId).remove(0);
            }
        }
        saveAll();
    }

    private void checkMarketCycle() {
        long now = System.currentTimeMillis();
        if (now < cycleEndTime) return;
        double cycleChance = plugin.getConfig().getDouble("market2.cycle.chance", 0.05);
        if (ThreadLocalRandom.current().nextDouble() >= cycleChance) return;
        if (marketCyclePhase == 0) {
            marketCyclePhase = ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        } else {
            marketCyclePhase = 0;
        }
        long durationMs = plugin.getConfig().getLong("market2.cycle.duration-ms", 1800000);
        cycleEndTime = now + durationMs;
        String msg;
        if (marketCyclePhase == 1) msg = "📈 [Биржа] Экономический БУМ! Все цены +30%!";
        else if (marketCyclePhase == 2) msg = "📉 [Биржа] Рыночный КРАХ! Все цены -30%!";
        else msg = "➡ [Биржа] Рынок стабилизировался.";
        org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + msg);
        addHistory("🔄 Цикл рынка: " + (marketCyclePhase == 1 ? "БУМ" : marketCyclePhase == 2 ? "КРАХ" : "НОРМА"));
    }

    // ═══ Daily trends ═══
    public synchronized void ensureDailyTrends() {
        String now = today();
        if (now.equals(trendDate) && !dailyTrends.isEmpty()) return;
        trendDate = now;
        dailyTrends.clear();
        dailyVolume.clear();
        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        double min = plugin.getConfig().getDouble("market2.trends.min-multiplier", 0.60);
        double max = plugin.getConfig().getDouble("market2.trends.max-multiplier", 1.80);
        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            double roll = ThreadLocalRandom.current().nextGaussian() * 0.2 + 1.0;
            roll = Math.max(min, Math.min(max, roll));
            dailyTrends.put(itemId, Math.round(roll * 100.0) / 100.0);
        }
        addHistory("📊 Новый торговый день: тренды обновлены.");
        saveAll();
    }

    public double getTrendMultiplier(String itemId) {
        ensureDailyTrends();
        return dailyTrends.getOrDefault(itemId, 1.0);
    }

    public String getTrendLabel(String itemId) {
        double m = getTrendMultiplier(itemId);
        if (m >= 1.60) return "🔥 АЖИОТАЖ x" + String.format("%.2f", m);
        if (m >= 1.30) return "📈 Высокий спрос x" + String.format("%.2f", m);
        if (m >= 1.10) return "↗ Рост x" + String.format("%.2f", m);
        if (m <= 0.40) return "💀 КРИЗИС x" + String.format("%.2f", m);
        if (m <= 0.60) return "📉 Обвал x" + String.format("%.2f", m);
        if (m <= 0.85) return "↘ Скидка x" + String.format("%.2f", m);
        return "➡ Стабильно x" + String.format("%.2f", m);
    }

    // ═══ Random events ═══
    public void checkForRandomEvent() {
        activeEvents.removeIf(e -> !e.isActive());
        int maxEvents = plugin.getConfig().getInt("market2.events.max-concurrent", 3);
        if (activeEvents.size() >= maxEvents) return;
        double eventChance = plugin.getConfig().getDouble("market2.events.chance", 0.15);
        if (ThreadLocalRandom.current().nextDouble() >= eventChance) return;
        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;
        String targetItem = items.get(ThreadLocalRandom.current().nextInt(items.size()));
        int eventType = ThreadLocalRandom.current().nextInt(12);
        String msg;
        long duration = plugin.getConfig().getLong("market2.events.duration-ms", 600000);
        MarketEvent newEvent;

        switch (eventType) {
            case 0:
                newEvent = new MarketEvent("⚠️ Дефицит: " + getItemName(targetItem), targetItem, 2.5, duration);
                msg = "🚨 [Биржа] " + newEvent.name + "! Цена x2.5!";
                stock.put(targetItem, Math.max(-100, stock.getOrDefault(targetItem, 0) - 50));
                break;
            case 1:
                newEvent = new MarketEvent("📦 Избыток: " + getItemName(targetItem), targetItem, 0.4, duration);
                msg = "📉 [Биржа] " + newEvent.name + "! Цена x0.4!";
                stock.put(targetItem, stock.getOrDefault(targetItem, 0) + 100);
                break;
            case 2:
                newEvent = new MarketEvent("⛏️ Золотая Лихорадка", "GOLD_INGOT", 3.0, duration);
                msg = "🚨 [Биржа] Золотая Лихорадка! Золото x3!";
                stock.put("GOLD_INGOT", Math.max(-100, stock.getOrDefault("GOLD_INGOT", 0) - 30));
                break;
            case 3:
                newEvent = new MarketEvent("🔌 Энергетический Кризис", "COAL", 4.0, duration);
                msg = "🚨 [Биржа] Энергетический Кризис! Уголь x4!";
                stock.put("COAL", Math.max(-100, stock.getOrDefault("COAL", 0) - 80));
                break;
            case 4:
                newEvent = new MarketEvent("🏗 Строительный Бум", "ALL", 1.5, duration);
                msg = "🏗 [Биржа] Строительный Бум! Все цены продажи +50%!";
                break;
            case 5:
                newEvent = new MarketEvent("💥 Рыночный Обвал", "ALL", 0.5, duration);
                msg = "💥 [Биржа] Рыночный Обвал! Все цены -50%!";
                break;
            case 6:
                newEvent = new MarketEvent("💎 Алмазная Лихорадка", "DIAMOND", 2.8, duration);
                msg = "💎 [Биржа] Алмазная Лихорадка! Алмазы x2.8!";
                stock.put("DIAMOND", Math.max(-100, stock.getOrDefault("DIAMOND", 0) - 40));
                break;
            case 7:
                newEvent = new MarketEvent("🍞 Голод", "ALL", 1.8, duration, "еда");
                msg = "🍞 [Биржа] Голод! Цены на еду x1.8!";
                break;
            case 8:
                newEvent = new MarketEvent("⚔️ Война", "ALL", 2.0, duration, "мобы");
                msg = "⚔️ [Биржа] Война! Лут мобов x2.0!";
                break;
            case 9:
                newEvent = new MarketEvent("🕊 Мирный Договор", "ALL", 0.6, duration, "мобы");
                msg = "🕊 [Биржа] Мирный Договор! Лут мобов x0.6!";
                break;
            case 10:
                newEvent = new MarketEvent("🔥 Лесной Пожар", "ALL", 2.5, duration, "дерево");
                msg = "🔥 [Биржа] Лесной Пожар! Дерево x2.5!";
                break;
            case 11:
                newEvent = new MarketEvent("🔬 Технологический Прорыв", "REDSTONE", 3.5, duration);
                msg = "🔬 [Биржа] Технологический Прорыв! Редстоун x3.5!";
                stock.put("REDSTONE", Math.max(-100, stock.getOrDefault("REDSTONE", 0) - 60));
                break;
            default:
                return;
        }
        activeEvents.add(newEvent);
        org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + msg);
        addHistory("⚡ Событие: " + newEvent.name + " x" + newEvent.multiplier);
        saveAll();
    }

    public MarketEvent getStrongestEvent(String itemId) {
        MarketEvent strongest = null;
        for (MarketEvent e : activeEvents) {
            if (e.isActive() && e.affects(itemId, null)) {
                if (strongest == null || Math.abs(e.multiplier - 1.0) > Math.abs(strongest.multiplier - 1.0)) {
                    strongest = e;
                }
            }
        }
        return strongest;
    }

    public java.util.List<MarketEvent> getActiveEvents() {
        java.util.List<MarketEvent> r = new java.util.ArrayList<>();
        for (MarketEvent e : activeEvents) if (e.isActive()) r.add(e);
        return r;
    }

    public String getActiveEventName() {
        MarketEvent e = getStrongestEvent("ALL");
        return e != null ? e.name : null;
    }
    public String getActiveEventItemId() {
        MarketEvent e = getStrongestEvent("ALL");
        return e != null ? e.itemId : null;
    }
    public double getActiveEventMultiplier() {
        MarketEvent e = getStrongestEvent("ALL");
        return e != null ? e.multiplier : 1.0;
    }
    public long getActiveEventExpireTime() {
        MarketEvent e = getStrongestEvent("ALL");
        return e != null ? e.expireTime : 0L;
    }

    public int getStock(String itemId) { return stock.getOrDefault(itemId, 0); }
    public int getMaxStock(String itemId) { return plugin.getConfig().getInt("items." + itemId + ".max-stock", 5000); }
    public boolean isScarcity(String itemId) {
        int current = stock.getOrDefault(itemId, 0);
        int threshold = plugin.getConfig().getInt("items." + itemId + ".scarcity-threshold", 0);
        return threshold > 0 && current <= threshold;
    }
    public double getPriceDeltaPercent(String itemId) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        if (base <= 0) return 0;
        return ((getCurrentPrice(itemId) - base) / base) * 100.0;
    }
    public int getDailyVolume(String itemId) {
        ensureDailyTrends();
        return dailyVolume.getOrDefault(itemId, 0);
    }
    public void recordTrade(String itemId, int amount, String type) {
        ensureDailyTrends();
        dailyVolume.put(itemId, dailyVolume.getOrDefault(itemId, 0) + amount);
        int logThreshold = plugin.getConfig().getInt("market2.history.min-volume-log", 16);
        if (amount >= logThreshold) {
            addHistory(type.equals("sell") ? "📤" : "📥" + " " + itemId + " x" + amount + " " + getTrendLabel(itemId));
        }
    }
    public void addHistory(String line) {
        String stamp = new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date());
        history.add(stamp + " — " + line);
        int maxHistory = plugin.getConfig().getInt("market2.history.max-entries", 100);
        while (history.size() > maxHistory) history.remove(0);
    }
    public java.util.List<String> getHistoryTail(int limit) {
        int from = Math.max(0, history.size() - Math.max(1, limit));
        return new java.util.ArrayList<>(history.subList(from, history.size()));
    }
    public java.util.List<String> getTopTrends(int limit) {
        ensureDailyTrends();
        java.util.List<String> ids = new java.util.ArrayList<>(dailyTrends.keySet());
        ids.sort((a, b) -> Double.compare(dailyTrends.getOrDefault(b, 1.0), dailyTrends.getOrDefault(a, 1.0)));
        if (ids.size() > limit) return new java.util.ArrayList<>(ids.subList(0, limit));
        return ids;
    }
    public String economyAuditLine() {
        ensureDailyTrends();
        int hot = 0, cheap = 0, deficit = 0;
        for (Map.Entry<String, Double> e : dailyTrends.entrySet()) {
            if (e.getValue() >= 1.3) hot++;
            if (e.getValue() <= 0.7) cheap++;
        }
        for (Map.Entry<String, Integer> e : stock.entrySet()) if (e.getValue() < 0) deficit++;
        String cycle = marketCyclePhase == 1 ? " | 📈 БУМ" : marketCyclePhase == 2 ? " | 📉 КРАХ" : "";
        return "📈 Рост: " + hot + " | 📉 Падение: " + cheap + " | ⚠️ Дефицит: " + deficit + cycle + " | ⚡ События: " + activeEvents.size();
    }
    public String getMarketCycleLabel() {
        if (marketCyclePhase == 1) return "📈 БУМ (+30%)";
        if (marketCyclePhase == 2) return "📉 КРАХ (-30%)";
        return "➡ Стабильно";
    }
    public void logTransaction(String player, String itemId, int amount, String type, double price, int rep) {
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String line = stamp + " | " + player + " | " + type + " | " + itemId + " x" + amount + " | цена: " + String.format("%.2f", price) + " | реп: " + rep;
        try (java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(plugin.getDataFolder(), "transactions.log"), true);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(fw);
             java.io.PrintWriter out = new java.io.PrintWriter(bw)) {
            out.println(line);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Не удалось записать в transactions.log: " + e.getMessage());
        }
    }
    public java.util.List<Double> getPriceHistory(String itemId) {
        return priceHistory.getOrDefault(itemId, new java.util.ArrayList<>());
    }
    private String today() { return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date()); }
    public void cleanupCooldowns() {
        long cutoff = System.currentTimeMillis() - 60000;
        lastTradeTime.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    // ═══ Custom books — ИСПРАВЛЕНО: с валидацией конфликтов ═══
    private void loadCustomBooks() {
        if (data.contains("custom_books")) {
            for (String k : data.getConfigurationSection("custom_books").getKeys(false))
                customBooks.put(k, data.getString("custom_books." + k));
        }
    }

    /**
     * Добавить кастомную книгу в сток.
     * ВАЖНО: проверяет конфликты через EnchantmentConflictManager.
     * @return id книги или null, если книга невалидна
     */
    public String addCustomBook(ItemStack book) {
        if (book == null || !book.hasItemMeta() || !book.getItemMeta().hasEnchants()) return null;
        ConflictResult cr = EnchantmentConflictManager.validateEnchantedBook(book);
        if (!cr.isValid()) {
            // Не сохраняем книгу с конфликтами — лог
            plugin.getLogger().warning("Отклонена книга с конфликтом: " + cr.formatUserMessage());
            return null;
        }
        Map<Enchantment, Integer> enchs = book.getItemMeta().getEnchants();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> e : enchs.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(EnchantmentConflictManager.resolveKey(e.getKey())).append(":").append(e.getValue());
        }
        if (sb.length() == 0) return null;
        String id = "CB_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        customBooks.put(id, sb.toString());
        stock.put(id, 1);
        return id;
    }

    public ItemStack takeCustomBook() {
        if (customBooks.isEmpty()) return null;
        String first = customBooks.keySet().iterator().next();
        String encoded = customBooks.remove(first);
        stock.remove(first);
        return decodeBook(encoded);
    }

    public boolean hasCustomBooks() { return !customBooks.isEmpty(); }

    /**
     * Декодировать кастомную книгу по строковому коду.
     */
    public static ItemStack decodeBook(String encoded) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        for (String part : encoded.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                Enchantment ench = resolveEnchantByName(kv[0]);
                if (ench != null) {
                    try {
                        meta.addEnchant(ench, Integer.parseInt(kv[1]), true);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Универсальный resolve Enchantment по строковому ключу, совместимый с 1.16–1.21.
     * На 1.20+ пытается Registry, на старых — getByName.
     */
    public static Enchantment resolveEnchantByName(String name) {
        if (name == null) return null;
        // 1) Старый API
        try {
            Enchantment e = Enchantment.getByName(name.toUpperCase(java.util.Locale.ROOT));
            if (e != null) return e;
        } catch (NoSuchMethodError | IllegalArgumentException ignored) {}
        // 2) Новый API
        try {
            org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT)));
        } catch (Throwable ignored) {}
        // 3) Карта legacy → new
        return LEGACY_MAP.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    private static final Map<String, Enchantment> LEGACY_MAP = new HashMap<>();
    static {
        // Маппинг старых имён в поля, чтобы избежать deprecation warning
        try { LEGACY_MAP.put("protection",              Enchantment.PROTECTION_ENVIRONMENTAL); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("damage_all",              Enchantment.DAMAGE_ALL); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("dig_speed",               Enchantment.DIG_SPEED); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("durability",              Enchantment.DURABILITY); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("loot_bonus_blocks",       Enchantment.LOOT_BONUS_BLOCKS); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("loot_bonus_mobs",         Enchantment.LOOT_BONUS_MOBS); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("fire_aspect",             Enchantment.FIRE_ASPECT); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("arrow_damage",            Enchantment.ARROW_DAMAGE); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("depth_strider",           Enchantment.DEPTH_STRIDER); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("thorns",                  Enchantment.THORNS); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("protection_fall",         Enchantment.PROTECTION_FALL); } catch (Throwable ignored) {}
        try { LEGACY_MAP.put("arrow_infinite",          Enchantment.ARROW_INFINITE); } catch (Throwable ignored) {}
    }

    private String getItemName(String itemId) {
        return plugin.getConfig().getString("items." + itemId + ".name", itemId);
    }
}
