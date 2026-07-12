package ru.example.vkchatmobs.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.Particle;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.boss.BossAbilityRegistry;
import ru.example.vkchatmobs.boss.BossAbilityRegistry.BossDef;
import ru.example.vkchatmobs.boss.BossAbilityRegistry.AbilityDef;
import ru.example.vkchatmobs.drop.MobDropFactory;
import ru.example.vkchatmobs.tracking.CooldownManager;
import ru.example.vkchatmobs.util.BloodMoonHelper;
import ru.example.vkchatmobs.util.VKChatBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * MobListener — основной обработчик событий мобов.
 *
 * FIX #1:  BossAbilityRegistry интегрирован (startBossAbilitiesTask, spawnSuperBoss, onMobDamage).
 * FIX #2:  MobDropFactory выделен из onMobDeath().
 * FIX #3:  BloodMoonHelper вместо дублированных try-catch.
 * FIX #7:  spawnSuperBoss() использует BossAbilityRegistry.
 * FIX #8:  rollRandomGearItem() делегирует в RuneRegistry/GearPlugin.
 * FIX #9:  7 Map полей инкапсулированы в CooldownManager.
 * IMPROVE #8: onMobDeath() декомпозирован на отдельные методы.
 * IMPROVE #9: Фазовый переход использует BossDef.
 */
public class MobListener implements Listener {
    private final VKChatMobsPlugin plugin;
    private final NamespacedKey diffKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey isBossKey;
    private final NamespacedKey isSuperBossKey;
    private final NamespacedKey superBossTypeKey;
    private final NamespacedKey bossPhaseKey;
    private final NamespacedKey elementKey;

    // FIX #9: Инкапсулированные кулдауны и антифарм
    private final CooldownManager cooldowns;
    // FIX #2: Фабрика лута и репутации
    private final MobDropFactory dropFactory;
    // FIX #1: Реестр способностей боссов
    private final BossAbilityRegistry bossRegistry;

    public MobListener(VKChatMobsPlugin plugin, CooldownManager cooldowns, MobDropFactory dropFactory, BossAbilityRegistry bossRegistry) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.dropFactory = dropFactory;
        this.bossRegistry = bossRegistry;
        this.diffKey = new NamespacedKey(plugin, "difficulty_multiplier");
        this.rankKey = new NamespacedKey(plugin, "mob_rank");
        this.isBossKey = new NamespacedKey(plugin, "is_mini_boss");
        this.isSuperBossKey = new NamespacedKey(plugin, "is_super_boss");
        this.superBossTypeKey = new NamespacedKey(plugin, "super_boss_type");
        this.bossPhaseKey = new NamespacedKey(plugin, "boss_phase");
        this.elementKey = new NamespacedKey(plugin, "hardcore_element");

        startRegenerationTask();
        startBossAbilitiesTask();
    }

    // ═══ Статические фабрики для уникальных токенов ═══

    public static ItemStack getRuneToken() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lДревний Жетон Рун");
        List<String> lore = new ArrayList<>();
        lore.add("§7Редкий трофей, добытый в тяжелом бою.");
        lore.add("§7Выпадает со сверхсильных монстров.");
        lore.add("");
        lore.add("§eИспользование (ПКМ):");
        lore.add("§e• Дарует §6+250 репутации ВК");
        lore.add("§e• Дарует §dслучайную руну/кристалл!");
        lore.add("");
        lore.add("§8[Нажмите ПКМ в руке для активации]");
        meta.setLore(lore);
        meta.setCustomModelData(48);
        meta.getPersistentDataContainer().set(new NamespacedKey(VKChatMobsPlugin.getInstance(), "is_rune_token"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack getArtifactShard() {
        ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lОсколок Древнего Артефакта");
        List<String> lore = new ArrayList<>();
        lore.add("§7Светится мистическим фиолетовым светом.");
        lore.add("§7Выпадает только с Мировых Супер-Боссов.");
        lore.add("");
        lore.add("§eИспользование (ПКМ):");
        lore.add("§e• Дарует §d+300 репутации ВК");
        lore.add("§e• Дарует §bслучайный артефакт/свиток!");
        lore.add("");
        lore.add("§8[Нажмите ПКМ в руке для активации]");
        meta.setLore(lore);
        meta.setCustomModelData(49);
        meta.getPersistentDataContainer().set(new NamespacedKey(VKChatMobsPlugin.getInstance(), "is_artifact_shard"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    // ═══ Регенерация ═══

    private void startRegenerationTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("abilities.regeneration.enabled", true)) return;
            double healAmount = plugin.getConfig().getDouble("abilities.regeneration.heal-amount", 1.5);
            int minRank = plugin.getConfig().getInt("abilities.regeneration.min-rank", 3);

            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(Monster.class)) {
                    if (entity instanceof LivingEntity) {
                        LivingEntity mob = (LivingEntity) entity;
                        if (mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) {
                            int rank = mob.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);
                            if (rank >= minRank) {
                                AttributeInstance maxHpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                                if (maxHpAttr == null) continue;
                                double maxHp = maxHpAttr.getValue();
                                double nextHp = Math.min(maxHp, mob.getHealth() + healAmount);
                                if (nextHp > mob.getHealth()) {
                                    mob.setHealth(nextHp);
                                    updateNameplate(mob);
                                }
                            }
                        }
                    }
                }
            }
        }, 60L, 60L);
    }

    // ═══ Способности супер-боссов (через BossAbilityRegistry) ═══

    private void startBossAbilitiesTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                // Аура мини-боссов
                for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(Monster.class)) {
                    if (!(entity instanceof LivingEntity)) continue;
                    LivingEntity mob = (LivingEntity) entity;

                    boolean isMini = mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER);
                    boolean isSuper = mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER);

                    if (isMini && !isSuper) {
                        world.spawnParticle(Particle.VILLAGER_HAPPY, mob.getLocation().add(0, 1, 0), 4, 0.4, 0.5, 0.4, 0.02);
                    }

                    if (isSuper) {
                        String bossType = mob.getPersistentDataContainer().get(superBossTypeKey, PersistentDataType.STRING);
                        if (bossType == null) continue;

                        int phase = mob.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);

                        // FIX #1: Ауры через BossAbilityRegistry
                        bossRegistry.spawnAuraParticles(mob, bossType);

                        // Активные способности через BossAbilityRegistry
                        List<Player> nearbyPlayers = new ArrayList<>();
                        for (Player p : world.getPlayers()) {
                            if (p.getLocation().distanceSquared(mob.getLocation()) <= 144.0) {
                                nearbyPlayers.add(p);
                            }
                        }

                        if (nearbyPlayers.isEmpty()) continue;

                        BossDef bossDef = bossRegistry.getBossDef(bossType);
                        if (bossDef == null) continue;

                        for (AbilityDef ability : bossDef.getAbilities()) {
                            if (ability.getMinPhase() > phase) continue;
                            bossRegistry.executeAbility(mob, bossDef, ability, nearbyPlayers);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    // ═══ Спавн мобов ═══

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;
        if (!(e.getEntity() instanceof Monster)) return;

        LivingEntity mob = e.getEntity();

        if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "mobs_scaled"), PersistentDataType.INTEGER)) return;

        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "from_spawner"), PersistentDataType.INTEGER, 1);
            if (!plugin.getConfig().getBoolean("scaling.affect-spawners", false)) return;
        }

        // --- Редкий спавн мировых супер-боссов ---
        double bossSpawnChance = plugin.getConfig().getDouble("scaling.super-boss-spawn-chance", 2.0);
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && ThreadLocalRandom.current().nextInt(1000) < (int)(bossSpawnChance * 10)) {
            // FIX #9: Антифарм через CooldownManager
            if (cooldowns.canSpawnSuperBoss()) {
                String areaKey = mob.getWorld().getName() + ":" + (mob.getLocation().getBlockX() / 5) + ":" + (mob.getLocation().getBlockZ() / 5);
                if (cooldowns.checkAntiFarm(areaKey)) {
                    cooldowns.markSuperBossSpawned();
                    spawnSuperBoss(mob);
                    return;
                }
            }
        }

        double radius = plugin.getConfig().getDouble("settings.search-radius", 64.0);

        Player closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Player p : mob.getWorld().getPlayers()) {
            double dist = p.getLocation().distanceSquared(mob.getLocation());
            if (dist <= radius * radius && dist < closestDist) {
                closestDist = dist;
                closest = p;
            }
        }
        if (closest == null) return;

        int totalJobLevels = getJobLevels(closest);

        double divider = Math.max(1.0, plugin.getConfig().getDouble("difficulty.rank-divider", 25.0));
        int rank = (int) (totalJobLevels / divider) + 1;

        double maxMult = plugin.getConfig().getDouble("difficulty.max-multiplier", 10.0);
        double multiplier = 1.0 + (rank * 0.2);

        // FIX #3: BloodMoonHelper вместо дублированного try-catch
        boolean bloodMoonActive = BloodMoonHelper.isBloodMoonActive();
        if (bloodMoonActive) {
            double bmMult = plugin.getConfig().getDouble("blood_moon.stat-multiplier", 1.5);
            multiplier *= bmMult;
            rank += 2;
        }

        boolean isMiniBoss = false;
        double bossChance = bloodMoonActive ?
                plugin.getConfig().getDouble("mini_bosses.blood-moon-spawn-chance", 15.0) :
                plugin.getConfig().getDouble("mini_bosses.spawn-chance", 5.0);

        if (ThreadLocalRandom.current().nextInt(100) < bossChance) {
            isMiniBoss = true;
            rank = 10;
            double bossMult = plugin.getConfig().getDouble("mini_bosses.stat-multiplier", 2.0);
            multiplier *= bossMult;
        }

        if (multiplier > maxMult) multiplier = maxMult;

        mob.getPersistentDataContainer().set(diffKey, PersistentDataType.DOUBLE, multiplier);
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, rank);
        if (isMiniBoss) {
            mob.getPersistentDataContainer().set(isBossKey, PersistentDataType.INTEGER, 1);
            mob.setGlowing(true);
        } else if (bloodMoonActive && plugin.getConfig().getBoolean("blood_moon.glowing", true)) {
            mob.setGlowing(true);
        }

        if (plugin.getConfig().getBoolean("scaling.health", true)) {
            AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                double newHp = hp.getBaseValue() * multiplier;
                hp.setBaseValue(newHp);
                mob.setHealth(newHp);
            }
        }
        if (plugin.getConfig().getBoolean("scaling.damage", true)) {
            AttributeInstance dmg = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (dmg != null) {
                double newDmg = dmg.getBaseValue() * multiplier;
                dmg.setBaseValue(newDmg);
            }
        }
        if (plugin.getConfig().getBoolean("scaling.speed", false)) {
            AttributeInstance speed = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                double newSpeed = speed.getBaseValue() + (speed.getBaseValue() * (multiplier * 0.05));
                speed.setBaseValue(newSpeed);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> updateNameplate(mob), 2L);
    }

    // ═══ Спавн супер-босса (через BossAbilityRegistry) ═══

    private void spawnSuperBoss(LivingEntity mob) {
        // FIX #7: Используем BossAbilityRegistry вместо хардкода
        BossDef bossDef = bossRegistry.getRandomBossDef();
        if (bossDef == null) return;

        String name = bossDef.getDisplayName();
        String bId = bossDef.getId();
        double hpVal = bossDef.getBaseHp();

        mob.getPersistentDataContainer().set(isSuperBossKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(superBossTypeKey, PersistentDataType.STRING, bId);
        mob.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, 15);
        mob.getPersistentDataContainer().set(diffKey, PersistentDataType.DOUBLE, 5.0);
        mob.setGlowing(true);

        AttributeInstance hpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hpVal);
            mob.setHealth(hpVal);
        }
        AttributeInstance dmgAttr = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(bossDef.getBaseDamage());
        }

        final String finalName = name;
        final String world = mob.getWorld().getName();
        final int x = mob.getLocation().getBlockX();
        final int z = mob.getLocation().getBlockZ();

        String alert = ChatColor.RED + "☠️ [МИРОВОЙ БОСС] " + ChatColor.GOLD + "" + ChatColor.BOLD + finalName + ChatColor.RED + " пробудился в мире " + ChatColor.YELLOW + world + ChatColor.RED + " на координатах " + ChatColor.AQUA + "X:" + x + " Z:" + z + ChatColor.RED + "! В бой!";
        Bukkit.broadcastMessage(alert);

        mob.setCustomName(ChatColor.translateAlternateColorCodes('&', "&d&l☠ " + finalName + " ☠"));
        mob.setCustomNameVisible(true);
    }

    // ═══ Неймплейт ═══

    private void updateNameplate(LivingEntity mob) {
        if (!plugin.getConfig().getBoolean("difficulty.show-health-nameplate", true)) return;
        if (!mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) return;

        int rank = mob.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);
        AttributeInstance maxHpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr == null) return;
        double maxHp = maxHpAttr.getValue();
        double currentHp = mob.getHealth();

        String color = org.bukkit.ChatColor.GREEN.toString();
        if (currentHp < maxHp * 0.3) color = org.bukkit.ChatColor.DARK_RED.toString();
        else if (currentHp < maxHp * 0.6) color = org.bukkit.ChatColor.YELLOW.toString();

        String rankColor = org.bukkit.ChatColor.GRAY.toString();
        if (rank >= 15) rankColor = org.bukkit.ChatColor.LIGHT_PURPLE.toString();
        else if (rank >= 10) rankColor = org.bukkit.ChatColor.DARK_RED.toString() + org.bukkit.ChatColor.BOLD;
        else if (rank >= 7) rankColor = org.bukkit.ChatColor.RED.toString();
        else if (rank >= 4) rankColor = org.bukkit.ChatColor.GOLD.toString();

        String name = "Монстр";
        if (mob.getCustomName() != null && !mob.getCustomName().contains("Ранг") && !mob.getCustomName().contains("МИНИ-БОСС") && !mob.getCustomName().contains("СУПЕР-БОСС")) {
            name = mob.getCustomName();
        } else {
            String n = mob.getType().name().replace("_", " ").toLowerCase();
            name = n.substring(0, 1).toUpperCase() + n.substring(1);
        }

        boolean isMiniBoss = mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER);
        boolean isSuperBoss = mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER);

        String plate;
        if (isSuperBoss) {
            String bType = mob.getPersistentDataContainer().get(superBossTypeKey, PersistentDataType.STRING);
            String title = getSuperBossTitle(bType);
            plate = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                "&d&l☠ " + title + " ☠ &c❤ " + color + String.format("%.0f", currentHp) + "&8/&c" + String.format("%.0f", maxHp));
        } else if (isMiniBoss) {
            plate = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                "&4&l☠ МИНИ-БОСС ☠ &c[Ранг " + rank + "] &f" + name + " &c❤ " + color + String.format("%.0f", currentHp) + "&8/&c" + String.format("%.0f", maxHp));
        } else {
            // FIX #3: BloodMoonHelper
            String prefix = BloodMoonHelper.isBloodMoonActive() ? "&4[Кровавая Луна] " : "";
            plate = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                prefix + "&8[" + rankColor + "Ранг " + rank + "&8] &f" + name + " &c❤ " + color + String.format("%.0f", currentHp) + "&8/&c" + String.format("%.0f", maxHp));
        }

        mob.setCustomName(plate);
        mob.setCustomNameVisible(false);
    }

    /**
     * Получить русский заголовок супер-босса через BossAbilityRegistry.
     */
    private String getSuperBossTitle(String bossType) {
        BossDef def = bossRegistry.getBossDef(bossType);
        if (def != null) return def.getDisplayName().toUpperCase();
        return "СУПЕР-БОСС";
    }

    // ═══ Урон по мобу + фазовый переход ═══

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDamage(EntityDamageEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Monster)) return;
        LivingEntity mob = (LivingEntity) e.getEntity();
        if (mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) updateNameplate(mob);
            }, 1L);
        }

        // --- Фазовый переход супер-боссов ---
        if (mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER)) {
            AttributeInstance maxHpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHpAttr == null) return;
            double maxHp = maxHpAttr.getValue();
            double nextHp = mob.getHealth() - e.getFinalDamage();
            int phase = mob.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);

            String bossType = mob.getPersistentDataContainer().get(superBossTypeKey, PersistentDataType.STRING);
            if (bossType == null) return;

            BossDef bossDef = bossRegistry.getBossDef(bossType);
            if (bossDef == null) return;

            double threshold = bossDef.getPhase2Threshold();
            if (nextHp > 0 && nextHp <= (maxHp * threshold) && phase == 1) {
                mob.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, 2);
                // IMPROVE #9: Фазовый переход через BossDef
                bossRegistry.handlePhaseTransition(mob, bossDef, 2);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobHeal(EntityRegainHealthEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Monster)) return;
        LivingEntity mob = (LivingEntity) e.getEntity();
        if (mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) updateNameplate(mob);
            }, 1L);
        }
    }

    // ═══ Атаки мобов ═══

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobAttack(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getDamager() instanceof Monster)) return;
        if (!(e.getEntity() instanceof Player)) return;

        Monster mob = (Monster) e.getDamager();
        Player player = (Player) e.getEntity();

        if (!mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) return;
        int rank = mob.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);

        // 1. Fire Strike
        if (plugin.getConfig().getBoolean("abilities.fire_strike.enabled", true)) {
            int minRank = plugin.getConfig().getInt("abilities.fire_strike.min-rank", 4);
            if (rank >= minRank) {
                int chance = plugin.getConfig().getInt("abilities.fire_strike.chance", 25);
                if (ThreadLocalRandom.current().nextInt(100) < chance) {
                    int duration = plugin.getConfig().getInt("abilities.fire_strike.duration-seconds", 4);
                    player.setFireTicks(duration * 20);
                    player.sendMessage(org.bukkit.ChatColor.RED + "☠ Огненный удар! " + mob.getCustomName() + " поджег тебя!");
                }
            }
        }

        // 2. Web Weaver
        if (plugin.getConfig().getBoolean("abilities.web_weaver.enabled", true)) {
            int minRank = plugin.getConfig().getInt("abilities.web_weaver.min-rank", 5);
            if (rank >= minRank) {
                int chance = plugin.getConfig().getInt("abilities.web_weaver.chance", 15);
                if (ThreadLocalRandom.current().nextInt(100) < chance) {
                    int duration = plugin.getConfig().getInt("abilities.web_weaver.slowness-duration-seconds", 5);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration * 20, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));

                    org.bukkit.block.Block block = player.getLocation().getBlock();
                    if (block.getType() == org.bukkit.Material.AIR) {
                        block.setType(org.bukkit.Material.COBWEB);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (block.getType() == org.bukkit.Material.COBWEB) block.setType(org.bukkit.Material.AIR);
                        }, 60L);
                    }
                    player.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "🕸 Сеть паутины! " + mob.getCustomName() + " опутал твои ноги!");
                }
            }
        }

        // 3. Ядовитый Взрыв
        if (rank >= 6 && ThreadLocalRandom.current().nextInt(100) < 15) {
            player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation(), 40, 1.0, 0.5, 1.0, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 0.8f);
            for (org.bukkit.entity.Entity near : player.getNearbyEntities(4, 4, 4)) {
                if (near instanceof Player) {
                    ((Player) near).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                    near.sendMessage(org.bukkit.ChatColor.GREEN + "☠️ [Ядовитый Взрыв] " + mob.getCustomName() + " распылил яд вокруг!");
                }
            }
        }

        // 4. Гравитационный Толчок
        if (rank >= 8 && ThreadLocalRandom.current().nextInt(100) < 10) {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);
            player.setVelocity(new Vector(0, 0.75, 0));
            player.sendMessage(org.bukkit.ChatColor.AQUA + "💥 [Гравитационный Толчок] " + mob.getCustomName() + " подбросил тебя в воздух!");
        }
    }

    // ═══ Призыв миньонов ═══

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDamageByPlayer(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Monster)) return;
        Monster mob = (Monster) e.getEntity();

        if (!mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) return;
        int rank = mob.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);
        boolean isMiniBoss = mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER);

        if (plugin.getConfig().getBoolean("abilities.minion_summon.enabled", true) || isMiniBoss) {
            int minRank = plugin.getConfig().getInt("abilities.minion_summon.min-rank", 6);
            if (rank >= minRank || isMiniBoss) {
                int chance = isMiniBoss ? 20 : plugin.getConfig().getInt("abilities.minion_summon.chance", 10);
                if (ThreadLocalRandom.current().nextInt(100) < chance) {
                    long cd = plugin.getConfig().getInt("abilities.minion_summon.cooldown-seconds", 15) * 1000L;
                    // FIX #9: Кулдаун через CooldownManager
                    if (cooldowns.isMinionOnCooldown(mob.getUniqueId(), cd)) {
                        return; // На кулдауне
                    }
                    // Кулдаун установлен в isMinionOnCooldown()

                    org.bukkit.entity.EntityType minionType = mob.getType() == org.bukkit.entity.EntityType.SPIDER ?
                            org.bukkit.entity.EntityType.CAVE_SPIDER : org.bukkit.entity.EntityType.SILVERFISH;
                    if (isMiniBoss) minionType = org.bukkit.entity.EntityType.ZOMBIE;

                    for (int i = 0; i < 2; i++) {
                        org.bukkit.entity.Entity entity = mob.getWorld().spawnEntity(mob.getLocation().add(ThreadLocalRandom.current().nextDouble() * 2 - 1, 0, ThreadLocalRandom.current().nextDouble() * 2 - 1), minionType);
                        if (entity instanceof LivingEntity) {
                            LivingEntity minion = (LivingEntity) entity;
                            minion.setCustomName(org.bukkit.ChatColor.RED + "Прислужник " + mob.getType().name());
                            minion.setCustomNameVisible(false);
                            if (minion instanceof org.bukkit.entity.Zombie) ((org.bukkit.entity.Zombie) minion).setBaby(true);
                            AttributeInstance mHp = minion.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                            if (mHp != null) { mHp.setBaseValue(10.0); minion.setHealth(10.0); }
                        }
                    }
                    mob.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, mob.getLocation(), 3);
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);

                    for (org.bukkit.entity.Entity near : mob.getNearbyEntities(10, 10, 10)) {
                        if (near instanceof Player) near.sendMessage(org.bukkit.ChatColor.GOLD + "⚡ " + mob.getCustomName() + " призывает своих слуг!");
                    }
                }
            }
        }
    }

    // ═══ Смерть моба (декомпозирована) ═══

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        Player killer = mob.getKiller();

        // Осада
        if (plugin.getSiegeManager() != null) {
            plugin.getSiegeManager().handleSiegeMonsterKill(mob, killer);
        }

        // Спавнер-мобы — урезанный опыт
        if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "from_spawner"), PersistentDataType.INTEGER)) {
            if (killer != null) {
                killer.sendMessage(org.bukkit.ChatColor.GRAY + "⚠️ Из-за спавнер-качалки получаемый опыт урезан до 10%, а начисление репутации и кастомных рун полностью отключено!");
            }
            int exp = e.getDroppedExp();
            e.setDroppedExp((int) Math.round(exp * 0.10));
            return;
        }

        if (!mob.getPersistentDataContainer().has(diffKey, PersistentDataType.DOUBLE)) return;

        double multiplier = mob.getPersistentDataContainer().get(diffKey, PersistentDataType.DOUBLE);
        int rank = mob.getPersistentDataContainer().getOrDefault(rankKey, PersistentDataType.INTEGER, 1);
        boolean isMiniBoss = mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER);
        boolean isSuperBoss = mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER);
        String bossType = mob.getPersistentDataContainer().getOrDefault(superBossTypeKey, PersistentDataType.STRING, "");

        // IMPROVE #8: Декомпозиция onMobDeath()
        // 1. Контракт
        if (killer != null && plugin.getContractManager() != null) {
            String element = mob.getPersistentDataContainer().getOrDefault(elementKey, PersistentDataType.STRING, null);
            plugin.getContractManager().handleMobKill(killer, rank, isMiniBoss, isSuperBoss, element, mob);
        }

        // 2. Репутация (через MobDropFactory)
        if (killer != null) {
            dropFactory.awardReputation(killer, rank, isMiniBoss, isSuperBoss);
        }

        // 3. Опыт
        if (plugin.getConfig().getBoolean("loot.multiply-exp", true)) {
            int currentExp = e.getDroppedExp();
            e.setDroppedExp((int) (currentExp + (currentExp * multiplier)));
        }

        // 4. Умножение дропа
        if (plugin.getConfig().getBoolean("loot.multiply-items", true)) {
            for (ItemStack drop : e.getDrops()) {
                int newAmount = (int) (drop.getAmount() + (drop.getAmount() * multiplier));
                if (newAmount > drop.getType().getMaxStackSize()) newAmount = drop.getType().getMaxStackSize();
                drop.setAmount(newAmount);
            }
        }

        // 5. Кристаллы/свитки (через MobDropFactory)
        dropFactory.rollCrystalScrollDrop(mob, killer, rank);

        // 6. Жетоны/осколки (через MobDropFactory)
        dropFactory.awardTokensAndShards(mob, killer, isSuperBoss, isMiniBoss, bossType);

        // 7. Экстра-лут (через MobDropFactory)
        dropFactory.rollExtraLoot(mob, multiplier);

        // 8. Мини-босс лут
        if (isMiniBoss) {
            dropFactory.dropMiniBossLoot(mob);
            mob.getWorld().spawnParticle(Particle.TOTEM, mob.getLocation(), 30);
            mob.getWorld().playSound(mob.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            for (org.bukkit.entity.Entity near : mob.getNearbyEntities(15, 15, 15)) {
                if (near instanceof Player) near.sendMessage(org.bukkit.ChatColor.GREEN + "🎉 Поздравляем! Вы одолели элитного МИНИ-БОССА " + mob.getType().name() + "!");
            }
        }
    }

    // ═══ Использование токенов ═══

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.isCancelled()) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        boolean isRuneToken = meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_rune_token"), PersistentDataType.INTEGER);
        boolean isArtifactShard = meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_artifact_shard"), PersistentDataType.INTEGER);

        // Совместимость со старыми наградами
        if (!isRuneToken && !isArtifactShard && meta.hasDisplayName()) {
            String display = ChatColor.stripColor(meta.getDisplayName());
            if (item.getType() == Material.GOLD_NUGGET && display.equalsIgnoreCase("Древний Жетон Рун")) isRuneToken = true;
            else if (item.getType() == Material.PRISMARINE_CRYSTALS && display.equalsIgnoreCase("Осколок Древнего Артефакта")) isArtifactShard = true;
        }

        if (!isRuneToken && !isArtifactShard) return;
        e.setCancelled(true);

        // IMPROVE #5: Проверка ВК или проходки через VKChatBridge.hasVkOrPass()
        if (!VKChatBridge.hasVkOrPass(p)) {
            p.sendMessage("§c❌ Для использования жетона ваш игровой аккаунт должен быть привязан к ВК или иметь проходку! Введите: /vklink");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        item.setAmount(item.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        if (isRuneToken) {
            VKChatBridge.addEffectiveRep(p, 250);
            p.sendMessage("§a🔺 Вы использовали Древний Жетон Рун и получили §6+250 Репутации ВК§a!");

            ItemStack rolled = rollRandomGearItem();
            safeGiveItem(p, rolled);
            p.sendMessage("§d✨ Вы получили предмет экипировки: " + (rolled.getItemMeta() != null ? rolled.getItemMeta().getDisplayName() : rolled.getType().name()));
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        } else {
            ItemStack rolled = rollRandomArtifactItem();
            if (rolled != null && rolled.hasItemMeta() && rolled.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("vkchat_artifacts", "is_artifact"), PersistentDataType.INTEGER)) {
                int max = 5;
                int current = 0;
                try {
                    max = ru.example.vkchatartifacts.VKChatArtifactsPlugin.getInstance().getConfig().getInt("artifacts.max-artifacts", 5);
                    current = ru.example.vkchatartifacts.VKChatArtifactsPlugin.getInstance().getArtifactListener().countArtifacts(p);
                } catch (Exception ignored) {}
                if (current >= max) {
                    item.setAmount(item.getAmount() + 1); // вернуть шард
                    p.sendMessage("§c☠ Лимит артефактов: " + current + "/" + max + ". Выбрось лишние!");
                    return;
                }
            }

            VKChatBridge.addEffectiveRep(p, 300);
            p.sendMessage("§a🔺 Вы использовали Осколок Древнего Артефакта и получили §d+300 Репутации ВК§a!");

            safeGiveItem(p, rolled);
            p.sendMessage("§b✨ Вы получили древний артефакт/свиток: " + (rolled.getItemMeta() != null ? rolled.getItemMeta().getDisplayName() : rolled.getType().name()));
            p.getWorld().spawnParticle(Particle.SPELL_WITCH, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private void safeGiveItem(Player p, ItemStack item) {
        if (p.getInventory().firstEmpty() == -1) p.getWorld().dropItemNaturally(p.getLocation(), item);
        else p.getInventory().addItem(item);
    }

    // ═══ FIX #8: rollRandomGearItem — делегирует в GearPlugin RuneRegistry ═══

    private ItemStack rollRandomGearItem() {
        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null || !gearPlugin.isEnabled()) {
            return new ItemStack(Material.DIAMOND, 3);
        }

        // Попробовать делегировать в RuneRegistry (если доступен)
        try {
            Object registry = gearPlugin.getClass().getMethod("getRuneRegistry").invoke(gearPlugin);
            if (registry != null) {
                ItemStack runeItem = (ItemStack) registry.getClass().getMethod("createRandomRuneItem").invoke(registry);
                if (runeItem != null) return runeItem;
            }
        } catch (Exception ignored) {
            // Fallback к локальному методу
        }

        // Fallback: локальное создание рун/кристаллов
        return rollRandomGearItemFallback(gearPlugin);
    }

    /**
     * Fallback: локальное создание кристаллов/рун (обратная совместимость).
     */
    private ItemStack rollRandomGearItemFallback(org.bukkit.plugin.Plugin gearPlugin) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 25) {
            return MobDropFactoryHelper.createCommonCrystal(gearPlugin);
        } else if (roll < 50) {
            return MobDropFactoryHelper.createRareCrystal(gearPlugin);
        } else if (roll < 60) {
            return MobDropFactoryHelper.createLegendaryCrystal(gearPlugin);
        } else if (roll < 75) {
            return MobDropFactoryHelper.createSafetyScroll(gearPlugin);
        } else {
            return MobDropFactoryHelper.createRandomRune(gearPlugin);
        }
    }

    private ItemStack rollRandomArtifactItem() {
        org.bukkit.plugin.Plugin artifactsPlugin = Bukkit.getPluginManager().getPlugin("VKChatArtifacts");
        if (artifactsPlugin == null || !artifactsPlugin.isEnabled()) {
            return new ItemStack(Material.DIAMOND, 5);
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 70) {
            boolean isMythic = ThreadLocalRandom.current().nextInt(100) < 15;
            return ru.example.vkchatartifacts.items.ArtifactFactory.generateArtifact(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin, isMythic);
        } else if (roll < 80) {
            return ru.example.vkchatartifacts.items.ConsumableFactory.generateCleanseScroll(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin);
        } else if (roll < 90) {
            return ru.example.vkchatartifacts.items.ConsumableFactory.generateReviveScroll(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin);
        } else {
            return ru.example.vkchatartifacts.items.ConsumableFactory.generateEscapeScroll(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin);
        }
    }

    // ═══ Утилиты ═══

    private int getJobLevels(Player p) {
        int total = ru.example.vkchat.util.JobsBridge.getTotalLevel(p);
        return total > 0 ? total : 1;
    }

    public void cleanupMaps(long now) {
        cooldowns.cleanup(now);
        dropFactory.cleanup(now);
        bossRegistry.cleanup(now);
    }

    /**
     * Внутренний хелпер для создания предметов экипировки (fallback).
     */
    private static class MobDropFactoryHelper {
        static ItemStack createCommonCrystal(org.bukkit.plugin.Plugin gearPlugin) {
            ItemStack crystal = new ItemStack(Material.EMERALD);
            ItemMeta meta = crystal.getItemMeta();
            meta.setDisplayName("§a💎 Кристалл Заточки: Обычный [I-X]");
            List<String> lore = new ArrayList<>();
            lore.add("§7Позволяет затачивать снаряжение.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_tier"), PersistentDataType.STRING, "common");
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_name"), PersistentDataType.STRING, "Обычный [I-X]");
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_price"), PersistentDataType.INTEGER, 400);
            crystal.setItemMeta(meta);
            return crystal;
        }

        static ItemStack createRareCrystal(org.bukkit.plugin.Plugin gearPlugin) {
            ItemStack crystal = new ItemStack(Material.DIAMOND);
            ItemMeta meta = crystal.getItemMeta();
            meta.setDisplayName("§9💎 Кристалл Заточки: Редкий [XI-XV]");
            List<String> lore = new ArrayList<>();
            lore.add("§7Позволяет затачивать снаряжение.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_tier"), PersistentDataType.STRING, "rare");
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_name"), PersistentDataType.STRING, "Редкий [XI-XV]");
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_price"), PersistentDataType.INTEGER, 900);
            crystal.setItemMeta(meta);
            return crystal;
        }

        static ItemStack createLegendaryCrystal(org.bukkit.plugin.Plugin gearPlugin) {
            ItemStack crystal = new ItemStack(Material.PRISMARINE_SHARD);
            ItemMeta meta = crystal.getItemMeta();
            meta.setDisplayName("§6§l💎 Кристалл Заточки: Легендарный [XVI-XX]");
            List<String> lore = new ArrayList<>();
            lore.add("§7Позволяет затачивать снаряжение.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_tier"), PersistentDataType.STRING, "legendary");
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_name"), PersistentDataType.STRING, "Легендарный [XVI-XX]");
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_price"), PersistentDataType.INTEGER, 1800);
            crystal.setItemMeta(meta);
            return crystal;
        }

        static ItemStack createSafetyScroll(org.bukkit.plugin.Plugin gearPlugin) {
            ItemStack scroll = new ItemStack(Material.PAPER);
            ItemMeta meta = scroll.getItemMeta();
            meta.setDisplayName("§d§lСвиток Сохранения");
            List<String> lore = new ArrayList<>();
            lore.add("§7Защищает предмет от отката");
            lore.add("§7уровня заточки при неудаче!");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "safety_scroll_price"), PersistentDataType.INTEGER, 1500);
            scroll.setItemMeta(meta);
            return scroll;
        }

        static ItemStack createRandomRune(org.bukkit.plugin.Plugin gearPlugin) {
            String[] runes = {
                "vampirism", "execute", "meteor", "soul_reaper", "critical_strike", "disintegration", "thunder_strike",
                "dodge", "shield", "second_wind", "golem_skin", "reflect_magic", "absorption", "soul_bond",
                "haste_aura", "rarity_seal", "wind_glide", "ore_magnet", "vampire_aoe", "frozen_touch", "poison_cloud",
                "spider_reflexes", "magma_walker", "meteor_shower"
            };
            String[] runeNames = {
                "Вампиризм", "Казнь", "Метеоритный Удар", "Жнец Душ", "Критический Удар", "Распад", "Удар Грома",
                "Уклонение", "Эгида", "Второе Дыхание", "Кожа Голема", "Зеркало", "Поглощение", "Связь Душ",
                "Аура Спешки", "Печать Души", "Полет Ветра", "Магнит Руд", "Аура Вампиризма", "Ледяное Касание", "Ядовитое Облако",
                "Рефлексы Паука", "Магматический Шаг", "Метеоритный Дождь"
            };
            int index = ThreadLocalRandom.current().nextInt(runes.length);
            String id = runes[index];
            String name = runeNames[index];

            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§d§l✨ Руна: " + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Перетащите эту руну на");
            lore.add("§7ваше снаряжение в инвентаре,");
            lore.add("§7чтобы наложить чары!");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "rune_id"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "rune_name"), PersistentDataType.STRING, name);
            meta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "rune_price"), PersistentDataType.INTEGER, 1000);
            item.setItemMeta(meta);
            return item;
        }
    }
}
