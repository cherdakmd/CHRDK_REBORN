package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Система аномалий Энда — нестабильные зоны с уникальными эффектами
 */
public class EndAnomalyManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey anomalyKey;
    private final Map<String, AnomalyData> activeAnomalies = new ConcurrentHashMap<>();

    // Типы аномалий
    public enum AnomalyType {
        VOID_RIFT("Разлом Бездны", "Телепортирует случайных игроков", ChatColor.DARK_PURPLE, 30),
        GRAVITY_WELL("Гравитационный колодец", "Притягивает существ к центру", ChatColor.DARK_GRAY, 25),
        TEMPORAL_BUBBLE("Временна́й пузырь", "Замедляет/ускоряет время", ChatColor.LIGHT_PURPLE, 20),
        ENERGY_STORM("Энергетический шторм", "Наносит урон всем в зоне", ChatColor.RED, 35),
        ENDER_MIASMA("Эндер-миазмы", "Токсичное облако спавнит эндермитов", ChatColor.DARK_PURPLE, 40),
        ENDER_VORTEX("Эндер-вортекс", "Телепортирует в случайное место", ChatColor.DARK_PURPLE, 25),
        CORRUPTION_ZONE("Зона коррупции", "Заражает область", ChatColor.DARK_RED, 50),
        CRYSTAL_SURGE("Кристальный всплеск", "Усиливает кристаллы рядом", ChatColor.LIGHT_PURPLE, 15),
        VOID_TEAR("Разрыв Бездны", "Спавнит эндер-мобов", ChatColor.DARK_PURPLE, 45),
        DRAGON_ECHO("Эхо Дракона", "Спавнит мини-драконов", ChatColor.RED, 60);

        public final String displayName;
        public final String description;
        public final ChatColor color;
        public final int radius;

        AnomalyType(String displayName, String description, ChatColor color, int radius) {
            this.displayName = displayName;
            this.description = description;
            this.color = color;
            this.radius = radius;
        }
    }

    private static class AnomalyData {
        AnomalyType type;
        Location center;
        int radius;
        long spawnTime;
        long duration;
        int intensity;

        AnomalyData(AnomalyType type, Location center, int intensity) {
            this.type = type;
            this.center = center;
            this.radius = type.radius;
            this.spawnTime = System.currentTimeMillis();
            this.duration = 600000 + new Random().nextInt(600000); // 10-20 минут
            this.intensity = intensity;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - spawnTime > duration;
        }
    }

    public EndAnomalyManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.anomalyKey = new NamespacedKey(plugin, "end_anomaly");
        startAnomalyTask();
    }

    /**
     * Задача обновления аномалий
     */
    private void startAnomalyTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAnomalies();
                maybeSpawnAnomaly();
            }
        }.runTaskTimer(plugin, 100L, 100L); // Каждые 5 секунд
    }

    /**
     * Обновление аномалий
     */
    private void updateAnomalies() {
        Iterator<Map.Entry<String, AnomalyData>> it = activeAnomalies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AnomalyData> entry = it.next();
            AnomalyData anomaly = entry.getValue();

            if (anomaly.isExpired()) {
                // Удалить аномалию
                Bukkit.broadcastMessage(anomaly.type.color + "✦ Аномалия рассеялась: " + anomaly.type.displayName);
                it.remove();
                continue;
            }

            // Применить эффекты аномалии
            applyAnomalyEffects(anomaly);
        }
    }

    /**
     * Применить эффекты аномалии
     */
    private void applyAnomalyEffects(AnomalyData anomaly) {
        World world = anomaly.center.getWorld();
        if (world == null) return;

        for (Player p : world.getPlayers()) {
            double distance = p.getLocation().distance(anomaly.center);
            if (distance > anomaly.radius) continue;

            switch (anomaly.type) {
                case GRAVITY_WELL:
                    // Притяжение к центру
                    if (distance > 3) {
                        org.bukkit.util.Vector direction = anomaly.center.toVector().subtract(p.getLocation().toVector()).normalize();
                        p.setVelocity(p.getVelocity().add(direction.multiply(0.1)));
                    }
                    break;

                case ENERGY_STORM:
                    // Урон каждые 5 секунд
                    if (System.currentTimeMillis() % 5000 < 100) {
                        p.damage(anomaly.intensity * 0.5);
                        p.sendMessage(ChatColor.RED + "⚡ Энергетический шторм наносит урон!");
                    }
                    break;

                case TEMPORAL_BUBBLE:
                    // Замедление
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, anomaly.intensity));
                    break;

                case CORRUPTION_ZONE:
                    // Коррупция
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    break;

                default:
                    break;
            }

            // Визуальные эффекты
            spawnAnomalyParticles(p.getLocation(), anomaly.type);
        }
    }

    /**
     * Спавн частиц аномалии
     */
    private void spawnAnomalyParticles(Location loc, AnomalyType type) {
        switch (type) {
            case VOID_RIFT:
                loc.getWorld().spawnParticle(Particle.PORTAL, loc, 5, 0.5, 0.5, 0.5, 0.05);
                break;
            case GRAVITY_WELL:
                loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc, 3, 0.3, 0.3, 0.3, 0.02);
                break;
            case TEMPORAL_BUBBLE:
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.3, 0.3, 0.3, 0.01);
                break;
            case ENERGY_STORM:
                loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 5, 0.5, 0.5, 0.5, 0.02, new Particle.DustOptions(Color.RED, 1));
                break;
            case ENDER_MIASMA:
                loc.getWorld().spawnParticle(Particle.SPELL_MOB, loc, 3, 0.3, 0.3, 0.3, 0.02,
                        new Particle.DustOptions(Color.PURPLE, 1));
                break;
            case ENDER_VORTEX:
                loc.getWorld().spawnParticle(Particle.PORTAL, loc, 10, 0.5, 0.5, 0.5, 0.1);
                break;
            case CORRUPTION_ZONE:
                loc.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 3, 0.3, 0.3, 0.3, 0.02);
                break;
            case CRYSTAL_SURGE:
                loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 2, 0.3, 0.3, 0.3, 0.01);
                break;
            case VOID_TEAR:
                loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.3, 0.3, 0.3, 0.02);
                break;
            case DRAGON_ECHO:
                loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0.5, 0.5, 0.5);
                break;
        }
    }

    /**
     * Попытаться заспавнить аномалию
     */
    private void maybeSpawnAnomaly() {
        if (activeAnomalies.size() >= plugin.getConfig().getInt("end.anomalies.max-active", 5)) return;

        double chance = plugin.getConfig().getDouble("end.anomalies.spawn-chance", 0.02);
        if (new Random().nextDouble() >= chance) return;

        World endWorld = plugin.getEndWorld();
        if (endWorld == null) return;

        // Найти случайную точку
        List<Player> players = new ArrayList<>(endWorld.getPlayers());
        if (players.isEmpty()) return;

        Player target = players.get(new Random().nextInt(players.size()));
        Location center = target.getLocation().add(
                new Random().nextInt(100) - 50,
                0,
                new Random().nextInt(100) - 50
        );
        center.setY(endWorld.getHighestBlockYAt(center) + 1);

        // Выбрать тип аномалии
        AnomalyType[] types = AnomalyType.values();
        AnomalyType type = types[new Random().nextInt(types.length)];

        // Интенсивность зависит от количества игроков
        int intensity = 1 + (players.size() / 3);

        spawnAnomaly(center, type, intensity);
    }

    /**
     * Заспавнить аномалию
     */
    public void spawnAnomaly(Location center, AnomalyType type, int intensity) {
        String key = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        activeAnomalies.put(key, new AnomalyData(type, center, intensity));

        Bukkit.broadcastMessage(type.color + "⚠ Аномалия обнаружена: " + type.displayName + " в Энду!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Координаты: " + center.getBlockX() + ", " + center.getBlockZ());

        // Визуальные эффекты при появлении
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            double x = center.getX() + type.radius * Math.cos(angle);
            double z = center.getZ() + type.radius * Math.sin(angle);
            center.getWorld().spawnParticle(Particle.PORTAL, new Location(center.getWorld(), x, center.getY() + 1, z), 5, 0.1, 0.1, 0.1, 0.05);
        }

        // Спавн мобов в зависимости от типа
        if (type == AnomalyType.ENDER_MIASMA) {
            for (int i = 0; i < intensity * 2; i++) {
                Location spawnLoc = center.clone().add(
                        new Random().nextInt(10) - 5,
                        0,
                        new Random().nextInt(10) - 5
                );
                spawnLoc.setY(center.getWorld().getHighestBlockYAt(spawnLoc) + 1);
                center.getWorld().spawnEntity(spawnLoc, EntityType.ENDERMITE);
            }
        } else if (type == AnomalyType.VOID_TEAR) {
            for (int i = 0; i < intensity * 3; i++) {
                Location spawnLoc = center.clone().add(
                        new Random().nextInt(15) - 7,
                        0,
                        new Random().nextInt(15) - 7
                );
                spawnLoc.setY(center.getWorld().getHighestBlockYAt(spawnLoc) + 1);
                center.getWorld().spawnEntity(spawnLoc, EntityType.ENDERMITE);
            }
        } else if (type == AnomalyType.DRAGON_ECHO) {
            for (int i = 0; i < intensity; i++) {
                Location spawnLoc = center.clone().add(
                        new Random().nextInt(20) - 10,
                        5,
                        new Random().nextInt(20) - 10
                );
                center.getWorld().spawnEntity(spawnLoc, EntityType.PHANTOM);
            }
        }
    }

    /**
     * Получить количество активных аномалий
     */
    public int getActiveAnomalyCount() {
        return activeAnomalies.size();
    }

    /**
     * Получить информацию об аномалиях
     */
    public String getAnomaliesInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ ⚠ Аномалии Энда ═══\n\n");

        for (AnomalyType type : AnomalyType.values()) {
            sb.append(type.color).append("• ").append(type.displayName);
            sb.append(ChatColor.GRAY).append(" — ").append(type.description);
            sb.append(ChatColor.DARK_GRAY).append(" (радиус: ").append(type.radius).append(")\n");
        }

        sb.append("\n").append(ChatColor.GRAY).append("Активных аномалий: ").append(activeAnomalies.size());

        return sb.toString();
    }
}
