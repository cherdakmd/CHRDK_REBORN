package ru.example.vkchatoffline.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.*;

/**
 * Данные зон, врагов, лута и сетовых предметов
 */
public final class ZoneData {
    private ZoneData() {}

    // ===== ЗОНЫ =====
    public enum Zone {
        DARK_FOREST("🌲 Тёмный лес", 1, 4, "🎋", ChatColor.DARK_GREEN),
        DEEP_MINES("⛏ Глубокие шахты", 2, 5, "💎", ChatColor.GRAY),
        ANCIENT_RUINS("🏛 Древние руины", 3, 5, "📜", ChatColor.GOLD),
        NETHER_WASTES("🔥 Незер-пустоши", 4, 6, "☠", ChatColor.DARK_RED),
        FROZEN_TUNDRA("❄️ Ледяная тундра", 5, 6, "🧊", ChatColor.AQUA),
        VOID_EDGE("🌑 Грань Бездны", 6, 7, "🕳", ChatColor.DARK_PURPLE);

        public final String name;
        public final int difficulty;
        public final int stages;
        public final String icon;
        public final ChatColor color;

        Zone(String name, int diff, int stages, String icon, ChatColor color) {
            this.name = name; this.difficulty = diff; this.stages = stages;
            this.icon = icon; this.color = color;
        }
    }

    // ===== ВРАГИ =====
    public enum EnemyType {
        // Лес
        WOLF("Лесной волк", 40, 6, 2, "🐺", ChatColor.GRAY),
        SPIDER("Гигантский паук", 50, 7, 3, "🕷", ChatColor.DARK_GREEN),
        ENT("Древний энт", 70, 8, 4, "🌳", ChatColor.GREEN),
        TREANT_BOSS("Трент-хранитель", 180, 12, 6, "👹", ChatColor.DARK_GREEN),

        // Шахты
        SKELETON("Шахтный скелет", 55, 8, 3, "💀", ChatColor.GRAY),
        CAVE_SPIDER("Пещерный паук", 45, 9, 2, "🕸", ChatColor.DARK_GRAY),
        GOLEM("Каменный голем", 100, 10, 5, "🗿", ChatColor.GRAY),
        WORM_BOSS("Червь недр", 220, 14, 8, "🪱", ChatColor.DARK_GRAY),

        // Руины
        ZOMBIE("Древний зомби", 65, 9, 4, "🧟", ChatColor.YELLOW),
        GHOST("Призрак", 50, 10, 3, "👻", ChatColor.WHITE),
        GUARDIAN("Страж руин", 120, 12, 6, "🛡", ChatColor.GOLD),
        LICH_BOSS("Лич руин", 260, 16, 8, "💀", ChatColor.GOLD),

        // Незер
        BLAZE("Пылающий блейз", 80, 11, 5, "🔥", ChatColor.RED),
        PIGLIN("Брутальный пиглин", 100, 13, 6, "🐷", ChatColor.GOLD),
        WITHER_SKELETON("Визер-скелет", 90, 14, 5, "☠", ChatColor.DARK_RED),
        NETHER_LORD("Лорд Незера", 300, 18, 10, "👑", ChatColor.DARK_RED),

        // Тундра
        STRAY("Ледяной скелет", 85, 12, 5, "💀", ChatColor.AQUA),
        ICE_GOLEM("Ледяной голем", 140, 15, 7, "❄", ChatColor.AQUA),
        FROST_WYRM("Морозный вирм", 160, 16, 8, "🐉", ChatColor.WHITE),
        FROST_DRAGON("Ледяной дракон", 380, 20, 12, "🐲", ChatColor.AQUA),

        // Бездна
        ENDERMITE("Эндермит", 60, 10, 3, "🪲", ChatColor.DARK_PURPLE),
        VOID_WALKER("Странник Бездны", 130, 15, 7, "🚶", ChatColor.DARK_PURPLE),
        SHADOW("Тень Бездны", 110, 14, 6, "👤", ChatColor.BLACK),
        VOID_LORD("Владыка Бездны", 450, 22, 14, "🌀", ChatColor.DARK_PURPLE);

        public final String name;
        public final int hp, atk, def;
        public final String icon;
        public final ChatColor color;

        EnemyType(String name, int hp, int atk, int def, String icon, ChatColor color) {
            this.name = name; this.hp = hp; this.atk = atk; this.def = def;
            this.icon = icon; this.color = color;
        }
    }

    // ===== РЕСУРСЫ =====
    public enum ResourceType {
        WOOD("Древесина", "🪵", Material.OAK_LOG, 1),
        STONE("Камень", "🪨", Material.STONE, 1),
        IRON("Железо", "⛓", Material.IRON_INGOT, 3),
        GOLD("Золото", "🪙", Material.GOLD_INGOT, 5),
        DIAMOND("Алмаз", "💎", Material.DIAMOND, 10),
        NETHERITE("Незерит", "🔮", Material.NETHERITE_SCRAP, 20),
        ENDER_PEARL("Жемчуг Энда", "🟣", Material.ENDER_PEARL, 8),
        LEATHER("Кожа", "🧵", Material.LEATHER, 1),
        BONE("Кость", "🦴", Material.BONE, 1),
        GUNPOWDER("Порох", "💥", Material.GUNPOWDER, 2),
        SHULKER_SHELL("Панцирь шалкера", "🐚", Material.SHULKER_SHELL, 15),
        PHANTOM_MEMBRANE("Мембрана", "🪶", Material.PHANTOM_MEMBRANE, 5);

        public final String name, icon;
        public final Material material;
        public final int repValue;

        ResourceType(String name, String icon, Material mat, int repValue) {
            this.name = name; this.icon = icon; this.material = mat; this.repValue = repValue;
        }
    }

    // ===== СЕТОВЫЕ ПРЕДМЕТЫ =====
    public enum SetPiece {
        // Лесной разведчик
        SCOUT_HOOD("Капюшон разведчика", Zone.DARK_FOREST, "SCOUT", "helmet", Material.LEATHER_HELMET, 2, 20),
        SCOUT_TUNIC("Туника разведчика", Zone.DARK_FOREST, "SCOUT", "chestplate", Material.LEATHER_CHESTPLATE, 2, 25),
        SCOUT_LEGS("Штаны разведчика", Zone.DARK_FOREST, "SCOUT", "leggings", Material.LEATHER_LEGGINGS, 2, 20),
        SCOUT_BOOTS("Сапоги разведчика", Zone.DARK_FOREST, "SCOUT", "boots", Material.LEATHER_BOOTS, 2, 15),

        // Шахтёр
        MINER_HELM("Шахтёрская каска", Zone.DEEP_MINES, "MINER", "helmet", Material.IRON_HELMET, 3, 30),
        MINER_CHEST("Шахтёрская броня", Zone.DEEP_MINES, "MINER", "chestplate", Material.IRON_CHESTPLATE, 3, 35),
        MINER_LEGS("Шахтёрские поножи", Zone.DEEP_MINES, "MINER", "leggings", Material.IRON_LEGGINGS, 3, 30),
        MINER_BOOTS("Шахтёрские ботинки", Zone.DEEP_MINES, "MINER", "boots", Material.IRON_BOOTS, 3, 20),

        // Древний страж
        ANCIENT_HELM("Шлем стража", Zone.ANCIENT_RUINS, "ANCIENT", "helmet", Material.GOLDEN_HELMET, 4, 40),
        ANCIENT_CHEST("Кираса стража", Zone.ANCIENT_RUINS, "ANCIENT", "chestplate", Material.GOLDEN_CHESTPLATE, 4, 45),
        ANCIENT_LEGS("Поножи стража", Zone.ANCIENT_RUINS, "ANCIENT", "leggings", Material.GOLDEN_LEGGINGS, 4, 40),
        ANCIENT_BOOTS("Сапоги стража", Zone.ANCIENT_RUINS, "ANCIENT", "boots", Material.GOLDEN_BOOTS, 4, 30),

        // Пламя Незера
        NETHER_HELM("Шлем пламени", Zone.NETHER_WASTES, "NETHER", "helmet", Material.CHAINMAIL_HELMET, 5, 50),
        NETHER_CHEST("Броня пламени", Zone.NETHER_WASTES, "NETHER", "chestplate", Material.CHAINMAIL_CHESTPLATE, 5, 55),
        NETHER_LEGS("Поножи пламени", Zone.NETHER_WASTES, "NETHER", "leggings", Material.CHAINMAIL_LEGGINGS, 5, 50),
        NETHER_BOOTS("Сапоги пламени", Zone.NETHER_WASTES, "NETHER", "boots", Material.CHAINMAIL_BOOTS, 5, 35),

        // Стража льда
        FROST_HELM("Шлем льда", Zone.FROZEN_TUNDRA, "FROST", "helmet", Material.DIAMOND_HELMET, 6, 70),
        FROST_CHEST("Кираса льда", Zone.FROZEN_TUNDRA, "FROST", "chestplate", Material.DIAMOND_CHESTPLATE, 6, 80),
        FROST_LEGS("Поножи льда", Zone.FROZEN_TUNDRA, "FROST", "leggings", Material.DIAMOND_LEGGINGS, 6, 70),
        FROST_BOOTS("Сапоги льда", Zone.FROZEN_TUNDRA, "FROST", "boots", Material.DIAMOND_BOOTS, 6, 50),

        // Странник Бездны
        VOID_HELM("Шлем Бездны", Zone.VOID_EDGE, "VOID", "helmet", Material.NETHERITE_HELMET, 7, 100),
        VOID_CHEST("Кираса Бездны", Zone.VOID_EDGE, "VOID", "chestplate", Material.NETHERITE_CHESTPLATE, 7, 120),
        VOID_LEGS("Поножи Бездны", Zone.VOID_EDGE, "VOID", "leggings", Material.NETHERITE_LEGGINGS, 7, 100),
        VOID_BOOTS("Сапоги Бездны", Zone.VOID_EDGE, "VOID", "boots", Material.NETHERITE_BOOTS, 7, 70);

        public final String name;
        public final Zone zone;
        public final String setId, slot;
        public final Material material;
        public final int rarity;  // 1-7
        public final int repCost;

        SetPiece(String name, Zone zone, String setId, String slot, Material mat, int rarity, int repCost) {
            this.name = name; this.zone = zone; this.setId = setId; this.slot = slot;
            this.material = mat; this.rarity = rarity; this.repCost = repCost;
        }
    }

    // ===== КЛАССЫ =====
    public enum ClassType {
        WARRIOR("Воин", "⚔", "Мощные атаки ближнего боя", 120, 10, 4),
        RANGER("Следопыт", "🏹", "Меткие выстрелы и криты", 90, 12, 2),
        MAGE("Маг", "🔮", "Разрушительная магия", 70, 16, 1),
        PALADIN("Паладин", "🛡", "Защита и исцеление", 140, 8, 5),
        ASSASSIN("Убийца", "🗡", "Скорость и яды", 85, 14, 2);

        public final String name, icon, desc;
        public final int baseHp, baseAtk, baseDef;

        ClassType(String name, String icon, String desc, int hp, int atk, int def) {
            this.name = name; this.icon = icon; this.desc = desc;
            this.baseHp = hp; this.baseAtk = atk; this.baseDef = def;
        }
    }

    // ===== СЛУЧАЙНЫЕ СОБЫТИЯ =====
    public static final String[][] EVENTS = {
        {"trap", "🪤 Ловушка!", "Вы наступили на скрытую ловушку!", "dodge/disable"},
        {"treasure", "💰 Сокровище!", "Вы нашли заброшенный сундук!", "open/leave"},
        {"merchant", "🧳 Торговец", "Странствующий торговец предлагает товары.", "trade/pass"},
        {"shrine", "⛩ Святилище", "Древнее святилище излучает силу.", "pray/pass"},
        {"camp", "🏕 Лагерь", "Вы нашли безопасное место для отдыха.", "rest/continue"},
        {"riddle", "🧩 Загадка", "Каменная табличка с загадкой.", "solve/pass"},
        {"ambush", "⚠ Засада!", "Из засады выпрыгивают враги!", "fight/flee"},
        {"cave", "🕳 Пещера", "Тёмная пещера уходит вглубь.", "enter/pass"},
    };

    // ===== СКИЛЛЫ ПО КЛАССАМ =====
    public static Map<String, List<Skill>> getSkills() {
        Map<String, List<Skill>> map = new HashMap<>();

        map.put("WARRIOR", Arrays.asList(
            new Skill("Мощный удар", "Удвоенный урон, -2 HP себе", 1, 0),
            new Skill("Берсерк", "+50% атаки на 3 хода, -5 защиты", 3, 15),
            new Skill("Круговой удар", "Урон всем врагам", 5, 20),
            new Skill("Казнь", "Мгновенное убийство если HP < 20%", 7, 30)
        ));

        map.put("RANGER", Arrays.asList(
            new Skill("Меткий выстрел", "Игнорирует 50% защиты", 1, 0),
            new Skill("Град стрел", "3 быстрых атаки по 60% урона", 3, 15),
            new Skill("Ядовитая стрела", "Урон + яд на 3 хода", 5, 20),
            new Skill("Выстрел в голову", "Крит x3, пропуск хода если мимо", 7, 30)
        ));

        map.put("MAGE", Arrays.asList(
            new Skill("Огненный шар", "Урон 150%, поджигает", 1, 0),
            new Skill("Ледяная стрела", "Урон 100%, замедляет врага", 3, 15),
            new Skill("Цепная молния", "Урон всем + шанс оглушить", 5, 25),
            new Skill("Метеор", "Колоссальный урон 300%, кулдаун 3 хода", 7, 35)
        ));

        map.put("PALADIN", Arrays.asList(
            new Skill("Святое исцеление", "Восстанавливает 30% HP", 1, 0),
            new Skill("Божественный щит", "Блокирует весь урон на 2 хода", 3, 20),
            new Skill("Кара небес", "Урон 200% нежити", 5, 25),
            new Skill("Аура света", "Реген 5% HP каждый ход, 5 ходов", 7, 35)
        ));

        map.put("ASSASSIN", Arrays.asList(
            new Skill("Удар в спину", "Урон 200% если первый ход", 1, 0),
            new Skill("Ядовитый клинок", "Урон + яд 5% HP на 3 хода", 3, 15),
            new Skill("Шаг в тень", "Исчезновение на 1 ход, след. атака x2", 5, 20),
            new Skill("Смертельный удар", "Урон 250% если враг отравлен", 7, 30)
        ));

        return map;
    }

    public static class Skill {
        public final String name;
        public final String desc;
        public final int requiredLevel;
        public final int energyCost;

        public Skill(String name, String desc, int level, int cost) {
            this.name = name; this.desc = desc; this.requiredLevel = level; this.energyCost = cost;
        }
    }
}
