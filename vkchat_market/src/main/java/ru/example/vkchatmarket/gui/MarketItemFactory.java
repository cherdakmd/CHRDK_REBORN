package ru.example.vkchatmarket.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MarketItemFactory — фабрика ItemStack для GUI рынка.
 * Вынесена из MarketGuiListener для устранения God-класса.
 */
public final class MarketItemFactory {

    private MarketItemFactory() {}

    /**
     * Создать базовый предмет с именем и lore.
     */
    public static ItemStack create(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    /**
     * Создать предмет-категорию.
     */
    public static ItemStack categoryItem(VKChatMarketPlugin plugin, Material mat,
                                          String category, String name, String... desc) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(desc));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "market_category"),
                PersistentDataType.STRING, category);
        it.setItemMeta(meta);
        return it;
    }

    /**
     * Создать навигационную кнопку.
     */
    public static ItemStack navItem(VKChatMarketPlugin plugin, Material mat,
                                     String name, int page, String category) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList("§7Нажми для перехода"));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "market_nav_page"),
                PersistentDataType.INTEGER, Math.max(0, page));
        it.setItemMeta(meta);
        return it;
    }

    /**
     * Создать кнопку "Продать всё".
     */
    public static ItemStack sellAllItem(VKChatMarketPlugin plugin) {
        ItemStack it = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName("§a§l💰 Продать всё");
        meta.setLore(Arrays.asList(
                "§7Продать все обычные предметы",
                "§7из инвентаря в рынок",
                "",
                "§cПредметы с lore не продаются",
                "",
                "§eНажми для продажи"));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "market_sell_all"),
                PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    /**
     * Создать предмет подтверждения продажи.
     */
    public static ItemStack confirmSellAllItem(VKChatMarketPlugin plugin) {
        ItemStack it = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName("§a§l✅ ПОДТВЕРДИТЬ ПРОДАЖУ");
        meta.setLore(Arrays.asList("§7Нажми для подтверждения", "§cОтмена при закрытии инвентаря"));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "market_confirm_sell_all"),
                PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    /**
     * Создать случайную зачарованную книгу.
     */
    public static ItemStack createRandomEnchantedBook() {
        Enchantment[] pool = {
                Enchantment.DURABILITY, Enchantment.DIG_SPEED,
                Enchantment.LOOT_BONUS_BLOCKS, Enchantment.LOOT_BONUS_MOBS,
                Enchantment.FIRE_ASPECT, Enchantment.ARROW_DAMAGE,
                Enchantment.DEPTH_STRIDER, Enchantment.THORNS,
                Enchantment.PROTECTION_FALL
        };
        Enchantment ench = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        int maxLvl = ench.getMaxLevel();
        int lvl = maxLvl > 3
                ? ThreadLocalRandom.current().nextInt(1, 4)
                : ThreadLocalRandom.current().nextInt(1, maxLvl + 1);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;
        meta.addEnchant(ench, lvl, true);
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Определить иконку для категории.
     */
    public static Material getCatIcon(String cat) {
        return switch (cat) {
            case "ores" -> Material.IRON_INGOT;
            case "food" -> Material.GOLDEN_CARROT;
            case "wood" -> Material.OAK_LOG;
            case "blocks" -> Material.STONE_BRICKS;
            case "earth" -> Material.GRASS_BLOCK;
            case "mob" -> Material.BONE;
            case "decor" -> Material.PINK_WOOL;
            case "all" -> Material.CHEST;
            default -> Material.PAPER;
        };
    }

    /**
     * Получить читаемое имя категории.
     */
    public static String getCategoryDisplayName(String category) {
        return switch (category) {
            case "ores" -> "Руды и слитки";
            case "food" -> "Еда и ферма";
            case "wood" -> "Дерево";
            case "blocks" -> "Стройматериалы";
            case "earth" -> "Земля и природа";
            case "ice" -> "Снег и лёд";
            case "nether" -> "Незер";
            case "mob" -> "Лут мобов";
            case "decor" -> "Декор";
            case "decor2" -> "Декор 2";
            case "limited" -> "Редкости дня";
            case "rare" -> "Книги и редкости";
            case "all" -> "Все товары";
            default -> "Биржа";
        };
    }

    /**
     * Определить донат-статус игрока.
     */
    public static String getDonorStatus(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "Повелитель";
        if (p.hasPermission("vkchat.donate.legend")) return "Легенда";
        if (p.hasPermission("vkchat.donate.star")) return "Звезда";
        if (p.hasPermission("vkchat.donate.flame")) return "Пламя";
        if (p.hasPermission("vkchat.donate.spark")) return "Искра";
        if (p.hasPermission("vkchat.donate.vip")) return "VIP";
        return "";
    }

    /**
     * Множитель продажи донатера (единый метод, без дублирования).
     */
    public static double donorSellMultiplier(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 1.70;
        if (p.hasPermission("vkchat.donate.legend")) return 1.50;
        if (p.hasPermission("vkchat.donate.star")) return 1.35;
        if (p.hasPermission("vkchat.donate.flame")) return 1.20;
        if (p.hasPermission("vkchat.donate.spark")) return 1.10;
        return 1.0;
    }

    /**
     * Множитель покупки донатера.
     */
    public static double donorBuyMultiplier(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 0.35;
        if (p.hasPermission("vkchat.donate.legend")) return 0.50;
        if (p.hasPermission("vkchat.donate.star")) return 0.65;
        if (p.hasPermission("vkchat.donate.flame")) return 0.80;
        if (p.hasPermission("vkchat.donate.spark")) return 0.90;
        return 1.0;
    }

    /**
     * Текстовый бонус продажи.
     */
    public static String getSellBonus(org.bukkit.entity.Player p) {
        double mult = donorSellMultiplier(p);
        if (mult > 1.0) return "+" + String.format("%.0f", (mult - 1.0) * 100) + "%";
        return "нет";
    }

    /**
     * Текстовый бонус покупки.
     */
    public static String getBuyBonus(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "-65%";
        if (p.hasPermission("vkchat.donate.legend")) return "-50%";
        if (p.hasPermission("vkchat.donate.star")) return "-35%";
        if (p.hasPermission("vkchat.donate.flame")) return "-20%";
        if (p.hasPermission("vkchat.donate.spark")) return "-10%";
        if (p.hasPermission("vkchat.donate.vip")) return "-5%";
        return "нет";
    }

    /**
     * Текстовый лимит-бонус.
     */
    public static String getLimitBonus(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "x2.5";
        if (p.hasPermission("vkchat.donate.legend")) return "x2.0";
        if (p.hasPermission("vkchat.donate.star")) return "x1.5";
        if (p.hasPermission("vkchat.donate.flame")) return "x1.3";
        if (p.hasPermission("vkchat.donate.spark")) return "x1.1";
        return "стандарт";
    }

    /**
     * Проверка редкого товара.
     */
    public static boolean isRareShopItem(String id) {
        return id.contains("TOTEM") || id.contains("ENCHANTED_GOLDEN_APPLE")
                || id.contains("NETHERITE_INGOT") || id.contains("ECHO_SHARD")
                || id.contains("ANCIENT_DEBRIS") || id.contains("NETHER_STAR")
                || id.contains("HEART_OF_THE_SEA");
    }

    /**
     * Угадать категорию по ID предмета.
     */
    public static String guessCategory(String id) {
        if (id.contains("LOG") || id.contains("WOOD") || id.contains("PLANKS")) return "Дерево";
        if (id.contains("ORE") || id.contains("INGOT") || id.contains("DIAMOND")
                || id.contains("NETHERITE") || id.contains("GOLD")) return "Руды/слитки";
        if (id.contains("APPLE") || id.contains("BREAD") || id.contains("CARROT")
                || id.contains("POTATO") || id.contains("BEEF") || id.contains("PORK")
                || id.contains("CHICKEN") || id.contains("WHEAT")) return "Еда/ферма";
        if (id.contains("DIRT") || id.contains("GRASS") || id.contains("GRAVEL")
                || id.contains("MUD")) return "Земля";
        if (id.contains("ICE") || id.contains("SNOW")) return "Снег/лёд";
        if (id.contains("WOOL") || id.contains("DYE") || id.contains("TERRACOTTA")) return "Декор";
        if (id.contains("BLAZE") || id.contains("ENDER") || id.contains("GHAST")
                || id.contains("SOUL")) return "Незер/мобы";
        return "Ресурсы";
    }

    /**
     * Нормализация категории.
     */
    public static String normalizeCategory(String category) {
        if (category == null) return "all";
        String c = category.toLowerCase();
        if (c.equals("all") || c.equals("menu") || c.equals("все") || c.equals("категории")) return "menu";
        if (c.equals("ores") || c.equals("руды") || c.equals("руды/слитки")) return "ores";
        if (c.equals("food") || c.equals("еда") || c.equals("еда и ферма")) return "food";
        if (c.equals("wood") || c.equals("дерево")) return "wood";
        if (c.equals("blocks") || c.equals("строй") || c.equals("стройматериалы")) return "blocks";
        if (c.equals("earth") || c.equals("земля")) return "earth";
        if (c.equals("mob") || c.equals("мобы") || c.equals("лут мобов")) return "mob";
        if (c.equals("decor") || c.equals("декор")) return "decor";
        if (c.equals("limited") || c.equals("редкости")) return "limited";
        if (c.equals("rare") || c.equals("книги")) return "rare";
        if (c.equals("trends") || c.equals("тренды")) return "trends";
        return "all";
    }
}
