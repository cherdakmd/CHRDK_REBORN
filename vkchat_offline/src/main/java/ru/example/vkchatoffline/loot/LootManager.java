package ru.example.vkchatoffline.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootManager {
    public enum Rarity {
        COMMON("Обычный", "&7", 50),
        UNCOMMON("Необычный", "&a", 25),
        RARE("Редкий", "&9", 15),
        EPIC("Эпический", "&5", 8),
        LEGENDARY("Легендарный", "&6", 2),
        MYTHIC("Мифический", "&c", 0.5);

        public final String displayName, color;
        public final double baseChance;
        Rarity(String displayName, String color, double baseChance) {
            this.displayName = displayName; this.color = color; this.baseChance = baseChance;
        }
    }

    private static final String[][] SERVER_LOOT = {
        {"DIAMOND", "Алмаз", "RARE", "5"},
        {"EMERALD", "Изумруд", "RARE", "4"},
        {"NETHERITE_SCRAP", "Незеритовый лом", "LEGENDARY", "1"},
        {"DIAMOND_SWORD", "Алмазный меч", "EPIC", "2"},
        {"DIAMOND_PICKAXE", "Алмазная кирка", "EPIC", "2"},
        {"DIAMOND_CHESTPLATE", "Алмазный нагрудник", "EPIC", "2"},
        {"NETHERITE_SWORD", "Незеритовый меч", "LEGENDARY", "0.5"},
        {"NETHER_STAR", "Звезда ада", "MYTHIC", "0.1"},
        {"ELYTRA", "Элитра", "MYTHIC", "0.1"},
        {"TOTEM_OF_UNDYING", "Тотем бессмертия", "EPIC", "0.8"},
        {"ENCHANTED_GOLDEN_APPLE", "Зачарованное яблоко", "EPIC", "1"},
        {"DRAGON_EGG", "Драконье яйцо", "MYTHIC", "0.02"},
        {"GOLD_INGOT", "Золотой слиток", "UNCOMMON", "10"},
        {"IRON_INGOT", "Железный слиток", "COMMON", "15"},
        {"OBSIDIAN", "Обсидиан", "UNCOMMON", "8"},
        {"ENDER_PEARL", "Эндер-жемчуг", "UNCOMMON", "6"},
    };

    public List<ItemStack> generateLoot(int level, String route, boolean isBoss) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = ThreadLocalRandom.current();
        int count = 1 + rand.nextInt(3) + (isBoss ? 2 : 0);
        for (int i = 0; i < count; i++) {
            ItemStack item = rollItem(level, isBoss, rand);
            if (item != null) loot.add(item);
        }
        return loot;
    }

    private ItemStack rollItem(int level, boolean isBoss, Random rand) {
        Rarity rarity = rollRarity(level, isBoss, rand);
        List<String[]> candidates = new ArrayList<>();
        for (String[] item : SERVER_LOOT) if (item[2].equals(rarity.name())) candidates.add(item);
        if (candidates.isEmpty()) return null;
        String[] selected = candidates.get(rand.nextInt(candidates.size()));
        Material mat;
        try { mat = Material.valueOf(selected[0]); } catch (Exception e) { return null; }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rarity.color + selected[1]);
        meta.setLore(Arrays.asList("§7Редкость: " + rarity.color + rarity.displayName, "§7Из офлайн-похода"));
        item.setItemMeta(meta);
        return item;
    }

    private Rarity rollRarity(int level, boolean isBoss, Random rand) {
        double roll = rand.nextDouble() * 100;
        double bonus = level * 0.1 + (isBoss ? 10 : 0);
        if (roll < 0.5 + bonus * 0.01) return Rarity.MYTHIC;
        if (roll < 2.5 + bonus * 0.05) return Rarity.LEGENDARY;
        if (roll < 10.5 + bonus * 0.1) return Rarity.EPIC;
        if (roll < 25.5 + bonus * 0.15) return Rarity.RARE;
        if (roll < 50.5 + bonus * 0.2) return Rarity.UNCOMMON;
        return Rarity.COMMON;
    }

    public int getLootItemCount() { return SERVER_LOOT.length; }
}
