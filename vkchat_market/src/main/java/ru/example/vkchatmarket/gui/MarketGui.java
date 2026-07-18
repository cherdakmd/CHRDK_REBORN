package ru.example.vkchatmarket.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;
import ru.example.vkchatmarket.service.MarketService;

import java.util.*;
import java.util.stream.Collectors;

public class MarketGui {

    private static final int PAGE_SIZE = 35;
    private static final int[] ITEM_SLOTS = {
        10,11,12,13,14,15,16,17,18, 19,20,21,22,23,24,25,26,27,
        28,29,30,31,32,33,34,35,36, 37,38,39,40,41,42,43,44
    };
    private static final int[] CATEGORY_SLOTS = {
        10,11,12,13, 19,20,21,22, 28,29,30,31
    };

    public static void openMainMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lБИРЖА §8◂ §7Меню");
        plugin.getGuiState().set(p.getUniqueId(), "all", 0, null);

        ItemStack bg = bg();
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;
        MarketService svc = plugin.getMarketService();

        String eventLine;
        if (plugin.getMarketService().prices().hasActiveEvent()) {
            long remaining = (plugin.getMarketService().prices().getActiveEventEnd() - System.currentTimeMillis()) / 60000;
            eventLine = "§c⚡ " + plugin.getMarketService().prices().getActiveEventName() + " §7(" + remaining + " мин.)";
        } else {
            eventLine = "§7Рынок стабилен";
        }

        inv.setItem(4, item(Material.GOLD_BLOCK, "§6§lБИРЖА CHRDK",
                "§7Динамический рынок ресурсов",
                "",
                "§eБаланс: §f" + rep + " реп.",
                "§bТоваров: §f" + svc.getAll().size() + " §7в §f" + MarketCategory.values().length + " §7категориях",
                eventLine));

        MarketCategory[] cats = MarketCategory.values();
        for (int i = 0; i < cats.length && i < CATEGORY_SLOTS.length; i++) {
            MarketCategory cat = cats[i];
            String key = cat.configKey();
            int count = svc.getByCategory(cat).size();
            int slot = CATEGORY_SLOTS[i];

            int minP = Integer.MAX_VALUE, maxP = 0;
            for (MarketEntry e : svc.getByCategory(cat)) {
                int bp = e.basePrice();
                if (bp < minP) minP = bp;
                if (bp > maxP) maxP = bp;
            }
            String priceRange = (minP == Integer.MAX_VALUE) ? "" : "§7Цена: §e" + minP + "§7-§e" + maxP;

            String catName = plugin.getCategoriesConfig().getString("categories." + key + ".name", key);
            double trend = plugin.getMarketService().prices().dynamics().getCategoryTrend(cat);
            String trendStr;
            if (trend > 0.05) trendStr = " §a▲";
            else if (trend < -0.05) trendStr = " §c▼";
            else trendStr = " §7─";

            inv.setItem(slot, categoryItem(plugin, cat.icon(), key,
                    catName + trendStr,
                    "§7" + count + " товаров",
                    priceRange));
        }

        var favorites = plugin.getGuiState().get(p.getUniqueId());
        NamespacedKey favKey = new NamespacedKey(plugin, "mkt_fav");
        Set<String> favIds = new HashSet<>();
        if (p.getPersistentDataContainer().has(favKey, PersistentDataType.STRING)) {
            String favStr = p.getPersistentDataContainer().get(favKey, PersistentDataType.STRING);
            if (favStr != null && !favStr.isEmpty()) {
                favIds.addAll(Arrays.asList(favStr.split(",")));
            }
        }
        if (!favIds.isEmpty()) {
            int slot = 37;
            for (String favId : favIds) {
                if (slot > 43) break;
                MarketEntry favEntry = svc.get(favId);
                if (favEntry != null) {
                    inv.setItem(slot++, tradeItem(plugin, favEntry, p));
                }
            }
        }

        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все товары", "§7" + svc.getAll().size() + " товаров"));
        inv.setItem(46, categoryItem(plugin, Material.CHEST, "mkt_cart", "§e🛒 Корзина", "§7Собрать товары для покупки"));
        inv.setItem(47, item(plugin, Material.OAK_SIGN, "mkt_search", "§e🔍 Поиск", "§7Найти товар по названию"));
        inv.setItem(48, item(plugin, Material.EMERALD, "mkt_stats", "§a📊 Статистика", "§7Объём торгов, топ-товары"));
        inv.setItem(49, item(plugin, Material.DIAMOND, "mkt_daily", "§b🎁 Дневной бонус", "§7Забрать §e" + plugin.getSettingsConfig().getInt("settings.daily-reward-amount", 50) + " §7реп."));
        inv.setItem(50, categoryItem(plugin, Material.CLOCK, "mkt_profile", "§6👤 Профиль", "§7Твоя статистика трейдера"));
        inv.setItem(53, item(plugin, Material.BARRIER, "mkt_close", "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    public static void openCategory(VKChatMarketPlugin plugin, Player p, String categoryKey, int page) {
        MarketService svc = plugin.getMarketService();
        List<MarketEntry> entries;
        MarketCategory cat = MarketCategory.fromConfig(categoryKey);

        if (categoryKey.equals("all")) entries = svc.getAll();
        else if (cat != null) entries = svc.getByCategory(cat);
        else entries = svc.getAll();

        var state = plugin.getGuiState().get(p.getUniqueId());
        entries = sortEntries(entries, state.sortMode());

        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));
        plugin.getGuiState().set(p.getUniqueId(), categoryKey, page, null);

        String catName = categoryKey.equals("all") ? "Все товары"
                : plugin.getCategoriesConfig().getString("categories." + categoryKey + ".name", categoryKey);

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §7" + catName + " §8" + (page+1) + "/" + pages);

        ItemStack bg = bg();
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        inv.setItem(4, item(Material.GOLD_INGOT, "§eБаланс: §f" + rep + " реп.",
                "§7Товаров: §f" + entries.size() + " §7на стр. §f" + (page+1) + "/" + pages,
                "§7ЛКМ — продать 1 | ПКМ — купить 1",
                "§7Shift+ЛКМ — продать 64 | Shift+ПКМ — купить 16"));

        int start = page * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], tradeItem(plugin, entries.get(i), p));
        }

        ItemStack navGlass = navBg();
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);
        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));

        if (pages > 1) {
            if (page > 0) inv.setItem(46, item(plugin, Material.ARROW, "mkt_prev", "§f◀ Назад"));
            if (page < pages - 1) inv.setItem(47, item(plugin, Material.ARROW, "mkt_next", "§f▶ Вперёд"));
        }

        inv.setItem(48, item(plugin, Material.HOPPER, "mkt_sellall", "§a♻ Продать всё", "§7Продать все предметы", "§7из этой категории"));
        inv.setItem(49, item(plugin, Material.OAK_SIGN, "mkt_search", "§e🔍 Поиск", "§7Искать по названию"));
        inv.setItem(50, item(plugin, Material.ANVIL, "mkt_amount", "§b✎ Кол-во", "§7Ввести точное", "§7количество"));
        inv.setItem(51, item(plugin, Material.NETHER_STAR, "mkt_sellall_all", "§c💰 Продать ВСЁ", "§7Продать все предметы", "§7изо всех категорий"));
        inv.setItem(52, item(plugin, Material.CHEST, "mkt_cart", "§e🛒 Корзина", "§7Открыть корзину"));
        inv.setItem(53, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все", "§7Без категории"));

        p.openInventory(inv);
    }

    public static void openSortMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lБИРЖА §8◂ §7Сортировка");

        for (int i = 0; i < 27; i++) inv.setItem(i, bg());

        var state = plugin.getGuiState().get(p.getUniqueId());
        PlayerGuiState.SortMode current = state.sortMode();

        inv.setItem(10, sortItem(plugin, Material.EMERALD, PlayerGuiState.SortMode.PRICE, current, "§eПо цене"));
        inv.setItem(13, sortItem(plugin, Material.NAME_TAG, PlayerGuiState.SortMode.NAME, current, "§bПо названию"));
        inv.setItem(16, sortItem(plugin, Material.REDSTONE, PlayerGuiState.SortMode.TREND, current, "§cПо тренду"));

        inv.setItem(22, item(plugin, Material.BARRIER, "mkt_sort_close", "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    public static void openBulkConfirm(VKChatMarketPlugin plugin, Player p, String mode, MarketEntry entry, int amount) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lБИРЖА §8◂ §eПодтверждение");

        for (int i = 0; i < 27; i++) inv.setItem(i, bg());

        int price = "buy".equals(mode) ?
                plugin.getMarketService().prices().getBuyPrice(entry, p, amount) :
                plugin.getMarketService().prices().getSellPrice(entry, p, amount);
        int total = price * amount;

        inv.setItem(11, item(plugin, Material.EMERALD, "mkt_bulk_confirm",
                "§a✓ Подтвердить",
                "§7" + ("buy".equals(mode) ? "Покупка" : "Продажа") + ": §f" + amount + "x §f" + entry.displayName(),
                "§7Сумма: §e" + total + " реп.",
                "",
                "§e▶ Нажми для подтверждения"));

        inv.setItem(15, item(plugin, Material.BARRIER, "mkt_bulk_cancel", "§c✖ Отмена"));

        p.openInventory(inv);
    }

    public static void openCartView(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lБИРЖА §8◂ §e🛒 Корзина");

        for (int i = 0; i < 27; i++) inv.setItem(i, bg());

        var cart = plugin.getGuiState().getCart(p.getUniqueId());
        if (cart.isEmpty()) {
            inv.setItem(13, item(Material.BARRIER, "§7Корзина пуста", "§7Добавляй товары через §e🛒"));
        } else {
            int slot = 10;
            int totalCost = 0;
            for (var ce : cart.entrySet()) {
                if (slot > 16) break;
                MarketEntry me = plugin.getMarketService().get(ce.getKey());
                if (me == null) continue;
                int count = ce.getValue();
                int buyPrice = plugin.getMarketService().prices().getBuyPrice(me, p, count);
                totalCost += buyPrice * count;
                inv.setItem(slot++, tradeItem(plugin, me, p));
            }
            inv.setItem(4, item(Material.EMERALD_BLOCK, "§aИтого: §e" + totalCost + " реп.",
                    "§7Товаров: §f" + cart.values().stream().mapToInt(Integer::intValue).sum(),
                    "",
                    "§e▶ ПКМ = купить все, ЛКМ = убрать"));
            inv.setItem(22, item(plugin, Material.EMERALD, "mkt_cart_buy", "§a✓ Купить всё", "§7За §e" + totalCost + " реп."));
        }

        inv.setItem(18, item(plugin, Material.BARRIER, "mkt_cart_close", "§c✕ Закрыть"));
        p.openInventory(inv);
    }

    public static void openSearchResults(VKChatMarketPlugin plugin, Player p, List<MarketEntry> results, String query, int page) {
        int pages = Math.max(1, (int) Math.ceil(results.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));
        plugin.getGuiState().set(p.getUniqueId(), "search", page, query);

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §7Поиск: §f" + query + " §8" + (page+1) + "/" + pages);

        ItemStack bg = bg();
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        inv.setItem(4, item(Material.GOLD_INGOT, "§eБаланс: §f" + rep + " реп.",
                "§7Результатов: §f" + results.size(),
                "§7ЛКМ — продать | ПКМ — купить"));

        int start = page * PAGE_SIZE;
        int end = Math.min(results.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], tradeItem(plugin, results.get(i), p));
        }

        ItemStack navGlass = navBg();
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);
        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));

        if (pages > 1) {
            if (page > 0) inv.setItem(46, item(plugin, Material.ARROW, "mkt_prev", "§f◀ Назад"));
            if (page < pages - 1) inv.setItem(47, item(plugin, Material.ARROW, "mkt_next", "§f▶ Вперёд"));
        }

        inv.setItem(49, item(plugin, Material.OAK_SIGN, "mkt_search", "§e🔍 Новый поиск", "§7Искать заново"));
        inv.setItem(53, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все", "§7Без категории"));

        p.openInventory(inv);
    }

    public static void openSearchResults(VKChatMarketPlugin plugin, Player p, List<MarketEntry> results, String query) {
        openSearchResults(plugin, p, results, query, 0);
    }

    public static void openSellAllConfirm(VKChatMarketPlugin plugin, Player p, String categoryKey, int totalRep, int itemCount) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lБИРЖА §8◂ §cПодтверждение");

        for (int i = 0; i < 27; i++) inv.setItem(i, bg());

        inv.setItem(11, item(plugin, Material.EMERALD, "mkt_sellall_confirm",
                "§a✓ Продать всё",
                "§7Предметов: §e" + itemCount,
                "§7Выручка: §a" + totalRep + " реп.",
                "",
                "§e▶ Нажми для подтверждения"));

        inv.setItem(15, categoryItem(plugin, Material.BARRIER, categoryKey, "§c✖ Отмена", "§7Вернуться назад"));

        p.openInventory(inv);
    }

    public static void openSellAllPreview(VKChatMarketPlugin plugin, Player p, String categoryKey) {
        MarketService svc = plugin.getMarketService();
        Map<String, Integer> breakdown = svc.getCategoryBreakdown(p);

        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lБИРЖА §8◂ §cПродажа всех");

        for (int i = 0; i < 27; i++) inv.setItem(i, bg());

        int totalItems = 0;
        int totalRep = 0;
        int slot = 10;
        for (var entry : breakdown.entrySet()) {
            if (slot > 16) break;
            String catKey = entry.getKey();
            int count = entry.getValue();
            MarketCategory cat = MarketCategory.fromConfig(catKey);
            String catName = plugin.getCategoriesConfig().getString("categories." + catKey + ".name", catKey);

            int catRep = 0;
            if (cat != null) {
                for (MarketEntry me : svc.getByCategory(cat)) {
                    int owned = svc.countItems(p, me);
                    if (owned > 0) catRep += svc.prices().getSellPrice(me, p) * owned;
                }
            }

            inv.setItem(slot++, sellAllCategoryItem(plugin, cat != null ? cat.icon() : Material.PAPER, catKey,
                    catName,
                    "§7Предметов: §e" + count,
                    "§7Выручка: §a" + catRep + " реп."));

            totalItems += count;
            totalRep += catRep;
        }

        inv.setItem(4, item(Material.EMERALD_BLOCK, "§aИтого: §e" + totalRep + " реп. §7за §f" + totalItems + " §7шт.",
                "", "§e▶ Нажми на категорию для продажи"));

        inv.setItem(22, categoryItem(plugin, Material.BARRIER, categoryKey, "§c✖ Отмена", "§7Вернуться назад"));

        p.openInventory(inv);
    }

    // === ITEM BUILDERS ===

    static ItemStack tradeItem(VKChatMarketPlugin plugin, MarketEntry entry, Player p) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        var prices = plugin.getMarketService().prices();
        var dynamics = prices.dynamics();
        int sellPrice = prices.getSellPrice(entry, p);
        int buyPrice = prices.getBuyPrice(entry, p);
        int owned = plugin.getMarketService().countItems(p, entry);
        int maxBuyable = prices.getMaxBuyable(p, entry);
        int maxSellable = prices.getMaxSellable(p, entry);
        String trend = prices.trendArrow(entry);
        String momentum = dynamics.getMomentumArrow(entry);
        String donorTag = prices.donorTag(p);

        meta.setDisplayName("§r" + entry.displayName() + trend + momentum);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§aПродажа: §f" + sellPrice + " реп. / шт.");
        lore.add("§cПокупка: §f" + buyPrice + " реп. / шт.");
        lore.add("");
        lore.add("§7У тебя: §e" + owned + " шт.");
        if (maxSellable > 0) lore.add("§7Макс. продать: §e" + maxSellable);
        if (maxBuyable > 0) lore.add("§7Макс. купить: §e" + maxBuyable);
        if (owned < 5 && owned > 0) lore.add("§c⚠ Мало!");
        if (donorTag != null) {
            double buyMult = prices.donorBuyMultVisible(p);
            double sellMult = prices.donorSellMultVisible(p);
            lore.add("§6⭐ " + donorTag + "§7: покупка §a" + (int)((1.0 - buyMult) * 100) + "%§7, продажа §a+" + (int)((sellMult - 1.0) * 100) + "%");
        }
        lore.add("");
        lore.add("§7Рынок: " + dynamics.getMarketHealthBar(entry) + " " + dynamics.getMarketHealthText(entry));
        String sparkline = dynamics.getPriceSparkline(entry);
        if (!sparkline.equals("§7─")) lore.add("§7Тренд: " + sparkline);
        lore.add("");
        lore.add("§7§oЛКМ-продать | ПКМ-купить");
        lore.add("§7§oПКМ+Shift-купить 16 | СКМ-ввести кол-во");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mkt_item"), PersistentDataType.STRING, entry.id());
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack categoryItem(VKChatMarketPlugin plugin, Material mat, String catKey, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        List<String> l = new ArrayList<>(Arrays.asList(lore));
        meta.setLore(l);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mkt_cat"), PersistentDataType.STRING, catKey);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack sellAllCategoryItem(VKChatMarketPlugin plugin, Material mat, String catKey, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(Arrays.asList(lore)));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mkt_sellall_confirm"), PersistentDataType.STRING, catKey);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack item(VKChatMarketPlugin plugin, Material mat, String pdcKey, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        if (pdcKey != null) meta.getPersistentDataContainer().set(new NamespacedKey(plugin, pdcKey), PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        return item;
    }

    static Material getCatIcon(MarketCategory cat) { return cat.icon(); }

    static ItemStack item(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta(); if (m != null) { m.setDisplayName(name); m.setLore(Arrays.asList(lore)); i.setItemMeta(m); }
        return i;
    }

    private static ItemStack sortItem(VKChatMarketPlugin plugin, Material mat, PlayerGuiState.SortMode mode, PlayerGuiState.SortMode current, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        boolean active = mode == current;
        meta.setDisplayName((active ? "§a✓ " : "§7") + name);
        if (active) meta.setDisplayName("§a✓ " + name + " §7(активно)");
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mkt_sort"), PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        return item;
    }

    private static List<MarketEntry> sortEntries(List<MarketEntry> entries, PlayerGuiState.SortMode mode) {
        List<MarketEntry> sorted = new ArrayList<>(entries);
        switch (mode) {
            case PRICE:
                sorted.sort(Comparator.comparingInt(MarketEntry::basePrice));
                break;
            case NAME:
                sorted.sort(Comparator.comparing(e -> org.bukkit.ChatColor.stripColor(e.displayName())));
                break;
            case TREND:
                break;
            default:
                break;
        }
        return sorted;
    }

    static ItemStack bg() {
        ItemStack i = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta(); if (m != null) { m.setDisplayName(" "); i.setItemMeta(m); }
        return i;
    }

    static ItemStack navBg() {
        ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta(); if (m != null) { m.setDisplayName(" "); i.setItemMeta(m); }
        return i;
    }
}
