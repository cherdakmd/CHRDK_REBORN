package ru.example.vkchatteleport.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DonateTierHelper — конфиг-управляемые скидки/кулдауны/лимиты по донат-статусам.
 *
 * Извлечено из TeleportCommand (5 дублирующихся методов).
 * Теперь все донат-уровни настраиваются из config.yml.
 */
public class DonateTierHelper {

    private final JavaPlugin plugin;

    // Порядок: от высшего к низшему
    private static final String[] TIER_PERMS = {
            "vkchat.donate.overlord",
            "vkchat.donate.legend",
            "vkchat.donate.star",
            "vkchat.donate.flame",
            "vkchat.donate.spark"
    };

    public DonateTierHelper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Получить индекс тира (0=overlord, 1=legend, 2=star, 3=flame, 4=spark, -1=нет тира)
     */
    public int getTierIndex(Player p) {
        for (int i = 0; i < TIER_PERMS.length; i++) {
            if (p.hasPermission(TIER_PERMS[i])) return i;
        }
        return -1;
    }

    /** Применить скидку доната к стоимости */
    public int applyDiscount(Player p, int cost) {
        if (cost <= 0) return 0;
        int tier = getTierIndex(p);
        if (tier < 0) return cost;

        double[] defaults = {0.35, 0.50, 0.65, 0.80, 0.90};
        double multiplier = plugin.getConfig().getDouble(
                "teleportation.donate-tiers." + TIER_PERMS[tier].replace("vkchat.donate.", "") + ".cost-multiplier",
                defaults[tier]);
        return Math.max(1, (int) (cost * multiplier));
    }

    /** Получить кулдаун для типа телепорта с учётом донат-скидки */
    public int getCooldown(Player p, String type) {
        int base = plugin.getConfig().getInt("teleportation." + type + ".cooldown", 60);
        int tier = getTierIndex(p);
        if (tier < 0) return base;

        double[] defaults = {0.15, 0.30, 0.50, 0.65, 0.80};
        double multiplier = plugin.getConfig().getDouble(
                "teleportation.donate-tiers." + TIER_PERMS[tier].replace("vkchat.donate.", "") + ".cooldown-multiplier",
                defaults[tier]);
        return Math.max(1, (int) (base * multiplier));
    }

    /** Получить максимальное количество домов */
    public int getMaxHomes(Player p) {
        int tier = getTierIndex(p);
        if (tier < 0) return plugin.getConfig().getInt("teleportation.home.max-homes", 3);

        int[] defaults = {20, 16, 12, 8, 5};
        return plugin.getConfig().getInt(
                "teleportation.donate-tiers." + TIER_PERMS[tier].replace("vkchat.donate.", "") + ".max-homes",
                defaults[tier]);
    }

    /** Есть ли у игрока донат-статус? */
    public boolean hasDonateStatus(Player p) {
        return getTierIndex(p) >= 0;
    }

    /** Процент скидки (0.0 — нет скидки, 0.65 — 65% скидка) */
    public double getDiscountPercent(Player p) {
        int tier = getTierIndex(p);
        if (tier < 0) return 0;

        double[] defaults = {0.65, 0.50, 0.35, 0.20, 0.10};
        return plugin.getConfig().getDouble(
                "teleportation.donate-tiers." + TIER_PERMS[tier].replace("vkchat.donate.", "") + ".discount-percent",
                defaults[tier]);
    }

    /** Форматировать время в человекочитаемый вид */
    public static String formatTime(long seconds) {
        if (seconds < 60) return seconds + " сек";
        if (seconds < 3600) return (seconds / 60) + " мин " + (seconds % 60) + " сек";
        return (seconds / 3600) + " ч " + ((seconds % 3600) / 60) + " мин";
    }
}
