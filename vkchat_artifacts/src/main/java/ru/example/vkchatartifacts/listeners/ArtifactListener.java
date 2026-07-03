package ru.example.vkchatartifacts.listeners;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchat.api.events.ReputationChangeEvent;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.Collections;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class ArtifactListener implements Listener {

    private final VKChatArtifactsPlugin plugin;
    private final NamespacedKey isArtifactKey;
    private final NamespacedKey buffKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey curseKey;
    private final NamespacedKey mythicKey;
    private final NamespacedKey expireKey;
    private final NamespacedKey curseGrowthKey;
    private final Set<Integer> boostingIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> absorbedCurses = Collections.synchronizedSet(new HashSet<>());
    private final java.util.Map<java.util.UUID, Long> revivalCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> processing = new HashSet<>(); // Защита от рекурсии
    private static final java.util.UUID ARTIFACT_HEALTH_UUID = java.util.UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1001");
    private static final java.util.UUID ARTIFACT_SPEED_UUID = java.util.UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1002");
    private static final java.util.UUID ARTIFACT_ARMOR_UUID = java.util.UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1003");
    private static final java.util.UUID ARTIFACT_KB_UUID = java.util.UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1004");
    private static final java.util.UUID ARTIFACT_GREED_HP_UUID = java.util.UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1005");
    private static final java.util.UUID ARTIFACT_DRAGON_HP_UUID = java.util.UUID.fromString("7d4f6b7a-2f5a-4dbd-9c8c-0df91f5c1006");

    public ArtifactListener(VKChatArtifactsPlugin plugin) {
        this.plugin = plugin;
        this.isArtifactKey = new NamespacedKey(plugin, "is_artifact");
        this.buffKey = new NamespacedKey(plugin, "buff_type");
        this.levelKey = new NamespacedKey(plugin, "buff_level");
        this.curseKey = new NamespacedKey(plugin, "curse_type");
        this.mythicKey = new NamespacedKey(plugin, "is_mythic");
        this.expireKey = new NamespacedKey(plugin, "expire_time");
        this.curseGrowthKey = new NamespacedKey(plugin, "curse_growth");
        
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::applyPassiveEffects, 40L, 20L);
        if (plugin.getConfig().getBoolean("curse-growth.enabled", true)) {
            long growthInterval = plugin.getConfig().getLong("curse-growth.interval", 60) * 20L;
            plugin.getServer().getScheduler().runTaskTimer(plugin, this::processCurseGrowth, 100L, growthInterval);
        }
    }

    private void applyPassiveEffects() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            boolean hasHealth = false;
            boolean hasSpeed = false;
            boolean hasArmor = false;
            boolean hasKbResist = false;
            boolean hasDoubleJump = false;
            boolean hasEnderShift = false;
            boolean hasDecay = false;
            boolean hasGreed = false;
            boolean hasDragonBlood = false;
            boolean hasAbyssalPower = false;
            
            double extraHealth = 0;
            double speedMult = 0;
            double extraArmor = 0;
            double kbResist = 0;
            java.util.Map<String, Integer> buffCounts = new java.util.HashMap<>();

            double buffMult = 1.0;
            Long boostExpiry = ConsumablesListener.ENCHANTMENT_SCROLL_BOOST.get(p.getUniqueId());
            if (boostExpiry != null && boostExpiry > System.currentTimeMillis()) {
                buffMult = 1.5;
            } else if (boostExpiry != null) {
                ConsumablesListener.ENCHANTMENT_SCROLL_BOOST.remove(p.getUniqueId());
            }

            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                ItemMeta meta = item.getItemMeta();
                if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;

                if (meta.getPersistentDataContainer().has(expireKey, PersistentDataType.LONG)) {
                    long expire = meta.getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
                    if (System.currentTimeMillis() > expire) {
                        item.setAmount(0);
                        p.sendMessage(org.bukkit.ChatColor.RED + "Твой Хрупкий Артефакт рассыпался в пыль!");
                        continue;
                    }
                }

                String buff = meta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
                int level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
                String curse = meta.getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);

                if (buff != null) {
                    buffCounts.put(buff, buffCounts.getOrDefault(buff, 0) + 1);
                    if (buff.equals("REGENERATION")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, level - 1, false, false));
                    } else if (buff.equals("HEALTH")) {
                        hasHealth = true;
                        extraHealth += (level * 2);
                    } else if (buff.equals("SPEED")) {
                        hasSpeed = true;
                        speedMult += (level * 0.1);
                    } else if (buff.equals("FIRE_RESISTANCE")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
                    } else if (buff.equals("ABSORPTION")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, level - 1, false, false));
                    } else if (buff.equals("NIGHT_VISION")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, false, false));
                    } else if (buff.equals("HASTE")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 100, level - 1, false, false));
                    } else if (buff.equals("WATER_BREATHING")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, false, false));
                    } else if (buff.equals("JUMP_BOOST")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, level - 1, false, false));
                    } else if (buff.equals("LUCK")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, level - 1, false, false));
                    } else if (buff.equals("GHOST_WALK")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
                    } else if (buff.equals("AQUATIC_SPEED")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, level - 1, false, false));
                    } else if (buff.equals("FIRE_WALKER")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
                    } else if (buff.equals("STEEL_SKIN")) {
                        hasArmor = true;
                        extraArmor += level;
                    } else if (buff.equals("KNOCKBACK_RESIST")) {
                        hasKbResist = true;
                        kbResist += (level * 0.3);
                    } else if (buff.equals("MAX_HEALTH_BOOST")) {
                        hasHealth = true;
                        extraHealth += (level * 10);
                    } else if (buff.equals("DOUBLE_JUMP")) {
                        hasDoubleJump = true;
                    } else if (buff.equals("HERO_OF_VILLAGE")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 100, level - 1, false, false));
                    } else if (buff.equals("STRENGTH_BOOST")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, level - 1, false, false));
                    } else if (buff.equals("RESISTANCE")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, level - 1, false, false));
                    } else if (buff.equals("SATURATION")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0, false, false));
                    } else if (buff.equals("LUCK_OF_THE_SEA")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, level, false, false));
                    } else if (buff.equals("FREEZE_AURA")) {
                        for (org.bukkit.entity.Entity near : p.getNearbyEntities(5, 5, 5)) {
                            if (near instanceof org.bukkit.entity.LivingEntity && !(near instanceof Player)) {
                                ((org.bukkit.entity.LivingEntity) near).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, level - 1, false, false));
                            }
                        }
                    } else if (buff.equals("WIND_WALKER")) {
                        if (!p.isOnGround()) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, level - 1, false, false));
                        }
                    } else if (buff.equals("ENDER_SHIFT")) {
                        hasEnderShift = true;
                    } else if (buff.equals("SOUL_SHIELD")) {
                        AttributeInstance hpAttr1 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (hpAttr1 != null && p.getHealth() < hpAttr1.getValue() * 0.3) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, false));
                        }
                    } else if (buff.equals("FIRE_RESISTANCE_AURA")) {
                        for (org.bukkit.entity.Entity near : p.getNearbyEntities(5, 5, 5)) {
                            if (near instanceof Player && near != p) {
                                ((Player) near).addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
                            }
                        }
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
                    } else if (buff.equals("REVIVAL")) {
                        // Handled in onPlayerDeath
                    } else if (buff.equals("ABYSSAL_POWER")) {
                        hasAbyssalPower = true;
                        AttributeInstance hpAttr2 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (hpAttr2 != null && p.getHealth() < hpAttr2.getValue() * 0.3) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
                        }
                    } else if (buff.equals("DRAGON_BLOOD")) {
                        hasDragonBlood = true;
                        extraHealth += 10;
                        hasHealth = true;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false));
                    }
                }

                // Проверяем, поглощает ли сет проклятия артефактов (если надет любой полный сет из vkchat_gear)
                boolean setAbsorbsCurses = false;
                org.bukkit.plugin.Plugin gearPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatGear");
                if (gearPlugin != null && gearPlugin.isEnabled()) {
                    try {
                        ru.example.vkchatgear.VKChatGearPlugin gp = (ru.example.vkchatgear.VKChatGearPlugin) gearPlugin;
                        // Проверяем, надет ли какой-либо полный сет
                        for (String setKey : gp.getConfig().getConfigurationSection("sets").getKeys(false)) {
                            if (gp.getGearManager().isWearingSet(p, setKey)) {
                                setAbsorbsCurses = true;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (curse != null && !curse.equals("NONE") && !setAbsorbsCurses) {
                    if (curse.equals("SLOWNESS")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0, false, false));
                    if (curse.equals("WEAKNESS")) p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false));
                    if (curse.equals("HUNGER")) p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1, false, false));
                    if (curse.equals("BLINDNESS")) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false));
                    if (curse.equals("DECAY")) hasDecay = true;
                    if (curse.equals("NIGHTMARE") && ThreadLocalRandom.current().nextInt(100) < 1) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 0, false, false));
                    }
                    if (curse.equals("GREED")) {
                        hasGreed = true;
                    }
                    if (curse.equals("CHAOS") && ThreadLocalRandom.current().nextInt(100) < 10) {
                        PotionEffectType[] chaosEffects = {
                            PotionEffectType.SPEED, PotionEffectType.SLOW, PotionEffectType.INCREASE_DAMAGE,
                            PotionEffectType.WEAKNESS, PotionEffectType.REGENERATION, PotionEffectType.POISON,
                            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.JUMP, PotionEffectType.BLINDNESS,
                            PotionEffectType.NIGHT_VISION, PotionEffectType.DAMAGE_RESISTANCE, PotionEffectType.HUNGER
                        };
                        PotionEffectType chosen = chaosEffects[ThreadLocalRandom.current().nextInt(chaosEffects.length)];
                        p.addPotionEffect(new PotionEffect(chosen, 200, ThreadLocalRandom.current().nextInt(2), false, false));
                    }
                } else if (curse != null && !curse.equals("NONE") && setAbsorbsCurses) {
                    // Сет полностью поглотил проклятие — показываем один раз
                    String curseAbsorbKey = "curse_absorb_" + p.getUniqueId();
                    if (!absorbedCurses.contains(curseAbsorbKey)) {
                        absorbedCurses.add(curseAbsorbKey);
                        p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Сет поглотил проклятие: " + curse + "!");
                        p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.1);
                        // Очищаем через 60 секунд
                        Bukkit.getScheduler().runTaskLater(plugin, () -> absorbedCurses.remove(curseAbsorbKey), 1200L);
                    }
                }
            }

            // TELEKINESIS: pickup nearby items
            if (hasArtifactBuff(p, "TELEKINESIS")) {
                for (org.bukkit.entity.Entity entity : p.getNearbyEntities(5, 5, 5)) {
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

            // DECAY curse: slowly drain health
            if (hasDecay && p.getHealth() > 2) {
                p.setHealth(p.getHealth() - 1);
            }

            // Артефакты работают из инвентаря постоянно, но больше не перезаписывают baseValue атрибутов.
            // Вместо setBaseValue используем собственные AttributeModifier с фиксированными UUID.
            // Это предотвращает конфликты с Gear, эффектами и другими плагинами.
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_MAX_HEALTH), ARTIFACT_HEALTH_UUID, "vkchat_artifact_health", hasHealth ? extraHealth * buffMult : 0.0);
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), ARTIFACT_SPEED_UUID, "vkchat_artifact_speed", hasSpeed ? speedMult * buffMult : 0.0);
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_ARMOR), ARTIFACT_ARMOR_UUID, "vkchat_artifact_armor", hasArmor ? extraArmor * buffMult : 0.0);
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE), ARTIFACT_KB_UUID, "vkchat_artifact_kb", hasKbResist ? kbResist * buffMult : 0.0);
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_MAX_HEALTH), ARTIFACT_GREED_HP_UUID, "vkchat_artifact_greed_hp", hasGreed ? -6.0 : 0.0);
            applyManagedModifier(p.getAttribute(Attribute.GENERIC_MAX_HEALTH), ARTIFACT_DRAGON_HP_UUID, "vkchat_artifact_dragon_hp", hasDragonBlood ? 10.0 : 0.0);
            AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(Math.max(1.0, hp.getValue()));

            // Сет-бонусы (синергия) при наличии 3 одинаковых артефактов
            boolean hasSynergy = false;
            for (java.util.Map.Entry<String, Integer> entry : buffCounts.entrySet()) {
                if (entry.getValue() >= 3) {
                    hasSynergy = true;
                    String buffType = entry.getKey();
                    if (buffType.equals("SPEED")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 1, false, false));
                    } else if (buffType.equals("HEALTH") || buffType.equals("MAX_HEALTH_BOOST")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, false));
                    } else if (buffType.equals("DAMAGE") || buffType.equals("STRENGTH_BOOST")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, 1, false, false));
                    } else if (buffType.equals("REGENERATION")) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0, false, false));
                    } else {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 100, 0, false, false));
                    }
                    
                    // Красивый след частиц синергии вокруг игрока
                    p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, p.getLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.02);
                }
            }
            if (hasSynergy && System.currentTimeMillis() % 4000 < 100) {
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent("§6§l✨ СИНЕРГИЯ АРТЕФАКТОВ АКТИВНА (3+ шт.) ✨"));
            }

            if (hasDoubleJump || hasEnderShift) {
                if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                    if (p.isOnGround()) {
                        p.setAllowFlight(true);
                    }
                }
            } else {
                if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                }
            }
        }
    }

    private void applyManagedModifier(AttributeInstance attr, java.util.UUID uuid, String name, double amount) {
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
                    p.sendMessage(org.bukkit.ChatColor.DARK_RED + "☠ Проклятие поглотило артефакт! Он рассыпался в прах!");
                    p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.5f, 1.5f);
                } else if (growth == warn90) {
                    p.sendMessage(org.bukkit.ChatColor.DARK_RED + "☠ Проклятие почти поглотило артефакт! (90%)");
                } else if (growth == warn75) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "☠ Проклятие усиливается! (75%)");
                } else if (growth == warn50) {
                    p.sendMessage(org.bukkit.ChatColor.YELLOW + "☠ Проклятие медленно разъедает артефакт... (50%)");
                }

                item.setItemMeta(meta);
            }
        }
    }

    private boolean hasArtifactBuff(Player p, String buffName) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;
            String buff = meta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
            if (buffName.equals(buff)) return true;
        }
        return false;
    }

    private int getArtifactBuffLevel(Player p, String buffName) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) continue;
            String buff = meta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
            if (buffName.equals(buff)) {
                return meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
            }
        }
        return 1;
    }

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            boolean hasBuff = hasArtifactBuff(p, "DOUBLE_JUMP");
            if (hasBuff) {
                e.setCancelled(true);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setVelocity(p.getLocation().getDirection().multiply(0.8).setY(0.75));
                p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 15, 0.2, 0.2, 0.2, 0.1);
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
            } else if (hasArtifactBuff(p, "ENDER_SHIFT")) {
                e.setCancelled(true);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(5)));
                p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        double multiplier = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                String buff = meta.getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
                int level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
                if ("XP_BOOST".equals(buff)) {
                    multiplier += (level * 0.15);
                } else if ("XP_MAGNET".equals(buff)) {
                    multiplier += (level * 0.5);
                }
            }
        }
        if (multiplier > 0) {
            int extra = (int) Math.round(e.getAmount() * multiplier);
            e.setAmount(e.getAmount() + extra);
        }
    }

    @EventHandler
    public void onReputationChange(ReputationChangeEvent e) {
        int vkId = e.getVkId();
        if (boostingIds.contains(vkId)) return;

        int diff = e.getNewAmount() - e.getOldAmount();
        if (diff <= 0) return;

        // [FIX] Не бустить маленькие изменения (сообщения в чате +1)
        if (diff < 10) return;

        Player player = null;
        try {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                int linked = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (linked == vkId) {
                    player = p;
                    break;
                }
            }
        } catch (Throwable ignored) {}

        if (player == null) return;

        double multiplier = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                String curseType = meta.getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
                if ("GREED".equals(curseType)) {
                    multiplier += 0.5;
                }
            }
        }

        if (multiplier > 0) {
            int extra = (int) Math.round(diff * multiplier);
            if (extra > 0) {
                boostingIds.add(vkId);
                final Player finalPlayer = player;
                final int finalExtra = extra;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        ru.example.vkchat.VKChatPlugin.getInstance().getReputationManager().addPoints(vkId, finalExtra);
                        Bukkit.getScheduler().runTask(plugin, () ->
                            finalPlayer.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Бонус к репутации ВК +" + finalExtra + " (Артефакт REP_BOOST)"));
                    } finally {
                        boostingIds.remove(vkId);
                    }
                });
            }
        }
    }

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
                onDamageByVictim(e);
            }
        } finally {
            processing.remove(targetId);
        }
    }

    private void onDamageInternal(EntityDamageByEntityEvent e, Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                String buff = item.getItemMeta().getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
                int level = item.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);

                if ("DAMAGE".equals(buff)) {
                    e.setDamage(e.getDamage() + level);
                } else if ("VAMPIRISM".equals(buff)) {
                    double heal = e.getDamage() * (level * 0.1);
                    AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                    p.setHealth(Math.min(maxHp, p.getHealth() + heal));
                } else if ("CRITICAL".equals(buff)) {
                    if (ThreadLocalRandom.current().nextInt(100) < (level * 5)) {
                        e.setDamage(e.getDamage() * 2);
                        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, e.getEntity().getLocation().add(0, 1, 0), 15);
                    }
                } else if ("WITHER_TOUCH".equals(buff) && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                    ((org.bukkit.entity.LivingEntity) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, level - 1, false, false));
                } else if ("POISON_STRIKE".equals(buff) && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                    ((org.bukkit.entity.LivingEntity) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, level - 1, false, false));
                } else if ("LIGHTNING_STRIKE".equals(buff)) {
                    if (ThreadLocalRandom.current().nextInt(100) < (level * 10)) {
                        e.getEntity().getWorld().strikeLightningEffect(e.getEntity().getLocation());
                        if (e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                            ((org.bukkit.entity.LivingEntity) e.getEntity()).damage(4.0 * level, p);
                        }
                    }
                } else if ("TRUE_STRIKE".equals(buff)) {
                    e.setDamage(e.getDamage() + (level * 1.5));
                    p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 10);
                } else if ("FROST_BITE".equals(buff) && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                    ((org.bukkit.entity.LivingEntity) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, level - 1, false, false));
                } else if ("BERSERKER".equals(buff)) {
                    AttributeInstance maxHpAttr2 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double maxHpVal = maxHpAttr2 != null ? maxHpAttr2.getValue() : 20.0;
                    double missingHealth = maxHpVal - p.getHealth();
                    double healthPercent = missingHealth / maxHpVal;
                    e.setDamage(e.getDamage() * (1.0 + healthPercent * level * 0.2));
                } else if ("FLAME_TONGUE".equals(buff) && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                    ((org.bukkit.entity.LivingEntity) e.getEntity()).setFireTicks(40 + level * 20);
                } else if ("ECHO_STRIKE".equals(buff)) {
                    if (ThreadLocalRandom.current().nextInt(100) < (level * 10)) {
                        e.setDamage(e.getDamage() * 2);
                        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, e.getEntity().getLocation().add(0, 1, 0), 20);
                        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                    }
                } else if ("LIFESTEAL_AURA".equals(buff)) {
                    for (org.bukkit.entity.Entity near : p.getNearbyEntities(8, 8, 8)) {
                        if (near instanceof Player && near != p) {
                            Player ally = (Player) near;
                            double heal = e.getDamage() * 0.15;
                            AttributeInstance allyMaxHpAttr = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                            double allyMaxHp = allyMaxHpAttr != null ? allyMaxHpAttr.getValue() : 20.0;
                            ally.setHealth(Math.min(allyMaxHp, ally.getHealth() + heal));
                        }
                    }
                } else if ("ABYSSAL_POWER".equals(buff)) {
                    e.setDamage(e.getDamage() + 10);
                } else if ("DRAGON_BLOOD".equals(buff) && e.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                    if (((org.bukkit.entity.LivingEntity) e.getEntity()).getFireTicks() > 0) {
                        e.setDamage(e.getDamage() * 1.5);
                    }
                }
            }
        }

        // BLOODLETTING curse: take damage back when attacking
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                String curse = item.getItemMeta().getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
                if ("BLOODLETTING".equals(curse)) {
                    p.damage(e.getDamage() * 0.2);
                    break;
                }
            }
        }
    }

    private void onDamageByVictim(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                if (item.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                    String buff = item.getItemMeta().getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
                    int level = item.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
                    
                    if ("THORNS".equals(buff) && e.getDamager() instanceof org.bukkit.entity.LivingEntity) {
                        ((org.bukkit.entity.LivingEntity) e.getDamager()).damage(level * 1.5, p);
                    } else if ("DODGE_CHANCE".equals(buff)) {
                        if (ThreadLocalRandom.current().nextInt(100) < (level * 5)) {
                            e.setCancelled(true);
                            p.sendMessage(org.bukkit.ChatColor.GREEN + "⚡ Уклонение! Вы увернулись от удара!");
                            p.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 5);
                            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
                            if (hasArtifactBuff(p, "SHADOW_STEP")) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
                                p.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "⚡ Теневой шаг! Скорость tăng!");
                            }
                            return;
                        }
                    } else if ("MANA_SHIELD".equals(buff)) {
                        double reduction = level * 0.1;
                        e.setDamage(e.getDamage() * (1.0 - reduction));
                    } else if ("IRON_WILL".equals(buff)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, level - 1, false, false));
                    }
                }
            }

            // VULNERABILITY curse: amplify damage
            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                if (item.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                    String curse = item.getItemMeta().getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
                    if ("VULNERABILITY".equals(curse)) {
                        e.setDamage(e.getDamage() * 1.2);
                        break;
                    }
                }
            }

            // ARCANE_BURST: AoE damage when player takes damage
            if (hasArtifactBuff(p, "ARCANE_BURST")) {
                int abLevel = getArtifactBuffLevel(p, "ARCANE_BURST");
                for (org.bukkit.entity.Entity near : p.getNearbyEntities(4, 4, 4)) {
                    if (near instanceof org.bukkit.entity.LivingEntity && !(near instanceof Player)) {
                        ((org.bukkit.entity.LivingEntity) near).damage(2.0 * abLevel, p);
                    }
                }
                p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_NORMAL, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                if (item.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                    String buff = item.getItemMeta().getPersistentDataContainer().get(buffKey, PersistentDataType.STRING);
                    if ("LEVITATION".equals(buff)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        
        // REVIVAL: revive with 50% HP (cooldown 10 min)
        if (hasArtifactBuff(p, "REVIVAL")) {
            Long lastUse = revivalCooldowns.get(p.getUniqueId());
            if (lastUse == null || System.currentTimeMillis() - lastUse >= 600000) {
                revivalCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
                e.setKeepInventory(true);
                e.getDrops().clear();
                AttributeInstance maxHpAttr3 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp = maxHpAttr3 != null ? maxHpAttr3.getValue() : 20.0;
                p.setHealth(Math.max(1.0, maxHp * 0.5));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, false, false));
                p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.2);
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Перо Феникса спасло тебя от смерти!");
                return;
            }
        }
        
        Iterator<ItemStack> iter = e.getDrops().iterator();
        List<ItemStack> savedItems = new java.util.ArrayList<>();

        while (iter.hasNext()) {
            ItemStack item = iter.next();
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(mythicKey, PersistentDataType.INTEGER)) {
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
                    for (ItemStack i : savedItems) {
                        e.getEntity().getInventory().addItem(i);
                    }
                    e.getEntity().sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "✨ Твоя мифическая реликвия вернулась к тебе после смерти!");
                }
            }, 60L);
        }
    }
    
    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (plugin.getBossManager() != null) {
            plugin.getBossManager().onBossDeath(e);
        }
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        if (hasArtifactBuff(p, "SOUL_DRAIN")) {
            int level = getArtifactBuffLevel(p, "SOUL_DRAIN");
            double heal = level * 2;
            AttributeInstance maxHpAttr4 = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp4 = maxHpAttr4 != null ? maxHpAttr4.getValue() : 20.0;
            p.setHealth(Math.min(maxHp4, p.getHealth() + heal));
            p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, e.getEntity().getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_HURT, 0.5f, 1.5f);
        }
        if (hasArtifactBuff(p, "LOOT_FIND")) {
            int level = getArtifactBuffLevel(p, "LOOT_FIND");
            if (ThreadLocalRandom.current().nextInt(100) < (level * 25)) {
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
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
                String curse = item.getItemMeta().getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
                if ("ANCHOR".equals(curse)) {
                    if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN && e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
                        e.setCancelled(true);
                        p.sendMessage(org.bukkit.ChatColor.RED + "☠ Проклятие Якоря не позволяет тебе телепортироваться!");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.hasItemMeta() && mainHand.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
            String curse = mainHand.getItemMeta().getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
            if ("SILENCE".equals(curse)) {
                e.setCancelled(true);
                p.sendMessage(org.bukkit.ChatColor.RED + "☠ Проклятие Молчания блокирует использование этого артефакта!");
                return;
            }
        }

        ItemStack offHand = p.getInventory().getItemInOffHand();
        if (offHand != null && offHand.hasItemMeta() && offHand.getItemMeta().getPersistentDataContainer().has(isArtifactKey, PersistentDataType.INTEGER)) {
            String curse = offHand.getItemMeta().getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
            if ("SILENCE".equals(curse)) {
                e.setCancelled(true);
                p.sendMessage(org.bukkit.ChatColor.RED + "☠ Проклятие Молчания блокирует использование этого артефакта!");
                return;
            }
        }
    }
}
