package ru.example.vkchatevents.managers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "statistics.yml");
            org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Integer>> entry : stats.entrySet()) {
                String key = entry.getKey().toString();
                for (Map.Entry<String, Integer> stat : entry.getValue().entrySet()) {
                    yml.set(key + "." + stat.getKey(), stat.getValue());
                }
            }
            yml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка сохранения статистики: " + e.getMessage());
        }
    }

    public void load() {
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "statistics.yml");
            if (!file.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            for (String uuidKey : yml.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    Map<String, Integer> map = new ConcurrentHashMap<>();
                    for (String statKey : yml.getConfigurationSection(uuidKey).getKeys(false)) {
                        map.put(statKey, yml.getInt(uuidKey + "." + statKey));
                    }
                    stats.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки статистики: " + e.getMessage());
        }
    }
}
