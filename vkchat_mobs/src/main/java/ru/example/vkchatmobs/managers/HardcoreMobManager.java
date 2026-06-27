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
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.listeners.MobListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Новая надстройка над vkchat_mobs: хардкорные элитки, архетипы, телеграфы,
 * смешанные награды, ВК-охота и админ-инструменты. Старый MobListener остаётся
 * как базовый слой совместимости, этот менеджер добавляет новый дизайн.
 */
public class HardcoreMobManager implements Listener {
    private final VKChatMobsPlugin plugin;
    private final Random random = new Random();

    private final NamespacedKey eliteKey;
    private final NamespacedKey archetypeKey;
    private final NamespacedKey elementKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey spawnerKey;
    private final NamespacedKey lastAbilityKey;

    private final Map<UUID, Long> rewardCooldowns = new ConcurrentHashMap<>();

    private static final String[] ARCHETYPES = {"tank", "assassin", "archer", "shaman", "necromancer"};
    private static final String[] ELEMENTS = {"fire", "frost", "poison", "storm", "dark"};

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

        int maxPerWorld = plugin.getConfig().getInt("hardcore-mobs.anti-farm.max-elites-per-world", 35);
        if (countElites(mob.getWorld()) >= maxPerWorld) return;

        double chance = plugin.getConfig().getDouble("hardcore-mobs.elites.natural-spawn-chance", 2.0);
        if (isNightOrCave(mob.getLocation())) chance += plugin.getConfig().getDouble("hardcore-mobs.elites.night-cave-bonus", 3.0);
        if (isBloodMoonLike()) chance += plugin.getConfig().getDouble("hardcore-mobs.elites.event-bonus", 8.0);

        if (random.nextDouble() * 100.0 <= chance) {
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
        String archetype = forcedArchetype != null ? forcedArchetype : ARCHETYPES[random.nextInt(ARCHETYPES.length)];
        String element = forcedElement != null ? forcedElement : ELEMENTS[random.nextInt(ELEMENTS.length)];
        tier = normalizeTier(tier);

        mob.getPersistentDataContainer().set(eliteKey, PersistentDataType.INTEGER, 1);
        mob.getPersistentDataContainer().set(archetypeKey, PersistentDataType.STRING, archetype);
        mob.getPersistentDataContainer().set(elementKey, PersistentDataType.STRING, element);
        mob.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        mob.setGlowing(true);

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

        mob.setCustomName(formatName(tier, archetype, element, mob));
        mob.setCustomNameVisible(plugin.getConfig().getBoolean("hardcore-mobs.elites.show-name", true));

        if (tier.equals("raid") || tier.equals("world")) {
            String msg = ChatColor.DARK_RED + "☠ [ОХОТА] " + ChatColor.GOLD + strip(formatName(tier, archetype, element, mob)) + ChatColor.RED + " появился: X " + mob.getLocation().getBlockX() + " Z " + mob.getLocation().getBlockZ();
            Bukkit.broadcastMessage(msg);
            try { VKChatPlugin.getInstance().getApi().sendToMainChat(ChatColor.stripColor(msg)); } catch (Throwable ignored) {}
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
        if (archetype.equals("tank")) p.setVelocity(p.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(1.0).setY(0.35));
        if (archetype.equals("necromancer")) spawnMinion(mob);
        if (archetype.equals("shaman")) mob.setHealth(Math.min(mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), mob.getHealth() + 8));
        p.damage(dmg, mob);
        switch (element) {
            case "fire": p.setFireTicks(100); break;
            case "frost": p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 2)); break;
            case "poison": p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1)); break;
            case "storm": p.getWorld().strikeLightningEffect(p.getLocation()); p.setVelocity(new Vector(0, 0.8, 0)); break;
            case "dark": p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0)); p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0)); break;
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
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(killer);
            if (vkId != -1) VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
        } catch (Throwable ignored) {}

        if (random.nextInt(100) < plugin.getConfig().getInt("hardcore-mobs.rewards.rune-token-chance." + tier, 10)) {
            e.getDrops().add(MobListener.getRuneToken());
        }
        if ((tier.equals("raid") || tier.equals("world")) && random.nextInt(100) < plugin.getConfig().getInt("hardcore-mobs.rewards.artifact-shard-chance." + tier, 20)) {
            e.getDrops().add(MobListener.getArtifactShard());
        }
        if (random.nextInt(100) < plugin.getConfig().getInt("hardcore-mobs.rewards.set-fragment-chance." + tier, 5)) {
            ItemStack frag = createSetFragment();
            if (frag != null) e.getDrops().add(frag);
        }
        killer.sendMessage(ChatColor.GOLD + "🏹 Хардкорная охота: +" + rep + " репутации ВК за " + ChatColor.stripColor(mob.getCustomName()));

        if (tier.equals("raid") || tier.equals("world")) {
            String msg = "🏆 " + killer.getName() + " победил " + ChatColor.stripColor(mob.getCustomName()) + "!";
            Bukkit.broadcastMessage(ChatColor.GOLD + msg);
            try { VKChatPlugin.getInstance().getApi().sendToMainChat(msg); } catch (Throwable ignored) {}
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
        VKChatPlugin.getInstance().getApi().sendMessage(peer, sender > 0 && peer >= 2000000000 ? "@id" + sender + ", " + text : text);
    }

    private ItemStack createSetFragment() {
        org.bukkit.plugin.Plugin gear = Bukkit.getPluginManager().getPlugin("VKChatGear");
        if (gear == null) return null;
        List<String> sets = new ArrayList<>(plugin.getConfig().getStringList("hardcore-mobs.rewards.set-fragments"));
        if (sets.isEmpty()) sets.addAll(Arrays.asList("bogatyr", "sokol", "volhv", "koshchey"));
        String set = sets.get(random.nextInt(sets.size()));
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String setName = plugin.getConfig().getString("hardcore-mobs.rewards.set-fragment-names." + set, set);
        meta.setDisplayName(ChatColor.GOLD + "Фрагмент сета: " + setName);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Трофей элитной охоты.", ChatColor.GRAY + "Используется при ковке брони в VKChatGear."));
        meta.getPersistentDataContainer().set(new NamespacedKey(gear, "set_fragment"), PersistentDataType.STRING, set);
        item.setItemMeta(meta);
        return item;
    }

    private void spawnMinion(LivingEntity owner) {
        if (owner.getWorld().getNearbyEntities(owner.getLocation(), 8, 8, 8, e -> e instanceof Monster).size() > 12) return;
        Entity e = owner.getWorld().spawnEntity(owner.getLocation().clone().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1), EntityType.SILVERFISH);
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
        try {
            int vk = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vk != -1) score += VKChatPlugin.getInstance().getApi().getReputation(vk) / 500;
        } catch (Throwable ignored) {}
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
        try { return VKChatPlugin.getInstance().getBloodMoonManager() != null && VKChatPlugin.getInstance().getBloodMoonManager().isActive(); } catch (Throwable ignored) { return false; }
    }

    private String normalizeTier(String tier) {
        if (tier == null) return "elite";
        tier = tier.toLowerCase(Locale.ROOT);
        if (tier.equals("mini") || tier.equals("raid") || tier.equals("world")) return tier;
        return "elite";
    }

    private String strip(String s) { return ChatColor.stripColor(s == null ? "" : s); }
    private Particle particleFor(String e) { if (e.equals("fire")) return Particle.FLAME; if (e.equals("frost")) return Particle.SNOWBALL; if (e.equals("poison")) return Particle.SPELL_WITCH; if (e.equals("storm")) return Particle.CRIT_MAGIC; return Particle.SMOKE_NORMAL; }
    private String abilityName(String a, String e) { return archetypeName(a) + " / " + elementName(e); }
    private String formatName(String tier, String a, String e, LivingEntity mob) { return ChatColor.translateAlternateColorCodes('&', tierColor(tier) + "[" + tier.toUpperCase() + "] &f" + archetypeName(a) + " " + elementName(e)); }
    private String tierColor(String t) { if (t.equals("world")) return "&5&l"; if (t.equals("raid")) return "&4&l"; if (t.equals("mini")) return "&c&l"; return "&6"; }
    private String archetypeName(String a) { switch (a) { case "tank": return "Танк"; case "assassin": return "Ассасин"; case "archer": return "Стрелок"; case "shaman": return "Шаман"; case "necromancer": return "Некромант"; default: return "Элита"; } }
    private String elementName(String e) { switch (e) { case "fire": return "Огня"; case "frost": return "Льда"; case "poison": return "Яда"; case "storm": return "Бури"; case "dark": return "Тьмы"; default: return "Хаоса"; } }
}
