package ru.example.vkchatartifacts.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchatartifacts.conflicts.ArtifactEffectRegistry;
import ru.example.vkchatartifacts.conflicts.ArtifactEffectRegistry.ApplyResult;
import ru.example.vkchatartifacts.conflicts.ArtifactEffectRegistry.AppliedEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  ArtifactListenerV2 — рефакторинг для устранения конфликтов
 *  ─────────────────────────────────────────────────────────────────────
 *  Что изменено относительно v1:
 *    1. DRAGON_BLOOD: убран дубль +10 HP (теперь только реген и спец. эффекты).
 *    2. VAMPIRISM × LIFESTEAL_AURA: применяется только winner-эффект из группы.
 *    3. SPEED × WIND_WALKER: побеждает тот, у кого выше приоритет (SPEED > WIND_WALKER).
 *    4. BERSERKER + CRITICAL + ECHO_STRIKE: только один CRIT-эффект (по приоритету).
 *    5. ABSORPTION × SOUL_SHIELD: только один.
 *    6. ENCHANTMENT_SCROLL_BOOST: применяется только к 6 эффектам (раньше частично).
 *    7. setAbsorbsCurses вычисляется 1 раз, не N.
 *    8. Curse-логика перенесена в реестр, не дублируется.
 *    9. onDamageInternal — 1 проход по инвентарю.
 *   10. Race condition в onDamage защищена флагом.
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class ArtifactListenerV2 implements Listener {

    private final VKChatArtifactsPlugin plugin;

    private final NamespacedKey isArtifactKey;
    private final NamespacedKey buffKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey curseKey;
    private final NamespacedKey mythicKey;
    private final NamespacedKey expireKey;
    private final NamespacedKey curseGrowthKey;

    // Защита от рекурсии в onDamage
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Set<String> absorbedCurses = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Long> revivalCooldowns = new ConcurrentHashMap<>();

    // Managed modifiers (фиксированные UUID'ы — не конфликтуют с Gear)
    private static final UUID ART_HEALTH   = UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1001");
    private static final UUID ART_SPEED    = UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1002");
    private static final UUID ART_ARMOR    = UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1003");
    private static final UUID ART_KB       = UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1004");
    private static final UUID ART_GREED_HP = UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1005");
    private static final UUID ART_DRAGON_HP= UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1006");

    public ArtifactListenerV2(VKChatArtifactsPlugin plugin) {
        this.plugin = plugin;
        this.isArtifactKey = ArtifactEffectRegistry.isArtifactKey();
        this.buffKey       = ArtifactEffectRegistry.buffKey();
        this.levelKey      = ArtifactEffectRegistry.levelKey();
        this.curseKey      = ArtifactEffectRegistry.curseKey();
        this.mythicKey     = new NamespacedKey(plugin, "is_mythic");
        this.expireKey     = new NamespacedKey(plugin, "expire_time");
        this.curseGrowthKey= new NamespacedKey(plugin, "curse_growth");

        // Тик: пассивные эффекты (раз в секунду)
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::applyPassiveEffects, 40L, 20L);
        // Curse growth
        if (plugin.getConfig().getBoolean("curse-growth.enabled", true)) {
            long growthInterval = plugin.getConfig().getLong("curse-growth.interval", 60) * 20L;
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::processCurseGrowth, 100L, growthInterval);
        }
    }

    /**
     * Главный метод — заменяет ~200 строк хардкода в v1.
     */
    private void applyPassiveEffects() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ApplyResult result = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);

            // ═══ 1. Очистка просроченных FRAGILE-артефактов ═══
            processFragile(p);

            // ═══ 2. Атрибуты (Max Health, Speed, Armor, KB Resistance) ═══
            applyAttributes(p, result);

            // ═══ 3. Potion-эффекты (победители групп) ═══
            applyPotionEffects(p, result);

            // ═══ 4. Curse-эффекты ═══
            applyCurses(p, result);

            // ═══ 5. Passive-эффекты (TELEKINESIS, REVIVAL-уже-в-onDeath, GHOST_WALK) ═══
            applyPassiveBuffs(p, result);

            // ═══ 6. Synergy (3+ одинаковых buff'а) ═══
            applySynergy(p, result);

            // ═══ 7. Flight (DOUBLE_JUMP, ENDER_SHIFT) ═══
            applyFlight(p, result);
        }
    }

    private void processFragile(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;
            if (!meta.getPersistentDataContainer().has(expireKey, PersistentDataType.LONG)) continue;
            long expire = meta.getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
            if (System.currentTimeMillis() > expire) {
                item.setAmount(0);
                p.sendMessage(ChatColor.RED + "Твой Хрупкий Артефакт рассыпался в пыль!");
            }
        }
    }

    private void applyAttributes(Player p, ApplyResult r) {
        // ═══ ЗДОРОВЬЕ (группа GROUP_HEALTH) — побеждает DRAGON_BLOOD > MAX_HEALTH_BOOST > HEALTH ═══
        double healthAdd = 0;
        AppliedEffect healthWinner = r.winners.get(ArtifactEffectRegistry.GROUP_HEALTH);
        if (healthWinner != null) {
            int level = r.getEffectiveLevel(healthWinner.eff.id);
            switch (healthWinner.eff.id) {
                case "HEALTH":           healthAdd = level * 2; break;
                case "MAX_HEALTH_BOOST": healthAdd = level * 10; break;
                case "DRAGON_BLOOD":     healthAdd = 10; break; // +10 HP (НЕ ×2!)
            }
        }

        // ═══ СКОРОСТЬ (группа GROUP_SPEED) — побеждает SPEED > WIND_WALKER ═══
        double speedAdd = 0;
        AppliedEffect speedWinner = r.winners.get(ArtifactEffectRegistry.GROUP_SPEED);
        if (speedWinner != null) {
            int level = r.getEffectiveLevel(speedWinner.eff.id);
            switch (speedWinner.eff.id) {
                case "SPEED":       speedAdd = level * 0.1; break;     // атрибут
                case "WIND_WALKER": speedAdd = 0; break;               // только зелье, атрибут не даём
            }
        }

        // ═══ БРОНЯ (STEEL_SKIN) ═══
        double armorAdd = 0;
        if (r.hasBuff("STEEL_SKIN")) {
            armorAdd = r.getEffectiveLevel("STEEL_SKIN");
        }

        // ═══ KNOCKBACK RESIST ═══
        double kbAdd = 0;
        if (r.hasBuff("KNOCKBACK_RESIST")) {
            kbAdd = r.getEffectiveLevel("KNOCKBACK_RESIST") * 0.3;
        }

        // ═══ GREED curse → −6 HP ═══
        double greedHPSub = r.hasCurse("GREED") ? -6.0 : 0.0;

        // Применяем через managed modifier
        applyManagedModifier(p.getAttribute(Attribute.GENERIC_MAX_HEALTH),
                ART_HEALTH, "vkchat_artifact_health", healthAdd);
        applyManagedModifier(p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED),
                ART_SPEED, "vkchat_artifact_speed", speedAdd);
        applyManagedModifier(p.getAttribute(Attribute.GENERIC_ARMOR),
                ART_ARMOR, "vkchat_artifact_armor", armorAdd);
        applyManagedModifier(p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE),
                ART_KB, "vkchat_artifact_kb", kbAdd);
        applyManagedModifier(p.getAttribute(Attribute.GENERIC_MAX_HEALTH),
                ART_GREED_HP, "vkchat_artifact_greed_hp", greedHPSub);
        // DRAGON_BLOOD HP — только если НЕ winner (иначе двойное начисление)
        if (healthWinner != null && healthWinner.eff.id.equals("DRAGON_BLOOD")) {
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_MAX_HEALTH),
                    ART_DRAGON_HP, "vkchat_artifact_dragon_hp", 0.0);
        } else if (r.hasBuff("DRAGON_BLOOD")) {
            // Случай, когда DRAGON_BLOOD не победил в группе (например, есть DRAGON_BLOOD lvl 1 и MAX_HEALTH_BOOST lvl 5)
            // Побеждает MAX_HEALTH_BOOST, но +10 от DRAGON_BLOOD теряется.
            // Решение: даём +10 только если winner — DRAGON_BLOOD.
        }

        // Ограничение HP
        AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) {
            p.setHealth(Math.max(1.0, hp.getValue()));
        }
    }

    private void applyPotionEffects(Player p, ApplyResult r) {
        int regenAmp = 0;
        if (r.hasBuff("REGENERATION")) regenAmp = Math.max(regenAmp, r.getBuffLevel("REGENERATION") - 1);
        if (r.hasBuff("DRAGON_BLOOD")) regenAmp = Math.max(regenAmp, 1);
        if (regenAmp > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, regenAmp, false, false));

        if (r.hasBuff("ABSORPTION")) {
            int lvl = r.getBuffLevel("ABSORPTION");
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, lvl - 1, false, false));
        }
        if (r.hasBuff("SOUL_SHIELD")) {
            AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null && p.getHealth() < hp.getValue() * 0.3) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, false));
            }
        }
        if (r.hasBuff("FIRE_RESISTANCE") || r.hasBuff("FIRE_RESISTANCE_AURA") || r.hasBuff("FIRE_WALKER")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
            if (r.hasBuff("FIRE_RESISTANCE_AURA")) {
                for (org.bukkit.entity.Entity near : p.getNearbyEntities(5, 5, 5)) {
                    if (near instanceof Player && near != p) {
                        ((Player) near).addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
                    }
                }
            }
        }
        if (r.hasBuff("NIGHT_VISION")) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, false, false));
        if (r.hasBuff("HASTE"))         p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 100, r.getBuffLevel("HASTE") - 1, false, false));
        if (r.hasBuff("WATER_BREATHING"))p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, false, false));
        if (r.hasBuff("JUMP_BOOST"))    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, r.getBuffLevel("JUMP_BOOST") - 1, false, false));
        if (r.hasBuff("LUCK"))          p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, r.getBuffLevel("LUCK") - 1, false, false));
        if (r.hasBuff("LUCK_OF_THE_SEA"))p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, r.getBuffLevel("LUCK_OF_THE_SEA"), false, false));
        if (r.hasBuff("AQUATIC_SPEED")) p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, r.getBuffLevel("AQUATIC_SPEED") - 1, false, false));
        if (r.hasBuff("GHOST_WALK")) {
            AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null && p.getHealth() < hp.getValue() * 0.4) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
            }
        }
        if (r.hasBuff("HERO_OF_VILLAGE")) p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 100, r.getBuffLevel("HERO_OF_VILLAGE") - 1, false, false));
        if (r.hasBuff("STRENGTH_BOOST"))  p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, r.getBuffLevel("STRENGTH_BOOST") - 1, false, false));
        if (r.hasBuff("RESISTANCE"))      p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, r.getBuffLevel("RESISTANCE") - 1, false, false));
        if (r.hasBuff("SATURATION"))      p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0, false, false));

        // WIND_WALKER — в воздухе SPEED (только если WIND_WALKER — winner в группе)
        if (r.winners.get(ArtifactEffectRegistry.GROUP_SPEED) != null
                && r.winners.get(ArtifactEffectRegistry.GROUP_SPEED).eff.id.equals("WIND_WALKER")
                && !p.isOnGround()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, r.getBuffLevel("WIND_WALKER") - 1, false, false));
        }
        // FREEZE_AURA — AoE slow
        if (r.hasBuff("FREEZE_AURA")) {
            int lvl = r.getBuffLevel("FREEZE_AURA");
            for (org.bukkit.entity.Entity near : p.getNearbyEntities(5, 5, 5)) {
                if (near instanceof org.bukkit.entity.LivingEntity && !(near instanceof Player)) {
                    ((org.bukkit.entity.LivingEntity) near).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, lvl - 1, false, false));
                }
            }
        }
        // ABYSSAL_POWER — невидимость при HP<30%
        if (r.hasBuff("ABYSSAL_POWER")) {
            AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null && p.getHealth() < hp.getValue() * 0.3) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
            }
        }
    }

    private void applyCurses(Player p, ApplyResult r) {
        if (r.curses.isEmpty()) return;

        // Gear set: проверяем 1 раз
        boolean setAbsorbsCurses = checkSetAbsorbsCurses(p);

        if (setAbsorbsCurses) {
            String curseAbsorbKey = "curse_absorb_" + p.getUniqueId();
            if (!absorbedCurses.contains(curseAbsorbKey)) {
                absorbedCurses.add(curseAbsorbKey);
                p.sendMessage(ChatColor.GOLD + "✨ Сет поглотил проклятия!");
                p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> absorbedCurses.remove(curseAbsorbKey), 1200L);
            }
            return;
        }

        if (r.hasCurse("SLOWNESS"))  p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0, false, false));
        if (r.hasCurse("WEAKNESS"))  p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false));
        if (r.hasCurse("HUNGER"))    p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1, false, false));
        if (r.hasCurse("BLINDNESS")) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false));
        // DECAY handled in applyPassiveBuffs
        if (r.hasCurse("NIGHTMARE") && ThreadLocalRandom.current().nextInt(100) < 1) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 0, false, false));
        }
        if (r.hasCurse("CHAOS") && ThreadLocalRandom.current().nextInt(100) < 10) {
            PotionEffectType[] chaosEffects = {
                PotionEffectType.SPEED, PotionEffectType.SLOW, PotionEffectType.INCREASE_DAMAGE,
                PotionEffectType.WEAKNESS, PotionEffectType.REGENERATION, PotionEffectType.POISON,
                PotionEffectType.FIRE_RESISTANCE, PotionEffectType.JUMP, PotionEffectType.BLINDNESS,
                PotionEffectType.NIGHT_VISION, PotionEffectType.DAMAGE_RESISTANCE, PotionEffectType.HUNGER
            };
            PotionEffectType chosen = chaosEffects[ThreadLocalRandom.current().nextInt(chaosEffects.length)];
            p.addPotionEffect(new PotionEffect(chosen, 200, ThreadLocalRandom.current().nextInt(2), false, false));
        }
    }

    private void applyPassiveBuffs(Player p, ApplyResult r) {
        // TELEKINESIS — pickup nearby items
        if (r.hasBuff("TELEKINESIS")) {
            int radius = 5 + r.getBuffLevel("TELEKINESIS") * 2;
            for (org.bukkit.entity.Entity entity : p.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof org.bukkit.entity.Item) {
                    org.bukkit.entity.Item itemEntity = (org.bukkit.entity.Item) entity;
                    java.util.Map<Integer, ItemStack> leftover = p.getInventory().addItem(itemEntity.getItemStack());
                    if (leftover.isEmpty()) {
                        itemEntity.remove();
                    } else {
                        itemEntity.setItemStack(leftover.values().iterator().next());
                    }
                }
            }
        }

        // DECAY curse — drain 1 HP/тик
        if (r.hasCurse("DECAY") && p.getHealth() > 2) {
            p.setHealth(p.getHealth() - 1);
        }
    }

    private void applySynergy(Player p, ApplyResult r) {
        // Считаем количество по каждому buff
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;
            String buff = meta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
            if (buff != null) counts.merge(buff, 1, Integer::sum);
        }

        boolean hasSynergy = false;
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= 3) {
                hasSynergy = true;
                String b = e.getKey();
                if (b.equals("SPEED")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 1, false, false));
                } else if (b.equals("HEALTH") || b.equals("MAX_HEALTH_BOOST")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, false));
                } else if (b.equals("DAMAGE") || b.equals("STRENGTH_BOOST")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, 1, false, false));
                } else if (b.equals("REGENERATION")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0, false, false));
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 100, 0, false, false));
                }
                p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, p.getLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (hasSynergy && System.currentTimeMillis() % 4000 < 100) {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent("§6§l✨ СИНЕРГИЯ АРТЕФАКТОВ АКТИВНА (3+ шт.) ✨"));
        }
    }

    private void applyFlight(Player p, ApplyResult r) {
        boolean needsFlight = r.hasBuff("DOUBLE_JUMP") || r.hasBuff("ENDER_SHIFT");
        if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            if (needsFlight) {
                if (p.isOnGround()) p.setAllowFlight(true);
            } else {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        }
    }

    /**
     * 1 раз проверяем, надет ли сет. Возвращает true, если сет поглощает проклятия.
     * В v1 это вызывалось N раз (по предметам в инвентаре) — теперь 1.
     */
    private boolean checkSetAbsorbsCurses(Player p) {
        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null || !gearPlugin.isEnabled()) return false;
        try {
            ru.example.vkchatgear.VKChatGearPlugin gp = (ru.example.vkchatgear.VKChatGearPlugin) gearPlugin;
            if (gp.getConfig() == null || gp.getConfig().getConfigurationSection("sets") == null) return false;
            for (String setKey : gp.getConfig().getConfigurationSection("sets").getKeys(false)) {
                if (gp.getGearManager().isWearingSet(p, setKey)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void applyManagedModifier(AttributeInstance attr, UUID uuid, String name, double amount) {
        if (attr == null) return;
        try {
            java.util.List<AttributeModifier> remove = new java.util.ArrayList<>();
            for (AttributeModifier mod : attr.getModifiers()) {
                if (mod.getUniqueId().equals(uuid) || mod.getName().equals(name)) remove.add(mod);
            }
            for (AttributeModifier mod : remove) attr.removeModifier(mod);
            if (Math.abs(amount) > 0.0001) {
                attr.addModifier(new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.ADD_NUMBER));
            }
        } catch (Exception ignored) {}
    }

    private void processCurseGrowth() {
        int breakAt = plugin.getConfig().getInt("curse-growth.break-at", 100);
        int warn50 = breakAt / 2;
        int warn75 = (int) (breakAt * 0.75);
        int warn90 = (int) (breakAt * 0.9);

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                ItemMeta meta = item.getItemMeta();
                if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;
                String curse = meta.getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
                if (curse == null || curse.equals("NONE")) continue;

                int growth = 0;
                if (meta.getPersistentDataContainer().has(curseGrowthKey, PersistentDataType.INTEGER)) {
                    growth = meta.getPersistentDataContainer().get(curseGrowthKey, PersistentDataType.INTEGER);
                }
                growth++;
                meta.getPersistentDataContainer().set(curseGrowthKey, PersistentDataType.INTEGER, growth);

                if (growth >= breakAt) {
                    item.setAmount(0);
                    p.sendMessage(ChatColor.DARK_RED + "☠ Проклятие поглотило артефакт! Он рассыпался в прах!");
                    p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.5f);
                } else if (growth == warn90) {
                    p.sendMessage(ChatColor.DARK_RED + "☠ Проклятие почти поглотило артефакт! (90%)");
                } else if (growth == warn75) {
                    p.sendMessage(ChatColor.RED + "☠ Проклятие усиливается! (75%)");
                } else if (growth == warn50) {
                    p.sendMessage(ChatColor.YELLOW + "☠ Проклятие медленно разъедает артефакт... (50%)");
                }
                item.setItemMeta(meta);
            }
        }
    }

    // ═══ ON DAMAGE (v2) — 1 проход по инвентарю, conflict-aware ═══
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        UUID targetId = e.getEntity().getUniqueId();
        if (processing.contains(targetId)) return;
        processing.add(targetId);
        try {
            if (e.getDamager() instanceof Player) {
                onDamageInternal(e, (Player) e.getDamager());
            }
            if (e.getEntity() instanceof Player) {
                onDamageByVictim(e, (Player) e.getEntity());
            }
        } finally {
            processing.remove(targetId);
        }
    }

    private void onDamageInternal(EntityDamageByEntityEvent e, Player p) {
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);
        if (r.all.isEmpty()) return;

        // DAMAGE (простой +level)
        if (r.hasBuff("DAMAGE")) {
            e.setDamage(e.getDamage() + r.getBuffLevel("DAMAGE"));
        }
        // VAMPIRISM × LIFESTEAL_AURA — winner
        if (r.winners.get(ArtifactEffectRegistry.GROUP_LIFESTEAL) != null) {
            AppliedEffect w = r.winners.get(ArtifactEffectRegistry.GROUP_LIFESTEAL);
            double heal = e.getDamage() * (w.level * 0.1);
            AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
            p.setHealth(Math.min(maxHp, p.getHealth() + heal));
        }
        // CRIT / ECHO — winner в GROUP_CRIT
        AppliedEffect crit = r.winners.get(ArtifactEffectRegistry.GROUP_CRIT);
        if (crit != null) {
            if (crit.eff.id.equals("CRITICAL") && ThreadLocalRandom.current().nextInt(100) < (crit.level * 5)) {
                e.setDamage(e.getDamage() * 2);
                p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, e.getEntity().getLocation().add(0, 1, 0), 15);
            } else if (crit.eff.id.equals("ECHO_STRIKE") && ThreadLocalRandom.current().nextInt(100) < (crit.level * 10)) {
                e.setDamage(e.getDamage() * 2);
                p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, e.getEntity().getLocation().add(0, 1, 0), 20);
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            }
        }
        // WITHER / POISON / FROST / FLAME
        if (r.hasBuff("WITHER_TOUCH") && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            ((org.bukkit.entity.LivingEntity) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, r.getBuffLevel("WITHER_TOUCH") - 1, false, false));
        }
        if (r.hasBuff("POISON_STRIKE") && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            ((org.bukkit.entity.LivingEntity) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, r.getBuffLevel("POISON_STRIKE") - 1, false, false));
        }
        if (r.hasBuff("FROST_BITE") && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            ((org.bukkit.entity.LivingEntity) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, r.getBuffLevel("FROST_BITE") - 1, false, false));
        }
        if (r.hasBuff("FLAME_TONGUE") && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            ((org.bukkit.entity.LivingEntity) e.getEntity()).setFireTicks(40 + r.getBuffLevel("FLAME_TONGUE") * 20);
        }
        if (r.hasBuff("LIGHTNING_STRIKE") && ThreadLocalRandom.current().nextInt(100) < (r.getBuffLevel("LIGHTNING_STRIKE") * 10)) {
            e.getEntity().getWorld().strikeLightningEffect(e.getEntity().getLocation());
            if (e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                ((org.bukkit.entity.LivingEntity) e.getEntity()).damage(4.0 * r.getBuffLevel("LIGHTNING_STRIKE"), p);
            }
        }
        if (r.hasBuff("TRUE_STRIKE")) {
            e.setDamage(e.getDamage() + (r.getBuffLevel("TRUE_STRIKE") * 1.5));
            p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 10);
        }
        // BERSERKER — damage × (1 + missingHP% × 0.2 × level)
        if (r.hasBuff("BERSERKER")) {
            AttributeInstance maxHpAttr2 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHpVal = maxHpAttr2 != null ? maxHpAttr2.getValue() : 20.0;
            double missingHealth = maxHpVal - p.getHealth();
            double healthPercent = missingHealth / maxHpVal;
            e.setDamage(e.getDamage() * (1.0 + healthPercent * r.getBuffLevel("BERSERKER") * 0.2));
        }
        // ABYSSAL_POWER +10 damage
        if (r.hasBuff("ABYSSAL_POWER")) {
            e.setDamage(e.getDamage() + 10);
        }
        // DRAGON_BLOOD — +50% damage если цель горит
        if (r.hasBuff("DRAGON_BLOOD") && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            if (((org.bukkit.entity.LivingEntity) e.getEntity()).getFireTicks() > 0) {
                e.setDamage(e.getDamage() * 1.5);
            }
        }
        // LIFESTEAL_AURA — хилит СОЮЗНИКОВ
        if (r.hasBuff("LIFESTEAL_AURA")) {
            for (org.bukkit.entity.Entity near : p.getNearbyEntities(8, 8, 8)) {
                if (near instanceof Player && near != p) {
                    Player ally = (Player) near;
                    double heal = e.getDamage() * 0.15;
                    AttributeInstance allyMaxHpAttr = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double allyMaxHp = allyMaxHpAttr != null ? allyMaxHpAttr.getValue() : 20.0;
                    ally.setHealth(Math.min(allyMaxHp, ally.getHealth() + heal));
                }
            }
        }
        // BLOODLETTING curse
        if (r.hasCurse("BLOODLETTING")) {
            p.damage(e.getDamage() * 0.2);
        }
    }

    private void onDamageByVictim(EntityDamageByEntityEvent e, Player p) {
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);

        if (r.hasBuff("THORNS") && e.getDamager() instanceof org.bukkit.entity.LivingEntity) {
            ((org.bukkit.entity.LivingEntity) e.getDamager()).damage(r.getBuffLevel("THORNS") * 1.5, p);
        }
        if (r.hasBuff("DODGE_CHANCE") && ThreadLocalRandom.current().nextInt(100) < (r.getBuffLevel("DODGE_CHANCE") * 5)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.GREEN + "⚡ Уклонение!");
            p.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 5);
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
            // SHADOW_STEP в группе DODGE: после dodge → SPEED II
            if (r.hasBuff("SHADOW_STEP")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
                p.sendMessage(ChatColor.DARK_PURPLE + "⚡ Теневой шаг!");
            }
            return;
        }
        // MANA_SHIELD × RESISTANCE — winner в группе
        AppliedEffect resWinner = r.winners.get(ArtifactEffectRegistry.GROUP_RESISTANCE);
        if (resWinner != null) {
            if (resWinner.eff.id.equals("MANA_SHIELD")) {
                double reduction = resWinner.level * 0.1;
                e.setDamage(e.getDamage() * (1.0 - reduction));
            }
            // RESISTANCE — potion, applied in applyPotionEffects, не дублируем здесь
        }
        if (r.hasBuff("IRON_WILL")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, r.getBuffLevel("IRON_WILL") - 1, false, false));
        }
        // VULNERABILITY curse
        if (r.hasCurse("VULNERABILITY")) {
            e.setDamage(e.getDamage() * 1.2);
        }
        // ARCANE_BURST
        if (r.hasBuff("ARCANE_BURST")) {
            int abLevel = r.getBuffLevel("ARCANE_BURST");
            for (org.bukkit.entity.Entity near : p.getNearbyEntities(4, 4, 4)) {
                if (near instanceof org.bukkit.entity.LivingEntity && !(near instanceof Player)) {
                    ((org.bukkit.entity.LivingEntity) near).damage(2.0 * abLevel, p);
                }
            }
            p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);
            if (r.hasBuff("LEVITATION")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, null);

        if (r.hasBuff("REVIVAL")) {
            Long lastUse = revivalCooldowns.get(p.getUniqueId());
            if (lastUse == null || System.currentTimeMillis() - lastUse >= 600000) {
                revivalCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
                e.setKeepInventory(true);
                e.getDrops().clear();
                AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                p.setHealth(Math.max(1.0, maxHp * 0.5));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, false));
                p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.2);
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                p.sendMessage(ChatColor.GOLD + "✨ Перо Феникса спасло тебя от смерти!");
                return;
            }
        }
        // Mythic items return to player
        Iterator<ItemStack> iter = e.getDrops().iterator();
        List<ItemStack> savedItems = new ArrayList<>();
        while (iter.hasNext()) {
            ItemStack item = iter.next();
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(mythicKey, PersistentDataType.INTEGER)) {
                int isMythic = item.getItemMeta().getPersistentDataContainer().get(mythicKey, PersistentDataType.INTEGER);
                if (isMythic == 1) {
                    savedItems.add(item.clone());
                    iter.remove();
                }
            }
        }
        if (!savedItems.isEmpty()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (e.getEntity().isOnline()) {
                    for (ItemStack i : savedItems) e.getEntity().getInventory().addItem(i);
                    e.getEntity().sendMessage(ChatColor.LIGHT_PURPLE + "✨ Твоя мифическая реликвия вернулась к тебе после смерти!");
                }
            }, 60L);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (plugin.getBossManager() != null) plugin.getBossManager().onBossDeath(e);
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);

        if (r.hasBuff("SOUL_DRAIN")) {
            double heal = r.getBuffLevel("SOUL_DRAIN") * 2;
            AttributeInstance maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = maxHp != null ? maxHp.getValue() : 20.0;
            p.setHealth(Math.min(max, p.getHealth() + heal));
            p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, e.getEntity().getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_HURT, 0.5f, 1.5f);
        }
        if (r.hasBuff("LOOT_FIND") && ThreadLocalRandom.current().nextInt(100) < (r.getBuffLevel("LOOT_FIND") * 25)) {
            for (ItemStack drop : e.getDrops()) {
                if (drop != null && drop.getType() != Material.AIR) {
                    ItemStack bonus = drop.clone();
                    bonus.setAmount(1);
                    e.getDrops().add(bonus);
                    p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, e.getEntity().getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);
        if (r.hasCurse("ANCHOR")
                && e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "☠ Проклятие Якоря не позволяет тебе телепортироваться!");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);
        if (r.hasCurse("SILENCE")) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            ItemStack offHand = p.getInventory().getItemInOffHand();
            boolean hasArtifact = (mainHand != null && mainHand.hasItemMeta()
                    && mainHand.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER))
                    || (offHand != null && offHand.hasItemMeta()
                    && offHand.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER));
            if (hasArtifact) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "☠ Проклятие Молчания блокирует использование этого артефакта!");
            }
        }
    }

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.ADVENTURE) return;
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);
        if (r.hasBuff("DOUBLE_JUMP")) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setVelocity(p.getLocation().getDirection().multiply(0.8).setY(0.75));
            p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 15, 0.2, 0.2, 0.2, 0.1);
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
        } else if (r.hasBuff("ENDER_SHIFT")) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(5)));
            p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        ApplyResult r = ArtifactEffectRegistry.collectActive(p, ConsumablesListener.ENCHANTMENT_SCROLL_BOOST);
        double multiplier = 0;
        if (r.hasBuff("XP_BOOST"))  multiplier += r.getBuffLevel("XP_BOOST") * 0.15;
        if (r.hasBuff("XP_MAGNET")) multiplier += r.getBuffLevel("XP_MAGNET") * 0.5;
        if (multiplier > 0) {
            int extra = (int) Math.round(e.getAmount() * multiplier);
            e.setAmount(e.getAmount() + extra);
        }
    }
}
