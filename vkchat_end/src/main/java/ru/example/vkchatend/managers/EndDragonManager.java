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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Улучшенный Эндер-Дракон — реально сложный босс
 */
public class EndDragonManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey dragonKey;
    private final NamespacedKey dragonPhaseKey;
    private final Map<UUID, DragonData> activeDragons = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageTracker = new ConcurrentHashMap<>();

    // Фазы дракона
    public enum DragonPhase {
        NORMAL("Обычный", 1.0, ChatColor.WHITE),
        ENRAGED("Разъярённый", 1.5, ChatColor.RED),
        FRENZIED("Безумный", 2.0, ChatColor.DARK_RED),
        ASCENDANT("Вознесённый", 3.0, ChatColor.DARK_PURPLE),
        GODLIKE("Божественный", 5.0, ChatColor.GOLD);

        public final String displayName;
        public final double multiplier;
        public final ChatColor color;

        DragonPhase(String displayName, double multiplier, ChatColor color) {
            this.displayName = displayName;
            this.multiplier = multiplier;
            this.color = color;
        }
    }

    private static class DragonData {
        DragonPhase phase;
        BossBar bossBar;
        int level;
        long spawnTime;
        int minionCount;
        boolean isHealing;
        double maxHealth;

        DragonData(int level, BossBar bossBar) {
            this.phase = DragonPhase.NORMAL;
            this.bossBar = bossBar;
            this.level = level;
            this.spawnTime = System.currentTimeMillis();
            this.minionCount = 0;
            this.isHealing = false;
            this.maxHealth = 1024 * (1 + level * 0.5);
        }
    }

    public EndDragonManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.dragonKey = new NamespacedKey(plugin, "end_dragon");
        this.dragonPhaseKey = new NamespacedKey(plugin, "dragon_phase");
    }

    /**
     * Заспавнить усиленного дракона
     */
    public EnderDragon spawnEnhancedDragon(Location loc, int level) {
        World world = loc.getWorld();
        if (world == null) return null;

        EnderDragon dragon = (EnderDragon) world.spawnEntity(loc, EntityType.ENDER_DRAGON);

        // Настройка здоровья
        double health = 1024 * (1 + level * 0.5);
        if (dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            dragon.setHealth(health);
        }

        // PDC метки
        dragon.getPersistentDataContainer().set(dragonKey, PersistentDataType.INTEGER, level);
        dragon.getPersistentDataContainer().set(dragonPhaseKey, PersistentDataType.STRING, DragonPhase.NORMAL.name());

        // BossBar
        BossBar bossBar = Bukkit.createBossBar(
                ChatColor.DARK_PURPLE + "☠ Эндер-Дракон [" + level + "]",
                BarColor.PURPLE,
                BarStyle.SEGMENTED_10
        );
        bossBar.setVisible(true);

        // Сохранить данные
        DragonData data = new DragonData(level, bossBar);
        activeDragons.put(dragon.getUniqueId(), data);
        damageTracker.put(dragon.getUniqueId(), new ConcurrentHashMap<>());

        // Обновление BossBar и фаз
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dragon.isValid() || dragon.isDead()) {
                    bossBar.removeAll();
                    activeDragons.remove(dragon.getUniqueId());
                    damageTracker.remove(dragon.getUniqueId());
                    cancel();
                    return;
                }

                // Обновление BossBar
                double progress = dragon.getHealth() / data.maxHealth;
                bossBar.setProgress(Math.max(0, Math.min(1, progress)));

                // Проверка смены фазы
                checkPhaseChange(dragon, data);

                // Спавн миньонов
                if (data.phase.ordinal() >= DragonPhase.ENRAGED.ordinal()) {
                    spawnMinions(dragon, data);
                }

                // Лечение в фазе вознесения
                if (data.phase == DragonPhase.ASCENDANT && !data.isHealing) {
                    data.isHealing = true;
                    dragon.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
                }

                // Телепортация в фазе безумия
                if (data.phase == DragonPhase.FRENZIED && new Random().nextInt(100) < 5) {
                    Location newLoc = dragon.getLocation().add(
                            new Random().nextInt(20) - 10,
                            0,
                            new Random().nextInt(20) - 10
                    );
                    dragon.teleport(newLoc);
                    world.spawnParticle(Particle.PORTAL, dragon.getLocation(), 30, 1, 1, 1, 0.1);
                }

                // AoE атака в фазе божественного
                if (data.phase == DragonPhase.GODLIKE && new Random().nextInt(100) < 10) {
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(dragon.getLocation()) < 100) {
                            p.damage(10 * data.level);
                            p.sendMessage(ChatColor.GOLD + "☠ Божественный Дракон наносит сокрушительный удар!");
                        }
                    }
                    world.spawnParticle(Particle.EXPLOSION_LARGE, dragon.getLocation(), 5, 2, 2, 2);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Уведомление
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GOLD + "☠ ЭНДЕР-ДРАКООН ПРОБУДИЛСЯ!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Уровень: " + level);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Здоровье: " + (int) health);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Это будет НЕПРОСТОЙ бой!");
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");

        return dragon;
    }

    /**
     * Проверить смену фазы
     */
    private void checkPhaseChange(EnderDragon dragon, DragonData data) {
        double healthPercent = dragon.getHealth() / data.maxHealth;
        DragonPhase newPhase;

        if (healthPercent > 0.75) {
            newPhase = DragonPhase.NORMAL;
        } else if (healthPercent > 0.50) {
            newPhase = DragonPhase.ENRAGED;
        } else if (healthPercent > 0.25) {
            newPhase = DragonPhase.FRENZIED;
        } else if (healthPercent > 0.10) {
            newPhase = DragonPhase.ASCENDANT;
        } else {
            newPhase = DragonPhase.GODLIKE;
        }

        if (newPhase != data.phase) {
            data.phase = newPhase;
            onPhaseChange(dragon, data, newPhase);
        }
    }

    /**
     * Обработка смены фазы
     */
    private void onPhaseChange(EnderDragon dragon, DragonData data, DragonPhase phase) {
        World world = dragon.getWorld();

        // Обновление BossBar
        data.bossBar.setTitle(phase.color + "☠ Эндер-Дракон [" + data.level + "] — " + phase.displayName);
        data.bossBar.setColor(phase == DragonPhase.GODLIKE ? BarColor.RED : BarColor.PURPLE);

        // Усиление дракона
        if (dragon.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseDamage = 10 * data.level;
            dragon.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseDamage * phase.multiplier);
        }

        // Эффекты
        dragon.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, phase.ordinal()));
        dragon.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, phase.ordinal()));

        // Уведомление
        Bukkit.broadcastMessage(phase.color + "☠ Дракон входит в фазу: " + phase.displayName + "!");

        // Спавн миньонов при смене фазы
        if (phase.ordinal() >= DragonPhase.ENRAGED.ordinal()) {
            int minionCount = phase.ordinal() * 2;
            for (int i = 0; i < minionCount; i++) {
                Location spawnLoc = dragon.getLocation().add(
                        new Random().nextInt(20) - 10,
                        0,
                        new Random().nextInt(20) - 10
                );
                EntityType minionType = phase == DragonPhase.GODLIKE ? EntityType.WITHER_SKELETON : EntityType.ENDERMITE;
                Entity minion = world.spawnEntity(spawnLoc, minionType);
                if (minion instanceof LivingEntity) {
                    ((LivingEntity) minion).setCustomName(ChatColor.DARK_PURPLE + "Прислужник Дракона");
                    ((LivingEntity) minion).setCustomNameVisible(true);
                    ((LivingEntity) minion).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                }
            }
        }

        // Визуальные эффекты
        world.spawnParticle(Particle.EXPLOSION_LARGE, dragon.getLocation(), 10, 3, 3, 3);
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.5f);
    }

    /**
     * Спавн миньонов
     */
    private void spawnMinions(EnderDragon dragon, DragonData data) {
        if (data.minionCount >= data.phase.ordinal() * 5) return;
        if (new Random().nextInt(100) > 10) return; // 10% шанс каждый тик

        World world = dragon.getWorld();
        Location spawnLoc = dragon.getLocation().add(
                new Random().nextInt(15) - 7,
                0,
                new Random().nextInt(15) - 7
        );

        EntityType minionType = data.phase == DragonPhase.GODLIKE ? EntityType.WITHER_SKELETON : EntityType.ENDERMITE;
        Entity minion = world.spawnEntity(spawnLoc, minionType);
        if (minion instanceof LivingEntity) {
            ((LivingEntity) minion).setCustomName(ChatColor.DARK_PURPLE + "Прислужник Дракона");
            ((LivingEntity) minion).setCustomNameVisible(true);
            ((LivingEntity) minion).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            data.minionCount++;
        }
    }

    @EventHandler
    public void onDragonDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderDragon)) return;
        EnderDragon dragon = (EnderDragon) e.getEntity();
        if (!dragon.getPersistentDataContainer().has(dragonKey, PersistentDataType.INTEGER)) return;

        // Записать урон
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            damageTracker.computeIfAbsent(dragon.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
        }

        // Снижение урона от стрел (фаза вознесения)
        DragonData data = activeDragons.get(dragon.getUniqueId());
        if (data != null && data.phase == DragonPhase.ASCENDANT) {
            if (e.getDamager() instanceof Arrow) {
                e.setDamage(e.getDamage() * 0.5); // -50% урона от стрел
            }
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof EnderDragon)) return;
        EnderDragon dragon = (EnderDragon) e.getEntity();
        if (!dragon.getPersistentDataContainer().has(dragonKey, PersistentDataType.INTEGER)) return;

        DragonData data = activeDragons.remove(dragon.getUniqueId());
        if (data == null) return;

        // Убрать BossBar
        if (data.bossBar != null) data.bossBar.removeAll();

        // Раздать награды
        Map<UUID, Double> damage = damageTracker.remove(dragon.getUniqueId());
        if (damage == null || damage.isEmpty()) return;

        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(damage.entrySet());
        sorted.sort((a, b2) -> Double.compare(b2.getValue(), a.getValue()));

        UUID topDamager = sorted.get(0).getKey();

        for (Map.Entry<UUID, Double> entry : sorted) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().distanceSquared(dragon.getLocation()) > 200 * 200) continue;

            boolean isTop = entry.getKey().equals(topDamager);
            int baseRep = 200 * data.level;
            int bonus = isTop ? baseRep : 0;
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

            p.sendMessage(ChatColor.GOLD + "☠ Дракон повержен! +" + totalRep + " реп. ВК" + (isTop ? " (TOP!)" : ""));
        }

        // Объявление
        Player killer = dragon.getKiller();
        String killerName = killer != null ? killer.getName() : "Игроки";
        Bukkit.broadcastMessage(ChatColor.GOLD + "☠ ЭНДЕР-ДРАКООН ПОВЕРЖЕН " + killerName + "!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Уровень дракона: " + data.level);

        // Лут
        Random rand = new Random();
        e.getDrops().add(new ItemStack(Material.DRAGON_EGG));
        e.getDrops().add(new ItemStack(Material.ELYTRA, 1 + rand.nextInt(2)));
        e.getDrops().add(new ItemStack(Material.DIAMOND, 10 + rand.nextInt(20)));
        e.getDrops().add(new ItemStack(Material.NETHERITE_INGOT, 1 + rand.nextInt(3)));
        e.getDrops().add(new ItemStack(Material.NETHER_STAR, 1 + rand.nextInt(2)));

        // Шанс на редкий артефакт
        if (rand.nextInt(100) < 30) {
            org.bukkit.inventory.ItemStack artifact = plugin.getEndArtifactManager().createRandomArtifact("legendary");
            if (artifact != null) e.getDrops().add(artifact);
        }
    }

    /**
     * Получить количество активных драконов
     */
    public int getActiveDragonCount() {
        return activeDragons.size();
    }

    /**
     * Получить информацию о драконах
     */
    public String getDragonInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ ☠ Эндер-Дракон ═══\n\n");
        sb.append(ChatColor.GRAY).append("Реально сложный босс с 5 фазами!\n\n");

        for (DragonPhase phase : DragonPhase.values()) {
            sb.append(phase.color).append("• ").append(phase.displayName);
            sb.append(ChatColor.GRAY).append(" — множитель x").append(String.format("%.1f", phase.multiplier));
            sb.append(ChatColor.DARK_GRAY).append(" (").append(getPhaseThreshold(phase)).append("% HP)\n");
        }

        sb.append("\n").append(ChatColor.RED).append("Особенности:\n");
        sb.append(ChatColor.GRAY).append("• Фаза ENRAGED: спавн миньонов\n");
        sb.append(ChatColor.GRAY).append("• Фаза FRENZIED: телепортация\n");
        sb.append(ChatColor.GRAY).append("• Фаза ASCENDANT: лечение + защита от стрел\n");
        sb.append(ChatColor.GRAY).append("• Фаза GODLIKE: AoE атаки + визери\n");

        return sb.toString();
    }

    private int getPhaseThreshold(DragonPhase phase) {
        switch (phase) {
            case NORMAL: return 100;
            case ENRAGED: return 75;
            case FRENZIED: return 50;
            case ASCENDANT: return 25;
            case GODLIKE: return 10;
            default: return 0;
        }
    }
}
