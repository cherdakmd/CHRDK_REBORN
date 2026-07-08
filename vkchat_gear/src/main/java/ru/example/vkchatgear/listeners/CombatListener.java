package ru.example.vkchatgear.listeners;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatgear.VKChatGearPlugin;
import ru.example.vkchatgear.combat.CombatContext;
import ru.example.vkchatgear.combat.CombatEffectRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CombatListener — обработчик боевых событий.
 *
 * Рефакторинг #5: извлечён CombatEffectRegistry.
 * Боевые эффекты (зачарования, проки редкости, сет-бонусы в бою)
 * вынесены в конфиг-управляемый реестр.
 *
 * CombatListener теперь — тонкий оркестратор:
 * 1. Обрабатывает спецмеханики (Тьма, заточка,ActionBar, падение, смерть)
 * 2. Делегирует боевые эффекты → CombatEffectRegistry
 */
public class CombatListener implements Listener {

    private final VKChatGearPlugin plugin;
    private final CombatEffectRegistry effectRegistry;

    /** Защита от рекурсии (Meteor Shower и подобные создают взрывы) */
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    /** Кулдаун сообщений о смерти (для Печати Души) */
    private final Map<UUID, Long> deathMessageCooldowns = new ConcurrentHashMap<>();
    private static final long DEATH_MSG_COOLDOWN_MS = 2000;

    private boolean vkApiWarningLogged = false;

    public CombatListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
        this.effectRegistry = new CombatEffectRegistry(plugin);
    }

    public CombatEffectRegistry getEffectRegistry() {
        return effectRegistry;
    }

    // ═══════════════════════════════════════
    // Смерть игрока — даунгрейд снаряжения
    // ═══════════════════════════════════════

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        List<ItemStack> inventory = new ArrayList<>(e.getDrops());

        List<ItemStack> downgradeCandidates = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                if (item.getItemMeta().getLore().toString().contains("Редкость:")) {
                    downgradeCandidates.add(item);
                }
            }
        }

        if (downgradeCandidates.isEmpty()) return;

        ItemStack toDowngrade = downgradeCandidates.get(ThreadLocalRandom.current().nextInt(downgradeCandidates.size()));
        ItemMeta meta = toDowngrade.getItemMeta();
        List<String> lore = meta.getLore();
        boolean hasSeal = false;
        int sealIndex = -1;

        for (int i = 0; i < lore.size(); i++) {
            if (org.bukkit.ChatColor.stripColor(lore.get(i)).contains("Печать Души")) {
                hasSeal = true;
                sealIndex = i;
                break;
            }
        }

        if (hasSeal) {
            lore.remove(sealIndex);
            meta.setLore(lore);
            toDowngrade.setItemMeta(meta);
            sendDeathMessage(p, org.bukkit.ChatColor.LIGHT_PURPLE + "🛡️ [Печать Души] Ваша Печать Души защитила предмет " + meta.getDisplayName() + " от потери грейда, но разрушилась!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation(), 40, 0.5, 0.5, 0.5, 0.15);
        } else {
            plugin.getGearManager().downgradeGear(toDowngrade);
            sendDeathMessage(p, org.bukkit.ChatColor.DARK_RED + "☠ При смерти ваше снаряжение пострадало... Один из предметов потерял свой грейд!");
        }
    }

    private void sendDeathMessage(Player p, String msg) {
        long now = System.currentTimeMillis();
        Long last = deathMessageCooldowns.get(p.getUniqueId());
        if (last != null && now - last < DEATH_MSG_COOLDOWN_MS) return;
        deathMessageCooldowns.put(p.getUniqueId(), now);
        p.sendMessage(msg);
    }

    // ═══════════════════════════════════════
    // Основной обработчик боевых событий
    // ═══════════════════════════════════════

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        UUID targetId = e.getEntity().getUniqueId();
        if (processing.contains(targetId)) return;
        processing.add(targetId);
        try {
            onHitInternal(e);
        } finally {
            processing.remove(targetId);
        }
    }

    private void onHitInternal(EntityDamageByEntityEvent e) {
        CombatContext ctx = new CombatContext(e, plugin,
                effectRegistry.getCooldowns(), effectRegistry.getMessageCooldowns());

        // ─── 1. Тьма наступает: репутация ВК усиливает мобов ───
        processDarkness(ctx);

        // ─── 2. Защита от заточки брони жертвы ───
        processArmorUpgradeDefense(ctx);

        // ─── 3. Защитные эффекты брони + сет-эффекты жертвы ───
        if (ctx.isVictimPlayer() && ctx.isAttackerLiving()) {
            Player victim = ctx.getVictim();
            LivingEntity attacker = ctx.getAttackerEntity();

            // Защитные зачарования и проки редкости
            boolean cancelled = effectRegistry.processDefensiveEnchants(ctx, victim, attacker);
            if (cancelled || e.isCancelled()) return;

            // Защитные сет-эффекты
            cancelled = effectRegistry.processDefensiveSetEffects(ctx, victim, attacker);
            if (cancelled || e.isCancelled()) return;

            // Спасение от смерти (Второе дыхание)
            if (victim.getHealth() - e.getFinalDamage() <= 0) {
                if (effectRegistry.processDeathSave(ctx, victim)) return;
            }
        }

        // ─── 4. Атакующие эффекты оружия атакующего ───
        if (ctx.isAttackerPlayer() && ctx.isVictimLiving()) {
            Player attacker = ctx.getAttacker();
            LivingEntity target = ctx.getVictimEntity();
            ItemStack weapon = attacker.getInventory().getItemInMainHand();

            // ActionBar (GearScore + урон + редкость)
            sendActionBar(attacker, weapon, e);

            // Бонусы заточки и дефекты оружия
            processWeaponUpgradeOffense(ctx, attacker, weapon, e);

            // Проки редкости оружия
            if (weapon != null && weapon.hasItemMeta()) {
                effectRegistry.processOffensiveRarityProcs(ctx, attacker, target, weapon);
            }

            // Зачарования оружия
            if (weapon != null && weapon.hasItemMeta()) {
                effectRegistry.processOffensiveEnchants(ctx, attacker, target, weapon);
            }

            // Атакующие сет-эффекты
            effectRegistry.processOffensiveSetEffects(ctx, attacker, target);
        }
    }

    // ═══════════════════════════════════════
    // Спецмеханики (остаются в CombatListener)
    // ═══════════════════════════════════════

    /**
     * Тьма наступает: монстры сильнее бьют игроков с высокой репутацией ВК.
     */
    private void processDarkness(CombatContext ctx) {
        if (!ctx.isVictimPlayer() || !ctx.isDamagerMonster()) return;

        Player victim = ctx.getVictim();
        try {
            int vkId = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getLinkedVkId(victim);
            if (vkId == -1) return;
            int rep = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep <= 100) return;

            double multiplier = 1.0 + (rep - 100) * 0.0005;
            multiplier = Math.min(2.5, multiplier);
            ctx.getEvent().setDamage(ctx.getEvent().getDamage() * multiplier);

            victim.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 0), 1.5f));

            if (rep > 1000 && ThreadLocalRandom.current().nextInt(100) < 15) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                victim.sendMessage(org.bukkit.ChatColor.RED + "☠ Твоя высокая репутация привлекает Тьму! Монстр ошеломил тебя (Замедление II)!");
            }
        } catch (Exception ex) {
            if (!vkApiWarningLogged) {
                plugin.getLogger().warning("Ошибка VKChat API в боевой системе: " + ex.getMessage());
                vkApiWarningLogged = true;
            }
        }
    }

    /**
     * Защита от заточки брони: +0.5 HP и 1% резиста за уровень заточки каждого предмета.
     */
    private void processArmorUpgradeDefense(CombatContext ctx) {
        if (!ctx.isVictimPlayer()) return;
        Player victim = ctx.getVictim();
        EntityDamageByEntityEvent e = ctx.getEvent();

        double extraHealth = 0.0;
        double damageReduction = 0.0;

        for (ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) {
                int lvl = armor.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
                extraHealth += (lvl * 0.5);
                damageReduction += (lvl * 0.01);
            }
        }

        double multiplier = 1.0;
        // Дебафф сета Ясного Сокола: +10% получаемого урона
        if (plugin.getGearManager().isWearingSet(victim, "sokol")) {
            multiplier = 1.10;
        }

        if (damageReduction > 0) {
            e.setDamage(e.getDamage() * (1.0 - Math.min(damageReduction, 0.35)) * multiplier);

            double rarityDefense = 0.0;
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                rarityDefense += plugin.getGearManager().getRarityDefenseBonus(plugin.getGearManager().getRarityKey(armor));
            }
            if (rarityDefense > 0) {
                e.setDamage(e.getDamage() * (1.0 - Math.min(0.25, rarityDefense)));
            }
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (plugin.getGearManager().hasDefect(armor, "fragile")) {
                    e.setDamage(e.getDamage() * 1.10);
                    break;
                }
            }
        } else if (multiplier != 1.0) {
            e.setDamage(e.getDamage() * multiplier);
        }
    }

    /**
     * Бонусы заточки оружия: +0.6 урона за уровень, дефекты, бонус редкости.
     */
    private void processWeaponUpgradeOffense(CombatContext ctx, Player attacker, ItemStack weapon, EntityDamageByEntityEvent e) {
        if (weapon == null || !weapon.hasItemMeta()) return;
        ItemMeta meta = weapon.getItemMeta();

        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER)) {
            int upgradeLvl = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER);
            e.setDamage(e.getDamage() + (upgradeLvl * 0.6));
        }
        if (plugin.getGearManager().hasDefect(weapon, "dull")) {
            e.setDamage(e.getDamage() * 0.90);
        }
        double rarityDamageBonus = plugin.getGearManager().getRarityDamageBonus(plugin.getGearManager().getRarityKey(weapon));
        if (rarityDamageBonus > 0) {
            e.setDamage(e.getDamage() * (1.0 + rarityDamageBonus));
        }
    }

    /**
     * ActionBar: GearScore + урон + редкость.
     */
    private void sendActionBar(Player attacker, ItemStack weapon, EntityDamageByEntityEvent e) {
        int gearScore = plugin.getGearManager().calculateGearScore(attacker);
        String rarityKey = plugin.getGearManager().getRarityKey(weapon);
        String actionBar = ChatColor.GOLD + "⚔ GS: " + ChatColor.YELLOW + gearScore +
                ChatColor.GRAY + " | " + ChatColor.RED + "DMG: " + ChatColor.WHITE +
                String.format("%.1f", e.getFinalDamage()) +
                ChatColor.GRAY + " | " + ChatColor.LIGHT_PURPLE + rarityKey.toUpperCase();
        attacker.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBar));
    }

    // ═══════════════════════════════════════
    // Урон от падения
    // ═══════════════════════════════════════

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Player p = (Player) e.getEntity();

        // Проверка чар Полет Ветра
        boolean hasWindGlide = false;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                if (CombatContext.hasEnchant(armor.getItemMeta().getLore(), "Полет Ветра")) {
                    hasWindGlide = true;
                    break;
                }
            }
        }
        if (hasWindGlide) {
            e.setCancelled(true);
            p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 20, 0.4, 0.2, 0.4, 0.05);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f);
            sendDeathMessage(p, org.bukkit.ChatColor.AQUA + "🍃 [Полет Ветра] Сила ветра спасла вас от урона при падении!");
            return;
        }

        // Гагарин — гравитационный импульс
        if (plugin.getGearManager().isWearingSet(p, "gagarin")) {
            e.setCancelled(true);
            p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 100, 2.0, 0.2, 2.0, 0.1);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

            for (org.bukkit.entity.Entity ent : p.getNearbyEntities(5, 5, 5)) {
                if (ent instanceof LivingEntity && ent != p) {
                    LivingEntity le = (LivingEntity) ent;
                    le.setVelocity(new org.bukkit.util.Vector(0, 0.8, 0));
                    le.damage(4.0, p);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0));
                }
            }
            sendDeathMessage(p, org.bukkit.ChatColor.AQUA + " 🌌 Гравитационный импульс отбросил всех врагов вокруг!");
        }
    }

    // ═══════════════════════════════════════
    // Очистка кулдаунов
    // ═══════════════════════════════════════

    public void cleanupCooldowns(long now) {
        deathMessageCooldowns.entrySet().removeIf(e -> now - e.getValue() > 600000);
        effectRegistry.cleanupCooldowns(now);
    }
}
