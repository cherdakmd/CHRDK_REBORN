package ru.example.vkchatauction;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final VKChatAuctionPlugin plugin;

    public AuctionCommand(VKChatAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }
        Player player = (Player) sender;
        AuctionManager manager = plugin.getAuctionManager();

        if (args.length == 0) {
            new AuctionGUI(plugin, player).openMainMenu();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell":
                return handleSell(player, args);
            case "bid":
                return handleBid(player, args);
            case "buy":
                return handleBuy(player, args);
            case "cancel":
                return handleCancel(player, args);
            case "my":
                new AuctionGUI(plugin, player).openMyAuctions();
                return true;
            case "history":
                new AuctionGUI(plugin, player).openHistory();
                return true;
            case "collect":
                manager.collectPending(player);
                return true;
            case "info":
                player.sendMessage(manager.getDonatePerksInfo(player));
                return true;
            default:
                player.sendMessage("§6Использование: §e/ah [sell|bid|buy|cancel|my|history|collect|info]");
                return true;
        }
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: §e/ah sell <цена> [выкуп]");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            player.sendMessage("§c❌ Возьми предмет в руку!");
            return true;
        }
        double startPrice;
        try {
            startPrice = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Некорректная цена!");
            return true;
        }
        double buyItNow = 0;
        if (args.length >= 3) {
            try {
                buyItNow = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c❌ Некорректная цена выкупа!");
                return true;
            }
        }
        AuctionManager.AuctionResult result = plugin.getAuctionManager().createAuction(player, item, startPrice, buyItNow);
        player.sendMessage(result.getMessage());
        return true;
    }

    private boolean handleBid(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cИспользование: §e/ah bid <id> <сумма>");
            return true;
        }
        UUID auctionId = parseAuctionId(args[1]);
        if (auctionId == null) {
            player.sendMessage("§c❌ Некорректный ID аукциона!");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Некорректная сумма!");
            return true;
        }
        AuctionManager.AuctionResult result = plugin.getAuctionManager().placeBid(player, auctionId, amount);
        player.sendMessage(result.getMessage());
        return true;
    }

    private boolean handleBuy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: §e/ah buy <id>");
            return true;
        }
        UUID auctionId = parseAuctionId(args[1]);
        if (auctionId == null) {
            player.sendMessage("§c❌ Некорректный ID аукциона!");
            return true;
        }
        AuctionManager.AuctionResult result = plugin.getAuctionManager().buyItNow(player, auctionId);
        if (!result.getMessage().isEmpty()) {
            player.sendMessage(result.getMessage());
        }
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: §e/ah cancel <id>");
            return true;
        }
        UUID auctionId = parseAuctionId(args[1]);
        if (auctionId == null) {
            player.sendMessage("§c❌ Некорректный ID аукциона!");
            return true;
        }
        AuctionManager.AuctionResult result = plugin.getAuctionManager().cancelAuction(player, auctionId);
        player.sendMessage(result.getMessage());
        return true;
    }

    private UUID parseAuctionId(String input) {
        try {
            if (input.length() < 8) return null;
            String full;
            if (input.contains("-")) {
                full = input;
            } else {
                AuctionManager manager = plugin.getAuctionManager();
                for (Auction a : manager.getActiveAuctions()) {
                    if (a.getId().toString().startsWith(input)) {
                        return a.getId();
                    }
                }
                return null;
            }
            return UUID.fromString(full);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;
        if (args.length == 1) {
            return Arrays.asList("sell", "bid", "buy", "cancel", "my", "history", "collect", "info").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Collections.singletonList("<цена>");
        }
        return Collections.emptyList();
    }
}
