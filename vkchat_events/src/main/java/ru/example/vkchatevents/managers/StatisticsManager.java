package ru.example.vkchatevents.managers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [12] Статистика событий
 */
public class StatisticsManager implements Listener {
    private final VKChatEventsPlugin plugin;
    private final Map<UUID, Map<String, Integer>> stats = new ConcurrentHashMap<>();

    public StatisticsManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void addStat(UUID uuid, String stat, int amount) {
        stats.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).merge(stat, amount, Integer::sum);
    }

    public int getStat(UUID uuid, String stat) {
        return stats.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(stat, 0);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        addStat(e.getEntity().getKiller().getUniqueId(), "kills", 1);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        addStat(e.getPlayer().getUniqueId(), "blocks_mined", 1);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        addStat(e.getPlayer().getUniqueId(), "joins", 1);
    }

    public void save() {
        // TODO: сохранение в файл/БД
    }
}
