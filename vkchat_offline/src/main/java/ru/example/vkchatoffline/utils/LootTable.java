package ru.example.vkchatoffline.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Управление таблицей добычи.
 * Поддерживает разные типы лута, шансы выпадения и множители.
 */
public class LootTable {
    private final Map<String, LootTable.Entry> lootTables = new HashMap<>();

    /**
     * Класс записи лута.
     */
    public static class Entry {
        public final String name;
        public final Material material;
        public final int minAmount;
        public final int maxAmount;
        public final int chance; // 0-100
        public final String rarity;

        public Entry(String name, Material material, int minAmount, int maxAmount, int chance, String rarity) {
            this.name = name;
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chance = chance;
            this.rarity = rarity;
        }

        public int getRandomAmount() {
            if (maxAmount <= minAmount) return minAmount;
            return minAmount + ThreadLocalRandom.current().nextInt(maxAmount - minAmount + 1);
        }
    }

    /**
     * Регистрация таблицы лута.
     */
    public void registerTable(String name, List<Entry> entries) {
        lootTables.put(name, new Entry(name, Material.AIR, 0, 0, 0, "common"));
        lootTables.putAll(entries.stream()
                .collect(Collectors.toMap(
                        e -> name + "_" + e.name.toLowerCase().replace(" ", "_"),
                        e -> e
                )));
    }

    /**
     * Генерация лута по таблице.
     */
    public List<ItemStack> generateLoot(String tableName, int levelMultiplier) {
        List<ItemStack> loot = new ArrayList<>();
        List<Entry> table = lootTables.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tableName + "_"))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        for (Entry entry : table) {
            if (ThreadLocalRandom.current().nextInt(100) < entry.chance) {
                int amount = entry.getRandomAmount();
                if (levelMultiplier > 1) {
                    amount = (int) (amount * (1.0 + (levelMultiplier - 1) * 0.1));
                }
                if (amount > 0) {
                    loot.add(new ItemStack(entry.material, Math.max(1, amount)));
                }
            }
        }

        return loot;
    }

    /**
     * Генерация случайного предмета.
     */
    public ItemStack generateRandomLoot(List<String> itemStrings, int level) {
        List<Entry> entries = new ArrayList<>();

        for (String itemStr : itemStrings) {
            String[] parts = itemStr.split(";");
            if (parts.length < 2) continue;

            try {
                Material material = Material.valueOf(parts[0].toUpperCase());
                int min = Integer.parseInt(parts[1]);
                int max = parts.length > 2 ? Integer.parseInt(parts[2]) : min;
                int chance = Math.min(100, 10 + (level * 5)); // Чем выше уровень, тем выше шанс

                entries.add(new Entry(parts[0], material, min, max, chance, "common"));
            } catch (Exception ignored) {}
        }

        if (entries.isEmpty()) return new ItemStack(Material.STONE);

        // Взвешенный выбор
        int totalWeight = entries.stream().mapToInt(e -> e.chance).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;

        for (Entry entry : entries) {
            current += entry.chance;
            if (roll < current) {
                int amount = entry.getRandomAmount();
                return new ItemStack(entry.material, Math.max(1, amount));
            }
        }

        return new ItemStack(Material.STONE);
    }

    // --- Статические методы для создания таблиц ---

    public static LootTable createForestTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Ягоды", Material.REDSTONE, 1, 3, 80, "common"),
                new Entry("Грибы", Material.BROWN_MUSHROOM, 2, 5, 70, "common"),
                new Entry("Палки", Material.STICK, 3, 8, 90, "common"),
                new Entry("Птичьи яйца", Material.EGG, 1, 2, 40, "uncommon"),
                new Entry("Мед", Material.HONEY_BOTTLE, 1, 1, 25, "rare"),
                new Entry("Карта", Material.MAP, 1, 1, 5, "epic")
        );

        table.registerTable("forest", entries);
        return table;
    }

    public static LootTable createMineTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Уголь", Material.COAL, 5, 15, 90, "common"),
                new Entry("Железо", Material.IRON_ORE, 2, 8, 75, "common"),
                new Entry("Золото", Material.GOLD_ORE, 1, 3, 50, "uncommon"),
                new Entry("Алмаз", Material.DIAMOND, 1, 2, 15, "rare"),
                new Entry("Лазурит", Material.LAPIS_LAZULI, 3, 10, 60, "uncommon"),
                new Entry("Редстоун", Material.REDSTONE, 2, 6, 40, "uncommon"),
                new Entry("Незерит", Material.NETHERITE_SCRAP, 1, 1, 3, "legendary")
        );

        table.registerTable("mine", entries);
        return table;
    }

    public static LootTable createCastleTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Золотые монеты", Material.GOLD_INGOT, 10, 30, 95, "common"),
                new Entry("Алмазы", Material.DIAMOND, 3, 8, 70, "uncommon"),
                new Entry("Эндер жемчуг", Material.ENDER_PEARL, 1, 2, 20, "rare"),
                new Entry("Золотое яблоко", Material.ENCHANTED_GOLDEN_APPLE, 1, 2, 15, "rare"),
                new Entry("Блок алмаза", Material.DIAMOND_BLOCK, 1, 2, 10, "epic"),
                new Entry("Свиток синтеза", Material.PAPER, 1, 1, 8, "legendary"),
                new Entry("Артефакт", Material.TOTEM_OF_UNDYING, 1, 1, 5, "mythic")
        );

        table.registerTable("castle", entries);
        return table;
    }

    public static LootTable createBossLoot() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Древний артефакт", Material.DRAGON_HEAD, 1, 1, 100, "mythic"),
                new Entry("Титановая броня", Material.NETHERITE_CHESTPLATE, 1, 1, 80, "legendary"),
                new Entry("Свиток мощи", Material.GOLDEN_HOE, 1, 1, 60, "epic"),
                new Entry("Сердце босса", Material.BEACON, 1, 1, 40, "rare"),
                new Entry("Блок золота", Material.GOLD_BLOCK, 5, 10, 90, "common"),
                new Entry("Блок алмаза", Material.DIAMOND_BLOCK, 2, 5, 70, "uncommon")
        );

        table.registerTable("boss", entries);
        return table;
    }

    public static LootTable createMountainTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Железо", Material.IRON_INGOT, 8, 20, 85, "common"),
                new Entry("Золото", Material.GOLD_INGOT, 4, 12, 70, "common"),
                new Entry("Алмаз", Material.DIAMOND, 1, 3, 25, "rare"),
                new Entry("Изумруд", Material.EMERALD, 2, 6, 45, "uncommon"),
                new Entry("Камень", Material.COBBLESTONE, 16, 32, 60, "uncommon"),
                new Entry("Слеза призрака", Material.GHAST_TEAR, 1, 1, 8, "legendary")
        );

        table.registerTable("mountain", entries);
        return table;
    }

    public static LootTable createUnderwaterTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Сердце моря", Material.HEART_OF_THE_SEA, 1, 1, 15, "rare"),
                new Entry("Раковина наутилуса", Material.NAUTILUS_SHELL, 2, 4, 50, "uncommon"),
                new Entry("Осколок призмарина", Material.PRISMARINE_SHARD, 8, 24, 80, "common"),
                new Entry("Трезубец", Material.TRIDENT, 1, 1, 5, "legendary"),
                new Entry("Золото", Material.GOLD_INGOT, 4, 12, 65, "common"),
                new Entry("Водоросли", Material.KELP, 16, 32, 90, "common")
        );

        table.registerTable("underwater", entries);
        return table;
    }

    public static LootTable createDesertTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Песок", Material.SAND, 32, 64, 100, "common"),
                new Entry("Золото", Material.GOLD_INGOT, 4, 12, 65, "common"),
                new Entry("Изумруд", Material.EMERALD, 1, 3, 30, "uncommon"),
                new Entry("Кактус", Material.CACTUS, 8, 16, 70, "common"),
                new Entry("Седло", Material.SADDLE, 1, 1, 10, "rare"),
                new Entry("Древний обломок", Material.ANCIENT_DEBRIS, 1, 1, 5, "legendary")
        );

        table.registerTable("desert", entries);
        return table;
    }

    public static LootTable createFrozenTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Лёд", Material.ICE, 16, 48, 100, "common"),
                new Entry("Алмаз", Material.DIAMOND, 1, 3, 25, "rare"),
                new Entry("Плотный лёд", Material.PACKED_ICE, 8, 24, 60, "uncommon"),
                new Entry("Синий лёд", Material.BLUE_ICE, 4, 12, 40, "uncommon"),
                new Entry("Зачарованное золотое яблоко", Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 5, "legendary"),
                new Entry("Снежный ком", Material.SNOWBALL, 16, 32, 90, "common")
        );

        table.registerTable("frozen", entries);
        return table;
    }

    public static LootTable createVolcanicTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Слизь магмы", Material.MAGMA_CREAM, 8, 24, 100, "common"),
                new Entry("Обсидиан", Material.OBSIDIAN, 8, 24, 70, "common"),
                new Entry("Незеритовый обломок", Material.NETHERITE_SCRAP, 1, 3, 30, "rare"),
                new Entry("Древний обломок", Material.ANCIENT_DEBRIS, 1, 1, 12, "epic"),
                new Entry("Огненный заряд", Material.FIRE_CHARGE, 4, 8, 50, "uncommon"),
                new Entry("Базальт", Material.BASALT, 16, 32, 85, "common")
        );

        table.registerTable("volcanic", entries);
        return table;
    }

    public static LootTable createShadowTable() {
        LootTable table = new LootTable();

        List<Entry> entries = Arrays.asList(
                new Entry("Жемчуг Энда", Material.ENDER_PEARL, 8, 20, 100, "common"),
                new Entry("Око Энда", Material.ENDER_EYE, 4, 12, 60, "uncommon"),
                new Entry("Панцирь шалкера", Material.SHULKER_SHELL, 1, 2, 15, "rare"),
                new Entry("Элитра", Material.ELYTRA, 1, 1, 3, "legendary"),
                new Entry("Тотем бессмертия", Material.TOTEM_OF_UNDYING, 1, 1, 10, "epic"),
                new Entry("Незеритовый обломок", Material.NETHERITE_SCRAP, 2, 6, 40, "rare")
        );

        table.registerTable("shadow", entries);
        return table;
    }

    // --- Конвертация в Base64 (используется StashManager) ---

    public static String itemsToBase64(List<ItemStack> items) {
        return ru.example.vkchatoffline.utils.Base64Util.toBase64(items);
    }

    public static List<ItemStack> itemsFromBase64(String base64) {
        return ru.example.vkchatoffline.utils.Base64Util.fromBase64(base64);
    }
}
