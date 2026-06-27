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
import java.util.Random;

public class ArtifactFactory {
    private static final Random random = new Random();
    
    public static final String[] BUFFS = {
        "HEALTH", "DAMAGE", "SPEED", "REGENERATION", "VAMPIRISM", "THORNS", "FIRE_RESISTANCE", "LEVITATION", 
        "CRITICAL", "ABSORPTION", "NIGHT_VISION", "HASTE", "WATER_BREATHING", "JUMP_BOOST", "LUCK", "WITHER_TOUCH", 
        "POISON_STRIKE", "FREEZE_AURA", "LIGHTNING_STRIKE", "GHOST_WALK", "TRUE_STRIKE", "STEEL_SKIN", "AQUATIC_SPEED", 
        "FIRE_WALKER", "XP_BOOST", "REP_BOOST", "DOUBLE_JUMP", "DODGE_CHANCE", "KNOCKBACK_RESIST", "MAX_HEALTH_BOOST",
        "HERO_OF_VILLAGE", "STRENGTH_BOOST", "RESISTANCE", "SATURATION", "LUCK_OF_THE_SEA",
        "SOUL_DRAIN", "FROST_BITE", "MANA_SHIELD", "TELEKINESIS", "ENDER_SHIFT",
        "BERSERKER", "ARCANE_BURST", "SHADOW_STEP", "LIFESTEAL_AURA", "IRON_WILL",
        "TRAP_SENSE", "TREASURE_HUNTER", "FLAME_TONGUE", "WIND_WALKER", "ECHO_STRIKE"
    };
    public static final String[] CURSES = {"SLOWNESS", "WEAKNESS", "HUNGER", "FRAGILE", "BLINDNESS", "VULNERABILITY", "DECAY", "SILENCE", "BLOODLETTING", "ANCHOR"};

    public static ItemStack generateArtifact(VKChatArtifactsPlugin plugin, boolean isMythic) {
        Material[] possibleMats = {Material.NETHER_STAR, Material.TOTEM_OF_UNDYING, Material.HEART_OF_THE_SEA, Material.DRAGON_BREATH, Material.GHAST_TEAR, Material.BLAZE_POWDER, Material.RABBIT_FOOT, Material.MAGMA_CREAM, Material.END_CRYSTAL, Material.CONDUIT, Material.SCUTE, Material.END_ROD, Material.CHORUS_FRUIT};
        Material mat = possibleMats[random.nextInt(possibleMats.length)];
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        
        String buff = BUFFS[random.nextInt(BUFFS.length)];
        String curse = isMythic ? "NONE" : CURSES[random.nextInt(CURSES.length)];
        int level = random.nextInt(3) + 1; 
        
        if (isMythic) level = 5; 

        String name = "";
        ChatColor color;
        if (isMythic) {
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
            case "REP_BOOST": lore.add(ChatColor.GREEN + "➕ Бонус к репутации ВК +" + (level * 5) + "%"); break;
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
}
