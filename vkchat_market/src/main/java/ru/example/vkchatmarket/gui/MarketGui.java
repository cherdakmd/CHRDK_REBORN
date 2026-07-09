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

public class MarketGui {

    private static final int PAGE_SIZE = 35;
    private static final int[] ITEM_SLOTS = {
        10,11,12,13,14,15,16,17,18, 19,20,21,22,23,24,25,26,27,
        28,29,30,31,32,33,34,35,36, 37,38,39,40,41,42,43,44
    };

    public static void openMainMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lБИРЖА §8◂ §7Меню");
        ItemStack bg = bg();
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;
        MarketService svc = plugin.getMarketService();

        inv.setItem(4, item(Material.GOLD_BLOCK, "§6§lБИРЖА CHRDK",
                "§7Динамический рынок ресурсов",
                "",
                "§eБаланс: §f" + rep + " реп.",
                "§bТоваров: §f" + svc.getAll().size(),
                plugin.getMarketService().prices().hasActiveEvent()
                    ? "§c⚡ " + plugin.getMarketService().prices().getActiveEventName()
                    : "§7Рынок стабилен"));

        int[] catSlots = {20,21,22,23, 29,30,31,32};
        MarketCategory[] cats = MarketCategory.values();
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            MarketCategory cat = cats[i];
            String key = cat.configKey();
            int count = svc.getByCategory(cat).size();
            inv.setItem(catSlots[i], categoryItem(plugin,
                    getCatIcon(cat), key, plugin.getConfig().getString("categories." + key + ".name", key),
                    "§7" + count + " товаров"));
        }

        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все товары", "§7" + svc.getAll().size() + " товаров"));
        inv.setItem(49, item(plugin, Material.OAK_SIGN, "mkt_search", "§e🔍 Поиск", "§7Найти товар по названию"));
        inv.setItem(53, item(plugin, Material.BARRIER, null, "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    public static void openCategory(VKChatMarketPlugin plugin, Player p, String categoryKey, int page) {
        MarketService svc = plugin.getMarketService();
        List<MarketEntry> entries;
        MarketCategory cat = MarketCategory.fromConfig(categoryKey);

        if (categoryKey.equals("all")) entries = svc.getAll();
        else if (categoryKey.equals("search")) entries = new ArrayList<>(); // handled separately
        else if (cat != null) entries = svc.getByCategory(cat);
        else entries = svc.getAll();

        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        String catName = getCategoryName(plugin, categoryKey);

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §7" + catName + " §8" + (page+1) + "/" + pages);

        ItemStack bg = bg();
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        inv.setItem(4, item(Material.GOLD_INGOT, "§eБаланс: §f" + rep + " реп.",
                "§7ЛКМ — продать 1 | ПКМ — купить 1",
                "§7Shift+ЛКМ — продать 64 | Shift+ПКМ — купить 16",
                "§7Средняя кнопка — ввести количество"));

        int start = page * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], tradeItem(plugin, entries.get(i), p));
        }

        ItemStack navGlass = navBg();
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);
        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));
        inv.setItem(47, item(plugin, Material.HOPPER, "mkt_sellall", "§a♻ Продать всё", "§7Продать все предметы", "§7из этой категории"));
        inv.setItem(49, item(plugin, Material.OAK_SIGN, "mkt_search", "§e🔍 Поиск", "§7Искать по названию"));
        inv.setItem(51, item(plugin, Material.ANVIL, "mkt_amount", "§b✎ Кол-во", "§7Ввести точное", "§7количество"));
        inv.setItem(53, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все", "§7Без категории"));

        p.openInventory(inv);
    }

    public static void openSearchResults(VKChatMarketPlugin plugin, Player p, List<MarketEntry> results, String query) {
        MarketService svc = plugin.getMarketService();
        int pages = Math.max(1, (int) Math.ceil(results.size() / (double) PAGE_SIZE));

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §7Поиск: " + query + " §81/" + pages);

        ItemStack bg = bg();
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        inv.setItem(4, item(Material.GOLD_INGOT, "§eБаланс: §f" + rep + " реп.",
                "§7Результаты поиска: " + results.size(),
                "§7ЛКМ — продать | ПКМ — купить"));

        int end = Math.min(results.size(), PAGE_SIZE);
        for (int i = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[i], tradeItem(plugin, results.get(i), p));
        }

        ItemStack navGlass = navBg();
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);
        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));
        inv.setItem(49, item(plugin, Material.OAK_SIGN, "mkt_search", "§e🔍 Новый поиск", "§7Искать заново"));
        inv.setItem(53, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все", "§7Без категории"));

        p.openInventory(inv);
    }

    public static void openSellAllConfirm(VKChatMarketPlugin plugin, Player p, String categoryKey, int totalRep, int itemCount) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lБИРЖА §8◂ §cПодтверждение");

        for (int i = 0; i < 27; i++) inv.setItem(i, bg());

        inv.setItem(11, item(plugin, Material.EMERALD, "mkt_sellall_confirm§" + categoryKey,
                "§a✓ Продать всё",
                "§7Предметов: §e" + itemCount,
                "§7Выручка: §a" + totalRep + " реп.",
                "",
                "§e▶ Нажми для подтверждения"));

        inv.setItem(15, categoryItem(plugin, Material.BARRIER, categoryKey, "§c✖ Отмена", "§7Вернуться назад"));

        p.openInventory(inv);
    }

    static ItemStack tradeItem(VKChatMarketPlugin plugin, MarketEntry entry, Player p) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int sellPrice = plugin.getMarketService().prices().getSellPrice(entry);
        int buyPrice = plugin.getMarketService().prices().getBuyPrice(entry);
        int owned = plugin.getMarketService().countItems(p, entry);
        String trend = plugin.getMarketService().prices().trendArrow(entry);

        meta.setDisplayName("§r" + entry.displayName() + trend);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§aПродажа: §f" + sellPrice + " реп. / шт.");
        lore.add("§cПокупка: §f" + buyPrice + " реп. / шт.");
        lore.add("");
        lore.add("§7У тебя: §e" + owned + " шт.");
        lore.add("");
        lore.add("§7§oЛКМ-продать | ПКМ-купить | СКМ-кол-во");
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

    static String getCategoryName(VKChatMarketPlugin plugin, String key) {
        if (key.equals("all")) return "Все товары";
        if (key.equals("search")) return "Поиск";
        return plugin.getConfig().getString("categories." + key + ".name", key);
    }

    static Material getCatIcon(MarketCategory cat) {
        return switch (cat) {
            case ORES -> Material.IRON_INGOT;
            case FOOD -> Material.BREAD;
            case WOOD -> Material.OAK_LOG;
            case BLOCKS -> Material.STONE;
            case MOBS -> Material.BONE;
            case DECOR -> Material.WHITE_WOOL;
        };
    }

    static ItemStack item(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta(); if (m != null) { m.setDisplayName(name); m.setLore(Arrays.asList(lore)); i.setItemMeta(m); }
        return i;
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
