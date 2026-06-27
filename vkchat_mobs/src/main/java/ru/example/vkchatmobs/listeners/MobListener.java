package ru.example.vkchatmobs.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import ru.example.vkchat.VKChatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import java.util.Random;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class MobListener implements Listener {
    private final VKChatMobsPlugin plugin;
    private final NamespacedKey diffKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey isBossKey;
    private final NamespacedKey isSuperBossKey;
    private final NamespacedKey superBossTypeKey;
    private final NamespacedKey bossPhaseKey;

    private final Random random = new Random();
    private final Map<UUID, Long> minionCooldowns = new HashMap<>();
    private final Map<UUID, Integer> farmedRepToday = new HashMap<>();
    private final Map<UUID, Long> farmResetTimes = new HashMap<>();

    // Таймеры для способностей супер-боссов
    private final Map<UUID, Long> lastSpellTime = new HashMap<>();

    public MobListener(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.diffKey = new NamespacedKey(plugin, "difficulty_multiplier");
        this.rankKey = new NamespacedKey(plugin, "mob_rank");
        this.isBossKey = new NamespacedKey(plugin, "is_mini_boss");
        this.isSuperBossKey = new NamespacedKey(plugin, "is_super_boss");
        this.superBossTypeKey = new NamespacedKey(plugin, "super_boss_type");
        this.bossPhaseKey = new NamespacedKey(plugin, "boss_phase");
        
        startRegenerationTask();
        startBossAbilitiesTask();
    }

    // Статические фабрики для уникальных токенов
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
        meta.getPersistentDataContainer().set(new NamespacedKey(VKChatMobsPlugin.getInstance(), "is_artifact_shard"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

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
                                double maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
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
        }, 60L, 60L); // Раз в 3 секунды
    }

    // --- [НОВОЕ] ПЕРИОДИЧЕСКИЕ СПОСОБНОСТИ И АУРЫ БОССОВ ---
    private void startBossAbilitiesTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (org.bukkit.World world : Bukkit.getWorlds()) {
                // Аура мини-боссов
                for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(Monster.class)) {
                    if (!(entity instanceof LivingEntity)) continue;
                    LivingEntity mob = (LivingEntity) entity;

                    boolean isMini = mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER);
                    boolean isSuper = mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER);

                    if (isMini && !isSuper) {
                        // Зеленая пыльца вокруг мини-боссов
                        world.spawnParticle(Particle.VILLAGER_HAPPY, mob.getLocation().add(0, 1, 0), 4, 0.4, 0.5, 0.4, 0.02);
                    }

                    if (isSuper) {
                        String bossType = mob.getPersistentDataContainer().get(superBossTypeKey, PersistentDataType.STRING);
                        if (bossType == null) continue;

                        int phase = mob.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);

                        // 1. АУРЫ И ЧАСТИЦЫ СУПЕР-БОССОВ
                        if (bossType.equals("warlord")) {
                            world.spawnParticle(Particle.REDSTONE, mob.getLocation().add(0, 0.2, 0), 8, 0.5, 0.2, 0.5, 0.01, new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                        } else if (bossType.equals("storm")) {
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation().add(0, 0.1, 0), 5, 0.4, 0.2, 0.4, 0.02);
                            world.spawnParticle(Particle.CRIT_MAGIC, mob.getLocation().add(0, 1.2, 0), 4, 0.3, 0.4, 0.3, 0.02);
                        } else if (bossType.equals("alchemist")) {
                            world.spawnParticle(Particle.SPELL_WITCH, mob.getLocation().add(0, 0.5, 0), 6, 0.5, 0.5, 0.5, 0.02);
                        }

                        // 2. АКТИВНЫЕ СПОСОБНОСТИ (СПЕЛЛЫ) В БОЮ
                        List<Player> nearbyPlayers = new ArrayList<>();
                        for (Player p : world.getPlayers()) {
                            if (p.getLocation().distanceSquared(mob.getLocation()) <= 144.0) { // 12 блоков
                                nearbyPlayers.add(p);
                            }
                        }

                        if (nearbyPlayers.isEmpty()) continue;

                        long lastUsed = lastSpellTime.getOrDefault(mob.getUniqueId(), 0L);

                        if (bossType.equals("warlord")) {
                            // РАССЕКАЮЩИЙ УДАР (Spin Attack) — только во 2 фазе, раз в 8 сек
                            if (phase == 2 && now - lastUsed >= 8000L) {
                                lastSpellTime.put(mob.getUniqueId(), now);
                                mob.setCustomName("§c§l☠ Древний Воевода ☠ §e[ЯРОСТЬ]");

                                Bukkit.broadcastMessage("§c[Древний Воевода] РАССЕКАЮЩИЙ УДАР КЛИНКА!");
                                world.playSound(mob.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                                world.spawnParticle(Particle.SWEEP_ATTACK, mob.getLocation().add(0, 1, 0), 10, 2.0, 0.5, 2.0, 0.1);

                                for (Player p : nearbyPlayers) {
                                    if (p.getLocation().distance(mob.getLocation()) <= 6.0) {
                                        p.damage(8.0, mob);
                                        p.setVelocity(new Vector(0, 0.5, 0));
                                        p.sendMessage("§c☠ Вас подбросило вихрем клинка Древнего Воеводы!");
                                    }
                                }
                            }
                        } else if (bossType.equals("storm")) {
                            // УРАГАННЫЙ ПРИТЯГ (Tornado / Storm) — раз в 12 сек (Фаза 1) / 6 сек (Фаза 2)
                            long cd = phase == 2 ? 6000L : 12000L;
                            if (now - lastUsed >= cd) {
                                lastSpellTime.put(mob.getUniqueId(), now);

                                Bukkit.broadcastMessage("§b[Повелитель Бури] ПОДЧИНИТЕСЬ СИЛЕ УРАГАНА!");
                                world.playSound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.6f);

                                // Столб урагана
                                for (int h = 0; h < 6; h++) {
                                    world.spawnParticle(Particle.CLOUD, mob.getLocation().add(0, h, 0), 20, 1.0, 0.2, 1.0, 0.1);
                                }

                                for (Player p : nearbyPlayers) {
                                    double d = p.getLocation().distance(mob.getLocation());
                                    if (d <= 10.0) {
                                        p.damage(5.0, mob);
                                        // Притягиваем игрока
                                        Vector dir = mob.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.45);
                                        p.setVelocity(dir);
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                                        p.sendMessage("§b💥 Вас затянуло в грозовой шторм Повелителя Бури!");
                                    }
                                }
                            }
                        } else if (bossType.equals("alchemist")) {
                            // ЯДОВИТАЯ КОЛБА + ЭЛИКСИР (раз в 9 сек во 2 фазе)
                            if (phase == 2 && now - lastUsed >= 9000L) {
                                lastSpellTime.put(mob.getUniqueId(), now);

                                // 1. Выстрел колбой в случайного игрока
                                Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
                                Bukkit.broadcastMessage("§d[Проклятый Алхимик] ПОПРОБУЙТЕ МОЙ НОВЫЙ ЯДОВИТЫЙ РЕАГЕНТ!");
                                world.playSound(target.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.5f, 0.8f);
                                world.spawnParticle(Particle.SPELL_WITCH, target.getLocation(), 50, 2.0, 0.5, 2.0, 0.1);

                                for (Player p : nearbyPlayers) {
                                    if (p.getLocation().distance(target.getLocation()) <= 5.0) {
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
                                        p.sendMessage("§d☠ Вы вдохнули токсичные пары смертельного реагента Проклятого Алхимика!");
                                    }
                                }

                                // 2. Самолечение босса (5% здоровья)
                                double maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                                double heal = maxHp * 0.05;
                                mob.setHealth(Math.min(maxHp, mob.getHealth() + heal));
                                world.playSound(mob.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.2f, 1.0f);
                                world.spawnParticle(Particle.SPELL_INSTANT, mob.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
                                updateNameplate(mob);
                            }
                        }
                    }
                }
            }
        }, 20L, 20L); // Каждую секунду
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        // Применяем только к враждебным мобам
        if (!(e.getEntity() instanceof Monster)) return;

        LivingEntity mob = e.getEntity();
        
        // Маркируем спавнер-мобов во избежание фарма
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "from_spawner"), PersistentDataType.INTEGER, 1);
            if (!plugin.getConfig().getBoolean("scaling.affect-spawners", false)) return;
        }

        // --- [НОВОЕ] РЕДКИЙ СПАВН МИРОВЫХ СУПЕР-БОССОВ (шанс 0.2%) ---
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && random.nextInt(1000) < 2) {
            spawnSuperBoss(mob);
            return;
        }

        double radius = plugin.getConfig().getDouble("settings.search-radius", 64.0);

        // Ищем ближайшего игрока
        Player closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Player p : mob.getWorld().getPlayers()) {
            double dist = p.getLocation().distanceSquared(mob.getLocation());
            if (dist <= radius * radius) {
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = p;
                }
            }
        }

        if (closest == null) return;

        // Рассчитываем сложность через уровни профессий
        int totalJobLevels = 1;
        try {
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                Object dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                int m = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, closest.getUniqueId(), "miner");
                int w = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, closest.getUniqueId(), "woodcutter");
                int f = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, closest.getUniqueId(), "farmer");
                int a = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, closest.getUniqueId(), "alchemist");
                int b = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, closest.getUniqueId(), "blacksmith");
                totalJobLevels = m + w + f + a + b;
            }
        } catch (Exception ignored) {}

        double divider = plugin.getConfig().getDouble("difficulty.rank-divider", 25.0);
        int rank = (int) (totalJobLevels / divider) + 1;
        
        double maxMult = plugin.getConfig().getDouble("difficulty.max-multiplier", 10.0);
        double multiplier = 1.0 + (rank * 0.2); // +20% stats per rank

        // Проверка Кровавой Луны
        boolean bloodMoonActive = false;
        try {
            if (ru.example.vkchat.VKChatPlugin.getInstance() != null && 
                ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager() != null) {
                bloodMoonActive = ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager().isActive();
            }
        } catch (Throwable ignored) {}

        if (bloodMoonActive) {
            double bmMult = plugin.getConfig().getDouble("blood_moon.stat-multiplier", 1.5);
            multiplier *= bmMult;
            rank += 2; // Увеличиваем ранг во время Кровавой Луны
        }

        // Проверка Мини-Босса
        boolean isMiniBoss = false;
        double bossChance = bloodMoonActive ? 
                plugin.getConfig().getDouble("mini_bosses.blood-moon-spawn-chance", 15.0) : 
                plugin.getConfig().getDouble("mini_bosses.spawn-chance", 5.0);
                
        if (random.nextInt(100) < bossChance) {
            isMiniBoss = true;
            rank = 10; // Фиксированный Ранг 10 для мини-боссов
            double bossMult = plugin.getConfig().getDouble("mini_bosses.stat-multiplier", 2.0);
            multiplier *= bossMult;
        }

        if (multiplier > maxMult) multiplier = maxMult;

        // Сохраняем множитель и ранг в моба
        mob.getPersistentDataContainer().set(diffKey, PersistentDataType.DOUBLE, multiplier);
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, rank);
        if (isMiniBoss) {
            mob.getPersistentDataContainer().set(isBossKey, PersistentDataType.INTEGER, 1);
            mob.setGlowing(true);
        } else if (bloodMoonActive && plugin.getConfig().getBoolean("blood_moon.glowing", true)) {
            mob.setGlowing(true);
        }

        // Применяем характеристики
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

    private void spawnSuperBoss(LivingEntity mob) {
        int r = random.nextInt(3);
        String name = "Древний Воевода";
        String bId = "warlord";
        double hpVal = 500.0;
        
        if (r == 1) {
            name = "Повелитель Бури";
            bId = "storm";
            hpVal = 600.0;
        } else if (r == 2) {
            name = "Проклятый Алхимик";
            bId = "alchemist";
            hpVal = 550.0;
        }

        mob.getPersistentDataContainer().set(isSuperBossKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(superBossTypeKey, PersistentDataType.STRING, bId);
        mob.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, 1); // Стартовая фаза 1
        
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, 15); // Ранг 15 для Супер-Боссов
        mob.getPersistentDataContainer().set(diffKey, PersistentDataType.DOUBLE, 5.0);
        mob.setGlowing(true);

        AttributeInstance hpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hpVal);
            mob.setHealth(hpVal);
        }
        AttributeInstance dmgAttr = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(15.0);
        }

        final String finalName = name;
        final String world = mob.getWorld().getName();
        final int x = mob.getLocation().getBlockX();
        final int z = mob.getLocation().getBlockZ();
        
        // Создаем глобальный анонс о призыве босса
        String alert = ChatColor.RED + "☠️ [МИРОВОЙ БОСС] " + ChatColor.GOLD + "" + ChatColor.BOLD + finalName + ChatColor.RED + " пробудился в мире " + ChatColor.YELLOW + world + ChatColor.RED + " на координатах " + ChatColor.AQUA + "X:" + x + " Z:" + z + ChatColor.RED + "! В бой!";
        Bukkit.broadcastMessage(alert);
        
        try {
            VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(alert));
        } catch (Throwable ignored) {}

        mob.setCustomName(ChatColor.translateAlternateColorCodes('&', "&d&l☠ " + finalName + " ☠"));
        mob.setCustomNameVisible(true);
    }

    private void updateNameplate(LivingEntity mob) {
        if (!plugin.getConfig().getBoolean("difficulty.show-health-nameplate", true)) return;
        if (!mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) return;
        
        int rank = mob.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);
        double maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double currentHp = mob.getHealth();
        
        String color = org.bukkit.ChatColor.GREEN.toString();
        if (currentHp < maxHp * 0.3) color = org.bukkit.ChatColor.DARK_RED.toString();
        else if (currentHp < maxHp * 0.6) color = org.bukkit.ChatColor.YELLOW.toString();
        
        // --- [НОВОЕ] ЦВЕТОВАЯ РАЗМЕТКА РАНГОВ ПО СТЕПЕНИ УГРОЗЫ ---
        String rankColor = org.bukkit.ChatColor.GRAY.toString(); // Ранг 1-3 (Серый)
        if (rank >= 15) rankColor = org.bukkit.ChatColor.LIGHT_PURPLE.toString(); // Ранг 15 (Супер-Босс)
        else if (rank >= 10) rankColor = org.bukkit.ChatColor.DARK_RED.toString() + org.bukkit.ChatColor.BOLD; // Ранг 10 (Мини-Босс)
        else if (rank >= 7) rankColor = org.bukkit.ChatColor.RED.toString(); // Ранг 7-9 (Красный)
        else if (rank >= 4) rankColor = org.bukkit.ChatColor.GOLD.toString(); // Ранг 4-6 (Желтый)
        
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
            String title = "СУПЕР-БОСС";
            if ("warlord".equalsIgnoreCase(bType)) title = "ДРЕВНИЙ ВОЕВОДА";
            else if ("storm".equalsIgnoreCase(bType)) title = "ПОВЕЛИТЕЛЬ БУРИ";
            else if ("alchemist".equalsIgnoreCase(bType)) title = "ПРОКЛЯТЫЙ АЛХИМИК";
            
            plate = org.bukkit.ChatColor.translateAlternateColorCodes('&', 
                "&d&l☠ " + title + " ☠ &c❤ " + color + String.format("%.0f", currentHp) + "&8/&c" + String.format("%.0f", maxHp)
            );
        } else if (isMiniBoss) {
            plate = org.bukkit.ChatColor.translateAlternateColorCodes('&', 
                "&4&l☠ МИНИ-БОСС ☠ &c[Ранг " + rank + "] &f" + name + " &c❤ " + color + String.format("%.0f", currentHp) + "&8/&c" + String.format("%.0f", maxHp)
            );
        } else {
            boolean bloodMoonActive = false;
            try {
                if (ru.example.vkchat.VKChatPlugin.getInstance() != null && 
                    ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager() != null) {
                    bloodMoonActive = ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager().isActive();
                }
            } catch (Throwable ignored) {}
            
            String prefix = bloodMoonActive ? "&4[Кровавая Луна] " : "";
            plate = org.bukkit.ChatColor.translateAlternateColorCodes('&', 
                prefix + "&8[" + rankColor + "Ранг " + rank + "&8] &f" + name + " &c❤ " + color + String.format("%.0f", currentHp) + "&8/&c" + String.format("%.0f", maxHp)
            );
        }
        
        mob.setCustomName(plate);
        mob.setCustomNameVisible(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDamage(EntityDamageEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Monster)) return;
        LivingEntity mob = (LivingEntity) e.getEntity();
        if (mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) {
                    updateNameplate(mob);
                }
            }, 1L);
        }

        // --- [НОВОЕ] ОБРАБОТКА СМЕНЫ ФАЗ СУПЕР-БОССОВ (ПРИ < 50% HP) ---
        if (mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER)) {
            double maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double nextHp = mob.getHealth() - e.getFinalDamage();
            int phase = mob.getPersistentDataContainer().getOrDefault(bossPhaseKey, PersistentDataType.INTEGER, 1);

            if (nextHp > 0 && nextHp <= (maxHp * 0.5) && phase == 1) {
                // Переход во 2 фазу!
                mob.getPersistentDataContainer().set(bossPhaseKey, PersistentDataType.INTEGER, 2);
                String bossType = mob.getPersistentDataContainer().get(superBossTypeKey, PersistentDataType.STRING);
                if (bossType == null) return;

                if (bossType.equals("warlord")) {
                    Bukkit.broadcastMessage("§c[Древний Воевода] МОЯ КРОВЬ КИПИТ! ПОЗНАЙТЕ ИСТИННУЮ ЯРОСТЬ КЛИНКА!");
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12000, 1)); // 10 минут
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 12000, 0));
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
                    mob.getWorld().spawnParticle(Particle.REDSTONE, mob.getLocation(), 100, 1.0, 1.0, 1.0, 0.1, new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));
                } else if (bossType.equals("storm")) {
                    Bukkit.broadcastMessage("§b[Повелитель Бури] ГРОЗА ПОГЛОТИТ ВАС! ПРЕКЛОНИТЕ КОЛЕНИ ПЕРЕД СТИХИЕЙ!");
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);
                    mob.getWorld().strikeLightningEffect(mob.getLocation());
                    mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                    mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                } else if (bossType.equals("alchemist")) {
                    Bukkit.broadcastMessage("§d[Проклятый Алхимик] ХА-ХА-ХА! МОИ СМЕРТЕЛЬНЫЕ РЕАГЕНТЫ ГОТОВЫ К РАСПЫЛЕНИЮ!");
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 2.0f, 0.9f);
                    mob.getWorld().spawnParticle(Particle.SPELL_WITCH, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobHeal(EntityRegainHealthEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Monster)) return;
        LivingEntity mob = (LivingEntity) e.getEntity();
        if (mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) {
                    updateNameplate(mob);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobAttack(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getDamager() instanceof Monster)) return;
        if (!(e.getEntity() instanceof Player)) return;

        Monster mob = (Monster) e.getDamager();
        Player player = (Player) e.getEntity();

        if (!mob.getPersistentDataContainer().has(rankKey, PersistentDataType.INTEGER)) return;
        int rank = mob.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);

        // 1. Fire Strike (Поджигатель, Ранг >= 4)
        if (plugin.getConfig().getBoolean("abilities.fire_strike.enabled", true)) {
            int minRank = plugin.getConfig().getInt("abilities.fire_strike.min-rank", 4);
            if (rank >= minRank) {
                int chance = plugin.getConfig().getInt("abilities.fire_strike.chance", 25);
                if (random.nextInt(100) < chance) {
                    int duration = plugin.getConfig().getInt("abilities.fire_strike.duration-seconds", 4);
                    player.setFireTicks(duration * 20);
                    player.sendMessage(org.bukkit.ChatColor.RED + "☠ Огненный удар! " + mob.getCustomName() + " поджег тебя!");
                }
            }
        }

        // 2. Web Weaver (Паутина, Ранг >= 5)
        if (plugin.getConfig().getBoolean("abilities.web_weaver.enabled", true)) {
            int minRank = plugin.getConfig().getInt("abilities.web_weaver.min-rank", 5);
            if (rank >= minRank) {
                int chance = plugin.getConfig().getInt("abilities.web_weaver.chance", 15);
                if (random.nextInt(100) < chance) {
                    int duration = plugin.getConfig().getInt("abilities.web_weaver.slowness-duration-seconds", 5);
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, duration * 20, 2));
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 1));
                    
                    // Спавним паутину под ногами
                    org.bukkit.block.Block block = player.getLocation().getBlock();
                    if (block.getType() == org.bukkit.Material.AIR) {
                        block.setType(org.bukkit.Material.COBWEB);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (block.getType() == org.bukkit.Material.COBWEB) {
                                block.setType(org.bukkit.Material.AIR);
                            }
                        }, 60L);
                    }
                    player.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "🕸 Сеть паутины! " + mob.getCustomName() + " опутал твои ноги!");
                }
            }
        }

        // 3. [НОВОЕ] Ядовитый Взрыв (Poison Burst, Ранг >= 6, шанс 15%)
        if (rank >= 6 && random.nextInt(100) < 15) {
            player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation(), 40, 1.0, 0.5, 1.0, 0.1);
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITCH_THROW, 1f, 0.8f);
            
            for (org.bukkit.entity.Entity near : player.getNearbyEntities(4, 4, 4)) {
                if (near instanceof Player) {
                    ((Player) near).addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 100, 0));
                    near.sendMessage(org.bukkit.ChatColor.GREEN + "☠️ [Ядовитый Взрыв] " + mob.getCustomName() + " распылил яд вокруг!");
                }
            }
        }

        // 4. [НОВОЕ] Гравитационный Толчок (Gravity Thrust, Ранг >= 8, шанс 10%)
        if (rank >= 8 && random.nextInt(100) < 10) {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);
            player.setVelocity(new Vector(0, 0.75, 0));
            player.sendMessage(org.bukkit.ChatColor.AQUA + "💥 [Гравитационный Толчок] " + mob.getCustomName() + " подбросил тебя в воздух!");
        }
    }

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
                if (random.nextInt(100) < chance) {
                    long now = System.currentTimeMillis();
                    long cd = plugin.getConfig().getInt("abilities.minion_summon.cooldown-seconds", 15) * 1000L;
                    if (now - minionCooldowns.getOrDefault(mob.getUniqueId(), 0L) >= cd) {
                        minionCooldowns.put(mob.getUniqueId(), now);
                        
                        // Призываем двух чешуйниц или маленьких зомби
                        org.bukkit.entity.EntityType minionType = mob.getType() == org.bukkit.entity.EntityType.SPIDER ? 
                                org.bukkit.entity.EntityType.CAVE_SPIDER : org.bukkit.entity.EntityType.SILVERFISH;
                        
                        if (isMiniBoss) {
                            minionType = org.bukkit.entity.EntityType.ZOMBIE;
                        }

                        for (int i = 0; i < 2; i++) {
                            org.bukkit.entity.Entity entity = mob.getWorld().spawnEntity(mob.getLocation().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1), minionType);
                            if (entity instanceof LivingEntity) {
                                LivingEntity minion = (LivingEntity) entity;
                                minion.setCustomName(org.bukkit.ChatColor.RED + "Прислужник " + mob.getType().name());
                                minion.setCustomNameVisible(false);
                                
                                if (minion instanceof org.bukkit.entity.Zombie) {
                                    ((org.bukkit.entity.Zombie) minion).setBaby(true);
                                }
                                
                                AttributeInstance mHp = minion.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                                if (mHp != null) {
                                    mHp.setBaseValue(10.0);
                                    minion.setHealth(10.0);
                                }
                            }
                        }
                        
                        mob.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, mob.getLocation(), 3);
                        mob.getWorld().playSound(mob.getLocation(), org.bukkit.Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);
                        
                        for (org.bukkit.entity.Entity near : mob.getNearbyEntities(10, 10, 10)) {
                            if (near instanceof Player) {
                                near.sendMessage(org.bukkit.ChatColor.GOLD + "⚡ " + mob.getCustomName() + " призывает своих слуг!");
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        Player killer = mob.getKiller();
        
        // --- [НОВОЕ] ОБРАБОТКА СМЕРТИ МОНСТРА ОСАДЫ ---
        if (plugin.getSiegeManager() != null) {
            plugin.getSiegeManager().handleSiegeMonsterKill(mob, killer);
        }

        // 1. [НОВОЕ] ЗАЩИТА ОТ ФАРМА НА СПАВНЕРАХ/КАЧАЛКАХ (10% Опыта, 0 Репутации ВК, стандартный дроп)
        if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "from_spawner"), PersistentDataType.INTEGER)) {
            if (killer != null) {
                killer.sendMessage(org.bukkit.ChatColor.GRAY + "⚠️ Из-за спавнер-качалки получаемый опыт урезан до 10%, а начисление репутации и кастомных рун полностью отключено!");
            }
            int exp = e.getDroppedExp();
            e.setDroppedExp((int) Math.round(exp * 0.10)); // 10% опыта
            return;
        }

        if (!mob.getPersistentDataContainer().has(diffKey, PersistentDataType.DOUBLE)) return;

        double multiplier = mob.getPersistentDataContainer().get(diffKey, PersistentDataType.DOUBLE);
        int rank = mob.getPersistentDataContainer().getOrDefault(rankKey, PersistentDataType.INTEGER, 1);
        boolean isMiniBoss = mob.getPersistentDataContainer().has(isBossKey, PersistentDataType.INTEGER);
        boolean isSuperBoss = mob.getPersistentDataContainer().has(isSuperBossKey, PersistentDataType.INTEGER);

        // Начисление контракта
        if (killer != null && plugin.getContractManager() != null) {
            plugin.getContractManager().handleMobKill(killer, rank, isMiniBoss, isSuperBoss);
        }

        // Начисление репутации ВК за убийство монстра
        if (killer != null) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("VKChat")) {
                    int vkId = ru.example.vkchat.VKChatPlugin.getInstance().getAuthManager().getLinkedVkId(killer);
                    if (vkId != -1) {
                        int baseRep = 2; // Базовая репутация за обычного моба
                        int finalRep = baseRep + (rank - 1) * 2; // Ранг 10 даст 20 репутации!
                        
                        if (isSuperBoss) {
                            finalRep += 50; // Бонус за супер-босса (+50 реп)
                        } else if (isMiniBoss) {
                            finalRep += 15; // Бонусный куш за мини-босса (+15 реп)
                        }
                        
                        // Если Кровавая Луна, удваиваем получаемую репутацию!
                        boolean bloodMoonActive = false;
                        try {
                            if (ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager() != null) {
                                bloodMoonActive = ru.example.vkchat.VKChatPlugin.getInstance().getBloodMoonManager().isActive();
                            }
                        } catch (Throwable ignored) {}
                        
                        if (bloodMoonActive) {
                            finalRep *= 2;
                        }
                        
                        // --- [НОВОЕ] ДИНАМИЧЕСКИЙ ЛИМИТ ФАРМА РЕПУТАЦИИ (базовый 300 + уровень профессий * 3!) ---
                        int totalJobLevels = 0;
                        try {
                            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
                            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                                Object dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                                int m = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, killer.getUniqueId(), "miner");
                                int w = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, killer.getUniqueId(), "woodcutter");
                                int f = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, killer.getUniqueId(), "farmer");
                                int a = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, killer.getUniqueId(), "alchemist");
                                int b = (int) dataManager.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dataManager, killer.getUniqueId(), "blacksmith");
                                totalJobLevels = m + w + f + a + b;
                            }
                        } catch (Exception ignored) {}

                        UUID pUuid = killer.getUniqueId();
                        long now = System.currentTimeMillis();
                        if (now - farmResetTimes.getOrDefault(pUuid, 0L) >= 3600000L) {
                            farmResetTimes.put(pUuid, now);
                            farmedRepToday.put(pUuid, 0);
                        }
                        
                        int maxHourRep = 300 + totalJobLevels * 3; // Динамический часовой лимит!
                        int currentHourlyRep = farmedRepToday.getOrDefault(pUuid, 0);
                        
                        if (currentHourlyRep >= maxHourRep) {
                            killer.sendMessage(org.bukkit.ChatColor.RED + "⚠️ Лимит фарма! На основе ваших профессий лимит составляет " + maxHourRep + " реп/час. Вы набили максимум. Отдохните!");
                            return;
                        }
                        
                        ru.example.vkchat.VKChatPlugin.getInstance().getReputationManager().addPoints(vkId, finalRep);
                        farmedRepToday.put(pUuid, currentHourlyRep + finalRep);
                        
                        String message = org.bukkit.ChatColor.GOLD + "🔺 +" + finalRep + " репутации ВК за убийство " + 
                                (isSuperBoss ? "Мирового Босса" : (isMiniBoss ? "Мини-Босса" : "монстра")) + " (" + mob.getType().name() + " [Ранг " + rank + "])!";
                        if (bloodMoonActive) {
                            message += org.bukkit.ChatColor.RED + " 🌙 (Бонус Кровавой Луны!)";
                        }
                        killer.sendMessage(message);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Умножение опыта
        if (plugin.getConfig().getBoolean("loot.multiply-exp", true)) {
            int currentExp = e.getDroppedExp();
            e.setDroppedExp((int) (currentExp + (currentExp * multiplier)));
        }

        // Умножение обычного дропа
        if (plugin.getConfig().getBoolean("loot.multiply-items", true)) {
            for (ItemStack drop : e.getDrops()) {
                int newAmount = (int) (drop.getAmount() + (drop.getAmount() * multiplier));
                if (newAmount > drop.getType().getMaxStackSize()) {
                    newAmount = drop.getType().getMaxStackSize();
                }
                drop.setAmount(newAmount);
            }
        }

        // --- [НОВОЕ] ВЫПАДЕНИЕ КРИСТАЛЛОВ И СВИТКОВ С МОБОВ РАНГА 9-10 (шанс 5%) ---
        if (rank >= 9 && killer != null && random.nextInt(100) < 5) {
            org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
            if (gearPlugin != null && gearPlugin.isEnabled()) {
                ItemStack dropToGive = null;
                int itemRoll = random.nextInt(3);
                if (itemRoll == 0) {
                    // Свиток сохранения
                    dropToGive = new ItemStack(Material.PAPER);
                    ItemMeta sMeta = dropToGive.getItemMeta();
                    sMeta.setDisplayName("§d§lСвиток Сохранения");
                    List<String> sLore = new ArrayList<>();
                    sLore.add("§7Защищает предмет от отката");
                    sLore.add("§7уровня заточки при неудаче!");
                    sMeta.setLore(sLore);
                    sMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "safety_scroll_price"), PersistentDataType.INTEGER, 1500);
                    dropToGive.setItemMeta(sMeta);
                } else if (itemRoll == 1) {
                    // Редкий кристалл заточки
                    dropToGive = new ItemStack(Material.DIAMOND);
                    ItemMeta cMeta = dropToGive.getItemMeta();
                    cMeta.setDisplayName("§9💎 Кристалл Заточки: Редкий [XI-XV]");
                    List<String> cLore = new ArrayList<>();
                    cLore.add("§7Позволяет затачивать снаряжение.");
                    cMeta.setLore(cLore);
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_tier"), PersistentDataType.STRING, "rare");
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_name"), PersistentDataType.STRING, "Редкий [XI-XV]");
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_price"), PersistentDataType.INTEGER, 900);
                    dropToGive.setItemMeta(cMeta);
                } else {
                    // Обычный кристалл
                    dropToGive = new ItemStack(Material.EMERALD);
                    ItemMeta cMeta = dropToGive.getItemMeta();
                    cMeta.setDisplayName("§a💎 Кристалл Заточки: Обычный [I-X]");
                    List<String> cLore = new ArrayList<>();
                    cLore.add("§7Позволяет затачивать снаряжение.");
                    cMeta.setLore(cLore);
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_tier"), PersistentDataType.STRING, "common");
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_name"), PersistentDataType.STRING, "Обычный [I-X]");
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "crystal_price"), PersistentDataType.INTEGER, 400);
                    dropToGive.setItemMeta(cMeta);
                }
                
                if (dropToGive != null) {
                    mob.getWorld().dropItemNaturally(mob.getLocation(), dropToGive);
                    killer.sendMessage(ChatColor.LIGHT_PURPLE + "✨ ОСОБЫЙ ДРОП! С сильного элитного монстра выпал: " + dropToGive.getItemMeta().getDisplayName() + ChatColor.LIGHT_PURPLE + "!");
                }
            }
        }

        // --- [НОВОЕ] ВЫПАДЕНИЕ ДРЕВНИХ ЖЕТОНОВ И ОСКОЛКОВ ---
        if (killer != null) {
            if (isSuperBoss) {
                // С супер-боссов 100% выпадает Осколок Артефакта и 1-2 Жетона Рун
                mob.getWorld().dropItemNaturally(mob.getLocation(), getArtifactShard());
                ItemStack rt = getRuneToken();
                rt.setAmount(1 + random.nextInt(2));
                mob.getWorld().dropItemNaturally(mob.getLocation(), rt);
                
                killer.sendMessage("§d✨ ПРЕДОПРЕДЕЛЕННЫЙ ЛУТ! С поверженного Мирового Супер-Босса выпали древние жетоны сокровищ!");
            } else if (isMiniBoss) {
                // С мини-боссов шанс 25% на Жетон Рун
                if (random.nextInt(100) < 25) {
                    mob.getWorld().dropItemNaturally(mob.getLocation(), getRuneToken());
                    killer.sendMessage("§6✨ НАХОДКА! С мини-босса выпал Древний Жетон Рун!");
                }
            }
        }

        // Экстра лут за элитных мобов
        if (plugin.getConfig().getBoolean("loot.extra-rewards.enabled", true)) {
            double minMult = plugin.getConfig().getDouble("loot.extra-rewards.min-multiplier", 3.0);
            if (multiplier >= minMult) {
                int chance = plugin.getConfig().getInt("loot.extra-rewards.chance", 15);
                if (random.nextInt(100) < chance) {
                    java.util.List<String> items = plugin.getConfig().getStringList("loot.extra-rewards.items");
                    if (!items.isEmpty()) {
                        String randomItem = items.get(random.nextInt(items.size()));
                        String[] parts = randomItem.split(";");
                        if (parts.length == 3) {
                            try {
                                org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                                int min = Integer.parseInt(parts[1]);
                                int max = Integer.parseInt(parts[2]);
                                int amount = random.nextInt(max - min + 1) + min;
                                
                                mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(mat, amount));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }

        // Дроп за мини-босса
        if (isMiniBoss) {
            java.util.List<String> guaranteedLoot = plugin.getConfig().getStringList("mini_bosses.guaranteed-loot");
            if (guaranteedLoot != null) {
                for (String itemStr : guaranteedLoot) {
                    String[] parts = itemStr.split(";");
                    if (parts.length == 3) {
                        try {
                            org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                            int min = Integer.parseInt(parts[1]);
                            int max = Integer.parseInt(parts[2]);
                            int amount = random.nextInt(max - min + 1) + min;
                            mob.getWorld().dropItemNaturally(mob.getLocation(), new ItemStack(mat, amount));
                        } catch (Exception ignored) {}
                    }
                }
            }
            
            mob.getWorld().spawnParticle(Particle.TOTEM, mob.getLocation(), 30);
            mob.getWorld().playSound(mob.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            
            for (org.bukkit.entity.Entity near : mob.getNearbyEntities(15, 15, 15)) {
                if (near instanceof Player) {
                    near.sendMessage(org.bukkit.ChatColor.GREEN + "🎉 Поздравляем! Вы одолели элитного МИНИ-БОССА " + mob.getType().name() + "!");
                }
            }
        }
    }

    // --- [НОВОЕ] ИСПОЛЬЗОВАНИЕ ТОКЕНОВ И ЖЕТОНОВ НА ПРАВЫЙ КЛИК ---
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        boolean isRuneToken = meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_rune_token"), PersistentDataType.INTEGER);
        boolean isArtifactShard = meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_artifact_shard"), PersistentDataType.INTEGER);

        // Совместимость со старыми наградами из оффлайн-походов, которые были созданы только по имени без PDC.
        if (!isRuneToken && !isArtifactShard && meta.hasDisplayName()) {
            String display = ChatColor.stripColor(meta.getDisplayName());
            if (item.getType() == Material.GOLD_NUGGET && display.equalsIgnoreCase("Древний Жетон Рун")) {
                isRuneToken = true;
            } else if (item.getType() == Material.PRISMARINE_CRYSTALS && display.equalsIgnoreCase("Осколок Древнего Артефакта")) {
                isArtifactShard = true;
            }
        }

        if (!isRuneToken && !isArtifactShard) return;

        e.setCancelled(true);

        // Проверяем привязку ВК
        int vkId = ru.example.vkchat.VKChatPlugin.getInstance().getAuthManager().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage("§c❌ Для использования жетона ваш игровой аккаунт должен быть привязан к ВК! Введите: /vklink");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Потребляем 1 предмет из руки
        item.setAmount(item.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        if (isRuneToken) {
            // Начисление +250 репутации ВК
            ru.example.vkchat.VKChatPlugin.getInstance().getReputationManager().addPoints(vkId, 250);
            p.sendMessage("§a🔺 Вы использовали Древний Жетон Рун и получили §6+250 Репутации ВК§a!");

            // Выдаем случайную руну/кристалл из vkchat_gear
            ItemStack rolled = rollRandomGearItem();
            safeGiveItem(p, rolled);
            p.sendMessage("§d✨ Вы получили предмет экипировки: " + (rolled.getItemMeta() != null ? rolled.getItemMeta().getDisplayName() : rolled.getType().name()));
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        } else {
            // Начисление +300 репутации ВК
            ru.example.vkchat.VKChatPlugin.getInstance().getReputationManager().addPoints(vkId, 300);
            p.sendMessage("§a🔺 Вы использовали Осколок Древнего Артефакта и получили §d+300 Репутации ВК§a!");

            // Выдаем случайный артефакт/свиток из vkchat_artifacts
            ItemStack rolled = rollRandomArtifactItem();
            safeGiveItem(p, rolled);
            p.sendMessage("§b✨ Вы получили древний артефакт/свиток: " + (rolled.getItemMeta() != null ? rolled.getItemMeta().getDisplayName() : rolled.getType().name()));
            p.getWorld().spawnParticle(Particle.SPELL_WITCH, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private void safeGiveItem(Player p, ItemStack item) {
        if (p.getInventory().firstEmpty() == -1) {
            p.getWorld().dropItemNaturally(p.getLocation(), item);
        } else {
            p.getInventory().addItem(item);
        }
    }

    private ItemStack rollRandomGearItem() {
        org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gearPlugin == null || !gearPlugin.isEnabled()) {
            return new ItemStack(Material.DIAMOND, 3);
        }
        
        int roll = random.nextInt(100);
        if (roll < 25) { // Common Crystal
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
        } else if (roll < 50) { // Rare Crystal
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
        } else if (roll < 60) { // Legendary Crystal
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
        } else if (roll < 75) { // Safety Scroll
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
        } else { // Random Rune
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
            int index = random.nextInt(runes.length);
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

    private ItemStack rollRandomArtifactItem() {
        org.bukkit.plugin.Plugin artifactsPlugin = Bukkit.getPluginManager().getPlugin("VKChatArtifacts");
        if (artifactsPlugin == null || !artifactsPlugin.isEnabled()) {
            return new ItemStack(Material.DIAMOND, 5);
        }
        
        int roll = random.nextInt(100);
        if (roll < 70) { // Random Artifact
            boolean isMythic = random.nextInt(100) < 15; // 15% mythic!
            return ru.example.vkchatartifacts.items.ArtifactFactory.generateArtifact(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin,
                isMythic
            );
        } else if (roll < 80) { // Cleanse Scroll
            return ru.example.vkchatartifacts.items.ConsumableFactory.generateCleanseScroll(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin
            );
        } else if (roll < 90) { // Revive Totem
            return ru.example.vkchatartifacts.items.ConsumableFactory.generateReviveScroll(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin
            );
        } else { // Escape Sphere
            return ru.example.vkchatartifacts.items.ConsumableFactory.generateEscapeScroll(
                (ru.example.vkchatartifacts.VKChatArtifactsPlugin) artifactsPlugin
            );
        }
    }
}
