package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер разломов — порталы в Энд из обычного мира
 */
public class EndRiftManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final Map<String, RiftData> activeRifts = new ConcurrentHashMap<>();
    private final NamespacedKey riftKey;

    private static class RiftData {
        Location location;
        long spawnTime;
        long duration;
        int usesLeft;

        RiftData(Location location, long duration, int maxUses) {
            this.location = location;
            this.spawnTime = System.currentTimeMillis();
            this.duration = duration;
            this.usesLeft = maxUses;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - spawnTime > duration || usesLeft <= 0;
        }
    }

    public EndRiftManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.riftKey = new NamespacedKey(plugin, "end_rift");
        startRiftTask();
    }

    /**
     * Задача проверки разломов
     */
    private void startRiftTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkRifts();
                maybeSpawnRift();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Каждую минуту
    }

    /**
     * Проверить и удалить истёкшие разломы
     */
    private void checkRifts() {
        Iterator<Map.Entry<String, RiftData>> it = activeRifts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RiftData> entry = it.next();
            RiftData rift = entry.getValue();
            if (rift.isExpired()) {
                // Удалить частицы и метки
                Location loc = rift.location;
                loc.getWorld().spawnParticle(Particle.PORTAL, loc.add(0.5, 1, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
                it.remove();
                Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "🌀 Разлом в Энд закрылся!");
            }
        }
    }

    /**
     * Попытаться заспавнить разлом
     */
    private void maybeSpawnRift() {
        if (activeRifts.size() >= plugin.getConfig().getInt("end.rifts.max-active", 3)) return;

        double chance = plugin.getConfig().getDouble("end.rifts.spawn-chance", 0.05);
        if (new Random().nextDouble() >= chance) return;

        // Найти игрока в обычном мире
        List<Player> overworldPlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL) {
                overworldPlayers.add(p);
            }
        }
        if (overworldPlayers.isEmpty()) return;

        Player target = overworldPlayers.get(new Random().nextInt(overworldPlayers.size()));
        Location loc = target.getLocation().add(
                new Random().nextInt(100) - 50,
                0,
                new Random().nextInt(100) - 50
        );
        loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);

        spawnRift(loc);
    }

    /**
     * Заспавнить разлом
     */
    public void spawnRift(Location loc) {
        long duration = plugin.getConfig().getLong("end.rifts.duration-seconds", 600) * 1000;
        int maxUses = plugin.getConfig().getInt("end.rifts.max-uses", 10);

        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockZ();
        activeRifts.put(key, new RiftData(loc, duration, maxUses));

        // Визуальные эффекты
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                RiftData rift = activeRifts.get(key);
                if (rift == null || rift.isExpired()) {
                    cancel();
                    return;
                }
                loc.getWorld().spawnParticle(Particle.PORTAL, loc.add(0.5, 1.5, 0.5), 20, 0.3, 0.5, 0.3, 0.05);
                loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc.add(0.5, 1, 0.5), 5, 0.2, 0.3, 0.2, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "🌀 Разлом в Энд открылся! Координаты: " +
                loc.getBlockX() + ", " + loc.getBlockZ());
    }

    /**
     * Использовать разлом для телепортации
     */
    public boolean useRift(Player p) {
        for (Map.Entry<String, RiftData> entry : activeRifts.entrySet()) {
            RiftData rift = entry.getValue();
            if (p.getLocation().distanceSquared(rift.location) < 4) {
                if (rift.usesLeft <= 0) {
                    p.sendMessage(ChatColor.RED + "Разлом иссяк!");
                    return false;
                }

                rift.usesLeft--;

                // Телепортация в Энд
                World endWorld = Bukkit.getWorlds().stream()
                        .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                        .findFirst().orElse(null);

                if (endWorld == null) {
                    p.sendMessage(ChatColor.RED + "Мир Энда не найден!");
                    return false;
                }

                Location endLoc = endWorld.getSpawnLocation().add(0.5, 1, 0.5);
                p.teleport(endLoc);
                p.sendMessage(ChatColor.DARK_PURPLE + "✦ Телепортирован в Энд через разлом!");
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);

                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();

        // Проверка на разлом (метка в PDC)
        if (e.getClickedBlock().getType() == Material.END_PORTAL_FRAME) {
            // Попытка использования разлома
            useRift(p);
        }
    }

    /**
     * Получить количество активных разломов
     */
    public int getActiveRiftCount() {
        return activeRifts.size();
    }

    public void saveAll() {
        // Сохранение данных разломов
    }
}
