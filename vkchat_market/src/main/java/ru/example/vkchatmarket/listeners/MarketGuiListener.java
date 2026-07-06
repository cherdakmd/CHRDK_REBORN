package ru.example.vkchatmarket.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.enchantments.Enchantment;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarketGuiListener implements Listener {
    private static final int PAGE_SIZE = 36;
    private final VKChatMarketPlugin plugin;

    // Слоты для сетки товаров (4 ряда по 9)
    private static final int[] ITEM_SLOTS = {0,1,2,3,4,5,6,7,8, 9,10,11,12,13,14,15,16,17,
            18,19,20,21,22,23,24,25,26, 27,28,29,30,31,32,33,34,35};
    // Нижняя навигация (ряд 5+6)
    private static final int NAV_ROW = 45;

    public MarketGuiListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof org.bukkit.entity.Villager) {
            org.bukkit.entity.Villager npc = (org.bukkit.entity.Villager) e.getRightClicked();
            NamespacedKey key = new NamespacedKey(plugin, "market_npc");
            if (npc.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                e.setCancelled(true);
                openGui(plugin, e.getPlayer());
            }
        }
    }

    public static void openGui(VKChatMarketPlugin plugin, Player p) {
        openCategoryMenu(plugin, p);
    }

    public static void openGui(VKChatMarketPlugin plugin, Player p, String category) {
        if (category == null || category.equalsIgnoreCase("all") || category.equalsIgnoreCase("menu")) openCategoryMenu(plugin, p);
        else openGui(plugin, p, 0, category);
    }

    // ═══════════════════════════════════════════════════════════════
    // ГЛАВНОЕ МЕНЮ КАТЕГОРИЙ
    // ═══════════════════════════════════════════════════════════════
    public static void openCategoryMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lБИРЖА §8◂ §7Главное меню");

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) ? border : accent);

        // ═══ ШАПКА ═══
        int vkId = VKChatBridge.getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;
        inv.setItem(4, item(Material.GOLD_BLOCK,
                "§6§l⚡ БИРЖА CHRDK",
                "§7Динамическая экономика сервера",
                "",
                "§e💰 Баланс: §f" + rep + " реп.",
                "§b📦 Товаров: §f" + getConfiguredItems(plugin, "all").size(),
                "§d📈 Цикл: §f" + plugin.getMarketManager().getMarketCycleLabel(),
                "",
                "§7Клик на товар = детали и торговля"));

        // Донат-статус
        String donor = getDonorStatus(p);
        if (!donor.isEmpty()) {
            inv.setItem(0, item(Material.NETHER_STAR,
                    "§e§l⭐ " + donor,
                    "§7Продажа: §a+" + getSellBonus(p),
                    "§7Покупка: §a" + getBuyBonus(p),
                    "§7Лимиты: §a" + getLimitBonus(p)));
        }

        // ═══ КАТЕГОРИИ — сетка 4×2 ═══
        int[] catSlots = {10,11,12,13, 19,20,21,22};
        String[][] cats = {
            {"ores", "§c⛏ §fРуды/слитки", "§7Железо, золото, алмазы..."},
            {"food", "§6🍞 §fЕда и ферма", "§7Хлеб, мясо, урожай..."},
            {"wood", "§a🌲 §fДерево", "§7Доски, брёвна..."},
            {"blocks", "§7🧱 §fСтройматериалы", "§7Камень, кирпич, стекло..."},
            {"earth", "§8🟫 §fЗемля и природа", "§7Земля, гравий, мох..."},
            {"mob", "§5☠ §fЛут мобов", "§7Кости, порох, кожа..."},
            {"decor", "§d🎨 §fДекор", "§7Шерсть, краски, терракота..."},
            {"all", "§b📦 §fВсе товары", "§7Полный список без фильтра"},
        };
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            inv.setItem(catSlots[i], categoryItem(plugin, getCatIcon(cats[i][0]), cats[i][0], cats[i][1], cats[i][2]));
        }

        // ═══ НИЖНИЙ РЯД ═══
        inv.setItem(37, item(Material.CLOCK, "§b📈 Тренды дня", "§7Горячие товары и история", "", "§e▶ Нажми"));
        inv.setItem(40, item(Material.DIAMOND, "§d💎 Редкости дня", "§7Лимитированные товары", "§7Ротация каждый день", "", "§e▶ Нажми"));
        inv.setItem(43, item(Material.DIAMOND_SWORD, "§a📋 Квест дня", "§7" + plugin.getMarketFun().getQuestInfo(), "", "§aНаграда: 1000 реп. ВК"));
        inv.setItem(49, item(Material.HOPPER, "§c📤 Продать всё", "§7Продажа всех ненужных", "§7предметов из инвентаря", "", "§e▶ Нажми для продажи"));

        inv.setItem(45, item(Material.BARRIER, "§c✕ Закрыть"));
        inv.setItem(53, item(Material.ARROW, "§e→ Далее", "§7Тренды и редкие товары"));

        p.openInventory(inv);
    }

    private static Material getCatIcon(String cat) {
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

    // ═══════════════════════════════════════════════════════════════
    // ОТКРЫТИЕ КАТЕГОРИИ С ТОВАРАМИ
    // ═══════════════════════════════════════════════════════════════
    public static void openGui(VKChatMarketPlugin plugin, Player p, int page, String category) {
        category = normalizeCategory(category);
        if (category.equals("trends")) { openTrendsMenu(plugin, p); return; }
        if (category.equals("limited")) { openLimitedGui(plugin, p, page); return; }

        List<String> ids = getConfiguredItems(plugin, category);
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        String catName = getCategoryDisplayName(category);
        int vkId = VKChatBridge.getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;

        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lБИРЖА §8◂ §7" + catName + " §8" + (page+1) + "/" + pages);

        // Сетка товаров
        ItemStack bg = item(Material.BLACK_STAINED_GLASS_PANE, "§0");
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], createMarketItem(plugin, ids.get(i)));
        }

        // ═══ НИЖНЯЯ ПАНЕЛЬ ═══
        ItemStack navGlass = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);

        if (page > 0) inv.setItem(45, navItem(plugin, Material.SPECTRAL_ARROW, "§e◀ Назад", page - 1, category));
        inv.setItem(47, categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));
        inv.setItem(49, item(Material.BOOK, "§6" + catName + " §8[" + (page+1) + "/" + pages + "]", "§7Товаров: §f" + ids.size(), "§e💰 Баланс: §f" + rep + " реп."));
        inv.setItem(51, item(Material.HOPPER, "§c📤 Продать всё", "§7Все предметы из инвентаря", "§7подходящие под категорию"));
        if (page < pages - 1) inv.setItem(53, navItem(plugin, Material.SPECTRAL_ARROW, "§eВперёд ▶", page + 1, category));

        // Быстрые категории
        inv.setItem(46, categoryItem(plugin, Material.IRON_INGOT, "ores", "§7⛏ Руды", "§7"));
        inv.setItem(48, categoryItem(plugin, Material.GOLDEN_CARROT, "food", "§7🍞 Еда", "§7"));
        inv.setItem(50, categoryItem(plugin, Material.OAK_LOG, "wood", "§7🌲 Дерево", "§7"));
        inv.setItem(52, categoryItem(plugin, Material.STONE, "blocks", "§7🧱 Блоки", "§7"));

        p.openInventory(inv);
    }

    private static void openLimitedGui(VKChatMarketPlugin plugin, Player p, int page) {
        List<String> ids = getLimitedItems(plugin);
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lБИРЖА §8◂ §dРедкости §8" + (page+1) + "/" + pages);

        ItemStack bg = item(Material.BLACK_STAINED_GLASS_PANE, "§0");
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], createLimitedItem(plugin, ids.get(i)));
        }

        ItemStack navGlass = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);
        if (page > 0) inv.setItem(45, navItem(plugin, Material.SPECTRAL_ARROW, "§e◀ Назад", page - 1, "limited"));
        inv.setItem(49, categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));
        if (page < pages - 1) inv.setItem(53, navItem(plugin, Material.SPECTRAL_ARROW, "§eВперёд ▶", page + 1, "limited"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // МЕНЮ ТРЕНДОВ
    // ═══════════════════════════════════════════════════════════════
    public static void openTrendsMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l▸ §6§lБИРЖА §8§l◂ §bТренды");

        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        inv.setItem(4, item(Material.CLOCK,
                "§b§l📈 Тренды дня",
                "§7" + plugin.getMarketManager().economyAuditLine(),
                "§7Цикл: " + plugin.getMarketManager().getMarketCycleLabel(),
                "",
                "§eТренд обновляется каждый день"));

        // Топ тренды
        List<String> top = plugin.getMarketManager().getTopTrends(14);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            inv.setItem(slots[i], createTrendItem(plugin, top.get(i)));
        }

        // История
        List<String> history = plugin.getMarketManager().getHistoryTail(8);
        List<String> lore = new ArrayList<>();
        if (history.isEmpty()) lore.add("§7История пока пуста.");
        else for (String h : history) lore.add("§7" + h);
        inv.setItem(31, item(Material.WRITABLE_BOOK, "§6§l🧾 История рынка", lore.toArray(new String[0])));

        // Квест дня
        inv.setItem(33, item(Material.PAPER,
                "§e§l📋 Квест дня",
                "§7" + plugin.getMarketFun().getQuestInfo(),
                "",
                "§aНаграда: 1000 реп."));

        // Flash Sale
        if (plugin.getMarketFun().getFlashSaleItemId() != null) {
            String name = plugin.getConfig().getString("items." + plugin.getMarketFun().getFlashSaleItemId() + ".name", plugin.getMarketFun().getFlashSaleItemId());
            int percent = (int) (plugin.getMarketFun().getFlashSaleDiscount() * 100);
            long remaining = (plugin.getMarketFun().getFlashSaleEndTime() - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
                inv.setItem(35, item(Material.FIREWORK_ROCKET,
                        "§d§l⚡ Flash Sale",
                        "§7" + name,
                        "§aСкидка: -" + percent + "%",
                        "§7Осталось: §e" + remaining + " сек."));
            }
        }

        // Нижняя панель
        ItemStack bottomGlass = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, bottomGlass);
        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "menu", "§f§l🏠 Меню", "§7К категориям"));
        inv.setItem(49, sellAllItem(plugin));
        inv.setItem(53, categoryItem(plugin, Material.DIAMOND, "limited", "§d§l💎 Редкости дня", "§7Открыть ротацию"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // СОЗДАНИЕ ПРЕДМЕТОВ РЫНКА
    // ═══════════════════════════════════════════════════════════════
    private static Material getMarketMaterial(VKChatMarketPlugin plugin, String itemId) {
        String matName = plugin.getConfig().getString("items." + itemId + ".material", "");
        if (!matName.isEmpty()) {
            try { return Material.valueOf(matName); } catch (Exception e) {}
        }
        try { return Material.valueOf(itemId); } catch (Exception e) { return Material.BARRIER; }
    }

    private static ItemStack createCustomItem(VKChatMarketPlugin plugin, String itemId) {
        Material mat = getMarketMaterial(plugin, itemId);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String enchantStr = plugin.getConfig().getString("items." + itemId + ".enchant", "");
        if (!enchantStr.isEmpty() && mat == Material.ENCHANTED_BOOK) {
            try {
                Enchantment ench = Enchantment.getByName(enchantStr);
                int level = plugin.getConfig().getInt("items." + itemId + ".enchant-level", 1);
                if (ench != null) meta.addEnchant(ench, level, true);
            } catch (Exception ignored) {}
        }
        item.setItemMeta(meta);
        return item;
    }

    private static boolean customItemMatches(VKChatMarketPlugin plugin, String itemId, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        Material mat = getMarketMaterial(plugin, itemId);
        if (stack.getType() != mat) return false;

        String enchantStr = plugin.getConfig().getString("items." + itemId + ".enchant", "");
        if (!enchantStr.isEmpty() && mat == Material.ENCHANTED_BOOK && stack.getItemMeta().hasEnchants()) {
            try {
                Enchantment ench = Enchantment.getByName(enchantStr);
                int level = plugin.getConfig().getInt("items." + itemId + ".enchant-level", 1);
                return ench != null && stack.getItemMeta().getEnchantLevel(ench) == level;
            } catch (Exception e) { return false; }
        }
        return true;
    }

    private static ItemStack createMarketItem(VKChatMarketPlugin plugin, String itemId) {
        String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
        double sellPrice = plugin.getMarketManager().getCurrentPrice(itemId);
        double buyPrice = plugin.getMarketManager().getBuyPrice(itemId);
        double basePrice = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);

        ItemStack item = createCustomItem(plugin, itemId);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();

        // Активные события
        boolean isEvent = false;
        for (ru.example.vkchatmarket.data.MarketManager.MarketEvent ev : plugin.getMarketManager().getActiveEvents()) {
            if (ev.itemId.equals(itemId)) {
                long minLeft = Math.max(1, (ev.expireTime - System.currentTimeMillis()) / 60000);
                lore.add("§d⚡ " + ev.name + " §7(ещё " + minLeft + " мин)");
                isEvent = true; break;
            }
        }

        // Flash sale
        if (!isEvent && plugin.getMarketFun().isFlashSaleActive(itemId)) {
            lore.add("§e⚡ FLASH SALE §7— скидка §c" + (int)(plugin.getMarketFun().getFlashSaleDiscount()*100) + "%§7!");
            isEvent = true;
        }

        lore.add("");
        double delta = basePrice > 0 ? ((sellPrice - basePrice) / basePrice) * 100.0 : 0;
        String trend = delta >= 5 ? " §a▲" : delta <= -5 ? " §c▼" : "";
        lore.add("§6💰 Продажа: §e" + String.format("%.0f", sellPrice) + " реп" + trend);
        lore.add("§6🛒 Покупка: §e" + String.format("%.0f", buyPrice) + " реп");

        int stock = plugin.getMarketManager().getStock(itemId);
        int scarcityThreshold = plugin.getConfig().getInt("items." + itemId + ".scarcity-threshold", 0);
        String stockIcon;
        if (stock <= -50) stockIcon = "§4💀 КРИТ. ДЕФИЦИТ";
        else if (stock <= 0) stockIcon = "§c⚠ Дефицит";
        else if (scarcityThreshold > 0 && stock <= scarcityThreshold) stockIcon = "§e⚠ Мало: §f" + stock;
        else stockIcon = "§a✓ В наличии: §f" + stock;
        lore.add("§7" + stockIcon);

        lore.add("");
        lore.add("§eЛКМ — продать §7| §eПКМ — купить §7| §eShift — купить 16");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_item"), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createLimitedItem(VKChatMarketPlugin plugin, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { m = Material.BARRIER; }
        String name = plugin.getConfig().getString("limited-items." + itemId + ".name", itemId);
        int price = plugin.getConfig().getInt("limited-items." + itemId + ".price", 1000);
        int limit = plugin.getConfig().getInt("limited-items." + itemId + ".daily-limit", 1);
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
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
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_limited_item"), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createTrendItem(VKChatMarketPlugin plugin, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { m = Material.PAPER; }
        String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
        double price = plugin.getMarketManager().getCurrentPrice(itemId);
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double delta = base <= 0 ? 0 : ((price - base) / base) * 100.0;
        return item(m,
                "§f" + ChatColor.translateAlternateColorCodes('&', name),
                "§7Тренд: " + plugin.getMarketManager().getTrendLabel(itemId),
                "§7Цена: §a" + String.format("%.2f", price) + " реп.",
                "§7Отклонение: " + (delta >= 0 ? "§a+" : "§c") + String.format("%.1f", delta) + "%",
                "§8Оборот: " + plugin.getMarketManager().getDailyVolume(itemId) + " шт.");
    }

    // ═══════════════════════════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ
    // ═══════════════════════════════════════════════════════════════
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.contains("БИРЖА")) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        ItemMeta meta = e.getCurrentItem().getItemMeta();

        NamespacedKey itemKey = new NamespacedKey(plugin, "market_item");
        NamespacedKey navKey = new NamespacedKey(plugin, "market_nav_page");
        NamespacedKey catKey = new NamespacedKey(plugin, "market_category");
        NamespacedKey sellAllKey = new NamespacedKey(plugin, "market_sell_all");
        NamespacedKey confirmSellAllKey = new NamespacedKey(plugin, "market_confirm_sell_all");
        NamespacedKey limitedKey = new NamespacedKey(plugin, "market_limited_item");
        String category = getCategoryFromTitle(title);

        // Тренды
        if (title.contains("Тренды")) {
            if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
                String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
                if ("menu".equals(cat)) openCategoryMenu(plugin, p);
                else openGui(plugin, p, 0, cat);
                return;
            }
            if (meta.getPersistentDataContainer().has(sellAllKey, PersistentDataType.INTEGER)) {
                openSellAllConfirm(p, category);
                return;
            }
            return;
        }

        // Продать всё
        if (meta.getPersistentDataContainer().has(sellAllKey, PersistentDataType.INTEGER)) {
            openSellAllConfirm(p, category);
            return;
        }
        if (meta.getPersistentDataContainer().has(confirmSellAllKey, PersistentDataType.INTEGER)) {
            sellAllSellable(p);
            openGui(plugin, p, getPageFromTitle(title), category);
            return;
        }

        // Лимитированные
        if (meta.getPersistentDataContainer().has(limitedKey, PersistentDataType.STRING)) {
            buyLimitedItem(p, meta.getPersistentDataContainer().get(limitedKey, PersistentDataType.STRING));
            openGui(plugin, p, getPageFromTitle(title), category);
            return;
        }

        // Категории
        if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            if ("menu".equals(cat)) openCategoryMenu(plugin, p);
            else openGui(plugin, p, 0, cat);
            return;
        }

        // Навигация
        if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.INTEGER)) {
            openGui(plugin, p, meta.getPersistentDataContainer().get(navKey, PersistentDataType.INTEGER), category);
            return;
        }

        // Торговля
        if (!meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return;
        String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        ClickType click = e.getClick();
        if (click == ClickType.LEFT) sellItems(p, itemId, -1);
        else if (click == ClickType.SHIFT_LEFT) sellItems(p, itemId, 64);
        else if (click == ClickType.RIGHT) buyItems(p, itemId, 1);
        else if (click == ClickType.SHIFT_RIGHT) buyItems(p, itemId, 16);
        openGui(plugin, p, getPageFromTitle(title), category);
    }

    // ═══════════════════════════════════════════════════════════════
    // ТОРГОВЫЕ ОПЕРАЦИИ
    // ═══════════════════════════════════════════════════════════════
    private void sellItems(Player p, String itemId, int limit) {
        Material m = getMarketMaterial(plugin, itemId);
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage("§cПривяжи ВК (/vklink) для торговли!"); return; }

        int count = 0;
        boolean isCustom = !plugin.getConfig().getString("items." + itemId + ".enchant", "").isEmpty();
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == m) {
                if (isCustom) {
                    if (!customItemMatches(plugin, itemId, item)) continue;
                } else {
                    if (item.hasItemMeta() && item.getItemMeta().hasLore()) continue;
                }
                int can = limit < 0 ? item.getAmount() : Math.min(item.getAmount(), Math.max(0, limit - count));
                count += can;
                if (limit > 0 && count >= limit) break;
            }
        }
        if (count == 0) { p.sendMessage("§cНет предметов для продажи!"); return; }

        if (!plugin.getMarketManager().canTrade(itemId, p)) { p.sendMessage("§cПодождите..."); return; }

        double donorMult = donorSellMultiplier(p);
        int rep = plugin.getMarketManager().sellItems(itemId, count, donorMult);
        if (rep <= 0) { p.sendMessage("§cРынок переполнен!"); return; }
        plugin.getMarketManager().markTrade(itemId, p);

        int toRemove = count;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) {
                if (item.getAmount() <= toRemove) { toRemove -= item.getAmount(); p.getInventory().setItem(i, null); }
                else { item.setAmount(item.getAmount() - toRemove); toRemove = 0; }
                if (toRemove == 0) break;
            }
        }

        VKChatBridge.addPoints(vkId, rep);
        p.sendMessage("§a§l💰 Продано §f" + count + " шт. §a→ §e" + rep + " реп.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.sendTitle("§a§l+ " + rep + " реп.", "§fПродано " + count + " шт. " + itemId, 5, 20, 5);
        plugin.getMarketManager().logTransaction(p.getName(), itemId, count, "SELL", plugin.getMarketManager().getCurrentPrice(itemId), rep);
        plugin.getMarketFun().recordQuestProgress(p, itemId, count, "sell");
    }

    private void buyItems(Player p, String itemId, int amount) {
        Material m = getMarketMaterial(plugin, itemId);
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage("§cПривяжи ВК (/vklink) для торговли!"); return; }

        if (!plugin.getMarketManager().canTrade(itemId, p)) { p.sendMessage("§cПодождите..."); return; }

        int stock = plugin.getMarketManager().getStock(itemId);
        int minStock = plugin.getConfig().getInt("items." + itemId + ".min-stock", -200);
        int canBuy = Math.max(0, stock - minStock);
        if (canBuy <= 0) { p.sendMessage("§cТовар закончился! Дефицит!"); return; }
        int actual = Math.min(amount, canBuy);

        // Проверяем что все влезет в инвентарь ДО списания
        ItemStack sample = createCustomItem(plugin, itemId);
        int free = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = p.getInventory().getItem(i);
            if (slot == null || slot.getType() == Material.AIR) free += sample.getMaxStackSize();
            else if (slot.isSimilar(sample)) free += sample.getMaxStackSize() - slot.getAmount();
        }
        if (free < actual) { p.sendMessage("§cИнвентарь полон!"); return; }

        double donorMult = donorBuyMultiplier(p);
        int cost = plugin.getMarketManager().buyItems(itemId, actual, donorMult);
        if (cost <= 0) { p.sendMessage("§cОшибка цены!"); return; }

        int rep = VKChatBridge.getReputation(vkId);
        if (rep < cost) { p.sendMessage("§cНужно " + cost + " реп. (у тебя " + rep + ")"); return; }

        for (int i = 0; i < actual; i++) {
            ItemStack toGive = createCustomItem(plugin, itemId);
            p.getInventory().addItem(toGive);
        }

        VKChatBridge.takeReputation(vkId, cost);
        plugin.getMarketManager().markTrade(itemId, p);
        p.sendMessage("§6§l💰 Куплено §f" + actual + " шт. §6→ §e" + cost + " реп.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        p.sendTitle("§6§l- " + cost + " реп.", "§fКуплено " + actual + " шт. " + itemId, 5, 20, 5);
        plugin.getMarketManager().logTransaction(p.getName(), itemId, actual, "BUY", plugin.getMarketManager().getBuyPrice(itemId), cost);
        plugin.getMarketFun().recordQuestProgress(p, itemId, actual, "buy");
    }

    private void buyLimitedItem(Player p, String itemId) {
        Material m = getMarketMaterial(plugin, itemId);
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage("§cПривяжи ВК (/vklink)!"); return; }
        int price = (int) Math.max(1, Math.round(plugin.getConfig().getInt("limited-items." + itemId + ".price", 1000) * donorBuyMultiplier(p)));
        int limit = plugin.getConfig().getInt("limited-items." + itemId + ".daily-limit", 1);
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        NamespacedKey limitKey = new NamespacedKey(plugin, "limited_" + today + "_" + itemId.toLowerCase());
        int boughtToday = p.getPersistentDataContainer().getOrDefault(limitKey, PersistentDataType.INTEGER, 0);
        if (boughtToday >= limit) { p.sendMessage("§cЛимит на сегодня: " + boughtToday + "/" + limit); return; }
        int currentRep = VKChatBridge.getReputation(vkId);
        if (currentRep < price) { p.sendMessage("§cНужно " + price + " реп. (у тебя " + currentRep + ")"); return; }
        if (!p.getInventory().addItem(createCustomItem(plugin, itemId)).isEmpty()) { p.sendMessage("§cИнвентарь полон!"); return; }
        p.getPersistentDataContainer().set(limitKey, PersistentDataType.INTEGER, boughtToday + 1);
        VKChatBridge.takeReputation(vkId, price);
        p.sendMessage("§d§l💎 Куплено: §f" + itemId + " §dза §e" + price + " реп. §7(" + (boughtToday + 1) + "/" + limit + ")");
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    // ПРОДАЖА ВСЕГО
    // ═══════════════════════════════════════════════════════════════

    /**
     * Продать всё из команды (без GUI)
     */
    public static void sellAllFromCommand(VKChatMarketPlugin plugin, Player p) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage("§cПривяжи ВК (/vklink)!"); return; }

        java.util.Map<String, Integer> toSell = new java.util.HashMap<>();
        for (String itemId : getConfiguredItems(plugin, "all")) {
            Material m;
            try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = 0;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) count += item.getAmount();
            }
            if (count > 0) toSell.put(itemId, count);
        }

        if (toSell.isEmpty()) { p.sendMessage("§cНет предметов для продажи."); return; }

        int totalCount = 0;
        int totalRep = 0;
        for (java.util.Map.Entry<String, Integer> entry : toSell.entrySet()) {
            String itemId = entry.getKey();
            Material m; try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = entry.getValue();
            int toRemove = count;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) {
                    if (item.getAmount() <= toRemove) { toRemove -= item.getAmount(); p.getInventory().setItem(i, null); }
                    else { item.setAmount(item.getAmount() - toRemove); toRemove = 0; }
                    if (toRemove == 0) break;
                }
            }
            double donorMult = donorSellMultiplierStatic(p);

            int rep = Math.max(1, (int) Math.round(plugin.getMarketManager().calculateBulkSellPrice(itemId, count) * donorMult));
            totalRep += rep;
            totalCount += count;
            plugin.getMarketManager().sellItems(itemId, count, donorMult);
        }
        VKChatBridge.addPoints(vkId, totalRep);
        p.sendMessage("§a§l💰 Продано: §f" + totalCount + " шт. §a→ §e" + totalRep + " реп.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void openSellAllConfirm(Player p, String category) {
        java.util.Map<String, Integer> sellable = collectSellable(p);
        int totalItems = 0;
        int totalRep = 0;
        for (java.util.Map.Entry<String, Integer> e : sellable.entrySet()) {
            totalItems += e.getValue();
            totalRep += Math.max(1, (int) Math.round(plugin.getMarketManager().calculateBulkSellPrice(e.getKey(), e.getValue())));
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§8§l▸ §6§lБИРЖА §8§l◂ §cПодтверждение");
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Кнопка подтверждения с PDC ключом
        ItemStack confirmItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName("§a§l✅ Подтвердить продажу");
        confirmMeta.setLore(Arrays.asList(
                "§7Предметов: §e" + totalItems,
                "§7Выручка: §a" + totalRep + " реп.",
                "",
                "§cДействие необратимо!",
                "",
                "§eНажми для подтверждения"
        ));
        confirmMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_confirm_sell_all"), PersistentDataType.INTEGER, 1);
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(11, confirmItem);

        inv.setItem(15, categoryItem(plugin, Material.BARRIER, category, "§c§l✖ Отмена", "§7Вернуться назад"));

        p.openInventory(inv);
    }

    private java.util.Map<String, Integer> collectSellable(Player p) {
        java.util.Map<String, Integer> toSell = new java.util.HashMap<>();
        for (String itemId : getConfiguredItems(plugin, "all")) {
            Material m;
            try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = 0;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) count += item.getAmount();
            }
            if (count > 0) toSell.put(itemId, count);
        }
        return toSell;
    }

    private void sellAllSellable(Player p) {
        // Проверка кулдауна для первого предмета
        String firstItem = null;
        for (java.util.Map.Entry<String, Integer> entry : collectSellable(p).entrySet()) {
            firstItem = entry.getKey();
            break;
        }
        if (firstItem != null && !plugin.getMarketManager().canTrade(firstItem, p)) {
            p.sendMessage("§cПодождите между продажами!");
            return;
        }

        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage("§cПривяжи ВК (/vklink)!"); return; }
        int totalCount = 0;
        int totalRep = 0;
        java.util.Map<String, Integer> toSell = collectSellable(p);
        if (toSell.isEmpty()) { p.sendMessage("§cНет предметов для продажи."); return; }
        for (java.util.Map.Entry<String, Integer> entry : toSell.entrySet()) {
            String itemId = entry.getKey();
            Material m; try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
            int count = entry.getValue();
            int toRemove = count;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) {
                    if (item.getAmount() <= toRemove) { toRemove -= item.getAmount(); p.getInventory().setItem(i, null); }
                    else { item.setAmount(item.getAmount() - toRemove); toRemove = 0; }
                    if (toRemove == 0) break;
                }
            }
            double donorMult = donorSellMultiplier(p);
            int rep = Math.max(1, (int) Math.round(plugin.getMarketManager().calculateBulkSellPrice(itemId, count) * donorMult));
            totalRep += rep;
            totalCount += count;
            plugin.getMarketManager().sellItems(itemId, count, donorMult);
            plugin.getMarketManager().markTrade(itemId, p);
        }
        VKChatBridge.addPoints(vkId, totalRep);
        p.sendMessage("§a§l💰 Продано: §f" + totalCount + " шт. §a→ §e" + totalRep + " реп.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ═══════════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════════
    private static List<String> getConfiguredItems(VKChatMarketPlugin plugin, String category) {
        List<String> result = new ArrayList<>();
        if (plugin.getConfig().contains("items") && plugin.getConfig().getConfigurationSection("items") != null) {
            for (String id : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
                if (categoryMatches(plugin, id, category)) result.add(id);
            }
        }
        return result;
    }

    private static boolean categoryMatches(VKChatMarketPlugin plugin, String id, String category) {
        category = normalizeCategory(category);
        if (isRareShopItem(id)) return false;
        if (category.equals("limited")) return false;
        if (category.equals("all")) return true;
        String cfg = plugin.getConfig().getString("items." + id + ".category", guessCategory(id)).toLowerCase();

        if (category.equals("ores")) return cfg.contains("руд") || cfg.contains("слит") || id.contains("INGOT") || id.contains("ORE") || id.contains("DIAMOND") || id.contains("COPPER") || id.contains("EMERALD") || id.contains("SCRAP") || id.contains("DEBRIS");
        if (category.equals("food")) return cfg.contains("еда") || cfg.contains("ферм") || id.contains("BREAD") || id.contains("APPLE") || id.contains("CARROT") || id.contains("POTATO") || id.contains("WHEAT") || id.contains("PUMPKIN") || id.contains("MELON") || id.contains("BERRY") || id.contains("BEETROOT") || id.contains("SUGAR_CANE") || id.contains("BAMBOO") || id.contains("CACTUS");
        if (category.equals("wood")) return cfg.contains("дерев") || id.contains("LOG") || id.contains("WOOD");
        if (category.equals("blocks")) return cfg.contains("строй") || id.contains("STONE") || id.contains("SAND") || id.contains("GLASS") || id.contains("BRICK") || id.contains("BASALT") || id.contains("BLACKSTONE") || id.contains("OBSIDIAN") || id.contains("SLATE") || id.contains("CLAY") || id.contains("GRANITE") || id.contains("DIORITE") || id.contains("ANDESITE");
        if (category.equals("earth")) return id.contains("DIRT") || id.contains("GRASS") || id.contains("GRAVEL") || id.contains("MUD") || id.contains("MOSS") || id.contains("COARSE");
        if (category.equals("ice")) return id.contains("ICE") || id.contains("SNOW") || id.contains("POWDER");
        if (category.equals("nether")) return id.contains("SOUL") || id.contains("NETHERRACK") || id.contains("BLACKSTONE") || id.contains("BASALT");
        if (category.equals("mob")) return cfg.contains("мобы") || id.contains("BONE") || id.contains("STRING") || id.contains("GUNPOWDER") || id.contains("LEATHER") || id.contains("FEATHER") || id.contains("ROTTEN") || id.contains("SPIDER") || id.contains("SLIME") || id.contains("BLAZE") || id.contains("GHAST") || id.contains("MAGMA") || id.contains("PHANTOM") || id.contains("ENDER_PEARL");
        if (category.equals("decor")) return cfg.contains("декор") || id.contains("WOOL") || id.contains("DYE") || id.contains("INK") || id.contains("AMETHYST");
        if (category.equals("decor2")) return id.contains("TERRACOTTA") || id.contains("MUD_BRICK");
        return true;
    }

    private static String normalizeCategory(String category) {
        if (category == null) return "all";
        category = category.toLowerCase();
        if (category.equals("руды") || category.equals("ore") || category.equals("ores")) return "ores";
        if (category.equals("еда") || category.equals("food")) return "food";
        if (category.equals("дерево") || category.equals("wood")) return "wood";
        if (category.equals("блоки") || category.equals("blocks") || category.equals("building")) return "blocks";
        if (category.equals("земля") || category.equals("earth")) return "earth";
        if (category.equals("лёд") || category.equals("ice")) return "ice";
        if (category.equals("незер") || category.equals("nether")) return "nether";
        if (category.equals("мобы") || category.equals("mob") || category.equals("mobs")) return "mob";
        if (category.equals("декор") || category.equals("decor")) return "decor";
        if (category.equals("декор2") || category.equals("decor2")) return "decor2";
        if (category.equals("limited") || category.equals("лимит") || category.equals("редкости")) return "limited";
        if (category.equals("trends") || category.equals("тренды")) return "trends";
        if (category.equals("menu") || category.equals("категории")) return "menu";
        return "all";
    }

    private static String getCategoryDisplayName(String category) {
        switch (category) {
            case "ores": return "Руды и слитки";
            case "food": return "Еда и ферма";
            case "wood": return "Дерево";
            case "blocks": return "Стройматериалы";
            case "earth": return "Земля и природа";
            case "ice": return "Снег и лёд";
            case "nether": return "Незер";
            case "mob": return "Лут мобов";
            case "decor": return "Декор";
            case "decor2": return "Декор 2";
            case "limited": return "Редкости дня";
            case "all": return "Все товары";
            default: return "Биржа";
        }
    }

    private static List<String> getLimitedItems(VKChatMarketPlugin plugin) {
        List<String> result = new ArrayList<>();
        if (plugin.getConfig().contains("limited-items") && plugin.getConfig().getConfigurationSection("limited-items") != null) {
            result.addAll(plugin.getMarketManager().getRotatedLimitedItems());
        }
        return result;
    }

    private int getPageFromTitle(String title) {
        try { int a = title.lastIndexOf('['); int b = title.lastIndexOf('/'); if (a >= 0 && b > a) return Math.max(0, Integer.parseInt(title.substring(a + 1, b)) - 1); } catch (Exception ignored) {}
        return 0;
    }

    private String getCategoryFromTitle(String title) {
        // Пытаемся извлечь название категории из заголовка
        if (title.contains("Руды")) return "ores";
        if (title.contains("Еда")) return "food";
        if (title.contains("Дерево")) return "wood";
        if (title.contains("Строй")) return "blocks";
        if (title.contains("Земля")) return "earth";
        if (title.contains("Снег") || title.contains("лёд")) return "ice";
        if (title.contains("Незер")) return "nether";
        if (title.contains("Лут") || title.contains("моб")) return "mob";
        if (title.contains("Декор 2")) return "decor2";
        if (title.contains("Декор")) return "decor";
        if (title.contains("Редкости")) return "limited";
        if (title.contains("Тренды")) return "trends";
        return "all";
    }

    private static double donorSellMultiplierStatic(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 1.70;
        if (p.hasPermission("vkchat.donate.legend")) return 1.50;
        if (p.hasPermission("vkchat.donate.star")) return 1.35;
        if (p.hasPermission("vkchat.donate.flame")) return 1.20;
        if (p.hasPermission("vkchat.donate.spark")) return 1.10;
        return 1.0;
    }

    private double donorSellMultiplier(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 1.70;
        if (p.hasPermission("vkchat.donate.legend")) return 1.50;
        if (p.hasPermission("vkchat.donate.star")) return 1.35;
        if (p.hasPermission("vkchat.donate.flame")) return 1.20;
        if (p.hasPermission("vkchat.donate.spark")) return 1.10;
        return 1.0;
    }

    private double donorBuyMultiplier(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 0.35;
        if (p.hasPermission("vkchat.donate.legend")) return 0.50;
        if (p.hasPermission("vkchat.donate.star")) return 0.65;
        if (p.hasPermission("vkchat.donate.flame")) return 0.80;
        if (p.hasPermission("vkchat.donate.spark")) return 0.90;
        return 1.0;
    }

    private static boolean isRareShopItem(String id) {
        return id.contains("TOTEM") || id.contains("ENCHANTED_GOLDEN_APPLE") || id.contains("NETHERITE_INGOT") || id.contains("ECHO_SHARD") || id.contains("ANCIENT_DEBRIS") || id.contains("NETHER_STAR") || id.contains("HEART_OF_THE_SEA");
    }

    private static String guessCategory(String id) {
        if (id.contains("LOG") || id.contains("WOOD") || id.contains("PLANKS")) return "Дерево";
        if (id.contains("ORE") || id.contains("INGOT") || id.contains("DIAMOND") || id.contains("NETHERITE") || id.contains("GOLD")) return "Руды/слитки";
        if (id.contains("APPLE") || id.contains("BREAD") || id.contains("CARROT") || id.contains("POTATO") || id.contains("BEEF") || id.contains("PORK") || id.contains("CHICKEN") || id.contains("WHEAT")) return "Еда/ферма";
        if (id.contains("DIRT") || id.contains("GRASS") || id.contains("GRAVEL") || id.contains("MUD")) return "Земля";
        if (id.contains("ICE") || id.contains("SNOW")) return "Снег/лёд";
        if (id.contains("WOOL") || id.contains("DYE") || id.contains("TERRACOTTA")) return "Декор";
        if (id.contains("BLAZE") || id.contains("ENDER") || id.contains("GHAST") || id.contains("SOUL")) return "Незер/мобы";
        return "Ресурсы";
    }

    // ═══════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════
    private static ItemStack navItem(VKChatMarketPlugin plugin, Material mat, String name, int page, String category) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList("§7Нажми для перехода"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_nav_page"), PersistentDataType.INTEGER, Math.max(0, page));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack sellAllItem(VKChatMarketPlugin plugin) {
        ItemStack it = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§a§l💰 Продать всё");
        meta.setLore(Arrays.asList(
                "§7Продать все обычные предметы",
                "§7из инвентаря в рынок",
                "",
                "§cПредметы с lore не продаются",
                "",
                "§eНажми для продажи"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_sell_all"), PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack categoryItem(VKChatMarketPlugin plugin, Material mat, String category, String name, String... desc) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(desc));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_category"), PersistentDataType.STRING, category);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private static String getDonorStatus(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "Повелитель";
        if (p.hasPermission("vkchat.donate.legend")) return "Легенда";
        if (p.hasPermission("vkchat.donate.star")) return "Звезда";
        if (p.hasPermission("vkchat.donate.flame")) return "Пламя";
        if (p.hasPermission("vkchat.donate.spark")) return "Искра";
        if (p.hasPermission("vkchat.donate.vip")) return "VIP";
        return "";
    }

    private static String getSellBonus(Player p) {
        double mult = donorSellMultiplierStatic(p);
        if (mult > 1.0) return "+" + String.format("%.0f", (mult - 1.0) * 100) + "%";
        return "нет";
    }

    private static String getBuyBonus(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "-65%";
        if (p.hasPermission("vkchat.donate.legend")) return "-50%";
        if (p.hasPermission("vkchat.donate.star")) return "-35%";
        if (p.hasPermission("vkchat.donate.flame")) return "-20%";
        if (p.hasPermission("vkchat.donate.spark")) return "-10%";
        if (p.hasPermission("vkchat.donate.vip")) return "-5%";
        return "нет";
    }

    private static String getLimitBonus(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "x2.5";
        if (p.hasPermission("vkchat.donate.legend")) return "x2.0";
        if (p.hasPermission("vkchat.donate.star")) return "x1.5";
        if (p.hasPermission("vkchat.donate.flame")) return "x1.3";
        if (p.hasPermission("vkchat.donate.spark")) return "x1.1";
        return "стандарт";
    }
}
