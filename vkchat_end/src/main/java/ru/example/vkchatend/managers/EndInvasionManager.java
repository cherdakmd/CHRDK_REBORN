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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Система вторжений Энда в обычный мир — переработанная версия
 * без шалкеров, с новыми типами вторжений и улучшенной механикой волн
 */
public class EndInvasionManager implements Listener {
    private final VKChatEndPlugin plugin;
    private final NamespacedKey invasionMobKey;

    private final Map<String, InvasionData> activeInvasions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageTracker = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> mobToInvasion = new ConcurrentHashMap<>();

    public enum InvasionType {
        ENDER_RAIDS("Эндер-рейды", "Группы эндерменов атакуют поселения", 30,
                ChatColor.DARK_PURPLE, BarColor.PURPLE),
        VOID_PORTAL("Портал Бездны", "Разлом реальности извергает эндермитов и фантомов", 60,
                ChatColor.DARK_GRAY, BarColor.BLUE),
        ENDER_STORM("Эндер-шторм", "Грозовой фронт с эндер-молниями и фантомами", 45,
                ChatColor.DARK_AQUA, BarColor.BLUE),
        DRAGON_ATTACK("Атака Дракона", "Дракон Энда обрушивается на мир", 90,
                ChatColor.RED, BarColor.RED),
        CORRUPTION_SPREAD("Эндер-скверна", "Скверна Энда заражает ландшафт и существ", 120,
                ChatColor.DARK_RED, BarColor.RED);

        public final String displayName;
        public final String description;
        public final int durationMinutes;
        public final ChatColor color;
        public final BarColor barColor;

        InvasionType(String displayName, String description, int durationMinutes,
                     ChatColor color, BarColor barColor) {
            this.displayName = displayName;
            this.description = description;
            this.durationMinutes = durationMinutes;
            this.color = color;
            this.barColor = barColor;
        }
    }

    private static class InvasionData {
        InvasionType type;
        Location center;
        int radius;
        long startTime;
        long endTime;
        int wave;
        int totalMobsSpawned;
        BossBar bossBar;
        UUID invasionId;

        InvasionData(InvasionType type, Location center, int radius) {
            this.type = type;
            this.center = center;
            this.radius = radius;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + (type.durationMinutes * 60000L);
            this.wave = 0;
            this.totalMobsSpawned = 0;
            this.invasionId = UUID.randomUUID();
        }

        boolean isActive() {
            return System.currentTimeMillis() < endTime;
        }

        long remainingMs() {
            return Math.max(0, endTime - System.currentTimeMillis());
        }
    }

    public EndInvasionManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
        this.invasionMobKey = new NamespacedKey(plugin, "invasion_mob");
        startInvasionTask();
        startParticleTask();
    }

    private void startInvasionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkInvasions();
                maybeStartInvasion();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    /**
     * Задача частиц — визуальные эффекты над зоной вторжения
     */
    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (InvasionData invasion : activeInvasions.values()) {
                    if (!invasion.isActive()) continue;
                    spawnInvasionParticles(invasion);
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Каждые 5 секунд
    }

    private void spawnInvasionParticles(InvasionData invasion) {
        World world = invasion.center.getWorld();
        if (world == null) return;
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            int dx = rand.nextInt(invasion.radius * 2) - invasion.radius;
            int dz = rand.nextInt(invasion.radius * 2) - invasion.radius;
            int y = 16 + rand.nextInt(32);
            Location loc = invasion.center.clone().add(dx, y - invasion.center.getY(), dz);
            world.spawnParticle(Particle.PORTAL, loc, 3, 1, 1, 1, 0.05);
        }
        if (invasion.type == InvasionType.ENDER_STORM) {
            for (int i = 0; i < 3; i++) {
                int dx = rand.nextInt(invasion.radius * 2) - invasion.radius;
                int dz = rand.nextInt(invasion.radius * 2) - invasion.radius;
                int y = world.getHighestBlockYAt(invasion.center.getBlockX() + dx, invasion.center.getBlockZ() + dz);
                world.spawnParticle(Particle.ENCHANTMENT_TABLE,
                        new Location(world, invasion.center.getX() + dx, y + 3, invasion.center.getZ() + dz),
                        5, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    private void checkInvasions() {
        Iterator<Map.Entry<String, InvasionData>> it = activeInvasions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, InvasionData> entry = it.next();
            InvasionData invasion = entry.getValue();

            if (!invasion.isActive()) {
                endInvasion(invasion);
                it.remove();
                continue;
            }

            long elapsed = System.currentTimeMillis() - invasion.startTime;
            int newWave = (int) (elapsed / 120000);
            if (newWave > invasion.wave) {
                invasion.wave = newWave;
                spawnInvasionWave(invasion);
            }

            if (invasion.bossBar != null) {
                long remaining = invasion.remainingMs();
                long total = invasion.endTime - invasion.startTime;
                double progress = Math.max(0, Math.min(1, (double) remaining / total));
                invasion.bossBar.setProgress(progress);
                invasion.bossBar.setTitle(invasion.type.color + "⚔ " + invasion.type.displayName
                        + " — Волна " + invasion.wave
                        + " | " + formatTime(remaining));
            }
        }
    }

    private String formatTime(long millis) {
        long mins = millis / 60000;
        long secs = (millis % 60000) / 1000;
        return mins + ":" + (secs < 10 ? "0" : "") + secs;
    }

    private void maybeStartInvasion() {
        int maxActive = plugin.getConfig().getInt("end.invasions.max-active", 2);
        if (activeInvasions.size() >= maxActive) return;

        double chance = plugin.getConfig().getDouble("end.invasions.spawn-chance", 0.01);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        World normalWorld = Bukkit.getWorlds().get(0);
        if (normalWorld == null) return;

        List<Player> players = new ArrayList<>();
        for (Player p : normalWorld.getPlayers()) {
            if (p.getLocation().getY() > 60) players.add(p);
        }
        if (players.isEmpty()) return;

        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        Location center = target.getLocation().add(
                ThreadLocalRandom.current().nextInt(200) - 100,
                0,
                ThreadLocalRandom.current().nextInt(200) - 100
        );
        center.setY(normalWorld.getHighestBlockYAt(center) + 1);

        InvasionType[] types = InvasionType.values();
        InvasionType type = types[ThreadLocalRandom.current().nextInt(types.length)];

        startInvasion(center, type);
    }

    public void startInvasion(Location center, InvasionType type) {
        String key = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockZ();
        int radius = plugin.getConfig().getInt("end.invasions.radius", 50);

        InvasionData invasion = new InvasionData(type, center, radius);
        activeInvasions.put(key, invasion);

        invasion.bossBar = Bukkit.createBossBar(
                type.color + "⚔ " + type.displayName,
                type.barColor,
                BarStyle.SEGMENTED_10
        );
        invasion.bossBar.setVisible(true);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");
        Bukkit.broadcastMessage(type.color + "⚔ ВТОРЖЕНИЕ: " + type.displayName);
        Bukkit.broadcastMessage(ChatColor.GRAY + type.description);
        Bukkit.broadcastMessage(ChatColor.GRAY + "📍 " + center.getBlockX() + " "
                + center.getBlockZ() + " | Зона: " + radius + " блоков");
        Bukkit.broadcastMessage(ChatColor.GRAY + "⏳ " + type.durationMinutes + " мин | Волны каждые 2 мин");
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "═══════════════════════════════════");

        spawnInvasionWave(invasion);
    }

    private void spawnInvasionWave(InvasionData invasion) {
        World world = invasion.center.getWorld();
        if (world == null) return;

        int baseCount = 4 + invasion.wave;
        int radius = invasion.radius;
        Random rand = ThreadLocalRandom.current();

        switch (invasion.type) {
            case ENDER_RAIDS: {
                int endermanCount = baseCount + rand.nextInt(3);
                for (int i = 0; i < endermanCount; i++) {
                    LivingEntity mob = spawnInvasionMob(world, invasion.center, radius,
                            EntityType.ENDERMAN, invasion);
                    if (mob != null) {
                        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                                Integer.MAX_VALUE, 1));
                    }
                }
                Bukkit.broadcastMessage(invasion.type.color + "⚔ Волна " + invasion.wave
                        + ": " + endermanCount + " эндерменов!");
                break;
            }

            case VOID_PORTAL: {
                int endermiteCount = baseCount + rand.nextInt(2);
                int phantomCount = Math.min(invasion.wave, baseCount / 2 + 1);
                for (int i = 0; i < endermiteCount; i++) {
                    spawnInvasionMob(world, invasion.center, radius,
                            EntityType.ENDERMITE, invasion);
                }
                for (int i = 0; i < phantomCount; i++) {
                    LivingEntity phantom = spawnInvasionMobAir(world, invasion.center, radius,
                            EntityType.PHANTOM, invasion);
                    if (phantom != null) {
                        phantom.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                                Integer.MAX_VALUE, 2));
                    }
                }
                int total = endermiteCount + phantomCount;
                Bukkit.broadcastMessage(invasion.type.color + "⚔ Волна " + invasion.wave
                        + ": " + endermiteCount + " эндермитов + " + phantomCount + " фантомов!");
                break;
            }

            case ENDER_STORM: {
                int phantomCount = baseCount + rand.nextInt(3);
                int endermanCount = baseCount / 2;
                for (int i = 0; i < phantomCount; i++) {
                    LivingEntity phantom = spawnInvasionMobAir(world, invasion.center, radius,
                            EntityType.PHANTOM, invasion);
                    if (phantom != null) {
                        phantom.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                                Integer.MAX_VALUE, 2));
                    }
                }
                for (int i = 0; i < endermanCount; i++) {
                    spawnInvasionMob(world, invasion.center, radius,
                            EntityType.ENDERMAN, invasion);
                }
                // Молнии
                int lightningCount = 2 + rand.nextInt(3);
                for (int i = 0; i < lightningCount; i++) {
                    int dx = rand.nextInt(radius * 2) - radius;
                    int dz = rand.nextInt(radius * 2) - radius;
                    Location strike = invasion.center.clone().add(dx, 0, dz);
                    strike.setY(world.getHighestBlockYAt(strike) + 1);
                    world.strikeLightningEffect(strike);
                }
                Bukkit.broadcastMessage(invasion.type.color + "⚔ Волна " + invasion.wave
                        + ": " + phantomCount + " фантомов + " + endermanCount + " эндерменов! ⚡");
                break;
            }

            case DRAGON_ATTACK: {
                int dragonCount = Math.min(3, 1 + invasion.wave / 3);
                int endermanCount = baseCount;
                for (int i = 0; i < dragonCount; i++) {
                    Location airLoc = invasion.center.clone().add(
                            rand.nextInt(30) - 15,
                            20 + rand.nextInt(15),
                            rand.nextInt(30) - 15
                    );
                    EnderDragon dragon = (EnderDragon) world.spawnEntity(airLoc, EntityType.ENDER_DRAGON);
                    dragon.getPersistentDataContainer().set(invasionMobKey, PersistentDataType.INTEGER, invasion.wave);
                    dragon.setCustomName(ChatColor.RED + "☠ Дракон вторжения [" + invasion.wave + "]");
                    dragon.setCustomNameVisible(true);
                    dragon.setGlowing(true);
                    dragon.setPhase(EnderDragon.Phase.CIRCLING);
                    applyInvasionEffects(dragon, invasion.wave);
                    invasion.totalMobsSpawned++;
                    mobToInvasion.put(dragon.getUniqueId(), invasion.invasionId);
                }
                for (int i = 0; i < endermanCount; i++) {
                    spawnInvasionMob(world, invasion.center, radius,
                            EntityType.ENDERMAN, invasion);
                }
                Bukkit.broadcastMessage(invasion.type.color + "⚔ Волна " + invasion.wave
                        + ": " + dragonCount + " дракон(ов) + " + endermanCount + " эндерменов!");
                break;
            }

            case CORRUPTION_SPREAD: {
                int blockCount = baseCount * 3;
                int endermiteCount = baseCount + 2;
                for (int k = 0; k < blockCount; k++) {
                    int bx = invasion.center.getBlockX() + rand.nextInt(radius * 2) - radius;
                    int bz = invasion.center.getBlockZ() + rand.nextInt(radius * 2) - radius;
                    int by = world.getHighestBlockYAt(bx, bz);
                    org.bukkit.block.Block block = world.getBlockAt(bx, by, bz);
                    Material mat = block.getType();
                    if (mat == Material.GRASS_BLOCK || mat == Material.DIRT
                            || mat == Material.STONE || mat == Material.SAND) {
                        block.setType(rand.nextBoolean() ? Material.END_STONE : Material.OBSIDIAN);
                    }
                }
                for (int i = 0; i < endermiteCount; i++) {
                    spawnInvasionMob(world, invasion.center, radius,
                            EntityType.ENDERMITE, invasion);
                }
                Bukkit.broadcastMessage(invasion.type.color + "⚔ Волна " + invasion.wave
                        + ": " + blockCount + " блоков скверны + " + endermiteCount + " эндермитов!");
                break;
            }
        }
    }

    private LivingEntity spawnInvasionMob(World world, Location center, int radius,
                                          EntityType type, InvasionData invasion) {
        Location spawnLoc = center.clone().add(
                ThreadLocalRandom.current().nextInt(radius * 2) - radius,
                0,
                ThreadLocalRandom.current().nextInt(radius * 2) - radius
        );
        spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

        return tagAndBuff(world.spawnEntity(spawnLoc, type), invasion);
    }

    private LivingEntity spawnInvasionMobAir(World world, Location center, int radius,
                                             EntityType type, InvasionData invasion) {
        Location airLoc = center.clone().add(
                ThreadLocalRandom.current().nextInt(radius * 2) - radius,
                15 + ThreadLocalRandom.current().nextInt(20),
                ThreadLocalRandom.current().nextInt(radius * 2) - radius
        );
        return tagAndBuff(world.spawnEntity(airLoc, type), invasion);
    }

    private LivingEntity tagAndBuff(Entity entity, InvasionData invasion) {
        if (!(entity instanceof LivingEntity)) return null;
        LivingEntity mob = (LivingEntity) entity;
        mob.getPersistentDataContainer().set(invasionMobKey, PersistentDataType.INTEGER, invasion.wave);
        applyInvasionEffects(mob, invasion.wave);
        invasion.totalMobsSpawned++;
        mobToInvasion.put(mob.getUniqueId(), invasion.invasionId);
        return mob;
    }

    private void applyInvasionEffects(LivingEntity mob, int wave) {
        double multiplier = 1.0 + (wave * 0.25);

        if (mob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            double baseHealth = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseHealth * multiplier);
            mob.setHealth(mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }

        if (mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseDamage = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseDamage * multiplier);
        }

        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                Integer.MAX_VALUE, Math.min(3, wave / 3)));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,
                Integer.MAX_VALUE, Math.min(2, wave / 4)));

        mob.setCustomName(ChatColor.DARK_PURPLE + "☠ Эндер-захватчик [" + wave + "]");
        mob.setCustomNameVisible(true);
        mob.setGlowing(true);
    }

    private void endInvasion(InvasionData invasion) {
        if (invasion.bossBar != null) {
            invasion.bossBar.removeAll();
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════════");
        Bukkit.broadcastMessage(ChatColor.GREEN + "⚔ ОТРАЖЕНО: " + invasion.type.displayName);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Волн: " + invasion.wave
                + " | Мобов: " + invasion.totalMobsSpawned);
        Bukkit.broadcastMessage(ChatColor.GREEN + "═══════════════════════════════════");

        World world = invasion.center.getWorld();
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distance(invasion.center) <= invasion.radius * 1.5) {
                    int reward = 100 + (invasion.wave * 50);
                    // Бонус за урон
                    Map<UUID, Double> invasionDamage = damageTracker.get(invasion.invasionId);
                    if (invasionDamage != null) {
                        Double dmg = invasionDamage.get(p.getUniqueId());
                        if (dmg != null && dmg > 0) {
                            reward += (int)(dmg / 2);
                        }
                    }
                    try {
                        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                        if (vkId != -1) {
                            VKChatPlugin.getInstance().getApi().addReputation(vkId, reward);
                        }
                    } catch (Exception ignored) {}
                    plugin.getEndManager().addEndReputation(p, reward / 2);
                    p.sendMessage(invasion.type.color + "⚔ Вторжение отражено! +" + reward + " реп.");
                }
            }
        }
        damageTracker.remove(invasion.invasionId);
    }

    @EventHandler
    public void onMobDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        UUID mobId = e.getEntity().getUniqueId();
        UUID invasionId = mobToInvasion.get(mobId);
        if (invasionId == null) return;
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();

        damageTracker.computeIfAbsent(invasionId, k -> new ConcurrentHashMap<>())
                .merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        UUID mobId = mob.getUniqueId();
        UUID invasionId = mobToInvasion.remove(mobId);
        if (invasionId == null) return;

        Player killer = mob.getKiller();
        if (killer == null) return;

        int wave = mob.getPersistentDataContainer().getOrDefault(invasionMobKey,
                PersistentDataType.INTEGER, 1);
        int rep = 10 + (wave * 5);

        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(killer);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
            }
        } catch (Exception ignored) {}

        plugin.getEndManager().addEndReputation(killer, rep / 2);
        killer.sendMessage(ChatColor.DARK_PURPLE + "☠ Эндер-захватчик повержен! +" + rep + " реп.");
    }

    public int getActiveInvasionCount() {
        return activeInvasions.size();
    }

    public String getInvasionsInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.DARK_PURPLE).append("═══ ⚔ Вторжения Энда ═══\n\n");

        for (InvasionType type : InvasionType.values()) {
            sb.append(type.color).append("• ").append(type.displayName).append("\n");
            sb.append(ChatColor.GRAY).append("  ").append(type.description).append("\n");
            sb.append(ChatColor.DARK_GRAY).append("  Длительность: ").append(type.durationMinutes).append(" мин\n\n");
        }

        sb.append(ChatColor.GRAY).append("Активных вторжений: ")
                .append(ChatColor.WHITE).append(activeInvasions.size());
        if (!activeInvasions.isEmpty()) {
            sb.append("\n").append(ChatColor.GRAY).append("Активные:\n");
            for (InvasionData inv : activeInvasions.values()) {
                sb.append(inv.type.color).append("  • ").append(inv.type.displayName)
                        .append(" — X:").append(inv.center.getBlockX())
                        .append(" Z:").append(inv.center.getBlockZ())
                        .append(" | Волна ").append(inv.wave)
                        .append(" | ").append(formatTime(inv.remainingMs())).append("\n");
            }
        }

        return sb.toString();
    }
}
