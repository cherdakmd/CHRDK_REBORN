package ru.example.vkchatartifacts.effects;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BuffEffectRegistry — конфиг-управляемый реестр эффектов артефактов.
 *
 * FIX #6: Заменяет 50+ if-else цепочек в ArtifactListener.applyPassiveEffects()
 * IMPROVE #5: Баффы и проклятия настраиваются из config.yml
 */
public class BuffEffectRegistry {

    private final JavaPlugin plugin;
    private final Map<String, BuffDef> buffs = new LinkedHashMap<>();
    private final Map<String, CurseDef> curses = new LinkedHashMap<>();

    public static final class BuffDef {
        private final String id;
        private final String displayName;
        private final BuffType type;
        private final PotionEffectType potionEffect;
        private final double baseValue;     // для HEALTH, SPEED, ARMOR и т.д.
        private final boolean isPassive;    // применяется каждый тик
        private final boolean isOnAttack;   // при атаке
        private final boolean isOnDamage;   // при получении урона
        private final boolean isOnKill;     // при убийстве моба
        private final String description;

        public BuffDef(String id, String displayName, BuffType type,
                       PotionEffectType potionEffect, double baseValue,
                       boolean isPassive, boolean isOnAttack, boolean isOnDamage,
                       boolean isOnKill, String description) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
            this.potionEffect = potionEffect;
            this.baseValue = baseValue;
            this.isPassive = isPassive;
            this.isOnAttack = isOnAttack;
            this.isOnDamage = isOnDamage;
            this.isOnKill = isOnKill;
            this.description = description;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public BuffType getType() { return type; }
        public PotionEffectType getPotionEffect() { return potionEffect; }
        public double getBaseValue() { return baseValue; }
        public boolean isPassive() { return isPassive; }
        public boolean isOnAttack() { return isOnAttack; }
        public boolean isOnDamage() { return isOnDamage; }
        public boolean isOnKill() { return isOnKill; }
        public String getDescription() { return description; }
    }

    public enum BuffType {
        POTION,        // Выдаёт зельный эффект
        ATTRIBUTE,     // Модифицирует атрибут (здоровье, скорость, броня)
        SPECIAL,       // Специальная механика (Double Jump, Teleport и т.д.)
        ON_ATTACK,     // Срабатывает при атаке
        ON_DAMAGE,     // Срабатывает при получении урона
        ON_KILL        // Срабатывает при убийстве
    }

    public static final class CurseDef {
        private final String id;
        private final String displayName;
        private final CurseType type;
        private final PotionEffectType potionEffect;
        private final double value;
        private final String description;

        public CurseDef(String id, String displayName, CurseType type,
                        PotionEffectType potionEffect, double value, String description) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
            this.potionEffect = potionEffect;
            this.value = value;
            this.description = description;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public CurseType getType() { return type; }
        public PotionEffectType getPotionEffect() { return potionEffect; }
        public double getValue() { return value; }
        public String getDescription() { return description; }
    }

    public enum CurseType {
        POTION,         // Выдаёт постоянный зельный эффект
        DAMAGE_AMP,     // Увеличивает входящий урон
        HEALTH_REDUCE,  // Уменьшает максимальное здоровье
        BLOCK_ACTION,   // Блокирует действие (телепорт, предмет)
        SPECIAL         // Специальная механика
    }

    public BuffEffectRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    /**
     * Регистрируем дефолтные баффы (обратная совместимость).
     * В будущем — из config.yml секции artifact-buffs.
     */
    private void registerDefaults() {
        // Зельные пассивные баффы
        registerPotionBuff("REGENERATION", "Регенерация", PotionEffectType.REGENERATION, BuffType.POTION, true, false, false, false);
        registerPotionBuff("FIRE_RESISTANCE", "Огнестойкость", PotionEffectType.FIRE_RESISTANCE, BuffType.POTION, true, false, false, false);
        registerPotionBuff("ABSORPTION", "Абсорбция", PotionEffectType.ABSORPTION, BuffType.POTION, true, false, false, false);
        registerPotionBuff("NIGHT_VISION", "Ночное зрение", PotionEffectType.NIGHT_VISION, BuffType.POTION, true, false, false, false);
        registerPotionBuff("HASTE", "Спешка", PotionEffectType.FAST_DIGGING, BuffType.POTION, true, false, false, false);
        registerPotionBuff("WATER_BREATHING", "Дыхание под водой", PotionEffectType.WATER_BREATHING, BuffType.POTION, true, false, false, false);
        registerPotionBuff("JUMP_BOOST", "Прыгучесть", PotionEffectType.JUMP, BuffType.POTION, true, false, false, false);
        registerPotionBuff("LUCK", "Удача", PotionEffectType.LUCK, BuffType.POTION, true, false, false, false);
        registerPotionBuff("GHOST_WALK", "Призрачный шаг", PotionEffectType.INVISIBILITY, BuffType.POTION, true, false, false, false);
        registerPotionBuff("AQUATIC_SPEED", "Скорость под водой", PotionEffectType.DOLPHINS_GRACE, BuffType.POTION, true, false, false, false);
        registerPotionBuff("HERO_OF_VILLAGE", "Герой Деревни", PotionEffectType.HERO_OF_THE_VILLAGE, BuffType.POTION, true, false, false, false);
        registerPotionBuff("STRENGTH_BOOST", "Сила", PotionEffectType.INCREASE_DAMAGE, BuffType.POTION, true, false, false, false);
        registerPotionBuff("RESISTANCE", "Сопротивление", PotionEffectType.DAMAGE_RESISTANCE, BuffType.POTION, true, false, false, false);
        registerPotionBuff("SATURATION", "Сытость", PotionEffectType.SATURATION, BuffType.POTION, true, false, false, false);
        registerPotionBuff("LUCK_OF_THE_SEA", "Морская удача", PotionEffectType.LUCK, BuffType.POTION, true, false, false, false);
        registerPotionBuff("FIRE_RESISTANCE_AURA", "Аура огнестойкости", PotionEffectType.FIRE_RESISTANCE, BuffType.POTION, true, false, false, false);

        // Атрибутные баффы
        registerAttributeBuff("HEALTH", "Здоровье", 2.0);
        registerAttributeBuff("SPEED", "Скорость", 0.1);
        registerAttributeBuff("STEEL_SKIN", "Стальная кожа", 1.0);
        registerAttributeBuff("KNOCKBACK_RESIST", "Анти-отбрасывание", 0.3);
        registerAttributeBuff("MAX_HEALTH_BOOST", "Колоссальное здоровье", 10.0);

        // Специальные баффы
        registerSpecialBuff("DOUBLE_JUMP", "Двойной прыжок");
        registerSpecialBuff("ENDER_SHIFT", "Эндер-сдвиг");
        registerSpecialBuff("TELEKINESIS", "Телекинез");
        registerSpecialBuff("REVIVAL", "Возрождение");
        registerSpecialBuff("DRAGON_BLOOD", "Кровь Дракона");
        registerSpecialBuff("ABYSSAL_POWER", "Сила Бездны");
        registerSpecialBuff("FREEZE_AURA", "Ледяная аура");
        registerSpecialBuff("WIND_WALKER", "Шагающий по ветру");

        // Атакующие баффы
        registerAttackBuff("DAMAGE", "Урон");
        registerAttackBuff("VAMPIRISM", "Вампиризм");
        registerAttackBuff("CRITICAL", "Критический удар");
        registerAttackBuff("WITHER_TOUCH", "Касание Иссушителя");
        registerAttackBuff("POISON_STRIKE", "Ядовитый удар");
        registerAttackBuff("LIGHTNING_STRIKE", "Удар молнии");
        registerAttackBuff("TRUE_STRIKE", "Истинный удар");
        registerAttackBuff("FROST_BITE", "Морозный укус");
        registerAttackBuff("BERSERKER", "Ярость");
        registerAttackBuff("FLAME_TONGUE", "Пылающий язык");
        registerAttackBuff("ECHO_STRIKE", "Удар-эхо");
        registerAttackBuff("LIFESTEAL_AURA", "Аура вампиризма");
        registerAttackBuff("SOUL_DRAIN", "Вытягивание душ");
        registerAttackBuff("LOOT_FIND", "Поиск добычи");

        // Защитные при получении урона
        registerDamageBuff("THORNS", "Шипы");
        registerDamageBuff("DODGE_CHANCE", "Уклонение");
        registerDamageBuff("MANA_SHIELD", "Мана-щит");
        registerDamageBuff("IRON_WILL", "Железная воля");
        registerDamageBuff("ARCANE_BURST", "Магический взрыв");
        registerDamageBuff("SOUL_SHIELD", "Щит души");

        // Проклятия
        registerCurse("SLOWNESS", "Замедление", CurseType.POTION, PotionEffectType.SLOW, 0, "☠ Замедление I");
        registerCurse("WEAKNESS", "Слабость", CurseType.POTION, PotionEffectType.WEAKNESS, 0, "☠ Слабость I");
        registerCurse("HUNGER", "Голод", CurseType.POTION, PotionEffectType.HUNGER, 1, "☠ Сильный Голод");
        registerCurse("BLINDNESS", "Слепота", CurseType.POTION, PotionEffectType.BLINDNESS, 0, "☠ Периодическая слепота");
        registerCurse("DECAY", "Разложение", CurseType.SPECIAL, null, 1, "☠ Разложение (потеря ХП)");
        registerCurse("VULNERABILITY", "Уязвимость", CurseType.DAMAGE_AMP, null, 1.2, "☠ Уязвимость (+20% урон)");
        registerCurse("GREED", "Жадность", CurseType.HEALTH_REDUCE, null, -6.0, "☠ Жадность (+50% реп, −30% HP)");
        registerCurse("BLOODLETTING", "Кровопускание", CurseType.SPECIAL, null, 0.2, "☠ Кровопускание (20% урона назад)");
        registerCurse("ANCHOR", "Якорь", CurseType.BLOCK_ACTION, null, 0, "☠ Якорь (нет телепорта)");
        registerCurse("SILENCE", "Молчание", CurseType.BLOCK_ACTION, null, 0, "☠ Молчание (блок предметов)");
        registerCurse("NIGHTMARE", "Кошмар", CurseType.SPECIAL, null, 0.01, "☠ Кошмар (случайная тошнота 1%)");
        registerCurse("CHAOS", "Хаос", CurseType.SPECIAL, null, 0.10, "☠ Хаос (случайные зелья 10%)");
    }

    // ═══ Регистрационные методы ═══

    private void registerPotionBuff(String id, String name, PotionEffectType effect, BuffType type,
                                     boolean passive, boolean onAttack, boolean onDamage, boolean onKill) {
        buffs.put(id, new BuffDef(id, name, type, effect, 0, passive, onAttack, onDamage, onKill, name));
    }

    private void registerAttributeBuff(String id, String name, double baseValue) {
        buffs.put(id, new BuffDef(id, name, BuffType.ATTRIBUTE, null, baseValue, true, false, false, false, name));
    }

    private void registerSpecialBuff(String id, String name) {
        buffs.put(id, new BuffDef(id, name, BuffType.SPECIAL, null, 0, false, false, false, false, name));
    }

    private void registerAttackBuff(String id, String name) {
        buffs.put(id, new BuffDef(id, name, BuffType.ON_ATTACK, null, 0, false, true, false, false, name));
    }

    private void registerDamageBuff(String id, String name) {
        buffs.put(id, new BuffDef(id, name, BuffType.ON_DAMAGE, null, 0, false, false, true, false, name));
    }

    private void registerCurse(String id, String name, CurseType type, PotionEffectType effect, double value, String desc) {
        curses.put(id, new CurseDef(id, name, type, effect, value, desc));
    }

    // ═══ Геттеры ═══

    public BuffDef getBuff(String id) { return buffs.get(id); }
    public CurseDef getCurse(String id) { return curses.get(id); }
    public Collection<BuffDef> getAllBuffs() { return Collections.unmodifiableCollection(buffs.values()); }
    public Collection<CurseDef> getAllCurses() { return Collections.unmodifiableCollection(curses.values()); }

    /**
     * Проверить тип баффа.
     */
    public boolean isPotionBuff(String buffId) {
        BuffDef def = buffs.get(buffId);
        return def != null && def.getType() == BuffType.POTION;
    }

    public boolean isAttributeBuff(String buffId) {
        BuffDef def = buffs.get(buffId);
        return def != null && def.getType() == BuffType.ATTRIBUTE;
    }

    public boolean isSpecialBuff(String buffId) {
        BuffDef def = buffs.get(buffId);
        return def != null && def.getType() == BuffType.SPECIAL;
    }

    public boolean isOnAttackBuff(String buffId) {
        BuffDef def = buffs.get(buffId);
        return def != null && def.isOnAttack();
    }

    public boolean isOnDamageBuff(String buffId) {
        BuffDef def = buffs.get(buffId);
        return def != null && def.isOnDamage();
    }

    /**
     * Получить описание баффа (для лора артефакта).
     */
    public String getBuffLore(String buffId, int level) {
        BuffDef def = buffs.get(buffId);
        if (def == null) return ChatColor.GRAY + "Неизвестный эффект";
        return ChatColor.GREEN + "➕ " + def.getDisplayName() + " " + level + " ур.";
    }

    /**
     * Получить описание проклятия (для лора артефакта).
     */
    public String getCurseLore(String curseId) {
        CurseDef def = curses.get(curseId);
        if (def == null) return ChatColor.RED + "☠ Неизвестное проклятие";
        return ChatColor.RED + def.getDescription();
    }
}
