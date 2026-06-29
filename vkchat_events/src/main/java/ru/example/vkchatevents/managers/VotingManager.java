package ru.example.vkchatevents.managers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [13] Голосование за события
 */
public class VotingManager {
    private final Map<String, Map<UUID, String>> activeVotes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> voteResults = new ConcurrentHashMap<>();

    public VotingManager(ru.example.vkchatevents.VKChatEventsPlugin plugin) {}

    public boolean createVote(String voteId, String[] options) {
        if (activeVotes.containsKey(voteId)) return false;
        activeVotes.put(voteId, new ConcurrentHashMap<>());
        Map<String, Integer> results = new ConcurrentHashMap<>();
        for (String opt : options) results.put(opt, 0);
        voteResults.put(voteId, results);
        return true;
    }

    public boolean vote(String voteId, UUID uuid, String option) {
        Map<UUID, String> votes = activeVotes.get(voteId);
        if (votes == null) return false;
        if (votes.containsKey(uuid)) return false; // Уже голосовал
        votes.put(uuid, option);
        voteResults.get(voteId).merge(option, 1, Integer::sum);
        return true;
    }

    public Map<String, Integer> getResults(String voteId) {
        return voteResults.getOrDefault(voteId, Collections.emptyMap());
    }

    public String getWinner(String voteId) {
        Map<String, Integer> results = getResults(voteId);
        return results.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
