package ru.example.vkchatend.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;

/**
 * Менеджер эндер-артефактов — уникальные предметы Энда
 */
public class EndArtifactManager {
    private final VKChatEndPlugin plugin;

    // Эндер-артефакты
    public static final String[][] END_ARTIFACTS = {
        // Название, материал, редкость, описание
        {"Око Бездны", "ENDER_EYE", "legendary", "Телепортация к точке спавна в Энде"},
        {"Кристалл Пустоты", "AMETHYST_SHARD", "epic", "+20% урон в Энде"},
        {"Панцирь Дракона", "DRAGON_BREATH", "legendary", "Сопротивление II в Энде"},
        {"Хорус-сердце", "CHORUS_FRUIT", "rare", "Регенерация в Энде"},
        {"Жемчуг Странника", "ENDER_PEARL", "epic", "Телепортация без кулдауна"},
        {"Крыло Бездны", "PHANTOM_MEMBRANE", "legendary", "Полёт в Энде"},
        {"Осколок Портала", "END_STONE", "rare", "Создание разлома"},
        {"Клык Эндермена", "BLAZE_ROD", "epic", "+15% крит в Энде"},
        {"Пыль Хоруса", "GLOWSTONE_DUST", "common", "Ночное зрение в Энде"},
        {"Слеза Дракона", "GHAST_TEAR", "legendary", "Исцеление при убийстве в Энде"},
        {"Эндеритовый слиток", "NETHERITE_SCRAP", "ancient", "Крафт эндеритового снаряжения"},
        {"Кристалл Шалкера", "SHULKER_SHELL", "epic", "Дополнительный слот инвентаря"},
    };

    public EndArtifactManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    public int getArtifactCount() {
        return END_ARTIFACTS.length;
    }

    /**
     * Создать эндер-артефакт
     */
    public ItemStack createArtifact(String name) {
        for (String[] artifact : END_ARTIFACTS) {
            if (artifact[0].equals(name)) {
                return createArtifactItem(artifact);
            }
        }
        return null;
    }

    /**
     * Создать случайный артефакт
     */
    public ItemStack createRandomArtifact(String rarity) {
        List<String[]> filtered = new ArrayList<>();
        for (String[] artifact : END_ARTIFACTS) {
            if (artifact[2].equals(rarity)) {
                filtered.add(artifact);
            }
        }
        if (filtered.isEmpty()) return null;
        return createArtifactItem(filtered.get(new Random().nextInt(filtered.size())));
    }

    /**
     * Создать предмет артефакта
     */
    private ItemStack createArtifactItem(String[] artifact) {
        Material mat;
        try { mat = Material.valueOf(artifact[1]); } catch (Exception e) { mat = Material.ENDER_PEARL; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        ChatColor color;
        switch (artifact[2]) {
            case "ancient": color = ChatColor.GOLD; break;
            case "legendary": color = ChatColor.DARK_PURPLE; break;
            case "epic": color = ChatColor.BLUE; break;
            case "rare": color = ChatColor.AQUA; break;
            default: color = ChatColor.GREEN; break;
        }

        meta.setDisplayName(color + "✦ " + artifact[0]);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Редкость: " + color + artifact[2].toUpperCase(),
                ChatColor.GRAY + artifact[3],
                "",
                ChatColor.DARK_PURPLE + "Эндер-артефакт",
                ChatColor.GRAY + "Работает в инвентаре"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Проверить, является ли предмет эндер-артефактом
     */
    public boolean isEndArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().hasLore() &&
               item.getItemMeta().getLore().stream().anyMatch(line -> line.contains("Эндер-артефакт"));
    }

    /**
     * Получить все артефакты определённой редкости
     */
    public List<String> getArtifactsByRarity(String rarity) {
        List<String> result = new ArrayList<>();
        for (String[] artifact : END_ARTIFACTS) {
            if (artifact[2].equals(rarity)) {
                result.add(artifact[0]);
            }
        }
        return result;
    }
}
