package ru.example.vkchatevents.managers;

import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [10] Достижения событий
 */
public class AchievementManager {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Set<String>> achievements = new ConcurrentHashMap<>();

    // Достижения
    private static final String[][] ACHIEVEMENTS = {
        {"first_kill", "Первое убийство", "100"},
        {"kill_100", "Серийный убийца", "500"},
        {"kill_1000", "Массовый убийца", "2000"},
        {"first_craft", "Первый крафт", "100"},
        {"craft_100", "Мастер-ремесленник", "500"},
        {"first_boss", "Убийца боссов", "1000"},
        {"boss_10", "Охотник на боссов", "3000"},
        {"first_rift", "Разрушитель разломов", "500"},
        {"rift_10", "Мастер разломов", "2000"},
        {"daily_7", "Недельная серия", "300"},
        {"daily_30", "Месячная серия", "1000"},
        {"bounty_hunter", "Охотник за головами", "500"},
        {"quest_master", "Мастер квестов", "1500"},
        {"event_king", "Король событий", "5000"},
        {"lucky_devil", "Везунчик", "200"},
        {"survivor", "Выживший", "800"},
        {"explorer", "Исследователь", "600"},
        {"collector", "Коллекционер", "400"},
        {"trader", "Торговец", "300"},
        {"helper", "Помощник", "200"},
    };

    public AchievementManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean unlock(UUID uuid, String achievement) {
        achievements.putIfAbsent(uuid, ConcurrentHashMap.newKeySet());
        return achievements.get(uuid).add(achievement);
    }

    public boolean hasAchievement(UUID uuid, String achievement) {
        return achievements.getOrDefault(uuid, Collections.emptySet()).contains(achievement);
    }

    public Set<String> getAchievements(UUID uuid) {
        return achievements.getOrDefault(uuid, Collections.emptySet());
    }

    public String[][] getAllAchievements() { return ACHIEVEMENTS; }
}
