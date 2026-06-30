package ru.example.vkchatartifacts.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ArtifactFactory {
    
    public static final String[] BUFFS = {
        // Оригинальные баффы (53)
        "HEALTH", "DAMAGE", "SPEED", "REGENERATION", "VAMPIRISM", "THORNS", "FIRE_RESISTANCE", "LEVITATION", 
        "CRITICAL", "ABSORPTION", "NIGHT_VISION", "HASTE", "WATER_BREATHING", "JUMP_BOOST", "LUCK", "WITHER_TOUCH", 
        "POISON_STRIKE", "FREEZE_AURA", "LIGHTNING_STRIKE", "GHOST_WALK", "TRUE_STRIKE", "STEEL_SKIN", "AQUATIC_SPEED", 
        "FIRE_WALKER", "XP_BOOST", "DOUBLE_JUMP", "DODGE_CHANCE", "KNOCKBACK_RESIST", "MAX_HEALTH_BOOST",
        "HERO_OF_VILLAGE", "STRENGTH_BOOST", "RESISTANCE", "SATURATION", "LUCK_OF_THE_SEA",
        "SOUL_DRAIN", "FROST_BITE", "MANA_SHIELD", "TELEKINESIS", "ENDER_SHIFT",
        "BERSERKER", "ARCANE_BURST", "SHADOW_STEP", "LIFESTEAL_AURA", "IRON_WILL",
        "TRAP_SENSE", "TREASURE_HUNTER", "FLAME_TONGUE", "WIND_WALKER", "ECHO_STRIKE",
        "SOUL_SHIELD", "FIRE_RESISTANCE_AURA", "XP_MAGNET", "LOOT_FIND",
        // Новые баффы (37)
        "DARK_PACT", "BLOOD_OATH", "VOID_TOUCH", "STARFALL", "ICE_SHIELD",
        "STONE_SKIN", "PHANTOM_STRIKE", "MAGIC_AMPLIFY", "ARROW_DEFLECT", "FIRE_ENCHANT",
        "ICE_ENCHANT", "POISON_ENCHANT", "WITHER_ENCHANT", "SMITE_ENCHANT", "BANE_ENCHANT",
        "SHARPNESS_ENCHANT", "PROTECTION_ENCHANT", "FIRE_PROTECTION", "BLAST_PROTECTION",
        "PROJECTILE_PROTECTION", "FEATHER_FALLING", "AQUA_AFFINITY", "RESPIRATION",
        "DEPTH_STRIDER", "FROST_WALKER", "SOUL_SPEED", "SWIFT_SNEAK", "LOOTING_ENCHANT",
        "FORTUNE_ENCHANT", "SILK_TOUCH", "INFINITY_ENCHANT", "MENDING_ENCHANT",
        "UNBREAKING_ENCHANT", "SWEEPING_EDGE", "CHANNELING", "RIPTIDE", "LOYALTY",
        "MULTISHOT", "PIERCING", "QUICK_CHARGE"
    };

    // ═══ 35 УНИКАЛЬНЫХ АРТЕФАКТОВ ═══
    public static final String[][] NAMED_ARTIFACTS = {
        // Название, материал, бафф, уровень, редкость
        {"✨ Перо Феникса", "TOTEM_OF_UNDYING", "REVIVAL", "5", "mythic"},
        {"👑 Корона Бездны", "NETHER_STAR", "ABYSSAL_POWER", "5", "mythic"},
        {"❤️ Сердце Дракона", "DRAGON_BREATH", "DRAGON_BLOOD", "5", "mythic"},
        {"💎 Осколок Вечности", "DIAMOND", "MAX_HEALTH_BOOST", "4", "legendary"},
        {"🔥 Пылающий Клинок", "BLAZE_POWDER", "FLAME_TONGUE", "4", "legendary"},
        {"❄️ Ледяное Сердце", "GHAST_TEAR", "FROST_BITE", "4", "legendary"},
        {"⚡ Молния в Бутылке", "END_CRYSTAL", "LIGHTNING_STRIKE", "4", "legendary"},
        {"🌙 Лунный Камень", "CONDUIT", "NIGHT_VISION", "3", "epic"},
        {"🌊 Приливный Талисман", "HEART_OF_THE_SEA", "WATER_BREATHING", "3", "epic"},
        {"🍀 Клевер Удачи", "RABBIT_FOOT", "LUCK", "3", "epic"},
        {"🛡️ Щит Предков", "SCUTE", "STEEL_SKIN", "3", "epic"},
        {"👁️ Глаз Провидца", "ENDER_ROD", "TRAP_SENSE", "3", "epic"},
        {"🌿 Травяной Эликсир", "CHORUS_FRUIT", "REGENERATION", "3", "epic"},
        {"💀 Череп Силы", "WITHER_SKELETON_SKULL", "WITHER_TOUCH", "2", "rare"},
        {"🕸️ Нить Паутины", "COBWEB", "SHADOW_STEP", "2", "rare"},
        {"🧲 Магнит Опыта", "MAGMA_CREAM", "XP_MAGNET", "2", "rare"},
        {"🔮 Хрустальный Шар", "GLASS", "ARCANE_BURST", "2", "rare"},
        {"🧤 Рукавицы Гиганта", "IRON_INGOT", "KNOCKBACK_RESIST", "2", "rare"},
        {"🥾 Сапоги Скорохода", "LEATHER", "SPEED", "2", "rare"},
        {"📿 Ожерелье Вампира", "AMETHYST_SHARD", "VAMPIRISM", "2", "rare"},
        {"🎯 Прицел Снайпера", "ARROW", "CRITICAL", "2", "rare"},
        {"🪶 Перо Птицы", "FEATHER", "DOUBLE_JUMP", "2", "rare"},
        {"🧪 Зелье Силы", "POTION", "STRENGTH_BOOST", "1", "common"},
        {"🛡️ Малый Щит", "SHIELD", "RESISTANCE", "1", "common"},
        {"🥾 Ботинки Бегуна", "CHAINMAIL_BOOTS", "SPEED", "1", "common"},
        {"🧤 Перчатки Кузнеца", "IRON_NUGGET", "DAMAGE", "1", "common"},
        {"📿 Амулет Здоровья", "GOLDEN_APPLE", "HEALTH", "1", "common"},
        {"🔮 Малый Кристалл", "AMETHYST_SHARD", "ABSORPTION", "1", "common"},
        {"🎯 Меткая Рука", "BOW", "CRITICAL", "1", "common"},
        {"🍀 Крошечный Клевер", "SEEDS", "LUCK", "1", "common"},
        {"🔥 Огненный Камень", "FLINT_AND_STEEL", "FIRE_RESISTANCE", "1", "common"},
        {"💧 Капля Жизни", "POTION", "REGENERATION", "1", "common"},
        {"🌿 Травяной Мешочек", "WHEAT", "SATURATION", "1", "common"},
        {"🧲 Малый Магнит", "IRON_INGOT", "TREASURE_HUNTER", "1", "common"},
        {"🌙 Ночной Камень", "INK_SAC", "NIGHT_VISION", "1", "common"},
    };
    public static final String[] CURSES = {
        // Оригинальные проклятия (13)
        "SLOWNESS", "WEAKNESS", "HUNGER", "FRAGILE", "BLINDNESS", "VULNERABILITY", 
        "DECAY", "SILENCE", "BLOODLETTING", "ANCHOR", "NIGHTMARE", "GREED", "CHAOS",
        // Новые проклятия (12)
        "CURSED_LUCK", "CURSED_XP", "CURSED_SPEED", "CURSED_DAMAGE", "CURSED_DEFENSE",
        "CURSED_VISION", "CURSED_HUNGER", "CURSED_WEIGHT", "CURSED_FIRE", "CURSED_DROWNING",
        "CURSED_FALL", "CURSED_DARKNESS"
    };

    public static ItemStack generateArtifact(VKChatArtifactsPlugin plugin, boolean isMythic) {
        // Шанс на именованный артефакт (20%)
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            return generateNamedArtifact(plugin);
        }

        Material[] possibleMats = {Material.NETHER_STAR, Material.TOTEM_OF_UNDYING, Material.HEART_OF_THE_SEA, Material.DRAGON_BREATH, Material.GHAST_TEAR, Material.BLAZE_POWDER, Material.RABBIT_FOOT, Material.MAGMA_CREAM, Material.END_CRYSTAL, Material.CONDUIT, Material.SCUTE, Material.END_ROD, Material.CHORUS_FRUIT};
        Material mat = possibleMats[ThreadLocalRandom.current().nextInt(possibleMats.length)];
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        
        String buff = BUFFS[ThreadLocalRandom.current().nextInt(BUFFS.length)];
        String curse = isMythic ? "NONE" : CURSES[ThreadLocalRandom.current().nextInt(CURSES.length)];
        int level = ThreadLocalRandom.current().nextInt(3) + 1; 
        
        if (isMythic) level = 5;

        String specialArtifact = null;
        if (isMythic) {
            double roll = ThreadLocalRandom.current().nextDouble() * 100;
            if (roll < 0.3) {
                specialArtifact = "DRAGON_HEART";
                buff = "DRAGON_BLOOD";
                mat = Material.DRAGON_BREATH;
            } else if (roll < 0.8) {
                specialArtifact = "PHOENIX_FEATHER";
                buff = "REVIVAL";
                mat = Material.TOTEM_OF_UNDYING;
            } else if (roll < 2.3) {
                specialArtifact = "ABYSSAL_CROWN";
                buff = "ABYSSAL_POWER";
                mat = Material.NETHER_STAR;
            }
        }

        String name = "";
        ChatColor color;
        if (specialArtifact != null) {
            switch (specialArtifact) {
                case "PHOENIX_FEATHER":
                    name = "✨ Перо Феникса ✨";
                    color = ChatColor.GOLD;
                    break;
                case "ABYSSAL_CROWN":
                    name = "✨ Корона Бездны ✨";
                    color = ChatColor.DARK_PURPLE;
                    break;
                case "DRAGON_HEART":
                    name = "✨ Сердце Дракона ✨";
                    color = ChatColor.RED;
                    break;
                default:
                    name = "✨ МИФИЧЕСКАЯ РЕЛИКВИЯ ✨";
                    color = ChatColor.LIGHT_PURPLE;
            }
        } else if (isMythic) {
            name = "✨ МИФИЧЕСКАЯ РЕЛИКВИЯ ✨";
            color = ChatColor.LIGHT_PURPLE;
        } else if (level == 3) {
            name = "Эпический Артефакт";
            color = ChatColor.DARK_PURPLE;
        } else if (level == 2) {
            name = "Редкий Артефакт";
            color = ChatColor.BLUE;
        } else {
            name = "Необычный Артефакт";
            color = ChatColor.GREEN;
        }

        meta.setDisplayName(color + "" + ChatColor.BOLD + name);
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Древняя вещь, источающая магию.");
        lore.add("");
        
        switch (buff) {
            case "HEALTH": lore.add(ChatColor.GREEN + "➕ Максимальное Здоровье +" + (level * 2)); break;
            case "DAMAGE": lore.add(ChatColor.GREEN + "➕ Урон в ближнем бою +" + level); break;
            case "SPEED": lore.add(ChatColor.GREEN + "➕ Скорость передвижения +" + (level * 10) + "%"); break;
            case "REGENERATION": lore.add(ChatColor.GREEN + "➕ Пассивная Регенерация " + level + " ур."); break;
            case "VAMPIRISM": lore.add(ChatColor.GREEN + "➕ Вампиризм " + (level * 10) + "%"); break;
            case "THORNS": lore.add(ChatColor.GREEN + "➕ Отражение урона " + level + " ур."); break;
            case "FIRE_RESISTANCE": lore.add(ChatColor.GREEN + "➕ Иммунитет к огню"); break;
            case "LEVITATION": lore.add(ChatColor.GREEN + "➕ Иммунитет к урону от падения"); break;
            case "CRITICAL": lore.add(ChatColor.GREEN + "➕ Шанс крита " + (level * 5) + "%"); break;
            case "ABSORPTION": lore.add(ChatColor.GREEN + "➕ Абсорбция " + level + " ур."); break;
            case "NIGHT_VISION": lore.add(ChatColor.GREEN + "➕ Ночное зрение"); break;
            case "HASTE": lore.add(ChatColor.GREEN + "➕ Спешка " + level + " ур."); break;
            case "WATER_BREATHING": lore.add(ChatColor.GREEN + "➕ Дыхание под водой"); break;
            case "JUMP_BOOST": lore.add(ChatColor.GREEN + "➕ Мощный прыжок " + level + " ур."); break;
            case "LUCK": lore.add(ChatColor.GREEN + "➕ Удача " + level + " ур."); break;
            case "WITHER_TOUCH": lore.add(ChatColor.GREEN + "➕ Касание Иссушителя " + level + " ур."); break;
            case "POISON_STRIKE": lore.add(ChatColor.GREEN + "➕ Ядовитый Удар " + level + " ур."); break;
            case "FREEZE_AURA": lore.add(ChatColor.GREEN + "➕ Ледяная Аура " + level + " ур."); break;
            case "LIGHTNING_STRIKE": lore.add(ChatColor.GREEN + "➕ Удар Молнии " + level + " ур."); break;
            case "GHOST_WALK": lore.add(ChatColor.GREEN + "➕ Призрачный Шаг (Невидимость)"); break;
            case "TRUE_STRIKE": lore.add(ChatColor.GREEN + "➕ Истинный Удар (Пробивание брони)"); break;
            case "STEEL_SKIN": lore.add(ChatColor.GREEN + "➕ Стальная Кожа (Броня +" + level + ")"); break;
            case "AQUATIC_SPEED": lore.add(ChatColor.GREEN + "➕ Скорость под водой " + level + " ур."); break;
            case "FIRE_WALKER": lore.add(ChatColor.GREEN + "➕ Огненный Шаг (Хождение по лаве)"); break;
            case "XP_BOOST": lore.add(ChatColor.GREEN + "➕ Бонус к опыту +" + (level * 15) + "%"); break;
            case "DOUBLE_JUMP": lore.add(ChatColor.GREEN + "➕ Двойной прыжок"); break;
            case "DODGE_CHANCE": lore.add(ChatColor.GREEN + "➕ Шанс уклонения " + (level * 5) + "%"); break;
            case "KNOCKBACK_RESIST": lore.add(ChatColor.GREEN + "➕ Сопротивление отбрасыванию " + (level * 30) + "%"); break;
            case "MAX_HEALTH_BOOST": lore.add(ChatColor.GREEN + "➕ Колоссальное здоровье +" + (level * 10)); break;
            
            // Новые баффы
            case "HERO_OF_VILLAGE": lore.add(ChatColor.GREEN + "➕ Герой Деревни " + level + " ур."); break;
            case "STRENGTH_BOOST": lore.add(ChatColor.GREEN + "➕ Сила " + level + " ур."); break;
            case "RESISTANCE": lore.add(ChatColor.GREEN + "➕ Сопротивление " + level + " ур."); break;
            case "SATURATION": lore.add(ChatColor.GREEN + "➕ Вечная сытость"); break;
            case "LUCK_OF_THE_SEA": lore.add(ChatColor.GREEN + "➕ Морская удача " + level + " ур."); break;
            case "SOUL_DRAIN": lore.add(ChatColor.GREEN + "➕ Вытягивание души (лечит при убийстве) +" + level); break;
            case "FROST_BITE": lore.add(ChatColor.GREEN + "➕ Морозный укус " + level + " ур."); break;
            case "MANA_SHIELD": lore.add(ChatColor.GREEN + "➕ Мана-щит (поглощает " + (level * 10) + "% урона)"); break;
            case "TELEKINESIS": lore.add(ChatColor.GREEN + "➕ Телекинез (подбор предметов на расст.)"); break;
            case "ENDER_SHIFT": lore.add(ChatColor.GREEN + "➕ Эндер-сдвиг (телепорт ПКМ в воздухе)"); break;
            case "BERSERKER": lore.add(ChatColor.GREEN + "➕ Ярость " + level + " ур. (урон растет с потерей ХП)"); break;
            case "ARCANE_BURST": lore.add(ChatColor.GREEN + "➕ Магический взрыв " + level + " ур."); break;
            case "SHADOW_STEP": lore.add(ChatColor.GREEN + "➕ Теневой шаг (ускорение после уклонения)"); break;
            case "LIFESTEAL_AURA": lore.add(ChatColor.GREEN + "➕ Аура вампиризма " + level + " ур."); break;
            case "IRON_WILL": lore.add(ChatColor.GREEN + "➕ Железная воля " + level + " ур."); break;
            case "TRAP_SENSE": lore.add(ChatColor.GREEN + "➕ Чувство ловушки " + level + " ур."); break;
            case "TREASURE_HUNTER": lore.add(ChatColor.GREEN + "➕ Охотник за сокровищами +" + (level * 10) + "%"); break;
            case "FLAME_TONGUE": lore.add(ChatColor.GREEN + "➕ Пылающий язык " + level + " ур."); break;
            case "WIND_WALKER": lore.add(ChatColor.GREEN + "➕ Шагающий по ветру " + level + " ур."); break;
            case "ECHO_STRIKE": lore.add(ChatColor.GREEN + "➕ Удар-эхо " + level + " ур. (шанс двойного удара)"); break;
            case "SOUL_SHIELD": lore.add(ChatColor.GREEN + "➕ Щит Души (Абсорбция II при HP < 30%)"); break;
            case "FIRE_RESISTANCE_AURA": lore.add(ChatColor.GREEN + "➕ Аура Огнестойкости (союзникам, 5 блоков)"); break;
            case "XP_MAGNET": lore.add(ChatColor.GREEN + "➕ Магнит Опыта +" + (level * 50) + "%"); break;
            case "LOOT_FIND": lore.add(ChatColor.GREEN + "➕ Поиск Добычи +" + (level * 25) + "% редких дропов"); break;
            case "REVIVAL": lore.add(ChatColor.GOLD + "➕ Возрождение (50% HP при смерти, КД 10 мин)"); break;
            case "ABYSSAL_POWER": lore.add(ChatColor.DARK_PURPLE + "➕ Сила Бездны (+10 урон, невидимость при HP < 30%)"); break;
            case "DRAGON_BLOOD": lore.add(ChatColor.RED + "➕ Кровь Дракона (+10 HP, Регенерация II, +50% огненный урон)"); break;

            // Новые баффы (37)
            case "DARK_PACT": lore.add(ChatColor.DARK_RED + "➕ Тёмный пакт (+30% урон, -15% HP)"); break;
            case "BLOOD_OATH": lore.add(ChatColor.RED + "➕ Кровавая клятва (+20% вампиризм, -10% защиты)"); break;
            case "VOID_TOUCH": lore.add(ChatColor.DARK_PURPLE + "➕ Прикосновение пустоты (шанс телепорта врага)"); break;
            case "STARFALL": lore.add(ChatColor.GOLD + "➕ Падение звезды (шанс метеорита при ударе)"); break;
            case "ICE_SHIELD": lore.add(ChatColor.AQUA + "➕ Ледяной щит (замедляет атакующих)"); break;
            case "STONE_SKIN": lore.add(ChatColor.GRAY + "➕ Каменная кожа (+50% защиты, -20% скорости)"); break;
            case "PHANTOM_STRIKE": lore.add(ChatColor.LIGHT_PURPLE + "➕ Призрачный удар (шанс игнорировать броню)"); break;
            case "MAGIC_AMPLIFY": lore.add(ChatColor.BLUE + "➕ Усиление магии (+40% к зельям)"); break;
            case "ARROW_DEFLECT": lore.add(ChatColor.YELLOW + "➕ Отражение стрел (30% шанс)"); break;
            case "FIRE_ENCHANT": lore.add(ChatColor.RED + "➕ Огненное зачарование (поджигает)"); break;
            case "ICE_ENCHANT": lore.add(ChatColor.AQUA + "➕ Ледяное зачарование (замораживает)"); break;
            case "POISON_ENCHANT": lore.add(ChatColor.GREEN + "➕ Ядовитое зачарование (отравляет)"); break;
            case "WITHER_ENCHANT": lore.add(ChatColor.DARK_GRAY + "➕ Иссушающее зачарование (иссушает)"); break;
            case "SMITE_ENCHANT": lore.add(ChatColor.GOLD + "➕ Кара (+100% урона по нежити)"); break;
            case "BANE_ENCHANT": lore.add(ChatColor.DARK_GREEN + "➕ Гибель членистоногих (+100% урона по паукам)"); break;
            case "SHARPNESS_ENCHANT": lore.add(ChatColor.WHITE + "➕ Острота (+20% урона)"); break;
            case "PROTECTION_ENCHANT": lore.add(ChatColor.BLUE + "➕ Защита (-20% получаемого урона)"); break;
            case "FIRE_PROTECTION": lore.add(ChatColor.RED + "➕ Огненная защита (-50% урона от огня)"); break;
            case "BLAST_PROTECTION": lore.add(ChatColor.YELLOW + "➕ Взрывозащита (-50% урона от взрывов)"); break;
            case "PROJECTILE_PROTECTION": lore.add(ChatColor.GREEN + "➕ Защита от снарядов (-50%)"); break;
            case "FEATHER_FALLING": lore.add(ChatColor.WHITE + "➕ Плавное падение (-50% урона от падения)"); break;
            case "AQUA_AFFINITY": lore.add(ChatColor.AQUA + "➕ Подводная скорость (+50%)"); break;
            case "RESPIRATION": lore.add(ChatColor.BLUE + "➕ Подводное дыхание"); break;
            case "DEPTH_STRIDER": lore.add(ChatColor.AQUA + "➕ Ходьба по воде"); break;
            case "FROST_WALKER": lore.add(ChatColor.WHITE + "➕ Хождение по воде"); break;
            case "SOUL_SPEED": lore.add(ChatColor.GOLD + "➕ Скорость по песку душ"); break;
            case "SWIFT_SNEAK": lore.add(ChatColor.GREEN + "➕ Скрытность (+30% скорости при приседании)"); break;
            case "LOOTING_ENCHANT": lore.add(ChatColor.GOLD + "➕ Грабеж (+30% дропа)"); break;
            case "FORTUNE_ENCHANT": lore.add(ChatColor.YELLOW + "➕ Удача (+30% ресурсов)"); break;
            case "SILK_TOUCH": lore.add(ChatColor.WHITE + "➕ Шёлковое касание"); break;
            case "INFINITY_ENCHANT": lore.add(ChatColor.AQUA + "➕ Бесконечность (не расходует стрелы)"); break;
            case "MENDING_ENCHANT": lore.add(ChatColor.GREEN + "➕ Починка (восстанавливает прочность)"); break;
            case "UNBREAKING_ENCHANT": lore.add(ChatColor.BLUE + "➕ Прочность (+100% к прочности)"); break;
            case "SWEEPING_EDGE": lore.add(ChatColor.RED + "➕ Разящий клинок (+50% к AoE урону)"); break;
            case "CHANNELING": lore.add(ChatColor.YELLOW + "➕ Канал (молния при ударе трезубцем)"); break;
            case "RIPTIDE": lore.add(ChatColor.AQUA + "➕ Буря (ускорение в воде/дожде)"); break;
            case "LOYALTY": lore.add(ChatColor.BLUE + "➕ Верность (трезубец возвращается)"); break;
            case "MULTISHOT": lore.add(ChatColor.GREEN + "➕ Тройной выстрел"); break;
            case "PIERCING": lore.add(ChatColor.YELLOW + "➕ Пробивание (стрела проходит через цели)"); break;
            case "QUICK_CHARGE": lore.add(ChatColor.WHITE + "➕ Быстрая зарядка"); break;
        }
        
        if (!isMythic) {
            switch (curse) {
                case "SLOWNESS": lore.add(ChatColor.RED + "☠ Проклятие: Замедление I"); break;
                case "WEAKNESS": lore.add(ChatColor.RED + "☠ Проклятие: Слабость I"); break;
                case "HUNGER": lore.add(ChatColor.RED + "☠ Проклятие: Сильный Голод"); break;
                case "FRAGILE": lore.add(ChatColor.RED + "☠ Проклятие: Хрупкость (Ломается через 24ч)"); break;
                case "BLINDNESS": lore.add(ChatColor.RED + "☠ Проклятие: Периодическая слепота"); break;
                case "VULNERABILITY": lore.add(ChatColor.RED + "☠ Проклятие: Уязвимость (+20% урон)"); break;
                case "DECAY": lore.add(ChatColor.RED + "☠ Проклятие: Разложение (потеря ХП)"); break;
                case "SILENCE": lore.add(ChatColor.RED + "☠ Проклятие: Молчание (блок предметов)"); break;
                case "BLOODLETTING": lore.add(ChatColor.RED + "☠ Проклятие: Кровопускание (потеря ХП)"); break;
                case "ANCHOR": lore.add(ChatColor.RED + "☠ Проклятие: Якорь (нет телепорта)"); break;
                case "NIGHTMARE": lore.add(ChatColor.RED + "☠ Проклятие: Кошмар (случайная тошнота)"); break;
                case "GREED": lore.add(ChatColor.RED + "☠ Проклятие: Жадность (+50% золото/реп, -30% HP)"); break;
                case "CHAOS": lore.add(ChatColor.RED + "☠ Проклятие: Хаос (случайные зелья каждые 10 сек)"); break;

                // Новые проклятия (12)
                case "CURSED_LUCK": lore.add(ChatColor.RED + "☠ Проклятие: Проклятая удача (-50% к дропу)"); break;
                case "CURSED_XP": lore.add(ChatColor.RED + "☠ Проклятие: Проклятый опыт (-50% к опыту)"); break;
                case "CURSED_SPEED": lore.add(ChatColor.RED + "☠ Проклятие: Проклятая скорость (-20% скорости)"); break;
                case "CURSED_DAMAGE": lore.add(ChatColor.RED + "☠ Проклятие: Проклятый урон (-20% к урону)"); break;
                case "CURSED_DEFENSE": lore.add(ChatColor.RED + "☠ Проклятие: Проклятая защита (-20% к защите)"); break;
                case "CURSED_VISION": lore.add(ChatColor.RED + "☠ Проклятие: Проклятое зрение (периодическая слепота)"); break;
                case "CURSED_HUNGER": lore.add(ChatColor.RED + "☠ Проклятие: Проклятый голод (быстрый голод)"); break;
                case "CURSED_WEIGHT": lore.add(ChatColor.RED + "☠ Проклятие: Проклятый вес (замедление при полном инвентаре)"); break;
                case "CURSED_FIRE": lore.add(ChatColor.RED + "☠ Проклятие: Проклятый огонь (периодическое горение)"); break;
                case "CURSED_DROWNING": lore.add(ChatColor.RED + "☠ Проклятие: Проклятое утопление (урон в воде)"); break;
                case "CURSED_FALL": lore.add(ChatColor.RED + "☠ Проклятие: Проклятое падение (+50% урона от падения)"); break;
                case "CURSED_DARKNESS": lore.add(ChatColor.RED + "☠ Проклятие: Проклятая тьма (слепота в темноте)"); break;
            }
        } else {
            lore.add(ChatColor.AQUA + "✨ Эта реликвия не имеет проклятий.");
            lore.add(ChatColor.AQUA + "✨ Привязана к душе (Не выпадает при смерти).");
        }
        
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Работает, если находится в инвентаре.");
        
        meta.setLore(lore);
        
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_artifact"), PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buff_type"), PersistentDataType.STRING, buff);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buff_level"), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "curse_type"), PersistentDataType.STRING, curse);
        if (isMythic || curse.equals("FRAGILE")) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_mythic"), PersistentDataType.INTEGER, isMythic ? 1 : 0);
            if (curse.equals("FRAGILE")) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "expire_time"), PersistentDataType.LONG, System.currentTimeMillis() + 86400000L); // 24 часа
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Генерация именованного артефакта из списка 35 штук
     */
    public static ItemStack generateNamedArtifact(VKChatArtifactsPlugin plugin) {
        String[] artDef = NAMED_ARTIFACTS[ThreadLocalRandom.current().nextInt(NAMED_ARTIFACTS.length)];
        String name = artDef[0];
        Material mat = Material.valueOf(artDef[1]);
        String buff = artDef[2];
        int level = Integer.parseInt(artDef[3]);
        String rarity = artDef[4];

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        ChatColor color;
        String prefix;
        switch (rarity) {
            case "mythic":
                color = ChatColor.GOLD;
                prefix = "✨ ";
                break;
            case "legendary":
                color = ChatColor.DARK_PURPLE;
                prefix = "✦ ";
                break;
            case "epic":
                color = ChatColor.BLUE;
                prefix = "◆ ";
                break;
            case "rare":
                color = ChatColor.AQUA;
                prefix = "● ";
                break;
            default:
                color = ChatColor.GREEN;
                prefix = "• ";
                break;
        }

        meta.setDisplayName(color + "" + ChatColor.BOLD + prefix + name + " " + prefix);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Древний артефакт с уникальной силой.");
        lore.add("");
        lore.add(ChatColor.GREEN + "➕ " + getBuffDescription(buff, level));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Работает в инвентаре.");
        if (rarity.equals("mythic") || rarity.equals("legendary")) {
            lore.add(ChatColor.AQUA + "✨ Привязан к душе (не выпадает при смерти).");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_artifact"), PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buff_type"), PersistentDataType.STRING, buff);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "buff_level"), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "curse_type"), PersistentDataType.STRING, "NONE");
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "artifact_name"), PersistentDataType.STRING, name);

        if (rarity.equals("mythic")) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_mythic"), PersistentDataType.INTEGER, 1);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Получить описание баффа
     */
    public static String getBuffDescription(String buff, int level) {
        switch (buff) {
            case "HEALTH": return "Максимальное Здоровье +" + (level * 2);
            case "DAMAGE": return "Урон в ближнем бою +" + level;
            case "SPEED": return "Скорость +" + (level * 10) + "%";
            case "REGENERATION": return "Регенерация " + level + " ур.";
            case "VAMPIRISM": return "Вампиризм " + (level * 10) + "%";
            case "THORNS": return "Отражение урона " + level + " ур.";
            case "FIRE_RESISTANCE": return "Иммунитет к огню";
            case "LEVITATION": return "Иммунитет к падению";
            case "CRITICAL": return "Шанс крита " + (level * 5) + "%";
            case "ABSORPTION": return "Абсорбция " + level + " ур.";
            case "NIGHT_VISION": return "Ночное зрение";
            case "HASTE": return "Спешка " + level + " ур.";
            case "WATER_BREATHING": return "Дыхание под водой";
            case "JUMP_BOOST": return "Прыжок " + level + " ур.";
            case "LUCK": return "Удача " + level + " ур.";
            case "WITHER_TOUCH": return "Касание Иссушителя " + level + " ур.";
            case "POISON_STRIKE": return "Ядовитый Удар " + level + " ур.";
            case "FREEZE_AURA": return "Ледяная Аура " + level + " ур.";
            case "LIGHTNING_STRIKE": return "Удар Молнии " + level + " ур.";
            case "GHOST_WALK": return "Призрачный Шаг";
            case "TRUE_STRIKE": return "Истинный Удар";
            case "STEEL_SKIN": return "Стальная Кожа +" + level;
            case "AQUATIC_SPEED": return "Скорость в воде " + level;
            case "FIRE_WALKER": return "Хождение по лаве";
            case "XP_BOOST": return "Бонус опыта +" + (level * 15) + "%";
            case "DOUBLE_JUMP": return "Двойной прыжок";
            case "DODGE_CHANCE": return "Уклонение " + (level * 5) + "%";
            case "KNOCKBACK_RESIST": return "Сопротивление отбрасыванию " + (level * 30) + "%";
            case "MAX_HEALTH_BOOST": return "Здоровье +" + (level * 10);
            case "REVIVAL": return "Возрождение при смерти (50% HP)";
            case "ABYSSAL_POWER": return "Сила Бездны (+10 урон)";
            case "DRAGON_BLOOD": return "Кровь Дракона (+10 HP, Регенерация II)";
            default: return buff;
        }
    }
}
