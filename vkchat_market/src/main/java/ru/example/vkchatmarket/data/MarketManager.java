package ru.example.vkchatmarket.data;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MarketManager v3.1 — Рефакторинг.
 * 
 * Изменения v3.1:
 * - Добавлен PriceEngine для вычисления цен (делегирование)
 * - Добавлен TradeLogger для логирования (делегирование)
 * - Добавлен метод adjustStock() для атомарного изменения стока
 * - priceHistory теперь сохраняется на диск
 * 
 * Модель: реальная кривая спроса/предложения с эластичностью.
 * - Цена = base * supplyMultiplier * trendMultiplier * eventMultiplier
 * - supplyMultiplier = Math.pow(0.95, stock / elasticity) — экспоненциальная кривая
 * - Bid-ask спред: покупка дороже продажи на 15-30%
 * - Анти-манипуляция: кулдаун на продажу того же предмета
 * - Восстановление: постепенное, зависит от спроса
 */
public class MarketManager {
    // Внутренний класс для рыночных событий
    public static class MarketEvent {
        public final String name;
        public final String itemId; // "ALL" для глобальных
        public final double multiplier;
        public final long expireTime;
        public final String category; // null = все категории

        public MarketEvent(String name, String itemId, double multiplier, long durationMs) {
            this.name = name;
            this.itemId = itemId;
            this.multiplier = multiplier;
            this.expireTime = System.currentTimeMillis() + durationMs;
            this.category = null;
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
    private PriceEngine priceEngine;
    private TradeLogger tradeLogger;

    // Основные данные рынка
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();       // Текущий запас (полож = избыток, отриц = дефицит)
    private final Map<String, Double> dailyTrends = new ConcurrentHashMap<>();  // Дневные тренды
    private final Map<String, Integer> dailyVolume = new ConcurrentHashMap<>(); // Объём торгов за день
    private final Map<String, Long> lastTradeTime = new ConcurrentHashMap<>(); // Кулдаун на торговлю
    private final Map<String, Double> momentum = new ConcurrentHashMap<>();    // Моментум цены (ускорение)
    private final Map<String, Integer> yesterdayVolume = new ConcurrentHashMap<>(); // Вчерашний объём
    private final java.util.List<String> history = new CopyOnWriteArrayList<>();
    private String trendDate = "";
    private int marketCyclePhase = 0;
    private final Map<String, String> customBooks = new ConcurrentHashMap<>();
    private long cycleEndTime = 0L;

    private final Map<String, java.util.List<Double>> priceHistory = new ConcurrentHashMap<>();
    private int priceHistoryTick = 0;

    // Рыночные события (поддержка множественных)
    private final java.util.List<MarketEvent> activeEvents = new CopyOnWriteArrayList<>();

    // Совместимость со старым API
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
        java.util.List<MarketEvent> result = new java.util.ArrayList<>();
        for (MarketEvent e : activeEvents) {
            if (e.isActive()) result.add(e);
        }
        return result;
    }

    public MarketManager(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.priceEngine = new PriceEngine(plugin, this);
        this.tradeLogger = new TradeLogger(plugin);
        load();
    }

    /**
     * Получить PriceEngine для вычисления цен.
     */
    public PriceEngine getPriceEngine() {
        return priceEngine;
    }

    /**
     * Получить TradeLogger для логирования.
     */
    public TradeLogger getTradeLogger() {
        return tradeLogger;
    }

    /**
     * Атомарное изменение стока предмета.
     */
    public void adjustStock(String itemId, int delta) {
        stock.merge(itemId, delta, Integer::sum);
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "market_data.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать market_data.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);

        if (data.contains("stock")) {
            ConfigurationSection sec = data.getConfigurationSection("stock");
            if (sec != null) {
                for (String item : sec.getKeys(false)) {
                    stock.put(item, data.getInt("stock." + item));
                }
            }
        }
        trendDate = data.getString("daily.date", today());
        if (data.contains("daily.trends")) {
            ConfigurationSection sec = data.getConfigurationSection("daily.trends");
            if (sec != null) {
                for (String item : sec.getKeys(false)) {
                    dailyTrends.put(item, data.getDouble("daily.trends." + item, 1.0));
                }
            }
        }
        if (data.contains("daily.volume")) {
            ConfigurationSection sec = data.getConfigurationSection("daily.volume");
            if (sec != null) {
                for (String item : sec.getKeys(false)) {
                    dailyVolume.put(item, data.getInt("daily.volume." + item, 0));
                }
            }
        }
        history.addAll(data.getStringList("history"));
        if (data.contains("momentum")) {
            ConfigurationSection sec = data.getConfigurationSection("momentum");
            if (sec != null) {
                for (String item : sec.getKeys(false)) {
                    momentum.put(item, data.getDouble("momentum." + item, 0.0));
                }
            }
        }
        marketCyclePhase = data.getInt("cycle.phase", 0);
        cycleEndTime = data.getLong("cycle.endtime", 0L);
        loadCustomBooks();
        ensureDailyTrends();
        tradeLogger.loadFrom(data);
        loadPriceHistory();
    }

    private void loadCustomBooks() {
        org.bukkit.configuration.ConfigurationSection sec = data.getConfigurationSection("custom_books");
        if (sec != null) {
            for (String key : sec.getKeys(false))
                customBooks.put(key, data.getString("custom_books." + key));
        }
    }

    public String addCustomBook(ItemStack book) {
        if (!book.hasItemMeta() || !book.getItemMeta().hasEnchants()) return null;
        Map<org.bukkit.enchantments.Enchantment, Integer> enchs = book.getItemMeta().getEnchants();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : enchs.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey().getName()).append(":").append(e.getValue());
        }
        if (sb.length() == 0) return null;
        String id = "CB_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        customBooks.put(id, sb.toString()); stock.put(id, 1);
        return id;
    }

    public ItemStack takeCustomBook() {
        if (customBooks.isEmpty()) return null;
        String first = customBooks.keySet().iterator().next();
        String encoded = customBooks.remove(first); stock.remove(first);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        for (String part : encoded.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try {
                    org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByName(kv[0]);
                    if (ench != null) meta.addEnchant(ench, Integer.parseInt(kv[1]), true);
                } catch (Exception ignored) {}
            }
        }
        book.setItemMeta(meta);
        return book;
    }

    public boolean hasCustomBooks() { return !customBooks.isEmpty(); }

    public void saveAll() {
        data.set("stock", null);
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            data.set("stock." + entry.getKey(), entry.getValue());
        }
        data.set("daily.date", trendDate);
        data.set("daily.trends", null);
        for (Map.Entry<String, Double> entry : dailyTrends.entrySet()) {
            data.set("daily.trends." + entry.getKey(), entry.getValue());
        }
        data.set("daily.volume", null);
        for (Map.Entry<String, Integer> entry : dailyVolume.entrySet()) {
            data.set("daily.volume." + entry.getKey(), entry.getValue());
        }
        int maxHistory = plugin.getConfig().getInt("market2.history.max-entries", 100);
        tradeLogger.saveTo(data);
        savePriceHistory();
        data.set("history", history.size() > maxHistory
                ? history.subList(Math.max(0, history.size() - maxHistory), history.size())
                : history);
        data.set("momentum", null);
        for (Map.Entry<String, Double> entry : momentum.entrySet()) {
            data.set("momentum." + entry.getKey(), entry.getValue());
        }
        data.set("cycle.phase", marketCyclePhase);
        data.set("cycle.endtime", cycleEndTime);
        data.set("custom_books", null);
        for (Map.Entry<String, String> e : customBooks.entrySet())
            data.set("custom_books." + e.getKey(), e.getValue());
        try { data.save(file); } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить market_data.yml: " + e.getMessage());
        }
    }

    // ========================================
    // ЦЕНООБРАЗОВАНИЕ v3.0
    // ========================================

    /**
     * Текущая цена ПРОДАЖИ (игрок продаёт рынку).
     * Формула: base * supplyMult * trendMult * eventMult * momentumMult * cycleMult
     * 
     * supplyMult = Math.pow(0.95, stock / elasticity)
     * - stock > 0 (избыток) → цена падает
     * - stock < 0 (дефицит) → цена растёт
     * - elasticity определяет, насколько чувствительна цена
     */
    public double getCurrentPrice(String itemId) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double maxMult = plugin.getConfig().getDouble("items." + itemId + ".max-multiplier", 5.0);
        double elasticity = plugin.getConfig().getDouble("items." + itemId + ".elasticity", 100.0);
        int currentStock = stock.getOrDefault(itemId, 0);

        // Экспоненциальная кривая спроса/предложения
        double supplyMult;
        if (elasticity <= 0) elasticity = 100.0;
        supplyMult = Math.pow(0.95, (double) currentStock / elasticity);

        // Ограничиваем множитель
        supplyMult = Math.max(0.1, Math.min(maxMult, supplyMult));

        double price = base * supplyMult;

        // Дневной тренд
        price *= getTrendMultiplier(itemId);

        // Моментум (каждый предмет двигает цену на trade-impact %)
        double mom = momentum.getOrDefault(itemId, 0.0);
        price *= (1.0 + mom);

        // Рыночные события (суммируем все активные)
        double eventMult = 1.0;
        for (MarketEvent e : getActiveEvents()) {
            if (e.affects(itemId, plugin.getConfig().getString("items." + itemId + ".category", ""))) {
                eventMult *= e.multiplier;
            }
        }
        price *= eventMult;

        // Цикл рынка (бум/крах)
        if (marketCyclePhase == 1) price *= 1.3; // бум
        else if (marketCyclePhase == 2) price *= 0.7; // крах

        // Округляем до 2 знаков
        return Math.max(min, Math.round(price * 100.0) / 100.0);
    }

    /**
     * Цена ПОКУПКИ (игрок покупает у рынка).
     * Всегда дороже продажи на величину спреда.
     */
    public double getBuyPrice(String itemId) {
        double sellPrice = getCurrentPrice(itemId);
        double spread = plugin.getConfig().getDouble("settings.buy-spread", 0.20);

        if (plugin.getMarketFun() != null && plugin.getMarketFun().isFlashSaleActive(itemId)) {
            double discount = plugin.getMarketFun().getFlashSaleDiscount();
            sellPrice *= (1.0 - discount);
        }

        return Math.round(sellPrice * (1.0 + spread) * 100.0) / 100.0;
    }

    /**
     * Рассчитать стоимость покупки N предметов.
     * Каждый следующий предмет дороже (рост спроса).
     */
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

        for (int i = 0; i < amount; i++) {
            int virtualStock = currentStock - i; // Сток уменьшается с каждой покупкой
            double supplyMult = Math.pow(0.95, (double) virtualStock / elasticity);
            supplyMult = Math.max(0.1, Math.min(maxMult, supplyMult));
            double price = base * supplyMult;
            price *= getTrendMultiplier(itemId);
            price *= (1.0 + mom);
            // События
            double eventMult = 1.0;
            for (MarketEvent e : getActiveEvents()) {
                if (e.affects(itemId, null)) eventMult *= e.multiplier;
            }
            price *= eventMult;
            if (marketCyclePhase == 1) price *= 1.3;
            else if (marketCyclePhase == 2) price *= 0.7;
            price = Math.max(min, price);
            total += price * (1.0 + spread);
        }

        total = applyFlashSale(itemId, total);

        return Math.round(total * 100.0) / 100.0;
    }

    /** Применить Flash Sale скидку к цене. */
    private double applyFlashSale(String itemId, double price) {
        if (plugin.getMarketFun() != null && plugin.getMarketFun().isFlashSaleActive(itemId)) {
            price *= (1.0 - plugin.getMarketFun().getFlashSaleDiscount());
        }
        return price;
    }

    /**
     * Рассчитать выручку от продажи N предметов.
     * Каждый следующий предмет дешевле (рост предложения).
     */
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

        for (int i = 0; i < amount; i++) {
            int virtualStock = currentStock + i; // Сток увеличивается с каждой продажей
            double supplyMult = Math.pow(0.95, (double) virtualStock / elasticity);
            supplyMult = Math.max(0.1, Math.min(maxMult, supplyMult));
            double price = base * supplyMult;
            price *= getTrendMultiplier(itemId);
            price *= (1.0 + mom);
            // События
            double eventMult = 1.0;
            for (MarketEvent e : getActiveEvents()) {
                if (e.affects(itemId, null)) eventMult *= e.multiplier;
            }
            price *= eventMult;
            if (marketCyclePhase == 1) price *= 1.3;
            else if (marketCyclePhase == 2) price *= 0.7;
            price = Math.max(min, price);
            total += price * (1.0 - sellSpread);
        }

        return Math.round(total * 100.0) / 100.0;
    }

    // ========================================
    // ТОРГОВЫЕ ОПЕРАЦИИ
    // ========================================

    /**
     * Проверить, можно ли торговать (анти-спам).
     */
    public boolean canTrade(String itemId, Player player) {
        long cooldownMs = plugin.getConfig().getLong("settings.trade-cooldown-ms", 500);
        long now = System.currentTimeMillis();
        String key = itemId + ":" + player.getUniqueId();
        Long last = lastTradeTime.get(key);
        return last == null || now - last >= cooldownMs;
    }

    public void markTrade(String itemId, Player player) {
        String key = itemId + ":" + player.getUniqueId();
        lastTradeTime.put(key, System.currentTimeMillis());
    }

    /**
     * Продать предметы рынку.
     */
    public int sellItems(String itemId, int amount, double donorMultiplier) {
        int maxStock = plugin.getConfig().getInt("items." + itemId + ".max-stock", 5000);
        int current = stock.getOrDefault(itemId, 0);

        // Проверяем лимит стока
        int canAccept = Math.max(0, maxStock - current);
        int actualAmount = Math.min(amount, canAccept);

        if (actualAmount <= 0) return 0;

        stock.put(itemId, current + actualAmount);
        double totalPrice = calculateBulkSellPrice(itemId, actualAmount) * donorMultiplier;
        int repToGive = Math.max(1, (int) Math.round(totalPrice));

        // Обновляем моментум (продажа давит на цену)
        double mom = momentum.getOrDefault(itemId, 0.0);
        double tradeImpact = plugin.getConfig().getDouble("market2.trade-impact", 0.05);
        double impact = actualAmount * tradeImpact;
        momentum.put(itemId, Math.max(-2.0, mom - impact));

        recordTrade(itemId, actualAmount, "sell");
        return repToGive;
    }

    /**
     * Купить предметы у рынка.
     */
    public int buyItems(String itemId, int amount, double donorMultiplier) {
        int current = stock.getOrDefault(itemId, 0);
        int minStock = plugin.getConfig().getInt("items." + itemId + ".min-stock", -200);

        // Проверяем доступность
        int canProvide = Math.max(0, current - minStock);
        int actualAmount = Math.min(amount, canProvide);

        if (actualAmount <= 0) return -1; // Нет в наличии

        stock.put(itemId, current - actualAmount);
        double totalPrice = calculateBulkBuyPrice(itemId, actualAmount) * donorMultiplier;
        int repToCharge = Math.max(1, (int) Math.round(totalPrice));

        // Обновляем моментум (покупка толкает цену вверх)
        // Каждый предмет повышает цену на trade-impact %
        double mom = momentum.getOrDefault(itemId, 0.0);
        double tradeImpact = plugin.getConfig().getDouble("market2.trade-impact", 0.05);
        double impact = actualAmount * tradeImpact;
        momentum.put(itemId, Math.min(2.0, mom + impact));

        recordTrade(itemId, actualAmount, "buy");
        return repToCharge;
    }

    // ========================================
    // ВОССТАНОВЛЕНИЕ РЫНКА
    // ========================================

    /**
     * Постепенное восстановление рынка.
     * Сток стремится к 0 (равновесие).
     * Моментум затухает со временем.
     */
    public void recoverMarket() {
        if (!plugin.getConfig().contains("items")) return;

        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            int current = stock.getOrDefault(itemId, 0);
            if (current == 0) continue;

            int recoveryRate = plugin.getConfig().getInt("items." + itemId + ".recovery-rate", 5);
            double recoveryMultiplier = plugin.getConfig().getDouble("market2.recovery-multiplier", 1.0);

            int recovery = (int) Math.max(1, Math.round(recoveryRate * recoveryMultiplier));

            if (current > 0) {
                // Избыток → уменьшаем сток
                stock.put(itemId, Math.max(0, current - recovery));
            } else {
                // Дефицит → увеличиваем сток (но не выше 0)
                stock.put(itemId, Math.min(0, current + recovery));
            }

            // Затухание моментума
            double mom = momentum.getOrDefault(itemId, 0.0);
            if (Math.abs(mom) > 0.01) {
                momentum.put(itemId, mom * 0.9); // 10% затухание
            } else {
                momentum.remove(itemId);
            }
        }

        // Проверяем цикл рынка
        checkMarketCycle();

        priceHistoryTick++;
        if (priceHistoryTick % 10 == 0) {
            for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
                priceHistory.computeIfAbsent(itemId, k -> new java.util.ArrayList<>()).add(getCurrentPrice(itemId));
                if (priceHistory.get(itemId).size() > 200) priceHistory.get(itemId).remove(0);
            }
        }

        saveAll();
    }

    /**
     * Проверка и обновление рыночного цикла.
     * Рынок цикличен: бум → нормализация → крах → восстановление.
     */
    private void checkMarketCycle() {
        long now = System.currentTimeMillis();
        if (now < cycleEndTime) return;

        double cycleChance = plugin.getConfig().getDouble("market2.cycle.chance", 0.05);
        if (ThreadLocalRandom.current().nextDouble() >= cycleChance) return;

        // Определяем новую фазу
        int oldPhase = marketCyclePhase;
        if (marketCyclePhase == 0) {
            // Нормальная → бум или крах
            marketCyclePhase = ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        } else {
            // Возврат к норме
            marketCyclePhase = 0;
        }

        long durationMs = plugin.getConfig().getLong("market2.cycle.duration-ms", 1800000); // 30 мин
        cycleEndTime = now + durationMs;

        String msg;
        if (marketCyclePhase == 1) {
            msg = "📈 [Биржа] Экономический БУМ! Все цены +30%!";
        } else if (marketCyclePhase == 2) {
            msg = "📉 [Биржа] Рыночный КРАХ! Все цены -30%!";
        } else {
            msg = "➡ [Биржа] Рынок стабилизировался.";
        }

        org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + msg);
        addHistory("🔄 Цикл рынка: " + (marketCyclePhase == 1 ? "БУМ" : marketCyclePhase == 2 ? "КРАХ" : "НОРМА"));
    }

    // ========================================
    // ДНЕВНЫЕ ТРЕНДЫ
    // ========================================

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
            // Нормальное распределение вокруг 1.0
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

    // ========================================
    // РЫНОЧНЫЕ СОБЫТИЯ (РАСШИРЕННЫЕ)
    // ========================================

    public void checkForRandomEvent() {
        // Очищаем истёкшие события
        activeEvents.removeIf(e -> !e.isActive());

        // Проверяем, не слишком ли много событий
        int maxEvents = plugin.getConfig().getInt("market2.events.max-concurrent", 3);
        if (activeEvents.size() >= maxEvents) return;

        double eventChance = plugin.getConfig().getDouble("market2.events.chance", 0.15);
        if (ThreadLocalRandom.current().nextDouble() >= eventChance) return;

        if (plugin.getConfig().getConfigurationSection("items") == null) return;

        List<String> items = new ArrayList<>(plugin.getConfig().getConfigurationSection("items").getKeys(false));
        if (items.isEmpty()) return;

        // Выбираем случайный предмет для события
        String targetItem = items.get(ThreadLocalRandom.current().nextInt(items.size()));

        // Определяем тип события (расширенный список)
        int eventType = ThreadLocalRandom.current().nextInt(12);
        String msg;
        long duration;
        MarketEvent newEvent;

        switch (eventType) {
            case 0: // Дефицит
                newEvent = new MarketEvent("⚠️ Дефицит: " + getItemName(targetItem), targetItem, 2.5,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "🚨 [Биржа] " + newEvent.name + "! Спрос критический! Цена x2.5!";
                stock.put(targetItem, Math.max(-100, stock.getOrDefault(targetItem, 0) - 50));
                break;

            case 1: // Избыток
                newEvent = new MarketEvent("📦 Избыток: " + getItemName(targetItem), targetItem, 0.4,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "📉 [Биржа] " + newEvent.name + "! Рынок переполнен! Цена x0.4!";
                stock.put(targetItem, stock.getOrDefault(targetItem, 0) + 100);
                break;

            case 2: // Золотая лихорадка
                newEvent = new MarketEvent("⛏️ Золотая Лихорадка", "GOLD_INGOT", 3.0,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "🚨 [Биржа] Золотая Лихорадка! Скупщики платят x3 за золото!";
                stock.put("GOLD_INGOT", Math.max(-100, stock.getOrDefault("GOLD_INGOT", 0) - 30));
                break;

            case 3: // Энергетический кризис
                newEvent = new MarketEvent("🔌 Энергетический Кризис", "COAL", 4.0,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "🚨 [Биржа] Энергетический Кризис! Уголь в дефиците! Цена x4!";
                stock.put("COAL", Math.max(-100, stock.getOrDefault("COAL", 0) - 80));
                break;

            case 4: // Строительный бум
                newEvent = new MarketEvent("🏗 Строительный Бум", "ALL", 1.5,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "🏗 [Биржа] Строительный Бум! Все цены продажи +50%!";
                break;

            case 5: // Рыночный обвал
                newEvent = new MarketEvent("💥 Рыночный Обвал", "ALL", 0.5,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "💥 [Биржа] Рыночный Обвал! Все цены -50%!";
                break;

            case 6: // Алмазная лихорадка
                newEvent = new MarketEvent("💎 Алмазная Лихорадка", "DIAMOND", 2.8,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
                msg = "💎 [Биржа] Алмазная Лихорадка! Спрос на алмазы x2.8!";
                stock.put("DIAMOND", Math.max(-100, stock.getOrDefault("DIAMOND", 0) - 40));
                break;

            case 7: // Голод
                newEvent = new MarketEvent("🍞 Голод", "ALL", 1.8,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000), "еда");
                msg = "🍞 [Биржа] Голод! Цены на еду x1.8!";
                break;

            case 8: // Война (мобы дорожают)
                newEvent = new MarketEvent("⚔️ Война", "ALL", 2.0,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000), "мобы");
                msg = "⚔️ [Биржа] Война! Лут мобов в цене x2.0!";
                break;

            case 9: // Мирный договор
                newEvent = new MarketEvent("🕊 Мирный Договор", "ALL", 0.6,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000), "мобы");
                msg = "🕊 [Биржа] Мирный Договор! Лут мобов подешевел x0.6!";
                break;

            case 10: // Лесной пожар
                newEvent = new MarketEvent("🔥 Лесной Пожар", "ALL", 2.5,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000), "дерево");
                msg = "🔥 [Биржа] Лесной Пожар! Дерево в дефиците x2.5!";
                break;

            case 11: // Технологический прорыв
                newEvent = new MarketEvent("🔬 Технологический Прорыв", "REDSTONE", 3.5,
                        plugin.getConfig().getLong("market2.events.duration-ms", 600000));
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

    private boolean isEventActive(String itemId) {
        for (MarketEvent e : activeEvents) {
            if (e.isActive() && e.affects(itemId, null)) return true;
        }
        return false;
    }

    private String getItemName(String itemId) {
        return plugin.getConfig().getString("items." + itemId + ".name", itemId);
    }

    // ========================================
    // УТИЛИТЫ
    // ========================================

    public int getStock(String itemId) {
        return stock.getOrDefault(itemId, 0);
    }

    public int getMaxStock(String itemId) {
        return plugin.getConfig().getInt("items." + itemId + ".max-stock", 5000);
    }

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

    public java.util.List<String> getRotatedLimitedItems() {
        java.util.List<String> all = new java.util.ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("limited-items");
        if (sec != null) all.addAll(sec.getKeys(false));
        if (all.isEmpty()) return all;
        java.util.Collections.sort(all);

        int count = Math.max(1, plugin.getConfig().getInt("market2.limited-rotation.count", 2));
        if (!plugin.getConfig().getBoolean("market2.limited-rotation.enabled", true)) return all;

        java.util.List<String> result = new java.util.ArrayList<>();
        int seed = Math.abs(today().hashCode());
        for (int i = 0; i < Math.min(count, all.size()); i++) {
            result.add(all.get((seed + i * 2) % all.size()));
        }
        return result;
    }

    public String economyAuditLine() {
        ensureDailyTrends();
        int hot = 0, cheap = 0, deficit = 0;
        for (Map.Entry<String, Double> entry : dailyTrends.entrySet()) {
            if (entry.getValue() >= 1.3) hot++;
            if (entry.getValue() <= 0.7) cheap++;
        }
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            if (entry.getValue() < 0) deficit++;
        }
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
        recordToTransactionLog(line);
    }

    private void recordToTransactionLog(String line) {
        java.io.File logFile = new java.io.File(plugin.getDataFolder(), "transactions.log");
        try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
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

    private String today() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    }

    public void cleanupCooldowns() {
        long cutoff = System.currentTimeMillis() - 60000;
        lastTradeTime.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    /**
     * FIX: Сохранение priceHistory на диск.
     */
    private void savePriceHistory() {
        int maxPoints = plugin.getConfig().getInt("market2.price-history.max-points", 100);
        data.set("price_history", null);
        for (Map.Entry<String, java.util.List<Double>> entry : priceHistory.entrySet()) {
            java.util.List<Double> list = entry.getValue();
            if (list.size() > maxPoints) {
                list = list.subList(list.size() - maxPoints, list.size());
            }
            data.set("price_history." + entry.getKey(), list);
        }
    }

    /**
     * FIX: Загрузка priceHistory с диска.
     */
    private void loadPriceHistory() {
        if (!data.contains("price_history")) return;
        ConfigurationSection sec = data.getConfigurationSection("price_history");
        if (sec == null) return;
        for (String itemId : sec.getKeys(false)) {
            java.util.List<Double> list = data.getDoubleList("price_history." + itemId);
            if (!list.isEmpty()) {
                priceHistory.put(itemId, new java.util.ArrayList<>(list));
            }
        }
    }

    /**
     * FIX: Getter для cyclePhase (нужен PriceEngine).
     */
    public int getMarketCyclePhase() {
        return marketCyclePhase;
    }

    /**
     * FIX: Getter для dailyTrend конкретного предмета.
     */
    public double getDailyTrend(String itemId) {
        ensureDailyTrends();
        return dailyTrends.getOrDefault(itemId, 1.0);
    }
}
