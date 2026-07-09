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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                "§7Рынок ресурсов сервера",
                "",
                "§eБаланс: §f" + rep + " реп.",
                "§bТоваров: §f" + svc.getAll().size()));

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

        inv.setItem(45, item(Material.BARRIER, "§c✕ Закрыть"));
        inv.setItem(49, item(Material.COMPASS, "§b📦 Все товары", "§7Показать всё",
                "",
                "§e▶ Нажми"));
        inv.setItem(53, item(Material.COMPASS, "§b📦 Все товары", "§7Показать всё",
                "",
                "§e▶ Нажми"));
        inv.setItem(49, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все товары", "§7" + svc.getAll().size() + " товаров"));

        p.openInventory(inv);
    }

    public static void openCategory(VKChatMarketPlugin plugin, Player p, String categoryKey, int page) {
        MarketService svc = plugin.getMarketService();
        List<MarketEntry> entries;
        MarketCategory cat = MarketCategory.fromConfig(categoryKey);

        if (categoryKey.equals("all")) entries = svc.getAll();
        else if (cat != null) entries = svc.getByCategory(cat);
        else entries = svc.getAll();

        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        String catName = categoryKey.equals("all") ? "Все товары"
                : plugin.getConfig().getString("categories." + categoryKey + ".name", categoryKey);

        int rep = VKChatBridge.getLinkedVkId(p) != -1 ? VKChatBridge.getReputation(VKChatBridge.getLinkedVkId(p)) : 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §7" + catName + " §8" + (page+1) + "/" + pages);

        ItemStack bg = bg();
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        inv.setItem(4, item(Material.GOLD_INGOT, "§eБаланс: §f" + rep + " реп.",
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
        if (page > 0)
            inv.setItem(48, navItem(plugin, Material.SPECTRAL_ARROW, "§e◀ Назад", page - 1, categoryKey));
        inv.setItem(50, categoryItem(plugin, Material.COMPASS, "all", "§b📦 Все", "§7Без категории"));
        if (page < pages - 1)
            inv.setItem(53, navItem(plugin, Material.SPECTRAL_ARROW, "§eВперёд ▶", page + 1, categoryKey));

        p.openInventory(inv);
    }

    private static ItemStack tradeItem(VKChatMarketPlugin plugin, MarketEntry entry, Player p) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int sellPrice = plugin.getMarketService().prices().getSellPrice(entry);
        int buyPrice = plugin.getMarketService().prices().getBuyPrice(entry);
        int owned = plugin.getMarketService().countItems(p, entry);

        meta.setDisplayName("§r" + entry.displayName());
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§aПродажа: §f" + sellPrice + " реп. / шт.");
        lore.add("§cПокупка: §f" + buyPrice + " реп. / шт.");
        lore.add("");
        lore.add("§7У тебя: §e" + owned + " шт.");
        lore.add("");
        lore.add("§7§oЛКМ — продать | ПКМ — купить");
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

    static ItemStack navItem(VKChatMarketPlugin plugin, Material mat, String name, int page, String catKey) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mkt_nav"), PersistentDataType.INTEGER, page);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "mkt_cat"), PersistentDataType.STRING, catKey);
        item.setItemMeta(meta);
        return item;
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
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
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
