package ru.example.vkchatevents.managers;

import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [9] Магазин событий — покупка бонусов за очки событий
 */
public class EventShopManager {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Integer> eventPoints = new ConcurrentHashMap<>();

    // Товары магазина
    private static final String[][] SHOP_ITEMS = {
        {"Буст XP x2", "xp_boost", "200", "600"},      // 200 очков, 10 мин
        {"Буст лута x2", "loot_boost", "300", "600"},
        {"Буст репу x2", "rep_boost", "400", "600"},
        {"Телепорт к боссу", "boss_tp", "100", "0"},
        {"Исцеление", "heal", "50", "0"},
        {"Супер-броня (5 мин)", "armor_boost", "500", "300"},
        {"Невидимость (3 мин)", "invisibility", "250", "180"},
        {"Скорость x2 (5 мин)", "speed_boost", "150", "300"},
    };

    public EventShopManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void addPoints(UUID uuid, int amount) {
        eventPoints.merge(uuid, amount, Integer::sum);
    }

    public int getPoints(UUID uuid) {
        return eventPoints.getOrDefault(uuid, 0);
    }

    public boolean purchase(UUID uuid, String itemId) {
        for (String[] item : SHOP_ITEMS) {
            if (item[1].equals(itemId)) {
                int cost = Integer.parseInt(item[2]);
                int points = eventPoints.getOrDefault(uuid, 0);
                if (points >= cost) {
                    eventPoints.put(uuid, points - cost);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public String[][] getShopItems() { return SHOP_ITEMS; }
}
