package ru.example.vkchatevents.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [31-35] Слияние, Трансцендентность, Апокалипсис, Перерождение, Просветление
 */
public class EvolutionManager implements Listener {
    private final VKChatEventsPlugin plugin;

    // Слияние
    private final Map<UUID, Integer> fusionLevel = new ConcurrentHashMap<>();
    // Трансцендентность
    private final Map<UUID, Integer> transcendence = new ConcurrentHashMap<>();
    // Апокалипсис
    private final Map<UUID, Integer> apocalypseSurvived = new ConcurrentHashMap<>();
    // Перерождение
    private final Map<UUID, Integer> rebirthCount = new ConcurrentHashMap<>();
    // Просветление
    private final Map<UUID, Integer> enlightenment = new ConcurrentHashMap<>();

    public EvolutionManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    // Слияние
    public void fuse(UUID uuid) {
        fusionLevel.merge(uuid, 1, Integer::sum);
    }

    public int getFusionLevel(UUID uuid) {
        return fusionLevel.getOrDefault(uuid, 0);
    }

    // Трансцендентность
    public void transcend(UUID uuid) {
        transcendence.merge(uuid, 1, Integer::sum);
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p != null) {
            VKChatPlugin.getInstance().getApi().addReputation(
                    VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 500);
        }
    }

    // Апокалипсис
    public void surviveApocalypse(UUID uuid) {
        apocalypseSurvived.merge(uuid, 1, Integer::sum);
    }

    // Перерождение
    public void rebirth(UUID uuid) {
        rebirthCount.merge(uuid, 1, Integer::sum);
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p != null) {
            VKChatPlugin.getInstance().getApi().addReputation(
                    VKChatPlugin.getInstance().getApi().getLinkedVkId(p), 1000);
        }
    }

    // Просветление
    public void enlighten(UUID uuid) {
        enlightenment.merge(uuid, 1, Integer::sum);
    }

    public String getEvolutionStats(UUID uuid) {
        return "🧬 Эволюция:\n" +
                "• Слияние: ур. " + getFusionLevel(uuid) + "\n" +
                "• Трансцендентность: " + transcendence.getOrDefault(uuid, 0) + "\n" +
                "• Апокалипсисов пережито: " + apocalypseSurvived.getOrDefault(uuid, 0) + "\n" +
                "• Перерождений: " + rebirthCount.getOrDefault(uuid, 0) + "\n" +
                "• Просветление: " + enlightenment.getOrDefault(uuid, 0);
    }
}
