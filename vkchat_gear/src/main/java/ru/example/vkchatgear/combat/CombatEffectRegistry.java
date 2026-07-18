package ru.example.vkchatgear.combat;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CombatEffectRegistry — реестр боевых эффектов.
 *
 * Извлечён из CombatListener.onHitInternal() (700+ строк if-else).
 * Каждый эффект — именованный метод + регистрация.
 * CombatListener делегирует обработку реестру.
 *
 * Категории эффектов:
 * 1. Защитные зачарования брони (defensive enchants)
 * 2. Защитные проки редкости брони (defensive rarity procs)
 * 3. Защитные сет-эффекты при получении урона (defensive set effects)
 * 4. Спасение от смерти (death save)
 * 5. Атакующие проки редкости оружия (offensive rarity procs)
 * 6. Атакующие зачарования оружия (offensive enchants)
 * 7. Атакующие сет-эффекты при ударе (offensive set effects)
 */
public class CombatEffectRegistry {

    private final VKChatGearPlugin plugin;

    // Разделяемые кулдаун-мапы (переживают между событиями)
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    // ─── Регистрационные структуры ───

    /** Защитные зачарования: lore-name → эффект */
    private final Map<String, DefensiveEnchant> defenseEnchants = new LinkedHashMap<>();
    /** Защитные проки редкости: алиасы → шанс → эффект */
    private final List<DefensiveRarityProc> defenseRarityProcs = new ArrayList<>();
    /** Защитные сет-эффекты: setId → эффект */
    private final Map<String, DefensiveSetEffect> defenseSetEffects = new LinkedHashMap<>();
    /** Спасение от смерти */
    private final List<DeathSaveEffect> deathSaveEffects = new ArrayList<>();
    /** Атакующие проки редкости оружия */
    private final List<OffensiveRarityProc> offenseRarityProcs = new ArrayList<>();
    /** Атакующие зачарования оружия: lore-name → эффект */
    private final Map<String, OffensiveEnchant> offenseEnchants = new LinkedHashMap<>();
    /** Атакующие сет-эффекты */
    private final Map<String, OffensiveSetEffect> offenseSetEffects = new LinkedHashMap<>();

    // ─── Функциональные интерфейсы ───

    @FunctionalInterface
    public interface DefensiveEnchant {
        /** @return true если событие нужно отменить (cancel event) */
        boolean apply(CombatContext ctx, Player victim, LivingEntity attacker, ItemStack armor);
    }

    @FunctionalInterface
    public interface DefensiveSetEffect {
        /** @return true если событие нужно отменить */
        boolean apply(CombatContext ctx, Player victim, LivingEntity attacker);
    }

    @FunctionalInterface
    public interface DeathSaveEffect {
        /**
         * @param armorSlotIdx индекс слота брони (0-3)
         * @return true если смерть предотвращена
         */
        boolean apply(CombatContext ctx, Player victim, ItemStack armor, int armorSlotIdx);
    }

    public interface OffensiveRarityProc {
        void apply(CombatContext ctx, Player attacker, LivingEntity target, ItemStack weapon);
        String[] getAliases();
        int getChance();
    }

    @FunctionalInterface
    public interface OffensiveEnchant {
        /**
         * Применить эффект зачарования.
         * @return true если эффект сработал (занимает слот прок'а),
         *         false если не сработал (не занимает слот — например, не прошёл шанс)
         */
        boolean apply(CombatContext ctx, Player attacker, LivingEntity target);
    }

    @FunctionalInterface
    public interface OffensiveSetEffect {
        void apply(CombatContext ctx, Player attacker, LivingEntity target);
    }

    // ═══════════════════════════════════════
    // Constructor & Registration
    // ═══════════════════════════════════════

    public CombatEffectRegistry(VKChatGearPlugin plugin) {
        this.plugin = plugin;
        registerDefensiveEnchants();
        registerDefensiveRarityProcs();
        registerDefensiveSetEffects();
        registerDeathSaves();
        registerOffensiveRarityProcs();
        registerOffensiveEnchants();
        registerOffensiveSetEffects();
        plugin.getLogger().info("[CombatEffectRegistry] Зарегистрировано: " +
                defenseEnchants.size() + " защитных чар, " +
                defenseRarityProcs.size() + " защитных проков, " +
                defenseSetEffects.size() + " защитных сетов, " +
                deathSaveEffects.size() + " спасений, " +
                offenseRarityProcs.size() + " атакующих проков, " +
                offenseEnchants.size() + " атакующих чар, " +
                offenseSetEffects.size() + " атакующих сетов");
    }

    public Map<String, Long> getCooldowns() { return cooldowns; }
    public Map<UUID, Long> getMessageCooldowns() { return messageCooldowns; }

    public void cleanupCooldowns(long now) {
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > 600000);
        messageCooldowns.entrySet().removeIf(e -> now - e.getValue() > 600000);
    }

    // ═══════════════════════════════════════
    // 1. ЗАЩИТНЫЕ ЗАЧАРОВАНИЯ БРОНИ
    // ═══════════════════════════════════════

    private void registerDefensiveEnchants() {
        // Уклонение — 10% шанс полностью избежать урона
        registerDefense("Уклонение", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(10)) return false;
            ctx.getEvent().setCancelled(true);
            victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            victim.sendMessage(ChatColor.WHITE + " Вы уклонились от атаки!");
            return true;
        });

        // Кровавые шипы — 30% шанс вернуть урон
        registerDefense("Кровавые шипы", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(30)) return false;
            attacker.damage(ctx.getEvent().getDamage() * 0.3);
            attacker.getWorld().spawnParticle(Particle.REDSTONE, attacker.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5,
                    new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
            return false;
        });

        // Огненная аура — 20% шанс поджечь атакующего
        registerDefense("Огненная аура", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(20)) return false;
            attacker.setFireTicks(80);
            return false;
        });

        // Эгида — при ХП ≤ 20% даёт Сопротивление V на 5 сек
        registerDefense("Эгида", (ctx, victim, attacker, armor) -> {
            if (ctx.getHpPercent(victim) > 0.20) return false;
            if (victim.hasPotionEffect(PotionEffectType.DAMAGE_RESISTANCE)) return false;
            ctx.addPotion(victim, PotionEffectType.DAMAGE_RESISTANCE, 100, 4);
            victim.sendMessage(ChatColor.AQUA + " Эгида активирована! Вы защищены.");
            return false;
        });

        // Поглощение — 15% шанс получить Золотые сердечки
        registerDefense("Поглощение", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(15)) return false;
            ctx.addPotion(victim, PotionEffectType.ABSORPTION, 100, 1);
            return false;
        });

        // Эндер Щит — 5% шанс телепортации
        registerDefense("Эндер Щит", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(5)) return false;
            Location loc = victim.getLocation().add(
                    ThreadLocalRandom.current().nextInt(10) - 5, 0,
                    ThreadLocalRandom.current().nextInt(10) - 5);
            victim.teleport(loc);
            return false;
        });

        // Зеркало — 15% шанс отразить магический/снарядный урон
        registerDefense("Зеркало", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(15)) return false;
            EntityDamageByEntityEvent.DamageCause cause = ctx.getEvent().getCause();
            if (cause == EntityDamageByEntityEvent.DamageCause.MAGIC || cause == EntityDamageByEntityEvent.DamageCause.PROJECTILE) {
                attacker.damage(ctx.getEvent().getDamage());
                ctx.getEvent().setCancelled(true);
                victim.getWorld().spawnParticle(Particle.SPELL_WITCH, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                return true;
            }
            return false;
        });

        // Связь Душ — 10% шанс вернуть 50% урона и исцелиться
        registerDefense("Связь Душ", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(10)) return false;
            double dmg = ctx.getEvent().getDamage() * 0.50;
            attacker.damage(dmg, victim);
            ctx.heal(victim, dmg);
            victim.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 [Связь Душ] Вы вернули " + String.format("%.1f", dmg) + " урона и исцелились!");
            victim.getWorld().spawnParticle(Particle.SPELL_WITCH, victim.getLocation().add(0, 1.0, 0), 25, 0.3, 0.3, 0.3);
            attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1.0, 0), 10);
            return false;
        });

        // Каменная кожа — 12% шанс снизить урон на 40%, замедлить атакующего
        registerDefense("Каменная кожа", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(12)) return false;
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 0.60);
            ctx.addPotion(attacker, PotionEffectType.SLOW, 60, 0);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.0, 0), 15, 0.3, 0.5, 0.3, 0.1);
            victim.sendMessage(ChatColor.GRAY + "🛡 [Каменная кожа] Урон снижен на 40%, атакующий замедлен!");
            return false;
        });

        // Связь жизней — 10% шанс перенаправить 20% урона ближайшему союзнику
        registerDefense("Связь жизней", (ctx, victim, attacker, armor) -> {
            if (!ctx.rollChance(10)) return false;
            double redirectDmg = ctx.getEvent().getDamage() * 0.20;
            LivingEntity nearestAlly = null;
            double nearestDist = 5.0;
            for (org.bukkit.entity.Entity near : victim.getNearbyEntities(5, 5, 5)) {
                if (near instanceof Player && near != victim && near != attacker) {
                    double dist = near.getLocation().distance(victim.getLocation());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestAlly = (LivingEntity) near;
                    }
                }
            }
            if (nearestAlly != null) {
                nearestAlly.damage(redirectDmg, attacker);
                ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 0.80);
                victim.sendMessage(ChatColor.RED + "❤ [Связь жизней] 20% урона перенаправлено на ближайшего союзника!");
                nearestAlly.sendMessage(ChatColor.RED + "❤ [Связь жизней] Вы приняли на себя часть урона союзника!");
                victim.getWorld().spawnParticle(Particle.HEART, victim.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3);
                nearestAlly.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, nearestAlly.getLocation().add(0, 1.0, 0), 5);
            }
            return false;
        });
    }

    private void registerDefense(String name, DefensiveEnchant effect) {
        defenseEnchants.put(name, effect);
    }

    // ═══════════════════════════════════════
    // 2. ЗАЩИТНЫЕ ПРОКИ РЕДКОСТИ БРОНИ
    // ═══════════════════════════════════════

    private void registerDefensiveRarityProcs() {
        // Астральный Барьер / Щит Сварога / Оберег — 12% шанс
        defenseRarityProcs.add(new DefensiveRarityProc(
                new String[]{"Астральный Барьер", "Щит Сварога", "Оберег"}, 12,
                (ctx, victim, attacker, armor) -> {
                    ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 0.65);
                    ctx.addPotion(victim, PotionEffectType.ABSORPTION, 80, 1);
                    victim.getWorld().spawnParticle(Particle.SPELL_WITCH, victim.getLocation().add(0, 1.0, 0), 35, 0.5, 0.7, 0.5, 0.08);
                    victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
                    victim.sendMessage(ChatColor.AQUA + "✦ [Астральный Барьер] Удар частично рассеян.");
                }));

        // Развеивание / Очищение — 10% шанс
        defenseRarityProcs.add(new DefensiveRarityProc(
                new String[]{"Развеивание", "Очищение"}, 10,
                (ctx, victim, attacker, armor) -> {
                    CombatContext.cleanseNegativeEffects(victim);
                    victim.getWorld().spawnParticle(Particle.END_ROD, victim.getLocation().add(0, 1.0, 0), 25, 0.4, 0.6, 0.4, 0.04);
                    victim.playSound(victim.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.6f);
                    victim.sendMessage(ChatColor.LIGHT_PURPLE + "✦ [Развеивание] Негативные эффекты рассеяны.");
                }));
    }

    // ═══════════════════════════════════════
    // 3. ЗАЩИТНЫЕ СЕТ-ЭФФЕКТЫ
    // ═══════════════════════════════════════

    private void registerDefensiveSetEffects() {
        // Грозовой Доспех (perun) — 15% шанс контратаки молнией
        registerDefenseSet("perun", (ctx, victim, attacker) -> {
            if (!ctx.rollChance(15)) return false;
            attacker.getWorld().strikeLightningEffect(attacker.getLocation());
            attacker.damage(5.0, victim);
            victim.sendMessage(ChatColor.YELLOW + " Грозовой разряд ударил вашего обидчика!");
            return false;
        });

        // Танкист — дымовая завеса при ХП ≤ 20%, кд 45 сек
        registerDefenseSet("tankist", (ctx, victim, attacker) -> {
            if (ctx.getHpPercent(victim) > 0.20) return false;
            if (!ctx.isMetaCooldownReady(victim, "tankist_smoke_cooldown", 45000L)) return false;
            ctx.setMetaCooldown(victim, "tankist_smoke_cooldown");
            ctx.addPotion(victim, PotionEffectType.INVISIBILITY, 100, 1);
            ctx.addPotion(victim, PotionEffectType.SPEED, 100, 2);
            victim.sendMessage(ChatColor.DARK_RED + "💨 [Спецагент] Сработала дымовая завеса! Вы получили невидимость и скорость!");
            victim.playSound(victim.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.8f);
            victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation(), 80, 1.0, 0.5, 1.0, 0.1);
            victim.getWorld().spawnParticle(Particle.SMOKE_LARGE, victim.getLocation(), 80, 1.0, 0.5, 1.0, 0.1);
            for (org.bukkit.entity.Entity ent : victim.getNearbyEntities(5, 5, 5)) {
                if (ent instanceof LivingEntity && ent != victim) {
                    LivingEntity le = (LivingEntity) ent;
                    ctx.addPotion(le, PotionEffectType.BLINDNESS, 80, 0);
                    ctx.addPotion(le, PotionEffectType.SLOW, 80, 2);
                }
            }
            return false;
        });

        // Богатырь — щит бессмертия при ХП ≤ 15%, кд 2 мин
        registerDefenseSet("bogatyr", (ctx, victim, attacker) -> {
            if (ctx.getHpPercent(victim) > 0.15) return false;
            if (!ctx.isMetaCooldownReady(victim, "bogatyr_shield_cooldown", 120000L)) return false;
            ctx.setMetaCooldown(victim, "bogatyr_shield_cooldown");
            ctx.getEvent().setCancelled(true);
            ctx.addPotion(victim, PotionEffectType.DAMAGE_RESISTANCE, 100, 4);
            victim.sendMessage(ChatColor.GOLD + "🛡️ [Богатырский Щит] Ваше здоровье критическое! Пробужден щит бессмертия на 5 секунд!");
            victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.8f);
            victim.getWorld().spawnParticle(Particle.TOTEM, victim.getLocation(), 100, 0.8, 1.0, 0.8, 0.2);
            return true;
        });

        // Костяной Доспех — щит бессмертия при ХП ≤ 20%, кд 90 сек
        registerDefenseSet("bone_armor", (ctx, victim, attacker) -> {
            if (ctx.getHpPercent(victim) > 0.20) return false;
            if (!ctx.isMetaCooldownReady(victim, "bone_armor_shield_cooldown", 90000L)) return false;
            ctx.setMetaCooldown(victim, "bone_armor_shield_cooldown");
            ctx.getEvent().setCancelled(true);
            ctx.addPotion(victim, PotionEffectType.DAMAGE_RESISTANCE, 100, 4);
            victim.sendMessage(ChatColor.DARK_PURPLE + "🦴 [Костяной Щит] Ваше здоровье критическое! Доспех пробудил древний щит на 5 секунд!");
            victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.6f);
            victim.getWorld().spawnParticle(Particle.SMOKE_LARGE, victim.getLocation(), 80, 0.8, 1.0, 0.8, 0.2);
            return true;
        });

        // Пепельная Корона (defensive) — поджигает атакующего
        registerDefenseSet("ember_crown", (ctx, victim, attacker) -> {
            attacker.setFireTicks(60);
            victim.getWorld().spawnParticle(Particle.FLAME, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.02);
            return false;
        });
    }

    private void registerDefenseSet(String setId, DefensiveSetEffect effect) {
        defenseSetEffects.put(setId, effect);
    }

    // ═══════════════════════════════════════
    // 4. СПАСЕНИЕ ОТ СМЕРТИ
    // ═══════════════════════════════════════

    private void registerDeathSaves() {
        // Второе дыхание — при смертельном ударе восстанавливает 50% ХП
        deathSaveEffects.add((ctx, victim, armor, armorSlotIdx) -> {
            ctx.getEvent().setCancelled(true);
            victim.setHealth(ctx.getMaxHp(victim) / 2);
            victim.getWorld().spawnParticle(Particle.TOTEM, victim.getLocation(), 100, 1, 1, 1, 0.1);

            boolean isSetPiece = plugin.getGearManager().isLegalSetPiece(armor);

            if (isSetPiece) {
                NamespacedKey lvlKey = new NamespacedKey(plugin, "upgrade_level");
                int currentLvl = armor.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(lvlKey, PersistentDataType.INTEGER, 0);
                int newLvl = Math.max(0, currentLvl - 5);
                plugin.getGearManager().updateGearUpgradeLevel(armor, newLvl);

                ItemMeta armorMeta = armor.getItemMeta();
                if (armorMeta instanceof org.bukkit.inventory.meta.Damageable dmgMeta) {
                    dmgMeta.setDamage(armor.getType().getMaxDurability() - 1);
                    armor.setItemMeta((ItemMeta) dmgMeta);
                }
                victim.sendMessage(ChatColor.YELLOW + "🛡️ [Второе Дыхание] Сработало спасение! Твой ценный сет брони " +
                        armor.getItemMeta().getDisplayName() + " не пропал, но потерял -5 уровней заточки и сломан до 1 прочности!");
            } else {
                victim.sendMessage(ChatColor.YELLOW + " Сработало Второе дыхание! Обычная броня была уничтожена.");
                ItemStack[] contents = victim.getInventory().getArmorContents();
                contents[armorSlotIdx] = null;
                victim.getInventory().setArmorContents(contents);
            }
            return true;
        });
    }

    // ═══════════════════════════════════════
    // 5. АТАКУЮЩИЕ ПРОКИ РЕДКОСТИ ОРУЖИЯ
    // ═══════════════════════════════════════

    private void registerOffensiveRarityProcs() {
        // Грозовой Импульс / Воля Грозаа — 12% шанс
        offenseRarityProcs.add(new OffensiveRarityEntry(
                new String[]{"Грозовой Импульс", "Воля Грозы"}, 12,
                (ctx, attacker, target, weapon) -> {
                    double procDamage = 4.0 + Math.min(8.0, ctx.getEvent().getDamage() * 0.20);
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.damage(procDamage, attacker);
                    for (org.bukkit.entity.Entity near : target.getNearbyEntities(3, 3, 3)) {
                        if (near instanceof LivingEntity && near != attacker && near != target)
                            ((LivingEntity) near).damage(procDamage * 0.45, attacker);
                    }
                    ctx.sendMessage(attacker, ChatColor.YELLOW + "✦ [Грозовой Импульс] Разряд прошёл по цели.");
                }));

        // Багровый Резонанс / Кровь Рода — 14% шанс
        offenseRarityProcs.add(new OffensiveRarityEntry(
                new String[]{"Багровый Резонанс", "Кровь Рода"}, 14,
                (ctx, attacker, target, weapon) -> {
                    double heal = Math.min(6.0, Math.max(1.0, ctx.getEvent().getFinalDamage() * 0.22));
                    ctx.heal(attacker, heal);
                    attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.2, 0), 5, 0.4, 0.4, 0.4, 0.03);
                    ctx.sendMessage(attacker, ChatColor.RED + "✦ [Багровый Резонанс] Восстановлено " + String.format("%.1f", heal) + " HP.");
                }));

        // Похищение Жизни / Вампиризм — 16% шанс
        offenseRarityProcs.add(new OffensiveRarityEntry(
                new String[]{"Похищение Жизни", "Вампиризм"}, 16,
                (ctx, attacker, target, weapon) -> {
                    double heal = Math.min(4.0, Math.max(1.0, ctx.getEvent().getFinalDamage() * 0.18));
                    ctx.heal(attacker, heal);
                    target.getWorld().spawnParticle(Particle.REDSTONE, target.getLocation().add(0, 1.0, 0), 12, 0.25, 0.35, 0.25,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 80), 1.2f));
                }));

        // Пламенный Контур — 12% шанс
        offenseRarityProcs.add(new OffensiveRarityEntry(
                new String[]{"Пламенный Контур"}, 12,
                (ctx, attacker, target, weapon) -> {
                    target.setFireTicks(Math.max(target.getFireTicks(), 80));
                    ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 1.10);
                    target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1.0, 0), 20, 0.35, 0.35, 0.35, 0.04);
                }));
    }

    // ═══════════════════════════════════════
    // 6. АТАКУЮЩИЕ ЗАЧАРОВАНИЯ ОРУЖИЯ
    // ═══════════════════════════════════════

    private void registerOffensiveEnchants() {
        // Вампиризм — 20% исцеление от урона (всегда срабатывает)
        registerOffense("Вампиризм", (ctx, attacker, target) -> {
            double heal = ctx.getEvent().getFinalDamage() * 0.2;
            ctx.heal(attacker, heal);
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3);
            return true;
        });

        // Отравление (всегда срабатывает)
        registerOffense("Отравление", (ctx, attacker, target) -> {
            ctx.addPotion(target, PotionEffectType.POISON, 100, 1);
            return true;
        });

        // Метеоритный Удар — 10% шанс, кд 3 сек
        registerOffense("Метеоритный Удар", (ctx, attacker, target) -> {
            if (!ctx.rollChance(10)) return false;
            if (!ctx.checkCooldown("meteor:" + attacker.getUniqueId(), 3000L)) return false;
            target.getWorld().createExplosion(target.getLocation(), 1.5f, false, false, attacker);
            return true;
        });

        // Грозовой Разряд — 15% шанс, кд 1.5 сек
        registerOffense("Грозовой Разряд", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.damage(5.0, attacker);
            return true;
        });

        // Окоченение (всегда срабатывает)
        registerOffense("Окоченение", (ctx, attacker, target) -> {
            ctx.addPotion(target, PotionEffectType.SLOW, 60, 2);
            return true;
        });

        // Мрак — 20% шанс
        registerOffense("Мрак", (ctx, attacker, target) -> {
            if (!ctx.rollChance(20)) return false;
            ctx.addPotion(target, PotionEffectType.BLINDNESS, 60, 0);
            return true;
        });

        // Гниль (всегда срабатывает)
        registerOffense("Гниль", (ctx, attacker, target) -> {
            ctx.addPotion(target, PotionEffectType.WITHER, 100, 1);
            return true;
        });

        // Подбрасывание — 15% шанс
        registerOffense("Подбрасывание", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            ctx.addPotion(target, PotionEffectType.LEVITATION, 20, 4);
            return true;
        });

        // Бронебойность — частичный игнор брони + 8% урона (всегда срабатывает)
        registerOffense("Бронебойность", (ctx, attacker, target) -> {
            ctx.getEvent().setDamage(EntityDamageByEntityEvent.DamageModifier.ARMOR, 0);
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 1.08);
            return true;
        });

        // Берсерк — +0.5 урона за каждое отсутствующее ХП (всегда срабатывает)
        registerOffense("Берсерк", (ctx, attacker, target) -> {
            double missingHp = ctx.getMaxHp(attacker) - attacker.getHealth();
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() + (missingHp * 0.5));
            return true;
        });

        // Разоружение — 5% шанс
        registerOffense("Разоружение", (ctx, attacker, target) -> {
            if (!ctx.rollChance(5)) return false;
            if (target instanceof Player) {
                Player tPlayer = (Player) target;
                ItemStack hand = tPlayer.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() != Material.AIR) {
                    tPlayer.getWorld().dropItemNaturally(tPlayer.getLocation(), hand);
                    tPlayer.getInventory().setItemInMainHand(null);
                    tPlayer.sendMessage(ChatColor.RED + " У вас выбили оружие из рук!");
                }
            }
            return true;
        });

        // Жнец Душ — 5% шанс казни при ХП ≤ 15%
        registerOffense("Жнец Душ", (ctx, attacker, target) -> {
            if (!ctx.rollChance(5)) return false;
            if (!(target instanceof Boss)) {
                if (ctx.getHpPercent(target) <= 0.15) {
                    target.setHealth(0);
                    attacker.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
                }
            }
            return true;
        });

        // Критический Удар — 15% шанс +35% урона
        registerOffense("Критический Удар", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 1.35);
            attacker.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation(), 20);
            return true;
        });

        // Взрыв Иссушения — 10% шанс, кд 1.5 сек
        registerOffense("Взрыв Иссушения", (ctx, attacker, target) -> {
            if (!ctx.rollChance(10)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            for (org.bukkit.entity.Entity ent : target.getNearbyEntities(3, 3, 3)) {
                if (ent instanceof LivingEntity && ent != attacker) {
                    ctx.addPotion((LivingEntity) ent, PotionEffectType.WITHER, 60, 1);
                }
            }
            return true;
        });

        // Ядовитое Облако — 15% шанс, кд 1.5 сек
        registerOffense("Ядовитое Облако", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            for (org.bukkit.entity.Entity ent : target.getNearbyEntities(3, 3, 3)) {
                if (ent instanceof LivingEntity && ent != attacker) {
                    ctx.addPotion((LivingEntity) ent, PotionEffectType.POISON, 60, 0);
                }
            }
            return true;
        });

        // Удар Грома — 10% шанс, кд 1.5 сек
        registerOffense("Удар Грома", (ctx, attacker, target) -> {
            if (!ctx.rollChance(10)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            target.getWorld().strikeLightningEffect(target.getLocation());
            for (org.bukkit.entity.Entity ent : target.getNearbyEntities(3, 3, 3)) {
                if (ent instanceof LivingEntity && ent != attacker) {
                    ((LivingEntity) ent).damage(5.0);
                }
            }
            return true;
        });

        // Аура Вампира — 30% исцеление от урона (всегда срабатывает)
        registerOffense("Аура Вампира", (ctx, attacker, target) -> {
            double heal = ctx.getEvent().getFinalDamage() * 0.3;
            ctx.heal(attacker, heal);
            return true;
        });

        // Казнь — +25% урона при ХП цели ≤ 30% (всегда занимает слот прок'а)
        registerOffense("Казнь", (ctx, attacker, target) -> {
            if (ctx.getHpPercent(target) <= 0.3) {
                ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 1.25);
                attacker.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
            }
            return true;
        });

        // Похищение Жизни (оружейное) — +1 HP (всегда срабатывает)
        registerOffense("Похищение Жизни", (ctx, attacker, target) -> {
            ctx.heal(attacker, 1.0);
            return true;
        });

        // Огненный Удар (всегда срабатывает)
        registerOffense("Огненный Удар", (ctx, attacker, target) -> {
            target.setFireTicks(80);
            return true;
        });

        // Паралич — 10% шанс
        registerOffense("Паралич", (ctx, attacker, target) -> {
            if (!ctx.rollChance(10)) return false;
            ctx.addPotion(target, PotionEffectType.SLOW, 40, 5);
            ctx.addPotion(target, PotionEffectType.BLINDNESS, 40, 0);
            ctx.sendMessage(attacker, ChatColor.YELLOW + " Цель парализована!");
            return true;
        });

        // Метеоритный Дождь — 10% шанс, кд 10 сек
        registerOffense("Метеоритный Дождь", (ctx, attacker, target) -> {
            if (!ctx.rollChance(10)) return false;
            if (!ctx.checkCooldown("meteor_shower:" + attacker.getUniqueId(), 10000L)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            ctx.sendMessage(attacker, ChatColor.GOLD + "☄️ [Метеоритный Дождь] Огненная волна накрыла врагов вокруг!");
            for (int k = 0; k < 3; k++) {
                Location loc = target.getLocation().clone().add(
                        ThreadLocalRandom.current().nextInt(6) - 3, 0,
                        ThreadLocalRandom.current().nextInt(6) - 3);
                target.getWorld().createExplosion(loc, 1.0f, false, false, attacker);
            }
            return true;
        });

        // Ледяное Касание — 15% шанс заморозить
        registerOffense("Ледяное Касание", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            ctx.addPotion(target, PotionEffectType.SLOW, 60, 9);
            ctx.addPotion(target, PotionEffectType.BLINDNESS, 60, 0);
            ctx.sendMessage(attacker, ChatColor.BLUE + "❄️ [Ледяное Касание] Вы заморозили цель на 3 секунды!");
            target.getWorld().spawnParticle(Particle.SNOWBALL, target.getLocation().add(0, 1.0, 0), 30, 0.3, 0.5, 0.3, 0.1);
            return true;
        });

        // Распад — 5% шанс двойной урон + Иссушение III
        registerOffense("Распад", (ctx, attacker, target) -> {
            if (!ctx.rollChance(5)) return false;
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 1.45);
            ctx.addPotion(target, PotionEffectType.WITHER, 100, 2);
            ctx.sendMessage(attacker, ChatColor.DARK_RED + "☠️ [Распад] Цель дезинтегрирована! Двойной урон и увядание III!");
            target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1.0, 0), 30, 0.4, 0.5, 0.4, 0.1);
            return true;
        });

        // Аура Вампиризма — 15% шанс AOE вампиризм, кд 1.5 сек
        registerOffense("Аура Вампиризма", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            double totalHeal = 0.0;
            int count = 0;
            for (org.bukkit.entity.Entity near : target.getNearbyEntities(4, 4, 4)) {
                if (near instanceof LivingEntity && near != attacker) {
                    LivingEntity enemy = (LivingEntity) near;
                    enemy.damage(3.0, attacker);
                    totalHeal += 1.5;
                    count++;
                    enemy.getWorld().spawnParticle(Particle.REDSTONE, enemy.getLocation().add(0, 1.0, 0), 10, 0.2, 0.2, 0.2,
                            new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                }
            }
            if (count > 0) {
                ctx.heal(attacker, totalHeal);
                ctx.sendMessage(attacker, ChatColor.DARK_RED + "✨ [Аура Вампиризма] Вы похитили ХП у " + count +
                        " противников в радиусе, восстановив +" + String.format("%.1f", totalHeal) + " HP!");
                Location pLoc = attacker.getLocation();
                for (int i = 0; i < 30; i++) {
                    double angle = i * (2 * Math.PI / 30);
                    double rx = pLoc.getX() + 4.0 * Math.cos(angle);
                    double rz = pLoc.getZ() + 4.0 * Math.sin(angle);
                    pLoc.getWorld().spawnParticle(Particle.REDSTONE, new Location(pLoc.getWorld(), rx, pLoc.getY() + 0.1, rz),
                            1, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 2.0f));
                }
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_BAT_DEATH, 1.0f, 0.5f);
            }
            return true;
        });

        // Вытягивание душ — 15% шанс
        registerOffense("Вытягивание душ", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return false;
            target.damage(3.0, attacker);
            ctx.heal(attacker, 2.0);
            target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1.0, 0), 15, 0.3, 0.5, 0.3, 0.05);
            ctx.sendMessage(attacker, ChatColor.DARK_PURPLE + "☠ [Вытягивание душ] Вы вытянули жизненную силу из цели!");
            if (ctx.rollChance(8)) {
                ctx.addPotion(target, PotionEffectType.WITHER, 60, 0);
                ctx.sendMessage(attacker, ChatColor.DARK_PURPLE + "☠ [Вытягивание душ] Цель поражена Иссушением!");
            }
            return true;
        });

        // Цепная молния — 10% шанс, кд 1.5 сек
        registerOffense("Цепная молния", (ctx, attacker, target) -> {
            if (!ctx.rollChance(10)) return false;
            if (!ctx.checkCooldown("enchant:" + attacker.getUniqueId(), 1500L)) return false;
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.damage(4.0, attacker);
            ctx.sendMessage(attacker, ChatColor.AQUA + "⚡ [Цепная молния] Разряд поразил цель!");
            int chained = 0;
            for (org.bukkit.entity.Entity near : target.getNearbyEntities(3, 3, 3)) {
                if (chained >= 2) break;
                if (near instanceof LivingEntity && near != attacker && near != target) {
                    LivingEntity enemy = (LivingEntity) near;
                    enemy.getWorld().strikeLightningEffect(enemy.getLocation());
                    enemy.damage(4.0, attacker);
                    chained++;
                }
            }
            if (chained > 0) {
                ctx.sendMessage(attacker, ChatColor.AQUA + "⚡ [Цепная молния] Молния перескочила на " + chained + " врагов!");
            }
            return true;
        });

        // Удар Бездны — 8% шанс телепорт + 1.5x урон
        registerOffense("Удар Бездны", (ctx, attacker, target) -> {
            if (!ctx.rollChance(8)) return false;
            Location behind = target.getLocation().clone();
            behind.setZ(behind.getZ() + 1);
            behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
            attacker.teleport(behind);
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() * 1.5);
            target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1.0, 0), 30, 0.3, 0.5, 0.3, 0.1);
            ctx.sendMessage(attacker, ChatColor.DARK_GRAY + "⚔ [Удар Бездны] Вы телепортировались за спину врага!");
            if (target instanceof Player) {
                target.sendMessage(ChatColor.DARK_GRAY + "⚔ [Удар Бездны] Противник материализовался у вас за спиной!");
            }
            return true;
        });
    }

    private void registerOffense(String name, OffensiveEnchant effect) {
        offenseEnchants.put(name, effect);
    }

    // ═══════════════════════════════════════
    // 7. АТАКУЮЩИЕ СЕТ-ЭФФЕКТЫ
    // ═══════════════════════════════════════

    private void registerOffensiveSetEffects() {
        // Чернобог — 20% шанс Иссушение
        registerOffenseSet("chernobog", (ctx, attacker, target) -> {
            if (!ctx.rollChance(20)) return;
            ctx.addPotion(target, PotionEffectType.WITHER, 100, 1);
            attacker.getWorld().spawnParticle(Particle.SPELL_WITCH, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
        });

        // Кощей — 15% вампиризм от нанесённого урона
        registerOffenseSet("koshchey", (ctx, attacker, target) -> {
            double heal = ctx.getEvent().getFinalDamage() * 0.15;
            ctx.heal(attacker, heal);
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2);
        });

        // Клинок Тени — 15% шанс 15% вампиризм
        registerOffenseSet("shadow_blade", (ctx, attacker, target) -> {
            if (!ctx.rollChance(15)) return;
            double heal = ctx.getEvent().getFinalDamage() * 0.15;
            ctx.heal(attacker, heal);
            attacker.getWorld().spawnParticle(Particle.SPELL_WITCH, attacker.getLocation().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2);
            ctx.sendMessage(attacker, ChatColor.DARK_PURPLE + "🗡️ [Клинок Тени] Вы высосли жизнь из противника!");
        });

        // Пепельная Корона (offensive) — 25% шанс поджечь
        registerOffenseSet("ember_crown", (ctx, attacker, target) -> {
            if (!ctx.rollChance(25)) return;
            target.setFireTicks(80);
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.02);
            ctx.sendMessage(attacker, ChatColor.GOLD + "🔥 [Пепельная Корона] Пламя обрушилось на врага!");
        });

        // Моровой Туман — 20% шанс AoE Poison II
        registerOffenseSet("plague_mist", (ctx, attacker, target) -> {
            if (!ctx.rollChance(20)) return;
            for (org.bukkit.entity.Entity ent : target.getNearbyEntities(5, 5, 5)) {
                if (ent instanceof LivingEntity && ent != attacker) {
                    ctx.addPotion((LivingEntity) ent, PotionEffectType.POISON, 100, 1);
                }
            }
            ctx.addPotion(target, PotionEffectType.POISON, 100, 1);
            target.getWorld().spawnParticle(Particle.SPELL_MOB, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05,
                    org.bukkit.Color.fromRGB(80, 200, 80));
            ctx.sendMessage(attacker, ChatColor.GREEN + "☠️ [Моровой Туман] Ядовитый туман окурал врага!");
        });

        // Ясный Сокол — казнь при ХП цели ≤ 25%, 20% шанс
        registerOffenseSet("sokol", (ctx, attacker, target) -> {
            double maxHp = ctx.getMaxHp(target);
            if (target.getHealth() / maxHp <= 0.25) {
                if (!ctx.rollChance(20)) return;
                ctx.getEvent().setDamage(Math.max(ctx.getEvent().getDamage() * 2.0, 12.0));
                ctx.sendMessage(attacker, ChatColor.RED + "⚔️ [Опричная Казнь] Вы казнили раненого противника!");
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.15);
                target.getWorld().spawnParticle(Particle.REDSTONE, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
            }
        });
    }

    private void registerOffenseSet(String setId, OffensiveSetEffect effect) {
        offenseSetEffects.put(setId, effect);
    }

    // ═══════════════════════════════════════
    // PROCESSING — вызываются из CombatListener
    // ═══════════════════════════════════════

    /**
     * Обработать защитные зачарования брони жертвы.
     * @return true если событие отменено (dodge/reflect)
     */
    public boolean processDefensiveEnchants(CombatContext ctx, Player victim, LivingEntity attacker) {
        for (ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta() || !armor.getItemMeta().hasLore()) continue;
            java.util.List<String> lore = armor.getItemMeta().getLore();
            ItemMeta armorMeta = armor.getItemMeta();

            for (Map.Entry<String, DefensiveEnchant> entry : defenseEnchants.entrySet()) {
                if (CombatContext.hasEnchant(lore, entry.getKey(), plugin, armorMeta)) {
                    boolean cancel = entry.getValue().apply(ctx, victim, attacker, armor);
                    if (cancel) return true;
                }
            }

            // Проки редкости брони
            String armorProc = ctx.getRarityProc(armor);
            if (!armorProc.isEmpty()) {
                for (DefensiveRarityProc drp : defenseRarityProcs) {
                    if (CombatContext.isProc(armorProc, drp.aliases) && ctx.rollChance(drp.chance)) {
                        drp.effect.apply(ctx, victim, attacker, armor);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Обработать защитные сет-эффекты жертвы.
     * @return true если событие отменено (щит бессмертия)
     */
    public boolean processDefensiveSetEffects(CombatContext ctx, Player victim, LivingEntity attacker) {
        for (Map.Entry<String, DefensiveSetEffect> entry : defenseSetEffects.entrySet()) {
            if (ctx.isWearingSet(victim, entry.getKey())) {
                boolean cancel = entry.getValue().apply(ctx, victim, attacker);
                if (cancel) return true;
            }
        }
        return false;
    }

    /**
     * Обработать спасение от смерти (Второе дыхание).
     * @return true если смерть предотвращена
     */
    public boolean processDeathSave(CombatContext ctx, Player victim) {
        ItemStack[] armorContents = victim.getInventory().getArmorContents();
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack armor = armorContents[i];
            if (armor == null || !armor.hasItemMeta() || !armor.getItemMeta().hasLore()) continue;
            if (CombatContext.hasEnchant(armor.getItemMeta().getLore(), "Второе дыхание", plugin, armor.getItemMeta())) {
                for (DeathSaveEffect ds : deathSaveEffects) {
                    if (ds.apply(ctx, victim, armor, i)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Обработать атакующие проки редкости оружия.
     */
    public void processOffensiveRarityProcs(CombatContext ctx, Player attacker, LivingEntity target, ItemStack weapon) {
        String weaponProc = ctx.getRarityProc(weapon);
        if (weaponProc.isEmpty()) return;
        for (OffensiveRarityProc orp : offenseRarityProcs) {
            if (CombatContext.isProc(weaponProc, orp.getAliases()) && ctx.rollChance(orp.getChance())) {
                orp.apply(ctx, attacker, target, weapon);
            }
        }
    }

    /**
     * Обработать атакующие зачарования оружия.
     */
    public void processOffensiveEnchants(CombatContext ctx, Player attacker, LivingEntity target, ItemStack weapon) {
        if (!weapon.hasItemMeta() || !weapon.getItemMeta().hasLore()) return;
        java.util.List<String> lore = weapon.getItemMeta().getLore();
        ItemMeta weaponMeta = weapon.getItemMeta();

        for (Map.Entry<String, OffensiveEnchant> entry : offenseEnchants.entrySet()) {
            if (!ctx.canProc()) break;
            if (CombatContext.hasEnchant(lore, entry.getKey(), plugin, weaponMeta)) {
                boolean procced = entry.getValue().apply(ctx, attacker, target);
                if (procced) ctx.addProc();
            }
        }
    }

    /**
     * Обработать атакующие сет-эффекты.
     */
    public void processOffensiveSetEffects(CombatContext ctx, Player attacker, LivingEntity target) {
        for (Map.Entry<String, OffensiveSetEffect> entry : offenseSetEffects.entrySet()) {
            if (ctx.isWearingSet(attacker, entry.getKey())) {
                entry.getValue().apply(ctx, attacker, target);
            }
        }
    }

    // ═══════════════════════════════════════
    // Вспомогательные классы для проков редкости
    // ═══════════════════════════════════════

    static class DefensiveRarityProc {
        final String[] aliases;
        final int chance;
        final DefensiveRarityApplier effect;

        @FunctionalInterface
        interface DefensiveRarityApplier {
            void apply(CombatContext ctx, Player victim, LivingEntity attacker, ItemStack armor);
        }

        DefensiveRarityProc(String[] aliases, int chance, DefensiveRarityApplier effect) {
            this.aliases = aliases;
            this.chance = chance;
            this.effect = effect;
        }
    }

    static class OffensiveRarityEntry implements OffensiveRarityProc {
        final String[] aliases;
        final int chance;
        final OffensiveRarityApplier effect;

        @FunctionalInterface
        interface OffensiveRarityApplier {
            void apply(CombatContext ctx, Player attacker, LivingEntity target, ItemStack weapon);
        }

        OffensiveRarityEntry(String[] aliases, int chance, OffensiveRarityApplier effect) {
            this.aliases = aliases;
            this.chance = chance;
            this.effect = effect;
        }

        @Override
        public void apply(CombatContext ctx, Player attacker, LivingEntity target, ItemStack weapon) {
            effect.apply(ctx, attacker, target, weapon);
        }

        @Override public String[] getAliases() { return aliases; }
        @Override public int getChance() { return chance; }
    }
}
