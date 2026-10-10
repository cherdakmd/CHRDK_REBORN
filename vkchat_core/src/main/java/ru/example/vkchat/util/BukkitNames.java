package ru.example.vkchat.util;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/**
 * Поиск эффектов и зачарований по строке из конфигов.
 * <p>
 * Minecraft 26.x (Paper/Purpur API 26.3) переименовал часть констант
 * (например, SLOW -> SLOWNESS, DAMAGE_ALL -> SHARPNESS). Старые имена,
 * которые уже лежат в конфигах серверов, переводятся в новые здесь.
 */
public final class BukkitNames {

    private static final Map<String, String> POTION_LEGACY = Map.ofEntries(
            Map.entry("SLOW", "SLOWNESS"),
            Map.entry("FAST_DIGGING", "HASTE"),
            Map.entry("SLOW_DIGGING", "MINING_FATIGUE"),
            Map.entry("INCREASE_DAMAGE", "STRENGTH"),
            Map.entry("HEAL", "INSTANT_HEALTH"),
            Map.entry("HARM", "INSTANT_DAMAGE"),
            Map.entry("JUMP", "JUMP_BOOST"),
            Map.entry("CONFUSION", "NAUSEA"),
            Map.entry("DAMAGE_RESISTANCE", "RESISTANCE")
    );

    private static final Map<String, String> ENCHANT_LEGACY = Map.ofEntries(
            Map.entry("PROTECTION_ENVIRONMENTAL", "PROTECTION"),
            Map.entry("PROTECTION_FIRE", "FIRE_PROTECTION"),
            Map.entry("PROTECTION_FALL", "FEATHER_FALLING"),
            Map.entry("PROTECTION_EXPLOSIONS", "BLAST_PROTECTION"),
            Map.entry("PROTECTION_PROJECTILE", "PROJECTILE_PROTECTION"),
            Map.entry("OXYGEN", "RESPIRATION"),
            Map.entry("WATER_WORKER", "AQUA_AFFINITY"),
            Map.entry("DAMAGE_ALL", "SHARPNESS"),
            Map.entry("DAMAGE_UNDEAD", "SMITE"),
            Map.entry("DAMAGE_ARTHROPODS", "BANE_OF_ARTHROPODS"),
            Map.entry("LOOT_BONUS_MOBS", "LOOTING"),
            Map.entry("DIG_SPEED", "EFFICIENCY"),
            Map.entry("DURABILITY", "UNBREAKING"),
            Map.entry("LOOT_BONUS_BLOCKS", "FORTUNE"),
            Map.entry("ARROW_DAMAGE", "POWER"),
            Map.entry("ARROW_KNOCKBACK", "PUNCH"),
            Map.entry("ARROW_FIRE", "FLAME"),
            Map.entry("ARROW_INFINITE", "INFINITY")
    );

    private BukkitNames() {}

    /** Вернёт эффект или null, если имя не распознано. */
    @SuppressWarnings("deprecation")
    public static PotionEffectType potionEffect(String name) {
        if (name == null) return null;
        String key = normalize(name);
        return PotionEffectType.getByName(POTION_LEGACY.getOrDefault(key, key));
    }

    /** Вернёт зачарование или null, если имя не распознано. */
    @SuppressWarnings("deprecation")
    public static Enchantment enchantment(String name) {
        if (name == null) return null;
        String key = normalize(name);
        return Enchantment.getByName(ENCHANT_LEGACY.getOrDefault(key, key));
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
