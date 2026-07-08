package ru.example.vkchatevents.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [26-30] Дипломатия, Шпионаж, Революция, Эволюция, Мутация
 */
public class SocialManager implements Listener {
    private final VKChatEventsPlugin plugin;

    // Дипломатия
    private final Map<UUID, Map<UUID, String>> relations = new ConcurrentHashMap<>();
    // Шпионаж
    private final Map<UUID, Integer> spyCount = new ConcurrentHashMap<>();
    // Революция
    private final Map<UUID, Integer> revolutionPoints = new ConcurrentHashMap<>();
    // Эволюция
    private final Map<UUID, Integer> evolutionLevel = new ConcurrentHashMap<>();
    // Мутация
    private final Map<UUID, Set<String>> mutations = new ConcurrentHashMap<>();

    public SocialManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    // Дипломатия
    public void setRelation(UUID from, UUID to, String relation) {
        relations.computeIfAbsent(from, k -> new ConcurrentHashMap<>()).put(to, relation);
    }

    public String getRelation(UUID from, UUID to) {
        return relations.getOrDefault(from, Collections.emptyMap()).get(to);
    }

    // Шпионаж
    public void recordSpy(UUID uuid) {
        spyCount.merge(uuid, 1, Integer::sum);
    }

    // Революция
    public void addRevolutionPoints(UUID uuid, int points) {
        revolutionPoints.merge(uuid, points, Integer::sum);
    }

    // Эволюция
    public void evolve(UUID uuid) {
        evolutionLevel.merge(uuid, 1, Integer::sum);
    }

    public int getEvolutionLevel(UUID uuid) {
        return evolutionLevel.getOrDefault(uuid, 1);
    }

    // Мутация
    public void addMutation(UUID uuid, String mutation) {
        Set<String> muts = mutations.get(uuid);
        if (muts == null) {
            muts = new HashSet<>();
            mutations.put(uuid, muts);
        }
        muts.add(mutation);
    }

    public Set<String> getMutations(UUID uuid) {
        return mutations.getOrDefault(uuid, Collections.emptySet());
    }

    public String getSocialStats(UUID uuid) {
        return "👥 Социальное:\n" +
                "• Дипломатия: " + relations.getOrDefault(uuid, Collections.emptyMap()).size() + " связей\n" +
                "• Шпионаж: " + spyCount.getOrDefault(uuid, 0) + "\n" +
                "• Революция: " + revolutionPoints.getOrDefault(uuid, 0) + " очков\n" +
                "• Эволюция: ур. " + getEvolutionLevel(uuid) + "\n" +
                "• Мутации: " + getMutations(uuid).size();
    }
}
