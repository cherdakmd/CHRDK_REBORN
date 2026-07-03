package ru.example.vkchatmobs.managers;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmobs.util.VKChatBridge;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.listeners.MobListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Новая надстройка над vkchat_mobs: хардкорные элитки, архетипы, телеграфы,
 * смешанные награды, ВК-охота и админ-инструменты. Старый MobListener остаётся
 * как базовый слой совместимости, этот менеджер добавляет новый дизайн.
 */
public class HardcoreMobManager implements Listener {
    private final VKChatMobsPlugin plugin;

    private final NamespacedKey eliteKey;
    private final NamespacedKey archetypeKey;
    private final NamespacedKey elementKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey spawnerKey;
    private final NamespacedKey lastAbilityKey;

    private final Map<UUID, Long> rewardCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> vkMessageCooldowns = new ConcurrentHashMap<>();
    private static final long VK_MSG_COOLDOWN_MS = 5000L; // 5 секунд между VK-сообщениями

    private static final String[] ARCHETYPES = {"tank", "assassin", "archer", "shaman", "necromancer", "hunter", "warlord", "berserker", "paladin", "ranger", "alchemist", "summoner", "guardian"};
    private static final String[] ELEMENTS = {"fire", "frost", "poison", "storm", "dark", "light", "void", "nature", "ice", "blood", "wind", "earth", "crystal"};

    public HardcoreMobManager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.eliteKey = new NamespacedKey(plugin, "hardcore_elite");
        this.archetypeKey = new NamespacedKey(plugin, "hardcore_archetype");
        this.elementKey = new NamespacedKey(plugin, "hardcore_element");
        this.tierKey = new NamespacedKey(plugin, "hardcore_tier");
        this.spawnerKey = new NamespacedKey(plugin, "from_spawner");
        this.lastAbilityKey = new NamespacedKey(plugin, "last_hardcore_ability");
        startAbilityTicker();
    }

    public int getActiveEliteCount() {
        int count = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Monster m : w.getEntitiesByClass(Monster.class)) {
                if (m.getPersistentDataContainer().has(eliteKey, PersistentDataType.INTEGER)) count++;
            }
        }
        return count;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.isCancelled() || !plugin.getConfig().getBoolean("hardcore-mobs.enabled", true)) return;
        if (!(e.getEntity() instanceof Monster)) return;
        LivingEntity mob = e.getEntity();

        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            mob.getPersistentDataContainer().set(spawnerKey, PersistentDataType.INTEGER, 1);
            return;
        }

        int playerCount = mob.getWorld().getPlayers().size();
        int maxPerWorld = plugin.getConfig().getInt("hardcore-mobs.anti-farm.max-elites-per-world", 35);
        int scaledMax = maxPerWorld + (playerCount * 2);
        if (countElites(mob.getWorld()) >= scaledMax) return;

        double baseChance = plugin.getConfig().getDouble("hardcore-mobs.elites.natural-spawn-chance", 2.0);
        double chance = baseChance + (playerCount * plugin.getConfig().getDouble("hardcore-mobs.elites.player-bonus", 0.5));
        if (isNightOrCave(mob.getLocation())) chance += plugin.getConfig().getDouble("hardcore-mobs.elites.night-cave-bonus", 3.0);
        if (isBloodMoonLike()) chance += plugin.getConfig().getDouble("hardcore-mobs.elites.event-bonus", 8.0);

        if (ThreadLocalRandom.current().nextDouble() * 100.0 <= chance) {
            makeElite(mob, "elite", null, null, false);
        }
    }

    public LivingEntity spawnCustom(Location loc, String tier, String archetype, String element) {
        EntityType type = tier.equalsIgnoreCase("world") ? EntityType.WITHER_SKELETON : EntityType.ZOMBIE;
        if (tier.equalsIgnoreCase("raid")) type = EntityType.ILLUSIONER;
        if (tier.equalsIgnoreCase("mini")) type = EntityType.VINDICATOR;
        Entity e = loc.getWorld().spawnEntity(loc, type);
        if (e instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) e;
            makeElite(le, tier, archetype, element, true);
            return le;
        }
        return null;
    }

    public void makeElite(LivingEntity mob, String tier, String forcedArchetype, String forcedElement, boolean adminOrEvent) {
        String archetype = forcedArchetype != null ? forcedArchetype : ARCHETYPES[ThreadLocalRandom.current().nextInt(ARCHETYPES.length)];
        String element = forcedElement != null ? forcedElement : ELEMENTS[ThreadLocalRandom.current().nextInt(ELEMENTS.length)];
        tier = normalizeTier(tier);

        mob.getPersistentDataContainer().set(eliteKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(archetypeKey, PersistentDataType.STRING, archetype);
        mob.getPersistentDataContainer().set(elementKey, PersistentDataType.STRING, element);
        mob.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        mob.setGlowing(true);
        mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "mobs_scaled"), PersistentDataType.INTEGER, 1);

        double scale = calculateScale(mob, tier);
        AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            double base = tier.equals("world") ? 900 : tier.equals("raid") ? 450 : tier.equals("mini") ? 180 : 70;
            hp.setBaseValue(base * scale);
            mob.setHealth(hp.getBaseValue());
        }
        AttributeInstance dmg = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) {
            double base = dmg.getBaseValue();
            if (tier.equals("world")) base = Math.max(base, 14);
            else if (tier.equals("raid")) base = Math.max(base, 10);
            else if (tier.equals("mini")) base = Math.max(base, 7);
            dmg.setBaseValue(base * Math.min(3.5, scale));
        }
        if (archetype.equals("tank")) mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
        if (archetype.equals("assassin")) mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
        if (archetype.equals("shaman")) mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, true, false));
        if (archetype.equals("paladin")) mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
        if (archetype.equals("ranger")) mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));

        mob.setCustomName(formatName(tier, archetype, element, mob));
        mob.setCustomNameVisible(plugin.getConfig().getBoolean("hardcore-mobs.elites.show-name", true));

        if (tier.equals("raid") || tier.equals("world")) {
            String msg = ChatColor.DARK_RED + "☠ [ОХОТА] " + ChatColor.GOLD + strip(formatName(tier, archetype, element, mob)) + ChatColor.RED + " появился: X " + mob.getLocation().getBlockX() + " Z " + mob.getLocation().getBlockZ();
            Bukkit.broadcastMessage(msg);
        }
    }

    private void startAbilityTicker() {
        new BukkitRunnable() {
            @Override public void run() {
                if (!plugin.getConfig().getBoolean("hardcore-mobs.abilities.enabled", true)) return;
                long now = System.currentTimeMillis();
                for (World world : Bukkit.getWorlds()) {
                    for (Monster mob : world.getEntitiesByClass(Monster.class)) {
                        if (!mob.getPersistentDataContainer().has(eliteKey, PersistentDataType.INTEGER)) continue;
                        long last = mob.getPersistentDataContainer().getOrDefault(lastAbilityKey, PersistentDataType.LONG, 0L);
                        long cd = plugin.getConfig().getLong("hardcore-mobs.abilities.cooldown-ms", 8000L);
                        String tier = mob.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.STRING, "elite");
                        if (tier.equals("raid")) cd = 6000L;
                        if (tier.equals("world")) cd = 4500L;
                        if (now - last < cd) continue;
                        Player target = nearestPlayer(mob, 14);
                        if (target == null) continue;
                        mob.getPersistentDataContainer().set(lastAbilityKey, PersistentDataType.LONG, now);
                        telegraphAndCast(mob, target);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    private void telegraphAndCast(LivingEntity mob, Player target) {
        String element = mob.getPersistentDataContainer().getOrDefault(elementKey, PersistentDataType.STRING, "fire");
        String archetype = mob.getPersistentDataContainer().getOrDefault(archetypeKey, PersistentDataType.STRING, "tank");
        Location center = target.getLocation().clone();
        World w = center.getWorld();
        w.playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 0.6f);
        Particle particle = particleFor(element);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24.0;
            w.spawnParticle(particle, center.clone().add(Math.cos(a) * 2.2, 0.15, Math.sin(a) * 2.2), 1, 0, 0, 0, 0);
        }
        target.sendMessage(ChatColor.RED + "⚠ " + ChatColor.stripColor(mob.getCustomName()) + " готовит " + abilityName(archetype, element) + "! Уходи из зоны!");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isValid() || mob.isDead()) return;
            w.spawnParticle(particle, center.clone().add(0, 0.2, 0), 60, 2.0, 0.3, 2.0, 0.08);
            w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= 9.0) applyAbility(mob, p, archetype, element);
            }
        }, plugin.getConfig().getLong("hardcore-mobs.abilities.telegraph-ticks", 30L));
    }

    private void applyAbility(LivingEntity mob, Player p, String archetype, String element) {
        double dmg = plugin.getConfig().getDouble("hardcore-mobs.abilities.zone-damage", 5.0);
        if (archetype.equals("assassin")) dmg += 3;
        if (archetype.equals("hunter")) dmg += 2;
        if (archetype.equals("tank")) p.setVelocity(p.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(1.0).setY(0.35));
        if (archetype.equals("necromancer")) spawnMinion(mob);
        if (archetype.equals("shaman")) {
            org.bukkit.attribute.AttributeInstance hpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hpAttr != null) mob.setHealth(Math.min(hpAttr.getValue(), mob.getHealth() + 8));
        }
        if (archetype.equals("hunter")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            p.getWorld().spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 0.1, 0), 30, 1.0, 0.1, 1.0, 0.02);
        }
        if (archetype.equals("warlord")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
            dmg += 2;
        }
        if (archetype.equals("berserker")) {
            org.bukkit.attribute.AttributeInstance hpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hpAttr != null) {
                double healthPercent = mob.getHealth() / hpAttr.getValue();
                double bonus = (1.0 - healthPercent) * 10;
                dmg += bonus;
            }
            p.getWorld().spawnParticle(Particle.REDSTONE, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.02, new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
        }
        if (archetype.equals("paladin")) {
            for (LivingEntity near : mob.getWorld().getNearbyEntities(mob.getLocation(), 10, 10, 10, e -> e instanceof Monster).stream().map(e -> (LivingEntity) e).collect(java.util.stream.Collectors.toList())) {
                if (!near.equals(mob)) {
                    org.bukkit.attribute.AttributeInstance nearHp = near.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (nearHp != null) near.setHealth(Math.min(nearHp.getValue(), near.getHealth() + 5));
                }
            }
            p.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 2, 0), 6, 1.0, 0.5, 1.0, 0);
        }
        if (archetype.equals("ranger")) {
            Vector dir = p.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize();
            for (int i = -1; i <= 1; i++) {
                Vector spread = dir.clone().rotateAroundY(Math.toRadians(i * 15));
                org.bukkit.entity.Arrow arrow = mob.getWorld().spawn(mob.getLocation().add(0, 1.5, 0), org.bukkit.entity.Arrow.class);
                arrow.setVelocity(spread.multiply(1.5).setY(0.3));
                arrow.setDamage(dmg * 0.5);
                arrow.setShooter(mob);
            }
        }
        if (archetype.equals("alchemist")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
            p.getWorld().spawnParticle(Particle.SPELL_WITCH, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            dmg += 2;
        }
        if (archetype.equals("summoner")) {
            for (int i = 0; i < 3; i++) {
                Zombie minion = mob.getWorld().spawn(mob.getLocation().add(ThreadLocalRandom.current().nextDouble() * 4 - 2, 0, ThreadLocalRandom.current().nextDouble() * 4 - 2), Zombie.class);
                minion.setCustomName("§cПризванный слуга");
                minion.setCustomNameVisible(true);
                minion.getPersistentDataContainer().set(eliteKey, PersistentDataType.INTEGER, 1);
            }
            p.getWorld().spawnParticle(Particle.SPELL_MOB, mob.getLocation().add(0, 1, 0), 30, 1.0, 0.5, 1.0, 0.1, org.bukkit.Color.fromRGB(128, 0, 128));
        }
        if (archetype.equals("guardian")) {
            for (LivingEntity near : mob.getWorld().getNearbyEntities(mob.getLocation(), 8, 8, 8, e -> e instanceof Monster).stream().map(e -> (LivingEntity) e).collect(java.util.stream.Collectors.toList())) {
                if (!near.equals(mob)) {
                    near.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 1));
                }
            }
            mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 2));
            p.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, mob.getLocation().add(0, 2, 0), 10, 1.0, 0.5, 1.0, 0);
        }
        p.damage(dmg, mob);
        switch (element) {
            case "fire": p.setFireTicks(100); break;
            case "frost": p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 2)); break;
            case "poison": p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1)); break;
            case "storm": p.getWorld().strikeLightningEffect(p.getLocation()); p.setVelocity(new Vector(0, 0.8, 0)); break;
            case "dark": p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0)); break;
            case "light":
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 40, 0));
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                if (mob.getType() == EntityType.ZOMBIE || mob.getType() == EntityType.SKELETON || mob.getType() == EntityType.WITHER_SKELETON) {
                    mob.damage(8.0, p);
                }
                break;
            case "void":
                p.teleport(p.getLocation().add(ThreadLocalRandom.current().nextDouble() * 6 - 3, 0, ThreadLocalRandom.current().nextDouble() * 6 - 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 1));
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
                break;
            case "nature":
                mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                p.getWorld().spawnParticle(Particle.BLOCK_CRACK, p.getLocation().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.02, org.bukkit.Material.OAK_LEAVES.createBlockData());
                break;
            case "ice":
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 3));
                p.getWorld().spawnParticle(Particle.SNOW_SHOVEL, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.02);
                break;
            case "blood":
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                p.getWorld().spawnParticle(Particle.BLOCK_DUST, p.getLocation().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.02, org.bukkit.Material.REDSTONE_BLOCK.createBlockData());
                break;
            case "wind":
                p.setVelocity(p.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(1.5).setY(0.4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.1);
                break;
            case "earth":
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0));
                p.getWorld().spawnParticle(Particle.BLOCK_DUST, p.getLocation().add(0, 0.5, 0), 40, 0.5, 0.5, 0.5, 0.02, org.bukkit.Material.DIRT.createBlockData());
                break;
            case "crystal":
                if (ThreadLocalRandom.current().nextInt(100) < 20) {
                    double reflect = p.getHealth() * 0.3;
                    p.damage(reflect);
                    p.sendMessage("§d✦ Кристаллический отражённый урон!");
                }
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (!mob.getPersistentDataContainer().has(eliteKey, PersistentDataType.INTEGER)) return;
        Player killer = mob.getKiller();
        if (killer == null) return;

        if (mob.getPersistentDataContainer().has(spawnerKey, PersistentDataType.INTEGER)) return;
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("hardcore-mobs.anti-farm.reward-cooldown-ms", 2500L);
        if (now - rewardCooldowns.getOrDefault(killer.getUniqueId(), 0L) < cd) return;
        rewardCooldowns.put(killer.getUniqueId(), now);

        String tier = mob.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.STRING, "elite");
        int rep = plugin.getConfig().getInt("hardcore-mobs.rewards.rep." + tier, 8);
        VKChatBridge.addPoints(VKChatBridge.getLinkedVkId(killer), rep);

        if (ThreadLocalRandom.current().nextInt(100) < plugin.getConfig().getInt("hardcore-mobs.rewards.rune_token_chance." + tier, 10)) {
            e.getDrops().add(MobListener.getRuneToken());
        }
        if ((tier.equals("raid") || tier.equals("world")) && ThreadLocalRandom.current().nextInt(100) < plugin.getConfig().getInt("hardcore-mobs.rewards.artifact-shard-chance." + tier, 20)) {
            e.getDrops().add(MobListener.getArtifactShard());
        }
        if (ThreadLocalRandom.current().nextInt(100) < plugin.getConfig().getInt("hardcore-mobs.rewards.set-fragment-chance." + tier, 5)) {
            ItemStack frag = VKChatMobsPlugin.createSetFragment(plugin);
            if (frag != null) e.getDrops().add(frag);
        }
        killer.sendMessage(ChatColor.GOLD + "🏹 Хардкорная охота: +" + rep + " репутации ВК за " + ChatColor.stripColor(mob.getCustomName()));

        if (tier.equals("raid") || tier.equals("world")) {
            String msg = "🏆 " + killer.getName() + " победил " + ChatColor.stripColor(mob.getCustomName()) + "!";
            Bukkit.broadcastMessage(ChatColor.GOLD + msg);
        }
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        String cmd = e.getCommand();
        if (!cmd.equals("!охота") && !cmd.equals("!мобы") && !cmd.equals("!контракт")) return;
        e.setCancelled(true);
        int peer = e.getPeerId();
        int sender = e.getSenderVkId();
        String text = "🏹 Охота VKChatMobs\n" +
                "Элитки: редкие мобы с архетипом и стихией.\n" +
                "Боссы: mini / raid / world.\n" +
                "Награды: репутация ВК, жетоны рун, осколки артефактов, фрагменты сетов.\n" +
                "Антифарм: спавнеры не дают редкий лут, есть лимиты и кулдауны.\n" +
                "Активных элиток сейчас: " + getActiveEliteCount();
        VKChatBridge.sendMessage(peer, sender > 0 && peer >= 2000000000 ? "@id" + sender + ", " + text : text);
    }

    private void spawnMinion(LivingEntity owner) {
        if (owner.getWorld().getNearbyEntities(owner.getLocation(), 8, 8, 8, e -> e instanceof Monster).size() > 12) return;
        Entity e = owner.getWorld().spawnEntity(owner.getLocation().clone().add(ThreadLocalRandom.current().nextDouble() * 2 - 1, 0, ThreadLocalRandom.current().nextDouble() * 2 - 1), EntityType.SILVERFISH);
        if (e instanceof LivingEntity) {
            ((LivingEntity) e).setCustomName(ChatColor.DARK_PURPLE + "Призванная тень");
        }
    }

    private int countElites(World world) {
        int count = 0;
        for (Monster m : world.getEntitiesByClass(Monster.class)) if (m.getPersistentDataContainer().has(eliteKey, PersistentDataType.INTEGER)) count++;
        return count;
    }

    private Player nearestPlayer(LivingEntity mob, double radius) {
        Player best = null; double bestD = radius * radius;
        for (Player p : mob.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private double calculateScale(LivingEntity mob, String tier) {
        double scale = 1.0;
        int nearby = 0;
        int progress = 0;
        for (Player p : mob.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(mob.getLocation()) <= 32 * 32) {
                nearby++;
                progress += approximateProgress(p);
            }
        }
        scale += Math.max(0, nearby - 1) * plugin.getConfig().getDouble("hardcore-mobs.scaling.per-nearby-player", 0.20);
        scale += Math.min(2.0, progress * plugin.getConfig().getDouble("hardcore-mobs.scaling.per-progress-point", 0.004));
        if (isNightOrCave(mob.getLocation())) scale += plugin.getConfig().getDouble("hardcore-mobs.scaling.night-cave", 0.25);
        if (mob.getWorld().getEnvironment() == World.Environment.NETHER) scale += 0.35;
        if (tier.equals("mini")) scale += 0.8;
        if (tier.equals("raid")) scale += 1.8;
        if (tier.equals("world")) scale += 3.0;
        return Math.min(plugin.getConfig().getDouble("hardcore-mobs.scaling.max", 5.0), scale);
    }

    private int approximateProgress(Player p) {
        int score = 0;
        int vk = VKChatBridge.getLinkedVkId(p);
        if (vk != -1) score += VKChatBridge.getReputation(vk) / 500;
        for (ItemStack item : p.getInventory().getArmorContents()) score += getUpgrade(item);
        score += getUpgrade(p.getInventory().getItemInMainHand());
        try {
            org.bukkit.plugin.Plugin jobs = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobs != null && jobs.isEnabled()) {
                Object dm = jobs.getClass().getMethod("getJobsDataManager").invoke(jobs);
                for (String j : Arrays.asList("miner", "woodcutter", "farmer", "alchemist", "blacksmith")) {
                    score += (int) dm.getClass().getMethod("getLevel", UUID.class, String.class).invoke(dm, p.getUniqueId(), j);
                }
            }
        } catch (Throwable ignored) {}
        return score;
    }

    private int getUpgrade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        org.bukkit.plugin.Plugin gear = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gear == null) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(gear, "upgrade_level"), PersistentDataType.INTEGER, 0);
    }

    private boolean isNightOrCave(Location loc) {
        long time = loc.getWorld().getTime();
        return time > 13000 || loc.getBlockY() < 45 || loc.getBlock().getLightLevel() <= 7;
    }

    private boolean isBloodMoonLike() {
        try { return VKChatBridge.isBloodMoonActive(); } catch (Throwable ignored) { return false; }
    }

    private String normalizeTier(String tier) {
        if (tier == null) return "elite";
        tier = tier.toLowerCase(Locale.ROOT);
        if (tier.equals("mini") || tier.equals("raid") || tier.equals("world")) return tier;
        return "elite";
    }

    private String strip(String s) { return ChatColor.stripColor(s == null ? "" : s); }
    private Particle particleFor(String e) { if (e.equals("fire")) return Particle.FLAME; if (e.equals("frost")) return Particle.SNOWBALL; if (e.equals("poison")) return Particle.SPELL_WITCH; if (e.equals("storm")) return Particle.CRIT_MAGIC; if (e.equals("light")) return Particle.END_ROD; if (e.equals("ice")) return Particle.SNOW_SHOVEL; if (e.equals("blood")) return Particle.SPELL_WITCH; return Particle.SMOKE_NORMAL; }
    private String abilityName(String a, String e) { return archetypeName(a) + " / " + elementName(e); }
    private String formatName(String tier, String a, String e, LivingEntity mob) { return ChatColor.translateAlternateColorCodes('&', tierColor(tier) + "[" + tier.toUpperCase() + "] &f" + archetypeName(a) + " " + elementName(e)); }
    private String tierColor(String t) { if (t.equals("world")) return "&5&l"; if (t.equals("raid")) return "&4&l"; if (t.equals("mini")) return "&c&l"; return "&6"; }
    private String archetypeName(String a) { switch (a) { case "tank": return "Танк"; case "assassin": return "Ассасин"; case "archer": return "Стрелок"; case "shaman": return "Шаман"; case "necromancer": return "Некромант"; case "hunter": return "Охотник"; case "warlord": return "Полководец"; case "berserker": return "Берсерк"; case "paladin": return "Паладин"; case "ranger": return "Рейнджер"; case "alchemist": return "Алхимик"; case "summoner": return "Призыватель"; case "guardian": return "Страж"; default: return "Элита"; } }
    private String elementName(String e) { switch (e) { case "fire": return "Огня"; case "frost": return "Льда"; case "poison": return "Яда"; case "storm": return "Бури"; case "dark": return "Тьмы"; case "light": return "Света"; case "void": return "Бездны"; case "nature": return "Природы"; case "ice": return "Льда"; case "blood": return "Крови"; case "wind": return "Ветра"; case "earth": return "Земли"; case "crystal": return "Кристалла"; default: return "Хаоса"; } }
}
