package ru.example.vkchatevents.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatevents.VKChatEventsPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI дашборд событий v3.0
 */
public class EventsCommand implements CommandExecutor, TabCompleter, Listener {
    private final VKChatEventsPlugin plugin;
    private final String GUI_TITLE = "§8▸ §c§lСОБЫТИЯ §8◂ §7Меню";

    public EventsCommand(VKChatEventsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("stats")) {
                if (!sender.hasPermission("vkchat.events.stats")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                showStats(sender);
                return true;
            }
            if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("admin")) {
                if (!sender.hasPermission("vkchat.events.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "VKChatEvents v3.0 — 16 активных менеджеров.");
                sender.sendMessage(ChatColor.GRAY + "Используйте /events для открытия GUI.");
                return true;
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков! Используйте /events stats для статистики.");
            return true;
        }
        openDashboard((Player) sender);
        return true;
    }

    private void showStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ СТАТИСТИКА СОБЫТИЙ ═══");
        boolean invasionActive = plugin.getInvasionManager().isActive();
        boolean bossActive = plugin.getWrathManager().isActive();
        String cataclysm = plugin.getWrathManager().getActiveCataclysm();
        sender.sendMessage(ChatColor.DARK_PURPLE + "Разлом Бездны: " + (invasionActive ? ChatColor.GREEN + "АКТИВЕН" : ChatColor.RED + "Закрыт"));
        sender.sendMessage(ChatColor.DARK_RED + "Аватар Гнева: " + (bossActive ? ChatColor.GREEN + "ПРИЗВАН" : ChatColor.RED + "Не призван"));
        sender.sendMessage(ChatColor.AQUA + "Катаклизм: " + (cataclysm != null ? ChatColor.GREEN + cataclysm : ChatColor.YELLOW + "Нет"));
        sender.sendMessage(ChatColor.GRAY + "Менеджеров активно: 16");
    }

    public void openDashboard(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Стекло
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // === СУЩЕСТВУЮЩИЕ СИСТЕМЫ ===

        // 1. Разлом Бездны (Слот 10)
        boolean invasionActive = plugin.getInvasionManager().isActive();
        inv.setItem(10, createItem(Material.OBSIDIAN, ChatColor.DARK_PURPLE + "🌌 Разлом Бездны",
                invasionActive ? ChatColor.GREEN + "● АКТИВНО" : ChatColor.RED + "○ Закрыт"));

        // 2. Босс (Слот 11)
        boolean bossActive = plugin.getWrathManager().isActive();
        inv.setItem(11, createItem(Material.WITHER_SKELETON_SKULL, ChatColor.DARK_RED + "☠️ Аватар Гнева",
                bossActive ? ChatColor.GREEN + "● БОСС ПРИЗВАН" : ChatColor.RED + "○ Не призван"));

        // 3. Катаклизм (Слот 12)
        String cataclysm = plugin.getWrathManager().getActiveCataclysm();
        inv.setItem(12, createItem(Material.CLOCK, ChatColor.AQUA + "🌤️ Катаклизм",
                cataclysm != null ? ChatColor.GREEN + "● " + cataclysm : ChatColor.YELLOW + "○ Тихо"));

        // 4. Квесты (Слот 13)
        inv.setItem(13, createItem(Material.BOOK, ChatColor.YELLOW + "📋 Квесты",
                ChatColor.GRAY + "Сюжетные квесты-цепочки"));

        // 5. Контракты (Слот 14)
        inv.setItem(14, createItem(Material.WRITABLE_BOOK, ChatColor.RED + "🎯 Контракты",
                ChatColor.GRAY + "Баунти на игроков"));

        // === НОВЫЕ СИСТЕМЫ ===

        // 6. Ежедневная награда (Слот 19)
        inv.setItem(19, createItem(Material.GOLD_INGOT, ChatColor.GOLD + "🎁 Ежедневная награда",
                ChatColor.GRAY + "Получай реп за вход каждый день!"));

        // 7. Испытания (Слот 20)
        inv.setItem(20, createItem(Material.PAPER, ChatColor.AQUA + "📋 Испытания",
                ChatColor.GRAY + "Ежедневные задания с наградами"));

        // 8. Магазин событий (Слот 21)
        inv.setItem(21, createItem(Material.EMERALD, ChatColor.GREEN + "🛒 Магазин событий",
                ChatColor.GRAY + "Бусты и бонусы за очки"));

        // 9. Достижения (Слот 22)
        inv.setItem(22, createItem(Material.NETHER_STAR, ChatColor.GOLD + "🏅 Достижения",
                ChatColor.GRAY + "20 достижений событий"));

        // 10. Таблица лидеров (Слот 23)
        inv.setItem(23, createItem(Material.DIAMOND, ChatColor.YELLOW + "🏆 Лидерборд",
                ChatColor.GRAY + "Лучшие игроки сервера"));

        // 11. Статистика (Слот 24)
        inv.setItem(24, createItem(Material.PAPER, ChatColor.AQUA + "📊 Статистика",
                ChatColor.GRAY + "Твоя статистика"));

        // 12. Предсказание (Слот 29)
        inv.setItem(29, createItem(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "🔮 Предсказание",
                ChatColor.GRAY + "Узнай что ждёт тебя сегодня!"));

        // 13. Комбо (Слот 30)
        inv.setItem(30, createItem(Material.FIREWORK_ROCKET, ChatColor.YELLOW + "🔥 Комбо",
                ChatColor.GRAY + "Бонусы за серию действий"));

        // 14. Активность (Слот 31)
        inv.setItem(31, createItem(Material.CLOCK, ChatColor.GREEN + "📈 Активность",
                ChatColor.GRAY + "Твоя активность на сервере"));

        // 15. PvP (Слот 32)
        inv.setItem(32, createItem(Material.IRON_SWORD, ChatColor.RED + "⚔ PvP",
                ChatColor.GRAY + "Статистика боёв"));

        // 16. Эволюция (Слот 33)
        inv.setItem(33, createItem(Material.BEACON, ChatColor.LIGHT_PURPLE + "🧬 Эволюция",
                ChatColor.GRAY + "Развивай своего персонажа"));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        switch (e.getRawSlot()) {
            case 10: // Разлом
                p.sendMessage(ChatColor.DARK_PURPLE + "Разлом Бездны: " +
                        (plugin.getInvasionManager().isActive() ? "Активен" : "Закрыт"));
                break;
            case 11: // Босс
                p.sendMessage(ChatColor.DARK_RED + "Аватар Гнева: " +
                        (plugin.getWrathManager().isActive() ? "Жив" : "Не призван"));
                break;
            case 19: // Ежедневная награда
                p.sendMessage(ChatColor.GOLD + "🎁 Ежедневная награда: заходи на сервер каждый день!");
                break;
            case 20: // Испытания
                p.sendMessage(ChatColor.AQUA + "📋 Испытания: убивай мобов, добывай ресурсы, крафти!");
                break;
            case 29: // Предсказание
                if (plugin.getActivityManager() != null) {
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 " + plugin.getActivityManager().getPrediction(p.getUniqueId()));
                }
                break;
            case 31: // Активность
                if (plugin.getActivityManager() != null) {
                    p.sendMessage(plugin.getActivityManager().getStats(p.getUniqueId()));
                }
                break;
            case 32: // PvP
                if (plugin.getCombatManager() != null) {
                    p.sendMessage(plugin.getCombatManager().getPvPStats(p.getUniqueId()));
                }
                break;
            case 33: // Эволюция
                if (plugin.getEvolutionManager() != null) {
                    p.sendMessage(plugin.getEvolutionManager().getEvolutionStats(p.getUniqueId()));
                }
                break;
        }
    }

    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        loreList.add(lore);
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("info", "boss", "ores", "artifacts", "shop", "quests", "challenges", "stats"));
            if (sender.hasPermission("vkchat.events.admin")) {
                options.add("reload");
                options.add("admin");
            }
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
