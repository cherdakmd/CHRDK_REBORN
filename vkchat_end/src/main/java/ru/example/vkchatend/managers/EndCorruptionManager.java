package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер коррупции — заражение и очищение островов Энда
 */
public class EndCorruptionManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final Map<String, CorruptionZone> corruptedZones = new ConcurrentHashMap<>();
    private final NamespacedKey purifierKey;

    // Уровни коррупции
    public enum CorruptionLevel {
        NONE("Чисто", ChatColor.GREEN, 0),
        LOW("Слабая", ChatColor.YELLOW, 1),
        MEDIUM("Средняя", ChatColor.GOLD, 2),
        HIGH("Сильная", ChatColor.RED, 3),
        CRITICAL("Критическая", ChatColor.DARK_RED, 4);

        public final String displayName;
        public final ChatColor color;
        public final int level;

        CorruptionLevel(String displayName, ChatColor color, int level) {
            this.displayName = displayName;
            this.color = color;
            this.level = level;
        }
    }

    private static class CorruptionZone {
        Location center;
        int radius;
        CorruptionLevel level;
        long lastUpdate;

        CorruptionZone(Location center, int radius, CorruptionLevel level) {
            this.center = center;
            this.radius = radius;
            this.level = level;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    public EndCorruptionManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.purifierKey = new NamespacedKey(plugin, "end_purifier");
        startCorruptionTask();
    }

    /**
     * Запуск задачи распространения коррупции
     */
    private void startCorruptionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                spreadCorruption();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Каждые 5 минут
    }

    /**
     * Распространить коррупцию
     */
    private void spreadCorruption() {
        World endWorld = getEndWorld();
        if (endWorld == null) return;

        for (CorruptionZone zone : corruptedZones.values()) {
            if (zone.level == CorruptionLevel.CRITICAL) continue;

            // Шанс повышения уровня
            if (new Random().nextInt(100) < 10) {
                zone.level = CorruptionLevel.values()[Math.min(zone.level.level + 1, CorruptionLevel.CRITICAL.level)];
                zone.lastUpdate = System.currentTimeMillis();
            }

            // Распространение на соседние блоки
            if (new Random().nextInt(100) < 5) {
                spreadToNearbyBlocks(endWorld, zone.center, zone.radius);
            }
        }
    }

    /**
     * Распространить на соседние блоки
     */
    private void spreadToNearbyBlocks(World world, Location center, int radius) {
        int x = center.getBlockX() + new Random().nextInt(radius * 2) - radius;
        int z = center.getBlockZ() + new Random().nextInt(radius * 2) - radius;
        int y = world.getHighestBlockYAt(x, z);

        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == Material.END_STONE) {
            block.setType(Material.PURPUR_BLOCK);
        }
    }

    /**
     * Очистить зону от коррупции
     */
    public boolean purifyZone(Player p, Location center, int radius) {
        // Проверка ресурсов
        int cost = plugin.getConfig().getInt("end.purification.cost", 1000);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Привяжи ВК (/vklink)!");
            return false;
        }

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. (у тебя " + rep + ")");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        // Очистка блоков
        World world = center.getWorld();
        int purified = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -10; y <= 10; y++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() == Material.PURPUR_BLOCK) {
                        block.setType(Material.END_STONE);
                        purified++;
                    }
                }
            }
        }

        // Удалить зону коррупции
        String zoneKey = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        corruptedZones.remove(zoneKey);

        // Награда
        int repReward = purified * 2;
        plugin.getEndManager().addEndReputation(p, repReward);

        p.sendMessage(ChatColor.GREEN + "✦ Очищено " + purified + " блоков! +" + repReward + " репутации Энда");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);

        return true;
    }

    /**
     * Получить уровень коррупции в точке
     */
    public CorruptionLevel getCorruptionAt(Location loc) {
        for (CorruptionZone zone : corruptedZones.values()) {
            double distance = loc.distance(zone.center);
            if (distance <= zone.radius) {
                return zone.level;
            }
        }
        return CorruptionLevel.NONE;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;

        // Проверка очистителя
        if (e.getItemInHand().hasItemMeta() &&
            e.getItemInHand().getItemMeta().getPersistentDataContainer().has(purifierKey, PersistentDataType.INTEGER)) {
            Location loc = e.getBlock().getLocation();
            int radius = plugin.getConfig().getInt("end.purification.radius", 5);
            purifyZone(p, loc, radius);
            e.setCancelled(true);
        }
    }

    private World getEndWorld() {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                .findFirst().orElse(null);
    }

    public void saveAll() {
        // Сохранение данных коррупции
    }
}
