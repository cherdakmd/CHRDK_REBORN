package ru.example.vkchatmarket.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class MarketManager {
    private final VKChatMarketPlugin plugin;
    private File file;
    private FileConfiguration data;
    
    // Хранит количество проданных предметов (сток). Чем больше сток - тем ниже цена.
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final Map<String, Double> dailyTrends = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyVolume = new ConcurrentHashMap<>();
    private final java.util.List<String> history = new CopyOnWriteArrayList<>();
    private String trendDate = "";

    // Экономические кризисы и события
    private String activeEventName = null;
    private String activeEventItemId = null;
    private double activeEventMultiplier = 1.0;
    private long activeEventExpireTime = 0L;

    public String getActiveEventName() { return activeEventName; }
    public String getActiveEventItemId() { return activeEventItemId; }
    public double getActiveEventMultiplier() { return activeEventMultiplier; }
    public long getActiveEventExpireTime() { return activeEventExpireTime; }

    public MarketManager(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "market_data.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);

        if (data.contains("stock")) {
            for (String item : data.getConfigurationSection("stock").getKeys(false)) {
                stock.put(item, data.getInt("stock." + item));
            }
        }
        trendDate = data.getString("daily.date", today());
        if (data.contains("daily.trends") && data.getConfigurationSection("daily.trends") != null) {
            for (String item : data.getConfigurationSection("daily.trends").getKeys(false)) {
                dailyTrends.put(item, data.getDouble("daily.trends." + item, 1.0));
            }
        }
        if (data.contains("daily.volume") && data.getConfigurationSection("daily.volume") != null) {
            for (String item : data.getConfigurationSection("daily.volume").getKeys(false)) {
                dailyVolume.put(item, data.getInt("daily.volume." + item, 0));
            }
        }
        history.addAll(data.getStringList("history"));
        ensureDailyTrends();
    }

    public void saveAll() {
        data.set("stock", null);
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            data.set("stock." + entry.getKey(), entry.getValue());
        }
        data.set("daily.date", trendDate);
        data.set("daily.trends", null);
        for (Map.Entry<String, Double> entry : dailyTrends.entrySet()) data.set("daily.trends." + entry.getKey(), entry.getValue());
        data.set("daily.volume", null);
        for (Map.Entry<String, Integer> entry : dailyVolume.entrySet()) data.set("daily.volume." + entry.getKey(), entry.getValue());
        data.set("history", history.subList(Math.max(0, history.size() - 80), history.size()));
        try { data.save(file); } catch (IOException ignored) {}
    }

    public int getStock(String itemId) {
        return stock.getOrDefault(itemId, 0);
    }

    public double getPriceDeltaPercent(String itemId) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        if (base <= 0) return 0;
        return ((getCurrentPrice(itemId) - base) / base) * 100.0;
    }

    public double getCurrentPrice(String itemId) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double drop = plugin.getConfig().getDouble("items." + itemId + ".drop-per-item", 0.01);
        int currentStock = stock.getOrDefault(itemId, 0);
        
        double price = base - (currentStock * drop);
        price = Math.max(min, Math.min(base * 4.0, price)); // Защита от дефолта / космических цен
        price *= getTrendMultiplier(itemId);
        
        if ((itemId.equals(activeEventItemId) || "ALL".equals(activeEventItemId)) && System.currentTimeMillis() < activeEventExpireTime) {
            price *= activeEventMultiplier;
        }
        return price;
    }

    public double calculateBulkPrice(String itemId, int amount) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double drop = plugin.getConfig().getDouble("items." + itemId + ".drop-per-item", 0.01);
        int currentStock = stock.getOrDefault(itemId, 0);
        
        double total = 0;
        for (int i = 0; i < amount; i++) {
            double price = base - ((currentStock + i) * drop);
            price = Math.max(min, Math.min(base * 4.0, price));
            price *= getTrendMultiplier(itemId);
            
            if ((itemId.equals(activeEventItemId) || "ALL".equals(activeEventItemId)) && System.currentTimeMillis() < activeEventExpireTime) {
                price *= activeEventMultiplier;
            }
            total += price;
        }
        return total;
    }

    public double calculateBulkBuyPrice(String itemId, int amount) {
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double min = plugin.getConfig().getDouble("items." + itemId + ".min-price", 1.0);
        double drop = plugin.getConfig().getDouble("items." + itemId + ".drop-per-item", 0.01);
        int currentStock = stock.getOrDefault(itemId, 0);
        
        double total = 0;
        for (int i = 0; i < amount; i++) {
            int virtualStock = currentStock - i;
            double price = base - (virtualStock * drop);
            price = Math.max(min, Math.min(base * 4.0, price));
            price *= getTrendMultiplier(itemId);
            
            if ((itemId.equals(activeEventItemId) || "ALL".equals(activeEventItemId)) && System.currentTimeMillis() < activeEventExpireTime) {
                price *= activeEventMultiplier;
            }
            // Спред/комиссия +15% для защиты от вечной накрутки
            total += price * 1.15;
        }
        return total;
    }

    public void addStock(String itemId, int amount) {
        // Лимит максимального стока для защиты от гиперинфляции (максимум 5000 предметов)
        int current = stock.getOrDefault(itemId, 0);
        stock.put(itemId, Math.min(5000, current + amount));
        recordTrade(itemId, amount, "sell");
    }

    public void removeStock(String itemId, int amount) {
        // Лимит минимального стока для защиты от космических цен (минимум -1000)
        int current = stock.getOrDefault(itemId, 0);
        stock.put(itemId, Math.max(-1000, current - amount));
        recordTrade(itemId, amount, "buy");
    }


    private String today() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    }

    public synchronized void ensureDailyTrends() {
        String now = today();
        if (now.equals(trendDate) && !dailyTrends.isEmpty()) return;
        trendDate = now;
        dailyTrends.clear();
        dailyVolume.clear();
        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        double min = plugin.getConfig().getDouble("market2.trends.min-multiplier", 0.75);
        double max = plugin.getConfig().getDouble("market2.trends.max-multiplier", 1.35);
        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            double roll = min + ThreadLocalRandom.current().nextDouble() * Math.max(0.01, max - min);
            dailyTrends.put(itemId, Math.round(roll * 100.0) / 100.0);
        }
        addHistory("Новый торговый день: обновлены тренды цен.");
        saveAll();
    }

    public double getTrendMultiplier(String itemId) {
        ensureDailyTrends();
        return dailyTrends.getOrDefault(itemId, 1.0);
    }

    public String getTrendLabel(String itemId) {
        double m = getTrendMultiplier(itemId);
        if (m >= 1.25) return "ажиотаж дня x" + String.format(java.util.Locale.US, "%.2f", m);
        if (m >= 1.08) return "спрос дня x" + String.format(java.util.Locale.US, "%.2f", m);
        if (m <= 0.82) return "переизбыток дня x" + String.format(java.util.Locale.US, "%.2f", m);
        if (m <= 0.95) return "скидка дня x" + String.format(java.util.Locale.US, "%.2f", m);
        return "стабильно x" + String.format(java.util.Locale.US, "%.2f", m);
    }

    public int getDailyVolume(String itemId) {
        ensureDailyTrends();
        return dailyVolume.getOrDefault(itemId, 0);
    }

    public void recordTrade(String itemId, int amount, String type) {
        ensureDailyTrends();
        dailyVolume.put(itemId, dailyVolume.getOrDefault(itemId, 0) + Math.max(0, amount));
        if (amount >= plugin.getConfig().getInt("market2.history.min-volume-log", 64)) {
            addHistory(type + " " + itemId + " x" + amount + " trend=" + getTrendLabel(itemId));
        }
    }

    private void addHistory(String line) {
        String stamp = new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date());
        history.add(stamp + " — " + line);
        while (history.size() > 80) history.remove(0);
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
        if (plugin.getConfig().getConfigurationSection("limited-items") != null) all.addAll(plugin.getConfig().getConfigurationSection("limited-items").getKeys(false));
        if (all.isEmpty()) return all;
        java.util.Collections.sort(all);
        int count = Math.max(1, plugin.getConfig().getInt("market2.limited-rotation.count", Math.min(2, all.size())));
        if (!plugin.getConfig().getBoolean("market2.limited-rotation.enabled", true)) return all;
        java.util.List<String> result = new java.util.ArrayList<>();
        int seed = Math.abs(today().hashCode());
        for (int i = 0; i < Math.min(count, all.size()); i++) result.add(all.get((seed + i * 2) % all.size()));
        return result;
    }

    public String economyAuditLine() {
        ensureDailyTrends();
        int hot = 0, cheap = 0;
        for (double m : dailyTrends.values()) { if (m >= 1.2) hot++; if (m <= 0.85) cheap++; }
        return "Горячих товаров: " + hot + ", переизбыток: " + cheap + ", записей истории: " + history.size();
    }

    public void checkForRandomEvent() {
        if (System.currentTimeMillis() < activeEventExpireTime) {
            return; // Предыдущее событие еще активно
        }
        
        // Шанс 20% на новое событие каждые 30 минут
        if (ThreadLocalRandom.current().nextInt(100) >= 20) {
            return;
        }
        
        if (plugin.getConfig().getConfigurationSection("items") == null) return;
        
        int eventRoll = ThreadLocalRandom.current().nextInt(16);
        String msg = "";
        
        switch (eventRoll) {
            case 0: // Gold Rush
                activeEventName = "⛏️ Золотая Лихорадка";
                activeEventItemId = "GOLD_INGOT";
                activeEventMultiplier = 3.0;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Скупщики готовы платить тройную цену за золото! Цена GOLD_INGOT умножена на x3.0!";
                break;
                
            case 1: // Iron Shortage
                activeEventName = "⚔️ Железный Дефицит";
                activeEventItemId = "IRON_INGOT";
                activeEventMultiplier = 2.5;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Нациям срочно требуется железо для ковки оружия! Цена IRON_INGOT умножена на x2.5!";
                break;
                
            case 2: // Construction Boom
                activeEventName = "🏡 Строительный Бум";
                activeEventItemId = "OAK_LOG";
                activeEventMultiplier = 3.0;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Великая застройка городов! Цена OAK_LOG поднялась в x3.0 раза!";
                break;
                
            case 3: // Plague Outbreak
                activeEventName = "🧟 Вспышка Чумы";
                activeEventItemId = "ROTTEN_FLESH";
                activeEventMultiplier = 6.0;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Алхимикам срочно требуется гнилая плоть для сыворотки! Цена ROTTEN_FLESH выросла в x6.0 раз!";
                break;
                
            case 4: // Diamond Surplus
                activeEventName = "💎 Алмазный Профицит";
                activeEventItemId = "DIAMOND";
                activeEventMultiplier = 0.3;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Открыто гигантское алмазное месторождение! Цена DIAMOND обрушилась до x0.3!";
                break;
                
            case 5: // Forest Hurricane
                activeEventName = "🌪️ Лесной Ураган";
                activeEventItemId = "OAK_LOG";
                activeEventMultiplier = 4.0;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Ураган повалил вековые дубы, бревна в жутком дефиците! Цена OAK_LOG умножена на x4.0!";
                break;
                
            case 6: // Tax Holiday / Market Boom
                activeEventName = "🪙 Королевская Ярмарка";
                activeEventItemId = "ALL"; // Применяется ко всем ценам!
                activeEventMultiplier = 1.5;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Королевская ярмарка снизила налоги! Цены продажи ВСЕХ ресурсов выросли на +50%!";
                break;

            case 7: // Gold Import Ban (Embargo)
                activeEventName = "🚫 Санкции на Золото";
                activeEventItemId = "GOLD_INGOT";
                activeEventMultiplier = 0.2;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Торговая палата ввела запрет на импорт золота! Цена продажи GOLD_INGOT упала до x0.2!";
                break;

            case 8: // Netherite Embargo
                activeEventName = "🌋 Незеритовое Эмбарго";
                activeEventItemId = "NETHERITE_INGOT";
                activeEventMultiplier = 3.0;
                activeEventExpireTime = System.currentTimeMillis() + 900000L; // 15 минут
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Полное эмбарго на незеритовую продукцию! Спрос критический! Цена NETHERITE_INGOT взлетела до x3.0!";
                break;

            case 9: // Coal Deficit (Energy Crisis)
                activeEventName = "🔌 Энергетический Кризис";
                activeEventItemId = "COAL";
                activeEventMultiplier = 3.5;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] НОВОСТИ: " + activeEventName + "! Великий дефицит угля! Цена COAL выросла на x3.5!";
                break;
            case 10:
                activeEventName = "🍞 Голодная Неделя";
                activeEventItemId = "BREAD";
                activeEventMultiplier = 2.8;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] " + activeEventName + "! Еда дорожает: BREAD x2.8!";
                break;
            case 11:
                activeEventName = "🧱 Великая Стройка";
                activeEventItemId = "STONE";
                activeEventMultiplier = 2.7;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] " + activeEventName + "! Камень нужен строителям: STONE x2.7!";
                break;
            case 12:
                activeEventName = "🎨 Фестиваль Красок";
                activeEventItemId = "WHITE_WOOL";
                activeEventMultiplier = 2.4;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] " + activeEventName + "! Декор и шерсть в спросе: WHITE_WOOL x2.4!";
                break;
            case 13:
                activeEventName = "💥 Пороховой Заказ";
                activeEventItemId = "GUNPOWDER";
                activeEventMultiplier = 3.2;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] " + activeEventName + "! Порох нужен срочно: GUNPOWDER x3.2!";
                break;
            case 14:
                activeEventName = "🌲 Лесной Переизбыток";
                activeEventItemId = "OAK_LOG";
                activeEventMultiplier = 0.45;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] " + activeEventName + "! Дерево подешевело: OAK_LOG x0.45!";
                break;
            case 15:
                activeEventName = "📦 Большая Ярмарка";
                activeEventItemId = "ALL";
                activeEventMultiplier = 1.25;
                activeEventExpireTime = System.currentTimeMillis() + 900000L;
                msg = "🚨 [Биржа] " + activeEventName + "! Все цены продажи +25%!";
                break;
        }
        
        org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + msg);
        addHistory("Событие рынка: " + activeEventName + " item=" + activeEventItemId + " x" + activeEventMultiplier);
        saveAll();
    }

    public void recoverMarket() {
        if (!plugin.getConfig().contains("items")) return;
        
        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            int currentStock = stock.getOrDefault(itemId, 0);
            if (currentStock > 0) {
                int recoverAmount = plugin.getConfig().getInt("items." + itemId + ".recovery-amount", 50);
                int newStock = Math.max(0, currentStock - recoverAmount);
                stock.put(itemId, newStock);
            }
        }
        saveAll();
    }
}
