package ru.example.vkchatevents.managers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [14] Комбо-система — бонусы за последовательные действия
 */
public class ComboManager {
    private final Map<UUID, Map<String, Integer>> combos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastComboTime = new ConcurrentHashMap<>();
    private static final long COMBO_TIMEOUT = 60000; // 1 минута

    public ComboManager(ru.example.vkchatevents.VKChatEventsPlugin plugin) {}

    public int addCombo(UUID uuid, String type) {
        long now = System.currentTimeMillis();
        Long last = lastComboTime.get(uuid);

        if (last != null && now - last > COMBO_TIMEOUT) {
            combos.remove(uuid); // Сброс комбо
        }

        lastComboTime.put(uuid, now);
        Map<String, Integer> playerCombos = combos.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        return playerCombos.merge(type, 1, Integer::sum);
    }

    public int getCombo(UUID uuid, String type) {
        return combos.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(type, 0);
    }

    public double getComboMultiplier(UUID uuid, String type) {
        int combo = getCombo(uuid, type);
        return 1.0 + (combo * 0.1); // +10% за каждый комбо
    }

    public void resetCombo(UUID uuid, String type) {
        Map<String, Integer> playerCombos = combos.get(uuid);
        if (playerCombos != null) playerCombos.remove(type);
    }
}
