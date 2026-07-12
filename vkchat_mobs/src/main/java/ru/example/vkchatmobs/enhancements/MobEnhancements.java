package ru.example.vkchatmobs.enhancements;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatmobs.VKChatMobsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MobEnhancements {
    private final VKChatMobsPlugin plugin;

    private final Map<UUID, long[]> killStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> contractStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> encyclopedia = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    private static final long STREAK_WINDOW_MS = 60_000;
    private static final String[] STREAK_NAMES = {
        "", "Рубака", "Мясник", "Уничтожитель", "Ката", "ГЕНОЦИД"
    };

    public MobEnhancements(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public int getLootingBonus(Player killer) {
        if (!plugin.getConfig().getBoolean("enhancements.looting.enabled", true)) return 0;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return 0;
        int looting = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS);
        return looting * plugin.getConfig().getInt("enhancements.looting.bonus-per-level", 1);
    }

    public double getLootingRepMultiplier(Player killer) {
        if (!plugin.getConfig().getBoolean("enhancements.looting.enabled", true)) return 1.0;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return 1.0;
        int looting = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS);
        return 1.0 + (looting * plugin.getConfig().getDouble("enhancements.looting.rep-multiplier-per-level", 0.05));
    }

    public void playAbilitySound(LivingEntity mob, String ability) {
        if (!plugin.getConfig().getBoolean("enhancements.ability-sounds", true)) return;
        World w = mob.getWorld();
        Location loc = mob.getLocation();
        switch (ability) {
            case "fire_strike":
                w.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);
                break;
            case "web_weaver":
                w.playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.5f);
                break;
            case "poison_explosion":
                w.playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 0.3f);
                break;
            case "gravitational_push":
                w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
                break;
            case "minion_summon":
                w.playSound(loc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);
                break;
            default:
                w.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.2f);
        }
    }

    public void playDeathAnimation(LivingEntity mob) {
        if (!plugin.getConfig().getBoolean("enhancements.death-animation", true)) return;
        Location loc = mob.getLocation();
        World w = mob.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.EXPLOSION_LARGE, loc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5);
        w.spawnParticle(Particle.SMOKE_LARGE, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        w.spawnParticle(Particle.FIREWORKS_SPARK, loc.clone().add(0, 1, 0), 30, 0.8, 0.8, 0.8, 0.1);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
        w.playSound(loc, Sound.ENTITY_WITHER_DEATH, 0.3f, 2.0f);
        if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_mini_boss"), PersistentDataType.INTEGER)) {
            w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0, 1.5, 0), 50, 1, 1, 1, 0.02);
            w.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        }
    }

    public void recordKill(UUID playerId) {
        if (!plugin.getConfig().getBoolean("enhancements.kill-streak.enabled", true)) return;
        long now = System.currentTimeMillis();
        long[] data = killStreaks.getOrDefault(playerId, new long[]{0, 0});
        if (now - data[1] > STREAK_WINDOW_MS) {
            data[0] = 0;
        }
        data[0]++;
        data[1] = now;
        killStreaks.put(playerId, data);
    }

    public int getKillStreak(UUID playerId) {
        long[] data = killStreaks.get(playerId);
        if (data == null) return 0;
        if (System.currentTimeMillis() - data[1] > STREAK_WINDOW_MS) return 0;
        return (int) data[0];
    }

    public double getStreakBonus(UUID playerId) {
        int streak = getKillStreak(playerId);
        double bonusPerKill = plugin.getConfig().getDouble("enhancements.kill-streak.bonus-per-kill", 0.02);
        double maxBonus = plugin.getConfig().getDouble("enhancements.kill-streak.max-bonus", 0.50);
        return Math.min(maxBonus, streak * bonusPerKill);
    }

    public String getStreakName(UUID playerId) {
        int streak = getKillStreak(playerId);
        int idx = Math.min(streak / 5, STREAK_NAMES.length - 1);
        return idx > 0 ? STREAK_NAMES[idx] : null;
    }

    public void notifyEliteSpawn(LivingEntity mob, String archetype, String element) {
        if (!plugin.getConfig().getBoolean("enhancements.elite-spawn-notify", true)) return;
        int radius = plugin.getConfig().getInt("enhancements.elite-notify-radius", 100);
        String mobName = mob.getCustomName() != null ? mob.getCustomName() : mob.getType().name();
        String msg = "§5⚔ Элитный моб заспавнился рядом: §f" + mobName;
        if (archetype != null) msg += " §7(" + archetype + ")";
        if (element != null) msg += " §7[" + element + "]";
        msg += " §7X:" + mob.getLocation().getBlockX() + " Z:" + mob.getLocation().getBlockZ();
        for (Player p : mob.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(mob.getLocation()) <= radius * radius) {
                p.sendMessage(msg);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.5f);
            }
        }
    }

    public void createBossBar(LivingEntity boss, String name) {
        if (!plugin.getConfig().getBoolean("enhancements.boss-health-bar", true)) return;
        double maxHp = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                ? boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : 100;
        BossBar bar = Bukkit.createBossBar(
                ChatColor.RED + name,
                BarColor.RED,
                BarStyle.SOLID
        );
        bar.setProgress(boss.getHealth() / maxHp);
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) <= 64 * 64) {
                bar.addPlayer(p);
            }
        }
        bossBars.put(boss.getUniqueId(), bar);
    }

    public void updateBossBar(LivingEntity boss) {
        BossBar bar = bossBars.get(boss.getUniqueId());
        if (bar == null || !boss.isValid() || boss.isDead()) {
            if (bar != null) { bar.removeAll(); bossBars.remove(boss.getUniqueId()); }
            return;
        }
        double maxHp = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                ? boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : 100;
        bar.setProgress(Math.max(0, boss.getHealth() / maxHp));
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) <= 64 * 64 && !bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
        bar.getPlayers().removeIf(p -> p.getLocation().distanceSquared(boss.getLocation()) > 80 * 80);
    }

    public void removeBossBar(UUID bossId) {
        BossBar bar = bossBars.remove(bossId);
        if (bar != null) bar.removeAll();
    }

    public void announceSiegeWave(String siegeKey, int wave, int maxWaves, int monsterCount) {
        if (!plugin.getConfig().getBoolean("enhancements.siege-wave-announce", true)) return;
        String[] waveNames = {"", "⚔ Первая волна!", "🔥 Вторая волна!", "💀 Финальная волна!"};
        String name = wave <= waveNames.length - 1 ? waveNames[wave] : "⚔ Волна " + wave;
        String msg = "§4§lОСАДА §8▸ " + name + " §7(" + wave + "/" + maxWaves + ") §cМонстров: §f" + monsterCount;
        Bukkit.broadcastMessage(msg);
    }

    public void recordContractComplete(UUID playerId) {
        if (!plugin.getConfig().getBoolean("enhancements.contract-streak.enabled", true)) return;
        contractStreaks.merge(playerId, 1, Integer::sum);
    }

    public int getContractStreak(UUID playerId) {
        return contractStreaks.getOrDefault(playerId, 0);
    }

    public double getContractStreakBonus(UUID playerId) {
        int streak = getContractStreak(playerId);
        double bonusPerStreak = plugin.getConfig().getDouble("enhancements.contract-streak.bonus-per-streak", 0.05);
        double maxBonus = plugin.getConfig().getDouble("enhancements.contract-streak.max-bonus", 0.50);
        return Math.min(maxBonus, streak * bonusPerStreak);
    }

    public void resetContractStreak(UUID playerId) {
        contractStreaks.put(playerId, 0);
    }

    public void recordMobKill(UUID playerId, LivingEntity mob) {
        if (!plugin.getConfig().getBoolean("enhancements.encyclopedia.enabled", false)) return;
        encyclopedia.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(mob.getType().name());
    }

    public Set<String> getEncyclopedia(UUID playerId) {
        return encyclopedia.getOrDefault(playerId, Collections.emptySet());
    }

    public int getEncyclopediaCount(UUID playerId) {
        return getEncyclopedia(playerId).size();
    }

    public void spawnDamageIndicator(LivingEntity mob, double damage) {
        if (!plugin.getConfig().getBoolean("enhancements.damage-indicators", false)) return;
        if (damage < 5) return;
        Location loc = mob.getLocation().add(0, mob.getHeight() + 0.5, 0);
        World w = mob.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.CRIT, loc, 5, 0.3, 0.3, 0.3, 0.1);
    }

    public void announceRareDrop(Player killer, ItemStack drop) {
        if (!plugin.getConfig().getBoolean("enhancements.rare-drop-announce", true)) return;
        String name = drop.hasItemMeta() && drop.getItemMeta().hasDisplayName()
                ? drop.getItemMeta().getDisplayName() : drop.getType().name();
        String msg = "§d§l★ РЕДКИЙ ДРОП! §f" + killer.getName() + " §7получил §f" + name + " §7x" + drop.getAmount();
        Bukkit.broadcastMessage(msg);
    }

    public void showContractProgress(Player p, int current, int required, String contractName) {
        if (!plugin.getConfig().getBoolean("enhancements.contract-progress-bar", true)) return;
        int bars = 20;
        int filled = (int) Math.round((double) current / required * bars);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? "§a|" : "§8|");
        }
        bar.append("§7]");
        p.sendMessage("§e" + contractName + " " + bar + " §f" + current + "/" + required);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        killStreaks.entrySet().removeIf(e -> now - e.getValue()[1] > STREAK_WINDOW_MS * 2);
    }
}
