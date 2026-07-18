package ru.example.vkchatauction;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionAdminCommand implements CommandExecutor, TabCompleter {

    private final VKChatAuctionPlugin plugin;

    public AuctionAdminCommand(VKChatAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vkchat.auction.admin")) {
            sender.sendMessage("§c❌ Нет прав!");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        AuctionManager manager = plugin.getAuctionManager();
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                manager.reloadConfig();
                sender.sendMessage("§a✓ Конфиг аукциона перезагружен.");
                break;
            case "list":
                long total = manager.getAuctionMapValues().size();
                long active = manager.getActiveAuctions().size();
                sender.sendMessage("§6═══ Статистика аукциона ═══");
                sender.sendMessage("§fВсего лотов: §e" + total);
                sender.sendMessage("§fАктивных: §e" + active);
                sender.sendMessage("§fИстекших/завершённых: §e" + (total - active));
                break;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /ahadmin remove <player> [auction-id]");
                    break;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c❌ Игрок не найден!");
                    break;
                }
                if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
                    try {
                        UUID auctionId = UUID.fromString(args[2]);
                        Auction removed = manager.adminRemoveAuction(auctionId);
                        if (removed != null) {
                            sender.sendMessage("§a✓ Лот удалён: §f" + AuctionManager.formatItemName(removed.getItemStack()));
                        } else {
                            sender.sendMessage("§c❌ Лот не найден.");
                        }
                    } catch (Exception e) {
                        sender.sendMessage("§c❌ Некорректный ID.");
                    }
                } else {
                    int count = manager.removePlayerAuctions(target.getUniqueId());
                    sender.sendMessage("§a✓ Удалено лотов игрока §f" + target.getName() + "§a: §e" + count);
                }
                break;
            case "clear":
                manager.clearAll();
                sender.sendMessage("§a✓ Все аукционы очищены.");
                break;
            default:
                sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6═══ Админка аукциона ═══");
        sender.sendMessage("§e/ahadmin reload §7— перезагрузить конфиг");
        sender.sendMessage("§e/ahadmin list §7— статистика");
        sender.sendMessage("§e/ahadmin remove <player> [all|<id>] §7— удалить лоты игрока");
        sender.sendMessage("§e/ahadmin clear §7— очистить все лоты");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vkchat.auction.admin")) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("reload", "list", "remove", "clear").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
