package ru.example.vkchatmarket.conflicts;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  EnchantmentConflictManager v1.0
 *  ─────────────────────────────────────────────────────────────────────
 *  Универсальный менеджер конфликтов зачарований Minecraft.
 *  Поддерживает версии 1.16.5 — 1.21.x через reflection / Registry API.
 *
 *  • Определяет группы конфликтующих зачарований (vanilla rules).
 *  • Совместим со старым API: Enchantment.getByName(String).
 *  • Совместим с новым API: Registry.ENCHANTMENT.get(NamespacedKey).
 *  • Не зависит от других модулей (может жить в любом vkchat_* плагине).
 *
 *  Использование:
 *      ConflictResult r = EnchantmentConflictManager.validateEnchantedBook(book);
 *      if (!r.isValid()) { player.sendMessage(r.formatUserMessage()); return; }
 * ═══════════════════════════════════════════════════════════════════════════
 */
public final class EnchantmentConflictManager {

    private static final Logger LOG = Logger.getLogger("EnchantmentConflictManager");

    /** Карта: "группа конфликтов" → набор ключей зачарований. */
    private static final Map<String, Set<String>> CONFLICT_GROUPS = new LinkedHashMap<>();
    /** Карта: ключ зачарования → ID группы. */
    private static final Map<String, String> ENCHANT_TO_GROUP = new HashMap<>();

    static {
        registerGroup("damage_swords",
                "sharpness", "smite", "bane_of_arthropods", "damage_all",
                "sharpness_enchant", "smite_enchant", "bane_enchant");
        registerGroup("protection",
                "protection", "fire_protection", "blast_protection", "projectile_protection",
                "protection_enchant", "fire_protection_enchant", "blast_protection_enchant", "projectile_protection_enchant");
        registerGroup("bow_utility",
                "infinity", "mending", "infinity_enchant", "mending_enchant");
        registerGroup("trident_meta",
                "loyalty", "riptide", "channeling");
        registerGroup("crossbow_meta",
                "multishot", "piercing");
        registerGroup("tool_loot",
                "silk_touch", "loot_bonus_blocks", "fortune_enchant");
        registerGroup("boots_movement",
                "depth_strider", "frost_walker");
        registerGroup("weapon_fire_aspect",
                "fire_aspect", "flame");
    }

    private EnchantmentConflictManager() {}

    /**
     * Зарегистрировать группу конфликтов.
     */
    public static void registerGroup(String groupId, String... enchantKeys) {
        Set<String> set = CONFLICT_GROUPS.computeIfAbsent(groupId, k -> new LinkedHashSet<>());
        for (String k : enchantKeys) {
            String key = k.toLowerCase(Locale.ROOT);
            set.add(key);
            ENCHANT_TO_GROUP.put(key, groupId);
        }
    }

    /**
     * Получить ID группы для зачарования.
     */
    public static String getGroupId(Enchantment ench) {
        if (ench == null) return null;
        String key = resolveKey(ench);
        return key == null ? null : ENCHANT_TO_GROUP.get(key.toLowerCase(Locale.ROOT));
    }

    /**
     * Получить все ключи зачарований в группе (включая запрошенный).
     */
    public static Set<String> getGroupMembers(String groupId) {
        return CONFLICT_GROUPS.getOrDefault(groupId, Collections.emptySet());
    }

    /**
     * Получить читаемое имя группы.
     */
    public static String getGroupDisplayName(String groupId) {
        switch (groupId) {
            case "damage_swords":   return "⚔️ Урон по мобам (Sharpness / Smite / Bane)";
            case "protection":      return "🛡️ Защита (одна из Protection-типов)";
            case "bow_utility":     return "🏹 Утилитарные (Infinity / Mending)";
            case "trident_meta":    return "🔱 Трезубец (Loyalty / Riptide / Channeling)";
            case "crossbow_meta":   return "🎯 Арбалет (Multishot / Piercing)";
            case "tool_loot":       return "⛏️ Инструмент (Silk Touch / Fortune)";
            case "boots_movement":  return "👢 Ботинки (Depth Strider / Frost Walker)";
            case "weapon_fire_aspect": return "🔥 Огненный аспект (Fire Aspect / Flame)";
            default:                return groupId;
        }
    }

    /**
     * Проверить одну книгу: если в ней ≥ 2 чара из одной группы — это конфликт.
     * Если в ней несовместимые ванильные чары (например, Sharpness + Smite) — это тоже конфликт.
     */
    public static ConflictResult validateEnchantedBook(ItemStack book) {
        if (book == null || !book.hasItemMeta() || !book.getItemMeta().hasEnchants()) {
            return ConflictResult.VALID;
        }
        Map<Enchantment, Integer> enchants = book.getItemMeta().getEnchants();
        return validateEnchantMap(enchants);
    }

    /**
     * Проверить любой предмет (меч, броню, инструмент).
     */
    public static ConflictResult validateItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasEnchants()) {
            return ConflictResult.VALID;
        }
        return validateEnchantMap(item.getItemMeta().getEnchants());
    }

    /**
     * Низкоуровневая проверка карты чар.
     */
    public static ConflictResult validateEnchantMap(Map<Enchantment, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) return ConflictResult.VALID;
        // groupId -> list of conflicting enchant names
        Map<String, List<String>> seenInGroup = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            String key = resolveKey(e.getKey());
            if (key == null) continue;
            String group = ENCHANT_TO_GROUP.get(key.toLowerCase(Locale.ROOT));
            if (group == null) continue;
            seenInGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(humanName(key));
        }
        for (Map.Entry<String, List<String>> entry : seenInGroup.entrySet()) {
            if (entry.getValue().size() >= 2) {
                return new ConflictResult(false, entry.getKey(), entry.getValue());
            }
        }
        return ConflictResult.VALID;
    }

    /**
     * Получить ключ зачарования, безопасный для 1.16 и 1.20+:
     *  - На 1.20+ пытаемся Registry.ENCHANTMENT.getKey().
     *  - На 1.16-1.19 используем getName() (deprecation, но рабочий).
     */
    public static String resolveKey(Enchantment ench) {
        if (ench == null) return null;
        // Новый API 1.20+
        try {
            NamespacedKey nsk = Registry.ENCHANTMENT.getKey(ench);
            if (nsk != null) {
                return nsk.getKey().toLowerCase(Locale.ROOT);
            }
        } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
            // Fall through
        }
        // Старый API
        return ench.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Получить читаемое имя для отображения.
     */
    public static String humanName(String key) {
        if (key == null) return "?";
        switch (key.toLowerCase(Locale.ROOT)) {
            case "sharpness":            return "Острота (Sharpness)";
            case "smite":                return "Небесная кара (Smite)";
            case "bane_of_arthropods":   return "Гибель членистоногих (Bane)";
            case "damage_all":           return "Острота (legacy)";
            case "protection":           return "Защита (Protection)";
            case "fire_protection":      return "Огнезащита";
            case "blast_protection":     return "Взрывозащита";
            case "projectile_protection":return "Защита от снарядов";
            case "infinity":             return "Бесконечность (Infinity)";
            case "mending":              return "Починка (Mending)";
            case "loyalty":              return "Верность (Loyalty)";
            case "riptide":              return "Буря (Riptide)";
            case "channeling":           return "Канал (Channeling)";
            case "multishot":            return "Тройной выстрел (Multishot)";
            case "piercing":             return "Пробивание (Piercing)";
            case "silk_touch":           return "Шёлковое касание (Silk Touch)";
            case "loot_bonus_blocks":    return "Удача (Fortune)";
            case "depth_strider":        return "Ходок глубин (Depth Strider)";
            case "frost_walker":         return "Ледяная поступь (Frost Walker)";
            case "fire_aspect":          return "Огненный аспект";
            case "flame":                return "Пламя (Flame)";
            default:                     return key;
        }
    }

    /**
     * Зарегистрировать кастомный конфликт (для RPG-чарований артефактов).
     * Например, "sharpness_enchant" уже в damage_swords, но если у нас есть RPG-группа
     * типа "fire_damage", её можно добавить отдельно.
     */
    public static void registerCustomGroup(String groupId, String displayName, String... enchantKeys) {
        Set<String> set = CONFLICT_GROUPS.computeIfAbsent(groupId, k -> new LinkedHashSet<>());
        for (String k : enchantKeys) {
            String key = k.toLowerCase(Locale.ROOT);
            set.add(key);
            ENCHANT_TO_GROUP.put(key, groupId);
        }
        if (displayName != null) GROUP_DISPLAY_OVERRIDES.put(groupId, displayName);
    }

    private static final Map<String, String> GROUP_DISPLAY_OVERRIDES = new HashMap<>();
    static {
        GROUP_DISPLAY_OVERRIDES.put("damage_swords", "⚔️ Урон по мобам (Sharpness / Smite / Bane)");
        GROUP_DISPLAY_OVERRIDES.put("protection", "🛡️ Защита (только один тип Protection)");
        GROUP_DISPLAY_OVERRIDES.put("bow_utility", "🏹 Утилитарные (Infinity / Mending)");
        GROUP_DISPLAY_OVERRIDES.put("trident_meta", "🔱 Трезубец (Loyalty / Riptide / Channeling)");
        GROUP_DISPLAY_OVERRIDES.put("crossbow_meta", "🎯 Арбалет (Multishot / Piercing)");
        GROUP_DISPLAY_OVERRIDES.put("tool_loot", "⛏️ Инструмент (Silk Touch / Fortune)");
        GROUP_DISPLAY_OVERRIDES.put("boots_movement", "👢 Ботинки (Depth Strider / Frost Walker)");
        GROUP_DISPLAY_OVERRIDES.put("weapon_fire_aspect", "🔥 Огненный аспект (Fire Aspect / Flame)");
    }

    /**
     * Получить читаемое имя группы (с учётом override).
     */
    public static String getGroupDisplayNameOverridden(String groupId) {
        if (GROUP_DISPLAY_OVERRIDES.containsKey(groupId)) {
            return GROUP_DISPLAY_OVERRIDES.get(groupId);
        }
        return getGroupDisplayName(groupId);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     *  Результат проверки конфликтов.
     * ═══════════════════════════════════════════════════════════════════════
     */
    public static final class ConflictResult {
        public static final ConflictResult VALID = new ConflictResult(true, null, Collections.emptyList());

        private final boolean valid;
        private final String groupId;
        private final List<String> conflicts;

        ConflictResult(boolean valid, String groupId, List<String> conflicts) {
            this.valid = valid;
            this.groupId = groupId;
            this.conflicts = conflicts;
        }

        public boolean isValid() { return valid; }
        public String getGroupId() { return groupId; }
        public List<String> getConflicts() { return conflicts; }

        /** Отформатировать сообщение об ошибке для игрока. */
        public String formatUserMessage() {
            if (valid) return "§a✔ Зачарования совместимы";
            String group = getGroupDisplayNameOverridden(groupId);
            return "§c✖ Конфликт чар в группе: §e" + group + "\n" +
                    "§7Содержит: §f" + String.join(", ", conflicts) + "\n" +
                    "§7Можно оставить только одно из них.";
        }

        /** Короткое сообщение для title/actionbar. */
        public String formatShortMessage() {
            if (valid) return "✔";
            return "✖ " + (groupId == null ? "конфликт" : groupId);
        }
    }
}
