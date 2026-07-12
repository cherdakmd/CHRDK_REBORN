package ru.example.vkchatmobs.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchatmobs.util.VKChatBridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MobsEvents2Manager implements Listener {
    private final VKChatMobsPlugin plugin;
    private final NamespacedKey phaseKey;
    private final NamespacedKey raidBossKey;
    private final NamespacedKey threatBossKey;
    private final Map<UUID, Map<UUID, Double>> damage = new ConcurrentHashMap<>();
    private final Map<String, ActiveThreat> threats = new ConcurrentHashMap<>();
    private final Map<String, Integer> triggerKills = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rareRewardDaily = new ConcurrentHashMap<>();

    private static class ActiveThreat {
        String world;
        Location center;
        int phase = 1;
        long nextPhase;
        UUID boss;
        ActiveThreat(Location center) { this.world = center.getWorld().getName(); this.center = center; this.nextPhase = System.currentTimeMillis() + 45000L; }
    }

    public MobsEvents2Manager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.phaseKey = new NamespacedKey(plugin, "events2_phase");
        this.raidBossKey = new NamespacedKey(plugin, "events2_raid_boss");
        this.threatBossKey = new NamespacedKey(plugin, "events2_threat_boss");
        startTicker();
    }

    public int getActiveThreatCount() { return threats.size(); }
    public String listThreats() {
        if (threats.isEmpty()) return "Активных угроз нет.";
        StringBuilder sb = new StringBuilder("Активные угрозы:\n");
        for (ActiveThreat t : threats.values()) sb.append(t.world).append(" phase ").append(t.phase).append(" X").append(t.center.getBlockX()).append(" Z").append(t.center.getBlockZ()).append("\n");
        return sb.toString();
    }

    public void stopAllThreats() {
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class)) {
                if (e.getPersistentDataContainer().has(threatBossKey, PersistentDataType.INTEGER)) e.remove();
            }
        }
        threats.clear();
    }

    public void startThreatNear(Player p) { startThreat(findSafeLocation(p.getLocation())); }

    public void startThreat(Location loc) {
        loc = findSafeLocation(loc);
        if (loc == null || loc.getWorld() == null) return;
        String key = loc.getWorld().getName();
        if (threats.containsKey(key)) return;
        ActiveThreat t = new ActiveThreat(loc);
        threats.put(key, t);
        announce("⚠ [МИРОВАЯ УГРОЗА] Предвестники замечены в мире " + key + " около X:" + loc.getBlockX() + " Z:" + loc.getBlockZ());
        spawnThreatPhase(t);
    }

    private Location findSafeLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) return origin;
        World w = origin.getWorld();
        int radius = plugin.getConfig().getInt("events2.threats.safe-search-radius", 80);
        for (int tries = 0; tries < 30; tries++) {
            int dx = tries == 0 ? 0 : ThreadLocalRandom.current().nextInt(radius * 2 + 1) - radius;
            int dz = tries == 0 ? 0 : ThreadLocalRandom.current().nextInt(radius * 2 + 1) - radius;
            Location loc = origin.clone().add(dx, 0, dz);
            loc.setY(w.getHighestBlockYAt(loc) + 1);
            if (loc.distanceSquared(w.getSpawnLocation()) < 80 * 80) continue;
            if (!isClaimProtected(loc)) return loc;
        }
        return origin;
    }

    private boolean isClaimProtected(Location loc) {
        try {
            org.bukkit.plugin.Plugin events = Bukkit.getPluginManager().getPlugin("VKChatEvents");
            if (events != null && events.isEnabled()) {
                Class<?> cp = Class.forName("ru.example.vkchatevents.util.ClaimProtection");
                Object result = cp.getMethod("isProtected", Location.class, int.class).invoke(null, loc, plugin.getConfig().getInt("events2.threats.protected-radius", 48));
                if (result instanceof Boolean) return (Boolean) result;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override public void run() {
                tickBossPhases();
                tickThreats();
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private void tickBossPhases() {
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity boss : w.getEntitiesByClass(LivingEntity.class)) {
                if (!boss.getPersistentDataContainer().has(raidBossKey, PersistentDataType.INTEGER) && !boss.getPersistentDataContainer().has(threatBossKey, PersistentDataType.INTEGER)) continue;
                if (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) == null) continue;
                double max = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double hp = boss.getHealth();
                int phase = boss.getPersistentDataContainer().getOrDefault(phaseKey, PersistentDataType.INTEGER, 1);
                int next = hp <= max * 0.33 ? 3 : hp <= max * 0.66 ? 2 : 1;
                if (next > phase) {
                    boss.getPersistentDataContainer().set(phaseKey, PersistentDataType.INTEGER, next);
                    onPhase(boss, next);
                }
            }
        }
    }

    private void onPhase(LivingEntity boss, int phase) {
        String name = ChatColor.stripColor(boss.getCustomName() == null ? boss.getType().name() : boss.getCustomName());
        if (phase == 2) {
            announce("🔥 [РЕЙД] " + name + " входит во 2 фазу: усиление и прислужники!");
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 5, 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 60 * 5, 0));
            spawnAdds(boss.getLocation(), 4);
        } else if (phase == 3) {
            announce("☠ [РЕЙД] " + name + " входит в финальную ярость!");
            boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20 * 60 * 5, 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 1));
            telegraphNova(boss);
        }
    }

    private void telegraphNova(LivingEntity boss) {
        Location c = boss.getLocation();
        World w = c.getWorld();
        if (w == null) return;
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36.0;
            w.spawnParticle(Particle.REDSTONE, c.clone().add(Math.cos(a) * 5, 0.2, Math.sin(a) * 5), 1, new Particle.DustOptions(Color.PURPLE, 1.5f));
        }
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!boss.isValid() || boss.isDead()) return;
            w.spawnParticle(Particle.EXPLOSION_LARGE, boss.getLocation(), 4, 2, 0.5, 2);
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(boss.getLocation()) < 36) p.damage(10.0, boss);
        }, 35L);
    }

    private void tickThreats() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, ActiveThreat>> it = threats.entrySet().iterator(); it.hasNext();) {
            ActiveThreat t = it.next().getValue();
            if (now < t.nextPhase) continue;
            t.phase++;
            if (t.phase > 5) { it.remove(); announce("✅ [МИРОВАЯ УГРОЗА] Угроза в мире " + t.world + " рассеялась."); continue; }
            t.nextPhase = now + plugin.getConfig().getLong("events2.threats.phase-seconds", 45) * 1000L;
            spawnThreatPhase(t);
        }
    }

    private void spawnThreatPhase(ActiveThreat t) {
        World w = Bukkit.getWorld(t.world); if (w == null) return;
        String[] names = {"", "Предвестники", "Порталы", "Волны", "Командиры", "Босс угрозы"};
        announce("⚠ [УГРОЗА] Фаза " + t.phase + "/5: " + names[t.phase] + " в мире " + t.world + " X:" + t.center.getBlockX() + " Z:" + t.center.getBlockZ());
        if (t.phase == 1) spawnAdds(t.center, 4);
        else if (t.phase == 2) { cosmeticPortal(t.center); spawnAdds(t.center, 6); }
        else if (t.phase == 3) spawnAdds(t.center, 12);
        else if (t.phase == 4) for (int i = 0; i < 3; i++) plugin.getHardcoreMobManager().spawnCustom(t.center.clone().add(ThreadLocalRandom.current().nextInt(10)-5,0,ThreadLocalRandom.current().nextInt(10)-5), "mini", null, null);
        else if (t.phase == 5) {
            LivingEntity boss = plugin.getHardcoreMobManager().spawnCustom(t.center, "raid", "necromancer", worldElement(w));
            if (boss != null) { boss.getPersistentDataContainer().set(threatBossKey, PersistentDataType.INTEGER, 1); boss.getPersistentDataContainer().set(raidBossKey, PersistentDataType.INTEGER, 1); boss.getPersistentDataContainer().set(phaseKey, PersistentDataType.INTEGER, 1); t.boss = boss.getUniqueId(); }
        }
    }

    private String worldElement(World w) {
        if (w.getEnvironment() == World.Environment.NETHER) return "fire";
        if (w.getEnvironment() == World.Environment.THE_END) return "dark";
        return ThreadLocalRandom.current().nextBoolean() ? "storm" : "poison";
    }

    private void cosmeticPortal(Location c) {
        World w = c.getWorld(); if (w == null) return;
        for (int i = 0; i < 80; i++) w.spawnParticle(Particle.PORTAL, c.clone().add(ThreadLocalRandom.current().nextDouble()*4-2, ThreadLocalRandom.current().nextDouble()*3, ThreadLocalRandom.current().nextDouble()*4-2), 1, 0,0,0,0.1);
        w.playSound(c, Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.2f);
    }

    private void spawnAdds(Location loc, int count) {
        World w = loc.getWorld(); if (w == null) return;
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK, EntityType.STRAY, EntityType.WITCH, EntityType.PILLAGER, EntityType.VINDICATOR};
        for (int i = 0; i < count; i++) {
            Location s = loc.clone().add(ThreadLocalRandom.current().nextInt(12)-6, 0, ThreadLocalRandom.current().nextInt(12)-6);
            s.setY(w.getHighestBlockYAt(s) + 1);
            Entity e = w.spawnEntity(s, types[ThreadLocalRandom.current().nextInt(types.length)]);
            if (e instanceof LivingEntity) plugin.getHardcoreMobManager().makeElite((LivingEntity) e, "elite", null, worldElement(w), true);
        }
        if (count >= 8 && ThreadLocalRandom.current().nextInt(100) < 30) {
            Location miniloc = loc.clone().add(ThreadLocalRandom.current().nextInt(8)-4, 0, ThreadLocalRandom.current().nextInt(8)-4);
            miniloc.setY(w.getHighestBlockYAt((int)miniloc.getX(), (int)miniloc.getZ()) + 1);
            plugin.getHardcoreMobManager().spawnCustom(miniloc, "mini", null, worldElement(w));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity boss = (LivingEntity) e.getEntity();
        if (!boss.getPersistentDataContainer().has(raidBossKey, PersistentDataType.INTEGER) && !boss.getPersistentDataContainer().has(threatBossKey, PersistentDataType.INTEGER)) return;
        Player p = null;
        if (e.getDamager() instanceof Player) p = (Player) e.getDamager();
        else if (e.getDamager() instanceof Projectile && ((Projectile)e.getDamager()).getShooter() instanceof Player) p = (Player) ((Projectile)e.getDamager()).getShooter();
        if (p == null) return;
        damage.computeIfAbsent(boss.getUniqueId(), k -> new ConcurrentHashMap<>()).merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        boolean boss = mob.getPersistentDataContainer().has(raidBossKey, PersistentDataType.INTEGER) || mob.getPersistentDataContainer().has(threatBossKey, PersistentDataType.INTEGER);
        if (!boss) {
            Player killer = mob.getKiller();
            if (killer != null && !mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "from_spawner"), PersistentDataType.INTEGER)) maybeTriggerThreat(killer);
            return;
        }
        rewardParticipants(mob, mob.getKiller(), e.getDrops());
        damage.remove(mob.getUniqueId());
    }

    private void maybeTriggerThreat(Player killer) {
        if (!plugin.getConfig().getBoolean("events2.triggers.enabled", true)) return;
        String world = killer.getWorld().getName();
        int n = triggerKills.getOrDefault(world, 0) + 1;
        int threshold = plugin.getConfig().getInt("events2.triggers.mob-kills-for-threat", 240);
        if (n >= threshold && !threats.containsKey(world)) {
            triggerKills.put(world, 0);
            if (ThreadLocalRandom.current().nextInt(100) < plugin.getConfig().getInt("events2.triggers.threat-chance-percent", 30)) startThreatNear(killer);
        } else triggerKills.put(world, n);
    }

    private void rewardParticipants(LivingEntity boss, Player killer, List<ItemStack> drops) {
        Map<UUID, Double> map = damage.getOrDefault(boss.getUniqueId(), Collections.emptyMap());
        if (map.isEmpty() && killer != null) map = Collections.singletonMap(killer.getUniqueId(), 1.0);
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        UUID top = sorted.isEmpty() ? null : sorted.get(0).getKey();
        for (Map.Entry<UUID, Double> en : sorted) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.getWorld().equals(boss.getWorld())
                    || p.getLocation().distanceSquared(boss.getLocation()) > 80*80) continue;
            int rep = plugin.getConfig().getInt("events2.raid.rewards.participant-rep", 120);
            if (en.getKey().equals(top)) rep += plugin.getConfig().getInt("events2.raid.rewards.top-damage-bonus-rep", 80);
            if (killer != null && en.getKey().equals(killer.getUniqueId())) rep += plugin.getConfig().getInt("events2.raid.rewards.killer-bonus-rep", 60);
            giveRep(p, rep);
            safeGive(p, new ItemStack(Material.EMERALD, en.getKey().equals(top) ? 6 : 3));
            maybeRareReward(p, drops, en.getKey().equals(top) || (killer != null && en.getKey().equals(killer.getUniqueId())));
            p.sendMessage(ChatColor.GOLD + "🏆 Рейд-награда: +" + rep + " репутации ВК" + (en.getKey().equals(top) ? " §d(топ урона)" : ""));
        }
        announce("🏆 [РЕЙД] " + (killer != null ? killer.getName() : "Игроки") + " победили " + ChatColor.stripColor(boss.getCustomName() == null ? boss.getType().name() : boss.getCustomName()) + ". Участников: " + sorted.size());
    }

    private void maybeRareReward(Player p, List<ItemStack> drops, boolean bonus) {
        if (isRareDailyCapped(p)) return;
        int chance = bonus ? 45 : 25;
        if (ThreadLocalRandom.current().nextInt(100) >= chance) return;
        ItemStack reward;
        int r = ThreadLocalRandom.current().nextInt(4);
        if (r == 0) reward = MobListener.getRuneToken();
        else if (r == 1) reward = MobListener.getArtifactShard();
        else if (r == 2) reward = createForgeScroll(ThreadLocalRandom.current().nextBoolean() ? "chance_25" : "anti_defect");
        else reward = VKChatMobsPlugin.createSetFragment(plugin);
        if (reward != null) { safeGive(p, reward); incrementRareDaily(p); p.sendMessage(ChatColor.LIGHT_PURPLE + "✨ Редкая рейд-награда: " + reward.getItemMeta().getDisplayName()); }
    }

    private boolean isRareDailyCapped(Player p) {
        String key = "events2_rare_" + new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        NamespacedKey nk = new NamespacedKey(plugin, key);
        int got = p.getPersistentDataContainer().getOrDefault(nk, PersistentDataType.INTEGER, 0);
        return got >= plugin.getConfig().getInt("events2.anti-farm.daily-rare-cap", 5);
    }
    private void incrementRareDaily(Player p) {
        String key = "events2_rare_" + new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        NamespacedKey nk = new NamespacedKey(plugin, key);
        int got = p.getPersistentDataContainer().getOrDefault(nk, PersistentDataType.INTEGER, 0);
        p.getPersistentDataContainer().set(nk, PersistentDataType.INTEGER, got + 1);
    }

    private ItemStack createForgeScroll(String type) {
        org.bukkit.plugin.Plugin gear = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gear == null) return new ItemStack(Material.DIAMOND, 2);
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if ("anti_defect".equals(type)) { meta.setDisplayName("§aСвиток Чистой Стали"); meta.setCustomModelData(57); meta.setLore(Arrays.asList("§7Защищает от дефекта при следующей перековке.", "§8Трофей рейда.")); }
        else { meta.setDisplayName("§dСвиток Поддува Горна"); meta.setCustomModelData(55); meta.setLore(Arrays.asList("§7+25% к следующему слиянию редкости.", "§8Трофей рейда.")); }
        meta.getPersistentDataContainer().set(new NamespacedKey(gear, "forge_scroll_type"), PersistentDataType.STRING, type);
        it.setItemMeta(meta);
        return it;
    }

    private void giveRep(Player p, int rep) { VKChatBridge.addEffectiveRep(p, rep); }
    private void safeGive(Player p, ItemStack item) { if (item == null) return; p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left)); }

    private void announce(String msg) {
        if (plugin.getConfig().getBoolean("events2.announcements.chat", true)) Bukkit.broadcastMessage(ChatColor.GOLD + msg);
    }
}
