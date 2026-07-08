package ru.example.vkchatartifacts.conflicts;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  ArtifactEffectRegistry v1.0
 *  ─────────────────────────────────────────────────────────────────────
 *  Реестр RPG-эффектов артефактов с поддержкой:
 *    • Групп эффектов (взаимоисключающие: SPEED vs WIND_WALKER_potion).
 *    • Приоритетов эффектов (DRAGON_BLOOD > HEALTH).
 *    • Тип эффекта (POTION / ATTRIBUTE / EVENT / PASSIVE).
 *    • Конфликт-матрицы buff × curse.
 *    • Scroll boost (x1.5 на 10 мин).
 *
 *  ЗАМЕНЯЕТ хардкод if/else в ArtifactListener.
 * ═══════════════════════════════════════════════════════════════════════════
 */
public final class ArtifactEffectRegistry {

    private static final Logger LOG = Logger.getLogger("ArtifactEffectRegistry");

    /** PDC-ключи. Должны совпадать с ключами в vkchat_artifacts. */
    public static final String PLUGIN_NAME = "VKChatArtifacts";
    public static NamespacedKey buffKey() {
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (p == null) p = Bukkit.getPluginManager().getPlugin("VKChatMarket");
        return new NamespacedKey(p, "buff_type");
    }
    public static NamespacedKey levelKey() {
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (p == null) p = Bukkit.getPluginManager().getPlugin("VKChatMarket");
        return new NamespacedKey(p, "buff_level");
    }
    public static NamespacedKey curseKey() {
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (p == null) p = Bukkit.getPluginManager().getPlugin("VKChatMarket");
        return new NamespacedKey(p, "curse_type");
    }
    public static NamespacedKey isArtifactKey() {
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (p == null) p = Bukkit.getPluginManager().getPlugin("VKChatMarket");
        return new NamespacedKey(p, "is_artifact");
    }

    /** Тип эффекта. */
    public enum EffectType {
        POTION,        // PotionEffect (регенерация, скорость, сопротивление)
        ATTRIBUTE,     // Атрибут (MaxHealth, Speed, Armor, KB Resistance)
        EVENT,         // Срабатывает на событие (VAMPIRISM, THORNS, DODGE_CHANCE, ARCANE_BURST)
        PASSIVE        // Пассивный (TELEKINESIS, REVIVAL)
    }

    /** Категория. */
    public enum Category {
        OFFENSE, DEFENSE, MOBILITY, UTILITY, SURVIVAL, SPECIAL
    }

    /** Описание эффекта. */
    public static final class Effect {
        public final String id;
        public final String displayName;
        public final EffectType type;
        public final Category category;
        public final int defaultPriority;
        public final String group;
        public final String oppositeBuff;
        public final String oppositeCurse;

        public Effect(String id, String displayName, EffectType type, Category category,
                      int defaultPriority, String group, String oppositeBuff, String oppositeCurse) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
            this.category = category;
            this.defaultPriority = defaultPriority;
            this.group = group;
            this.oppositeBuff = oppositeBuff;
            this.oppositeCurse = oppositeCurse;
        }
    }

    // ═══ Группы (конфликтующие) ═══
    public static final String GROUP_SPEED        = "speed";
    public static final String GROUP_HEALTH       = "health";
    public static final String GROUP_REGEN        = "regen";
    public static final String GROUP_ABSORPTION   = "absorption";
    public static final String GROUP_RESISTANCE   = "damage_resistance";
    public static final String GROUP_DAMAGE       = "damage";
    public static final String GROUP_CRIT         = "crit";
    public static final String GROUP_LIFESTEAL    = "lifesteal";
    public static final String GROUP_FIRE         = "fire";
    public static final String GROUP_NIGHT_VISION = "night_vision";
    public static final String GROUP_LUCK         = "luck";

    private static final Map<String, Effect> EFFECTS = new LinkedHashMap<>();
    private static final Map<String, List<String>> GROUP_MEMBERS = new HashMap<>();

    static {
        // OFFENSE
        register(new Effect("DAMAGE",          "Урон в ближнем бою",     EffectType.EVENT,    Category.OFFENSE,  50,  GROUP_DAMAGE,    "TRUE_STRIKE", null));
        register(new Effect("TRUE_STRIKE",     "Истинный удар",          EffectType.EVENT,    Category.OFFENSE,  60,  GROUP_DAMAGE,    "DAMAGE",      null));
        register(new Effect("STRENGTH_BOOST",  "Сила",                   EffectType.POTION,   Category.OFFENSE,  55,  GROUP_DAMAGE,    null,          "WEAKNESS"));
        register(new Effect("BERSERKER",       "Ярость",                 EffectType.EVENT,    Category.OFFENSE,  40,  GROUP_DAMAGE,    null,          null));
        register(new Effect("ECHO_STRIKE",     "Удар-эхо",               EffectType.EVENT,    Category.OFFENSE,  45,  GROUP_CRIT,      "CRITICAL",    null));
        register(new Effect("CRITICAL",        "Критический удар",       EffectType.EVENT,    Category.OFFENSE,  45,  GROUP_CRIT,      "ECHO_STRIKE", null));
        register(new Effect("WITHER_TOUCH",    "Касание Иссушителя",     EffectType.EVENT,    Category.OFFENSE,  30,  null,            null,          null));
        register(new Effect("POISON_STRIKE",   "Ядовитый удар",          EffectType.EVENT,    Category.OFFENSE,  30,  null,            null,          null));
        register(new Effect("FROST_BITE",      "Морозный укус",          EffectType.EVENT,    Category.OFFENSE,  30,  null,            null,          null));
        register(new Effect("FLAME_TONGUE",    "Пылающий язык",          EffectType.EVENT,    Category.OFFENSE,  30,  null,            null,          null));
        register(new Effect("LIGHTNING_STRIKE","Удар молнии",            EffectType.EVENT,    Category.OFFENSE,  25,  null,            null,          null));
        register(new Effect("ARCANE_BURST",    "Магический взрыв",       EffectType.EVENT,    Category.OFFENSE,  20,  null,            null,          null));

        // DEFENSE
        register(new Effect("STEEL_SKIN",      "Стальная кожа",          EffectType.ATTRIBUTE,Category.DEFENSE,  50,  null,            null,          null));
        register(new Effect("RESISTANCE",      "Сопротивление",          EffectType.POTION,   Category.DEFENSE,  40,  GROUP_RESISTANCE,"MANA_SHIELD", "VULNERABILITY"));
        register(new Effect("MANA_SHIELD",     "Мана-щит",               EffectType.EVENT,    Category.DEFENSE,  45,  GROUP_RESISTANCE,"RESISTANCE",  "VULNERABILITY"));
        register(new Effect("IRON_WILL",       "Железная воля",          EffectType.EVENT,    Category.DEFENSE,  35,  null,            null,          null));
        register(new Effect("THORNS",          "Отражение урона",        EffectType.EVENT,    Category.DEFENSE,  30,  null,            null,          null));
        register(new Effect("DODGE_CHANCE",    "Уклонение",              EffectType.EVENT,    Category.DEFENSE,  40,  null,            "SHADOW_STEP", null));
        register(new Effect("KNOCKBACK_RESIST","Сопротивление отбрасыванию", EffectType.ATTRIBUTE, Category.DEFENSE, 30, null, null, null));
        register(new Effect("SOUL_SHIELD",     "Щит души",               EffectType.POTION,   Category.DEFENSE,  30,  GROUP_ABSORPTION,"ABSORPTION",  null));

        // MOBILITY
        register(new Effect("SPEED",           "Скорость передвижения",  EffectType.ATTRIBUTE,Category.MOBILITY,  60,  GROUP_SPEED,     "WIND_WALKER", "SLOWNESS"));
        register(new Effect("WIND_WALKER",     "Шагающий по ветру",      EffectType.POTION,   Category.MOBILITY,  50,  GROUP_SPEED,     "SPEED",       "SLOWNESS"));
        register(new Effect("JUMP_BOOST",      "Прыгучесть",             EffectType.POTION,   Category.MOBILITY,  40,  null,            null,          null));
        register(new Effect("DOUBLE_JUMP",     "Двойной прыжок",         EffectType.PASSIVE,  Category.MOBILITY,  45,  null,            "ENDER_SHIFT", null));
        register(new Effect("ENDER_SHIFT",     "Эндер-сдвиг",            EffectType.PASSIVE,  Category.MOBILITY,  45,  null,            "DOUBLE_JUMP", "ANCHOR"));
        register(new Effect("AQUATIC_SPEED",   "Скорость под водой",     EffectType.POTION,   Category.MOBILITY,  35,  null,            null,          null));
        register(new Effect("FIRE_WALKER",     "Хождение по огню",       EffectType.POTION,   Category.MOBILITY,  35,  null,            null,          null));
        register(new Effect("LEVITATION",      "Иммунитет к падению",    EffectType.EVENT,    Category.MOBILITY,  40,  null,            null,          null));
        register(new Effect("TELEKINESIS",     "Телекинез",              EffectType.PASSIVE,  Category.MOBILITY,  30,  null,            null,          null));

        // UTILITY
        register(new Effect("XP_BOOST",        "Бонус опыта",            EffectType.EVENT,    Category.UTILITY,   30,  null,            "XP_MAGNET",   null));
        register(new Effect("XP_MAGNET",       "Магнит опыта",           EffectType.EVENT,    Category.UTILITY,   30,  null,            "XP_BOOST",    null));
        register(new Effect("LUCK",            "Удача",                  EffectType.POTION,   Category.UTILITY,   40,  GROUP_LUCK,      "LUCK_OF_THE_SEA", null));
        register(new Effect("LUCK_OF_THE_SEA", "Морская удача",          EffectType.POTION,   Category.UTILITY,   40,  GROUP_LUCK,      "LUCK",        null));
        register(new Effect("TREASURE_HUNTER", "Охотник за сокровищами", EffectType.PASSIVE,  Category.UTILITY,   30,  null,            null,          null));
        register(new Effect("LOOT_FIND",       "Поиск добычи",           EffectType.EVENT,    Category.UTILITY,   30,  null,            null,          null));
        register(new Effect("HASTE",           "Спешка",                 EffectType.POTION,   Category.UTILITY,   30,  null,            null,          null));
        register(new Effect("HERO_OF_VILLAGE", "Герой Деревни",          EffectType.POTION,   Category.UTILITY,   20,  null,            null,          null));
        register(new Effect("TRAP_SENSE",      "Чувство ловушки",        EffectType.PASSIVE,  Category.UTILITY,   20,  null,            null,          null));

        // SURVIVAL
        register(new Effect("HEALTH",          "Здоровье",               EffectType.ATTRIBUTE,Category.SURVIVAL,  60,  GROUP_HEALTH,    "MAX_HEALTH_BOOST", null));
        register(new Effect("MAX_HEALTH_BOOST","Колоссальное здоровье",  EffectType.ATTRIBUTE,Category.SURVIVAL,  65,  GROUP_HEALTH,    "HEALTH",      null));
        register(new Effect("DRAGON_BLOOD",    "Кровь Дракона",          EffectType.POTION,   Category.SURVIVAL,  70,  GROUP_HEALTH,    null,          null));
        register(new Effect("REGENERATION",    "Регенерация",            EffectType.POTION,   Category.SURVIVAL,  50,  GROUP_REGEN,     null,          null));
        register(new Effect("ABSORPTION",      "Абсорбция",              EffectType.POTION,   Category.SURVIVAL,  50,  GROUP_ABSORPTION,"SOUL_SHIELD", null));
        register(new Effect("NIGHT_VISION",    "Ночное зрение",          EffectType.POTION,   Category.SURVIVAL,  40,  GROUP_NIGHT_VISION, null,       "BLINDNESS"));
        register(new Effect("FIRE_RESISTANCE", "Огнестойкость",          EffectType.POTION,   Category.SURVIVAL,  40,  null,            "FIRE_RESISTANCE_AURA", null));
        register(new Effect("FIRE_RESISTANCE_AURA","Аура огнестойкости",  EffectType.POTION,   Category.SURVIVAL,  35,  null,            "FIRE_RESISTANCE", null));
        register(new Effect("WATER_BREATHING", "Дыхание под водой",      EffectType.POTION,   Category.SURVIVAL,  40,  null,            null,          null));
        register(new Effect("SATURATION",      "Вечная сытость",         EffectType.POTION,   Category.SURVIVAL,  30,  null,            null,          "HUNGER"));
        register(new Effect("FREEZE_AURA",     "Ледяная аура",           EffectType.PASSIVE,  Category.SURVIVAL,  20,  null,            null,          null));
        register(new Effect("SOUL_DRAIN",      "Вытягивание души",       EffectType.EVENT,    Category.SURVIVAL,  35,  null,            null,          null));
        register(new Effect("LIFESTEAL_AURA",  "Аура вампиризма",        EffectType.EVENT,    Category.SURVIVAL,  50,  GROUP_LIFESTEAL, "VAMPIRISM",   null));
        register(new Effect("VAMPIRISM",       "Вампиризм",              EffectType.EVENT,    Category.SURVIVAL,  45,  GROUP_LIFESTEAL, "LIFESTEAL_AURA", null));
        register(new Effect("SHADOW_STEP",     "Теневой шаг",            EffectType.EVENT,    Category.MOBILITY,  35,  null,            "DODGE_CHANCE",null));
        register(new Effect("GHOST_WALK",      "Призрачный шаг",         EffectType.POTION,   Category.SURVIVAL,  30,  null,            null,          "BLINDNESS"));

        // SPECIAL
        register(new Effect("REVIVAL",         "Возрождение",            EffectType.EVENT,    Category.SPECIAL,  100,  null,            null,          null));
        register(new Effect("ABYSSAL_POWER",   "Сила Бездны",            EffectType.POTION,   Category.SPECIAL,   90,  null,            null,          null));
    }

    private static void register(Effect e) {
        EFFECTS.put(e.id, e);
        if (e.group != null) {
            GROUP_MEMBERS.computeIfAbsent(e.group, k -> new ArrayList<>()).add(e.id);
        }
    }

    public static Effect getEffect(String id) {
        if (id == null) return null;
        return EFFECTS.get(id.toUpperCase(Locale.ROOT));
    }

    public static Collection<Effect> all() { return EFFECTS.values(); }

    public static List<String> getGroupMembers(String groupId) {
        return GROUP_MEMBERS.getOrDefault(groupId, Collections.emptyList());
    }

    /**
     * Собрать активные эффекты игрока, разрешая конфликты.
     */
    public static ApplyResult collectActive(Player player, Map<UUID, Long> scrollBoosts) {
        Map<String, AppliedEffect> all = new LinkedHashMap<>();
        Map<String, List<AppliedEffect>> byGroup = new HashMap<>();
        Set<String> activeCurses = new HashSet<>();

        boolean scrollActive = false;
        if (scrollBoosts != null) {
            Long expiry = scrollBoosts.get(player.getUniqueId());
            scrollActive = expiry != null && expiry > System.currentTimeMillis();
        }

        NamespacedKey isArt = isArtifactKey();
        NamespacedKey buffK = buffKey();
        NamespacedKey lvlK = levelKey();
        NamespacedKey curK = curseKey();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.getPersistentDataContainer().has(isArt, PersistentDataType.INTEGER)) continue;

            String buffId = meta.getPersistentDataContainer().get(buffK, PersistentDataType.STRING);
            String curse = meta.getPersistentDataContainer().get(curK, PersistentDataType.STRING);
            Integer lvlObj = meta.getPersistentDataContainer().get(lvlK, PersistentDataType.INTEGER);
            int level = lvlObj == null ? 1 : lvlObj;

            if (curse != null && !curse.equals("NONE")) activeCurses.add(curse);
            if (buffId == null) continue;

            Effect eff = getEffect(buffId);
            if (eff == null) continue;
            AppliedEffect ae = new AppliedEffect(eff, level);
            all.putIfAbsent(buffId, ae);
            if (eff.group != null) {
                byGroup.computeIfAbsent(eff.group, k -> new ArrayList<>()).add(ae);
            }
        }

        // Разрешение конфликтов: в группе побеждает наивысший приоритет, тай-брейк — level
        Map<String, AppliedEffect> winners = new HashMap<>();
        for (Map.Entry<String, List<AppliedEffect>> g : byGroup.entrySet()) {
            AppliedEffect best = null;
            for (AppliedEffect a : g.getValue()) {
                if (best == null
                        || a.eff.defaultPriority > best.eff.defaultPriority
                        || (a.eff.defaultPriority == best.eff.defaultPriority && a.level > best.level)) {
                    best = a;
                }
            }
            if (best != null) winners.put(g.getKey(), best);
        }

        return new ApplyResult(winners, all, activeCurses, scrollActive);
    }

    /** Применённый эффект с уровнем. */
    public static final class AppliedEffect {
        public final Effect eff;
        public final int level;
        public AppliedEffect(Effect eff, int level) {
            this.eff = eff;
            this.level = level;
        }
    }

    /** Итог сбора эффектов. */
    public static final class ApplyResult {
        public final Map<String, AppliedEffect> winners;
        public final Map<String, AppliedEffect> all;
        public final Set<String> curses;
        public final boolean scrollBoost;

        public ApplyResult(Map<String, AppliedEffect> winners, Map<String, AppliedEffect> all,
                           Set<String> curses, boolean scrollBoost) {
            this.winners = winners;
            this.all = all;
            this.curses = curses;
            this.scrollBoost = scrollBoost;
        }

        public boolean hasBuff(String id) { return all.containsKey(id); }
        public int getBuffLevel(String id) {
            AppliedEffect a = all.get(id);
            return a == null ? 0 : a.level;
        }
        public boolean hasCurse(String id) { return curses.contains(id); }

        /** Применить scrollBoost ×1.5 только к тем эффектам, что в нём зарегистрированы. */
        public int getEffectiveLevel(String id) {
            AppliedEffect a = all.get(id);
            if (a == null) return 0;
            // Scroll boost действует на: HEALTH, MAX_HEALTH_BOOST, DRAGON_BLOOD, SPEED, STEEL_SKIN, KNOCKBACK_RESIST
            switch (id) {
                case "HEALTH": case "MAX_HEALTH_BOOST": case "DRAGON_BLOOD":
                case "SPEED":  case "STEEL_SKIN":       case "KNOCKBACK_RESIST":
                    return scrollBoost ? (int) Math.round(a.level * 1.5) : a.level;
                default:
                    return a.level;
            }
        }
    }
}
