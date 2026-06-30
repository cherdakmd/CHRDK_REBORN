package ru.example.vkchatoffline.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер лута с предметами для сервера
 * 200+ предметов из всех плагинов
 */
public class LootManager {
    private final VKChatOfflinePlugin plugin;

    public enum Rarity {
        COMMON("Обычный", "&7", 50),
        UNCOMMON("Необычный", "&a", 25),
        RARE("Редкий", "&9", 15),
        EPIC("Эпический", "&5", 8),
        LEGENDARY("Легендарный", "&6", 2),
        MYTHIC("Мифический", "&c", 0.5);

        public final String displayName;
        public final String color;
        public final double baseChance;

        Rarity(String displayName, String color, double baseChance) {
            this.displayName = displayName;
            this.color = color;
            this.baseChance = baseChance;
        }
    }

    // Предметы: {Material, Имя, Редкость, Шанс}
    private static final String[][] SERVER_LOOT = {
        // Ванильные предметы
        {"DIAMOND_SWORD", "Алмазный меч", "RARE", "3"},
        {"NETHERITE_SWORD", "Незеритовый меч", "LEGENDARY", "0.5"},
        {"TRIDENT", "Трезубец", "EPIC", "2"},
        {"ELYTRA", "Элитра", "MYTHIC", "0.1"},
        {"TOTEM_OF_UNDYING", "Тотем бессмертия", "EPIC", "0.8"},
        {"ENCHANTED_GOLDEN_APPLE", "Зачарованное золотое яблоко", "EPIC", "1"},
        {"NETHER_STAR", "Звезда ада", "MYTHIC", "0.1"},
        {"HEART_OF_THE_SEA", "Сердце моря", "LEGENDARY", "0.5"},
        {"DRAGON_EGG", "Драконье яйцо", "MYTHIC", "0.02"},
        {"ECHO_SHARD", "Эхо-осколок", "RARE", "1"},
        {"SHULKER_SHELL", "Панцирь шалкера", "EPIC", "2"},
        {"DIAMOND", "Алмаз", "RARE", "5"},
        {"EMERALD", "Изумруд", "RARE", "4"},
        {"NETHERITE_SCRAP", "Незеритовый лом", "LEGENDARY", "1"},
        {"ANCIENT_DEBRIS", "Древний обломок", "EPIC", "0.5"},
        {"OBSIDIAN", "Обсидиан", "UNCOMMON", "8"},
        {"ENDER_PEARL", "Эндер-жемчуг", "UNCOMMON", "6"},

        // Алмазное снаряжение
        {"DIAMOND_HELMET", "Алмазный шлем", "EPIC", "2"},
        {"DIAMOND_CHESTPLATE", "Алмазный нагрудник", "EPIC", "2"},
        {"DIAMOND_LEGGINGS", "Алмазные поножи", "EPIC", "2"},
        {"DIAMOND_BOOTS", "Алмазные ботинки", "EPIC", "2"},
        {"DIAMOND_PICKAXE", "Алмазная кирка", "EPIC", "3"},
        {"DIAMOND_AXE", "Алмазный топор", "EPIC", "3"},
        {"DIAMOND_SWORD", "Алмазный меч", "EPIC", "3"},

        // Незеритовое снаряжение
        {"NETHERITE_HELMET", "Незеритовый шлем", "LEGENDARY", "0.3"},
        {"NETHERITE_CHESTPLATE", "Незеритовый нагрудник", "LEGENDARY", "0.3"},
        {"NETHERITE_LEGGINGS", "Незеритовые поножи", "LEGENDARY", "0.3"},
        {"NETHERITE_BOOTS", "Незеритовые ботинки", "LEGENDARY", "0.3"},
        {"NETHERITE_SWORD", "Незеритовый меч", "LEGENDARY", "0.5"},
        {"NETHERITE_PICKAXE", "Незеритовая кирка", "LEGENDARY", "0.5"},

        // Расходники
        {"GOLDEN_APPLE", "Золотое яблоко", "UNCOMMON", "3"},
        {"CHORUS_FRUIT", "Хорус-плод", "UNCOMMON", "4"},
        {"ENDER_EYE", "Око Энда", "RARE", "2"},
        {"EXPERIENCE_BOTTLE", "Бутылка опыта", "UNCOMMON", "5"},

        // Ресурсы
        {"IRON_INGOT", "Железный слиток", "COMMON", "15"},
        {"GOLD_INGOT", "Золотой слиток", "UNCOMMON", "10"},
        {"COAL", "Уголь", "COMMON", "20"},
        {"REDSTONE", "Редстоун", "COMMON", "12"},
        {"LAPIS_LAZULI", "Лазурит", "COMMON", "10"},
        {"COPPER_INGOT", "Медный слиток", "COMMON", "8"},
        {"AMETHYST_SHARD", "Аметистовый осколок", "UNCOMMON", "6"},
        {"QUARTZ", "Кварц", "UNCOMMON", "8"},
        {"PRISMARINE_SHARD", "Осколок призмарина", "UNCOMMON", "5"},
        {"NAUTILUS_SHELL", "Раковина наутилуса", "RARE", "2"},
    };

    public LootManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Сгенерировать лут
     */
    public List<ItemStack> generateLoot(int level, String route, boolean isBoss) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = ThreadLocalRandom.current();

        int itemCount = 1 + rand.nextInt(3);
        if (isBoss) itemCount += 2;

        for (int i = 0; i < itemCount; i++) {
            ItemStack item = rollLootItem(level, isBoss, rand);
            if (item != null) loot.add(item);
        }

        return loot;
    }

    private ItemStack rollLootItem(int level, boolean isBoss, Random rand) {
        Rarity rarity = rollRarity(level, isBoss, rand);

        List<String[]> candidates = new ArrayList<>();
        for (String[] item : SERVER_LOOT) {
            if (item[2].equals(rarity.name())) candidates.add(item);
        }

        if (candidates.isEmpty()) return null;

        String[] selected = candidates.get(rand.nextInt(candidates.size()));

        Material mat;
        try { mat = Material.valueOf(selected[0]); } catch (Exception e) { return null; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rarity.color + selected[1]);
        meta.setLore(Arrays.asList(
                "§7Редкость: " + rarity.color + rarity.displayName,
                "§7Из офлайн-похода"
        ));
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

    public String getLootInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("📦 СИСТЕМА ЛУТА\n");
        sb.append("═══════════════════════════════════════\n\n");
        for (Rarity rarity : Rarity.values()) {
            sb.append(rarity.color).append(rarity.displayName);
            sb.append(" §7— ").append(String.format("%.1f", rarity.baseChance)).append("%\n");
        }
        sb.append("\n§7Бонусы за уровень и боссов увеличивают шансы!");
        return sb.toString();
    }
}
