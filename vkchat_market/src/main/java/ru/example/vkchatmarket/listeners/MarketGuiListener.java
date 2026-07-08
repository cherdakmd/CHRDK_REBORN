package ru.example.vkchatmarket.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.data.MarketCategoryResolver;
import ru.example.vkchatmarket.data.MarketTransactionService;
import ru.example.vkchatmarket.gui.MarketItemFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MarketGuiListener — GUI рендеринг и маршрутизация кликов рынка.
 *
 * Рефакторинг v3.3: бизнес-логика → MarketTransactionService,
 * категории → MarketCategoryResolver, предметы → MarketItemFactory.
 * Было: 1007 строк. Стало: ~420 строк.
 */
public class MarketGuiListener implements Listener {
    private static final int PAGE_SIZE = 35;
    private final VKChatMarketPlugin plugin;

    // Слоты для сетки товаров (4 ряда по 9)
    private static final int[] ITEM_SLOTS = {
            10,11,12,13,14,15,16,17,18, 19,20,21,22,23,24,25,26,27,
            28,29,30,31,32,33,34,35,36, 37,38,39,40,41,42,43,44
    };

    public MarketGuiListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════
    // NPC ИНТЕРАКЦИЯ
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    // ВХОДНЫЕ ТОЧКИ GUI
    // ═══════════════════════════════════════════════════════════════

    public static void openGui(VKChatMarketPlugin plugin, Player p) {
        openCategoryMenu(plugin, p);
    }

    public static void openGui(VKChatMarketPlugin plugin, Player p, String category) {
        if (category == null || category.equalsIgnoreCase("all") || category.equalsIgnoreCase("menu")) {
            openCategoryMenu(plugin, p);
        } else {
            openGui(plugin, p, 0, category);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ГЛАВНОЕ МЕНЮ КАТЕГОРИЙ
    // ═══════════════════════════════════════════════════════════════

    public static void openCategoryMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lБИРЖА §8◂ §7Главное меню");

        ItemStack border = MarketItemFactory.create(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Шапка
        int vkId = VKChatBridge.getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;
        inv.setItem(4, MarketItemFactory.create(Material.GOLD_BLOCK,
                "§6§l⚡ БИРЖА CHRDK",
                "§7Динамическая экономика сервера",
                "",
                "§e💰 Баланс: §f" + rep + " реп.",
                "§b📦 Товаров: §f" + MarketCategoryResolver.getConfiguredItems(plugin, "all").size(),
                "§d📈 Цикл: §f" + plugin.getMarketManager().getMarketCycleLabel()));

        String donor = MarketItemFactory.getDonorStatus(p);
        if (!donor.isEmpty()) {
            inv.setItem(8, MarketItemFactory.create(Material.NETHER_STAR, "§e⭐ " + donor,
                    "§7Продажа: §a+" + MarketItemFactory.getSellBonus(p),
                    "§7Покупка: §a" + MarketItemFactory.getBuyBonus(p)));
        }

        // Категории — сетка 4×2
        int[] catSlots = {20,21,22,23, 29,30,31,32};
        String[][] cats = {
                {"ores",   "§c⛏ §fРуды/слитки",      "§7Железо, золото, алмазы..."},
                {"food",   "§6🍞 §fЕда и ферма",      "§7Хлеб, мясо, урожай..."},
                {"wood",   "§a🌲 §fДерево",           "§7Доски, брёвна..."},
                {"blocks", "§7🧱 §fСтройматериалы",    "§7Камень, кирпич, стекло..."},
                {"earth",  "§8🟫 §fЗемля и природа",   "§7Земля, гравий, мох..."},
                {"mob",    "§5☠ §fЛут мобов",         "§7Кости, порох, кожа..."},
                {"decor",  "§d🎨 §fДекор",            "§7Шерсть, краски, терракота..."},
                {"all",    "§b📦 §fВсе товары",        "§7Полный список без фильтра"},
        };
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            inv.setItem(catSlots[i], MarketItemFactory.categoryItem(plugin,
                    MarketCategoryResolver.getCatIcon(cats[i][0]), cats[i][0], cats[i][1], cats[i][2]));
        }

        // Нижний ряд
        inv.setItem(45, MarketItemFactory.categoryItem(plugin, Material.CLOCK, "trends", "§b📈 Тренды", "§7Горячие товары"));
        inv.setItem(47, MarketItemFactory.categoryItem(plugin, Material.ENCHANTED_BOOK, "rare", "§d📚 Книги чар", "§7Все зачарования"));
        inv.setItem(49, MarketItemFactory.sellAllItem(plugin));
        inv.setItem(51, MarketItemFactory.categoryItem(plugin, Material.DIAMOND, "limited", "§d💎 Редкости дня", "§7Лимит. товары"));
        inv.setItem(53, MarketItemFactory.create(Material.BARRIER, "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // КАТЕГОРИЯ С ТОВАРАМИ
    // ═══════════════════════════════════════════════════════════════

    public static void openGui(VKChatMarketPlugin plugin, Player p, int page, String category) {
        category = MarketCategoryResolver.normalizeCategory(category);
        if (category.equals("trends"))  { openTrendsMenu(plugin, p); return; }
        if (category.equals("limited")) { openLimitedGui(plugin, p, page); return; }

        List<String> ids = MarketCategoryResolver.getConfiguredItems(plugin, category);
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        String catName = MarketCategoryResolver.getCategoryDisplayName(category);
        int vkId = VKChatBridge.getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatBridge.getReputation(vkId) : 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §7" + catName + " §8" + (page+1) + "/" + pages);

        // Сетка товаров
        ItemStack bg = MarketItemFactory.create(Material.BLACK_STAINED_GLASS_PANE, "§0");
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], MarketItemFactory.createMarketItem(plugin, ids.get(i)));
        }

        // Нижняя панель
        ItemStack navGlass = MarketItemFactory.create(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);

        if (page > 0)
            inv.setItem(45, MarketItemFactory.navItem(plugin, Material.SPECTRAL_ARROW, "§e◀ Назад", page - 1, category));
        inv.setItem(47, MarketItemFactory.categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));
        inv.setItem(49, MarketItemFactory.create(Material.BOOK,
                "§6" + catName + " §8" + (page+1) + "/" + pages,
                "§7Товаров: §f" + ids.size(), "§e💰 Баланс: §f" + rep + " реп."));
        inv.setItem(51, MarketItemFactory.sellAllItem(plugin));
        if (page < pages - 1)
            inv.setItem(53, MarketItemFactory.navItem(plugin, Material.SPECTRAL_ARROW, "§eВперёд ▶", page + 1, category));

        p.openInventory(inv);
    }

    private static void openLimitedGui(VKChatMarketPlugin plugin, Player p, int page) {
        List<String> ids = MarketCategoryResolver.getLimitedItems(plugin);
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(null, 54,
                "§8▸ §6§lБИРЖА §8◂ §dРедкости §8" + (page+1) + "/" + pages);

        ItemStack bg = MarketItemFactory.create(Material.BLACK_STAINED_GLASS_PANE, "§0");
        for (int s : ITEM_SLOTS) inv.setItem(s, bg);

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        for (int i = start, slot = 0; i < end; i++) {
            inv.setItem(ITEM_SLOTS[slot++], MarketItemFactory.createLimitedItem(plugin, ids.get(i)));
        }

        ItemStack navGlass = MarketItemFactory.create(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, navGlass);
        if (page > 0)
            inv.setItem(45, MarketItemFactory.navItem(plugin, Material.SPECTRAL_ARROW, "§e◀ Назад", page - 1, "limited"));
        inv.setItem(49, MarketItemFactory.categoryItem(plugin, Material.COMPASS, "menu", "§f🏠 Меню", "§7К категориям"));
        if (page < pages - 1)
            inv.setItem(53, MarketItemFactory.navItem(plugin, Material.SPECTRAL_ARROW, "§eВперёд ▶", page + 1, "limited"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // МЕНЮ ТРЕНДОВ
    // ═══════════════════════════════════════════════════════════════

    public static void openTrendsMenu(VKChatMarketPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l▸ §6§lБИРЖА §8§l◂ §bТренды");

        ItemStack glass = MarketItemFactory.create(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        inv.setItem(4, MarketItemFactory.create(Material.CLOCK,
                "§b§l📈 Тренды дня",
                "§7" + plugin.getMarketManager().economyAuditLine(),
                "§7Цикл: " + plugin.getMarketManager().getMarketCycleLabel(),
                "",
                "§eТренд обновляется каждый день"));

        // Топ тренды
        List<String> top = plugin.getMarketManager().getTopTrends(14);
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            inv.setItem(slots[i], MarketItemFactory.createTrendItem(plugin, top.get(i)));
        }

        // История
        List<String> history = plugin.getMarketManager().getHistoryTail(8);
        String[] histLore = history.isEmpty()
                ? new String[]{"§7История пока пуста."}
                : history.stream().map(h -> "§7" + h).toArray(String[]::new);
        inv.setItem(31, MarketItemFactory.create(Material.WRITABLE_BOOK, "§6§l🧾 История рынка", histLore));

        // Квест дня
        inv.setItem(33, MarketItemFactory.create(Material.PAPER,
                "§e§l📋 Квест дня",
                "§7" + plugin.getMarketFun().getQuestInfo(),
                "",
                "§aНаграда: 1000 реп."));

        // Flash Sale
        if (plugin.getMarketFun().getFlashSaleItemId() != null) {
            String name = plugin.getConfig().getString("items." + plugin.getMarketFun().getFlashSaleItemId() + ".name",
                    plugin.getMarketFun().getFlashSaleItemId());
            int percent = (int) (plugin.getMarketFun().getFlashSaleDiscount() * 100);
            long remaining = (plugin.getMarketFun().getFlashSaleEndTime() - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
                inv.setItem(35, MarketItemFactory.create(Material.FIREWORK_ROCKET,
                        "§d§l⚡ Flash Sale",
                        "§7" + name,
                        "§aСкидка: -" + percent + "%",
                        "§7Осталось: §e" + remaining + " сек."));
            }
        }

        // Нижняя панель
        ItemStack bottomGlass = MarketItemFactory.create(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, bottomGlass);
        inv.setItem(45, MarketItemFactory.categoryItem(plugin, Material.COMPASS, "menu", "§f§l🏠 Меню", "§7К категориям"));
        inv.setItem(49, MarketItemFactory.sellAllItem(plugin));
        inv.setItem(53, MarketItemFactory.categoryItem(plugin, Material.DIAMOND, "limited", "§d§l💎 Редкости дня", "§7Открыть ротацию"));

        p.openInventory(inv);
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

        NamespacedKey itemKey    = new NamespacedKey(plugin, "market_item");
        NamespacedKey navKey     = new NamespacedKey(plugin, "market_nav_page");
        NamespacedKey catKey     = new NamespacedKey(plugin, "market_category");
        NamespacedKey sellAllKey = new NamespacedKey(plugin, "market_sell_all");
        NamespacedKey confirmKey = new NamespacedKey(plugin, "market_confirm_sell_all");
        NamespacedKey limitedKey = new NamespacedKey(plugin, "market_limited_item");

        String category = MarketCategoryResolver.getCategoryFromTitle(title);

        // Продать всё — подтверждение
        if (meta.getPersistentDataContainer().has(sellAllKey, PersistentDataType.INTEGER)) {
            openSellAllConfirm(p, category);
            return;
        }

        // Подтверждение продажи всего
        if (meta.getPersistentDataContainer().has(confirmKey, PersistentDataType.INTEGER)) {
            MarketTransactionService.sellAllSellable(plugin, p);
            openGui(plugin, p, MarketCategoryResolver.getPageFromTitle(title), category);
            return;
        }

        // Лимитированные товары
        if (meta.getPersistentDataContainer().has(limitedKey, PersistentDataType.STRING)) {
            String itemId = meta.getPersistentDataContainer().get(limitedKey, PersistentDataType.STRING);
            MarketTransactionService.buyLimitedItem(plugin, p, itemId);
            openGui(plugin, p, MarketCategoryResolver.getPageFromTitle(title), category);
            return;
        }

        // Категории
        if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            if ("menu".equals(cat)) openCategoryMenu(plugin, p);
            else openGui(plugin, p, 0, cat);
            return;
        }

        // Навигация по страницам
        if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.INTEGER)) {
            int page = meta.getPersistentDataContainer().get(navKey, PersistentDataType.INTEGER);
            openGui(plugin, p, page, category);
            return;
        }

        // Торговля (ЛКМ/ПКМ/Shift)
        if (!meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return;
        String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        ClickType click = e.getClick();

        if (click == ClickType.LEFT)        MarketTransactionService.sellItems(plugin, p, itemId, -1);
        else if (click == ClickType.SHIFT_LEFT)  MarketTransactionService.sellItems(plugin, p, itemId, 64);
        else if (click == ClickType.RIGHT)       MarketTransactionService.buyItems(plugin, p, itemId, 1);
        else if (click == ClickType.SHIFT_RIGHT) MarketTransactionService.buyItems(plugin, p, itemId, 16);

        openGui(plugin, p, MarketCategoryResolver.getPageFromTitle(title), category);
    }

    // ═══════════════════════════════════════════════════════════════
    // ПОДТВЕРЖДЕНИЕ ПРОДАЖИ ВСЕГО
    // ═══════════════════════════════════════════════════════════════

    private void openSellAllConfirm(Player p, String category) {
        Map<String, Integer> sellable = MarketTransactionService.collectSellable(plugin, p);
        int totalItems = sellable.values().stream().mapToInt(Integer::intValue).sum();
        int totalRep = MarketTransactionService.calculateSellAllTotal(plugin, sellable);

        Inventory inv = Bukkit.createInventory(null, 27, "§8§l▸ §6§lБИРЖА §8§l◂ §cПодтверждение");
        ItemStack glass = MarketItemFactory.create(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Кнопка подтверждения
        ItemStack confirmItem = MarketItemFactory.confirmSellAllItem(plugin);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setLore(Arrays.asList(
                "§7Предметов: §e" + totalItems,
                "§7Выручка: §a" + totalRep + " реп.",
                "",
                "§cДействие необратимо!",
                "",
                "§eНажми для подтверждения"));
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(11, confirmItem);

        inv.setItem(15, MarketItemFactory.categoryItem(plugin, Material.BARRIER, category, "§c§l✖ Отмена", "§7Вернуться назад"));

        p.openInventory(inv);
    }
}
