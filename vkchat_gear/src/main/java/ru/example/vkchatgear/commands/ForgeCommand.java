package ru.example.vkchatgear.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatgear.VKChatGearPlugin;

import org.bukkit.command.TabCompleter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ForgeCommand implements CommandExecutor, Listener, TabCompleter {
    private final VKChatGearPlugin plugin;

    private final String HUB_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §7Меню";
    private final String FUSION_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §dСлияние";
    private final String REFORGE_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §cПерековка";
    private final String CLEANSE_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §bОчищение";
    private final String REPAIR_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §aРемонт";
    private final String SCROLLS_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §eСвитки";
    private final String RUNE_CLEANSING_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §5Руны";
    private final String ARTIFACTS_TITLE = "§8▸ §4§lКУЗНЯ §8◂ §dАртефакты";

    private static final int[] FUSION_SLOTS = {20, 22, 24};
    private static final int CENTER_SLOT = 22;
    private static final int CONFIRM_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private final Map<UUID, PendingOp> pending = new java.util.concurrent.ConcurrentHashMap<>();

    private static class PendingOp {
        String action;
        long created = System.currentTimeMillis();
        int repCost;
        Material materialCost;
        int materialAmount;
        int chance;
        String targetRarity;
        boolean guaranteed;
        boolean protection;
        boolean antiDefect;
        boolean discountScroll;
        String summary;
    }

    public ForgeCommand(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("vkchat.admin")) {
            plugin.reloadConfig();
            sender.sendMessage("§a§l⚙ §aКонфиг кузни перезагружен!");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats") && sender.hasPermission("vkchat.admin")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            p.sendMessage("§8▸ §4§lСтатистика кузни §8◂");
            p.sendMessage("§7Активных операций: §f" + pending.size());
            p.sendMessage("§7Сетов: §f" + plugin.getSetBonusManager().getSetDefs().size());
            if (plugin.getActiveMagicEventName() != null && System.currentTimeMillis() < plugin.getActiveMagicEventExpireTime()) {
                long rem = (plugin.getActiveMagicEventExpireTime() - System.currentTimeMillis()) / 1000;
                p.sendMessage("§7Маг. событие: §d" + plugin.getActiveMagicEventName() + " §7(" + rem + "с)");
            } else {
                p.sendMessage("§7Маг. событие: §7нет");
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("schedule") && sender.hasPermission("vkchat.forge")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            ru.example.vkchatgear.enhancements.GearEnhancements enhancements = plugin.getGearEnhancements();
            if (enhancements != null) {
                for (String line : enhancements.getMagicEventSchedule()) {
                    p.sendMessage(line);
                }
            } else {
                p.sendMessage("§7Модуль улучшений не загружен.");
            }
            return true;
        }
        if (!(sender instanceof Player)) return true;
        openHub((Player) sender);
        return true;
    }

    public void openMenu(Player p) { openHub(p); }

    private void openHub(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, HUB_TITLE);

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) ? border : accent);

        inv.setItem(4, item(Material.ANVIL, "§4§l⚒ МИСТИЧЕСКАЯ КУЗНЯ",
                "§7Древний горн работает с MMO-предметами",
                "§7и репутацией ВК. Все операции требуют",
                "§7предпросмотр и подтверждение."));

        inv.setItem(20, item(Material.NETHER_STAR, "§d⭐ Слияние редкости",
                "§7Три предмета одной редкости → один выше",
                "§7Центр — улучшается, бока — катализаторы",
                "", "§e▶ Открыть"));
        inv.setItem(22, item(Material.NETHERITE_INGOT, "§c🔥 Перековка",
                "§7Полный переброс свойств предмета",
                "§7Может добавить дефект",
                "", "§e▶ Открыть"));
        inv.setItem(24, item(Material.GRINDSTONE, "§b🕯 Очищение",
                "§7Снимает дефекты с предмета",
                "§7Цена зависит от глубины дефекта",
                "", "§e▶ Открыть"));

        inv.setItem(29, item(Material.IRON_INGOT, "§a🔧 Ремонт",
                "§7Восстановление прочности MMO-шмота",
                "§7Цена: репутация + материал предмета",
                "", "§e▶ Открыть"));
        inv.setItem(31, item(Material.PAPER, "§e📜 Свитки кузни",
                "§7Шанс, защита, анти-дефект, скидка",
                "§7Используются автоматически из инвентаря",
                "", "§e▶ Открыть"));
        inv.setItem(33, item(Material.PURPUR_BLOCK, "§5💀 Очищение рун",
                "§7Снимает все кастомные чары с предмета",
                "§7Цена: " + plugin.getRuneMarketConfig().getInt("rune-cleansing.cost", 500) + " реп + " +
                        plugin.getRuneMarketConfig().getInt("rune-cleansing.material-amount", 1) + "x " +
                        plugin.getRuneMarketConfig().getString("rune-cleansing.material", "DIAMOND_BLOCK"),
                "", "§e▶ Открыть"));

        inv.setItem(35, item(Material.NETHER_STAR, "§d🔮 Артефакты",
                "§7Мистические предметы силы",
                "§7Надеваются в доп. руку",
                "§7Макс. 5 активных",
                "", "§e▶ Открыть"));

        inv.setItem(49, item(Material.BOOK, "§e📖 Правила",
                "§7• Предпросмотр всегда обязателен",
                "§7• Цена = редкость + сила предмета",
                "§7• Кузнец (Jobs) даёт бонусы"));
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, "§c✕ Закрыть"));
        p.openInventory(inv);
    }

    private void openFusion(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, FUSION_TITLE);
        fill(inv, Material.RED_STAINED_GLASS_PANE);
        for (int s : FUSION_SLOTS) inv.setItem(s, null);
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "⭐ Слияние редкости",
                ChatColor.GRAY + "Положи любые 3 MMO-предмета одной редкости.",
                ChatColor.GRAY + "Слот 22 — цель, 20 и 24 — катализаторы.",
                ChatColor.RED + "Первый клик покажет предпросмотр. Второй — подтверждение."));
        inv.setItem(29, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Катализатор"));
        inv.setItem(31, marker(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.GOLD + "Цель"));
        inv.setItem(33, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Катализатор"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр слияния",
                ChatColor.GRAY + "Покажет шанс, цену, ресурсы и свитки."));
        nav(inv);
        p.openInventory(inv);
    }

    private void openReforge(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, REFORGE_TITLE);
        fill(inv, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.NETHERITE_INGOT, ChatColor.RED + "🔥 Перековка предмета",
                ChatColor.GRAY + "Положи предмет в центральный слот.",
                ChatColor.GRAY + "Перековка может усилить чары/свойство,",
                ChatColor.GRAY + "но может оставить дефект."));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр перековки"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openCleanse(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, CLEANSE_TITLE);
        fill(inv, Material.CYAN_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.GRINDSTONE, ChatColor.AQUA + "🕯 Очищение дефектов",
                ChatColor.GRAY + "Положи дефектный предмет в центральный слот.",
                ChatColor.GRAY + "Очищение требует репутацию и ресурсы."));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openRepair(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, REPAIR_TITLE);
        fill(inv, Material.GREEN_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.IRON_INGOT, ChatColor.GREEN + "🔧 Ремонт MMO-предмета",
                ChatColor.GRAY + "Положи повреждённый предмет в центральный слот.",
                ChatColor.GRAY + "Ремонт восстанавливает прочность до 100%.",
                ChatColor.GRAY + "Цена: репутация ВК + материал предмета.",
                ChatColor.RED + "Обычная наковальня для MMO Gear отключена."));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр ремонта"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openScrolls(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, SCROLLS_TITLE);
        fill(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(4, item(Material.PAPER, ChatColor.GOLD + "📜 Свитки горна",
                ChatColor.GRAY + "Свитки используются автоматически из инвентаря",
                ChatColor.GRAY + "при следующей подходящей операции кузни."));
        addScrollShopItem(inv, 19, "chance_25", Material.PAPER, "&dСвиток Точного Слияния", "+25% к шансу слияния");
        addScrollShopItem(inv, 20, "chance_50", Material.MAP, "&5Свиток Сильного Слияния", "+50% к шансу слияния");
        addScrollShopItem(inv, 21, "perfect", Material.NETHER_STAR, "&6&lСвиток Идеального Слияния", "100% успех слияния");
        addScrollShopItem(inv, 23, "protect_all", Material.TOTEM_OF_UNDYING, "&bСвиток Полной Защиты", "При провале сохраняет цель и катализаторы");
        addScrollShopItem(inv, 24, "anti_defect", Material.HONEYCOMB, "&aСвиток Чистой Стали", "Защита от дефекта при перековке");
        addScrollShopItem(inv, 25, "discount", Material.EMERALD, "&2Свиток Скидки", "-25% репутационной цены операции");
        nav(inv);
        p.openInventory(inv);
    }

    private void openRuneCleansing(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, RUNE_CLEANSING_TITLE);
        fill(inv, Material.MAGENTA_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.PURPUR_BLOCK, ChatColor.DARK_PURPLE + "💀 Очищение рун",
                ChatColor.GRAY + "Положи предмет с кастомными чарами в центральный слот.",
                ChatColor.GRAY + "Удаляет ВСЕ кастомные чары с предмета.",
                ChatColor.YELLOW + "Стоимость: " + plugin.getRuneMarketConfig().getInt("rune-cleansing.cost", 500) + " реп. ВК",
                ChatColor.YELLOW + "Ресурс: " + plugin.getRuneMarketConfig().getInt("rune-cleansing.material-amount", 1) + "x " +
                        plugin.getRuneMarketConfig().getString("rune-cleansing.material", "DIAMOND_BLOCK")));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения рун"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openArtifacts(Player p) {
        pending.remove(p.getUniqueId());
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        Inventory inv = Bukkit.createInventory(null, 54, ARTIFACTS_TITLE);
        fill(inv, Material.PURPLE_STAINED_GLASS_PANE);

        int count = mgr != null ? mgr.getActiveArtifactCount(p) : 0;

        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.DARK_PURPLE + "🔮 Артефакты — Кузня",
                ChatColor.GRAY + "Улучшайте и создавайте мистические артефакты.",
                ChatColor.YELLOW + "Артефактов: " + count + " / 5"));

        // Верхний ряд — основные операции
        inv.setItem(11, item(Material.CRYING_OBSIDIAN, ChatColor.DARK_RED + "💔 Дезинтеграция",
                ChatColor.GRAY + "Разобрать артефакт на материалы.",
                ChatColor.GRAY + "Рунные жетоны, кристаллы, фрагменты.",
                ChatColor.YELLOW + "Бесплатно", "", "§e▶ Открыть"));

        inv.setItem(13, item(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "💧 Эссенция артефакта",
                ChatColor.GRAY + "Извлечь эссенцию артефакта.",
                ChatColor.GRAY + "Жидкий предмет со статами.",
                ChatColor.YELLOW + "Цена: 600 реп. ВК", "", "§e▶ Открыть"));

        // Средний ряд — улучшение
        inv.setItem(20, item(Material.ANVIL, ChatColor.GREEN + "⬆ Заточка артефакта",
                ChatColor.GRAY + "Усилить все статы артефакта на +10%.",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.YELLOW + "Цена: 800 реп. ВК + 2x алмазов",
                "", "§e▶ Открыть"));

        inv.setItem(22, item(Material.SMITHING_TABLE, ChatColor.RED + "🔄 Перековка артефакта",
                ChatColor.GRAY + "Перебросить один случайный стат на другой.",
                ChatColor.GRAY + "Может изменить тип стата (урон→защита и т.д.).",
                ChatColor.YELLOW + "Цена: 1200 реп. ВК + 4x алмазов",
                "", "§e▶ Открыть"));

        inv.setItem(24, item(Material.NETHERITE_INGOT, ChatColor.GOLD + "⭐ Слияние артефактов",
                ChatColor.GRAY + "3 артефакта одной редкости → 1 выше.",
                ChatColor.GRAY + "Боковые катализаторы сгорают.",
                ChatColor.YELLOW + "Цена: 2000 реп. ВК + 3x незеритовых ломтиков",
                "", "§e▶ Открыть"));

        // Нижний ряд — специальные операции
        inv.setItem(29, item(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "🔒 Бинд артефакта",
                ChatColor.GRAY + "Привязать артефакт к игроку.",
                ChatColor.GRAY + "Нельзя выбросить/передать/положить в сундук.",
                ChatColor.YELLOW + "Цена: 400 реп. ВК", "", "§e▶ Открыть"));

        inv.setItem(31, item(Material.BREWING_STAND, ChatColor.YELLOW + "⚗ Трансмутация",
                ChatColor.GRAY + "Преобразовать 1 стат артефакта",
                ChatColor.GRAY + "в постоянный бонус оружия/брони.",
                ChatColor.YELLOW + "Цена: 1500 реп. ВК + 1x алмазов",
                "", "§e▶ Открыть"));

        inv.setItem(33, item(Material.SOUL_CAMPFIRE, ChatColor.DARK_AQUA + "🕯 Артефакт-ритуал",
                ChatColor.GRAY + "2 артефакта + руна =",
                ChatColor.GRAY + "артефакт с зачарованной руной.",
                ChatColor.YELLOW + "Цена: 2500 реп. ВК + 2x алмазов",
                "", "§e▶ Открыть"));

        // Правила — слот 49
        inv.setItem(49, item(Material.BOOK, ChatColor.YELLOW + "📖 Правила",
                ChatColor.GRAY + "• Заточка: +10% статов за уровень",
                ChatColor.GRAY + "• Перековка: перераспределение статов",
                ChatColor.GRAY + "• Слияние: 3 → 1 (повышение редкости)",
                ChatColor.GRAY + "• Дезинтеграция: разбор на материалы",
                ChatColor.GRAY + "• Эссенция: извлечение статов",
                ChatColor.GRAY + "• Бинд: привязка к игроку",
                ChatColor.GRAY + "• Трансмутация: стат → бонус снаряжения",
                ChatColor.GRAY + "• Ритуал: 2 артефакта + руна = зачарование",
                ChatColor.GRAY + "• Лимит: 5 артефактов"));

        nav(inv);
        p.openInventory(inv);
    }

    private void openArtifactSharpen(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §aЗаточка Артефакта");
        fill(inv, Material.GREEN_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.ANVIL, ChatColor.GREEN + "⬆ Заточка Артефакта",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.GRAY + "Каждый уровень: +10% ко всем статам.",
                ChatColor.GRAY + "Максимум: +5 уровней (макс. 150% статов).",
                ChatColor.YELLOW + "Цена: 800 реп. ВК + 2x DIAMOND"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр заточки"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openArtifactReforge(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §cПерековка Артефакта");
        fill(inv, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.SMITHING_TABLE, ChatColor.RED + "🔄 Перековка Артефакта",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.GRAY + "Случайный стат будет перераспределён.",
                ChatColor.GRAY + "Может изменить тип и значение стата.",
                ChatColor.YELLOW + "Цена: 1200 реп. ВК + 4x DIAMOND"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр перековки"));
        nav(inv);
        p.openInventory(inv);
    }

    private void openArtifactFusion(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §6Слияние Артефактов");
        fill(inv, Material.ORANGE_STAINED_GLASS_PANE);
        for (int s : FUSION_SLOTS) inv.setItem(s, null);
        inv.setItem(4, item(Material.NETHERITE_INGOT, ChatColor.GOLD + "⭐ Слияние Артефактов",
                ChatColor.GRAY + "Положи 3 артефакта одной редкости.",
                ChatColor.GRAY + "Слот 22 — цель, 20 и 24 — катализаторы.",
                ChatColor.RED + "Катализаторы сгорят при успехе!",
                ChatColor.YELLOW + "Цена: 2000 реп. ВК + 3x NETHERITE_SCRAP"));
        inv.setItem(29, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Катализатор"));
        inv.setItem(31, marker(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.GOLD + "Цель"));
        inv.setItem(33, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Катализатор"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр слияния"));
        nav(inv);
        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ДЕЗИНТЕГРАЦИЯ
    // ═══════════════════════════════════════════

    private void openArtifactDisintegrate(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §4Дезинтеграция");
        fill(inv, Material.RED_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.CRYING_OBSIDIAN, ChatColor.DARK_RED + "💔 Дезинтеграция Артефакта",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.GRAY + "Разобьёт на материалы по редкости:",
                ChatColor.GRAY + "  Common: 2x Рунный жетон",
                ChatColor.GRAY + "  Rare: 1x Кристалл + 3x Рунный жетон",
                ChatColor.GRAY + "  Epic: 2x Кристалл + фрагмент сета",
                ChatColor.GRAY + "  Legendary: 3x Кристалл + 2x фрагмента",
                ChatColor.GRAY + "  Ancient: 5x Кристалл + 3x фрагмента",
                ChatColor.GREEN + "Бесплатно!"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр разбора"));
        nav(inv);
        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ЭССЕНЦИЯ
    // ═══════════════════════════════════════════

    private void openArtifactEssence(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §bЭссенция Артефакта");
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "💧 Эссенция Артефакта",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.GRAY + "Создаёт жидкую эссенцию со статами.",
                ChatColor.GRAY + "Эссенцию можно использовать в кузнечных операциях.",
                ChatColor.YELLOW + "Цена: 600 реп. ВК"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр извлечения"));
        nav(inv);
        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — БИНД
    // ═══════════════════════════════════════════

    private void openArtifactBind(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §5Бинд Артефакта");
        fill(inv, Material.MAGENTA_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "🔒 Бинд Артефакта",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.GRAY + "Привяжет артефакт к тебе.",
                ChatColor.RED + "После бинда нельзя:",
                ChatColor.RED + "  • Выбросить",
                ChatColor.RED + "  • Передать другому игроку",
                ChatColor.RED + "  • Положить в сундук/шалкер",
                ChatColor.RED + "  • Положить в эндер-чест",
                ChatColor.YELLOW + "Цена: 400 реп. ВК"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр бинда"));
        nav(inv);
        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ТРАНСМУТАЦИЯ
    // ═══════════════════════════════════════════

    private void openArtifactTransmute(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §eТрансмутация");
        fill(inv, Material.YELLOW_STAINED_GLASS_PANE);
        inv.setItem(CENTER_SLOT, null);
        inv.setItem(4, item(Material.BREWING_STAND, ChatColor.YELLOW + "⚗ Трансмутация Артефакта",
                ChatColor.GRAY + "Положи артефакт в центральный слот.",
                ChatColor.GRAY + "Выбери 1 стат для переноса на снаряжение.",
                ChatColor.GRAY + "Бонус.apply к первому оружию/броне в инвентаре.",
                ChatColor.RED + "⚠ Необратимо! Стат удалится из артефакта.",
                ChatColor.YELLOW + "Цена: 1500 реп. ВК + 1x DIAMOND"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр трансмутации"));
        nav(inv);
        p.openInventory(inv);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — РИТУАЛ
    // ═══════════════════════════════════════════

    private void openArtifactRitual(Player p) {
        pending.remove(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §4§lКУЗНЯ §8◂ §3Артефакт-ритуал");
        fill(inv, Material.CYAN_STAINED_GLASS_PANE);
        for (int s : FUSION_SLOTS) inv.setItem(s, null);
        inv.setItem(4, item(Material.SOUL_CAMPFIRE, ChatColor.DARK_AQUA + "🕯 Артефакт-ритуал",
                ChatColor.GRAY + "Положи 2 артефакта одной редкости",
                ChatColor.GRAY + "и 1 руну (из инвентаря) в слоты 20/22/24.",
                ChatColor.GRAY + "Руна применяется как зачарование.",
                ChatColor.GRAY + "Статы руны добавляются к статам артефакта.",
                ChatColor.RED + "Руна сгорит при успехе!",
                ChatColor.YELLOW + "Цена: 2500 реп. ВК + 2x DIAMOND"));
        inv.setItem(29, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Артефакт"));
        inv.setItem(31, marker(Material.CYAN_STAINED_GLASS_PANE, ChatColor.AQUA + "Руна"));
        inv.setItem(33, marker(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Артефакт"));
        inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр ритуала"));
        nav(inv);
        p.openInventory(inv);
    }

    private void addScrollShopItem(Inventory inv, int slot, String type, Material mat, String name, String desc) {
        int price = plugin.getForgeAdvancedConfig().getInt("forge2.scrolls." + type + ".price", defaultScrollPrice(type));
        ItemStack it = item(mat, ChatColor.translateAlternateColorCodes('&', name),
                ChatColor.GRAY + desc,
                "",
                ChatColor.YELLOW + "Цена: " + price + " реп. ВК",
                ChatColor.DARK_GRAY + "Клик — купить");
        ItemMeta meta = it.getItemMeta();
        if (type.equals("perfect")) meta.setCustomModelData(30);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "forge_scroll_shop_type"), PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "forge_scroll_shop_price"), PersistentDataType.INTEGER, price);
        it.setItemMeta(meta);
        inv.setItem(slot, it);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();
        if (!isForgeTitle(title)) return;
        Player p = (Player) e.getWhoClicked();
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();

        if (raw >= top.getSize()) {
            boolean isArtifactGui = title.contains("Заточка Артефакта") || title.contains("Перековка Артефакта") || title.contains("Слияние Артефактов") || title.contains("Эссенция") || title.contains("Дезинтеграция") || title.contains("Бинд Артефакта") || title.contains("Трансмутация") || title.contains("Артефакт-ритуал");
            boolean isGearGui = title.equals(FUSION_TITLE) || title.equals(REFORGE_TITLE) || title.equals(CLEANSE_TITLE) || title.equals(REPAIR_TITLE);
            boolean canShift = isGearGui || isArtifactGui;
            if (canShift && e.isShiftClick() && e.getCurrentItem() != null) {
                boolean isGear = plugin.getGearManager().isGear(e.getCurrentItem().getType());
                boolean isArt = ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(e.getCurrentItem());
                if (isGear || isArt) {
                    int free;
                    if (title.equals(FUSION_TITLE) || title.contains("Слияние Артефактов")) {
                        free = firstFreeFusionSlot(top);
                    } else {
                        free = isEmpty(top.getItem(CENTER_SLOT)) ? CENTER_SLOT : -1;
                    }
                    if (free != -1) {
                        e.setCancelled(true);
                        top.setItem(free, e.getCurrentItem().clone());
                        e.getCurrentItem().setAmount(0);
                        pending.remove(p.getUniqueId());
                        resetConfirmButton(title, top);
                    }
                }
            }
            e.setCancelled(true);
            return;
        }

        if (isWorkSlot(title, raw)) {
            pending.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> resetConfirmButton(title, top));
            return;
        }

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(HUB_TITLE)) {
            if (raw == 20) openFusion(p);
            else if (raw == 22) openReforge(p);
            else if (raw == 24) openCleanse(p);
            else if (raw == 29) openRepair(p);
            else if (raw == 31) openScrolls(p);
            else if (raw == 33) openRuneCleansing(p);
            else if (raw == 35) openArtifacts(p);
            else if (raw == CLOSE_SLOT) p.closeInventory();
            return;
        }

        if (title.equals(ARTIFACTS_TITLE)) {
            if (raw == 11) { openArtifactDisintegrate(p); return; }
            else if (raw == 13) { openArtifactEssence(p); return; }
            else if (raw == 20) { openArtifactSharpen(p); return; }
            else if (raw == 22) { openArtifactReforge(p); return; }
            else if (raw == 24) { openArtifactFusion(p); return; }
            else if (raw == 29) { openArtifactBind(p); return; }
            else if (raw == 31) { openArtifactTransmute(p); return; }
            else if (raw == 33) { openArtifactRitual(p); return; }
            return;
        }

        if (raw == BACK_SLOT) { returnInputs(p, top); openHub(p); return; }
        if (raw == CLOSE_SLOT) { returnInputs(p, top); p.closeInventory(); return; }

        if (title.equals(SCROLLS_TITLE)) {
            buyScroll(p, clicked);
            return;
        }

        if (raw == CONFIRM_SLOT) {
            if (title.equals(FUSION_TITLE)) handleFusionButton(p, top);
            else if (title.equals(REFORGE_TITLE)) handleReforgeButton(p, top);
            else if (title.equals(CLEANSE_TITLE)) handleCleanseButton(p, top);
            else if (title.equals(REPAIR_TITLE)) handleRepairButton(p, top);
            else if (title.equals(RUNE_CLEANSING_TITLE)) handleRuneCleansingButton(p, top);
            else if (title.contains("Заточка Артефакта")) handleArtifactSharpenButton(p, top);
            else if (title.contains("Перековка Артефакта")) handleArtifactReforgeButton(p, top);
            else if (title.contains("Слияние Артефактов")) handleArtifactFusionButton(p, top);
            else if (title.contains("Дезинтеграция")) handleArtifactDisintegrateButton(p, top);
            else if (title.contains("Эссенция")) handleArtifactEssenceButton(p, top);
            else if (title.contains("Бинд Артефакта")) handleArtifactBindButton(p, top);
            else if (title.contains("Трансмутация")) handleArtifactTransmuteButton(p, top);
            else if (title.contains("Артефакт-ритуал")) handleArtifactRitualButton(p, top);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!isForgeTitle(e.getView().getTitle())) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        
        // Очистка GUI свитков — не выдавать предметы из магазина
        if (e.getView().getTitle().equals(SCROLLS_TITLE)) {
            for (int i = 0; i < e.getInventory().getSize(); i++) {
                ItemStack item = e.getInventory().getItem(i);
                if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                        .has(new NamespacedKey(plugin, "forge_scroll_shop_type"), PersistentDataType.STRING)) {
                    e.getInventory().setItem(i, null);
                }
            }
            return;
        }
        
        if (e.getView().getTitle().equals(HUB_TITLE)) return;
        pending.remove(p.getUniqueId());
        returnInputs(p, e.getInventory());
    }

    private void handleFusionButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("fusion") && System.currentTimeMillis() - op.created < 30000L) {
            executeFusion(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton(FUSION_TITLE, inv);
            return;
        }
        op = previewFusion(p, inv);
        if (op != null) {
            pending.put(p.getUniqueId(), op);
            inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить слияние",
                    op.summary.split("\n")));
        }
    }

    private PendingOp previewFusion(Player p, Inventory inv) {
        ItemStack left = inv.getItem(20), center = inv.getItem(22), right = inv.getItem(24);
        if (!isValidFusionItem(left) || !isValidFusionItem(center) || !isValidFusionItem(right)) {
            p.sendMessage(ChatColor.RED + "Положи 3 MMO-предмета одной редкости в слоты 20/22/24.");
            return null;
        }
        String rarity = plugin.getGearManager().getRarityKey(center);
        if (!rarity.equals(plugin.getGearManager().getRarityKey(left)) || !rarity.equals(plugin.getGearManager().getRarityKey(right))) {
            p.sendMessage(ChatColor.RED + "Все 3 предмета должны быть одной редкости.");
            return null;
        }
        String next = nextRarity(rarity);
        if (next == null) { p.sendMessage(ChatColor.RED + "Легендарную редкость выше повысить нельзя."); return null; }

        PendingOp op = new PendingOp();
        op.action = "fusion";
        op.targetRarity = next;
        int power = itemPower(center) + itemPower(left) / 2 + itemPower(right) / 2;
        int baseCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.rarity-upgrade-cost", 500);
        // Ancient fusion requires 5000 rep minimum
        if (next.equals("ancient")) {
            baseCost = Math.max(baseCost, 5000);
        }
        op.repCost = Math.max(1, baseCost + power * plugin.getForgeAdvancedConfig().getInt("forge2.cost.power-rep-multiplier", 18) + rarityIndex(next) * plugin.getForgeAdvancedConfig().getInt("forge2.cost.rarity-rep-step", 350));
        op.materialCost = materialCostFor(next);
        op.materialAmount = materialAmountFor(next) + Math.max(0, power / 12);
        op.chance = plugin.getConfig().getInt("hardcore-forging.rarity-upgrade-chance." + next, defaultUpgradeChance(next));
        int bs = plugin.getGearManager().getBlacksmithLevel(p);
        int jobBonus = Math.min(plugin.getForgeAdvancedConfig().getInt("forge2.blacksmith.max-chance-bonus", 10), bs / 5);
        op.chance += jobBonus;
        op.guaranteed = hasScroll(p, "perfect");
        if (!op.guaranteed) {
            if (hasScroll(p, "chance_50")) op.chance += 50;
            else if (hasScroll(p, "chance_25")) op.chance += 25;
        }
        op.protection = hasScroll(p, "protect_all");
        op.discountScroll = hasScroll(p, "discount");
        if (op.discountScroll) op.repCost = (int)Math.round(op.repCost * 0.75);
        op.chance = Math.min(100, op.chance);
        op.summary = ChatColor.GOLD + "Слияние: " + rarityDisplay(rarity) + ChatColor.GRAY + " → " + rarityDisplay(next) + "\n" +
                ChatColor.YELLOW + "Шанс: " + (op.guaranteed ? "100% (свиток)" : op.chance + "%") + "\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.RED + "Провал: катализаторы сгорят" + (op.protection ? " (оберег спасёт всё)" : "") + "\n" +
                ChatColor.GRAY + "Кликни ещё раз по зелёной кнопке.";
        p.sendMessage(ChatColor.GOLD + "⚒ Предпросмотр слияния готов. Проверь кнопку подтверждения.");
        return op;
    }

    private void executeFusion(Player p, Inventory inv, PendingOp op) {
        ItemStack left = inv.getItem(20), center = inv.getItem(22), right = inv.getItem(24);
        if (!isValidFusionItem(left) || !isValidFusionItem(center) || !isValidFusionItem(right)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "слияние редкости")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        consumeRelevantScrolls(p, op);
        boolean success = op.guaranteed || ThreadLocalRandom.current().nextInt(100) < op.chance;
        log(p, "FUSION_START", op.summary.replace("§", "&"));
        ru.example.vkchatgear.enhancements.GearEnhancements enhancements = plugin.getGearEnhancements();
        if (success) {
            ItemStack result = center.clone();
            applyRarity(result, op.targetRarity);
            maybeUpgradeEnchant(result);
            applyRarityProc(result, op.targetRarity);
            markLegacyIfNeeded(result);
            inv.setItem(20, null); inv.setItem(22, result); inv.setItem(24, null);
            if (enhancements != null) {
                enhancements.playFusionSuccess(p);
                enhancements.playFusionAnimation(p, true);
                enhancements.announceNamedGear(p, result, op.targetRarity);
            } else {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 70, 0.7, 0.7, 0.7, 0.2);
                announceIfNeeded(p, result, op.targetRarity);
            }
            p.sendMessage(ChatColor.GOLD + "⭐ Успех! Редкость повышена до " + rarityDisplay(op.targetRarity));
            log(p, "FUSION_SUCCESS", itemName(result));
        } else {
            if (op.protection) {
                inv.setItem(20, left); inv.setItem(22, center); inv.setItem(24, right);
                p.sendMessage(ChatColor.AQUA + "🛡 Свиток Полной Защиты сохранил все предметы. Списаны только цена и ресурсы.");
                log(p, "FUSION_FAIL_PROTECTED", itemName(center));
            } else {
                inv.setItem(20, null); inv.setItem(22, center); inv.setItem(24, null);
                p.sendMessage(ChatColor.RED + "❌ Провал. Центральный предмет сохранён, катализаторы сгорели.");
                log(p, "FUSION_FAIL", itemName(center));
            }
            if (enhancements != null) {
                enhancements.playFusionFail(p);
                enhancements.playFusionAnimation(p, false);
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
            }
        }
    }

    private void handleReforgeButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("reforge") && System.currentTimeMillis() - op.created < 30000L) {
            executeReforge(p, inv, op); pending.remove(p.getUniqueId()); resetConfirmButton(REFORGE_TITLE, inv); return;
        }
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        op = new PendingOp(); op.action = "reforge";
        int power = itemPower(item);
        op.repCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.reforge-cost", 650) + power * plugin.getForgeAdvancedConfig().getInt("forge2.cost.reforge-power-rep", 12);
        op.materialCost = Material.DIAMOND; op.materialAmount = 4 + power / 15;
        op.antiDefect = hasScroll(p, "anti_defect"); op.discountScroll = hasScroll(p, "discount");
        if (op.discountScroll) op.repCost = (int)Math.round(op.repCost * 0.75);
        op.summary = ChatColor.RED + "Перековка предмета\n" + ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" + ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x DIAMOND\n" + ChatColor.GRAY + "Может улучшить 1 чар и перебросить свойство.\n" + ChatColor.RED + "Риск дефекта" + (op.antiDefect ? " снят свитком" : " сохраняется") + "\n" + ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить перековку", op.summary.split("\n")));
    }

    private void executeReforge(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "перековка")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) { p.sendMessage(ChatColor.RED + "Не хватает алмазов."); return; }
        consumeRelevantScrolls(p, op);
        ItemStack result = item.clone();
        maybeUpgradeEnchant(result);
        applyRarityProc(result, plugin.getGearManager().getRarityKey(result));
        boolean defectAdded = false;
        if (!op.antiDefect && ThreadLocalRandom.current().nextInt(100) < plugin.getForgeAdvancedConfig().getInt("forge2.defects.chance-on-reforge", 22)) {
            plugin.getGearManager().applyRandomDefect(result);
            defectAdded = true;
        }
        inv.setItem(CENTER_SLOT, result);
        ru.example.vkchatgear.enhancements.GearEnhancements enhancements = plugin.getGearEnhancements();
        if (enhancements != null) {
            if (defectAdded) {
                enhancements.playReforgeFail(p);
            } else {
                enhancements.playReforgeSuccess(p);
                enhancements.playReforgeAnimation(p);
            }
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        }
        if (defectAdded) {
            p.sendMessage(ChatColor.YELLOW + "⚠ Перековка оставила дефект.");
        } else {
            p.sendMessage(ChatColor.GREEN + "🔥 Предмет перекован в реликтовой кузне.");
        }
        log(p, "REFORGE", itemName(result));
    }

    private void handleCleanseButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("cleanse") && System.currentTimeMillis() - op.created < 30000L) {
            executeCleanse(p, inv, op); pending.remove(p.getUniqueId()); resetConfirmButton(CLEANSE_TITLE, inv); return;
        }
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        op = new PendingOp(); op.action = "cleanse";
        int defects = countDefects(item);
        op.repCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.cleanse-cost", 350) + Math.max(0, defects - 1) * 250;
        op.materialCost = Material.LAPIS_LAZULI; op.materialAmount = 16 + Math.max(0, defects - 1) * 16;
        op.summary = ChatColor.AQUA + "Очищение дефектов\n" + ChatColor.YELLOW + "Дефектов найдено: " + defects + "\n" + ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" + ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x LAPIS_LAZULI\n" + ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить очищение", op.summary.split("\n")));
    }

    private void executeCleanse(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv); if (item == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "очищение")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) { p.sendMessage(ChatColor.RED + "Не хватает лазурита."); return; }
        boolean changed = plugin.getGearManager().cleanseDefects(item);
        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
        p.sendMessage(changed ? ChatColor.AQUA + "🕯 Дефекты очищены." : ChatColor.YELLOW + "Дефектов не было, но диагностика и обряд оплачены.");
        log(p, "CLEANSE", itemName(item) + " changed=" + changed);
    }

    private void handleRepairButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("repair") && System.currentTimeMillis() - op.created < 30000L) {
            executeRepair(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton(REPAIR_TITLE, inv);
            return;
        }
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable)) {
            p.sendMessage(ChatColor.YELLOW + "Этот предмет не имеет прочности и не нуждается в ремонте.");
            return;
        }
        org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        int max = item.getType().getMaxDurability();
        int damage = dmg.getDamage();
        if (max <= 0 || damage <= 0) {
            p.sendMessage(ChatColor.GREEN + "Предмет уже полностью целый.");
            return;
        }
        PendingOp preview = new PendingOp();
        preview.action = "repair";
        int percent = (int)Math.ceil((damage / (double)Math.max(1, max)) * 100.0);
        int power = itemPower(item);
        int base = plugin.getGearManager().getDiscountedCost(p, "forge2.repair.base-rep-cost", 120);
        int perPercent = plugin.getForgeAdvancedConfig().getInt("forge2.repair.rep-per-damage-percent", 8);
        int powerCost = plugin.getForgeAdvancedConfig().getInt("forge2.repair.power-rep-multiplier", 8);
        preview.repCost = Math.max(1, base + percent * perPercent + power * powerCost);
        if (plugin.getGearManager().hasDefect(item, "fragile")) preview.repCost = (int)Math.round(preview.repCost * plugin.getForgeAdvancedConfig().getDouble("forge2.repair.fragile-cost-multiplier", 1.35));
        preview.materialCost = repairMaterialFor(item.getType());
        preview.materialAmount = Math.max(1, plugin.getForgeAdvancedConfig().getInt("forge2.repair.base-material-amount", 2) + percent / plugin.getForgeAdvancedConfig().getInt("forge2.repair.percent-per-extra-material", 20));
        preview.discountScroll = hasScroll(p, "discount");
        if (preview.discountScroll) preview.repCost = (int)Math.round(preview.repCost * 0.75);
        preview.summary = ChatColor.GREEN + "Ремонт MMO-предмета\n" +
                ChatColor.GRAY + "Поломка: " + ChatColor.YELLOW + damage + "/" + max + " (" + percent + "%)\n" +
                ChatColor.YELLOW + "Цена: " + preview.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + preview.materialAmount + "x " + preview.materialCost.name() + "\n" +
                ChatColor.GRAY + "Результат: прочность будет восстановлена до 100%.\n" +
                (preview.discountScroll ? ChatColor.AQUA + "Свиток Скидки: -25% цены репутации\n" : "") +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), preview);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить ремонт", preview.summary.split("\n")));
        p.sendMessage(ChatColor.GREEN + "🔧 Предпросмотр ремонта готов. Проверь зелёную кнопку.");
    }

    private void executeRepair(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable)) return;
        org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        if (dmg.getDamage() <= 0) { p.sendMessage(ChatColor.GREEN + "Предмет уже полностью целый."); return; }
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "ремонт MMO-предмета")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса для ремонта: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        if (op.discountScroll) consumeScroll(p, "discount");
        dmg.setDamage(0);
        item.setItemMeta((ItemMeta) dmg);
        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.25f);
        p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.08);
        p.sendMessage(ChatColor.GREEN + "🔧 Предмет полностью отремонтирован за " + op.repCost + " реп. ВК и " + op.materialAmount + "x " + op.materialCost.name() + ".");
        log(p, "REPAIR", itemName(item) + " cost=" + op.repCost + " material=" + op.materialCost + "x" + op.materialAmount);
    }

    private void handleRuneCleansingButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("rune_cleansing") && System.currentTimeMillis() - op.created < 30000L) {
            executeRuneCleansing(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton(RUNE_CLEANSING_TITLE, inv);
            return;
        }
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        int enchantCount = plugin.getGearManager().countCustomRuneLines(item);
        if (enchantCount == 0) {
            p.sendMessage(ChatColor.YELLOW + "На этом предмете нет кастомных чар для удаления.");
            return;
        }
        op = new PendingOp();
        op.action = "rune_cleansing";
        op.repCost = plugin.getRuneMarketConfig().getInt("rune-cleansing.cost", 500);
        String matName = plugin.getRuneMarketConfig().getString("rune-cleansing.material", "DIAMOND_BLOCK");
        try { op.materialCost = Material.valueOf(matName); } catch (Exception e) { op.materialCost = Material.DIAMOND_BLOCK; }
        op.materialAmount = plugin.getRuneMarketConfig().getInt("rune-cleansing.material-amount", 1);

        List<String> enchantNames = new ArrayList<>();
        List<String> lore = item.getItemMeta().hasLore() ? item.getItemMeta().getLore() : new ArrayList<>();
        List<String> allCustom = plugin.getGearManager().getAvailableCustomEnchants(item.getType());
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            for (String key : allCustom) {
                String rawName = plugin.getEnchantsConfig().getString("custom_enchants." + key + ".name", "");
                String cfg = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName)).toLowerCase();
                if (!cfg.isEmpty() && stripped.contains(cfg.split(" ")[0].toLowerCase())) {
                    enchantNames.add(ChatColor.translateAlternateColorCodes('&', rawName));
                    break;
                }
            }
        }

        op.summary = ChatColor.DARK_PURPLE + "Очищение рун\n" +
                ChatColor.YELLOW + "Найдено чар: " + enchantCount + "\n" +
                ChatColor.GRAY + "Удаляемые чары:\n" +
                String.join("\n", enchantNames) + "\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.RED + "⚠ Все кастомные чары будут удалены!\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить очищение рун", op.summary.split("\n")));
        p.sendMessage(ChatColor.DARK_PURPLE + "💀 Предпросмотр очищения рун готов. Проверь зелёную кнопку.");
    }

    private void executeRuneCleansing(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterGear(p, inv);
        if (item == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "очищение рун")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        removeCustomEnchants(item);
        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 0.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        p.sendMessage(ChatColor.DARK_PURPLE + "💀 Все кастомные чары были удалены с предмета.");
        log(p, "RUNE_CLEANSING", itemName(item));
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ЗАТОЧКА
    // ═══════════════════════════════════════════

    private void handleArtifactSharpenButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_sharpen") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactSharpen(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Заточка Артефакта", inv);
            return;
        }
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) {
            p.sendMessage(ChatColor.RED + "Положи артефакт в центральный слот.");
            return;
        }
        int level = getArtifactLevel(item);
        if (level >= 5) {
            p.sendMessage(ChatColor.RED + "Артефакт уже максимального уровня заточки (5).");
            return;
        }

        op = new PendingOp();
        op.action = "artifact_sharpen";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.sharpen.rep-cost", 800);
        op.materialCost = Material.DIAMOND;
        op.materialAmount = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.sharpen.material-amount", 2);

        // Предпросмотр: покажем будущие статы
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        ru.example.vkchatgear.artifacts.ArtifactComponent def = mgr != null ? mgr.getArtifactDef(item) : null;
        String artName = item.getItemMeta().getDisplayName();
        double multiplier = (level + 1) * 0.10; // +10% за уровень

        StringBuilder statsPreview = new StringBuilder();
        if (def != null) {
            for (Map.Entry<String, Double> e : def.getStats().entrySet()) {
                double newVal = e.getValue() * (1 + multiplier);
                String statName = ru.example.vkchatgear.artifacts.ArtifactsManager.formatStatName(e.getKey());
                statsPreview.append(ChatColor.GRAY + "  ").append(statName).append(": ")
                        .append(ChatColor.GREEN + String.format("%.2f", e.getValue()))
                        .append(ChatColor.YELLOW + " → ").append(ChatColor.GREEN + String.format("%.2f", newVal))
                        .append("\n");
            }
        }

        op.summary = ChatColor.GREEN + "⬆ Заточка Артефакта\n" +
                ChatColor.AQUA + artName + "\n" +
                ChatColor.GRAY + "Уровень: " + ChatColor.YELLOW + level + " → " + (level + 1) + "\n" +
                ChatColor.GRAY + "Бонус: +" + (int)(multiplier * 100) + "% ко всем статам\n\n" +
                statsPreview.toString() +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить заточку", op.summary.split("\n")));
        p.sendMessage(ChatColor.GREEN + "⬆ Предпросмотр заточки готов.");
    }

    private void executeArtifactSharpen(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "заточка артефакта")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        int level = incrementArtifactLevel(item);
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr != null) mgr.rebuildArtifactLore(item);

        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.08);
        p.sendMessage(ChatColor.GREEN + "⬆ Артефакт заточен до уровня " + level + "! +" + (level * 10) + "% ко всем статам.");
        log(p, "ARTIFACT_SHARPEN", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(item) + " level=" + level);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ПЕРЕКОВКА
    // ═══════════════════════════════════════════

    private void handleArtifactReforgeButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_reforge") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactReforge(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Перековка Артефакта", inv);
            return;
        }
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) {
            p.sendMessage(ChatColor.RED + "Положи артефакт в центральный слот.");
            return;
        }

        op = new PendingOp();
        op.action = "artifact_reforge";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.reforge.rep-cost", 1200);
        op.materialCost = Material.DIAMOND;
        op.materialAmount = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.reforge.material-amount", 4);

        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        ru.example.vkchatgear.artifacts.ArtifactComponent def = mgr != null ? mgr.getArtifactDef(item) : null;
        String artName = item.getItemMeta().getDisplayName();

        // Покажем текущие статы
        StringBuilder statsCurrent = new StringBuilder();
        if (def != null) {
            for (Map.Entry<String, Double> e : def.getStats().entrySet()) {
                String statName = ru.example.vkchatgear.artifacts.ArtifactsManager.formatStatName(e.getKey());
                statsCurrent.append(ChatColor.GRAY + "  ").append(statName).append(": ")
                        .append(ChatColor.GREEN + String.format("%.2f", e.getValue())).append("\n");
            }
        }

        op.summary = ChatColor.RED + "🔄 Перековка Артефакта\n" +
                ChatColor.AQUA + artName + "\n\n" +
                ChatColor.GRAY + "Текущие статы:\n" +
                statsCurrent.toString() +
                ChatColor.GRAY + "⚠ Случайный стат будет перераспределён!\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить перековку", op.summary.split("\n")));
        p.sendMessage(ChatColor.RED + "🔄 Предпросмотр перековки готов.");
    }

    private void executeArtifactReforge(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "перековка артефакта")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr != null) mgr.reforgeArtifact(item);

        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.75f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
        p.sendMessage(ChatColor.RED + "🔄 Артефакт перекован! Статы были перераспределены.");
        log(p, "ARTIFACT_REFORGE", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(item));
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — СЛИЯНИЕ
    // ═══════════════════════════════════════════

    private void handleArtifactFusionButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_fusion") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactFusion(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Слияние Артефактов", inv);
            return;
        }
        // Собираем артефакты из слотов 20, 22, 24
        List<ItemStack> artifacts = new ArrayList<>();
        for (int slot : new int[]{20, 22, 24}) {
            ItemStack it = inv.getItem(slot);
            if (it != null && ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(it)) {
                artifacts.add(it);
            }
        }
        if (artifacts.size() < 3) {
            p.sendMessage(ChatColor.RED + "Нужно 3 артефакта одной редкости для слияния!");
            return;
        }
        // Проверяем редкость
        String rarity = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(artifacts.get(0));
        for (ItemStack art : artifacts) {
            String r = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(art);
            if (r == null || !r.equals(rarity)) {
                p.sendMessage(ChatColor.RED + "Все артефакты должны быть одной редкости!");
                return;
            }
        }
        if (rarity == null || rarity.equals("ancient")) {
            p.sendMessage(ChatColor.RED + "Невозможно слиять эти артефакты.");
            return;
        }

        String nextRarity = nextArtifactRarity(rarity);
        if (nextRarity == null) {
            p.sendMessage(ChatColor.RED + "Артефакты максимальной редкости, слияние невозможно.");
            return;
        }

        op = new PendingOp();
        op.action = "artifact_fusion";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.fusion.rep-cost", 2000);
        op.materialCost = Material.NETHERITE_SCRAP;
        op.materialAmount = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.fusion.material-amount", 3);

        op.summary = ChatColor.GOLD + "⭐ Слияние Артефактов\n" +
                ChatColor.GRAY + "Ингредиенты:\n" +
                "  " + artifacts.get(0).getItemMeta().getDisplayName() + "\n" +
                "  " + artifacts.get(1).getItemMeta().getDisplayName() + "\n" +
                "  " + artifacts.get(2).getItemMeta().getDisplayName() + "\n" +
                ChatColor.GRAY + "Редкость: " + rarityColorName(rarity) + " → " + rarityColorName(nextRarity) + "\n" +
                ChatColor.RED + "⚠ Все 3 артефакта сгорят!\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.YELLOW + "Ресурс: " + op.materialAmount + "x " + op.materialCost.name() + "\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить слияние", op.summary.split("\n")));
        p.sendMessage(ChatColor.GOLD + "⭐ Предпросмотр слияния готов.");
    }

    private void executeArtifactFusion(Player p, Inventory inv, PendingOp op) {
        List<ItemStack> artifacts = new ArrayList<>();
        for (int slot : new int[]{20, 22, 24}) {
            ItemStack it = inv.getItem(slot);
            if (it != null && ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(it)) {
                artifacts.add(it);
            }
        }
        if (artifacts.size() < 3) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "слияние артефактов")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }
        // Удаляем катализаторы
        for (ItemStack art : artifacts) {
            for (int slot : new int[]{20, 22, 24}) {
                ItemStack it = inv.getItem(slot);
                if (it != null && it.equals(art)) {
                    inv.setItem(slot, null);
                    break;
                }
            }
        }
        // Создаём новый артефакт следующей редкости
        String oldRarity = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(artifacts.get(0));
        String newRarity = nextArtifactRarity(oldRarity);
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        if (mgr != null) {
            ItemStack result = mgr.createMergedArtifact(newRarity);
            if (result != null) {
                inv.setItem(22, result);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, p.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.01);
                p.sendMessage(ChatColor.GOLD + "⭐ Слияние успешно! Получен артефакт: " + result.getItemMeta().getDisplayName());
                log(p, "ARTIFACT_FUSION", oldRarity + " -> " + newRarity);
            }
        }
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ДЕЗИНТЕГРАЦИЯ
    // ═══════════════════════════════════════════

    private void handleArtifactDisintegrateButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_disintegrate") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactDisintegrate(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Дезинтеграция", inv);
            return;
        }
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) {
            p.sendMessage(ChatColor.RED + "Положи артефакт в центральный слот.");
            return;
        }
        String rarity = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(item);
        String artName = item.getItemMeta().getDisplayName();

        op = new PendingOp();
        op.action = "artifact_disintegrate";
        op.repCost = 0;

        // Рассчитываем награду по редкости
        int runeTokens = 0;
        int crystals = 0;
        int fragments = 0;
        switch (rarity != null ? rarity : "common") {
            case "ancient": runeTokens = 0; crystals = 5; fragments = 3; break;
            case "legendary": runeTokens = 0; crystals = 3; fragments = 2; break;
            case "epic": runeTokens = 0; crystals = 2; fragments = 1; break;
            case "rare": runeTokens = 3; crystals = 1; fragments = 0; break;
            default: runeTokens = 2; crystals = 0; fragments = 0; break;
        }

        StringBuilder rewards = new StringBuilder();
        rewards.append(ChatColor.AQUA + artName + "\n");
        rewards.append(ChatColor.GRAY + "Редкость: " + rarityColorName(rarity) + "\n\n");
        rewards.append(ChatColor.YELLOW + "Награда:\n");
        if (runeTokens > 0) rewards.append(ChatColor.GREEN + "  " + runeTokens + "x Рунный жетон\n");
        if (crystals > 0) rewards.append(ChatColor.GREEN + "  " + crystals + "x Кристалл\n");
        if (fragments > 0) rewards.append(ChatColor.GREEN + "  " + fragments + "x Фрагмент сета\n");
        rewards.append(ChatColor.GREEN + "  Бесплатно!\n\n");
        rewards.append(ChatColor.GRAY + "Кликни ещё раз для подтверждения.");

        op.summary = rewards.toString();
        op.materialAmount = runeTokens + crystals + fragments; // сохраняем для execute
        op.chance = crystals; // переиспользуем поле для кристаллов
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить разбор", op.summary.split("\n")));
        p.sendMessage(ChatColor.DARK_RED + "💔 Предпросмотр разбора готов.");
    }

    private void executeArtifactDisintegrate(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) return;

        String rarity = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(item);
        int runeTokens = 0, crystals = 0, fragments = 0;
        switch (rarity != null ? rarity : "common") {
            case "ancient": crystals = 5; fragments = 3; break;
            case "legendary": crystals = 3; fragments = 2; break;
            case "epic": crystals = 2; fragments = 1; break;
            case "rare": runeTokens = 3; crystals = 1; break;
            default: runeTokens = 2; break;
        }

        // Выдаём награду
        if (runeTokens > 0) giveMaterial(p, Material.NETHER_WART, runeTokens);
        if (crystals > 0) giveMaterial(p, Material.PRISMARINE_SHARD, crystals);
        if (fragments > 0) giveMaterial(p, Material.IRON_NUGGET, fragments);

        inv.setItem(CENTER_SLOT, null);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1f, 0.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
        p.sendMessage(ChatColor.DARK_RED + "💔 Артефакт разобран! Получено: " +
                (runeTokens > 0 ? runeTokens + "x рунных жетонов, " : "") +
                (crystals > 0 ? crystals + "x кристаллов, " : "") +
                (fragments > 0 ? fragments + "x фрагментов" : ""));
        log(p, "ARTIFACT_DISINTEGRATE", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(item) + " rarity=" + rarity);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ЭССЕНЦИЯ
    // ═══════════════════════════════════════════

    private void handleArtifactEssenceButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_essence") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactEssence(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Эссенция", inv);
            return;
        }
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) {
            p.sendMessage(ChatColor.RED + "Положи артефакт в центральный слот.");
            return;
        }
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        ru.example.vkchatgear.artifacts.ArtifactComponent def = mgr != null ? mgr.getArtifactDef(item) : null;
        String artName = item.getItemMeta().getDisplayName();

        op = new PendingOp();
        op.action = "artifact_essence";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.essence.rep-cost", 600);

        StringBuilder stats = new StringBuilder();
        if (def != null) {
            for (Map.Entry<String, Double> e : def.getStats().entrySet()) {
                String statName = ru.example.vkchatgear.artifacts.ArtifactsManager.formatStatName(e.getKey());
                stats.append(ChatColor.GRAY + "  " + statName + ": " + ChatColor.GREEN + String.format("%.2f", e.getValue()) + "\n");
            }
        }

        op.summary = ChatColor.AQUA + "💧 Эссенция: " + artName + "\n\n" +
                ChatColor.GRAY + "Статы артефакта:\n" + stats.toString() + "\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.GRAY + "Артефакт будет уничтожен.\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить извлечение", op.summary.split("\n")));
        p.sendMessage(ChatColor.AQUA + "💧 Предпросмотр извлечения готов.");
    }

    private void executeArtifactEssence(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "эссенция артефакта")) return;

        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        ru.example.vkchatgear.artifacts.ArtifactComponent def = mgr != null ? mgr.getArtifactDef(item) : null;
        String rarity = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(item);

        // Создаём эссенцию
        ItemStack essence = new ItemStack(Material.POTION);
        ItemMeta meta = essence.getItemMeta();
        ChatColor color = rarity != null ? ru.example.vkchatgear.artifacts.ArtifactsManager.rarityColor(rarity) : ChatColor.WHITE;
        meta.setDisplayName(color + "💧 Эссенция: " + ChatColor.stripColor(item.getItemMeta().getDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Эссенция артефакта.");
        if (def != null) {
            for (Map.Entry<String, Double> e : def.getStats().entrySet()) {
                String statName = ru.example.vkchatgear.artifacts.ArtifactsManager.formatStatName(e.getKey());
                lore.add(ChatColor.GREEN + "+ " + statName + ": " + String.format("%.2f", e.getValue()));
            }
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Используется в операциях кузни.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "artifact_essence"), PersistentDataType.STRING, rarity != null ? rarity : "common");
        essence.setItemMeta(meta);

        inv.setItem(CENTER_SLOT, null);
        p.getInventory().addItem(essence).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1.2f);
        p.getWorld().spawnParticle(org.bukkit.Particle.WATER_SPLASH, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        p.sendMessage(ChatColor.AQUA + "💧 Эссенция извлечена! Артефакт уничтожен.");
        log(p, "ARTIFACT_ESSENCE", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(item));
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — БИНД
    // ═══════════════════════════════════════════

    private void handleArtifactBindButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_bind") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactBind(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Бинд Артефакта", inv);
            return;
        }
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) {
            p.sendMessage(ChatColor.RED + "Положи артефакт в центральный слот.");
            return;
        }
        NamespacedKey bindKey = new NamespacedKey(plugin, "artifact_bound");
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(bindKey, PersistentDataType.STRING)) {
            p.sendMessage(ChatColor.RED + "Этот артефакт уже привязан!");
            return;
        }

        op = new PendingOp();
        op.action = "artifact_bind";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.bind.rep-cost", 400);
        String artName = item.getItemMeta().getDisplayName();

        op.summary = ChatColor.LIGHT_PURPLE + "🔒 Бинд: " + artName + "\n\n" +
                ChatColor.GRAY + "Привязать к: " + ChatColor.YELLOW + p.getName() + "\n\n" +
                ChatColor.RED + "После бинда:\n" +
                ChatColor.RED + "  • Нельзя выбросить\n" +
                ChatColor.RED + "  • Нельзя передать\n" +
                ChatColor.RED + "  • Нельзя положить в сундук\n" +
                ChatColor.RED + "  • Нельзя положить в эндер-чест\n\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить бинд", op.summary.split("\n")));
        p.sendMessage(ChatColor.LIGHT_PURPLE + "🔒 Предпросмотр бинда готов.");
    }

    private void executeArtifactBind(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "бинд артефакта")) return;

        NamespacedKey bindKey = new NamespacedKey(plugin, "artifact_bound");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(bindKey, PersistentDataType.STRING, p.getUniqueId().toString());
        item.setItemMeta(meta);

        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "🔒 Артефакт привязан к " + p.getName() + "!");
        log(p, "ARTIFACT_BIND", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(item));
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — ТРАНСМУТАЦИЯ
    // ═══════════════════════════════════════════

    private void handleArtifactTransmuteButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_transmute") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactTransmute(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Трансмутация", inv);
            return;
        }
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) {
            p.sendMessage(ChatColor.RED + "Положи артефакт в центральный слот.");
            return;
        }
        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        ru.example.vkchatgear.artifacts.ArtifactComponent def = mgr != null ? mgr.getArtifactDef(item) : null;
        if (def == null || def.getStats().isEmpty()) {
            p.sendMessage(ChatColor.RED + "У этого артефакта нет статов для трансмутации!");
            return;
        }
        // Ищем первое оружие/броню в инвентаре
        ItemStack gear = null;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && plugin.getGearManager().isGear(it.getType())) {
                gear = it;
                break;
            }
        }
        if (gear == null) {
            p.sendMessage(ChatColor.RED + "Нет оружия/брони в инвентаре для трансмутации!");
            return;
        }

        // Выбираем первый стат для переноса
        Map.Entry<String, Double> firstStat = def.getStats().entrySet().iterator().next();
        String statName = ru.example.vkchatgear.artifacts.ArtifactsManager.formatStatName(firstStat.getKey());

        op = new PendingOp();
        op.action = "artifact_transmute";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.transmute.rep-cost", 1500);
        op.materialCost = Material.DIAMOND;
        op.materialAmount = 1;

        op.summary = ChatColor.YELLOW + "⚗ Трансмутация\n\n" +
                ChatColor.AQUA + "Из: " + item.getItemMeta().getDisplayName() + "\n" +
                ChatColor.GRAY + "  Стат: " + statName + ": " + String.format("%.2f", firstStat.getValue()) + "\n\n" +
                ChatColor.GREEN + "В: " + gear.getItemMeta().getDisplayName() + "\n" +
                ChatColor.GRAY + "  Бонус: +" + statName + "\n\n" +
                ChatColor.RED + "⚠ Стат удалится из артефакта!\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК + 1x DIAMOND\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить трансмутацию", op.summary.split("\n")));
        p.sendMessage(ChatColor.YELLOW + "⚗ Предпросмотр трансмутации готов.");
    }

    private void executeArtifactTransmute(Player p, Inventory inv, PendingOp op) {
        ItemStack item = getCenterItem(p, inv);
        if (item == null || !ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(item)) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "трансмутация артефакта")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }

        ru.example.vkchatgear.artifacts.ArtifactsManager mgr = plugin.getArtifactsManager();
        ru.example.vkchatgear.artifacts.ArtifactComponent def = mgr != null ? mgr.getArtifactDef(item) : null;
        if (def == null || def.getStats().isEmpty()) return;

        Map.Entry<String, Double> firstStat = def.getStats().entrySet().iterator().next();
        String statKey = firstStat.getKey();
        double statVal = firstStat.getValue();

        // Находим оружие/броню
        ItemStack gear = null;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && plugin.getGearManager().isGear(it.getType())) {
                gear = it;
                break;
            }
        }
        if (gear == null) return;

        // Добавляем стат к снаряжению через PDC
        ItemMeta gearMeta = gear.getItemMeta();
        NamespacedKey transmuteKey = new NamespacedKey(plugin, "transmuted_" + statKey);
        double current = gearMeta.getPersistentDataContainer().getOrDefault(transmuteKey, PersistentDataType.DOUBLE, 0.0);
        gearMeta.getPersistentDataContainer().set(transmuteKey, PersistentDataType.DOUBLE, current + statVal);
        gear.setItemMeta(gearMeta);

        // Удаляем стат из артефакта (пересобираем lore без этого стата)
        // Просто помечаем стат как удалённый в PDC
        NamespacedKey removedStatsKey = new NamespacedKey(plugin, "artifact_removed_stats");
        ItemMeta artMeta = item.getItemMeta();
        String removed = artMeta.getPersistentDataContainer().getOrDefault(removedStatsKey, PersistentDataType.STRING, "");
        removed += (removed.isEmpty() ? "" : ",") + statKey;
        artMeta.getPersistentDataContainer().set(removedStatsKey, PersistentDataType.STRING, removed);
        item.setItemMeta(artMeta);
        if (mgr != null) mgr.rebuildArtifactLore(item);

        inv.setItem(CENTER_SLOT, item);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        p.sendMessage(ChatColor.YELLOW + "⚗ Стат " + ru.example.vkchatgear.artifacts.ArtifactsManager.formatStatName(statKey) + " перенесён на " + gear.getItemMeta().getDisplayName() + "!");
        log(p, "ARTIFACT_TRANSMUTE", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(item) + " stat=" + statKey);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — РИТУАЛ
    // ═══════════════════════════════════════════

    private void handleArtifactRitualButton(Player p, Inventory inv) {
        PendingOp op = pending.get(p.getUniqueId());
        if (op != null && op.action.equals("artifact_ritual") && System.currentTimeMillis() - op.created < 30000L) {
            executeArtifactRitual(p, inv, op);
            pending.remove(p.getUniqueId());
            resetConfirmButton("Артефакт-ритуал", inv);
            return;
        }
        // Собираем артефакты из слотов 20 и 24, руну из слота 22
        List<ItemStack> artifacts = new ArrayList<>();
        for (int slot : new int[]{20, 24}) {
            ItemStack it = inv.getItem(slot);
            if (it != null && ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(it)) {
                artifacts.add(it);
            }
        }
        ItemStack runeItem = inv.getItem(22);

        if (artifacts.size() < 2) {
            p.sendMessage(ChatColor.RED + "Положи 2 артефакта одной редкости в слоты 20 и 24!");
            return;
        }
        if (runeItem == null || !isRuneItem(runeItem)) {
            p.sendMessage(ChatColor.RED + "Положи руну в центральный слот (22)!");
            return;
        }
        String rarity1 = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(artifacts.get(0));
        String rarity2 = ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactRarity(artifacts.get(1));
        if (rarity1 == null || !rarity1.equals(rarity2)) {
            p.sendMessage(ChatColor.RED + "Артефакты должны быть одной редкости!");
            return;
        }

        op = new PendingOp();
        op.action = "artifact_ritual";
        op.repCost = plugin.getForgeAdvancedConfig().getInt("forge2.artifacts.ritual.rep-cost", 2500);
        op.materialCost = Material.DIAMOND;
        op.materialAmount = 2;

        op.summary = ChatColor.DARK_AQUA + "🕯 Артефакт-ритуал\n\n" +
                ChatColor.GRAY + "Ингредиенты:\n" +
                "  " + artifacts.get(0).getItemMeta().getDisplayName() + "\n" +
                "  " + artifacts.get(1).getItemMeta().getDisplayName() + "\n" +
                "  " + runeItem.getItemMeta().getDisplayName() + "\n\n" +
                ChatColor.GRAY + "Результат: артефакт с зачарованной руной.\n" +
                ChatColor.RED + "Руна сгорит!\n" +
                ChatColor.YELLOW + "Цена: " + op.repCost + " реп. ВК + 2x DIAMOND\n" +
                ChatColor.GRAY + "Кликни ещё раз для подтверждения.";
        pending.put(p.getUniqueId(), op);
        inv.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "✅ Подтвердить ритуал", op.summary.split("\n")));
        p.sendMessage(ChatColor.DARK_AQUA + "🕯 Предпросмотр ритуала готов.");
    }

    private void executeArtifactRitual(Player p, Inventory inv, PendingOp op) {
        List<ItemStack> artifacts = new ArrayList<>();
        for (int slot : new int[]{20, 24}) {
            ItemStack it = inv.getItem(slot);
            if (it != null && ru.example.vkchatgear.artifacts.ArtifactsManager.isArtifact(it)) {
                artifacts.add(it);
            }
        }
        ItemStack runeItem = inv.getItem(22);
        if (artifacts.size() < 2 || runeItem == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, op.repCost, "артефакт-ритуал")) return;
        if (!takeMaterial(p, op.materialCost, op.materialAmount)) {
            p.sendMessage(ChatColor.RED + "Не хватает ресурса: " + op.materialAmount + "x " + op.materialCost.name());
            return;
        }

        // Берём первый артефакт как основу
        ItemStack base = artifacts.get(0).clone();
        String runeName = runeItem.getItemMeta().getDisplayName();

        // Добавляем имя руны в lore артефакта
        ItemMeta baseMeta = base.getItemMeta();
        List<String> lore = baseMeta.hasLore() ? new ArrayList<>(baseMeta.getLore()) : new ArrayList<>();
        // Удаляем пустые строки в конце
        while (!lore.isEmpty() && ChatColor.stripColor(lore.get(lore.size() - 1)).isEmpty()) lore.remove(lore.size() - 1);
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "✦ " + runeName);
        baseMeta.setLore(lore);
        base.setItemMeta(baseMeta);

        // Удаляем все предметы из слотов
        for (int slot : new int[]{20, 22, 24}) inv.setItem(slot, null);
        inv.setItem(22, base);

        p.playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1.5f);
        p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        p.sendMessage(ChatColor.DARK_AQUA + "🕯 Ритуал завершён! Артефакт зачарован руной: " + runeName);
        log(p, "ARTIFACT_RITUAL", ru.example.vkchatgear.artifacts.ArtifactsManager.getArtifactId(base) + " rune=" + runeName);
    }

    private boolean isRuneItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "rune_id"), PersistentDataType.STRING);
    }

    // ═══════════════════════════════════════════
    // АРТЕФАКТЫ — УТИЛИТЫ
    // ═══════════════════════════════════════════

    private int getArtifactLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        NamespacedKey key = new NamespacedKey(plugin, "artifact_level");
        Integer lvl = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return lvl != null ? lvl : 0;
    }

    private int incrementArtifactLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        NamespacedKey key = new NamespacedKey(plugin, "artifact_level");
        ItemMeta meta = item.getItemMeta();
        int lvl = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0) + 1;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, lvl);
        item.setItemMeta(meta);
        return lvl;
    }

    private String nextArtifactRarity(String current) {
        switch (current) {
            case "common": return "rare";
            case "rare": return "epic";
            case "epic": return "legendary";
            case "legendary": return "ancient";
            default: return null;
        }
    }

    private String rarityColorName(String rarity) {
        switch (rarity) {
            case "ancient": return ChatColor.DARK_PURPLE + "Древний";
            case "legendary": return ChatColor.GOLD + "Легендарный";
            case "epic": return ChatColor.BLUE + "Эпический";
            case "rare": return ChatColor.AQUA + "Редкий";
            default: return ChatColor.WHITE + "Обычный";
        }
    }

    private void removeCustomEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        // Remove PDC tags for all custom enchants
        if (plugin.getEnchantsConfig().getConfigurationSection("custom_enchants") != null) {
            for (String key : plugin.getEnchantsConfig().getConfigurationSection("custom_enchants").getKeys(false)) {
                NamespacedKey nk = new NamespacedKey(plugin, "custom_enchant_" + key);
                if (meta.getPersistentDataContainer().has(nk, PersistentDataType.INTEGER)) {
                    meta.getPersistentDataContainer().remove(nk);
                }
            }
        }

        // Remove lore lines for custom enchants
        if (!meta.hasLore()) { item.setItemMeta(meta); return; }
        List<String> lore = meta.getLore();
        List<String> allCustom = plugin.getGearManager().getAvailableCustomEnchants(item.getType());
        List<String> toRemove = new ArrayList<>();
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            for (String key : allCustom) {
                String rawName = plugin.getEnchantsConfig().getString("custom_enchants." + key + ".name", "");
                String cfg = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName)).toLowerCase();
                if (!cfg.isEmpty() && stripped.contains(cfg.split(" ")[0].toLowerCase())) {
                    toRemove.add(line);
                    break;
                }
            }
        }
        lore.removeAll(toRemove);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private Material repairMaterialFor(Material type) {
        String n = type.name();
        if (n.contains("NETHERITE")) return Material.NETHERITE_SCRAP;
        if (n.contains("DIAMOND")) return Material.DIAMOND;
        if (n.contains("GOLD") || n.contains("GOLDEN")) return Material.GOLD_INGOT;
        if (n.contains("IRON") || n.contains("CHAINMAIL")) return Material.IRON_INGOT;
        if (n.contains("LEATHER")) return Material.LEATHER;
        if (n.contains("STONE")) return Material.COBBLESTONE;
        if (n.contains("WOOD") || n.contains("BOW") || n.contains("CROSSBOW")) return Material.OAK_PLANKS;
        return Material.EMERALD;
    }

    private void buyScroll(Player p, ItemStack clicked) {
        if (!clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String type = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "forge_scroll_shop_type"), PersistentDataType.STRING);
        Integer price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "forge_scroll_shop_price"), PersistentDataType.INTEGER);
        if (type == null || price == null) return;
        if (!plugin.getGearManager().takeVkReputation(p, price, "покупка свитка кузни")) return;
        ItemStack scroll = createScroll(type);
        p.getInventory().addItem(scroll).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
        p.sendMessage(ChatColor.GREEN + "Куплен свиток: " + scroll.getItemMeta().getDisplayName() + ChatColor.GREEN + " за " + price + " реп.");
        log(p, "BUY_SCROLL", type + " price=" + price);
    }

    private ItemStack createScroll(String type) {
        Material mat = type.equals("perfect") ? Material.NETHER_STAR : type.equals("protect_all") ? Material.TOTEM_OF_UNDYING : Material.PAPER;
        String name;
        String lore;
        switch (type) {
            case "chance_25": name = "§dСвиток Точного Слияния"; lore = "§7+25% к следующему слиянию редкости."; break;
            case "chance_50": name = "§5Свиток Сильного Слияния"; lore = "§7+50% к следующему слиянию редкости."; break;
            case "perfect": name = "§6§lСвиток Идеального Слияния"; lore = "§7Следующее слияние редкости будет §a100%§7 успешным."; break;
            case "protect_all": name = "§bСвиток Полной Защиты"; lore = "§7При провале слияния сохраняет цель и катализаторы."; break;
            case "anti_defect": name = "§aСвиток Чистой Стали"; lore = "§7Защищает от дефекта при следующей перековке."; break;
            case "discount": name = "§2Свиток Скидки"; lore = "§7-25% репутационной цены следующей операции кузни."; break;
            default: name = "§7Свиток кузни"; lore = "§7Свиток.";
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore, "§8Расходуется автоматически в /forge."));
        switch (type) {
            case "chance_25": meta.setCustomModelData(55); break;
            case "chance_50": meta.setCustomModelData(56); break;
            case "perfect": meta.setCustomModelData(30); break;
            case "protect_all": meta.setCustomModelData(62); break;
            case "anti_defect": meta.setCustomModelData(57); break;
            case "discount": meta.setCustomModelData(58); break;
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "forge_scroll_type"), PersistentDataType.STRING, type);
        if (type.equals("perfect")) meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_fusion_scroll"), PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean hasScroll(Player p, String type) { return findScrollSlot(p, type) != -1; }
    private int findScrollSlot(Player p, String type) {
        ItemStack[] items = p.getInventory().getContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || !it.hasItemMeta()) continue;
            ItemMeta meta = it.getItemMeta();
            String t = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "forge_scroll_type"), PersistentDataType.STRING);
            if (type.equals(t)) return i;
            if (type.equals("perfect") && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_fusion_scroll"), PersistentDataType.INTEGER)) return i;
        }
        return -1;
    }

    private void consumeRelevantScrolls(Player p, PendingOp op) {
        if (op.discountScroll) consumeScroll(p, "discount");
        if (op.action.equals("fusion")) {
            if (op.guaranteed) consumeScroll(p, "perfect");
            else if (hasScroll(p, "chance_50")) consumeScroll(p, "chance_50");
            else if (hasScroll(p, "chance_25")) consumeScroll(p, "chance_25");
            if (op.protection) consumeScroll(p, "protect_all");
        }
        if (op.action.equals("reforge") && op.antiDefect) consumeScroll(p, "anti_defect");
    }

    private boolean consumeScroll(Player p, String type) {
        int slot = findScrollSlot(p, type);
        if (slot == -1) return false;
        ItemStack it = p.getInventory().getItem(slot);
        if (it.getAmount() <= 1) p.getInventory().setItem(slot, null); else it.setAmount(it.getAmount() - 1);
        return true;
    }

    private ItemStack getCenterGear(Player p, Inventory inv) {
        ItemStack item = inv.getItem(CENTER_SLOT);
        if (isEmpty(item)) { p.sendMessage(ChatColor.RED + "Положи предмет в центральный слот."); return null; }
        if (!plugin.getGearManager().isGear(item.getType())) { p.sendMessage(ChatColor.RED + "Это не оружие/броня/инструмент."); return null; }
        if (!isValidFusionItem(item)) p.sendMessage(ChatColor.YELLOW + "⚠ Legacy-предмет будет помечен как мигрированный после операции.");
        return item;
    }

    private ItemStack getCenterItem(Player p, Inventory inv) {
        ItemStack item = inv.getItem(CENTER_SLOT);
        if (isEmpty(item)) { p.sendMessage(ChatColor.RED + "Положи предмет в центральный слот."); return null; }
        return item;
    }

    private boolean isValidFusionItem(ItemStack item) {
        if (isEmpty(item) || !plugin.getGearManager().isGear(item.getType()) || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER)) return true;
        // Legacy-разрешение: старые предметы с лором редкости принимаются, но после операции помечаются.
        if (meta.hasLore()) {
            for (String line : meta.getLore()) if (ChatColor.stripColor(line).startsWith("Редкость:")) return true;
        }
        return false;
    }

    private void markLegacyIfNeeded(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_legacy_migrated"), PersistentDataType.INTEGER, 1);
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "Заточка: +0");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private int itemPower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        int power = meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
        power += item.getEnchantments().size() * 2;
        power += plugin.getGearManager().countCustomRuneLines(item) * 3;
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING)) power += 10;
        String n = item.getType().name();
        if (n.contains("NETHERITE")) power += 12; else if (n.contains("DIAMOND")) power += 8; else if (n.contains("IRON")) power += 4;
        return power;
    }

    private boolean takeMaterial(Player p, Material mat, int amount) {
        if (amount <= 0) return true;
        if (!p.getInventory().containsAtLeast(new ItemStack(mat), amount)) return false;
        p.getInventory().removeItem(new ItemStack(mat, amount));
        return true;
    }

    private void giveMaterial(Player p, Material mat, int amount) {
        if (amount <= 0) return;
        ItemStack drop = new ItemStack(mat, amount);
        p.getInventory().addItem(drop).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    private Material materialCostFor(String targetRarity) {
        String raw = plugin.getForgeAdvancedConfig().getString("forge2.resources." + targetRarity + ".material", null);
        if (raw != null) try { return Material.valueOf(raw); } catch (Exception ignored) {}
        if (targetRarity.equals("ancient")) return Material.NETHERITE_INGOT;
        if (targetRarity.equals("legendary")) return Material.NETHERITE_SCRAP;
        if (targetRarity.equals("epic")) return Material.DIAMOND;
        if (targetRarity.equals("rare")) return Material.GOLD_INGOT;
        return Material.IRON_INGOT;
    }

    private int materialAmountFor(String targetRarity) {
        int cfg = plugin.getForgeAdvancedConfig().getInt("forge2.resources." + targetRarity + ".amount", -1);
        if (cfg > 0) return cfg;
        if (targetRarity.equals("ancient")) return 2;
        if (targetRarity.equals("legendary")) return 4;
        if (targetRarity.equals("epic")) return 8;
        if (targetRarity.equals("rare")) return 16;
        return 16;
    }

    private void maybeUpgradeEnchant(ItemStack item) {
        if (item == null || item.getEnchantments().isEmpty()) return;
        List<Enchantment> list = new ArrayList<>(item.getEnchantments().keySet());
        Collections.shuffle(list);
        for (Enchantment e : list) {
            int lvl = item.getEnchantmentLevel(e);
            if (lvl < e.getMaxLevel() && e.canEnchantItem(item)) {
                item.addUnsafeEnchantment(e, lvl + 1);
                return;
            }
        }
    }

    private void applyRarityProc(ItemStack item, String rarity) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<String> pool = plugin.getForgeAdvancedConfig().getStringList("forge2.rarity-procs." + rarity);
        if (pool.isEmpty()) pool = defaultProcPool(rarity);
        if (pool.isEmpty()) return;
        String proc = rebrandProc(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.removeIf(l -> ChatColor.stripColor(l).startsWith("Прок редкости:"));
        lore.add(ChatColor.DARK_PURPLE + "Прок редкости: " + ChatColor.translateAlternateColorCodes('&', proc));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rarity_proc"), PersistentDataType.STRING, ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', proc)));
        item.setItemMeta(meta);
    }

    private String rebrandProc(String proc) {
        if (proc == null) return "";
        return proc.replace("Воля Перуна", "Грозовой Импульс")
                .replace("Кровь Рода", "Багровый Резонанс")
                .replace("Щит Сварога", "Астральный Барьер")
                .replace("Пламя Ярило", "Пламенный Контур")
                .replace("Очищение", "Развеивание")
                .replace("Вампиризм", "Похищение Жизни");
    }

    private List<String> defaultProcPool(String rarity) {
        if (rarity.equals("ancient")) return Arrays.asList("&5Древнее Проклятие", "&5Бездна Хаоса", "&5Вечная Тьма", "&5Космический Удар");
        if (rarity.equals("legendary")) return Arrays.asList("&6Грозовой Импульс", "&6Багровый Резонанс", "&6Астральный Барьер", "&6Пламенный Контур");
        if (rarity.equals("epic")) return Arrays.asList("&5Критический жар", "&5Оберег", "&5Развеивание", "&5Похищение Жизни");
        if (rarity.equals("rare")) return Arrays.asList("&9Искра удачи", "&9Стальная кожа", "&9Резкий удар");
        if (rarity.equals("uncommon")) return Arrays.asList("&aМалая искра", "&aЛёгкая сталь");
        return Collections.emptyList();
    }

    private int countDefects(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        int count = 0;
        for (String key : defectKeys()) if (plugin.getGearManager().hasDefect(item, key)) count++;
        if (count == 0 && item.getItemMeta().hasLore()) for (String l : item.getItemMeta().getLore()) if (ChatColor.stripColor(l).startsWith("Дефект:")) count++;
        return count;
    }

    private List<String> defectKeys() {
        if (plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list") == null) return Arrays.asList("fragile", "heavy", "dull");
        return new ArrayList<>(plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list").getKeys(false));
    }

    private void announceIfNeeded(Player p, ItemStack result, String rarity) {
        if (!(rarity.equals("epic") || rarity.equals("legendary") || rarity.equals("ancient"))) return;
        String msg = "⚒ Реликтовый горн вспыхнул! " + p.getName() + " возвысил предмет до " + ChatColor.stripColor(rarityDisplay(rarity)) + ": " + ChatColor.stripColor(itemName(result));
        if (rarity.equals("ancient")) Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + msg);
        else if (rarity.equals("legendary")) Bukkit.broadcastMessage(ChatColor.GOLD + msg);
        else Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + msg);
        try { VKChatBridge.sendToMainChat(msg); } catch (Exception ignored) {}
    }

    /**
     * FIX #3: Делегирует логирование в ForgeLogger.
     * Убирает прямой FileWriter из ForgeCommand (SRP).
     */
    private void log(Player p, String action, String details) {
        ru.example.vkchatgear.forge.ForgeLogger logger = plugin.getForgeLogger();
        if (logger != null) {
            logger.log(p.getName(), action, details);
        }
    }

    private void resetConfirmButton(String title, Inventory inv) {
        if (title.equals(FUSION_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр слияния", ChatColor.GRAY + "Покажет шанс, цену, ресурсы и свитки."));
        else if (title.equals(REFORGE_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр перековки"));
        else if (title.equals(CLEANSE_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения"));
        else if (title.equals(REPAIR_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр ремонта"));
        else if (title.equals(RUNE_CLEANSING_TITLE)) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр очищения рун"));
        else if (title.contains("Заточка Артефакта")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр заточки"));
        else if (title.contains("Перековка Артефакта")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр перековки"));
        else if (title.contains("Слияние Артефактов")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр слияния"));
        else if (title.contains("Дезинтеграция")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр разбора"));
        else if (title.contains("Эссенция")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр извлечения"));
        else if (title.contains("Бинд Артефакта")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр бинда"));
        else if (title.contains("Трансмутация")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр трансмутации"));
        else if (title.contains("Артефакт-ритуал")) inv.setItem(CONFIRM_SLOT, item(Material.OAK_SIGN, ChatColor.YELLOW + "🔍 Предпросмотр ритуала"));
    }

    private boolean isForgeTitle(String title) {
        return title.equals(HUB_TITLE) || title.equals(FUSION_TITLE) || title.equals(REFORGE_TITLE) ||
                title.equals(CLEANSE_TITLE) || title.equals(REPAIR_TITLE) || title.equals(SCROLLS_TITLE) ||
                title.equals(RUNE_CLEANSING_TITLE) || title.equals(ARTIFACTS_TITLE) ||
                title.contains("Заточка Артефакта") || title.contains("Перековка Артефакта") || title.contains("Слияние Артефактов") ||
                title.contains("Дезинтеграция") || title.contains("Эссенция") || title.contains("Бинд Артефакта") ||
                title.contains("Трансмутация") || title.contains("Артефакт-ритуал");
    }
    private boolean isWorkSlot(String title, int slot) {
        if (title.equals(FUSION_TITLE)) return slot == 20 || slot == 22 || slot == 24;
        if (title.contains("Слияние Артефактов")) return slot == 20 || slot == 22 || slot == 24;
        if (title.contains("Артефакт-ритуал")) return slot == 20 || slot == 22 || slot == 24;
        return (title.equals(REFORGE_TITLE) || title.equals(CLEANSE_TITLE) || title.equals(REPAIR_TITLE) ||
                title.equals(RUNE_CLEANSING_TITLE) || title.contains("Заточка Артефакта") || title.contains("Перековка Артефакта") ||
                title.contains("Дезинтеграция") || title.contains("Эссенция") || title.contains("Бинд Артефакта") ||
                title.contains("Трансмутация")) && slot == CENTER_SLOT;
    }
    private boolean isEmpty(ItemStack item) { return item == null || item.getType() == Material.AIR; }
    private int firstFreeFusionSlot(Inventory inv) { for (int s : FUSION_SLOTS) if (isEmpty(inv.getItem(s))) return s; return -1; }

    private void returnInputs(Player p, Inventory inv) {
        for (int slot : FUSION_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (isEmpty(item)) continue;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && " ".equals(item.getItemMeta().getDisplayName())) continue;
            inv.setItem(slot, null);
            p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    private void nav(Inventory inv) {
        inv.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.YELLOW + "← Назад"));
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "Закрыть"));
    }

    private int defaultScrollPrice(String type) {
        switch (type) {
            case "chance_25": return 2500;
            case "chance_50": return 5500;
            case "perfect": return 12000;
            case "protect_all": return 9000;
            case "anti_defect": return 3500;
            case "discount": return 4000;
            default: return 1000;
        }
    }

    private int defaultUpgradeChance(String targetRarity) { switch (targetRarity) { case "uncommon": return 85; case "rare": return 65; case "epic": return 40; case "legendary": return 20; case "ancient": return 8; default: return 50; } }
    private String nextRarity(String rarity) { switch (rarity) { case "common": return "uncommon"; case "uncommon": return "rare"; case "rare": return "epic"; case "epic": return "legendary"; case "legendary": return "ancient"; default: return null; } }
    private int rarityIndex(String key) { switch (key) { case "uncommon": return 1; case "rare": return 2; case "epic": return 3; case "legendary": return 4; case "ancient": return 5; default: return 0; } }

    private void applyRarity(ItemStack item, String rarityKey) {
        ItemMeta meta = item.getItemMeta();
        String rarityName = rarityDisplay(rarityKey);
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            if (ChatColor.stripColor(lore.get(i)).startsWith("Редкость:")) {
                lore.set(i, ChatColor.GRAY + "Редкость: " + rarityName);
                replaced = true;
                break;
            }
        }
        if (!replaced) lore.add(0, ChatColor.GRAY + "Редкость: " + rarityName);
        String prop = plugin.getGearManager().getRarityPropertyLine(rarityKey);
        if (prop != null) {
            lore.removeIf(l -> ChatColor.stripColor(l).startsWith("Свойство редкости:"));
            lore.add(Math.min(1, lore.size()), prop);
        }
        boolean alreadyMarked = false;
        for (String l : lore) {
            if (ChatColor.stripColor(l).contains("Возвышено в реликтовой кузне")) { alreadyMarked = true; break; }
        }
        if (!alreadyMarked) lore.add(ChatColor.DARK_PURPLE + "⭐ Возвышено в реликтовой кузне");
        meta.setLore(lore);
        String pure = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : item.getType().name();
        pure = pure.replaceFirst("^\\[[^]]+\\]\\s*", "");
        meta.setDisplayName(rarityName + " " + ChatColor.WHITE + pure);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_rarity"), PersistentDataType.STRING, rarityKey);
        item.setItemMeta(meta);
    }

    private String rarityDisplay(String key) { return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("rarities." + key + ".name", key)); }
    private String itemName(ItemStack item) { if (item == null) return "null"; return item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name(); }

    private ItemStack marker(Material mat, String name) { return item(mat, name, ChatColor.GRAY + "Рабочие слоты выше/рядом пустые."); }
    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv, Material mat) { ItemStack filler = item(mat, " "); for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            if ("reload".startsWith(prefix) && sender.hasPermission("vkchat.admin")) completions.add("reload");
            if ("stats".startsWith(prefix) && sender.hasPermission("vkchat.admin")) completions.add("stats");
            if ("schedule".startsWith(prefix) && sender.hasPermission("vkchat.forge")) completions.add("schedule");
            return completions;
        }
        return Collections.emptyList();
    }
}
