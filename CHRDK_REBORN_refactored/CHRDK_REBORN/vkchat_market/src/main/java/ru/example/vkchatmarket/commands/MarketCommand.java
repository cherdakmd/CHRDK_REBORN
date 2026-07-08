package ru.example.vkchatmarket.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.listeners.MarketGuiListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {
    private final VKChatMarketPlugin plugin;

    public MarketCommand(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        
        if (args.length > 0 && args[0].equalsIgnoreCase("spawnnpc")) {
            p.sendMessage(org.bukkit.ChatColor.RED + "Команда отключена.");
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("sellall") || args[0].equalsIgnoreCase("продатьвсе"))) {
            MarketGuiListener.sellAllFromCommand(plugin, p);
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("prices") || args[0].equalsIgnoreCase("цены"))) {
            showPrices(p);
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("search") || args[0].equalsIgnoreCase("поиск"))) {
            if (args.length < 2) { p.sendMessage(org.bukkit.ChatColor.RED + "Использование: /market search <название>"); return true; }
            showSearch(p, args[1]);
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("trends") || args[0].equalsIgnoreCase("тренды"))) {
            MarketGuiListener.openTrendsMenu(plugin, p);
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("история"))) {
            p.sendMessage(org.bukkit.ChatColor.GOLD + "История рынка:");
            for (String line : plugin.getMarketManager().getHistoryTail(10)) p.sendMessage(org.bukkit.ChatColor.GRAY + "• " + line);
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("quest") || args[0].equalsIgnoreCase("квест"))) {
            showQuestInfo(p);
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("flash") || args[0].equalsIgnoreCase("flashsale"))) {
            showFlashSaleInfo(p);
        } else if (args.length > 0) {
            MarketGuiListener.openGui(plugin, p, args[0]);
        } else {
            MarketGuiListener.openGui(plugin, p);
        }
        return true;
    }

    private void showQuestInfo(Player p) {
        var fun = plugin.getMarketFun();
        String info = fun.getQuestInfo();
        p.sendMessage(org.bukkit.ChatColor.GOLD + "═══ 📋 Квест Дня ═══");
        p.sendMessage(org.bukkit.ChatColor.YELLOW + info);
        if (fun.isQuestCompleted(p.getName())) {
            p.sendMessage(org.bukkit.ChatColor.GREEN + "✅ Вы уже выполнили квест!");
        } else {
            int progress = fun.getPlayerQuestProgress(p.getName());
            p.sendMessage(org.bukkit.ChatColor.GRAY + "Прогресс: " + progress + "/" + fun.getQuestTarget());
        }
    }

    private void showFlashSaleInfo(Player p) {
        var fun = plugin.getMarketFun();
        if (fun.getFlashSaleItemId() == null || System.currentTimeMillis() >= fun.getFlashSaleEndTime()) {
            p.sendMessage(org.bukkit.ChatColor.GRAY + "Сейчас нет активных Flash Sale.");
            return;
        }
        String name = plugin.getConfig().getString("items." + fun.getFlashSaleItemId() + ".name", fun.getFlashSaleItemId());
        int percent = (int) (fun.getFlashSaleDiscount() * 100);
        long remaining = (fun.getFlashSaleEndTime() - System.currentTimeMillis()) / 1000;
        p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "═══ ⚡ Flash Sale ═══");
        p.sendMessage(org.bukkit.ChatColor.YELLOW + name + org.bukkit.ChatColor.LIGHT_PURPLE + " -" + percent + "%");
        p.sendMessage(org.bukkit.ChatColor.GRAY + "Осталось: " + remaining + " сек.");
    }

    private void showPrices(Player p) {
        p.sendMessage(org.bukkit.ChatColor.GOLD + "═══ 📊 Все цены рынка ═══");
        if (plugin.getConfig().getConfigurationSection("items") == null) {
            p.sendMessage(org.bukkit.ChatColor.GRAY + "Нет товаров в конфигурации.");
            return;
        }
        var mgr = plugin.getMarketManager();
        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
            double sell = mgr.getCurrentPrice(itemId);
            double buy = mgr.getBuyPrice(itemId);
            String trend = mgr.getTrendLabel(itemId);
            p.sendMessage(org.bukkit.ChatColor.GRAY + "• " + org.bukkit.ChatColor.WHITE + name
                + org.bukkit.ChatColor.GRAY + " | Продать: " + org.bukkit.ChatColor.GREEN + String.format("%.2f", sell)
                + org.bukkit.ChatColor.GRAY + " | Купить: " + org.bukkit.ChatColor.GOLD + String.format("%.2f", buy)
                + org.bukkit.ChatColor.GRAY + " | " + trend);
        }
    }

    private void showSearch(Player p, String query) {
        String lowerQuery = query.toLowerCase();
        p.sendMessage(org.bukkit.ChatColor.GOLD + "═══ 🔍 Поиск: " + query + " ═══");
        if (plugin.getConfig().getConfigurationSection("items") == null) {
            p.sendMessage(org.bukkit.ChatColor.GRAY + "Нет товаров.");
            return;
        }
        int found = 0;
        var mgr = plugin.getMarketManager();
        for (String itemId : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            String name = plugin.getConfig().getString("items." + itemId + ".name", itemId);
            String cat = plugin.getConfig().getString("items." + itemId + ".category", "");
            if (itemId.toLowerCase().contains(lowerQuery) || name.toLowerCase().contains(lowerQuery) || cat.toLowerCase().contains(lowerQuery)) {
                double sell = mgr.getCurrentPrice(itemId);
                double buy = mgr.getBuyPrice(itemId);
                p.sendMessage(org.bukkit.ChatColor.WHITE + name
                    + org.bukkit.ChatColor.GRAY + " | Sell: " + org.bukkit.ChatColor.GREEN + String.format("%.2f", sell)
                    + org.bukkit.ChatColor.GRAY + " | Buy: " + org.bukkit.ChatColor.GOLD + String.format("%.2f", buy));
                found++;
                if (found >= 20) { p.sendMessage(org.bukkit.ChatColor.GRAY + "... и ещё результаты (показано 20)"); break; }
            }
        }
        if (found == 0) p.sendMessage(org.bukkit.ChatColor.GRAY + "Ничего не найдено по запросу: " + query);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("spawnnpc", "sellall", "продатьвсе", "trends", "тренды", "history", "история",
                "quest", "квест", "flash", "flashsale", "prices", "цены", "search", "поиск"));
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

}
