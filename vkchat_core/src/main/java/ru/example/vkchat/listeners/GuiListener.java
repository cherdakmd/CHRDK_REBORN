package ru.example.vkchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.vkchat.VKChatPlugin;

import java.util.Arrays;

public class GuiListener implements Listener {
    private final VKChatPlugin plugin;
    private final String GUI_TITLE;
    private final String SERVER_TITLE;
    private final String ECONOMY_TITLE;
    private final String RPG_TITLE;
    private final String VK_TITLE;
    private final String HELP_TITLE;
    private final String NEWBIE_TITLE;

    public GuiListener(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §b§lМЕНЮ §8◂ §7Главная");
        this.SERVER_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §6§lМЕНЮ CHRDK §8◂ §7Сервер");
        this.ECONOMY_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §a§lМЕНЮ §8◂ §7Экономика");
        this.RPG_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §d§lМЕНЮ §8◂ §7RPG");
        this.VK_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §b§lМЕНЮ §8◂ §7Социалка");
        this.HELP_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §f§lМЕНЮ §8◂ §7Помощь");
        this.NEWBIE_TITLE = ChatColor.translateAlternateColorCodes('&', "§8▸ §e§lМЕНЮ §8◂ §7Новичок");
    }

    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        int vkId = plugin.getAuthManager().getLinkedVkId(p);
        boolean linked = vkId != -1;
        int rep = linked ? plugin.getReputationManager().getPoints(vkId) : 0;

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) ? border : accent);

        inv.setItem(11, profile(p, rep, linked));
        inv.setItem(13, item(Material.COMPASS, "§6🏠 Серверное меню",
                "§7Основные механики, экономика,",
                "§7RPG-прогресс, помощь.",
                "", "§e▶ Открыть"));
        inv.setItem(15, item(Material.BOOK, "§b📱 Группа ВК",
                "§7Новости, мини-игры, поддержка.",
                "§7" + plugin.getConfig().getString("vk.group-link", "https://vk.com")));
        p.openInventory(inv);
    }

    public void openServerMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, SERVER_TITLE);

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) ? border : accent);

        int vkId = plugin.getAuthManager().getLinkedVkId(p);
        int rep = vkId != -1 ? plugin.getReputationManager().getPoints(vkId) : 0;
        inv.setItem(4, item(Material.GOLD_BLOCK, "§6§l⚡ СЕРВЕР CHRDK",
                "§7Все механики в одном меню",
                "§e💰 Баланс: §f" + rep + " реп."));

        // Ряд 2: RPG
        inv.setItem(10, menuItem(Material.NETHER_STAR, "§d⭐ RPG-прогресс", "§7Gear, артефакты, мобы, профессии"));
        inv.setItem(11, menuItem(Material.ANVIL, "§c⚒ Кузня", "§7Перековка, слияние, очистка", "§7§o/forge"));
        inv.setItem(12, menuItem(Material.ENCHANTING_TABLE, "§d🔮 Руны и заточка", "§7Кристаллы до +25", "§7§o/runes"));
        inv.setItem(13, menuItem(Material.TOTEM_OF_UNDYING, "§d🏺 Артефакты", "§7Баффы, эликсиры, свитки", "§7§o/artifacts"));
        inv.setItem(14, menuItem(Material.ZOMBIE_HEAD, "§4☠ Охота", "§7Элитки, контракты, осады", "§7§o/mobs"));
        inv.setItem(15, menuItem(Material.GOLDEN_SWORD, "§c⚔ События", "§7Катаклизмы, боссы, квесты", "§7§o/events"));
        inv.setItem(16, menuItem(Material.BOOK, "§e💼 Профессии", "§7Шахтёр, кузнец, охотник...", "§7§o/jobs"));

        // Ряд 3: Сервисы
        inv.setItem(20, menuItem(Material.SHIELD, "§b👥 Нации и приваты", "§76 наций, защита территории", "§7§o/nation"));
        inv.setItem(21, menuItem(Material.EMERALD_BLOCK, "§a📈 Биржа", "§7Динамический рынок ресурсов", "§7§o/market"));
        inv.setItem(22, menuItem(Material.ENDER_PEARL, "§b⏳ Телепорты", "§7RTP, home, TPA, back", "§7§o/rtp /home /tpa"));
        inv.setItem(23, menuItem(Material.CHEST, "§a🗑 Утилизация", "§7Разбор MMO-шмота", "§7§o/salvage"));
        inv.setItem(24, menuItem(Material.GOLD_INGOT, "§6💰 Донат", "§75 статусов от 250₽ до 5000₽", "§7Скидки, дома, ускорение"));

        // Ряд 4: Инфо
        inv.setItem(31, menuItem(Material.PAPER, "§b📱 ВК и социалка", "§7Профиль, чат, охота"));
        inv.setItem(32, menuItem(Material.MAP, "§f📖 Помощь", "§7Команды, FAQ, гайды"));
        inv.setItem(49, item(Material.BARRIER, "§c✕ Закрыть"));
        p.openInventory(inv);
    }

    private ItemStack menuItem(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(lore));
        i.setItemMeta(m);
        return i;
    }

    private void openEconomyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ECONOMY_TITLE);
        fill(inv, Material.GREEN_STAINED_GLASS_PANE);
        int vkId = plugin.getAuthManager().getLinkedVkId(p);
        int rep = vkId != -1 ? plugin.getReputationManager().getPoints(vkId) : 0;
        inv.setItem(4, item(Material.EMERALD, ChatColor.GREEN + "💰 Баланс репутации", ChatColor.GRAY + "Текущий баланс: " + ChatColor.YELLOW + rep, ChatColor.GRAY + "Репутация — главная валюта сервера."));
        inv.setItem(10, priceItem(Material.ANVIL, "⚒ Ковка Gear", "VKChatGear", "hardcore-forging.craft-cost", 120, "Обычный крафт MMO-предмета"));
        inv.setItem(11, priceItem(Material.NETHERITE_INGOT, "🔥 Перековка", "VKChatGear", "hardcore-forging.reforge-cost", 650, "Рискованная перековка /forge"));
        inv.setItem(12, priceItem(Material.GRINDSTONE, "✨ Очистка дефектов", "VKChatGear", "hardcore-forging.cleanse-cost", 350, "Снять дефекты предмета"));
        inv.setItem(13, priceItem(Material.ENCHANTING_TABLE, "🔮 Нанесение руны", "VKChatGear", "hardcore-forging.rune-apply-cost", 75, "Доп. цена нанесения руны"));
        inv.setItem(14, priceItem(Material.EXPERIENCE_BOTTLE, "💎 Заточка", "VKChatGear", "hardcore-forging.crystal-apply-cost", 50, "Попытка заточки кристаллом"));
        inv.setItem(15, priceItem(Material.ENDER_PEARL, "⏳ RTP", "VKChatTeleport", "costs.rtp-percent", 1, "Телепорты часто стоят % от баланса"));
        inv.setItem(28, item(Material.HOPPER, ChatColor.YELLOW + "Антиинфляция", ChatColor.GRAY + "• лимиты наград", ChatColor.GRAY + "• антифарм мобов", ChatColor.GRAY + "• редкий лут вместо чистой репы", ChatColor.GRAY + "• риск = награда"));
        inv.setItem(31, item(Material.PAPER, ChatColor.AQUA + "Аудит экономики", ChatColor.GRAY + "Админ-команда:", ChatColor.YELLOW + "/vkchat economy", ChatColor.GRAY + "Создаёт economy-report.md"));
        inv.setItem(49, backItem());
        p.openInventory(inv);
    }

    private void openRpgMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, RPG_TITLE);
        fill(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(10, item(Material.ANVIL, ChatColor.RED + "⚒ Кузня", ChatColor.GRAY + "GUI-слот, перековка, дефекты, фрагменты.", ChatColor.YELLOW + "/forge"));
        inv.setItem(12, item(Material.ENCHANTING_TABLE, ChatColor.LIGHT_PURPLE + "🔮 Руны", ChatColor.GRAY + "Кристаллы заточки до +20 и руны.", ChatColor.YELLOW + "/runes"));
        inv.setItem(14, item(Material.TOTEM_OF_UNDYING, ChatColor.LIGHT_PURPLE + "🏺 Артефакты", ChatColor.GRAY + "Артефакты, эликсиры, свитки.", ChatColor.YELLOW + "/artifacts"));
        inv.setItem(16, item(Material.ZOMBIE_HEAD, ChatColor.DARK_RED + "☠ Охота", ChatColor.GRAY + "Элитки, боссы, фрагменты сетов.", ChatColor.YELLOW + "/mobs"));
        inv.setItem(28, item(Material.BOOK, ChatColor.YELLOW + "💼 Профессии", ChatColor.GRAY + "Blacksmith влияет на Gear, другие Jobs — на прогресс.", ChatColor.YELLOW + "/jobs"));
        inv.setItem(30, item(Material.MAP, ChatColor.WHITE + "📖 Помощь", ChatColor.GRAY + "Wiki, команды, FAQ.", ChatColor.YELLOW + "Открыть подраздел"));
        inv.setItem(32, item(Material.BARREL, ChatColor.GOLD + "🏆 Достижения", ChatColor.GRAY + "Прогресс сервера и статистика.", ChatColor.YELLOW + "/events info"));
        inv.setItem(49, backItem());
        p.openInventory(inv);
    }

    private void openVkMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, VK_TITLE);
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(10, item(Material.PLAYER_HEAD, ChatColor.AQUA + "📱 ВК-профиль", ChatColor.GRAY + "Привязка аккаунта и репутация.", ChatColor.YELLOW + "/vklink /rep"));
        inv.setItem(12, item(Material.ZOMBIE_HEAD, ChatColor.RED + "🏹 Охота ВК", ChatColor.GRAY + "!охота, !мобы, !контракт", ChatColor.YELLOW + "Инфо про элиток и награды"));
        inv.setItem(14, item(Material.EMERALD, ChatColor.GREEN + "🎁 Промокоды", ChatColor.GRAY + "Промокоды публикуются в ВК и событиях сервера.", ChatColor.YELLOW + "!промо <код>"));
        inv.setItem(16, item(Material.PAPER, ChatColor.YELLOW + "Команды ВК", ChatColor.GRAY + "!охота, !профиль, !рейтинг, !бонус", ChatColor.YELLOW + "Пиши боту или в беседу"));
        inv.setItem(30, item(Material.BOOK, ChatColor.GOLD + "Группа ВК", ChatColor.WHITE + plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn")));
        inv.setItem(49, backItem());
        p.openInventory(inv);
    }

    private void openHelpMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, HELP_TITLE);
        fill(inv, Material.WHITE_STAINED_GLASS_PANE);
        inv.setItem(10, item(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Быстрый старт", ChatColor.GRAY + "1) /register", ChatColor.GRAY + "2) /vklink", ChatColor.GRAY + "3) /menu", ChatColor.GRAY + "4) /nation"));
        inv.setItem(12, item(Material.MAP, ChatColor.AQUA + "Wiki", ChatColor.GRAY + "Документация в папке docs/", ChatColor.YELLOW + "docs/index.html"));
        inv.setItem(14, item(Material.COMPASS, ChatColor.GREEN + "Безопасное начало", ChatColor.GRAY + "Начни с /jobs, /nation, /rtp", ChatColor.GRAY + "Не рискуй дорогой перековкой сразу."));
        inv.setItem(16, item(Material.BARRIER, ChatColor.RED + "Важно", ChatColor.GRAY + "Дорогие действия требуют внимания:", ChatColor.GRAY + "перековка может уничтожить предмет."));
        inv.setItem(49, backItem());
        p.openInventory(inv);
    }

    private void openNewbieMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, NEWBIE_TITLE);
        fill(inv, Material.YELLOW_STAINED_GLASS_PANE);
        inv.setItem(10, item(Material.OAK_SAPLING, ChatColor.GREEN + "Шаг 1: Привязка", ChatColor.GRAY + "Привяжи ВК и получи доступ к экономике.", ChatColor.YELLOW + "/vklink"));
        inv.setItem(12, item(Material.COMPASS, ChatColor.AQUA + "Шаг 2: Безопасный старт", ChatColor.GRAY + "Найди место через RTP и поставь приват.", ChatColor.YELLOW + "/rtp, /nation buyclaim"));
        inv.setItem(14, item(Material.WOODEN_PICKAXE, ChatColor.GOLD + "Шаг 3: Профессии", ChatColor.GRAY + "Возьми работу и копи уровни.", ChatColor.YELLOW + "/jobs"));
        inv.setItem(16, item(Material.IRON_CHESTPLATE, ChatColor.RED + "Шаг 4: Gear", ChatColor.GRAY + "Куй осторожно: репутация ценная.", ChatColor.YELLOW + "/forge, /runes"));
        inv.setItem(28, item(Material.CHEST, ChatColor.YELLOW + "Шаг 5: Награды", ChatColor.GRAY + "Лут со смен и наград — тут.", ChatColor.YELLOW + "/stash"));
        inv.setItem(49, backItem());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();
        if (title.equals(GUI_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            Material type = e.getCurrentItem().getType();
            if (type == Material.BOOK) {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Ссылка на нашу группу: " + ChatColor.WHITE + plugin.getConfig().getString("vk.group-link", "https://vk.com"));
            } else if (type == Material.COMPASS) {
                openServerMenu(p);
            }
            return;
        }
        if (title.equals(ECONOMY_TITLE) || title.equals(RPG_TITLE) || title.equals(VK_TITLE) || title.equals(HELP_TITLE) || title.equals(NEWBIE_TITLE)) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) openServerMenu(p);
            else handleSubMenuClick(p, e.getRawSlot(), title);
            return;
        }
        if (!title.equals(SERVER_TITLE)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        switch (e.getRawSlot()) {
            case 10: openRpgMenu(p); break;
            case 11: run(p, "forge"); break;
            case 12: run(p, "runes"); break;
            case 13: run(p, "artifacts"); break;
            case 14: run(p, "mobs"); break;
            case 15: run(p, "events"); break;
            case 16: run(p, "jobs"); break;
            case 20: run(p, "nation"); break;
            case 21: run(p, "market"); break;
            case 22: p.closeInventory(); p.sendMessage(ChatColor.AQUA + "Телепорты: /rtp, /sethome, /home, /tpa"); break;
            case 23: run(p, "salvage"); break;
            case 24: p.closeInventory(); p.sendMessage(ChatColor.GOLD + "💰 Поддержать сервер: " + ChatColor.WHITE + "https://donatepay.ru/don/CHRDK"); p.sendMessage(ChatColor.GRAY + "5 статусов на 30 дней со скидками до -65%!"); p.sendMessage(ChatColor.YELLOW + "Подробнее: /donate info"); break;
            case 31: openVkMenu(p); break;
            case 32: openHelpMenu(p); break;
            case 33: openNewbieMenu(p); break;
            case 34: break;
            case 41: p.closeInventory(); p.sendMessage(ChatColor.GOLD + "⛏ Шахтёрские смены: !шахта, !смена, !shift — в ЛС бота ВК"); p.sendMessage(ChatColor.YELLOW + "Донат: !донат / !donate — в ЛС бота ВК"); break;
        }
    }

    private void handleSubMenuClick(Player p, int slot, String title) {
        if (title.equals(ECONOMY_TITLE)) {
            if (slot == 10 || slot == 11 || slot == 12 || slot == 13 || slot == 14) run(p, "forge");
            else if (slot == 31) p.sendMessage(ChatColor.YELLOW + "Админ-аудит экономики: /vkchat economy");
        } else if (title.equals(RPG_TITLE)) {
            if (slot == 10) run(p, "forge"); else if (slot == 12) run(p, "runes"); else if (slot == 14) run(p, "artifacts"); else if (slot == 16) run(p, "mobs"); else if (slot == 28) run(p, "jobs"); else if (slot == 32) run(p, "stash");
        } else if (title.equals(VK_TITLE)) {
            if (slot == 10) run(p, "vk");
        }
    }

    private ItemStack priceItem(Material mat, String name, String pluginName, String path, int def, String desc) {
        int value = getPluginInt(pluginName, path, def);
        return item(mat, ChatColor.GREEN + name,
                ChatColor.GRAY + desc,
                ChatColor.GRAY + "Источник: " + pluginName,
                ChatColor.YELLOW + "Цена/значение: " + value,
                value >= 500 ? ChatColor.RED + "Дорогое действие: нужна внимательность" : ChatColor.GREEN + "Базовая стоимость");
    }

    private int getPluginInt(String pluginName, String path, int def) {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin(pluginName);
            if (pl instanceof JavaPlugin) return ((JavaPlugin) pl).getConfig().getInt(path, def);
        } catch (Exception ignored) {}
        return def;
    }

    private ItemStack backItem() {
        return item(Material.ARROW, ChatColor.YELLOW + "← Назад", ChatColor.GRAY + "Вернуться в главное меню");
    }

    private void run(Player p, String command) {
        p.closeInventory();
        p.performCommand(command);
    }

    private ItemStack profile(Player p, int rep, boolean linked) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(p);
        meta.setDisplayName(ChatColor.GOLD + "Профиль " + p.getName());
        meta.setLore(Arrays.asList(ChatColor.GRAY + "ВК: " + (linked ? ChatColor.GREEN + "привязан" : ChatColor.RED + "не привязан"), ChatColor.GRAY + "Репутация: " + ChatColor.YELLOW + rep));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv, Material mat) {
        ItemStack filler = item(mat, " ");
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }
}
