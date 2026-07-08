package ru.example.vkchatartifacts.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatartifacts.VKChatArtifactsPlugin;
import ru.example.vkchatartifacts.effects.BuffEffectRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ArtifactFactory {
    
    public static final String[] BUFFS = {
        // Только реально работающие баффы (53)
        "HEALTH", "DAMAGE", "SPEED", "REGENERATION", "VAMPIRISM", "THORNS", "FIRE_RESISTANCE", "LEVITATION", 
        "CRITICAL", "ABSORPTION", "NIGHT_VISION", "HASTE", "WATER_BREATHING", "JUMP_BOOST", "LUCK", "WITHER_TOUCH", 
        "POISON_STRIKE", "FREEZE_AURA", "LIGHTNING_STRIKE", "GHOST_WALK", "TRUE_STRIKE", "STEEL_SKIN", "AQUATIC_SPEED", 
        "FIRE_WALKER", "XP_BOOST", "DOUBLE_JUMP", "DODGE_CHANCE", "KNOCKBACK_RESIST", "MAX_HEALTH_BOOST",
        "HERO_OF_VILLAGE", "STRENGTH_BOOST", "RESISTANCE", "SATURATION", "LUCK_OF_THE_SEA",
        "SOUL_DRAIN", "FROST_BITE", "MANA_SHIELD", "TELEKINESIS", "ENDER_SHIFT",
        "BERSERKER", "ARCANE_BURST", "SHADOW_STEP", "LIFESTEAL_AURA", "IRON_WILL",
        "TRAP_SENSE", "TREASURE_HUNTER", "FLAME_TONGUE", "WIND_WALKER", "ECHO_STRIKE",
        "SOUL_SHIELD", "FIRE_RESISTANCE_AURA", "XP_MAGNET", "LOOT_FIND"
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
        // Только реально работающие проклятия (13)
        "SLOWNESS", "WEAKNESS", "HUNGER", "FRAGILE", "BLINDNESS", "VULNERABILITY", 
        "DECAY", "SILENCE", "BLOODLETTING", "ANCHOR", "NIGHTMARE", "GREED", "CHAOS"
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
        lore.add(ChatColor.GREEN + "➕ " + getBuffDescription(buff, level));
        
        if (!isMythic) {
            // FIX #9: Используем BuffEffectRegistry для описания проклятий
            String curseLore = getCurseLore(plugin, curse);
            lore.add(curseLore);
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
        plugin.incrementArtifactsGenerated();
        return item;
    }

    /**
     * FIX #9: Получить описание проклятия через BuffEffectRegistry.
     * Fallback на switch для неизвестных проклятий.
     */
    private static String getCurseLore(VKChatArtifactsPlugin plugin, String curse) {
        BuffEffectRegistry registry = plugin.getBuffEffectRegistry();
        if (registry != null) {
            BuffEffectRegistry.CurseDef def = registry.getCurse(curse);
            if (def != null) {
                return ChatColor.RED + def.getDescription();
            }
        }
        // Fallback
        return getCurseLoreFallback(curse);
    }

    /**
     * Legacy fallback для описания проклятий.
     */
    private static String getCurseLoreFallback(String curse) {
        switch (curse) {
            case "SLOWNESS": return ChatColor.RED + "☠ Проклятие: Замедление I";
            case "WEAKNESS": return ChatColor.RED + "☠ Проклятие: Слабость I";
            case "HUNGER": return ChatColor.RED + "☠ Проклятие: Сильный Голод";
            case "FRAGILE": return ChatColor.RED + "☠ Проклятие: Хрупкость (Ломается через 24ч)";
            case "BLINDNESS": return ChatColor.RED + "☠ Проклятие: Периодическая слепота";
            case "VULNERABILITY": return ChatColor.RED + "☠ Проклятие: Уязвимость (+20% урон)";
            case "DECAY": return ChatColor.RED + "☠ Проклятие: Разложение (потеря ХП)";
            case "SILENCE": return ChatColor.RED + "☠ Проклятие: Молчание (блок предметов)";
            case "BLOODLETTING": return ChatColor.RED + "☠ Проклятие: Кровопускание (потеря ХП)";
            case "ANCHOR": return ChatColor.RED + "☠ Проклятие: Якорь (нет телепорта)";
            case "NIGHTMARE": return ChatColor.RED + "☠ Проклятие: Кошмар (случайная тошнота)";
            case "GREED": return ChatColor.RED + "☠ Проклятие: Жадность (+50% золото/реп, -30% HP)";
            case "CHAOS": return ChatColor.RED + "☠ Проклятие: Хаос (случайные зелья каждые 10 сек)";
            case "CURSED_LUCK": return ChatColor.RED + "☠ Проклятие: Проклятая удача (-50% к дропу)";
            case "CURSED_XP": return ChatColor.RED + "☠ Проклятие: Проклятый опыт (-50% к опыту)";
            case "CURSED_SPEED": return ChatColor.RED + "☠ Проклятие: Проклятая скорость (-20% скорости)";
            case "CURSED_DAMAGE": return ChatColor.RED + "☠ Проклятие: Проклятый урон (-20% к урону)";
            case "CURSED_DEFENSE": return ChatColor.RED + "☠ Проклятие: Проклятая защита (-20% к защите)";
            case "CURSED_VISION": return ChatColor.RED + "☠ Проклятие: Проклятое зрение (периодическая слепота)";
            case "CURSED_HUNGER": return ChatColor.RED + "☠ Проклятие: Проклятый голод (быстрый голод)";
            case "CURSED_WEIGHT": return ChatColor.RED + "☠ Проклятие: Проклятый вес (замедление при полном инвентаре)";
            case "CURSED_FIRE": return ChatColor.RED + "☠ Проклятие: Проклятый огонь (периодическое горение)";
            case "CURSED_DROWNING": return ChatColor.RED + "☠ Проклятие: Проклятое утопление (урон в воде)";
            case "CURSED_FALL": return ChatColor.RED + "☠ Проклятие: Проклятое падение (+50% урона от падения)";
            case "CURSED_DARKNESS": return ChatColor.RED + "☠ Проклятие: Проклятая тьма (слепота в темноте)";
            default: return ChatColor.RED + "☠ Проклятие: " + curse;
        }
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

        if (rarity.equals("mythic") || rarity.equals("legendary")) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_mythic"), PersistentDataType.INTEGER, 1);
        }

        item.setItemMeta(meta);
        plugin.incrementArtifactsGenerated();
        return item;
    }

    /**
     * Получить описание баффа
     */
    public static String getBuffDescription(String buff, int level) {
        switch (buff) {
            case "HEALTH": return "Макс. здоровье +" + (level * 2) + " HP (пассивно)";
            case "DAMAGE": return "Урон в ближнем бою +" + level + " (каждый удар)";
            case "SPEED": return "Скорость передвижения +" + (level * 10) + "%";
            case "REGENERATION": return "Регенерация " + level + " ур. (восстановление HP)";
            case "VAMPIRISM": return "Вампиризм " + (level * 10) + "% (крадёт HP при ударе)";
            case "THORNS": return "Отражение урона " + level + " ур. (атакующий получает урон)";
            case "FIRE_RESISTANCE": return "Полный иммунитет к огню и лаве";
            case "LEVITATION": return "Иммунитет к урону от падения";
            case "CRITICAL": return "Шанс критического удара +" + (level * 5) + "% (x2 урон)";
            case "ABSORPTION": return "Абсорбция " + level + " ур. (временные сердца)";
            case "NIGHT_VISION": return "Ночное зрение (видно в темноте)";
            case "HASTE": return "Спешка " + level + " ур. (быстрее копаете/рубите)";
            case "WATER_BREATHING": return "Дыхание под водой (не задыхаетесь)";
            case "JUMP_BOOST": return "Прыгучесть +" + level + " (выше прыгаете)";
            case "LUCK": return "Удача " + level + " ур. (лучше лут с рыбалки/сундуков)";
            case "WITHER_TOUCH": return "Касание Иссушителя " + level + " ур. (эффект визера)";
            case "POISON_STRIKE": return "Ядовитый удар " + level + " ур. (отравляет врага)";
            case "FREEZE_AURA": return "Ледяная аура " + level + " ур. (замедляет рядом)";
            case "LIGHTNING_STRIKE": return "Удар молнии " + level + " ур. (шанс при атаке)";
            case "GHOST_WALK": return "Призрачный шаг (невидимость при HP < 40%)";
            case "TRUE_STRIKE": return "Истинный удар (игнорирует броню врага)";
            case "STEEL_SKIN": return "Стальная кожа +" + level + " (доп. броня)";
            case "AQUATIC_SPEED": return "Скорость под водой +" + level;
            case "FIRE_WALKER": return "Хождение по лаве (не горите на лаве)";
            case "XP_BOOST": return "Бонус опыта +" + (level * 15) + "% (от всех источников)";
            case "DOUBLE_JUMP": return "Двойной прыжок (прыжок в воздухе)";
            case "DODGE_CHANCE": return "Уклонение " + (level * 5) + "% (шанс избежать урона)";
            case "KNOCKBACK_RESIST": return "Анти-отбрасывание " + (level * 30) + "%";
            case "MAX_HEALTH_BOOST": return "Колоссальное здоровье +" + (level * 10) + " HP";
            case "HERO_OF_VILLAGE": return "Герой Деревни " + level + " (торговля с жителями)";
            case "STRENGTH_BOOST": return "Сила " + level + " ур. (больше урона)";
            case "RESISTANCE": return "Сопротивление " + level + " ур. (меньше урона)";
            case "SATURATION": return "Вечная сытость (не голодаете)";
            case "LUCK_OF_THE_SEA": return "Морская удача " + level + " (шанс сокровищ)";
            case "SOUL_DRAIN": return "Вытягивание души +" + level + " (лечит при убийстве)";
            case "FROST_BITE": return "Морозный укус " + level + " (замедление + урон)";
            case "MANA_SHIELD": return "Мана-щит (поглощает " + (level * 10) + "% урона)";
            case "TELEKINESIS": return "Телекинез (авто-подбор предметов, радиус +" + (level * 2) + ")";
            case "ENDER_SHIFT": return "Эндер-сдвиг (ПКМ в воздухе = телепорт)";
            case "BERSERKER": return "Ярость " + level + " (больше урона при низком HP)";
            case "ARCANE_BURST": return "Магический взрыв " + level + " (AoE при убийстве)";
            case "SHADOW_STEP": return "Теневой шаг (ускорение после уклонения)";
            case "LIFESTEAL_AURA": return "Аура вампиризма " + level + " (вампиризм x2 при HP<30%)";
            case "IRON_WILL": return "Железная воля " + level + " (не сдвигается при низком HP)";
            case "TRAP_SENSE": return "Чувство ловушки " + level + " (видит скрытые опасности)";
            case "TREASURE_HUNTER": return "Охотник за сокровищами +" + (level * 10) + "% (доп. дроп)";
            case "FLAME_TONGUE": return "Пылающий язык " + level + " (поджигает врагов)";
            case "WIND_WALKER": return "Шагающий по ветру " + level + " (скорость + прыжок)";
            case "ECHO_STRIKE": return "Удар-эхо " + level + " (шанс двойной атаки)";
            case "SOUL_SHIELD": return "Щит души (Абсорбция II при HP < 30%)";
            case "FIRE_RESISTANCE_AURA": return "Аура огнестойкости (союзникам в 5 блоках)";
            case "XP_MAGNET": return "Магнит опыта +" + (level * 50) + "% (радиус сбора)";
            case "LOOT_FIND": return "Поиск добычи +" + (level * 25) + "% (редкие предметы)";
            case "REVIVAL": return "Возрождение (50% HP при смерти, КД 10 мин)";
            case "ABYSSAL_POWER": return "Сила Бездны (+10 урон, невидимость при HP<30%)";
            case "DRAGON_BLOOD": return "Кровь Дракона (+10 HP, Реген II, +50% урон огнём)";
            case "DARK_PACT": return "Тёмный пакт (+30% урон, −15% HP)";
            case "BLOOD_OATH": return "Кровавая клятва (+20% вампиризм, −10% защиты)";
            case "VOID_TOUCH": return "Прикосновение пустоты (шанс телепорта врага при ударе)";
            case "STARFALL": return "Падение звезды (шанс метеорита при атаке)";
            case "ICE_SHIELD": return "Ледяной щит (замедляет атакующих вас)";
            case "STONE_SKIN": return "Каменная кожа (+50% защиты, −20% скорости)";
            case "PHANTOM_STRIKE": return "Призрачный удар (шанс игнорировать броню врага)";
            case "MAGIC_AMPLIFY": return "Усиление магии (+40% к эффектам зелий)";
            case "ARROW_DEFLECT": return "Отражение стрел (30% шанс блокировать)";
            case "FIRE_ENCHANT": return "Огненное зачарование (поджигает врагов)";
            case "ICE_ENCHANT": return "Ледяное зачарование (замораживает врагов)";
            case "POISON_ENCHANT": return "Ядовитое зачарование (отравляет врагов)";
            case "WITHER_ENCHANT": return "Иссушающее зачарование (иссушает врагов)";
            case "SMITE_ENCHANT": return "Кара (x2 урон по нежити)";
            case "BANE_ENCHANT": return "Гибель членистоногих (x2 урон по паукам)";
            case "SHARPNESS_ENCHANT": return "Острота (+20% базового урона)";
            case "PROTECTION_ENCHANT": return "Доп. защита (−20% любого урона)";
            case "FIRE_PROTECTION": return "Защита от огня (−50% урона)";
            case "BLAST_PROTECTION": return "Взрывозащита (−50% урона от взрывов)";
            case "PROJECTILE_PROTECTION": return "Защита от снарядов (−50% урона)";
            case "FEATHER_FALLING": return "Плавное падение (−50% урона от падения)";
            case "AQUA_AFFINITY": return "Подводная скорость (+50% под водой)";
            case "RESPIRATION": return "Подводное дыхание (дольше под водой)";
            case "DEPTH_STRIDER": return "Ходьба по дну (быстрее под водой)";
            case "FROST_WALKER": return "Ледяная поступь (замораживает воду)";
            case "SOUL_SPEED": return "Скорость души (быстрее на песке душ)";
            case "SWIFT_SNEAK": return "Скрытность (+30% скорости при приседании)";
            case "LOOTING_ENCHANT": return "Грабёж (+30% дропа с мобов)";
            case "FORTUNE_ENCHANT": return "Удача (+30% ресурсов при добыче)";
            case "SILK_TOUCH": return "Шёлковое касание (добыча блоков целиком)";
            case "INFINITY_ENCHANT": return "Бесконечность (не тратятся стрелы)";
            case "MENDING_ENCHANT": return "Починка (восстановление прочности опытом)";
            case "UNBREAKING_ENCHANT": return "Прочность (+100% к прочности предметов)";
            case "SWEEPING_EDGE": return "Разящий клинок (+50% к AoE урону)";
            case "CHANNELING": return "Канал (молния трезубцем в грозу)";
            case "RIPTIDE": return "Буря (бросок трезубца в воде/дожде)";
            case "LOYALTY": return "Верность (брошенный трезубец возвращается)";
            case "MULTISHOT": return "Тройной выстрел (3 стрелы вместо 1)";
            case "PIERCING": return "Пробивание (стрела проходит сквозь врагов)";
            case "QUICK_CHARGE": return "Быстрая зарядка (арбалет заряжается быстрее)";
            default: return "Уникальный эффект " + buff + " ур." + level;
        }
    }
}
