package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;

/**
 * [15] Система эндер-зачарований
 * [16] Эндер-зельеварение
 * [17] Эндер-рыбалка
 * [18] Эндер-фермерство
 */
public class EndEnchantmentManager {
    private final VKChatEndPlugin plugin;

    // Эндер-зачарования
    public static final String[][] END_ENCHANTMENTS = {
        {"void_walk", "Шаг Бездны", "Ходьба по пустоте", "5"},
        {"ender_shift", "Эндер-сдвиг", "Телепорт при ударе", "3"},
        {"void_shield", "Щит Бездны", "Блокировка урона 30%", "4"},
        {"chorus_blink", "Хорус-мерцание", "Телепорт на 10 блоков", "2"},
        {"dragon_breath", "Дыхание Дракона", "AoE огненный урон", "3"},
        {"shulker_float", "Шалкер-парение", "Левитация при прыжке", "2"},
        {"end_mining", "Эндер-добыча", "x2 руды в Энде", "3"},
        {"void_protection", "Защита Бездны", "Сопротивление в Энде", "4"},
        {"ender_venom", "Эндер-яд", "Отравление при ударе", "2"},
        {"purpur_strength", "Сила Пурпура", "+20% урон в Энде", "3"},
    };

    // Эндер-зелья
    public static final String[][] END_POTIONS = {
        {"potion_void_resistance", "Зелье сопротивления Бездне", "Сопротивление в Энде 5 мин", "1500"},
        {"potion_chorus_speed", "Зелье скорости Хоруса", "Скорость III в Энде 3 мин", "2000"},
        {"potion_dragon_power", "Зелье силы Дракона", "Сила III в Энде 2 мин", "3000"},
        {"potion_ender_vision", "Зелье эндер-зрения", "Видеть руды сквозь стены", "2500"},
        {"potion_void_healing", "Зелье исцеления Бездны", "Регенерация III 1 мин", "1800"},
    };

    // Эндер-рыба
    public static final String[][] END_FISH = {
        {"ender_fish", "Эндер-рыба", "Обычный улов Энда", "50"},
        {"chorus_fish", "Хорус-рыба", "Восстановление HP", "100"},
        {"void_fish", "Рыба Бездны", "Телепорт к спавну Энда", "200"},
        {"dragon_fish", "Драконья рыба", "+500 репутации", "500"},
        {"crystal_fish", "Кристальная рыба", "Дроп кристаллов", "300"},
    };

    // Эндер-урожай
    public static final String[][] END_CROPS = {
        {"chorus_fruit", "Хорус-плод", "Телепорт на 10 блоков", "30"},
        {"void_berry", "Ягода Бездны", "Регенерация 5 сек", "50"},
        {"ender_wheat", "Эндер-пшеница", "x2 хлеб при крафте", "20"},
        {"purpur_mushroom", "Пурпурный гриб", "Сопротивление 1 мин", "80"},
    };

    public EndEnchantmentManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Наложить эндер-зачарование
     */
    public boolean applyEndEnchantment(Player p, ItemStack item, String enchantId) {
        String[] enchData = getEnchantmentData(enchantId);
        if (enchData == null) {
            p.sendMessage(ChatColor.RED + "Неизвестное зачарование!");
            return false;
        }

        int cost = Integer.parseInt(enchData[3]) * 100;
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add(ChatColor.LIGHT_PURPLE + "✦ " + enchData[1] + " " + enchData[3]);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "end_enchant_" + enchantId), PersistentDataType.STRING, enchData[3]);
        item.setItemMeta(meta);

        p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Наложено: " + enchData[1] + " " + enchData[3]);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.5f);

        return true;
    }

    /**
     * Создать эндер-зелье
     */
    public ItemStack createEndPotion(String potionId) {
        String[] potionData = getPotionData(potionId);
        if (potionData == null) return null;

        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + potionData[1]);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + potionData[2],
                "",
                ChatColor.DARK_PURPLE + "Эндер-зелье"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "end_potion"), PersistentDataType.STRING, potionId);
        potion.setItemMeta(meta);

        return potion;
    }

    /**
     * Поймать эндер-рыбу
     */
    public ItemStack catchEndFish() {
        Random rand = new Random();
        int roll = rand.nextInt(100);

        String fishId;
        if (roll < 50) fishId = "ender_fish";
        else if (roll < 75) fishId = "chorus_fish";
        else if (roll < 90) fishId = "void_fish";
        else if (roll < 97) fishId = "crystal_fish";
        else fishId = "dragon_fish";

        String[] fishData = getFishData(fishId);
        if (fishData == null) return null;

        ItemStack fish = new ItemStack(Material.TROPICAL_FISH);
        ItemMeta meta = fish.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + fishData[1]);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + fishData[2],
                "",
                ChatColor.DARK_PURPLE + "Эндер-рыба"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "end_fish"), PersistentDataType.STRING, fishId);
        fish.setItemMeta(meta);

        return fish;
    }

    /**
     * Собрать эндер-урожай
     */
    public ItemStack harvestEndCrop(String cropId) {
        String[] cropData = getCropData(cropId);
        if (cropData == null) return null;

        Material mat;
        try { mat = Material.valueOf(cropData[0].toUpperCase()); } catch (Exception e) { mat = Material.CHORUS_FRUIT; }

        ItemStack crop = new ItemStack(mat);
        ItemMeta meta = crop.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + cropData[1]);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + cropData[2],
                "",
                ChatColor.DARK_PURPLE + "Эндер-урожай"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "end_crop"), PersistentDataType.STRING, cropId);
        crop.setItemMeta(meta);

        return crop;
    }

    private String[] getEnchantmentData(String id) {
        for (String[] ench : END_ENCHANTMENTS) {
            if (ench[0].equals(id)) return ench;
        }
        return null;
    }

    private String[] getPotionData(String id) {
        for (String[] potion : END_POTIONS) {
            if (potion[0].equals(id)) return potion;
        }
        return null;
    }

    private String[] getFishData(String id) {
        for (String[] fish : END_FISH) {
            if (fish[0].equals(id)) return fish;
        }
        return null;
    }

    private String[] getCropData(String id) {
        for (String[] crop : END_CROPS) {
            if (crop[0].equals(id)) return crop;
        }
        return null;
    }

    public int getEnchantmentCount() { return END_ENCHANTMENTS.length; }
    public int getPotionCount() { return END_POTIONS.length; }
    public int getFishCount() { return END_FISH.length; }
    public int getCropCount() { return END_CROPS.length; }
}
