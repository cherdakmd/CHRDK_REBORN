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
            // Рулетка доступна только через VK
            int vkId = ru.example.vkchat.VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Привяжи ВК (/vklink) для доступа к рулетке!");
            } else {
                p.sendMessage(org.bukkit.ChatColor.GOLD + "🎰 Рулетка доступна в ЛС бота ВКонтакте!");
                p.sendMessage(org.bukkit.ChatColor.YELLOW + "Напиши боту: " + org.bukkit.ChatColor.GREEN + "!рулетка");
                p.sendMessage(org.bukkit.ChatColor.GRAY + "Там можно крутить, выбирать ставки и забирать призы!");
                // Показать ожидающие предметы
                if (ru.example.vkchat.VKChatPlugin.getInstance().getRouletteManager().hasPendingItems(vkId)) {
                    p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "📦 У тебя есть ожидающие призы! Забери их:");
                    // Забрать предметы
                    var items = ru.example.vkchat.VKChatPlugin.getInstance().getRouletteManager().takePendingItems(vkId);
                    if (items != null && !items.isEmpty()) {
                        int given = 0, lost = 0;
                        for (String item : items) {
                            String[] parts = item.split(";");
                            try {
                                org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                                int amount = Integer.parseInt(parts[1]);
                                if (p.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount)).isEmpty()) given++;
                                else lost++;
                            } catch (Exception e) { lost++; }
                        }
                        p.sendMessage(org.bukkit.ChatColor.GREEN + "📦 Получено: " + given);
                        if (lost > 0) p.sendMessage(org.bukkit.ChatColor.RED + "⚠ Не удалось: " + lost + " (инвентарь полон)");
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                }
            }
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
            completions.addAll(Arrays.asList("spawnnpc", "trends", "тренды", "history", "история",
                "roulette", "рулетка", "quest", "квест", "flash", "flashsale"));
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

}
