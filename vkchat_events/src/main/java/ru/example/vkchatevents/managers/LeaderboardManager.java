package ru.example.vkchatevents.managers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [11] Таблицы лидеров событий
 */
public class LeaderboardManager {
    private final Map<String, Map<UUID, Integer>> leaderboards = new ConcurrentHashMap<>();

    public LeaderboardManager(ru.example.vkchatevents.VKChatEventsPlugin plugin) {}

    public void addScore(String board, UUID uuid, int amount) {
        leaderboards.computeIfAbsent(board, k -> new ConcurrentHashMap<>()).merge(uuid, amount, Integer::sum);
    }

    public int getScore(String board, UUID uuid) {
        return leaderboards.getOrDefault(board, Collections.emptyMap()).getOrDefault(uuid, 0);
    }

    public List<Map.Entry<UUID, Integer>> getTop(String boardName, int limit) {
        Map<UUID, Integer> boardData = leaderboards.getOrDefault(boardName, Collections.emptyMap());
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(boardData.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
