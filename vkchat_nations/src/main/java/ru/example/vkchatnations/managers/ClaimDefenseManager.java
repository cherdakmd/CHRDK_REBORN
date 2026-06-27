package ru.example.vkchatnations.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchatnations.data.NationManager;

import java.util.*;

public class ClaimDefenseManager {

    private final VKChatNationsPlugin plugin;
    private final NationManager nationManager;
    private long lastAutoTrigger = 0;

    private final Map<UUID, ActiveDefense> activeDefenses = new HashMap<>();
    private final Map<String, Long> nationCooldowns = new HashMap<>();

    public ClaimDefenseManager(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
        this.nationManager = plugin.getNationManager();
        startAutoScheduler();
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("defense-events.enabled", true);
    }

    private int getAutoCheckInterval() {
        return plugin.getConfig().getInt("defense-events.auto-check-interval", 18000);
    }

    private double getAutoChance() {
        return plugin.getConfig().getDouble("defense-events.auto-chance", 0.15);
    }

    private long getCooldownMs() {
        return plugin.getConfig().getLong("defense-events.cooldown-seconds", 3600) * 1000L;
    }

    private int getRaidDuration() { return plugin.getConfig().getInt("defense-events.raid.duration-seconds", 180); }
    private int getRaidMobCount() { return plugin.getConfig().getInt("defense-events.raid.mob-count", 20); }
    private int getRaidWaves() { return plugin.getConfig().getInt("defense-events.raid.waves", 3); }
    private List<String> getRaidMobTypes() { return plugin.getConfig().getStringList("defense-events.raid.mobs"); }
    private int getRaidReward() { return plugin.getConfig().getInt("defense-events.raid.reward-durability", 50); }

    private int getSiegeDuration() { return plugin.getConfig().getInt("defense-events.siege.duration-seconds", 300); }
    private int getSiegeMinionCount() { return plugin.getConfig().getInt("defense-events.siege.minion-count", 12); }
    private String getSiegeBossType() { return plugin.getConfig().getString("defense-events.siege.boss-type", "WITHER_SKELETON"); }
    private double getSiegeBossHealth() { return plugin.getConfig().getDouble("defense-events.siege.boss-health", 200.0); }
    private int getSiegeReward() { return plugin.getConfig().getInt("defense-events.siege.reward-durability", 100); }

    private int getSabotageDuration() { return plugin.getConfig().getInt("defense-events.sabotage.duration-seconds", 120); }
    private int getSabotageMobCount() { return plugin.getConfig().getInt("defense-events.sabotage.mob-count", 8); }
    private List<String> getSabotageMobTypes() { return plugin.getConfig().getStringList("defense-events.sabotage.mobs"); }
    private int getSabotageReward() { return plugin.getConfig().getInt("defense-events.sabotage.reward-durability", 30); }

    private int getReward(String type) {
        return switch (type.toUpperCase()) {
            case "RAID" -> getRaidReward();
            case "SIEGE" -> getSiegeReward();
            case "SABOTAGE" -> getSabotageReward();
            default -> 0;
        };
    }

    // ==========================================================
    //  АВТОМАТИЧЕСКИЙ ТАЙМЕР
    // ==========================================================

    private void startAutoScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled()) return;
                long now = System.currentTimeMillis();
                if (now - lastAutoTrigger < getAutoCheckInterval() * 50L) return;
                if (Math.random() > getAutoChance()) return;

                ChunkClaim claim = pickRandomClaim();
                if (claim == null) return;
                if (isOnCooldown(claim.getNation())) return;

                List<Player> nationPlayers = getOnlineNationPlayers(claim.getNation());
                if (nationPlayers.isEmpty()) return;

                String type = pickRandomType();
                Player target = nationPlayers.get(new Random().nextInt(nationPlayers.size()));
                startDefense(type, target, claim);
                lastAutoTrigger = now;
            }
        }.runTaskTimer(plugin, 6000L, 600L);
    }

    // ==========================================================
    //  РУЧНОЙ ЗАПУСК (/nation defend)
    // ==========================================================

    public boolean startManualDefense(Player player) {
        if (!isEnabled()) {
            player.sendMessage(ChatColor.RED + "Система защиты приватов отключена!");
            return false;
        }

        ChunkClaim claim = nationManager.getClaimAt(player.getLocation());
        if (claim == null || !claim.getNation().equals(nationManager.getPlayerNation(player))) {
            player.sendMessage(ChatColor.RED + "Вы должны находиться в своем привате!");
            return false;
        }

        if (activeDefenses.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас уже идет оборона!");
            return false;
        }

        String type = pickRandomType();
        startDefense(type, player, claim);
        return true;
    }

    // ==========================================================
    //  ЗАПУСК ИВЕНТА
    // ==========================================================

    public void startDefense(String type, Player target, ChunkClaim claim) {
        if (claim == null || target == null) return;

        nationCooldowns.put(claim.getNation(), System.currentTimeMillis());

        String nationName = nationManager.getNationNamePublic(claim.getNation());
        String typeRu = switch (type.toUpperCase()) {
            case "RAID" -> "РЕЙД";
            case "SIEGE" -> "ОСАДА";
            case "SABOTAGE" -> "ДИВЕРСИЯ";
            default -> "АТАКА";
        };

        String warning = ChatColor.RED + "" + ChatColor.BOLD + typeRu + ChatColor.RESET
                + ChatColor.RED + " на " + ChatColor.WHITE + nationName + ChatColor.RED + " приват!";
        String coordMsg = ChatColor.GRAY + "Координаты: "
                + ChatColor.YELLOW + claim.getX() + " " + claim.getZ()
                + ChatColor.GRAY + " (мир: " + claim.getWorldName() + ")";

        nationManager.broadcastToNationWithPrefix(claim.getNation(), warning);
        nationManager.broadcastToNationWithPrefix(claim.getNation(), coordMsg);
        nationManager.broadcastToNationWithPrefix(claim.getNation(),
                ChatColor.GOLD + "Защитите приват! Награда: +" + getReward(type) + " прочности");

        // VK-уведомление
        try {
            ru.example.vkchat.VKChatPlugin vkPlugin = ru.example.vkchat.VKChatPlugin.getInstance();
            if (vkPlugin != null && vkPlugin.getApi() != null) {
                String vkMsg = typeRu + " на " + nationName + " приват! Координаты: " + claim.getX() + " " + claim.getZ();
                vkPlugin.getApi().sendToMainChat(vkMsg);
            }
        } catch (Exception ignored) {}

        switch (type.toUpperCase()) {
            case "RAID" -> startRaid(target, claim);
            case "SIEGE" -> startSiege(target, claim);
            case "SABOTAGE" -> startSabotage(target, claim);
        }
    }

    // ==========================================================
    //  1. RAID — ВОЛНА МОБОВ ИДЁТ К БЛОКУ ПРИВАТА
    // ==========================================================

    private void startRaid(Player target, ChunkClaim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;

        Location center = new Location(world, claim.getX(), claim.getY(), claim.getZ());
        List<String> mobTypes = getRaidMobTypes();
        int totalMobs = getRaidMobCount();
        int waves = getRaidWaves();
        int mobsPerWave = Math.max(1, totalMobs / waves);
        int duration = getRaidDuration();

        BossBar raidBar = Bukkit.createBossBar(
                ChatColor.RED + "РЕЙД — " + nationManager.getNationNamePublic(claim.getNation()),
                BarColor.RED, BarStyle.SEGMENTED_6, BarFlag.CREATE_FOG
        );
        raidBar.setProgress(1.0);

        ActiveDefense defense = new ActiveDefense("RAID", claim, System.currentTimeMillis() + duration * 1000L);
        activeDefenses.put(target.getUniqueId(), defense);

        List<Player> nationPlayers = getOnlineNationPlayers(claim.getNation());
        for (Player p : nationPlayers) raidBar.addPlayer(p);

        for (Player p : nationPlayers) {
            p.sendTitle(ChatColor.RED + "РЕЙД!", ChatColor.YELLOW + "Волна 1/" + waves, 10, 40, 10);
        }

        new BukkitRunnable() {
            int wave = 0;
            int spawned = 0;

            @Override
            public void run() {
                if (System.currentTimeMillis() > defense.getEndTime() || isClaimDestroyed(claim)) {
                    finishDefense(target, defense, raidBar, true);
                    cancel();
                    return;
                }

                double elapsed = (System.currentTimeMillis() - (defense.getEndTime() - duration * 1000L));
                double progress = Math.max(0, 1.0 - elapsed / (duration * 1000L));
                raidBar.setProgress(progress);

                if (spawned < totalMobs && wave < waves) {
                    int toSpawn = Math.min(mobsPerWave, totalMobs - spawned);
                    for (int i = 0; i < toSpawn; i++) {
                        Location spawnLoc = getSpawnLocation(center, claim.getRadius());
                        if (spawnLoc != null) {
                            spawnRaidMob(world, spawnLoc, center, mobTypes);
                            spawned++;
                        }
                    }
                    wave++;

                    if (wave < waves) {
                        for (Player p : getOnlineNationPlayers(claim.getNation())) {
                            p.sendMessage(ChatColor.RED + "Волна " + wave + "/" + waves + " приближается!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnRaidMob(World world, Location loc, Location target, List<String> mobTypes) {
        EntityType type = parseEntityType(mobTypes.isEmpty() ? "ZOMBIE" :
                mobTypes.get(new Random().nextInt(mobTypes.size())));

        Entity raw = world.spawnEntity(loc, type);
        if (!(raw instanceof LivingEntity)) return;
        LivingEntity mob = (LivingEntity) raw;
        mob.setCustomName(ChatColor.RED + "Рейдер");
        mob.setCustomNameVisible(true);
        mob.setMetadata("defense_raid", new FixedMetadataValue(plugin, true));

        if (mob instanceof Zombie || mob instanceof Skeleton) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 1));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 6000, 0));
        }

        if (mob instanceof Creeper) {
            ((Creeper) mob).setPowered(true);
            ((Creeper) mob).setExplosionRadius(4);
        }

        mob.setRemoveWhenFarAway(false);
    }

    // ==========================================================
    //  2. SIEGE — БОСС + ЭЛИТНЫЕ МИНЬОНЫ
    // ==========================================================

    private void startSiege(Player target, ChunkClaim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;

        Location center = new Location(world, claim.getX(), claim.getY(), claim.getZ());
        int duration = getSiegeDuration();

        BossBar siegeBar = Bukkit.createBossBar(
                ChatColor.DARK_PURPLE + "ОСАДА — " + nationManager.getNationNamePublic(claim.getNation()),
                BarColor.PURPLE, BarStyle.SEGMENTED_10, BarFlag.CREATE_FOG
        );
        siegeBar.setProgress(1.0);

        ActiveDefense defense = new ActiveDefense("SIEGE", claim, System.currentTimeMillis() + duration * 1000L);
        activeDefenses.put(target.getUniqueId(), defense);

        List<Player> nationPlayers = getOnlineNationPlayers(claim.getNation());
        for (Player p : nationPlayers) siegeBar.addPlayer(p);

        EntityType bossType = parseEntityType(getSiegeBossType());
        Location bossLoc = getSpawnLocation(center, claim.getRadius());
        if (bossLoc == null) bossLoc = center.clone().add(0, 2, 0);

        Entity rawBoss = world.spawnEntity(bossLoc, bossType);
        if (!(rawBoss instanceof LivingEntity)) return;
        LivingEntity boss = (LivingEntity) rawBoss;
        boss.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ОСАДНИК");
        boss.setCustomNameVisible(true);
        boss.setMetadata("defense_siege_boss", new FixedMetadataValue(plugin, true));

        AttributeInstance healthAttr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(getSiegeBossHealth());
            boss.setHealth(getSiegeBossHealth());
        }

        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 1));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 6000, 1));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 6000, 0));
        boss.setRemoveWhenFarAway(false);

        for (Player p : nationPlayers) {
            p.sendTitle(ChatColor.DARK_PURPLE + "ОСАДА!", ChatColor.YELLOW + "Убейте босса!", 10, 40, 10);
            p.sendMessage(ChatColor.DARK_PURPLE + "Босс появился! HP: " + (int) getSiegeBossHealth());
        }

        int minionCount = getSiegeMinionCount();
        int minionsPerWave = Math.max(2, minionCount / 3);

        new BukkitRunnable() {
            int wave = 0;
            int spawned = 0;

            @Override
            public void run() {
                if (System.currentTimeMillis() > defense.getEndTime() || isClaimDestroyed(claim)
                        || !boss.isValid() || boss.isDead()) {
                    boolean win = boss.isDead();
                    finishDefense(target, defense, siegeBar, win);
                    cancel();
                    return;
                }

                double elapsed = (System.currentTimeMillis() - (defense.getEndTime() - duration * 1000L));
                double progress = Math.max(0, 1.0 - elapsed / (duration * 1000L));
                siegeBar.setProgress(progress);

                if (spawned < minionCount && wave < 3) {
                    int toSpawn = Math.min(minionsPerWave, minionCount - spawned);
                    for (int i = 0; i < toSpawn; i++) {
                        Location spawnLoc = getSpawnLocation(center, claim.getRadius());
                        if (spawnLoc != null) {
                            spawnSiegeMinion(world, spawnLoc, center);
                            spawned++;
                        }
                    }
                    wave++;

                    for (Player p : getOnlineNationPlayers(claim.getNation())) {
                        p.sendMessage(ChatColor.DARK_PURPLE + "Волна миньонов " + wave + "/3!");
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnSiegeMinion(World world, Location loc, Location target) {
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE_VILLAGER, EntityType.STRAY};
        EntityType type = types[new Random().nextInt(types.length)];

        Entity raw = world.spawnEntity(loc, type);
        if (!(raw instanceof LivingEntity)) return;
        LivingEntity minion = (LivingEntity) raw;
        minion.setCustomName(ChatColor.DARK_PURPLE + "Осадник");
        minion.setCustomNameVisible(true);
        minion.setMetadata("defense_siege_minion", new FixedMetadataValue(plugin, true));

        minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 0));
        minion.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 6000, 0));

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);

        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        meta.setColor(Color.fromRGB(75, 0, 130));
        helmet.setItemMeta(meta);
        chestplate.setItemMeta(meta);
        leggings.setItemMeta(meta);
        boots.setItemMeta(meta);

        minion.getEquipment().setHelmet(helmet);
        minion.getEquipment().setChestplate(chestplate);
        minion.getEquipment().setLeggings(leggings);
        minion.getEquipment().setBoots(boots);

        minion.setRemoveWhenFarAway(false);
    }

    // ==========================================================
    //  3. SABOTAGE — СКРЫТЫЕ МОБЫ ЛОМБЛЮТ БЛОКИ
    // ==========================================================

    private void startSabotage(Player target, ChunkClaim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;

        Location center = new Location(world, claim.getX(), claim.getY(), claim.getZ());
        int duration = getSabotageDuration();
        int mobCount = getSabotageMobCount();
        List<String> mobTypes = getSabotageMobTypes();

        BossBar sabBar = Bukkit.createBossBar(
                ChatColor.YELLOW + "ДИВЕРСИЯ — " + nationManager.getNationNamePublic(claim.getNation()),
                BarColor.YELLOW, BarStyle.SEGMENTED_6, BarFlag.CREATE_FOG
        );
        sabBar.setProgress(1.0);

        ActiveDefense defense = new ActiveDefense("SABOTAGE", claim, System.currentTimeMillis() + duration * 1000L);
        activeDefenses.put(target.getUniqueId(), defense);

        List<Player> nationPlayers = getOnlineNationPlayers(claim.getNation());
        for (Player p : nationPlayers) sabBar.addPlayer(p);

        for (Player p : nationPlayers) {
            p.sendTitle(ChatColor.YELLOW + "ДИВЕРСИЯ!", ChatColor.GRAY + "Обыщите территорию!", 10, 40, 10);
            p.sendMessage(ChatColor.YELLOW + "Диверсанты проникли на территорию! Ищите и уничтожайте!");
        }

        for (int i = 0; i < mobCount; i++) {
            Location spawnLoc = getSpawnLocation(center, claim.getRadius());
            if (spawnLoc != null) {
                spawnSaboteur(world, spawnLoc, center, mobTypes);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() > defense.getEndTime() || isClaimDestroyed(claim)) {
                    boolean allDead = true;
                    for (Entity e : world.getNearbyEntities(center, claim.getRadius() + 10, 20, claim.getRadius() + 10)) {
                        if (e.hasMetadata("defense_saboteur")) {
                            allDead = false;
                            break;
                        }
                    }
                    finishDefense(target, defense, sabBar, allDead);
                    cancel();
                    return;
                }

                double elapsed = (System.currentTimeMillis() - (defense.getEndTime() - duration * 1000L));
                double progress = Math.max(0, 1.0 - elapsed / (duration * 1000L));
                sabBar.setProgress(progress);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnSaboteur(World world, Location loc, Location target, List<String> mobTypes) {
        EntityType type = parseEntityType(mobTypes.isEmpty() ? "WITCH" :
                mobTypes.get(new Random().nextInt(mobTypes.size())));

        Entity raw = world.spawnEntity(loc, type);
        if (!(raw instanceof LivingEntity)) return;
        LivingEntity mob = (LivingEntity) raw;
        mob.setCustomName(ChatColor.YELLOW + "Диверсант");
        mob.setCustomNameVisible(true);
        mob.setMetadata("defense_saboteur", new FixedMetadataValue(plugin, true));

        mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 6000, 0));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 1));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 6000, 0));

        if (mob instanceof Endermite) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 2));
        }

        mob.setRemoveWhenFarAway(false);
    }

    // ==========================================================
    //  ЗАВЕРШЕНИЕ ИВЕНТА
    // ==========================================================

    private void finishDefense(Player target, ActiveDefense defense, BossBar bar, boolean success) {
        activeDefenses.remove(target.getUniqueId());
        bar.removeAll();

        ChunkClaim claim = defense.getClaim();

        if (success) {
            int reward = getReward(defense.getType());
            claim.addDurability(reward);

            String msg = ChatColor.GREEN + "" + ChatColor.BOLD + "ОБОРОНА УСПЕШНА!"
                    + ChatColor.GREEN + " +" + reward + " прочности";
            nationManager.broadcastToNationWithPrefix(claim.getNation(), msg);
            nationManager.broadcastToNationWithPrefix(claim.getNation(),
                    ChatColor.GOLD + "Прочность привата: " + claim.getDurability() + "/" + claim.getMaxDurability());
        } else {
            String msg = ChatColor.RED + "" + ChatColor.BOLD + "ОБОРОНА ПРОВАЛЕНА!";
            nationManager.broadcastToNationWithPrefix(claim.getNation(), msg);
            nationManager.broadcastToNationWithPrefix(claim.getNation(),
                    ChatColor.RED + "Прочность привата: " + claim.getDurability() + "/" + claim.getMaxDurability());
        }
    }

    // ==========================================================
    //  УТИЛИТЫ
    // ==========================================================

    private ChunkClaim pickRandomClaim() {
        List<ChunkClaim> claims = new ArrayList<>(nationManager.getNationClaims().values());
        if (claims.isEmpty()) return null;
        return claims.get(new Random().nextInt(claims.size()));
    }

    private String pickRandomType() {
        double roll = Math.random();
        if (roll < 0.4) return "RAID";
        if (roll < 0.7) return "SIEGE";
        return "SABOTAGE";
    }

    private List<Player> getOnlineNationPlayers(String nationId) {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (nationId.equals(nationManager.getPlayerNation(p))) {
                result.add(p);
            }
        }
        return result;
    }

    private boolean isOnCooldown(String nation) {
        Long lastTime = nationCooldowns.get(nation);
        if (lastTime == null) return false;
        return System.currentTimeMillis() - lastTime < getCooldownMs();
    }

    private boolean isClaimDestroyed(ChunkClaim claim) {
        return claim.getDurability() <= 0;
    }

    private Location getSpawnLocation(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return null;

        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = new Random().nextInt(radius * 2) - radius;
            int dz = new Random().nextInt(radius * 2) - radius;
            int dy = new Random().nextInt(10) - 3;

            Location loc = center.clone().add(dx, dy, dz);
            if (loc.getBlock().getType() == Material.AIR
                    && loc.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                return loc;
            }
        }
        return center.clone().add(0, 3, 0);
    }

    private EntityType parseEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase().replace(" ", "_"));
        } catch (Exception e) {
            return EntityType.ZOMBIE;
        }
    }

    public boolean hasActiveDefense(UUID playerUuid) {
        return activeDefenses.containsKey(playerUuid);
    }

    public ActiveDefense getActiveDefense(UUID playerUuid) {
        return activeDefenses.get(playerUuid);
    }

    public int getActiveDefenseCount() {
        return activeDefenses.size();
    }

    public static class ActiveDefense {
        private final String type;
        private final ChunkClaim claim;
        private final long endTime;

        public ActiveDefense(String type, ChunkClaim claim, long endTime) {
            this.type = type;
            this.claim = claim;
            this.endTime = endTime;
        }

        public String getType() { return type; }
        public ChunkClaim getClaim() { return claim; }
        public long getEndTime() { return endTime; }

        public boolean isExpired() {
            return System.currentTimeMillis() > endTime;
        }
    }
}
