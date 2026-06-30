package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
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
 * Эндер-мобы — уникальные существа Энда
 */
public class EndMobManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey endMobKey;
    private final NamespacedKey endMobTypeKey;

    // Типы эндер-мобов
    public enum EndMobType {
        // Обычные
        ENDER_ZOMBIE("Эндер-зомби", EntityType.ZOMBIE, 40, 6, ChatColor.DARK_PURPLE),
        ENDER_SKELETON("Эндер-скелет", EntityType.SKELETON, 35, 8, ChatColor.DARK_PURPLE),
        ENDER_SPIDER("Эндер-паук", EntityType.SPIDER, 30, 5, ChatColor.LIGHT_PURPLE),
        ENDER_CREEPER("Эндер-крипер", EntityType.CREEPER, 20, 12, ChatColor.DARK_PURPLE),

        // Редкие
        VOID_WALKER("Странник Бездны", EntityType.WITHER_SKELETON, 80, 10, ChatColor.DARK_GRAY),
        ENDER_WITCH("Эндер-ведьма", EntityType.WITCH, 60, 7, ChatColor.LIGHT_PURPLE),
        SHULKER_GUARDIAN("Страж Шалкеров", EntityType.SHULKER, 100, 8, ChatColor.GOLD),
        CHORUS_GOLEM("Хорус-голем", EntityType.IRON_GOLEM, 150, 12, ChatColor.DARK_PURPLE),

        // Эпические
        ENDER_PHANTOM("Эндер-фантом", EntityType.PHANTOM, 120, 15, ChatColor.DARK_PURPLE),
        VOID_DRAGON("Дракон Бездны", EntityType.ENDER_DRAGON, 200, 20, ChatColor.DARK_RED),
        ENDER_WARDEN("Эндер-хранитель", EntityType.IRON_GOLEM, 250, 25, ChatColor.RED),

        // Боссы
        ENDER_LORD("Повелитель Энда", EntityType.WITHER_SKELETON, 500, 30, ChatColor.GOLD),
        VOID_EMPEROR("Император Бездны", EntityType.ENDER_DRAGON, 1000, 50, ChatColor.DARK_RED);

        public final String displayName;
        public final EntityType entityType;
        public final double baseHealth;
        public final int baseDamage;
        public final ChatColor color;

        EndMobType(String displayName, EntityType entityType, double baseHealth, int baseDamage, ChatColor color) {
            this.displayName = displayName;
            this.entityType = entityType;
            this.baseHealth = baseHealth;
            this.baseDamage = baseDamage;
            this.color = color;
        }
    }

    // Активные эндер-мобы
    private final Map<UUID, EndMobData> activeMobs = new ConcurrentHashMap<>();

    private static class EndMobData {
        EndMobType type;
        int level;
        double healthMultiplier;
        double damageMultiplier;

        EndMobData(EndMobType type, int level) {
            this.type = type;
            this.level = level;
            this.healthMultiplier = 1.0 + (level * 0.2);
            this.damageMultiplier = 1.0 + (level * 0.15);
        }
    }

    public EndMobManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.endMobKey = new NamespacedKey(plugin, "end_mob");
        this.endMobTypeKey = new NamespacedKey(plugin, "end_mob_type");
        startSpawnTask();
    }

    /**
     * Задача спавна эндер-мобов
     */
    private void startSpawnTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnEndMobs();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Каждые 5 минут
    }

    /**
     * Спавн эндер-мобов в Энде
     */
    private void spawnEndMobs() {
        World endWorld = plugin.getEndWorld();
        if (endWorld == null) return;

        // Проверка лимита
        int maxMobs = plugin.getConfig().getInt("end.mobs.max-active", 50);
        if (activeMobs.size() >= maxMobs) return;

        // Спавн в случайных точках
        for (Player p : endWorld.getPlayers()) {
            if (new Random().nextInt(100) < 30) { // 30% шанс на игрока
                Location spawnLoc = p.getLocation().add(
                        new Random().nextInt(40) - 20,
                        0,
                        new Random().nextInt(40) - 20
                );
                spawnLoc.setY(endWorld.getHighestBlockYAt(spawnLoc) + 1);

                // Выбор типа моба
                EndMobType mobType = selectMobType(p);
                spawnEndMob(spawnLoc, mobType, getMobLevel(p));
            }
        }
    }

    /**
     * Выбрать тип моба на основе уровня игрока
     */
    private EndMobType selectMobType(Player p) {
        int endLevel = plugin.getEndManager().getEndLevel(p);
        Random rand = new Random();

        if (endLevel >= 8) {
            // Высокий уровень — все типы
            EndMobType[] types = EndMobType.values();
            return types[rand.nextInt(types.length)];
        } else if (endLevel >= 5) {
            // Средний уровень — редкие и обычные
            EndMobType[] types = {
                EndMobType.ENDER_ZOMBIE, EndMobType.ENDER_SKELETON,
                EndMobType.ENDER_SPIDER, EndMobType.VOID_WALKER,
                EndMobType.ENDER_WITCH, EndMobType.SHULKER_GUARDIAN
            };
            return types[rand.nextInt(types.length)];
        } else {
            // Низкий уровень — обычные
            EndMobType[] types = {
                EndMobType.ENDER_ZOMBIE, EndMobType.ENDER_SKELETON,
                EndMobType.ENDER_SPIDER, EndMobType.ENDER_CREEPER
            };
            return types[rand.nextInt(types.length)];
        }
    }

    /**
     * Получить уровень моба на основе игрока
     */
    private int getMobLevel(Player p) {
        int endLevel = plugin.getEndManager().getEndLevel(p);
        return Math.max(1, endLevel + new Random().nextInt(3) - 1);
    }

    /**
     * Заспавнить эндер-моба
     */
    public LivingEntity spawnEndMob(Location loc, EndMobType type, int level) {
        Entity entity = loc.getWorld().spawnEntity(loc, type.entityType);
        if (!(entity instanceof LivingEntity)) return null;
        LivingEntity mob = (LivingEntity) entity;

        // Настройка моба
        EndMobData mobData = new EndMobData(type, level);
        activeMobs.put(mob.getUniqueId(), mobData);

        // Здоровье
        double health = type.baseHealth * mobData.healthMultiplier;
        if (mob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            mob.setHealth(health);
        }

        // Урон
        if (mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(type.baseDamage * mobData.damageMultiplier);
        }

        // PDC метки
        mob.getPersistentDataContainer().set(endMobKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(endMobTypeKey, PersistentDataType.STRING, type.name());

        // Имя
        mob.setCustomName(type.color + "☠ " + type.displayName + " [" + level + "]");
        mob.setCustomNameVisible(true);
        mob.setGlowing(true);

        // Эффекты в зависимости от типа
        applyMobEffects(mob, type, level);

        return mob;
    }

    /**
     * Применить эффекты к мобу
     */
    private void applyMobEffects(LivingEntity mob, EndMobType type, int level) {
        switch (type) {
            case ENDER_ZOMBIE:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                break;
            case ENDER_SKELETON:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0));
                break;
            case ENDER_SPIDER:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 1));
                break;
            case ENDER_CREEPER:
                // Взрывчатка
                break;
            case VOID_WALKER:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
                break;
            case ENDER_WITCH:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1));
                break;
            case SHULKER_GUARDIAN:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2));
                break;
            case CHORUS_GOLEM:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
                break;
            case ENDER_PHANTOM:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                break;
            case VOID_DRAGON:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 2));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                break;
            case ENDER_WARDEN:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 3));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2));
                break;
            case ENDER_LORD:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 3));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 3));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                break;
            case VOID_EMPEROR:
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 4));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 3));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 4));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 3));
                break;
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (!mob.getPersistentDataContainer().has(endMobKey, PersistentDataType.INTEGER)) return;

        EndMobData mobData = activeMobs.remove(mob.getUniqueId());
        if (mobData == null) return;

        Player killer = mob.getKiller();
        if (killer == null) return;

        // Награды
        int baseRep = mobData.type.baseDamage * 2;
        int levelBonus = mobData.level * 10;
        int totalRep = baseRep + levelBonus;

        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(killer);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, totalRep);
            }
        } catch (Exception ignored) {}

        plugin.getEndManager().addEndReputation(killer, totalRep / 2);

        killer.sendMessage(mobData.type.color + "☠ " + mobData.type.displayName + " повержен! +" + totalRep + " реп.");

        // Дроп
        dropLoot(mob, mobData, killer);
    }

    /**
     * Дроп лута
     */
    private void dropLoot(LivingEntity mob, EndMobData mobData, Player killer) {
        Random rand = new Random();
        List<ItemStack> drops = new ArrayList<>();

        // Базовый дроп
        drops.add(new ItemStack(Material.ENDER_PEARL, 1 + rand.nextInt(3)));

        // Шанс на редкий дроп
        if (rand.nextInt(100) < 10) {
            drops.add(new ItemStack(Material.DIAMOND, 1 + rand.nextInt(2)));
        }

        // Шанс на эндер-артефакт
        if (rand.nextInt(100) < 5) {
            org.bukkit.inventory.ItemStack artifact = plugin.getEndArtifactManager().createRandomArtifact("rare");
            if (artifact != null) drops.add(artifact);
        }

        // Добавить дроп
        for (ItemStack drop : drops) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), drop);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity mob = (LivingEntity) e.getEntity();
        if (!mob.getPersistentDataContainer().has(endMobKey, PersistentDataType.INTEGER)) return;

        // Специальные способности при получении урона
        EndMobData mobData = activeMobs.get(mob.getUniqueId());
        if (mobData == null) return;

        // Телепортация при получении урона (для Эндер-мобов)
        if (mobData.type == EndMobType.VOID_WALKER || mobData.type == EndMobType.ENDER_PHANTOM) {
            if (new Random().nextInt(100) < 20) { // 20% шанс
                Location newLoc = mob.getLocation().add(
                        new Random().nextInt(10) - 5,
                        0,
                        new Random().nextInt(10) - 5
                );
                newLoc.setY(mob.getWorld().getHighestBlockYAt(newLoc) + 1);
                mob.teleport(newLoc);
                mob.getWorld().spawnParticle(Particle.PORTAL, mob.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    /**
     * Получить количество активных мобов
     */
    public int getActiveMobCount() {
        return activeMobs.size();
    }

    /**
     * Получить информацию о типах мобов
     */
    public String getMobTypesInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ ☠ Эндер-мобы ═══\n");

        sb.append(ChatColor.GRAY).append("Обычные:\n");
        for (EndMobType type : EndMobType.values()) {
            if (type.ordinal() < 4) {
                sb.append(type.color).append("• ").append(type.displayName);
                sb.append(ChatColor.GRAY).append(" — ").append((int) type.baseHealth).append(" HP, ");
                sb.append(type.baseDamage).append(" урон\n");
            }
        }

        sb.append(ChatColor.GRAY).append("\nРедкие:\n");
        for (EndMobType type : EndMobType.values()) {
            if (type.ordinal() >= 4 && type.ordinal() < 8) {
                sb.append(type.color).append("• ").append(type.displayName);
                sb.append(ChatColor.GRAY).append(" — ").append((int) type.baseHealth).append(" HP, ");
                sb.append(type.baseDamage).append(" урон\n");
            }
        }

        sb.append(ChatColor.GRAY).append("\nЭпические:\n");
        for (EndMobType type : EndMobType.values()) {
            if (type.ordinal() >= 8 && type.ordinal() < 11) {
                sb.append(type.color).append("• ").append(type.displayName);
                sb.append(ChatColor.GRAY).append(" — ").append((int) type.baseHealth).append(" HP, ");
                sb.append(type.baseDamage).append(" урон\n");
            }
        }

        sb.append(ChatColor.GRAY).append("\nБоссы:\n");
        for (EndMobType type : EndMobType.values()) {
            if (type.ordinal() >= 11) {
                sb.append(type.color).append("• ").append(type.displayName);
                sb.append(ChatColor.GRAY).append(" — ").append((int) type.baseHealth).append(" HP, ");
                sb.append(type.baseDamage).append(" урон\n");
            }
        }

        return sb.toString();
    }
}
