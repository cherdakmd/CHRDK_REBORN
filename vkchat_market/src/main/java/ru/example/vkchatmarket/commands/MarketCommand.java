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
            if (!p.hasPermission("vkchat.admin")) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Нет прав.");
                return true;
            }
            
            org.bukkit.entity.Villager npc = (org.bukkit.entity.Villager) p.getWorld().spawnEntity(p.getLocation(), org.bukkit.entity.EntityType.VILLAGER);
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setCustomName(org.bukkit.ChatColor.GOLD + "Скупщик Лута");
            npc.setCustomNameVisible(true);
            
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "market_npc");
            npc.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            
            p.sendMessage(org.bukkit.ChatColor.GREEN + "NPC-торговец успешно заспавнен!");
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("trends") || args[0].equalsIgnoreCase("тренды"))) {
            MarketGuiListener.openTrendsMenu(plugin, p);
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("история"))) {
            p.sendMessage(org.bukkit.ChatColor.GOLD + "История рынка:");
            for (String line : plugin.getMarketManager().getHistoryTail(10)) p.sendMessage(org.bukkit.ChatColor.GRAY + "• " + line);
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("roulette") || args[0].equalsIgnoreCase("рулетка"))) {
            plugin.getMarketFun().spinRoulette(p);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("spawnnpc", "trends", "тренды", "history", "история", "roulette", "рулетка", "quest", "квест", "flash", "flashsale"));
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

}
