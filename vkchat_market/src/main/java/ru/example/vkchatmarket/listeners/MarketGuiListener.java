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
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.ArrayList;
import java.util.List;

public class MarketGuiListener implements Listener {
    private static final int PAGE_SIZE = 45;
    private final VKChatMarketPlugin plugin;

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

    public static void openCategoryMenu(VKChatMarketPlugin plugin, Player p) {
        String baseTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("settings.gui-title", "&8Динамический Рынок"));
        Inventory inv = Bukkit.createInventory(null, 54, baseTitle + ChatColor.DARK_GRAY + " <menu>");
        for (int i = 0; i < 54; i++) inv.setItem(i, helpItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(4, infoItem(plugin, 0, 1, getConfiguredItems(plugin, "all").size(), p, "menu"));
        inv.setItem(10, categoryItem(plugin, Material.IRON_INGOT, "ores", ChatColor.GRAY + "⛏ Руды и слитки", "Железо, золото, алмазы, незерит"));
        inv.setItem(12, categoryItem(plugin, Material.BREAD, "food", ChatColor.GOLD + "🍞 Еда и ферма", "Еда, урожай, фермерские ресурсы"));
        inv.setItem(14, categoryItem(plugin, Material.OAK_LOG, "wood", ChatColor.YELLOW + "🌲 Дерево", "Все виды брёвен и древесины"));
        inv.setItem(16, categoryItem(plugin, Material.STONE, "blocks", ChatColor.WHITE + "🧱 Стройматериалы", "Камень, песок, стекло, кирпич"));
        inv.setItem(28, categoryItem(plugin, Material.BONE, "mob", ChatColor.RED + "☠ Лут мобов", "Кости, нити, порох, кожа"));
        inv.setItem(30, categoryItem(plugin, Material.WHITE_WOOL, "decor", ChatColor.LIGHT_PURPLE + "🎨 Декор", "Шерсть, красители, декоративный лут"));
        inv.setItem(32, categoryItem(plugin, Material.COMPASS, "all", ChatColor.AQUA + "📦 Все обычные товары", "Все разрешённые товары без редкостей"));
        inv.setItem(33, categoryItem(plugin, Material.DIAMOND, "limited", ChatColor.LIGHT_PURPLE + "💎 Лимитированные редкости", "Ротация дня, дорого, лимитировано"));
        inv.setItem(34, categoryItem(plugin, Material.CLOCK, "trends", ChatColor.AQUA + "📈 Тренды дня", "Горячие товары, история и аудит экономики"));
        inv.setItem(43, sellAllItem(plugin));
        inv.setItem(49, helpItem(Material.PAPER, ChatColor.AQUA + "Подсказка", ChatColor.GRAY + "Редкие предметы убраны из /shop.", ChatColor.GRAY + "Артефакты, тотемы и особый лут добываются в RPG/ивентах.", ChatColor.RED + "Предметы с lore не продаются."));
        p.openInventory(inv);
    }

    public static void openGui(VKChatMarketPlugin plugin, Player p, int page) {
        openGui(plugin, p, page, "all");
    }

    public static void openGui(VKChatMarketPlugin plugin, Player p, int page, String category) {
        category = normalizeCategory(category);
        if (category.equals("trends")) { openTrendsMenu(plugin, p); return; }
        String baseTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("settings.gui-title", "&8Динамический Рынок"));
        List<String> ids = category.equals("limited") ? getLimitedItems(plugin) : getConfiguredItems(plugin, category);
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));
        String title = baseTitle + ChatColor.DARK_GRAY + " [" + (page + 1) + "/" + pages + "]" + ChatColor.GRAY + " <" + category + ">";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);
        int slot = 0;
        for (int i = start; i < end; i++) {
            inv.setItem(slot++, category.equals("limited") ? createLimitedItem(plugin, ids.get(i)) : createMarketItem(plugin, ids.get(i)));
        }

        fillBottom(inv);
        inv.setItem(45, navItem(plugin, Material.ARROW, ChatColor.YELLOW + "← Предыдущая", page - 1, category));
        inv.setItem(53, navItem(plugin, Material.ARROW, ChatColor.YELLOW + "Следующая →", page + 1, category));
        inv.setItem(49, infoItem(plugin, page, pages, ids.size(), p, category));

        inv.setItem(46, categoryItem(plugin, Material.COMPASS, "menu", ChatColor.WHITE + "🏠 Категории", "Вернуться к меню категорий"));
        inv.setItem(47, categoryItem(plugin, Material.IRON_INGOT, "ores", ChatColor.GRAY + "Руды", "Фильтр"));
        inv.setItem(48, categoryItem(plugin, Material.BREAD, "food", ChatColor.GOLD + "Еда", "Фильтр"));
        inv.setItem(50, categoryItem(plugin, Material.OAK_LOG, "wood", ChatColor.YELLOW + "Дерево", "Фильтр"));
        inv.setItem(51, sellAllItem(plugin));
        inv.setItem(52, categoryItem(plugin, Material.CLOCK, "trends", ChatColor.AQUA + "📈 Тренды", "Горячие товары дня"));

        p.openInventory(inv);
    }

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
        if (category.equals("ores")) return cfg.contains("руд") || cfg.contains("слит") || cfg.contains("редкие") || id.contains("INGOT") || id.contains("ORE") || id.contains("DIAMOND") || id.contains("COPPER") || id.contains("EMERALD") || id.contains("SCRAP") || id.contains("DEBRIS");
        if (category.equals("food")) return cfg.contains("еда") || cfg.contains("ферм") || id.contains("BREAD") || id.contains("APPLE") || id.contains("CARROT") || id.contains("POTATO") || id.contains("WHEAT") || id.contains("PUMPKIN") || id.contains("MELON") || id.contains("BERRY") || id.contains("BEETROOT") || id.contains("SUGAR_CANE") || id.contains("BAMBOO") || id.contains("CACTUS");
        if (category.equals("wood")) return cfg.contains("дерев") || id.contains("LOG") || id.contains("WOOD");
        if (category.equals("blocks")) return cfg.contains("строй") || id.contains("STONE") || id.contains("SAND") || id.contains("GLASS") || id.contains("BRICK") || id.contains("BASALT") || id.contains("BLACKSTONE") || id.contains("OBSIDIAN") || id.contains("SLATE") || id.contains("CLAY") || id.contains("GRANITE") || id.contains("DIORITE") || id.contains("ANDESITE");
        if (category.equals("mob")) return cfg.contains("мобы") || cfg.contains("нижний мир") || cfg.contains("океан") || id.contains("BONE") || id.contains("STRING") || id.contains("GUNPOWDER") || id.contains("LEATHER") || id.contains("FEATHER") || id.contains("ROTTEN") || id.contains("SPIDER") || id.contains("SLIME") || id.contains("BLAZE") || id.contains("GHAST") || id.contains("MAGMA") || id.contains("PHANTOM") || id.contains("PRISMARINE") || id.contains("ENDER_PEARL");
        if (category.equals("decor")) return cfg.contains("декор") || cfg.contains("магия") || id.contains("WOOL") || id.contains("DYE") || id.contains("INK") || id.contains("AMETHYST") || id.contains("EXPERIENCE_BOTTLE");
        return true;
    }

    private static String normalizeCategory(String category) {
        if (category == null) return "all";
        category = category.toLowerCase();
        if (category.equals("руды") || category.equals("ore") || category.equals("ores")) return "ores";
        if (category.equals("еда") || category.equals("food")) return "food";
        if (category.equals("дерево") || category.equals("wood")) return "wood";
        if (category.equals("блоки") || category.equals("blocks") || category.equals("building")) return "blocks";
        if (category.equals("мобы") || category.equals("mob") || category.equals("mobs")) return "mob";
        if (category.equals("декор") || category.equals("decor")) return "decor";
        if (category.equals("limited") || category.equals("лимит") || category.equals("редкости")) return "limited";
        if (category.equals("trends") || category.equals("тренды") || category.equals("trend")) return "trends";
        if (category.equals("menu") || category.equals("категории")) return "menu";
        return "all";
    }

    private static List<String> getLimitedItems(VKChatMarketPlugin plugin) {
        List<String> result = new ArrayList<>();
        if (plugin.getConfig().contains("limited-items") && plugin.getConfig().getConfigurationSection("limited-items") != null) {
            result.addAll(plugin.getMarketManager().getRotatedLimitedItems());
        }
        return result;
    }

    private static ItemStack createLimitedItem(VKChatMarketPlugin plugin, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { m = Material.BARRIER; }
        String name = plugin.getConfig().getString("limited-items." + itemId + ".name", itemId);
        int price = plugin.getConfig().getInt("limited-items." + itemId + ".price", 1000);
        int limit = plugin.getConfig().getInt("limited-items." + itemId + ".daily-limit", 1);
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(java.util.Arrays.asList(
                ChatColor.LIGHT_PURPLE + "Лимитированный редкий товар дня",
                ChatColor.GRAY + "Цена: " + ChatColor.YELLOW + price + " реп.",
                ChatColor.GRAY + "Лимит покупки: " + limit + " в день",
                ChatColor.RED + "Не продаётся обратно в рынок.",
                "",
                ChatColor.YELLOW + "Нажми для покупки 1 шт."
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_limited_item"), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createMarketItem(VKChatMarketPlugin plugin, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { m = Material.BARRIER; }
        String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
        double currentPrice = plugin.getMarketManager().getCurrentPrice(itemId);
        double buyPrice = currentPrice * 1.15;
        double basePrice = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        String category = plugin.getConfig().getString("items." + itemId + ".category", guessCategory(itemId));

        String demand = ChatColor.GREEN + "Высокий";
        if (currentPrice < basePrice * 0.4) demand = ChatColor.RED + "Очень низкий 📉";
        else if (currentPrice < basePrice * 0.7) demand = ChatColor.YELLOW + "Низкий ↘";
        else if (currentPrice > basePrice * 1.5) demand = ChatColor.GOLD + "Ажиотажный 📈";

        // Статус дефицита
        int stock = plugin.getMarketManager().getStock(itemId);
        int scarcityThreshold = plugin.getConfig().getInt("items." + itemId + ".scarcity-threshold", 0);
        String stockStatus;
        if (stock <= 0) stockStatus = ChatColor.DARK_RED + "⚠ ДЕФИЦИТ";
        else if (scarcityThreshold > 0 && stock <= scarcityThreshold) stockStatus = ChatColor.RED + "⚠ Мало (" + stock + ")";
        else stockStatus = ChatColor.GRAY + "В наличии: " + stock;

        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();
        boolean isEvent = (itemId.equals(plugin.getMarketManager().getActiveEventItemId()) || "ALL".equals(plugin.getMarketManager().getActiveEventItemId()))
                && System.currentTimeMillis() < plugin.getMarketManager().getActiveEventExpireTime();
        if (isEvent) lore.add(ChatColor.GOLD + "⚡ СОБЫТИЕ: " + plugin.getMarketManager().getActiveEventName());
        lore.add(ChatColor.DARK_GRAY + "Категория: " + category);
        lore.add(stockStatus);
        lore.add(ChatColor.GRAY + "Продажа: " + ChatColor.GREEN + String.format("%.2f", currentPrice) + " реп/шт.");
        lore.add(ChatColor.GRAY + "Покупка: " + ChatColor.GOLD + String.format("%.2f", buyPrice) + " реп/шт.");
        lore.add(ChatColor.GRAY + "Спрос: " + demand);
        lore.add(ChatColor.AQUA + "Тренд дня: " + plugin.getMarketManager().getTrendLabel(itemId));
        lore.add(ChatColor.DARK_GRAY + "Оборот сегодня: " + plugin.getMarketManager().getDailyVolume(itemId) + " шт.");
        lore.add("");
        lore.add(ChatColor.GREEN + "ЛКМ: продать всё");
        lore.add(ChatColor.GREEN + "SHIFT+ЛКМ: продать 1 стак");
        lore.add(ChatColor.GOLD + "ПКМ: купить 1");
        lore.add(ChatColor.GOLD + "SHIFT+ПКМ: купить 16");
        lore.add(ChatColor.YELLOW + "Колесо: купить 1");
        meta.setLore(lore);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_item"), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return item;
    }


    public static void openTrendsMenu(VKChatMarketPlugin plugin, Player p) {
        String baseTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("settings.gui-title", "&8Динамический Рынок"));
        Inventory inv = Bukkit.createInventory(null, 54, baseTitle + ChatColor.DARK_GRAY + " <trends>");
        for (int i = 0; i < 54; i++) inv.setItem(i, helpItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(4, helpItem(Material.CLOCK, ChatColor.AQUA + "📈 Тренды дня",
                ChatColor.GRAY + plugin.getMarketManager().economyAuditLine(),
                ChatColor.GRAY + "Тренд влияет на цену покупки и продажи.",
                ChatColor.YELLOW + "Обновляется раз в день автоматически."));
        java.util.List<String> top = plugin.getMarketManager().getTopTrends(14);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for (int i = 0; i < top.size() && i < slots.length; i++) inv.setItem(slots[i], createTrendItem(plugin, top.get(i)));
        java.util.List<String> history = plugin.getMarketManager().getHistoryTail(8);
        java.util.List<String> lore = new java.util.ArrayList<>();
        if (history.isEmpty()) lore.add(ChatColor.GRAY + "История пока пуста.");
        else for (String h : history) lore.add(ChatColor.GRAY + h);
        inv.setItem(31, helpItem(Material.WRITABLE_BOOK, ChatColor.GOLD + "🧾 История рынка", lore.toArray(new String[0])));
        inv.setItem(45, categoryItem(plugin, Material.COMPASS, "menu", ChatColor.WHITE + "🏠 Категории", "Вернуться к меню"));
        inv.setItem(49, sellAllItem(plugin));
        inv.setItem(53, categoryItem(plugin, Material.DIAMOND, "limited", ChatColor.LIGHT_PURPLE + "💎 Редкости дня", "Открыть ротацию"));
        p.openInventory(inv);
    }

    private static ItemStack createTrendItem(VKChatMarketPlugin plugin, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { m = Material.PAPER; }
        String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
        double price = plugin.getMarketManager().getCurrentPrice(itemId);
        double base = plugin.getConfig().getDouble("items." + itemId + ".base-price", 10.0);
        double delta = base <= 0 ? 0 : ((price - base) / base) * 100.0;
        return helpItem(m, ChatColor.translateAlternateColorCodes('&', name),
                ChatColor.AQUA + "Тренд: " + plugin.getMarketManager().getTrendLabel(itemId),
                ChatColor.GRAY + "Текущая цена продажи: " + ChatColor.GREEN + String.format("%.2f", price),
                ChatColor.GRAY + "Отклонение от базы: " + (delta >= 0 ? ChatColor.GOLD + "+" : ChatColor.RED + "") + String.format("%.1f", delta) + "%",
                ChatColor.DARK_GRAY + "Оборот сегодня: " + plugin.getMarketManager().getDailyVolume(itemId) + " шт.");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String baseTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("settings.gui-title", "&8Динамический Рынок"));
        if (!e.getView().getTitle().startsWith(baseTitle)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        ItemMeta meta = e.getCurrentItem().getItemMeta();
        NamespacedKey itemKey = new NamespacedKey(plugin, "market_item");
        NamespacedKey navKey = new NamespacedKey(plugin, "market_nav_page");
        NamespacedKey catKey = new NamespacedKey(plugin, "market_category");
        NamespacedKey sellAllKey = new NamespacedKey(plugin, "market_sell_all");
        NamespacedKey confirmSellAllKey = new NamespacedKey(plugin, "market_confirm_sell_all");
        NamespacedKey limitedKey = new NamespacedKey(plugin, "market_limited_item");
        String category = getCategoryFromTitle(e.getView().getTitle());

        if (meta.getPersistentDataContainer().has(sellAllKey, PersistentDataType.INTEGER)) {
            openSellAllConfirm(p, category);
            return;
        }
        if (meta.getPersistentDataContainer().has(confirmSellAllKey, PersistentDataType.INTEGER)) {
            sellAllSellable(p);
            openGui(plugin, p, getPageFromTitle(e.getView().getTitle()), category);
            return;
        }
        if (meta.getPersistentDataContainer().has(limitedKey, PersistentDataType.STRING)) {
            buyLimitedItem(p, meta.getPersistentDataContainer().get(limitedKey, PersistentDataType.STRING));
            openGui(plugin, p, getPageFromTitle(e.getView().getTitle()), category);
            return;
        }

        if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
            String cat = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
            if ("menu".equals(cat)) openCategoryMenu(plugin, p);
            else openGui(plugin, p, 0, cat);
            return;
        }
        if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.INTEGER)) {
            openGui(plugin, p, meta.getPersistentDataContainer().get(navKey, PersistentDataType.INTEGER), category);
            return;
        }
        if (!meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return;
        String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        ClickType click = e.getClick();
        if (click == ClickType.LEFT) sellItems(p, itemId, -1);
        else if (click == ClickType.SHIFT_LEFT) sellItems(p, itemId, 64);
        else if (click == ClickType.RIGHT) buyItems(p, itemId, 1);
        else if (click == ClickType.SHIFT_RIGHT) buyItems(p, itemId, 16);
        else if (click == ClickType.MIDDLE) buyItems(p, itemId, 1);
        openGui(plugin, p, getPageFromTitle(e.getView().getTitle()), category);
    }

    private int getPageFromTitle(String title) {
        try { int a = title.lastIndexOf('['); int b = title.lastIndexOf('/'); if (a >= 0 && b > a) return Math.max(0, Integer.parseInt(title.substring(a + 1, b)) - 1); } catch (Exception ignored) {}
        return 0;
    }

    private String getCategoryFromTitle(String title) {
        try { int a = title.lastIndexOf('<'); int b = title.lastIndexOf('>'); if (a >= 0 && b > a) return title.substring(a + 1, b); } catch (Exception ignored) {}
        return "all";
    }

    private void openSellAllConfirm(Player p, String category) {
        java.util.Map<String, Integer> sellable = collectSellable(p);
        int totalItems = 0;
        int totalRep = 0;
        for (java.util.Map.Entry<String, Integer> e : sellable.entrySet()) {
            totalItems += e.getValue();
            totalRep += Math.max(1, (int) Math.round(plugin.getMarketManager().calculateBulkPrice(e.getKey(), e.getValue())));
        }
        String baseTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("settings.gui-title", "&8Динамический Рынок"));
        Inventory inv = Bukkit.createInventory(null, 27, baseTitle + ChatColor.DARK_GRAY + " <sell_confirm>");
        for (int i = 0; i < 27; i++) inv.setItem(i, helpItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(11, confirmSellAllItem(totalItems, totalRep));
        inv.setItem(15, categoryItem(plugin, Material.BARRIER, category, ChatColor.RED + "Отмена", "Вернуться назад"));
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

    private ItemStack confirmSellAllItem(int totalItems, int totalRep) {
        ItemStack it = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "✅ Подтвердить продажу всего");
        meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Будет продано предметов: " + ChatColor.YELLOW + totalItems,
                ChatColor.GRAY + "Ожидаемая выручка: " + ChatColor.GREEN + totalRep + " реп.",
                ChatColor.RED + "Внимание: действие необратимо.",
                "",
                ChatColor.YELLOW + "Нажми для подтверждения"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_confirm_sell_all"), PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    private void sellAllSellable(Player p) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Сначала привяжи ВКонтакте (/vklink), чтобы получать репутацию!");
            return;
        }
        int totalCount = 0;
        int totalRep = 0;
        java.util.Map<String, Integer> toSell = collectSellable(p);
        if (toSell.isEmpty()) {
            p.sendMessage(ChatColor.RED + "В инвентаре нет обычных предметов, которые можно продать рынку.");
            return;
        }
        for (java.util.Map.Entry<String, Integer> entry : toSell.entrySet()) {
            String itemId = entry.getKey();
            Material m;
            try { m = Material.valueOf(itemId); } catch (Exception ignored) { continue; }
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
            int rep = Math.max(1, (int) Math.round(plugin.getMarketManager().calculateBulkPrice(itemId, count) * donorSellMultiplier(p)));
            totalRep += rep;
            totalCount += count;
            plugin.getMarketManager().addStock(itemId, count);
        }
        VKChatPlugin.getInstance().getApi().addReputation(vkId, totalRep);
        p.sendMessage(ChatColor.GREEN + "💰 Продано всё продаваемое: " + totalCount + " шт. за " + totalRep + " репутации ВК.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void sellItems(Player p, String itemId, int limit) {
        Material m; try { m = Material.valueOf(itemId); } catch (Exception e) { return; }
        int count = 0;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) {
                int can = limit < 0 ? item.getAmount() : Math.min(item.getAmount(), Math.max(0, limit - count));
                count += can;
                if (limit > 0 && count >= limit) break;
            }
        }
        if (count == 0) { p.sendMessage(ChatColor.RED + "У тебя нет обычных предметов этого типа для продажи!"); return; }
        double totalEarned = plugin.getMarketManager().calculateBulkPrice(itemId, count) * donorSellMultiplier(p);
        int toRemove = count;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == m && (!item.hasItemMeta() || !item.getItemMeta().hasLore())) {
                if (item.getAmount() <= toRemove) { toRemove -= item.getAmount(); p.getInventory().setItem(i, null); }
                else { item.setAmount(item.getAmount() - toRemove); toRemove = 0; }
                if (toRemove == 0) break;
            }
        }
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId != -1) {
            int repToGive = Math.max(1, (int) Math.round(totalEarned));
            VKChatPlugin.getInstance().getApi().addReputation(vkId, repToGive);
            plugin.getMarketManager().addStock(itemId, count);
            p.sendMessage(ChatColor.GREEN + "💰 Продано " + count + " шт. за " + repToGive + " репутации ВК.");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else p.sendMessage(ChatColor.RED + "Сначала привяжи ВКонтакте (/vklink), чтобы получать репутацию!");
    }

    private void buyLimitedItem(Player p, String itemId) {
        Material m;
        try { m = Material.valueOf(itemId); } catch (Exception e) { return; }
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink)."); return; }
        int price = (int)Math.max(1, Math.round(plugin.getConfig().getInt("limited-items." + itemId + ".price", 1000) * donorBuyMultiplier(p)));
        int limit = plugin.getConfig().getInt("limited-items." + itemId + ".daily-limit", 1);
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        NamespacedKey limitKey = new NamespacedKey(plugin, "limited_" + today + "_" + itemId.toLowerCase());
        int boughtToday = p.getPersistentDataContainer().getOrDefault(limitKey, PersistentDataType.INTEGER, 0);
        if (boughtToday >= limit) {
            p.sendMessage(ChatColor.RED + "Лимит покупки на сегодня исчерпан: " + boughtToday + "/" + limit);
            return;
        }
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (currentRep < price) { p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + price); return; }
        if (!p.getInventory().addItem(new ItemStack(m, 1)).isEmpty()) { p.sendMessage(ChatColor.RED + "Недостаточно места в инвентаре!"); return; }
        p.getPersistentDataContainer().set(limitKey, PersistentDataType.INTEGER, boughtToday + 1);
        VKChatPlugin.getInstance().getApi().takeReputation(vkId, price);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "💎 Куплен лимитированный предмет: " + itemId + " за " + price + " реп. Лимит: " + (boughtToday + 1) + "/" + limit);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private void buyItems(Player p, String itemId, int amount) {
        Material m; try { m = Material.valueOf(itemId); } catch (Exception e) { return; }
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) { p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink), чтобы покупать предметы!"); return; }
        int finalCost = Math.max(1, (int) Math.round(plugin.getMarketManager().calculateBulkBuyPrice(itemId, amount) * donorBuyMultiplier(p)));
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (currentRep < finalCost) { p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + finalCost + " (У тебя: " + currentRep + ")"); return; }
        java.util.Map<Integer, ItemStack> remaining = p.getInventory().addItem(new ItemStack(m, amount));
        if (!remaining.isEmpty()) { p.sendMessage(ChatColor.RED + "Недостаточно места в инвентаре!"); return; }
        VKChatPlugin.getInstance().getApi().takeReputation(vkId, finalCost);
        plugin.getMarketManager().removeStock(itemId, amount);
        p.sendMessage(ChatColor.GREEN + "💰 Куплено " + amount + " шт. за " + finalCost + " реп. ВК.");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }


    private double donorSellMultiplier(Player p) {
        if (p.hasPermission("vkchat.donate.market.legend") || p.hasPermission("vkchat.donate.status.legend")) return plugin.getConfig().getDouble("market2.donate.sell-multiplier.legend", 3.00);
        if (p.hasPermission("vkchat.donate.market.star") || p.hasPermission("vkchat.donate.status.star")) return plugin.getConfig().getDouble("market2.donate.sell-multiplier.star", 2.20);
        if (p.hasPermission("vkchat.donate.market.flame") || p.hasPermission("vkchat.donate.status.flame")) return plugin.getConfig().getDouble("market2.donate.sell-multiplier.flame", 1.70);
        if (p.hasPermission("vkchat.donate.market.spark") || p.hasPermission("vkchat.donate.status.spark")) return plugin.getConfig().getDouble("market2.donate.sell-multiplier.spark", 1.30);
        return 1.0;
    }

    private double donorBuyMultiplier(Player p) {
        if (p.hasPermission("vkchat.donate.market.legend") || p.hasPermission("vkchat.donate.status.legend")) return plugin.getConfig().getDouble("market2.donate.buy-multiplier.legend", 0.00);
        if (p.hasPermission("vkchat.donate.market.star") || p.hasPermission("vkchat.donate.status.star")) return plugin.getConfig().getDouble("market2.donate.buy-multiplier.star", 0.40);
        if (p.hasPermission("vkchat.donate.market.flame") || p.hasPermission("vkchat.donate.status.flame")) return plugin.getConfig().getDouble("market2.donate.buy-multiplier.flame", 0.70);
        if (p.hasPermission("vkchat.donate.market.spark") || p.hasPermission("vkchat.donate.status.spark")) return plugin.getConfig().getDouble("market2.donate.buy-multiplier.spark", 0.90);
        return 1.0;
    }

    private static void fillBottom(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta(); meta.setDisplayName(" "); filler.setItemMeta(meta);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
    }

    private static ItemStack navItem(VKChatMarketPlugin plugin, Material mat, String name, int page, String category) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta(); meta.setDisplayName(name); meta.setLore(java.util.Collections.singletonList(ChatColor.GRAY + "Нажми для перехода"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_nav_page"), PersistentDataType.INTEGER, Math.max(0, page));
        it.setItemMeta(meta); return it;
    }

    private static ItemStack sellAllItem(VKChatMarketPlugin plugin) {
        ItemStack it = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "💰 Продать всё");
        meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Продать все обычные предметы",
                ChatColor.GRAY + "из инвентаря, которые есть в рынке.",
                ChatColor.RED + "Предметы с lore не продаются.",
                "",
                ChatColor.YELLOW + "Нажми для продажи всего"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_sell_all"), PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack categoryItem(VKChatMarketPlugin plugin, Material mat, String category, String name) {
        return categoryItem(plugin, mat, category, name, "Фильтр категории");
    }

    private static ItemStack categoryItem(VKChatMarketPlugin plugin, Material mat, String category, String name, String desc) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta(); meta.setDisplayName(name); meta.setLore(java.util.Collections.singletonList(ChatColor.GRAY + desc));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "market_category"), PersistentDataType.STRING, category);
        it.setItemMeta(meta); return it;
    }

    private static boolean isRareShopItem(String id) {
        return id.contains("TOTEM") || id.contains("ENCHANTED_GOLDEN_APPLE") || id.contains("NETHERITE_INGOT") || id.contains("ECHO_SHARD") || id.contains("ANCIENT_DEBRIS") || id.contains("NETHER_STAR") || id.contains("HEART_OF_THE_SEA");
    }

    private static ItemStack infoItem(VKChatMarketPlugin plugin, int page, int pages, int total, Player p, String category) {
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        int rep = vkId != -1 ? VKChatPlugin.getInstance().getApi().getReputation(vkId) : 0;
        return helpItem(Material.BOOK, ChatColor.GOLD + "Рынок " + (page + 1) + "/" + pages,
                ChatColor.GRAY + "Категория: " + category,
                ChatColor.GRAY + "Товаров: " + total,
                ChatColor.GRAY + "Баланс: " + ChatColor.YELLOW + rep + " реп.",
                ChatColor.DARK_GRAY + plugin.getMarketManager().economyAuditLine());
    }

    private static ItemStack helpItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta(); meta.setDisplayName(name); meta.setLore(java.util.Arrays.asList(lore)); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); it.setItemMeta(meta); return it;
    }

    private static String guessCategory(String id) {
        if (id.contains("LOG") || id.contains("WOOD") || id.contains("PLANKS")) return "Дерево";
        if (id.contains("ORE") || id.contains("INGOT") || id.contains("DIAMOND") || id.contains("NETHERITE") || id.contains("GOLD")) return "Руды/слитки";
        if (id.contains("APPLE") || id.contains("BREAD") || id.contains("CARROT") || id.contains("POTATO") || id.contains("BEEF") || id.contains("PORK") || id.contains("CHICKEN") || id.contains("WHEAT")) return "Еда/ферма";
        if (id.contains("WOOL") || id.contains("DYE")) return "Декор/краски";
        if (id.contains("BLAZE") || id.contains("ENDER") || id.contains("GHAST") || id.contains("PRISMARINE") || id.contains("EXPERIENCE") || id.contains("AMETHYST")) return "Магия/мобы";
        return "Ресурсы";
    }
}
