package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [28] Ресурсные узлы
 * [29] Энергетические кристаллы
 * [30] Энергия Бездны
 * [31] Пространственные разломы
 * [32] Временна́я дилатация
 * [33] Гравитационные аномалии
 * [34] Звуковые эффекты
 * [35] Визуальные эффекты
 */
public class EndResourceManagers {
    private final VKChatEndPlugin plugin;

    // ═══ [28] РЕСУРСНЫЕ УЗЛЫ ═══
    private final Map<String, ResourceNode> resourceNodes = new ConcurrentHashMap<>();

    private static class ResourceNode {
        Location location;
        String resourceType;
        int remainingResources;
        long respawnTime;

        ResourceNode(Location location, String resourceType, int amount) {
            this.location = location;
            this.resourceType = resourceType;
            this.remainingResources = amount;
            this.respawnTime = System.currentTimeMillis() + 3600000; // 1 час
        }
    }

    // ═══ [29] ЭНЕРГЕТИЧЕСКИЕ КРИСТАЛЛЫ ═══
    private final Map<UUID, PowerCrystal> powerCrystals = new ConcurrentHashMap<>();

    private static class PowerCrystal {
        Location location;
        int energy;
        int maxEnergy;
        String crystalType;

        PowerCrystal(Location location, String type, int maxEnergy) {
            this.location = location;
            this.crystalType = type;
            this.maxEnergy = maxEnergy;
            this.energy = maxEnergy;
        }
    }

    // ═══ [30] ЭНЕРГИЯ БЕЗДНЫ ═══
    private final Map<UUID, Integer> voidEnergy = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastVoidEnergyUpdate = new ConcurrentHashMap<>();

    // ═══ [32] ВРЕМЕННА́Я ДИЛАТАЦИЯ ═══
    private final Map<String, TimeDilation> timeDilations = new ConcurrentHashMap<>();

    private static class TimeDilation {
        Location center;
        int radius;
        double timeMultiplier;
        long duration;

        TimeDilation(Location center, int radius, double multiplier, long duration) {
            this.center = center;
            this.radius = radius;
            this.timeMultiplier = multiplier;
            this.duration = duration;
        }
    }

    // ═══ [33] ГРАВИТАЦИОННЫЕ АНОМАЛИИ ═══
    private final Map<String, GravityAnomaly> gravityAnomalies = new ConcurrentHashMap<>();

    private static class GravityAnomaly {
        Location center;
        int radius;
        double gravityMultiplier;
        long duration;

        GravityAnomaly(Location center, int radius, double multiplier, long duration) {
            this.center = center;
            this.radius = radius;
            this.gravityMultiplier = multiplier;
            this.duration = duration;
        }
    }

    public EndResourceManagers(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        startResourceTasks();
    }

    /**
     * Запуск задач ресурсов
     */
    private void startResourceTasks() {
        // Обновление энергии Бездны
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getEnvironment() == World.Environment.THE_END) {
                        updateVoidEnergy(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // Каждые 30 секунд

        // Обновление гравитационных аномалий
        new BukkitRunnable() {
            @Override
            public void run() {
                updateGravityAnomalies();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Каждую секунду
    }

    // ═══ РЕСУРСНЫЕ УЗЛЫ ═══

    /**
     * Создать ресурсный узел
     */
    public void createResourceNode(Location loc, String type, int amount) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockZ();
        resourceNodes.put(key, new ResourceNode(loc, type, amount));
    }

    /**
     * Добыть ресурс из узла
     */
    public boolean harvestResourceNode(Player p, Location loc) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockZ();
        ResourceNode node = resourceNodes.get(key);
        if (node == null || node.remainingResources <= 0) return false;

        node.remainingResources--;
        int rep = 50;
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
            }
        } catch (Exception ignored) {}

        plugin.getEndManager().addEndReputation(p, rep / 2);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Добыто из узла! +" + rep + " реп.");
        return true;
    }

    // ═══ ЭНЕРГЕТИЧЕСКИЕ КРИСТАЛЛЫ ═══

    /**
     * Создать энергетический кристалл
     */
    public void createPowerCrystal(Location loc, String type) {
        UUID id = UUID.nameUUIDFromBytes(loc.toString().getBytes());
        int maxEnergy = type.equals("legendary") ? 1000 : type.equals("epic") ? 500 : 200;
        powerCrystals.put(id, new PowerCrystal(loc, type, maxEnergy));
    }

    /**
     * Зарядить кристалл
     */
    public boolean chargeCrystal(Player p, Location loc, int amount) {
        UUID id = UUID.nameUUIDFromBytes(loc.toString().getBytes());
        PowerCrystal crystal = powerCrystals.get(id);
        if (crystal == null) return false;

        int chargeCost = amount * 10;
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < chargeCost) {
            p.sendMessage(ChatColor.RED + "Нужно " + chargeCost + " реп.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, chargeCost);
        crystal.energy = Math.min(crystal.maxEnergy, crystal.energy + amount);

        p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Кристалл заряжен! Энергия: " + crystal.energy + "/" + crystal.maxEnergy);
        return true;
    }

    // ═══ ЭНЕРГИЯ БЕЗДНЫ ═══

    /**
     * Получить энергию Бездны игрока
     */
    public int getVoidEnergy(Player p) {
        return voidEnergy.getOrDefault(p.getUniqueId(), 0);
    }

    /**
     * Добавить энергию Бездны
     */
    public void addVoidEnergy(Player p, int amount) {
        int current = getVoidEnergy(p);
        voidEnergy.put(p.getUniqueId(), Math.min(1000, current + amount));
    }

    /**
     * Обновить энергию Бездны
     */
    private void updateVoidEnergy(Player p) {
        int current = getVoidEnergy(p);
        if (current < 1000) {
            voidEnergy.put(p.getUniqueId(), Math.min(1000, current + 5));
        }
    }

    /**
     * Использовать энергию Бездны
     */
    public boolean useVoidEnergy(Player p, int amount) {
        int current = getVoidEnergy(p);
        if (current < amount) {
            p.sendMessage(ChatColor.RED + "Недостаточно энергии Бездны! Нужно: " + amount + ", есть: " + current);
            return false;
        }
        voidEnergy.put(p.getUniqueId(), current - amount);
        return true;
    }

    // ═══ ВРЕМЕННА́Я ДИЛАТАЦИЯ ═══

    /**
     * Создать зону временно́й дилатации
     */
    public void createTimeDilation(Location center, int radius, double multiplier, long duration) {
        String key = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        timeDilations.put(key, new TimeDilation(center, radius, multiplier, duration));
    }

    /**
     * Получить множитель времени в точке
     */
    public double getTimeMultiplier(Location loc) {
        for (TimeDilation dilation : timeDilations.values()) {
            if (dilation.center.getWorld().equals(loc.getWorld())) {
                double distance = dilation.center.distance(loc);
                if (distance <= dilation.radius) {
                    return dilation.timeMultiplier;
                }
            }
        }
        return 1.0;
    }

    // ═══ ГРАВИТАЦИОННЫЕ АНОМАЛИИ ═══

    /**
     * Создать гравитационную аномалию
     */
    public void createGravityAnomaly(Location center, int radius, double multiplier, long duration) {
        String key = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        gravityAnomalies.put(key, new GravityAnomaly(center, radius, multiplier, duration));
    }

    /**
     * Получить множитель гравитации в точке
     */
    public double getGravityMultiplier(Location loc) {
        for (GravityAnomaly anomaly : gravityAnomalies.values()) {
            if (anomaly.center.getWorld().equals(loc.getWorld())) {
                double distance = anomaly.center.distance(loc);
                if (distance <= anomaly.radius) {
                    return anomaly.gravityMultiplier;
                }
            }
        }
        return 1.0;
    }

    /**
     * Обновление гравитационных аномалий
     */
    private void updateGravityAnomalies() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == World.Environment.THE_END) {
                double gravity = getGravityMultiplier(p.getLocation());
                if (gravity != 1.0) {
                    // Применение гравитации
                    if (gravity < 1.0) {
                        // Низкая гравитация - прыжок выше
                        p.setVelocity(p.getVelocity().setY(p.getVelocity().getY() * (1.0 / gravity)));
                    } else {
                        // Высокая гравитация - прыжок ниже
                        p.setVelocity(p.getVelocity().setY(p.getVelocity().getY() * (1.0 / gravity)));
                    }
                }
            }
        }
    }

    // ═══ ЗВУКОВЫЕ ЭФФЕКТЫ ═══

    /**
     * Воспроизвести эндер-звук
     */
    public void playEndSound(Location loc, String soundType) {
        Sound sound;
        switch (soundType) {
            case "portal":
                sound = Sound.BLOCK_END_PORTAL_SPAWN;
                break;
            case "dragon":
                sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                break;
            case "enderman":
                sound = Sound.ENTITY_ENDERMAN_TELEPORT;
                break;
            case "shulker":
                sound = Sound.ENTITY_SHULKER_OPEN;
                break;
            case "crystal":
                sound = Sound.BLOCK_ENCHANTMENT_TABLE_USE;
                break;
            default:
                sound = Sound.BLOCK_END_PORTAL_FRAME_FILL;
        }
        loc.getWorld().playSound(loc, sound, 2f, 0.5f);
    }

    // ═══ ВИЗУАЛЬНЫЕ ЭФФЕКТЫ ═══

    /**
     * Создать эндер-частицы
     */
    public void spawnEndParticles(Location loc, String particleType, int count) {
        Particle particle;
        switch (particleType) {
            case "portal":
                particle = Particle.PORTAL;
                break;
            case "dragon":
                particle = Particle.DRAGON_BREATH;
                break;
            case "enderman":
                particle = Particle.END_ROD;
                break;
            case "void":
                particle = Particle.SMOKE_LARGE;
                break;
            default:
                particle = Particle.SPELL_WITCH;
        }
        loc.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.05);
    }

    /**
     * Получить количество ресурсных узлов
     */
    public int getResourceNodeCount() {
        return resourceNodes.size();
    }

    /**
     * Получить количество энергетических кристаллов
     */
    public int getPowerCrystalCount() {
        return powerCrystals.size();
    }

    /**
     * Получить количество зон временно́й дилатации
     */
    public int getTimeDilationCount() {
        return timeDilations.size();
    }

    /**
     * Получить количество гравитационных аномалий
     */
    public int getGravityAnomalyCount() {
        return gravityAnomalies.size();
    }
}
