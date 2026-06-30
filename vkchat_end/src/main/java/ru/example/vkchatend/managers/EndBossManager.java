package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
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
 * Менеджер боссов Энда — Эндер Дракон + мини-боссы
 */
public class EndBossManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey bossTypeKey;
    private final NamespacedKey bossPhaseKey;

    private final Map<UUID, BossData> activeBosses = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageTracker = new ConcurrentHashMap<>();

    // Типы боссов
    public enum BossType {
        ENDER_DRAGON("Эндер Дракон", 1024, 50, ChatColor.DARK_PURPLE),
        VOID_WALKER("Странник Бездны", 512, 30, ChatColor.DARK_GRAY),
        CHORUS_TITAN("Титан Хоруса", 768, 40, ChatColor.LIGHT_PURPLE),
        ENDER_SENTINEL("Эндер Страж", 384, 25, ChatColor.BLUE),
        SHULKER_LORD("Повелитель Шалкеров", 256, 20, ChatColor.GOLD),
        ENDER_WARDEN("Эндер Хранитель", 640, 35, ChatColor.RED);

        public final String displayName;
        public final double baseHealth;
        public final int repReward;
        public final ChatColor color;

        BossType(String displayName, double baseHealth, int repReward, ChatColor color) {
            this.displayName = displayName;
            this.baseHealth = baseHealth;
            this.repReward = repReward;
            this.color = color;
        }
    }

    private static class BossData {
        BossType type;
        BossBar bossBar;
        int phase;
        long spawnTime;

        BossData(BossType type, BossBar bossBar) {
            this.type = type;
            this.bossBar = bossBar;
            this.phase = 1;
            this.spawnTime = System.currentTimeMillis();
        }
    }

    public EndBossManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "end_boss");
        this.bossTypeKey = new NamespacedKey(plugin, "end_boss_type");
        this.bossPhaseKey = new NamespacedKey(plugin, "end_boss_phase");
    }

    public int getBossCount() {
        return BossType.values().length;
    }

    /**
     * Заспавнить босса
     */
    public LivingEntity spawnBoss(Location loc, BossType type, Player summoner) {
        EntityType entityType;
        switch (type) {
            case ENDER_DRAGON:
                entityType = EntityType.ENDER_DRAGON;
                break;
            case VOID_WALKER:
                entityType = EntityType.WITHER_SKELETON;
                break;
            case CHORUS_TITAN:
                entityType = EntityType.IRON_GOLEM;
                break;
            case ENDER_SENTINEL:
                entityType = EntityType.ENDERMITE;
                break;
            case SHULKER_LORD:
                entityType = EntityType.SHULKER;
                break;
            case ENDER_WARDEN:
                entityType = EntityType.IRON_GOLEM; // WARDEN не доступен в 1.16.5
                break;
            default:
                entityType = EntityType.ZOMBIE;
        }

        Entity entity = loc.getWorld().spawnEntity(loc, entityType);
        if (!(entity instanceof LivingEntity)) return null;
        LivingEntity boss = (LivingEntity) entity;

        // Настройка босса
        double health = type.baseHealth;
        if (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            boss.setHealth(health);
        }

        // PDC метки
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.INTEGER, 1);
        boss.getPersistentDataContainer().set(bossTypeKey, PersistentDataType.STRING, type.name());
        boss.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, 1);

        // Имя
        boss.setCustomName(type.color + "☠ " + type.displayName);
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);

        // Эффекты в зависимости от типа
        applyBossEffects(boss, type);

        // BossBar
        BossBar bossBar = Bukkit.createBossBar(
                type.color + "☠ " + type.displayName,
                BarColor.PURPLE,
                BarStyle.SEGMENTED_10
        );
        bossBar.setVisible(true);

        // Запомнить босса
        activeBosses.put(boss.getUniqueId(), new BossData(type, bossBar));
        damageTracker.put(boss.getUniqueId(), new ConcurrentHashMap<>());

        // Обновление BossBar
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    bossBar.removeAll();
                    activeBosses.remove(boss.getUniqueId());
                    damageTracker.remove(boss.getUniqueId());
                    cancel();
                    return;
                }
                double progress = boss.getHealth() / boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                bossBar.setProgress(Math.max(0, Math.min(1, progress)));

                // Обновить фазу
                int newPhase = progress > 0.66 ? 1 : progress > 0.33 ? 2 : 3;
                BossData data = activeBosses.get(boss.getUniqueId());
                if (data != null && newPhase > data.phase) {
                    data.phase = newPhase;
                    onPhaseChange(boss, type, newPhase);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Уведомление
        String msg = "☠ [ЭНД] " + type.displayName + " появился в Энду!";
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + msg);

        return boss;
    }

    /**
     * Применить эффекты босса
     */
    private void applyBossEffects(LivingEntity boss, BossType type) {
        switch (type) {
            case ENDER_DRAGON:
                // Дракон — стандартные настройки
                break;
            case VOID_WALKER:
                boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0));
                break;
            case CHORUS_TITAN:
                boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
                break;
            case ENDER_SENTINEL:
                boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                break;
            case SHULKER_LORD:
                boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2));
                break;
            case ENDER_WARDEN:
                boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 2));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
                break;
        }
    }

    /**
     * Смена фазы босса
     */
    private void onPhaseChange(LivingEntity boss, BossType type, int phase) {
        String phaseName;
        switch (phase) {
            case 2:
                phaseName = "Ярость";
                boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0));
                break;
            case 3:
                phaseName = "Безумие";
                boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 2));
                boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
                // AoE атака
                for (Player p : boss.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(boss.getLocation()) < 100) {
                        p.damage(5.0, boss);
                        p.sendMessage(ChatColor.RED + "☠ " + type.displayName + " впадает в безумие!");
                    }
                }
                break;
            default:
                phaseName = "Норма";
        }

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "☠ " + type.displayName + " — Фаза " + phase + ": " + phaseName);
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity boss = (LivingEntity) e.getEntity();
        if (!boss.getPersistentDataContainer().has(bossKey, PersistentDataType.INTEGER)) return;

        // Записать урон
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            damageTracker.computeIfAbsent(boss.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        LivingEntity boss = e.getEntity();
        if (!boss.getPersistentDataContainer().has(bossKey, PersistentDataType.INTEGER)) return;

        BossData data = activeBosses.remove(boss.getUniqueId());
        if (data == null) return;

        // Убрать BossBar
        if (data.bossBar != null) data.bossBar.removeAll();

        // Раздать награды
        Map<UUID, Double> damage = damageTracker.remove(boss.getUniqueId());
        if (damage == null || damage.isEmpty()) return;

        // Сортировка по урону
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(damage.entrySet());
        sorted.sort((a, b2) -> Double.compare(b2.getValue(), a.getValue()));

        // Топ урон
        UUID topDamager = sorted.get(0).getKey();

        for (Map.Entry<UUID, Double> entry : sorted) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().distanceSquared(boss.getLocation()) > 100 * 100) continue;

            int baseRep = data.type.repReward;
            boolean isTop = entry.getKey().equals(topDamager);
            int bonus = isTop ? baseRep / 2 : 0;
            int totalRep = baseRep + bonus;

            // Репутация ВК
            try {
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId != -1) {
                    VKChatPlugin.getInstance().getApi().addReputation(vkId, totalRep);
                }
            } catch (Exception ignored) {}

            // Репутация Энда
            plugin.getEndManager().addEndReputation(p, totalRep / 2);

            p.sendMessage(ChatColor.GOLD + "☠ Босс повержен! +" + totalRep + " реп. ВК" + (isTop ? " (TOP!)" : ""));
        }

        // Объявление
        Player killer = boss.getKiller();
        String killerName = killer != null ? killer.getName() : "Игроки";
        Bukkit.broadcastMessage(ChatColor.GOLD + "☠ " + data.type.displayName + " повержен " + killerName + "!");

        // Лут
        e.getDrops().add(new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL, 4 + new Random().nextInt(8)));
        if (new Random().nextInt(100) < 30) {
            e.getDrops().add(new org.bukkit.inventory.ItemStack(Material.ELYTRA));
        }
    }

    public void saveAll() {
        // Сохранение данных боссов
    }
}
