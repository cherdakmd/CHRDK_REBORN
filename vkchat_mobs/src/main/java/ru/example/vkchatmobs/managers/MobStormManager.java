package ru.example.vkchatmobs.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatmobs.VKChatMobsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Шторм мобов — мировое событие.
 * При убийстве мини-босса есть шанс вызвать шторм: 50 мобов спавнятся волнами за 30 секунд.
 */
public class MobStormManager implements Listener {
    private final VKChatMobsPlugin plugin;

    private final NamespacedKey rankKey;
    private final NamespacedKey diffKey;
    private final NamespacedKey isBossKey;
    private final NamespacedKey stormMobKey;

    private final Set<UUID> activeStormMobs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stormActive = new AtomicBoolean(false);

    public MobStormManager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.rankKey = new NamespacedKey(plugin, "mob_rank");
        this.diffKey = new NamespacedKey(plugin, "difficulty_multiplier");
        this.isBossKey = new NamespacedKey(plugin, "is_mini_boss");
        this.stormMobKey = new NamespacedKey(plugin, "from_mob_storm");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMiniBossDeath(EntityDeathEvent e) {
        if (!plugin.getConfig().getBoolean("mob-storm.enabled", true)) return;
        LivingEntity mob = e.getEntity();
        if (!mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER)) return;
        Player killer = mob.getKiller();
        if (killer == null) return;

        int chance = plugin.getConfig().getInt("mob-storm.trigger-chance-percent", 10);
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;
        if (stormActive.get()) return;

        startStorm(killer.getLocation());
    }

    public void startStorm(Location center) {
        stormActive.set(true);
        World world = center.getWorld();
        int totalMobs = plugin.getConfig().getInt("mob-storm.total-mobs", 50);
        int mobRank = plugin.getConfig().getInt("mob-storm.mob-rank", 5);
        double mobMultiplier = plugin.getConfig().getDouble("mob-storm.mob-multiplier", 2.0);

        String broadcast = "⚡ ШТОРМ МобОВ! " + totalMobs + " монстров обрушиваются на мир " + center.getWorld().getName() + "!";
        Bukkit.broadcastMessage(ChatColor.RED + ChatColor.BOLD.toString() + broadcast);
        for (Player onlineP : Bukkit.getOnlinePlayers()) {
            onlineP.playSound(onlineP.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (stormActive.get()) Bukkit.broadcastMessage(ChatColor.YELLOW + "⚡ [ШТОРМ] Приготовьтесь! До конца 20 секунд!");
        }, 200L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (stormActive.get()) Bukkit.broadcastMessage(ChatColor.RED + "⚡ [ШТОРМ] 10 секунд до окончания!");
        }, 400L);

        List<Map<?, ?>> waves = plugin.getConfig().getMapList("mob-storm.waves");
        if (waves.isEmpty()) {
            waves = new ArrayList<>();
            Map<String, Object> w1 = new HashMap<>();
            w1.put("count", 10); w1.put("delay-ticks", 0);
            Map<String, Object> w2 = new HashMap<>();
            w2.put("count", 15); w2.put("delay-ticks", 200);
            Map<String, Object> w3 = new HashMap<>();
            w3.put("count", 25); w3.put("delay-ticks", 400);
            waves.add(w1); waves.add(w2); waves.add(w3);
        }

        for (Map<?, ?> wave : waves) {
            int count = wave.containsKey("count") ? ((Number) wave.get("count")).intValue() : 10;
            long delay = wave.containsKey("delay-ticks") ? ((Number) wave.get("delay-ticks")).longValue() : 0L;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < count; i++) {
                    spawnStormMob(world, center, mobRank, mobMultiplier);
                }
            }, delay);
        }

        long duration = plugin.getConfig().getLong("mob-storm.duration-seconds", 30) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stormActive.set(false);
            clearStormMobs();
            Bukkit.broadcastMessage(ChatColor.GREEN + "⚡ Шторм мобов окончен!");
        }, duration);
    }

    private void spawnStormMob(World world, Location center, int rank, double multiplier) {
        double offsetX = ThreadLocalRandom.current().nextDouble() * 20 - 10;
        double offsetZ = ThreadLocalRandom.current().nextDouble() * 20 - 10;
        Location spawnLoc = center.clone().add(offsetX, 0, offsetZ);
        spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.CAVE_SPIDER};
        EntityType type = types[ThreadLocalRandom.current().nextInt(types.length)];

        LivingEntity mob = (LivingEntity) world.spawnEntity(spawnLoc, type);
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, rank);
        mob.getPersistentDataContainer().set(diffKey, PersistentDataType.DOUBLE, multiplier);
        mob.getPersistentDataContainer().set(stormMobKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "mobs_scaled"), PersistentDataType.INTEGER, 1);
        mob.setCustomName(ChatColor.DARK_RED + "⚡ Штормовой " + mob.getName());
        mob.setCustomNameVisible(true);
        mob.setGlowing(true);

        AttributeInstance hpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hpAttr == null) return;
        double maxHp = hpAttr.getValue();
        hpAttr.setBaseValue(maxHp * multiplier);
        mob.setHealth(hpAttr.getValue());

        AttributeInstance dmgAttr = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(dmgAttr.getValue() * multiplier);
        }

        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, true, false));
        activeStormMobs.add(mob.getUniqueId());
    }

    private void clearStormMobs() {
        Iterator<UUID> it = activeStormMobs.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid() && !e.isDead()) {
                e.remove();
            }
            it.remove();
        }
    }

    public boolean isStormActive() {
        return stormActive.get();
    }
}
