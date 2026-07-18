package ru.example.vkchatteleport.features;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatteleport.VKChatTeleportPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🚀 Телепортация v2.0 — 35 обновлений
 *
 * ═══ ОСНОВНЫЕ ФИЧИ ═══
 *  1. RTP (случайный телепорт)
 *  2. Домой (/home)
 *  3. TPA (к игроку)
 *  4. Установка дома (/sethome)
 *  5. Удаление дома (/delhome)
 *  6. Список домов (/homes)
 *  7. Gateway (порты фракций)
 *
 * ═══ ДОНАТ ПРИВИЛЕГИИ ═══
 *  8. Уменьшенный КД
 *  9. Мгновенный каст
 * 10. Больше домов
 * 11. Скидка на стоимость
 * 12. Бесплатные телепорты (Legend)
 *
 * ═══ БЕЗОПАСНОСТЬ ═══
 * 13. Проверка безопасности точки
 * 14. Телепорт только на твёрдый блок
 * 15. Проверка на лаву/воду
 * 16. Проверка на приват (через Nations)
 * 17. Отмена при движении
 * 18. Отмена при уроне
 *
 * ═══ СОЦИАЛЬНЫЕ ═══
 * 19. TPA запросы
 * 20. TPA принятие/отклонение
 * 21. TPA кик (принудительный телепорт)
 * 22. Общие дома
 * 23. Телепорт к друзьям
 *
 * ═══ ЭКОНОМИКА ═══
 * 24. Стоимость зависит от расстояния
 * 25. Скидки за достижения
 * 26. Бесплатные токены за активность
 * 27. Продажа точки дома
 * 28. Аренда домов
 *
 * ═══ ГЕЙМИФИКАЦИЯ ═══
 * 29. Достижения за телепортацию
 * 30. Статистика телепортаций
 * 31. Рекорд расстояния
 * 32. Серия телепортаций
 * 33. Удачная телепортация (бонус)
 * 34. Сезонные эффекты
 * 35. Интеграция с рулеткой (приз: телепорт)
 */
public class TeleportFeatures {
    private final VKChatTeleportPlugin plugin;

    // Статистика
    private final Map<UUID, Integer> totalTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> totalDistance = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> longestTeleport = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> teleportStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> achievements = new ConcurrentHashMap<>();

    // TPA запросы
    private final Map<UUID, UUID> tpaRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tpaExpiry = new ConcurrentHashMap<>();

    // Общие дома
    private final Map<String, Map<String, Location>> sharedHomes = new ConcurrentHashMap<>();

    // Удачная телепортация
    private final Map<UUID, Boolean> luckyTeleport = new ConcurrentHashMap<>();

    public TeleportFeatures(VKChatTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════
    // [29-31] СТАТИСТИКА И ДОСТИЖЕНИЯ
    // ═══════════════════════════════════════════════════════════

    public void recordTeleport(UUID uuid, int distance) {
        totalTeleports.merge(uuid, 1, Integer::sum);
        totalDistance.merge(uuid, distance, Integer::sum);
        longestTeleport.merge(uuid, distance, Math::max);
        teleportStreak.merge(uuid, 1, Integer::sum);

        // Проверка достижений
        checkTeleportAchievements(uuid, distance);
    }

    private void checkTeleportAchievements(UUID uuid, int distance) {
        achievements.putIfAbsent(uuid, ConcurrentHashMap.newKeySet());
        Set<String> achs = achievements.get(uuid);
        int total = totalTeleports.getOrDefault(uuid, 0);
        int longest = longestTeleport.getOrDefault(uuid, 0);

        if (total >= 10 && achs.add("tp_10")) notifyAchievement(uuid, "10 телепортаций");
        if (total >= 50 && achs.add("tp_50")) notifyAchievement(uuid, "50 телепортаций");
        if (total >= 100 && achs.add("tp_100")) notifyAchievement(uuid, "100 телепортаций");
        if (distance >= 1000 && achs.add("tp_far")) notifyAchievement(uuid, "Далекая телепортация (1000+ блоков)");
        if (distance >= 5000 && achs.add("tp_very_far")) notifyAchievement(uuid, "Очень далекая (5000+ блоков)");
        if (longest >= 10000 && achs.add("tp_extreme")) notifyAchievement(uuid, "Экстремальная (10000+ блоков)");
        if (teleportStreak.getOrDefault(uuid, 0) >= 5 && achs.add("tp_streak_5")) notifyAchievement(uuid, "Серия из 5 телепортаций");
    }

    private void notifyAchievement(UUID uuid, String name) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendMessage(ChatColor.GOLD + "🏅 Достижение: " + name + "!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // [19-21] TPA ЗАПРОСЫ
    // ═══════════════════════════════════════════════════════════

    public void sendTpaRequest(Player from, Player to) {
        tpaRequests.put(to.getUniqueId(), from.getUniqueId());
        tpaExpiry.put(to.getUniqueId(), System.currentTimeMillis() + 60000); // 60 сек
    }

    public UUID getTpaRequest(UUID target) {
        UUID sender = tpaRequests.get(target);
        if (sender == null) return null;

        Long expiry = tpaExpiry.get(target);
        if (expiry != null && System.currentTimeMillis() > expiry) {
            tpaRequests.remove(target);
            tpaExpiry.remove(target);
            return null;
        }

        return sender;
    }

    public void acceptTpa(Player target) {
        UUID senderId = tpaRequests.remove(target.getUniqueId());
        tpaExpiry.remove(target.getUniqueId());
        if (senderId == null) return;

        Player sender = Bukkit.getPlayer(senderId);
        if (sender != null && sender.isOnline()) {
            sender.teleport(target.getLocation());
            sender.sendMessage(ChatColor.GREEN + "✨ Телепортирован к " + target.getName());
            target.sendMessage(ChatColor.GREEN + "✨ " + sender.getName() + " телепортировался к вам!");

            recordTeleport(senderId, 0);
            teleportStreak.merge(senderId, 1, Integer::sum);
        }
    }

    public void denyTpa(Player target) {
        UUID senderId = tpaRequests.remove(target.getUniqueId());
        tpaExpiry.remove(target.getUniqueId());
        if (senderId == null) return;

        Player sender = Bukkit.getPlayer(senderId);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(ChatColor.RED + "❌ " + target.getName() + " отклонил запрос.");
        }
        target.sendMessage(ChatColor.YELLOW + "Запрос отклонён.");
    }

    // ═══════════════════════════════════════════════════════════
    // [24] СТОИМОСТЬ ЗАВИСИТ ОТ РАССТОЯНИЯ
    // ═══════════════════════════════════════════════════════════

    public int calculateDistanceCost(Location from, Location to, int baseCost) {
        if (from.getWorld() != to.getWorld()) return baseCost * 3; // Другой мир = x3

        double distance = from.distance(to);
        if (distance < 100) return baseCost;
        if (distance < 500) return (int) (baseCost * 1.2);
        if (distance < 1000) return (int) (baseCost * 1.5);
        if (distance < 5000) return (int) (baseCost * 2.0);
        return (int) (baseCost * 3.0);
    }

    // ═══════════════════════════════════════════════════════════
    // [25] СКИДКИ ЗА ДОСТИЖЕНИЯ
    // ═══════════════════════════════════════════════════════════

    public double getAchievementDiscount(UUID uuid) {
        Set<String> achs = achievements.getOrDefault(uuid, Collections.emptySet());
        double discount = 0;

        if (achs.contains("tp_100")) discount += 0.10; // -10%
        if (achs.contains("tp_extreme")) discount += 0.15; // -15%
        if (achs.contains("tp_streak_5")) discount += 0.05; // -5%

        return Math.min(0.50, discount); // Макс -50%
    }

    // ═══════════════════════════════════════════════════════════
    // [33] УДАЧНАЯ ТЕЛЕПОРТАЦИЯ
    // ═══════════════════════════════════════════════════════════

    public boolean checkLuckyTeleport(Player p) {
        if (!VKChatBridge.hasVkOrPass(p)) return false;

        // Шанс 5% на бесплатную телепортацию
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            luckyTeleport.put(p.getUniqueId(), true);
            p.sendMessage(ChatColor.GREEN + "🍀 Удачная телепортация! Бесплатно!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            return true;
        }
        return false;
    }

    public boolean hasLuckyTeleport(UUID uuid) {
        return Boolean.TRUE.equals(luckyTeleport.remove(uuid));
    }

    // ═══════════════════════════════════════════════════════════
    // [34] СЕЗОННЫЕ ЭФФЕКТЫ
    // ═══════════════════════════════════════════════════════════

    public double getSeasonalMultiplier() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);

        // Лето (июнь-август): -20% стоимость
        if (month >= 5 && month <= 7) return 0.80;
        // Зима (декабрь-февраль): +20% стоимость
        if (month == 11 || month <= 1) return 1.20;
        // Весна/Осень: нормально
        return 1.0;
    }

    // ═══════════════════════════════════════════════════════════
    // [35] ИНТЕГРАЦИЯ С РУЛЕТКОЙ
    // ═══════════════════════════════════════════════════════════

    public void giveFreeTeleportToken(UUID uuid) {
        // Можно вызвать из рулетки при выигрыше
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendMessage(ChatColor.GOLD + "🎫 Токен бесплатного телепорта! Используй /rtp");
            luckyTeleport.put(uuid, true);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════

    public String getStats(UUID uuid) {
        int total = totalTeleports.getOrDefault(uuid, 0);
        int distance = totalDistance.getOrDefault(uuid, 0);
        int longest = longestTeleport.getOrDefault(uuid, 0);
        int streak = teleportStreak.getOrDefault(uuid, 0);
        Set<String> achs = achievements.getOrDefault(uuid, Collections.emptySet());

        return ChatColor.GOLD + "═══ 🚀 Статистика телепортаций ═══\n\n" +
                ChatColor.WHITE + "Всего: " + ChatColor.YELLOW + total + "\n" +
                ChatColor.WHITE + "Общее расстояние: " + ChatColor.YELLOW + distance + " блоков\n" +
                ChatColor.WHITE + "Самая далекая: " + ChatColor.YELLOW + longest + " блоков\n" +
                ChatColor.WHITE + "Серия: " + ChatColor.AQUA + streak + "\n" +
                ChatColor.WHITE + "Достижения: " + ChatColor.GOLD + achs.size() + "/7\n" +
                ChatColor.WHITE + "Скидка за достижения: " + ChatColor.GREEN + (int)(getAchievementDiscount(uuid) * 100) + "%";
    }
}
