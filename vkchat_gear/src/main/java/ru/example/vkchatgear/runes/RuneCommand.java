package ru.example.vkchatgear.runes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.*;

/**
 * RuneCommand — GUI-управление биржей рун с категориями и пагинацией.
 *
 * Хаб: 4 категории (Оружие, Броня, Инструменты, Кристаллы и Свитки) + инфо + баланс.
 * Каждая категория открывает подстраницу с рунами и пагинацией.
 */
public class RuneCommand implements CommandExecutor, Listener {
    private final VKChatGearPlugin plugin;

    // === Титулы (единый dark theme) ===
    private static final String HUB_TITLE = "§8▸ §d§lРУНЫ §8◂ §7Биржа";
    private static final String WEAPON_TITLE = "§8▸ §d§lРУНЫ §8◂ §cОружие";
    private static final String ARMOR_TITLE = "§8▸ §d§lРУНЫ §8◂ §9Броня";
    private static final String TOOL_TITLE = "§8▸ §d§lРУНЫ §8◂ §aИнструменты";
    private static final String CRYSTAL_TITLE = "§8▸ §d§lРУНЫ §8◂ §6Кристаллы и Свитки";

    // Слоты хаба
    private static final int SLOT_WEAPON = 20;
    private static final int SLOT_ARMOR = 22;
    private static final int SLOT_TOOL = 24;
    private static final int SLOT_CRYSTAL = 31;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_CLOSE = 53;

    // Навигация подстраниц
    private static final int NAV_BACK = 45;
    private static final int NAV_PREV = 46;
    private static final int NAV_PAGE = 49;
    private static final int NAV_NEXT = 50;
    private static final int NAV_CLOSE = 53;

    // Пагинация
    private static final int PAGE_SIZE = 36; // слоты 0-8 (заголовок), 9-44 (предметы), 45-53 (навигация)
    private static final int ITEM_START_SLOT = 9;
    private static final int ITEM_END_SLOT = 45;

    private static final Set<Integer> NAV_SLOTS = Set.of(NAV_BACK, NAV_PREV, NAV_NEXT, NAV_CLOSE);
    private static final Set<Integer> BORDER_SLOTS = new HashSet<>();
    static {
        for (int i = 0; i < 9; i++) BORDER_SLOTS.add(i);
        for (int i = 45; i < 54; i++) BORDER_SLOTS.add(i);
        for (int i = 9; i < 45; i++) {
            if (i % 9 == 0 || i % 9 == 8) BORDER_SLOTS.add(i);
        }
    }

    // Контекст пагинации: страница текущего игрока
    private final Map<UUID, PageInfo> pageState = new java.util.concurrent.ConcurrentHashMap<>();

    private static class PageInfo {
        String category; // "weapon", "armor", "tool", "crystal"
        int page;
        long created = System.currentTimeMillis();

        PageInfo(String category, int page) {
            this.category = category;
            this.page = page;
        }
    }

    public RuneCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("vkchat.admin")) {
            plugin.reloadConfig();
            if (plugin.getRuneRegistry() != null) plugin.getRuneRegistry().reload();
            sender.sendMessage("§a§l⚙ §aКонфиг рун перезагружен!");
            return true;
        }
        if (!(sender instanceof Player)) return true;
        openHub((Player) sender);
        return true;
    }

    // ═══════════════════════════════════════════
    // ХАБ
    // ═══════════════════════════════════════════

    private void openHub(Player p) {
        pageState.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, HUB_TITLE);

        // Рамка
        ItemStack border = glass(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = glass(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, BORDER_SLOTS.contains(i) ? border : accent);
        }

        // Заголовок
        inv.setItem(SLOT_INFO, item(Material.BOOK, "§b§l📈 Как работают цены?",
                "§7Добро пожаловать на биржу рун!",
                "§7Цены колеблются в реальном времени:",
                "§e• Покупка руны: §7цена §c+5% §7(спрос)",
                "§e• Остальные руны: §7падают на §a-1% §7за покупку",
                "§e• Скидки до: §d-50% §7от базы",
                "§e• Макс. наценка: §c+200% §7при ажиотаже"));

        // Категории
        inv.setItem(SLOT_WEAPON, item(Material.NETHERITE_SWORD, "§c⚔ Руны Оружия",
                "§7Атакующие чары для мечей, топоров, луков",
                "§7Вампиризм, Казнь, Метеор и др.",
                "", "§e▶ Открыть"));
        inv.setItem(SLOT_ARMOR, item(Material.SHIELD, "§9🛡 Руны Брони",
                "§7Защитные чары для шлемов, нагрудников",
                "§7Уклонение, Эгида, Зеркало и др.",
                "", "§e▶ Открыть"));
        inv.setItem(SLOT_TOOL, item(Material.DIAMOND_PICKAXE, "§a⛏ Руны Инструментов",
                "§7Спешка, Телекинез, Магнит Руд",
                "", "§e▶ Открыть"));
        inv.setItem(SLOT_CRYSTAL, item(Material.NETHER_STAR, "§6💎 Кристаллы и Свитки",
                "§7Кристаллы заточки (4 тира)",
                "§7Свитки: Сохранения, Идеального Слияния",
                "", "§e▶ Открыть"));

        // Баланс
        inv.setItem(SLOT_BALANCE, createBalanceItem(p));

        // Закрыть
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // ПОДСТРАНИЦЫ
    // ═══════════════════════════════════════════

    private void openCategory(Player p, String category, int page) {
        pageState.put(p.getUniqueId(), new PageInfo(category, page));

        String title;
        Material accentMat;
        switch (category) {
            case "weapon":
                title = WEAPON_TITLE;
                accentMat = Material.RED_STAINED_GLASS_PANE;
                break;
            case "armor":
                title = ARMOR_TITLE;
                accentMat = Material.BLUE_STAINED_GLASS_PANE;
                break;
            case "tool":
                title = TOOL_TITLE;
                accentMat = Material.GREEN_STAINED_GLASS_PANE;
                break;
            default:
                title = CRYSTAL_TITLE;
                accentMat = Material.ORANGE_STAINED_GLASS_PANE;
                break;
        }

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Рамка
        ItemStack border = glass(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = glass(accentMat, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, BORDER_SLOTS.contains(i) ? border : accent);
        }

        // Баланс в заголовке
        inv.setItem(4, createBalanceItem(p));

        // Получаем предметы для текущей страницы
        List<ItemStack> items = getCategoryItems(category, p);
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / PAGE_SIZE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, items.size());
        int slot = ITEM_START_SLOT;
        for (int i = start; i < end; i++) {
            // Пропускаем слоты-столбцы (каждый 9-й слот = граница)
            while (BORDER_SLOTS.contains(slot) && slot < ITEM_END_SLOT) slot++;
            if (slot >= ITEM_END_SLOT) break;
            inv.setItem(slot, items.get(i));
            slot++;
        }

        // Навигация
        inv.setItem(NAV_BACK, item(Material.ARROW, "§e← Назад к хабу"));
        inv.setItem(NAV_PREV, page > 0
                ? item(Material.ARROW, "§e← Предыдущая страница")
                : glass(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(NAV_PAGE, item(Material.PAPER, "§fСтраница " + (page + 1) + " / " + totalPages,
                "§7Предметов: " + items.size()));
        inv.setItem(NAV_NEXT, page < totalPages - 1
                ? item(Material.ARROW, "§e→ Следующая страница")
                : glass(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(NAV_CLOSE, item(Material.BARRIER, "§c✕ Закрыть"));

        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // ПРЕДМЕТЫ ПО КАТЕГОРИЯМ
    // ═══════════════════════════════════════════

    private List<ItemStack> getCategoryItems(String category, Player p) {
        List<ItemStack> items = new ArrayList<>();
        RuneRegistry registry = plugin.getRuneRegistry();
        RuneMarketManager market = plugin.getRuneMarketManager();

        switch (category) {
            case "weapon":
                addRunesByCategory(items, registry, market, "weapon", p);
                break;
            case "armor":
                addRunesByCategory(items, registry, market, "armor", p);
                break;
            case "tool":
                addRunesByCategory(items, registry, market, "tool", p);
                break;
            case "crystal":
                addCrystals(items, market, p);
                addSpecialScrolls(items, market, p);
                addFusionScroll(items, market, p);
                break;
        }
        return items;
    }

    private void addRunesByCategory(List<ItemStack> items, RuneRegistry registry,
                                     RuneMarketManager market, String category, Player p) {
        if (registry == null) return;
        for (RuneRegistry.RuneDef def : registry.getAllRunes()) {
            if (!def.getCategory().equals(category) && !def.getCategory().equals("all")) continue;
            items.add(createRuneItem(def, market, p));
        }
    }

    private void addCrystals(List<ItemStack> items, RuneMarketManager market, Player p) {
        addCrystalItem(items, market, "common", "Обычный [I-X]", Material.EMERALD, "§a", p);
        addCrystalItem(items, market, "rare", "Редкий [XI-XV]", Material.DIAMOND, "§9", p);
        addCrystalItem(items, market, "legendary", "Легендарный [XVI-XX]", Material.PRISMARINE_SHARD, "§6§l", p);
        addCrystalItem(items, market, "ancient", "Древний [XXI-XXV]", Material.PRISMARINE_SHARD, "§5§l", p);
    }

    private void addCrystalItem(List<ItemStack> items, RuneMarketManager market,
                                 String tier, String name, Material mat, String color, Player p) {
        int price = market.getPrice("crystal_" + tier);
        int modifiedPrice = applyMagicEvent(price, tier);
        String eventSuffix = getEventSuffix(tier, false);

        int from = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".from",
                tier.equals("common") ? 0 : tier.equals("rare") ? 10 : tier.equals("legendary") ? 15 : 20);
        int to = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".to",
                tier.equals("common") ? 10 : tier.equals("rare") ? 15 : tier.equals("legendary") ? 20 : 25);
        int success = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".success",
                tier.equals("common") ? 90 : tier.equals("rare") ? 60 : tier.equals("legendary") ? 35 : 25);

        List<String> lore = new ArrayList<>();
        lore.add("§7Позволяет затачивать снаряжение.");
        lore.add("");
        lore.add("§e• Диапазон: §f+" + from + " ➔ +" + to);
        lore.add("§e• Шанс успеха: §a" + success + "%");
        if (tier.equals("common")) {
            lore.add("§7• Безопасный стартовый кристалл до +" + to);
            lore.add("§c• При провале: редко снижает на -1");
        } else if (tier.equals("rare")) {
            lore.add("§c• При провале: может снизить на -1, но не ниже +" + from);
        } else if (tier.equals("legendary")) {
            lore.add("§c• При провале: снижение на -1/-2");
            lore.add("§4• Шанс уничтожения без Свитка Сохранения");
        } else {
            lore.add("§4• При провале: снижение на -1/-3");
            lore.add("§4• Высокий шанс уничтожения!");
            lore.add("§5• Древний кристалл для эндгейм-заточки");
        }
        lore.add("");
        lore.add("§7Перетащите на предмет для заточки!");
        lore.add("");
        lore.add("§eЦена: §b" + modifiedPrice + " реп. ВК" + eventSuffix);
        lore.add("§8Нажмите, чтобы купить");

        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color + "💎 Кристалл Заточки: " + name);
        meta.setLore(lore);
        Integer cmd = getCrystalCustomModelData(tier);
        if (cmd != null) meta.setCustomModelData(cmd);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_tier"), PersistentDataType.STRING, tier);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_price"), PersistentDataType.INTEGER, modifiedPrice);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_name"), PersistentDataType.STRING, name);
        it.setItemMeta(meta);
        items.add(it);
    }

    private void addSpecialScrolls(List<ItemStack> items, RuneMarketManager market, Player p) {
        // Свиток Сохранения
        int safetyPrice = market.getPrice("safety_scroll");
        ItemStack safety = new ItemStack(Material.PAPER);
        ItemMeta sm = safety.getItemMeta();
        sm.setDisplayName("§d§lСвиток Сохранения");
        sm.setCustomModelData(54);
        List<String> sLore = new ArrayList<>();
        sLore.add("§7Защищает предмет от отката");
        sLore.add("§7уровня заточки при неудаче!");
        sLore.add("");
        sLore.add("§e• Держите в инвентаре при заточке");
        sLore.add("§e• Расходуется автоматически");
        sLore.add("");
        sLore.add("§eЦена: §b" + safetyPrice + " реп. ВК");
        sLore.add("§8Нажмите, чтобы купить");
        sm.setLore(sLore);
        sm.getPersistentDataContainer().set(new NamespacedKey(plugin, "safety_scroll_price"), PersistentDataType.INTEGER, safetyPrice);
        safety.setItemMeta(sm);
        items.add(safety);
    }

    private void addFusionScroll(List<ItemStack> items, RuneMarketManager market, Player p) {
        int price = plugin.getConfig().getInt("hardcore-forging.rarity-fusion-scroll.price", 10000);
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§6§lСвиток Идеального Слияния");
        meta.setCustomModelData(30);
        List<String> lore = new ArrayList<>();
        lore.add("§7Следующее слияние редкости в /forge");
        lore.add("§7будет §a100% успешным§7.");
        lore.add("");
        lore.add("§7Держите в инвентаре при слиянии.");
        lore.add("");
        lore.add("§eЦена: §b" + price + " реп. ВК");
        lore.add("§8Нажмите, чтобы купить");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "fusion_scroll_price"), PersistentDataType.INTEGER, price);
        it.setItemMeta(meta);
        items.add(it);
    }

    // ═══════════════════════════════════════════
    // СОЗДАНИЕ РУНЫ
    // ═══════════════════════════════════════════

    private ItemStack createRuneItem(RuneRegistry.RuneDef def, RuneMarketManager market, Player p) {
        int price = market.getPrice(def.getId());
        int modifiedPrice = applyMagicEventForRune(price, def.getId(), def.getCategory());

        String eventSuffix = getEventSuffix(def.getId(), isArmorRune(def.getId()));

        List<String> lore = new ArrayList<>();
        // Описание из конфига
        String rawName = plugin.getEnchantsConfig().getString("custom_enchants." + def.getId() + ".name", null);
        if (rawName != null) {
            lore.add(ChatColor.translateAlternateColorCodes('&', rawName));
            lore.add("");
        }
        // Конфликты
        List<String> conflicts = def.getConflicts();
        if (!conflicts.isEmpty()) {
            lore.add("§7Конфликты: §c" + String.join(", ", conflicts));
            lore.add("");
        }
        lore.add("§7Перетащите на предмет в инвентаре");
        lore.add("§7чтобы наложить чары!");
        lore.add("");
        lore.add("§eЦена: §b" + modifiedPrice + " реп. ВК" + eventSuffix);
        lore.add("§8Нажмите, чтобы купить");

        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✨ Руна: " + def.getName());
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_id"), PersistentDataType.STRING, def.getId());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_price"), PersistentDataType.INTEGER, modifiedPrice);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_name"), PersistentDataType.STRING, def.getName());
        Integer cmd = getRuneCustomModelData(def.getId());
        if (cmd != null) meta.setCustomModelData(cmd);
        it.setItemMeta(meta);
        return it;
    }

    // ═══════════════════════════════════════════
    // МАГИЧЕСКИЕ СОБЫТИЯ
    // ═══════════════════════════════════════════

    private int applyMagicEvent(int basePrice, String tier) {
        if (System.currentTimeMillis() >= plugin.getActiveMagicEventExpireTime()) return basePrice;
        String evt = plugin.getActiveMagicEventName();
        double mult = plugin.getActiveMagicEventMultiplier();
        if ("Магический Коллапс".equals(evt) || "Двойная Заточка".equals(evt)) {
            return (int) (basePrice * mult);
        }
        return basePrice;
    }

    private int applyMagicEventForRune(int basePrice, String runeId, String category) {
        if (System.currentTimeMillis() >= plugin.getActiveMagicEventExpireTime()) return basePrice;
        String evt = plugin.getActiveMagicEventName();
        double mult = plugin.getActiveMagicEventMultiplier();
        if ("Магический Коллапс".equals(evt)) {
            return (int) (basePrice * mult);
        } else if ("Неделя Защиты".equals(evt) && "armor".equals(category)) {
            return (int) (basePrice * mult);
        } else if ("Неделя Атаки".equals(evt) && "weapon".equals(category)) {
            return (int) (basePrice * mult);
        }
        return basePrice;
    }

    private String getEventSuffix(String id, boolean isArmor) {
        if (System.currentTimeMillis() >= plugin.getActiveMagicEventExpireTime()) return "";
        String evt = plugin.getActiveMagicEventName();
        if ("Магический Коллапс".equals(evt)) return " §c(Коллапс: +80%)";
        if ("Двойная Заточка".equals(evt)) return " §a(Скидка: -50%)";
        if ("Неделя Защиты".equals(evt) && isArmor) return " §a(Скидка: -40%)";
        if ("Неделя Атаки".equals(evt) && !isArmor) return " §a(Скидка: -40%)";
        return "";
    }

    // ═══════════════════════════════════════════
    // ОБРАБОТКА КЛИКОВ
    // ═══════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();
        if (!title.equals(HUB_TITLE) && !title.equals(WEAPON_TITLE) && !title.equals(ARMOR_TITLE)
                && !title.equals(TOOL_TITLE) && !title.equals(CRYSTAL_TITLE)) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();
        Inventory top = e.getView().getTopInventory();
        if (raw >= top.getSize()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // === ХАБ ===
        if (title.equals(HUB_TITLE)) {
            if (raw == SLOT_WEAPON) openCategory(p, "weapon", 0);
            else if (raw == SLOT_ARMOR) openCategory(p, "armor", 0);
            else if (raw == SLOT_TOOL) openCategory(p, "tool", 0);
            else if (raw == SLOT_CRYSTAL) openCategory(p, "crystal", 0);
            else if (raw == SLOT_CLOSE) p.closeInventory();
            return;
        }

        // === ПОДСТРАНИЦЫ ===
        PageInfo info = pageState.get(p.getUniqueId());

        // Навигация
        if (raw == NAV_BACK) { openHub(p); return; }
        if (raw == NAV_CLOSE) { p.closeInventory(); return; }
        if (raw == NAV_PREV && info != null) {
            openCategory(p, info.category, info.page - 1);
            return;
        }
        if (raw == NAV_NEXT && info != null) {
            openCategory(p, info.category, info.page + 1);
            return;
        }

        // Покупка руны
        if (clicked.hasItemMeta()) {
            ItemMeta meta = clicked.getItemMeta();

            // Руна
            if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "rune_price"), PersistentDataType.INTEGER)) {
                buyRune(p, clicked, meta);
                return;
            }
            // Кристалл
            if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "crystal_price"), PersistentDataType.INTEGER)) {
                buyCrystal(p, clicked, meta);
                return;
            }
            // Свиток Сохранения
            if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "safety_scroll_price"), PersistentDataType.INTEGER)) {
                buySafetyScroll(p, meta);
                return;
            }
            // Свиток Идеального Слияния
            if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "fusion_scroll_price"), PersistentDataType.INTEGER)) {
                buyFusionScroll(p, meta);
                return;
            }
        }
    }

    // ═══════════════════════════════════════════
    // ПОКУПКИ
    // ═══════════════════════════════════════════

    private void buyRune(Player p, ItemStack guiItem, ItemMeta meta) {
        int price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rune_price"), PersistentDataType.INTEGER);
        String name = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rune_name"), PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rune_id"), PersistentDataType.STRING);

        if (!checkVkLink(p)) return;
        if (!takeRep(p, price, "покупка руны: " + name)) return;

        ItemStack rune = createRuneStack(id, name);
        p.getInventory().addItem(rune).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.sendMessage(ChatColor.GREEN + "Вы купили Руну: " + name + " за " + price + " реп.");

        plugin.getRuneMarketManager().recordPurchase(id);
        reopenCurrentPage(p);
    }

    private void buyCrystal(Player p, ItemStack guiItem, ItemMeta meta) {
        int price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "crystal_price"), PersistentDataType.INTEGER);
        String name = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "crystal_name"), PersistentDataType.STRING);
        String tier = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "crystal_tier"), PersistentDataType.STRING);

        if (!checkVkLink(p)) return;
        if (!takeRep(p, price, "покупка кристалла: " + name)) return;

        Material mat = Material.EMERALD;
        String color = "§a";
        if ("rare".equals(tier)) { mat = Material.DIAMOND; color = "§9"; }
        else if ("legendary".equals(tier)) { mat = Material.PRISMARINE_SHARD; color = "§6§l"; }
        else if ("ancient".equals(tier)) { mat = Material.PRISMARINE_SHARD; color = "§5§l"; }

        ItemStack crystal = new ItemStack(mat);
        ItemMeta cMeta = crystal.getItemMeta();
        cMeta.setDisplayName(color + "💎 Кристалл Заточки: " + name);
        List<String> cLore = new ArrayList<>();
        int from = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".from", 0);
        int to = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".to", 10);
        int success = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".success", 90);
        cLore.add("§7Заточка от +" + from + " до +" + to + ", шанс " + success + "%");
        cLore.add("§7Перетащите на предмет для заточки!");
        cMeta.setLore(cLore);
        Integer cmd = getCrystalCustomModelData(tier);
        if (cmd != null) cMeta.setCustomModelData(cmd);
        cMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_tier"), PersistentDataType.STRING, tier);
        cMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_name"), PersistentDataType.STRING, name);
        crystal.setItemMeta(cMeta);

        p.getInventory().addItem(crystal).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(ChatColor.GREEN + "Вы купили Кристалл: " + name + " за " + price + " реп.!");

        plugin.getRuneMarketManager().recordPurchase("crystal_" + tier);
        reopenCurrentPage(p);
    }

    private void buySafetyScroll(Player p, ItemMeta meta) {
        int price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "safety_scroll_price"), PersistentDataType.INTEGER);

        if (!checkVkLink(p)) return;
        if (!takeRep(p, price, "покупка Свитка Сохранения")) return;

        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta sMeta = scroll.getItemMeta();
        sMeta.setDisplayName("§d§lСвиток Сохранения");
        sMeta.setCustomModelData(54);
        sMeta.setLore(Arrays.asList(
                "§7Защищает предмет от отката",
                "§7уровня заточки при неудаче!",
                "§7Держите в инвентаре при заточке.",
                "§7Расходуется автоматически."));
        sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_safety_scroll"), PersistentDataType.INTEGER, 1);
        scroll.setItemMeta(sMeta);

        p.getInventory().addItem(scroll).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(ChatColor.GREEN + "Вы купили Свиток Сохранения за " + price + " реп.!");

        plugin.getRuneMarketManager().recordPurchase("safety_scroll");
        reopenCurrentPage(p);
    }

    private void buyFusionScroll(Player p, ItemMeta meta) {
        int price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "fusion_scroll_price"), PersistentDataType.INTEGER);

        if (!checkVkLink(p)) return;
        if (!takeRep(p, price, "покупка Свитка Идеального Слияния")) return;

        ItemStack scroll = new ItemStack(Material.NETHER_STAR);
        ItemMeta fMeta = scroll.getItemMeta();
        fMeta.setDisplayName("§6§lСвиток Идеального Слияния");
        fMeta.setCustomModelData(30);
        fMeta.setLore(Arrays.asList(
                "§7Следующее слияние редкости в /forge",
                "§7будет §a100% успешным§7.",
                "§8Расходуется автоматически."));
        fMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_fusion_scroll"), PersistentDataType.INTEGER, 1);
        scroll.setItemMeta(fMeta);

        p.getInventory().addItem(scroll).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.sendMessage(ChatColor.GOLD + "Вы купили Свиток Идеального Слияния за " + price + " реп. ВК!");

        plugin.getRuneMarketManager().recordPurchase("fusion_scroll");
        reopenCurrentPage(p);
    }

    // ═══════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════

    private void reopenCurrentPage(Player p) {
        PageInfo info = pageState.get(p.getUniqueId());
        if (info != null) {
            openCategory(p, info.category, info.page);
        } else {
            openHub(p);
        }
    }

    private boolean checkVkLink(Player p) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        boolean hasPass = VKChatBridge.hasPass(p);
        if (vkId == -1 && !hasPass) {
            p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
            p.closeInventory();
            return false;
        }
        return true;
    }

    private boolean takeRep(Player p, int cost, String action) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        boolean hasPass = VKChatBridge.hasPass(p);
        if (vkId != -1) {
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + cost + ", у вас: " + rep);
                return false;
            }
            VKChatBridge.takeReputation(vkId, cost);
        } else if (hasPass) {
            int localRep = VKChatBridge.getLocalReputation(p);
            if (localRep < cost) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации! Нужно: " + cost + ", у вас: " + localRep);
                return false;
            }
            VKChatBridge.takeLocalReputation(p, cost);
        }
        return true;
    }

    private ItemStack createRuneStack(String id, String name) {
        ItemStack rune = new ItemStack(Material.NETHER_STAR);
        ItemMeta rMeta = rune.getItemMeta();
        rMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "✨ Руна: " + name);
        List<String> rLore = new ArrayList<>();
        if (id != null) {
            String desc = plugin.getEnchantsConfig().getString("custom_enchants." + id + ".name", null);
            if (desc != null) {
                rLore.add(ChatColor.translateAlternateColorCodes('&', desc));
                rLore.add("");
            }
        }
        rLore.add(ChatColor.GRAY + "Перетащите на предмет в инвентаре,");
        rLore.add(ChatColor.GRAY + "чтобы наложить чары!");
        rMeta.setLore(rLore);
        rMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_name"), PersistentDataType.STRING, name);
        Integer runeCmd = getRuneCustomModelData(id);
        if (runeCmd != null) rMeta.setCustomModelData(runeCmd);
        rune.setItemMeta(rMeta);
        return rune;
    }

    private ItemStack createBalanceItem(Player p) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        boolean hasPass = VKChatBridge.hasPass(p);
        int balance = 0;
        if (vkId != -1) {
            balance = VKChatBridge.getReputation(vkId);
        } else if (hasPass) {
            balance = VKChatBridge.getLocalReputation(p);
        }
        String source = vkId != -1 ? "ВК" : (hasPass ? "проходка" : "нет привязки");
        return item(Material.EMERALD_BLOCK, "§a§l💰 Ваш баланс",
                "§e" + balance + " реп. ВК",
                "§7Источник: " + source);
    }

    private boolean isArmorRune(String id) {
        if (id == null) return false;
        RuneRegistry registry = plugin.getRuneRegistry();
        if (registry != null) {
            RuneRegistry.RuneDef def = registry.getRune(id);
            return def != null && "armor".equals(def.getCategory());
        }
        return false;
    }

    // === Фабрики предметов ===

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glass(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    private static void setItem(Inventory inv, int slot, Material mat, String name, String... lore) {
        inv.setItem(slot, item(mat, name, lore));
    }

    private static Integer getRuneCustomModelData(String id) {
        if (id == null) return null;
        switch (id) {
            case "blood_rune": case "chaos_rune": return 10;
            case "frost_rune": return 11;
            case "poison_rune": return 12;
            case "lightning_rune": case "holy_rune": return 13;
            case "shadow_rune": case "void_rune": return 14;
            case "arcane_rune": return 15;
            case "darkness_rune": return 16;
            case "death_rune": return 17;
            case "earth_rune": return 18;
            case "farming_rune": return 19;
            case "fire_rune": return 20;
            case "flame_rune": return 21;
            case "fishing_rune": return 22;
            case "health_rune": return 23;
            case "ice_rune": return 24;
            case "iron_rune": return 25;
            case "light_rune": return 26;
            case "loot_rune": return 27;
            case "luck_rune": return 28;
            case "mining_rune": return 29;
            case "nature_rune": return 31;
            case "speed_rune": return 32;
            case "spirit_rune": return 33;
            case "stone_rune": return 34;
            case "strength_rune": return 35;
            case "thunder_rune": return 36;
            case "time_rune": return 37;
            case "water_rune": return 38;
            case "wind_rune": return 39;
            case "xp_rune": return 40;
            default: return null;
        }
    }

    private static Integer getCrystalCustomModelData(String tier) {
        if (tier == null) return null;
        switch (tier) {
            case "common": return 50;
            case "rare": return 51;
            case "legendary": return 6;
            case "ancient": return 52;
            default: return null;
        }
    }
}
