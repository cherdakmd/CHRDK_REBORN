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
import ru.example.vkchatmarket.data.MarketCategoryResolver;
import ru.example.vkchatmarket.data.MarketTransactionService;
import ru.example.vkchatmarket.integration.ExcellentEnchantsBridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MarketItemFactory v3.0 — фабрика ItemStack для GUI рынка.
 *
 * Изменения v3.0:
 * - Категорийная логика делегирована MarketCategoryResolver
 * - Бизнес-логика делегирована MarketTransactionService
 * - Убраны дублирующие методы (normalizeCategory, isRareShopItem, getCategoryDisplayName, getCatIcon)
 * - Добавлены createLimitedItem и createTrendItem (перенесены из MarketGuiListener)
 * - EE-интеграция через ExcellentEnchantsBridge
 * - Единые донат-множители
 */
public final class MarketItemFactory {

    private MarketItemFactory() {}

    // ═══════════════════════════════════════
    // БАЗОВЫЕ ПРЕДМЕТЫ
    // ═══════════════════════════════════════

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

    public static ItemStack confirmSellAllItem(VKChatMarketPlugin plugin) {
        ItemStack it = new ItemStack(Material.LIME_CONCRETE);
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

    // ═══════════════════════════════════════
    // ЗАЧАРОВАННЫЕ КНИГИ (EE ИНТЕГРАЦИЯ)
    // ═══════════════════════════════════════

    /**
     * Создать зачарованную книгу для рынка.
     * Приоритет: ExcellentEnchants → ванильные.
     */
    public static ItemStack createBookForMarket(String enchKey, int level) {
        return ExcellentEnchantsBridge.createBookForMarket(enchKey, level);
    }

    /**
     * Создать случайную зачарованную книгу.
     * EE доступен → взвешенная EE-книга.
     * EE не доступен → ванильная книга из пула.
     */
    public static ItemStack createRandomEnchantedBook() {
        if (ExcellentEnchantsBridge.isEnabled()) {
            ItemStack eeBook = ExcellentEnchantsBridge.createWeightedRandomEEBook();
            if (eeBook != null) return eeBook;
        }
        return createVanillaRandomBook();
    }

    /**
     * Создать случайную EE-книгу конкретной раритетности.
     */
    public static ItemStack createRandomEEBook(String rarity) {
        return ExcellentEnchantsBridge.createRandomEEBook(rarity);
    }

    /**
     * Ванильная случайная книга (fallback).
     */
    private static ItemStack createVanillaRandomBook() {
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
     * Проверить, подходит ли книга для рынка (конфликты).
     */
    public static boolean isBookValidForMarket(ItemStack book) {
        return ExcellentEnchantsBridge.isBookValidForMarket(book);
    }

    /**
     * Получить читаемое имя зачарования с раритетностью.
     */
    public static String getEnchantDisplayLine(Enchantment ench, int level) {
        String name = ExcellentEnchantsBridge.getEnchantDisplayName(ench);
        String rarity = ExcellentEnchantsBridge.getRarityDisplay(ench);
        return rarity + " " + name + " " + toRoman(level);
    }

    // ═══════════════════════════════════════
    // ПРЕДМЕТЫ РЫНКА С EE LORE
    // ═══════════════════════════════════════

    /**
     * Создать предмет для отображения в GUI рынка с полной информацией.
     * Включает EE-информацию о зачарованиях.
     */
    public static ItemStack createMarketItem(VKChatMarketPlugin plugin, String itemId) {
        String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
        double sellPrice = plugin.getMarketManager().getCurrentPrice(itemId);
        double buyPrice = plugin.getMarketManager().getBuyPrice(itemId);
        double basePrice = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);

        ItemStack displayItem = createCustomItem(plugin, itemId);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;
        meta.setDisplayName("§f" + ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();

        // Информация о EE-зачаровании (если это книга)
        String enchKey = plugin.getConfig().getString("items." + itemId + ".enchant", "");
        if (!enchKey.isEmpty()) {
            Enchantment ench = resolveEnchantment(enchKey);
            if (ench != null) {
                int level = plugin.getConfig().getInt("items." + itemId + ".enchant-level", 1);
                lore.add(ExcellentEnchantsBridge.getRarityDisplay(ench));
                lore.add("§9" + ExcellentEnchantsBridge.getEnchantDisplayName(ench) + " " + toRoman(level));
                lore.add("");
            }
        }

        // Активные события
        for (var ev : plugin.getMarketManager().getActiveEvents()) {
            if (ev.itemId.equals(itemId)) {
                long minLeft = Math.max(1, (ev.expireTime - System.currentTimeMillis()) / 60000);
                lore.add("§d⚡ " + ev.name + " §7(ещё " + minLeft + " мин)");
                break;
            }
        }

        // Flash sale
        if (plugin.getMarketFun().isFlashSaleActive(itemId)) {
            lore.add("§e⚡ FLASH SALE §7— скидка §c"
                    + (int)(plugin.getMarketFun().getFlashSaleDiscount() * 100) + "%§7!");
        }

        lore.add("");
        double delta = basePrice > 0 ? ((sellPrice - basePrice) / basePrice) * 100.0 : 0;
        String trend = delta >= 5 ? " §a▲" : delta <= -5 ? " §c▼" : "";
        lore.add("§6💰 Продажа: §e" + String.format("%.0f", sellPrice) + " реп" + trend);
        lore.add("§6🛒 Покупка: §e" + String.format("%.0f", buyPrice) + " реп");

        // Сток
        int stock = plugin.getMarketManager().getStock(itemId);
        int scarcityThreshold = plugin.getConfig().getInt("items." + itemId + ".scarcity-threshold", 0);
        String stockIcon;
        if ("ENCHANTED_BOOK".equals(itemId) || !enchKey.isEmpty())
            stockIcon = "§a✓ Всегда в наличии";
        else if (stock <= -50) stockIcon = "§4💀 КРИТ. ДЕФИЦИТ";
        else if (stock <= 0) stockIcon = "§c⚠ Дефицит";
        else if (scarcityThreshold > 0 && stock <= scarcityThreshold) stockIcon = "§e⚠ Мало: §f" + stock;
        else stockIcon = "§a✓ В наличии: §f" + stock;
        lore.add("§7" + stockIcon);

        lore.add("");
        lore.add("§eЛКМ — продать §7| §eПКМ — купить §7| §eShift — купить 16");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "market_item"),
                PersistentDataType.STRING, itemId);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    /**
     * Создать лимитированный предмет для GUI.
     */
    public static ItemStack createLimitedItem(VKChatMarketPlugin plugin, String itemId) {
        Material m = MarketTransactionService.getMarketMaterial(plugin, itemId);
        String name = plugin.getConfig().getString("limited-items." + itemId + ".name", itemId);
        int price = plugin.getConfig().getInt("limited-items." + itemId + ".price", 1000);
        int limit = plugin.getConfig().getInt("limited-items." + itemId + ".daily-limit", 1);

        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName("§d§l" + ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.asList(
                "§dЛимитированный товар дня",
                "",
                "§7Цена: §e" + price + " реп.",
                "§7Лимит: §f" + limit + " §7в день",
                "",
                "§cНе продаётся обратно",
                "",
                "§eНажми для покупки 1 шт."
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "market_limited_item"),
                PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Создать предмет-тренд для меню трендов.
     */
    public static ItemStack createTrendItem(VKChatMarketPlugin plugin, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { m = Material.PAPER; }
        String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
        double price = plugin.getMarketManager().getCurrentPrice(itemId);
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double delta = base <= 0 ? 0 : ((price - base) / base) * 100.0;
        return create(m,
                "§f" + ChatColor.translateAlternateColorCodes('&', name),
                "§7Тренд: " + plugin.getMarketManager().getTrendLabel(itemId),
                "§7Цена: §a" + String.format("%.2f", price) + " реп.",
                "§7Отклонение: " + (delta >= 0 ? "§a+" : "§c") + String.format("%.1f", delta) + "%",
                "§8Оборот: " + plugin.getMarketManager().getDailyVolume(itemId) + " шт.");
    }

    /**
     * Создать кастомный предмет (книгу с зачарованием).
     * Поддерживает EE-зачарования через ExcellentEnchantsBridge.
     */
    public static ItemStack createCustomItem(VKChatMarketPlugin plugin, String itemId) {
        String matName = plugin.getConfig().getString("items." + itemId + ".material", "");
        Material mat;
        if (!matName.isEmpty()) {
            try { mat = Material.valueOf(matName); } catch (Exception e) { mat = Material.BARRIER; }
        } else {
            try { mat = Material.valueOf(itemId); } catch (Exception e) { mat = Material.BARRIER; }
        }

        String enchKey = plugin.getConfig().getString("items." + itemId + ".enchant", "");
        if (!enchKey.isEmpty() && mat == Material.ENCHANTED_BOOK) {
            int level = plugin.getConfig().getInt("items." + itemId + ".enchant-level", 1);
            ItemStack book = ExcellentEnchantsBridge.createBookForMarket(enchKey, level);
            if (book != null) return book;
        }

        return new ItemStack(mat);
    }

    /**
     * Разрешить зачарование (EE или ванильное).
     */
    private static Enchantment resolveEnchantment(String key) {
        Enchantment ee = ExcellentEnchantsBridge.getEnchantment(key);
        if (ee != null) return ee;
        return Enchantment.getByName(key);
    }

    // ═══════════════════════════════════════
    // ДОНАТ-МНОЖИТЕЛИ (ЕДИНЫЙ ИСТОЧНИК)
    // ═══════════════════════════════════════

    public static double donorSellMultiplier(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 1.70;
        if (p.hasPermission("vkchat.donate.legend"))   return 1.50;
        if (p.hasPermission("vkchat.donate.star"))     return 1.35;
        if (p.hasPermission("vkchat.donate.flame"))    return 1.20;
        if (p.hasPermission("vkchat.donate.spark"))    return 1.10;
        return 1.0;
    }

    public static double donorBuyMultiplier(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 0.35;
        if (p.hasPermission("vkchat.donate.legend"))   return 0.50;
        if (p.hasPermission("vkchat.donate.star"))     return 0.65;
        if (p.hasPermission("vkchat.donate.flame"))    return 0.80;
        if (p.hasPermission("vkchat.donate.spark"))    return 0.90;
        return 1.0;
    }

    public static String getDonorStatus(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "Повелитель";
        if (p.hasPermission("vkchat.donate.legend"))   return "Легенда";
        if (p.hasPermission("vkchat.donate.star"))     return "Звезда";
        if (p.hasPermission("vkchat.donate.flame"))    return "Пламя";
        if (p.hasPermission("vkchat.donate.spark"))    return "Искра";
        if (p.hasPermission("vkchat.donate.vip"))      return "VIP";
        return "";
    }

    public static String getSellBonus(org.bukkit.entity.Player p) {
        double mult = donorSellMultiplier(p);
        if (mult > 1.0) return "+" + String.format("%.0f", (mult - 1.0) * 100) + "%";
        return "нет";
    }

    public static String getBuyBonus(org.bukkit.entity.Player p) {
        double mult = donorBuyMultiplier(p);
        if (mult < 1.0) return "-" + String.format("%.0f", (1.0 - mult) * 100) + "%";
        return "нет";
    }

    public static String getLimitBonus(org.bukkit.entity.Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "x2.5";
        if (p.hasPermission("vkchat.donate.legend"))   return "x2.0";
        if (p.hasPermission("vkchat.donate.star"))     return "x1.5";
        if (p.hasPermission("vkchat.donate.flame"))    return "x1.3";
        if (p.hasPermission("vkchat.donate.spark"))    return "x1.1";
        return "стандарт";
    }

    // ═══════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════

    /**
     * Римские цифры для уровней зачарований.
     */
    public static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";    case 2 -> "II";   case 3 -> "III";
            case 4 -> "IV";   case 5 -> "V";    case 6 -> "VI";
            case 7 -> "VII";  case 8 -> "VIII"; case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }
}
