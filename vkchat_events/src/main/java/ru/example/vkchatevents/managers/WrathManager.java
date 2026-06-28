package ru.example.vkchatevents.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchatevents.VKChatEventsPlugin;
import ru.example.vkchatevents.util.ClaimProtection;

import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public class WrathManager implements Listener {
    private final VKChatEventsPlugin plugin;
    
    private boolean wrathActive = false;
    private LivingEntity activeBoss = null;

    // Состояние катаклизмов
    private String activeCataclysm = null;
    private int cataclysmTaskId = -1;
    private long cataclysmEndTime = 0;
    private Location spontaneousCenter = null; // Центр спонтанного катаклизма (у игрока)

    public WrathManager(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        
        int interval = plugin.getConfig().getInt("wrath.interval", 28800); // Раз в 8 часов (Мирный режим)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Раз в 8 часов разыгрываем либо босса, либо случайный катаклизм/благословение
                if (ThreadLocalRandom.current().nextBoolean()) {
                    tryStartWrath();
                } else {
                    String[] cats = {"acid_rain", "earthquake", "tempest", "meteor_shower", "blizzard", "eclipse", "reputation_bloom", "angelic_grace", "star_shower", "geysers", "blood_moon_hunt", "treasure_comet", "station_fall", "fog_shadows", "plasma_storm", "gravity_anomaly"};
                    startCataclysm(cats[ThreadLocalRandom.current().nextInt(cats.length)]);
                }
            }
        }.runTaskTimer(plugin, interval * 20L, interval * 20L);

        // Автоматический спавн катаклизмов возле игроков
        int autoInterval = plugin.getConfig().getInt("wrath.cataclysms.auto-spawn.check-interval-seconds", 300);
        int autoChance = plugin.getConfig().getInt("wrath.cataclysms.auto-spawn.chance-percent", 8);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeCataclysm != null || isActive()) return;
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                if (ThreadLocalRandom.current().nextInt(100) >= autoChance) return;

                // Взвешенный выбор типа катаклизма
                String[] allTypes = {"acid_rain", "earthquake", "tempest", "meteor_shower", "blizzard", "eclipse",
                        "reputation_bloom", "angelic_grace", "star_shower", "geysers", "blood_moon_hunt",
                        "treasure_comet", "station_fall", "fog_shadows", "plasma_storm", "gravity_anomaly"};
                java.util.List<String> weighted = new java.util.ArrayList<>();
                for (String t : allTypes) {
                    double w = plugin.getConfig().getDouble("wrath.cataclysms.auto-spawn.weights." + t, 1.0);
                    int count = (int) Math.max(1, Math.round(w * 10));
                    for (int i = 0; i < count; i++) weighted.add(t);
                }
                String type = weighted.get(ThreadLocalRandom.current().nextInt(weighted.size()));

                // Выбираем случайного онлайн-игрока рядом с которым случится катаклизм
                java.util.List<Player> online = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
                Player target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
                spontaneousCenter = target.getLocation().clone();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    startCataclysm(type);
                    String msg = "⚡ Стихия разражается вокруг " + target.getName() + "!";
                    target.sendMessage(ChatColor.GOLD + "[Стихия] " + msg);
                    target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
                });
            }
        }.runTaskTimer(plugin, autoInterval * 20L, autoInterval * 20L);
    }
    
    public boolean isActive() {
        return wrathActive && activeBoss != null && !activeBoss.isDead();
    }
    
    public Location getActiveLocation() {
        return activeBoss != null ? activeBoss.getLocation() : null;
    }

    public String getActiveCataclysm() {
        return activeCataclysm;
    }

    public long getCataclysmEndTime() {
        return cataclysmEndTime;
    }

    private boolean isLocationClaimed(Location loc) {
        return ClaimProtection.isLocationClaimed(loc);
    }

    /** Проверяет, попадает ли игрок в зону действия спонтанного катаклизма.
     *  Если spontaneousCenter == null (глобальный запуск) — все игроки попадают. */
    private boolean isPlayerInCataclysmZone(Player p) {
        if (spontaneousCenter == null) return true;
        if (!p.getWorld().equals(spontaneousCenter.getWorld())) return false;
        int radius = plugin.getConfig().getInt("wrath.cataclysms.auto-spawn.radius", 64);
        return p.getLocation().distanceSquared(spontaneousCenter) <= (long) radius * radius;
    }

    /** Комбинированная проверка: не на привате + в зоне катаклизма */
    private boolean shouldAffectPlayer(Player p) {
        return !isLocationClaimed(p.getLocation()) && isPlayerInCataclysmZone(p);
    }

    public void tryStartWrath() {
        if (isActive()) return;

        World world = Bukkit.getWorlds().get(0);
        int radius = plugin.getConfig().getInt("wrath.spawn-radius", 2000);
        Location spawnLoc = ClaimProtection.findSafeWildernessLocation(world, radius, plugin.getConfig().getInt("wrath.protected-radius", 48), 80);
        if (spawnLoc == null) return;

        String bossName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("wrath.boss.name", "&c&lАватар Гнева Богов"));
        String bossTypeStr = plugin.getConfig().getString("wrath.boss.entity-type", "WITHER");
        EntityType bossType;
        try { bossType = EntityType.valueOf(bossTypeStr); } catch (Exception ex) { bossType = EntityType.WITHER; }
        double bossHealth = plugin.getConfig().getDouble("wrath.boss.health", 1500.0);

        activeBoss = (LivingEntity) world.spawnEntity(spawnLoc, bossType);
        activeBoss.setCustomName(bossName);
        activeBoss.setCustomNameVisible(true);
        activeBoss.getPersistentDataContainer().set(new NamespacedKey(plugin, "wrath_boss"), PersistentDataType.BYTE, (byte)1);
        
        if (activeBoss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            activeBoss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(bossHealth);
            activeBoss.setHealth(bossHealth);
        }
        
        wrathActive = true;

        String msg = "Боги в ярости! " + ChatColor.stripColor(bossName) + " заспавнился на X: " + spawnLoc.getBlockX() + ", Z: " + spawnLoc.getBlockZ() + "!";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.DARK_RED + "[Гнев Богов] " + ChatColor.RED + msg);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
            if (plugin.getConfig().getBoolean("wrath.boss.night-on-spawn", true)) world.setTime(18000);
            if (plugin.getConfig().getBoolean("wrath.boss.storm-on-spawn", true)) {
                world.setStorm(true);
                world.setThundering(true);
            }
        }

        if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
            VKChatPlugin.getInstance().getApi().sendToMainChat("⚡ Гнев Богов!\n" + msg);
        }
    }

    // ==========================================
    // СИСТЕМА ПРИРОДНЫХ КАТАКЛИЗМОВ И БЛАГОСЛОВЕНИЙ
    // ==========================================

    private int catDuration(String type, int defaultSec) {
        return plugin.getConfig().getInt("wrath.cataclysms." + type + ".duration-seconds", defaultSec);
    }
    private int catTick(String type, int defaultTick) {
        return plugin.getConfig().getInt("wrath.cataclysms." + type + ".tick-interval", defaultTick);
    }

    public void startCataclysm(String type) {
        if (activeCataclysm != null) return;
        this.activeCataclysm = type;
        
        World world = Bukkit.getWorlds().get(0);

        if (type.equals("acid_rain")) {
            cataclysmEndTime = System.currentTimeMillis() + 180000L; // 3 минуты
            world.setStorm(true);
            world.setThundering(false);

            String alert = "⛈️ ВНИМАНИЕ! Начинается Кислотный Дождь! Кислота плавит плоть и разъедает броню! Найдите укрытие под крышей!";
            Bukkit.broadcastMessage(ChatColor.RED + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌧️ Кислотный Дождь!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (p.getWorld().equals(world) && p.getLocation().getBlock().getY() >= world.getHighestBlockYAt(p.getLocation())) {
                        boolean hasArmor = false;
                        for (ItemStack armor : p.getInventory().getArmorContents()) {
                            if (armor != null && armor.getType() != Material.AIR) {
                                hasArmor = true;
                                if (armor.hasItemMeta() && armor.getItemMeta() instanceof Damageable) {
                                    Damageable dmg = (Damageable) armor.getItemMeta();
                                    dmg.setDamage(dmg.getDamage() + 1); // Портим броню на 1 ед.
                                    armor.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
                                }
                            }
                        }

                        if (!hasArmor) {
                            p.damage(2.0); // Кислота наносит урон игроку без брони!
                            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
                        }

                        p.sendMessage(ChatColor.RED + "🌧️ Кислотный дождь разъедает вашу плоть и портит броню! Срочно под крышу!");
                        p.getWorld().spawnParticle(org.bukkit.Particle.DRIP_WATER, p.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5);
                    }
                }
            }, 0L, 60L); // Каждые 3 секунды

        } else if (type.equals("earthquake")) {
            cataclysmEndTime = System.currentTimeMillis() + 60000L; // 1 минута

            String alert = "🌋 ВНИМАНИЕ! Начинается мощнейшее Землетрясение! Земля трескается и уходит из-под ног! Остерегайтесь провалов!";
            Bukkit.broadcastMessage(ChatColor.GOLD + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌋 Землетрясение!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 120, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 120, 2));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_GRAVEL_BREAK, 1f, 0.5f);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);

                    // С шансом 15% под ногами игрока трескается и проваливается блок (если не приват)!
                    if (ThreadLocalRandom.current().nextInt(100) < 15) {
                        Block below = p.getLocation().clone().add(0, -1, 0).getBlock();
                        if (below.getType() != Material.AIR && below.getType() != Material.BEDROCK && below.getType() != Material.BARRIER) {
                            if (!isLocationClaimed(below.getLocation())) {
                                below.setType(Material.AIR);
                                p.getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRACK, below.getLocation().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, below.getType().createBlockData());
                                p.sendMessage(ChatColor.RED + "☠ Земля треснула и провалилась прямо под вами!");
                            }
                        }
                    }

                    // С шансом 25% создаем реальную физическую ХАРДКОРНУЮ трещину (crack) на земле рядом с игроком!
                    if (ThreadLocalRandom.current().nextInt(100) < 25) {
                        Location center = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(10) - 5, -1, ThreadLocalRandom.current().nextInt(10) - 5);
                        if (!isLocationClaimed(center)) {
                            boolean xAxis = ThreadLocalRandom.current().nextBoolean();
                            int length = 6 + ThreadLocalRandom.current().nextInt(5); // Хардкорная длина 6-10 блоков!
                            for (int i = 0; i < length; i++) {
                                Location loc = center.clone().add(xAxis ? i : 0, 0, xAxis ? 0 : i);
                                // Углубляем трещину на 3 блока вниз
                                for (int yOffset = 0; yOffset >= -3; yOffset--) {
                                    Location targetLoc = loc.clone().add(0, yOffset, 0);
                                    if (!isLocationClaimed(targetLoc)) {
                                        Block b = targetLoc.getBlock();
                                        if (b.getType() != Material.AIR && b.getType() != Material.BEDROCK && b.getType() != Material.BARRIER) {
                                            b.setType(Material.AIR);
                                            b.getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRACK, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, b.getType().createBlockData());
                                        }
                                    }
                                }
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
                            p.sendMessage(ChatColor.RED + "🚨 Почва содрогается, и на земле расходится глубокая трещина!");
                        }
                    }
                }
            }, 0L, 40L); // Каждые 2 секунды

        } else if (type.equals("tempest")) {
            cataclysmEndTime = System.currentTimeMillis() + 120000L; // 2 минуты
            world.setStorm(true);
            world.setThundering(true);

            String alert = "⛈️ ВНИМАНИЕ! Разразился Грозовой Шторм! Молнии бьют беспощадно, а ураганный ветер сносит игроков!";
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("⛈️ Грозовой Шторм!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                // Частые удары молний вокруг игроков
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (p.getWorld().equals(world)) {
                        if (ThreadLocalRandom.current().nextInt(100) < 25) {
                            Location strikeLoc = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(16) - 8, 0, ThreadLocalRandom.current().nextInt(16) - 8);
                            world.strikeLightning(strikeLoc);
                        }

                        // Ураганный ветер сносит игроков под открытым небом!
                        if (p.getLocation().getBlock().getY() >= world.getHighestBlockYAt(p.getLocation())) {
                            double dx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
                            double dz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
                            p.setVelocity(new org.bukkit.util.Vector(dx, 0.1, dz));
                            p.sendMessage(ChatColor.YELLOW + "💨 Сильный порыв ураганного ветра сносит вас в сторону!");
                        }
                    }
                }
            }, 0L, 20L); // Каждую секунду

        } else if (type.equals("meteor_shower")) {
            cataclysmEndTime = System.currentTimeMillis() + 120000L; // 2 минуты
            world.setStorm(false);
            world.setThundering(false);

            String alert = "☄️ ВНИМАНИЕ! Начинается Метеоритный Дождь! Горящие кометы падают прямо с небес! Остерегайтесь взрывов!";
            Bukkit.broadcastMessage(ChatColor.GOLD + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("☄️ Метеоритный Дождь!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (p.getWorld().equals(world) && ThreadLocalRandom.current().nextInt(100) < 30) {
                        Location meteorLoc = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(20) - 10, 15, ThreadLocalRandom.current().nextInt(20) - 10);
                        org.bukkit.entity.LargeFireball fireball = (org.bukkit.entity.LargeFireball) world.spawnEntity(meteorLoc, EntityType.FIREBALL);
                        fireball.setDirection(new org.bukkit.util.Vector(0, -1, 0)); // Летят ровно вниз
                        fireball.setYield(2.0f); // Взрыв малой мощности
                        
                        p.sendMessage(ChatColor.RED + "☄️ Свист падающего метеорита раздается совсем рядом!");
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GHAST_SHOOT, 1f, 0.5f);
                    }
                }
            }, 0L, 40L); // Каждые 2 секунды

        } else if (type.equals("blizzard")) {
            cataclysmEndTime = System.currentTimeMillis() + 120000L; // 2 минуты
            world.setStorm(true);
            world.setThundering(false);

            String alert = "❄️ ВНИМАНИЕ! Начинается Снежный Буран! Температура падает! Держитесь источников тепла (костры, факелы, лава), чтобы не замерзнуть!";
            Bukkit.broadcastMessage(ChatColor.AQUA + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("❄️ Снежный Буран!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (p.getWorld().equals(world)) {
                        boolean nearHeat = false;
                        Location pLoc = p.getLocation();
                        
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dy = -2; dy <= 2; dy++) {
                                for (int dz = -3; dz <= 3; dz++) {
                                    Material mat = pLoc.clone().add(dx, dy, dz).getBlock().getType();
                                    if (mat == Material.TORCH || mat == Material.WALL_TORCH || 
                                        mat == Material.CAMPFIRE || mat == Material.SOUL_CAMPFIRE || 
                                        mat == Material.LAVA || mat == Material.GLOWSTONE || mat == Material.JACK_O_LANTERN) {
                                        nearHeat = true;
                                        break;
                                    }
                                }
                                if (nearHeat) break;
                            }
                            if (nearHeat) break;
                        }

                        if (!nearHeat && pLoc.getBlock().getY() >= world.getHighestBlockYAt(pLoc)) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
                            p.damage(1.0); // Урон от обморожения
                            
                            p.sendMessage(ChatColor.BLUE + "❄️ Вы замерзаете! Найдите источник тепла (костер, факел, лаву)!");
                            p.getWorld().spawnParticle(org.bukkit.Particle.SNOWBALL, pLoc.add(0, 1.5, 0), 15, 0.5, 0.5, 0.5);
                        } else if (nearHeat) {
                            p.getWorld().spawnParticle(org.bukkit.Particle.FLAME, pLoc.add(0, 0.1, 0), 3, 0.2, 0.1, 0.2, 0.01);
                        }
                    }
                }
            }, 0L, 60L); // Каждые 3 секунды

        } else if (type.equals("eclipse")) {
            cataclysmEndTime = System.currentTimeMillis() + 120000L; // 2 минуты
            world.setTime(18000); // Полночь

            String alert = "🌑 ВНИМАНИЕ! Начинается Солнечное Затмение! Тьма поглотила солнце, а Твари Безды восстали из своих могил!";
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌑 Солнечное Затмение!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                world.setTime(18000); // Удерживаем ночь

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (p.getWorld().equals(world)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                        
                        if (ThreadLocalRandom.current().nextInt(100) < 30) {
                            EntityType[] mobs = {EntityType.WITHER_SKELETON, EntityType.PHANTOM, EntityType.ZOMBIE, EntityType.SKELETON};
                            EntityType selectedMob = mobs[ThreadLocalRandom.current().nextInt(mobs.length)];
                            Location spawn = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(10) - 5, 0, ThreadLocalRandom.current().nextInt(10) - 5);
                            spawn.setY(world.getHighestBlockYAt(spawn) + 1);
                            
                            if (!isLocationClaimed(spawn)) {
                                world.spawnEntity(spawn, selectedMob);
                                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);
                            }
                        }
                    }
                }
            }, 0L, 100L); // Каждые 5 секунд

        } else if (type.equals("reputation_bloom")) {
            // ПОЗИТИВНОЕ СОБЫТИЕ: ЗОЛОТОЙ ВЕК
            cataclysmEndTime = System.currentTimeMillis() + 180000L; // 3 минуты
            world.setStorm(false);
            world.setThundering(false);
            world.setTime(6000); // Полдень

            String alert = "✨ ВНИМАНИЕ! Начинается ЗОЛОТОЙ ВЕК! Боги благословили этот мир! Все игроки получили Удачу и Героя Деревни, а цены продажи на Бирже выросли на 50%!";
            Bukkit.broadcastMessage(ChatColor.GREEN + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("✨ Золотой Век!\n" + ChatColor.stripColor(alert));
            }

            // Прямая интеграция с vkchat_market (рефлексивно выставляем Королевскую ярмарку!)
            try {
                org.bukkit.plugin.Plugin marketPlugin = Bukkit.getPluginManager().getPlugin("VKChatMarket");
                if (marketPlugin != null && marketPlugin.isEnabled()) {
                    Object marketMgr = marketPlugin.getClass().getMethod("getMarketManager").invoke(marketPlugin);
                    java.lang.reflect.Field nameF = marketMgr.getClass().getDeclaredField("activeEventName");
                    java.lang.reflect.Field itemF = marketMgr.getClass().getDeclaredField("activeEventItemId");
                    java.lang.reflect.Field multF = marketMgr.getClass().getDeclaredField("activeEventMultiplier");
                    java.lang.reflect.Field expF = marketMgr.getClass().getDeclaredField("activeEventExpireTime");
                    nameF.setAccessible(true);
                    itemF.setAccessible(true);
                    multF.setAccessible(true);
                    expF.setAccessible(true);
                    nameF.set(marketMgr, "✨ Золотой Век");
                    itemF.set(marketMgr, "ALL");
                    multF.set(marketMgr, 1.5);
                    expF.set(marketMgr, System.currentTimeMillis() + 180000L);
                }
            } catch (Exception ignored) {}

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 120, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 120, 0));
                    p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1.5, 0), 5, 0.5, 0.5, 0.5);
                }
            }, 0L, 40L); // Каждые 2 секунды

        } else if (type.equals("angelic_grace")) {
            // ПОЗИТИВНОЕ СОБЫТИЕ: АНГЕЛЬСКАЯ БЛАГОДАТЬ
            cataclysmEndTime = System.currentTimeMillis() + 180000L; // 3 минуты
            world.setStorm(false);
            world.setThundering(false);

            String alert = "😇 ВНИМАНИЕ! Пролилась АНГЕЛЬСКАЯ БЛАГОДАТЬ! Свет небес исцеляет ваши раны! Все болезни и проклятия сняты, игроки получили Регенерацию и Сытость!";
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("😇 Ангельская Благодать!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Снимаем негативные эффекты
                    p.removePotionEffect(PotionEffectType.POISON);
                    p.removePotionEffect(PotionEffectType.WITHER);
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                    p.removePotionEffect(PotionEffectType.SLOW);
                    p.removePotionEffect(PotionEffectType.WEAKNESS);
                    p.removePotionEffect(PotionEffectType.UNLUCK);
                    p.removePotionEffect(PotionEffectType.CONFUSION);

                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 120, 0));
                    
                    p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 1.5, 0), 3, 0.3, 0.3, 0.3);
                    p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 5, 0.4, 0.1, 0.4, 0.01);
                }
            }, 0L, 40L); // Каждые 2 секунды

        } else if (type.equals("star_shower")) {
            // ПОЗИТИВНОЕ СОБЫТИЕ: ЗВЕЗДОПАД ЖЕЛАНИЙ
            cataclysmEndTime = System.currentTimeMillis() + 120000L; // 2 минуты
            world.setStorm(false);
            world.setThundering(false);
            world.setTime(14000); // Звездная ночь

            String alert = "🌠 ВНИМАНИЕ! Начинается сказочный ЗВЕЗДОПАД ЖЕЛАНИЙ! Падающие кометы оставляют глубокие кратеры и ценные сокровища на Диких землях!";
            Bukkit.broadcastMessage(ChatColor.GOLD + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌠 Звездопад Желаний!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                world.setTime(14000); // Удерживаем ночь

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    Location pLoc = p.getLocation();
                    if (p.getWorld().equals(world) && pLoc.getBlock().getY() >= world.getHighestBlockYAt(pLoc)) {
                        // Игрок под открытым звездным небом! Шанс 40% на удар кометы
                        if (ThreadLocalRandom.current().nextInt(100) < 40) {
                            Location starStrike = pLoc.clone().add(ThreadLocalRandom.current().nextInt(16) - 8, 0, ThreadLocalRandom.current().nextInt(16) - 8);
                            starStrike.setY(world.getHighestBlockYAt(starStrike));
                            
                            if (!isLocationClaimed(starStrike)) {
                                // 1. Эффект падения звезды
                                world.spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, starStrike, 3);
                                world.playSound(starStrike, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.5f);
                                
                                // 2. Создаем ХАРДКОРНЫЙ воронку/кратер радиусом 4 блока!
                                int r = 4;
                                for (int dx = -r; dx <= r; dx++) {
                                    for (int dy = -r; dy <= 0; dy++) {
                                        for (int dz = -r; dz <= r; dz++) {
                                            if (dx*dx + dy*dy + dz*dz <= r*r) {
                                                Location blockLoc = starStrike.clone().add(dx, dy, dz);
                                                if (!isLocationClaimed(blockLoc)) {
                                                    Block b = blockLoc.getBlock();
                                                    if (b.getType() != Material.BEDROCK && b.getType() != Material.BARRIER) {
                                                        b.setType(Material.AIR);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // 3. Спавним космическую руду/звезду в центре
                                Block centerBlock = starStrike.getBlock();
                                if (!isLocationClaimed(starStrike)) {
                                    centerBlock.setType(Material.CRYING_OBSIDIAN);
                                }
                                
                                // 4. Спавним лут/награды
                                Material[] prizes = {Material.GOLD_NUGGET, Material.LAPIS_LAZULI, Material.EXPERIENCE_BOTTLE, Material.EMERALD};
                                Material selectedPrize = prizes[ThreadLocalRandom.current().nextInt(prizes.length)];
                                world.dropItemNaturally(starStrike.clone().add(0, 1, 0), new ItemStack(selectedPrize, 2 + ThreadLocalRandom.current().nextInt(4)));
                                
                                p.sendMessage(ChatColor.GOLD + "🌠 Комета упала с небес, пробив глубокий кратер в земле совсем рядом! Исследуйте кратер!");
                                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
                            }
                        }
                    }
                }
            }, 0L, 100L); // Каждые 5 секунд

        } else if (type.equals("geysers")) {
            // КАТАКЛИЗМ: ГЕЙЗЕРЫ
            cataclysmEndTime = System.currentTimeMillis() + 120000L; // 2 минуты
            world.setStorm(false);
            world.setThundering(false);

            String alert = "🌋 ВНИМАНИЕ! Раскаленные ГЕЙЗЕРЫ прорывают земную кору! Из недр бьют столбы кипящей воды и пара, оставляя глубокие провалы!";
            Bukkit.broadcastMessage(ChatColor.RED + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌋 Гейзеры земли!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (p.getWorld().equals(world) && ThreadLocalRandom.current().nextInt(100) < 35) {
                        Location geyserLoc = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(14) - 7, 0, ThreadLocalRandom.current().nextInt(14) - 7);
                        geyserLoc.setY(world.getHighestBlockYAt(geyserLoc));

                        if (!isLocationClaimed(geyserLoc)) {
                            // 1. Создаем воронку на месте извержения гейзера
                            int r = 2;
                            for (int dx = -r; dx <= r; dx++) {
                                for (int dy = -r; dy <= 1; dy++) {
                                    for (int dz = -r; dz <= r; dz++) {
                                        if (dx*dx + dy*dy + dz*dz <= r*r) {
                                            Location blockLoc = geyserLoc.clone().add(dx, dy, dz);
                                            if (!isLocationClaimed(blockLoc)) {
                                                Block b = blockLoc.getBlock();
                                                if (b.getType() != Material.BEDROCK && b.getType() != Material.BARRIER) {
                                                    b.setType(Material.AIR);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Ставим воду на дно гейзера
                            Block bottom = geyserLoc.clone().add(0, -1, 0).getBlock();
                            if (!isLocationClaimed(geyserLoc.clone().add(0, -1, 0))) {
                                bottom.setType(Material.WATER);
                            }

                            // 3. Эффект извержения гейзера (водный столб)
                            for (int h = 0; h < 8; h++) {
                                Location particleLoc = geyserLoc.clone().add(0, h, 0);
                                world.spawnParticle(org.bukkit.Particle.WATER_SPLASH, particleLoc, 40, 0.5, 0.5, 0.5, 0.2);
                                world.spawnParticle(org.bukkit.Particle.CLOUD, particleLoc, 10, 0.3, 0.3, 0.3, 0.05);
                            }
                            world.playSound(geyserLoc, org.bukkit.Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.5f);
                            world.playSound(geyserLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

                            // 4. Подбрасываем и обжигаем игроков поблизости
                            for (org.bukkit.entity.Entity entity : world.getNearbyEntities(geyserLoc, 3, 4, 3)) {
                                if (entity instanceof LivingEntity) {
                                    LivingEntity le = (LivingEntity) entity;
                                    le.setVelocity(new org.bukkit.util.Vector(0, 1.2, 0));
                                    le.damage(6.0); // Кипяток наносит 6 HP урона
                                    le.setFireTicks(60); // Поджигает на 3 секунды (60 тиков)
                                    if (le instanceof Player) {
                                        le.sendMessage(ChatColor.RED + "☠ Вас подбросил и обжег раскаленный Гейзер!");
                                    }
                                }
                            }
                        }
                    }
                }
            }, 0L, 40L); // Каждые 2 секунды
        } else if (type.equals("blood_moon_hunt")) {
            cataclysmEndTime = System.currentTimeMillis() + 180000L; // 3 минуты
            world.setTime(18000);
            world.setStorm(false);

            String alert = "🌕 ВНИМАНИЕ! Восходит Кровавая Луна! В Диких Землях открылась охота на усиленных монстров с повышенным лутом.";
            Bukkit.broadcastMessage(ChatColor.DARK_RED + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌕 Кровавая Луна!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) {
                    stopCataclysm();
                    return;
                }
                EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK};
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().equals(world) || !shouldAffectPlayer(p)) continue;
                    if (ThreadLocalRandom.current().nextInt(100) >= 35) continue;
                    Location spawn = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(18) - 9, 0, ThreadLocalRandom.current().nextInt(18) - 9);
                    spawn.setY(world.getHighestBlockYAt(spawn) + 1);
                    if (isLocationClaimed(spawn)) continue;
                    LivingEntity mob = (LivingEntity) world.spawnEntity(spawn, mobs[ThreadLocalRandom.current().nextInt(mobs.length)]);
                    mob.setCustomName(ChatColor.RED + "Кровавый охотник");
                    mob.setCustomNameVisible(true);
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 1));
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 60, 0));
                    mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "blood_moon_mob"), PersistentDataType.BYTE, (byte) 1);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WOLF_GROWL, 0.8f, 0.7f);
                }
            }, 0L, 80L);

        } else if (type.equals("treasure_comet")) {
            cataclysmEndTime = System.currentTimeMillis() + 60000L;
            Location chestLoc;
            if (spontaneousCenter != null) {
                chestLoc = spontaneousCenter.clone().add(ThreadLocalRandom.current().nextInt(20) - 10, 0, ThreadLocalRandom.current().nextInt(20) - 10);
                chestLoc.setY(world.getHighestBlockYAt(chestLoc) + 1);
            } else {
                chestLoc = ClaimProtection.findSafeWildernessLocation(world, plugin.getConfig().getInt("wrath.spawn-radius", 2000), 32, 80);
                if (chestLoc == null) { stopCataclysm(); return; }
                chestLoc.setY(world.getHighestBlockYAt(chestLoc) + 1);
            }

            String alert = "💎 Комета Сокровищ рассыпалась над Дикими Землями! Тайник появился на X: " + chestLoc.getBlockX() + " Z: " + chestLoc.getBlockZ() + ".";
            Bukkit.broadcastMessage(ChatColor.AQUA + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("💎 Комета Сокровищ!\n" + ChatColor.stripColor(alert));
            }

            Block chestBlock = chestLoc.getBlock();
            chestBlock.setType(Material.CHEST);
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
            chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 12 + ThreadLocalRandom.current().nextInt(13)));
            chest.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 24 + ThreadLocalRandom.current().nextInt(25)));
            if (ThreadLocalRandom.current().nextBoolean()) chest.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP, 1 + ThreadLocalRandom.current().nextInt(3)));
            world.spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, chestLoc.clone().add(0.5, 1.5, 0.5), 80, 1.2, 1.2, 1.2, 0.08);
            world.playSound(chestLoc, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 0.9f);
            stopCataclysm();

        } else if (type.equals("station_fall")) {
            cataclysmEndTime = System.currentTimeMillis() + 60000L; // 1 минута

            String alert = "☄️ ВНИМАНИЕ! Вышедшая из строя Космическая Станция теряет орбиту и падает на землю! Ищите обломки на Диких Землях!";
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("☄️ Космическая Станция!\n" + ChatColor.stripColor(alert));
            }

            // Запускаем 10 секундный обратный отсчет перед падением
            new BukkitRunnable() {
                int count = 10;
                @Override
                public void run() {
                    if (count > 0) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(ChatColor.RED + "⏰ Столкновение со станцией через " + count + " сек!");
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            // Искры в небе
                            p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, p.getLocation().add(0, 10, 0), 20, 5.0, 2.0, 5.0, 0.1);
                        }
                        count--;
                    } else {
                        cancel();
                        // Падение! Находим безопасную локацию
                        World world = Bukkit.getWorlds().get(0);
                        Location spawnLoc;
                        if (spontaneousCenter != null) {
                            spawnLoc = spontaneousCenter.clone().add(ThreadLocalRandom.current().nextInt(20) - 10, 0, ThreadLocalRandom.current().nextInt(20) - 10);
                            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ()));
                        } else {
                            int rRadius = plugin.getConfig().getInt("wrath.spawn-radius", 2000);
                            spawnLoc = ClaimProtection.findSafeWildernessLocation(world, rRadius, 64, 80);
                            if (spawnLoc == null) {
                                stopCataclysm();
                                return;
                            }
                            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ()));
                        }

                        // Взрыв и звук
                        world.spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, spawnLoc, 5);
                        world.playSound(spawnLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
                        world.playSound(spawnLoc, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);

                        // Создаем кратер
                        int r = 5;
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dy = -r; dy <= 1; dy++) {
                                for (int dz = -r; dz <= r; dz++) {
                                    if (dx*dx + dy*dy + dz*dz <= r*r) {
                                        Location bLoc = spawnLoc.clone().add(dx, dy, dz);
                                        if (!isLocationClaimed(bLoc)) {
                                            Block b = bLoc.getBlock();
                                            if (b.getType() != Material.BEDROCK && b.getType() != Material.BARRIER) {
                                                b.setType(Material.AIR);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Уголь, обсидиан и огонь вокруг кратера
                        for (int dx = -r-2; dx <= r+2; dx++) {
                            for (int dz = -r-2; dz <= r+2; dz++) {
                                if (ThreadLocalRandom.current().nextInt(100) < 30) {
                                    Location fLoc = spawnLoc.clone().add(dx, -1, dz);
                                    fLoc.setY(world.getHighestBlockYAt(fLoc));
                                    if (!isLocationClaimed(fLoc)) {
                                        fLoc.getBlock().setType(Material.OBSIDIAN);
                                        fLoc.clone().add(0, 1, 0).getBlock().setType(Material.FIRE);
                                    }
                                }
                            }
                        }

                        // Спавним запертый отсек (Chest) в центре
                        Location chestLoc = spawnLoc.clone().add(0, -1, 0);
                        chestLoc.setY(world.getHighestBlockYAt(chestLoc) + 1);
                        if (!isLocationClaimed(chestLoc)) {
                            Block chestB = chestLoc.getBlock();
                            chestB.setType(Material.CHEST);
                            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestB.getState();
                            
                            // Кладем легендарные сокровища!
                            ItemStack rt = createRuneToken();
                            rt.setAmount(3);
                            chest.getInventory().addItem(rt);
                            
                            ItemStack as = createArtifactShard();
                            as.setAmount(2);
                            chest.getInventory().addItem(as);
                            
                            chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 8));
                            chest.getInventory().addItem(new ItemStack(Material.NETHERITE_INGOT, 2));

                            org.bukkit.plugin.Plugin gearPlugin = Bukkit.getPluginManager().getPlugin("VKChatGear");
                            if (gearPlugin != null) {
                                ItemStack scroll = new ItemStack(Material.PAPER);
                                ItemMeta sMeta = scroll.getItemMeta();
                                sMeta.setDisplayName("§d§lСвиток Сохранения");
                                sMeta.getPersistentDataContainer().set(new NamespacedKey(gearPlugin, "safety_scroll_price"), PersistentDataType.INTEGER, 1500);
                                scroll.setItemMeta(sMeta);
                                scroll.setAmount(3);
                                chest.getInventory().addItem(scroll);
                            }
                        }

                        // Спавним дроидов охраны
                        for (int i = 0; i < 3; i++) {
                            Location dLoc = chestLoc.clone().add(ThreadLocalRandom.current().nextInt(4)-2, 1, ThreadLocalRandom.current().nextInt(4)-2);
                            dLoc.setY(world.getHighestBlockYAt(dLoc) + 1);
                            LivingEntity droid = (LivingEntity) world.spawnEntity(dLoc, EntityType.SKELETON);
                            droid.setCustomName("§b🤖 Сбойный Дроид Охраны");
                            droid.setCustomNameVisible(true);
                            droid.setGlowing(true);
                            droid.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                            droid.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                            droid.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12000, 1));
                            droid.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 12000, 0));
                        }

                        // Объявление
                        String finishAlert = "☄️ БУУУМ! Космическая Станция столкнулась с землей на координатах X: " + spawnLoc.getBlockX() + " Z: " + spawnLoc.getBlockZ() + "! Спешите взломать ее защищенный отсек!";
                        Bukkit.broadcastMessage(ChatColor.GOLD + finishAlert);
                        if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                            VKChatPlugin.getInstance().getApi().sendToMainChat("☄️ Падение Станции!\n" + ChatColor.stripColor(finishAlert));
                        }

                        stopCataclysm();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
        } else if (type.equals("fog_shadows")) {
            // КАТАКЛИЗМ: ТУМАН ТЕНЕЙ
            int dur = catDuration("fog_shadows", 180);
            int tick = catTick("fog_shadows", 60);
            cataclysmEndTime = System.currentTimeMillis() + dur * 1000L;
            world.setStorm(true);
            world.setThundering(false);
            world.setTime(18000);

            String alert = "🌫️ ВНИМАНИЕ! Над сервером навис Плотный Туман Теней! Из мрака материализуются призрачные охотники, а видимость падает до нуля!";
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌫️ Туман Теней!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) { stopCataclysm(); return; }
                int spawnChance = plugin.getConfig().getInt("wrath.cataclysms.fog_shadows.shadow-spawn-chance", 40);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (!p.getWorld().equals(world)) continue;

                    // Плотный туман: слепота и тошнота
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 1));

                    // Спавн теней рядом с игроками
                    if (ThreadLocalRandom.current().nextInt(100) < spawnChance) {
                        Location spawn = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(12) - 6, 0, ThreadLocalRandom.current().nextInt(12) - 6);
                        spawn.setY(world.getHighestBlockYAt(spawn) + 1);
                        if (!isLocationClaimed(spawn)) {
                            EntityType[] shadowTypes = {EntityType.ENDERMAN, EntityType.WITHER_SKELETON, EntityType.VEX};
                            LivingEntity shadow = (LivingEntity) world.spawnEntity(spawn, shadowTypes[ThreadLocalRandom.current().nextInt(shadowTypes.length)]);
                            shadow.setCustomName("§5§lТень");
                            shadow.setCustomNameVisible(true);
                            shadow.setGlowing(true);
                            shadow.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur * 20, 2));
                            shadow.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, dur * 20, 1));
                            shadow.getPersistentDataContainer().set(new NamespacedKey(plugin, "blood_moon_mob"), PersistentDataType.BYTE, (byte) 1);
                        }
                    }

                    p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, p.getLocation().add(0, 1, 0), 20, 1.5, 0.5, 1.5, 0.02);
                }
            }, 0L, tick);

        } else if (type.equals("plasma_storm")) {
            // КАТАКЛИЗМ: ПЛАЗМЕННЫЙ ШТОРМ
            int dur = catDuration("plasma_storm", 120);
            int tick = catTick("plasma_storm", 20);
            cataclysmEndTime = System.currentTimeMillis() + dur * 1000L;
            world.setStorm(true);
            world.setThundering(true);

            String alert = "⚡ ВНИМАНИЕ! Начинается ПЛАЗМЕННЫЙ ШТОРМ! Электрические разряды пронзают небо, а все мобы получают ускорение и ярость!";
            Bukkit.broadcastMessage(ChatColor.YELLOW + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("⚡ Плазменный Шторм!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) { stopCataclysm(); return; }
                int lightningChance = plugin.getConfig().getInt("wrath.cataclysms.plasma_storm.lightning-chance", 40);
                double fireDmg = plugin.getConfig().getDouble("wrath.cataclysms.plasma_storm.fire-damage", 3.0);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (!p.getWorld().equals(world)) continue;

                    // Плазменные молнии
                    if (ThreadLocalRandom.current().nextInt(100) < lightningChance) {
                        Location strike = p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(12) - 6, 0, ThreadLocalRandom.current().nextInt(12) - 6);
                        world.strikeLightning(strike);
                        // Обжигаем nearby
                        for (org.bukkit.entity.Entity ent : world.getNearbyEntities(strike, 3, 3, 3)) {
                            if (ent instanceof LivingEntity && !ent.equals(p)) {
                                ((LivingEntity) ent).damage(fireDmg);
                                ((LivingEntity) ent).setFireTicks(60);
                            }
                        }
                    }

                    // Электрические частицы
                    p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, p.getLocation().add(0, 2, 0), 10, 1.0, 1.0, 1.0, 0.1);

                    // Ускоряем мобов рядом с игроком
                    for (org.bukkit.entity.Entity ent : world.getNearbyEntities(p.getLocation(), 64, 32, 64)) {
                        if (ent instanceof LivingEntity && !(ent instanceof Player)) {
                            ((LivingEntity) ent).addPotionEffect(new PotionEffect(PotionEffectType.SPEED, tick + 10, 1));
                        }
                    }
                }
            }, 0L, tick);

        } else if (type.equals("gravity_anomaly")) {
            // КАТАКЛИЗМ: ИЗВРАЩЕНИЕ ГРАВИТАЦИИ
            int dur = catDuration("gravity_anomaly", 150);
            int tick = catTick("gravity_anomaly", 40);
            cataclysmEndTime = System.currentTimeMillis() + dur * 1000L;

            String alert = "🌀 ВНИМАНИЕ! Происходит Извращение Гравитации! Игроков随机 подбрасывает в воздух, а предметы летают хаотично!";
            Bukkit.broadcastMessage(ChatColor.AQUA + alert);
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("🌀 Извращение Гравитации!\n" + ChatColor.stripColor(alert));
            }

            cataclysmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (System.currentTimeMillis() >= cataclysmEndTime) { stopCataclysm(); return; }
                int launchChance = plugin.getConfig().getInt("wrath.cataclysms.gravity_anomaly.launch-chance", 30);
                double launchForce = plugin.getConfig().getDouble("wrath.cataclysms.gravity_anomaly.launch-force", 2.5);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!shouldAffectPlayer(p)) continue;
                    if (!p.getWorld().equals(world)) continue;

                    // Случайный запуск в небо
                    if (ThreadLocalRandom.current().nextInt(100) < launchChance) {
                        p.setVelocity(new org.bukkit.util.Vector(
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.0,
                            launchForce,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.0
                        ));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 1));
                        p.sendMessage(ChatColor.AQUA + "🌀 Гравитация захватила вас! Вы летите вверх!");
                    }

                    // Гравитационные частицы
                    p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation().add(0, 0.5, 0), 15, 1.0, 0.5, 1.0, 0.3);
                    p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 2, 0), 10, 0.8, 0.3, 0.8, 0.1);

                    // Подбрасываем мобов рядом с игроком
                    for (org.bukkit.entity.Entity ent : world.getNearbyEntities(p.getLocation(), 48, 24, 48)) {
                        if (ent instanceof LivingEntity && !(ent instanceof Player) && ThreadLocalRandom.current().nextInt(100) < 15) {
                            ent.setVelocity(new org.bukkit.util.Vector(0, 1.5, 0));
                        }
                    }
                }
            }, 0L, tick);
        }
    }

    public void stopCataclysm() {
        if (cataclysmTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cataclysmTaskId);
            cataclysmTaskId = -1;
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "🍃 Стихия успокоилась. Катаклизм успешно завершен!");
        if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
            VKChatPlugin.getInstance().getApi().sendToMainChat("☀️ Погода наладилась. Катаклизм завершен.");
        }
        World world = Bukkit.getWorlds().get(0);
        world.setStorm(false);
        world.setThundering(false);
        activeCataclysm = null;
        spontaneousCenter = null;
    }

    // ==========================================
    // ЛОКАЛЬНЫЕ ФАБРИКИ ТОКЕНОВ (без зависимости от vkchat_mobs)
    // ==========================================
    private ItemStack createRuneToken() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lДревний Жетон Рун");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Редкий трофей, добытый в тяжелом бою.");
        lore.add("§eИспользование (ПКМ): +250 репутации ВК + случайная руна/кристалл!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArtifactShard() {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lОсколок Древнего Артефакта");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Реликт забытой эпохи.");
        lore.add("§eИспользование (ПКМ): +500 репутации ВК + шанс легендарной руны!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onBloodMoonMobDeath(EntityDeathEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(new NamespacedKey(plugin, "blood_moon_mob"), PersistentDataType.BYTE)) {
            e.getDrops().add(new ItemStack(Material.EMERALD, 1 + ThreadLocalRandom.current().nextInt(3)));
            if (ThreadLocalRandom.current().nextInt(100) < 20) e.getDrops().add(new ItemStack(Material.DIAMOND));
            e.setDroppedExp(e.getDroppedExp() + 10);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (isActive() && e.getEntity().equals(activeBoss)) {
            wrathActive = false;
            Player killer = e.getEntity().getKiller();
            String killerName = killer != null ? killer.getName() : "Неизвестными героями";
            
            String msg = ChatColor.stripColor(plugin.getConfig().getString("wrath.boss.name", "Аватар Гнева Богов")) + " успешно повержен кузнецом " + killerName + "!";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.GREEN + "[Гнев Богов] " + msg);
            }
            if (Bukkit.getPluginManager().getPlugin("VKChat") != null) {
                VKChatPlugin.getInstance().getApi().sendToMainChat("☀️ " + msg);
            }

            // Конфигурируемый лут босса
            if (plugin.getConfig().getBoolean("wrath.boss.loot.enabled", true)) {
                java.util.List<String> lootItems = plugin.getConfig().getStringList("wrath.boss.loot.items");
                for (String lootStr : lootItems) {
                    String[] parts = lootStr.split(";");
                    if (parts.length >= 2) {
                        try {
                            Material mat = Material.valueOf(parts[0]);
                            int min = Integer.parseInt(parts[1]);
                            int max = parts.length >= 3 ? Integer.parseInt(parts[2]) : min;
                            int amount = min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1));
                            e.getDrops().add(new ItemStack(mat, amount));
                        } catch (Exception ignored) {}
                    }
                }
            }
            
            e.getEntity().getWorld().setStorm(false);
            e.getEntity().getWorld().setThundering(false);
        }
    }

    /** Отмена взрывов (метеориты, TNT и т.д.) в приватах наций */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        e.blockList().removeIf(block -> ClaimProtection.isLocationClaimed(block.getLocation()));
    }

    // ==========================================
    // ПОКУПКА ИВЕНТОВ ЗА РЕПУТАЦИЮ ВК
    // ==========================================
    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        String cmd = e.getCommand();
        if (cmd.equals("!ивент") || cmd.equals("!катаклизм")) {
            e.setCancelled(true);
            String[] args = e.getArgs();
            int vkId = e.getSenderVkId();
            int peer = e.getPeerId();

            int cost = plugin.getConfig().getInt("wrath.vk-event-cost", 1500); // 1500 репутации ВК

            if (args.length < 2) {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, 
                        "⛏️ Использование: !ивент <дождь/земля/шторм/метеорит/буран/затмение/золото/благо/звезда/гейзер/луна/комета/станция/туман/плазма/гравитация/босс>\n" +
                        "💰 Стоимость запуска любого события — " + cost + " репутации ВК!");
                return;
            }

            int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (currentRep < cost) {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Недостаточно репутации! Требуется " + cost + " репутации ВК (у тебя: " + currentRep + ").");
                return;
            }

            String type = args[1].toLowerCase();
            UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
            if (uuid == null) {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Твой аккаунт не привязан к серверу Minecraft!");
                return;
            }

            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Для покупки мирового ивента ты должен находиться на сервере онлайн!");
                return;
            }

            if (type.equals("дождь") || type.equals("rain")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("acid_rain"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы успешно запустили событие 'Кислотный Дождь' за " + cost + " репутации!");
            } else if (type.equals("земля") || type.equals("earth")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("earthquake"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы успешно запустили событие 'Землетрясение' за " + cost + " репутации!");
            } else if (type.equals("шторм") || type.equals("storm")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("tempest"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы успешно запустили событие 'Грозовой Шторм' за " + cost + " репутации!");
            } else if (type.equals("метеорит") || type.equals("meteor")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("meteor_shower"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы успешно запустили событие 'Метеоритный Дождь' за " + cost + " репутации!");
            } else if (type.equals("буран") || type.equals("blizzard")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("blizzard"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы успешно запустили событие 'Снежный Буран' за " + cost + " репутации!");
            } else if (type.equals("затмение") || type.equals("eclipse")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("eclipse"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы успешно запустили событие 'Солнечное Затмение' за " + cost + " репутации!");
            } else if (type.equals("золото") || type.equals("gold") || type.equals("bloom")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("reputation_bloom"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили благословение 'Золотой Век' за " + cost + " репутации!");
            } else if (type.equals("благо") || type.equals("grace") || type.equals("angel")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("angelic_grace"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили благословение 'Ангельская Благодать' за " + cost + " репутации!");
            } else if (type.equals("звезда") || type.equals("star") || type.equals("shower")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("star_shower"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили благословение 'Звездопад Желаний' за " + cost + " репутации!");
            } else if (type.equals("гейзер") || type.equals("geyser") || type.equals("geysers")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("geysers"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили катаклизм 'Гейзеры земли' за " + cost + " репутации!");
            } else if (type.equals("луна") || type.equals("moon") || type.equals("blood") || type.equals("blood_moon")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("blood_moon_hunt"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили событие 'Кровавая Луна' за " + cost + " репутации!");
            } else if (type.equals("комета") || type.equals("comet") || type.equals("treasure") || type.equals("treasure_comet")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("treasure_comet"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили событие 'Комета Сокровищ' за " + cost + " репутации!");
            } else if (type.equals("станция") || type.equals("station") || type.equals("station_fall")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("station_fall"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили событие 'Падение Космической Станции' за " + cost + " репутации!");
            } else if (type.equals("туман") || type.equals("fog") || type.equals("fog_shadows") || type.equals("тени")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("fog_shadows"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили катаклизм 'Туман Теней' за " + cost + " репутации!");
            } else if (type.equals("плазма") || type.equals("plasma") || type.equals("plasma_storm")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("plasma_storm"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили катаклизм 'Плазменный Шторм' за " + cost + " репутации!");
            } else if (type.equals("гравитация") || type.equals("gravity") || type.equals("gravity_anomaly") || type.equals("гравитация")) {
                if (activeCataclysm != null) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ На сервере уже бушует катаклизм!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, () -> startCataclysm("gravity_anomaly"));
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы запустили катаклизм 'Извращение Гравитации' за " + cost + " репутации!");
            } else if (type.equals("босс") || type.equals("boss")) {
                if (isActive()) {
                    VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Аватар Гнева Богов уже бродит по серверу!");
                    return;
                }
                VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
                Bukkit.getScheduler().runTask(plugin, this::tryStartWrath);
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "✅ Вы призвали Аватара Гнева Богов за " + cost + " репутации!");
            } else {
                VKChatPlugin.getInstance().getApi().sendMessage(peer, "❌ Неверный тип катаклизма! Доступные: дождь, земля, шторм, метеорит, буран, затмение, золото, благо, звезда, гейзер, луна, комета, станция, туман, плазма, гравитация, босс.");
            }
        }
    }
}
