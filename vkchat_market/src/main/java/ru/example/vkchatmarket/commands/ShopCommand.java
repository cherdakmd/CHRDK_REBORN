package ru.example.vkchatmarket.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.playerShop.PlayerShop;
import ru.example.vkchatmarket.playerShop.PlayerShopManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShopCommand implements CommandExecutor, TabCompleter {
    private final VKChatMarketPlugin plugin;

    public ShopCommand(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PlayerShopManager mgr = plugin.getPlayerShopManager();
        if (!mgr.isEnabled()) {
            sender.sendMessage("§cИгровые магазины отключены.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
            case "создать":
                handleCreate(p, args);
                break;
            case "remove":
            case "удалить":
                handleRemove(p);
                break;
            case "list":
            case "список":
                int page = args.length > 1 ? tryParseInt(args[1], 1) : 1;
                handleList(p, page);
                break;
            case "info":
            case "инфо":
                handleInfo(p);
                break;
            case "admin":
                handleAdmin(p, args);
                break;
            default:
                sendHelp(p);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6§l═══ ИГРОВЫЕ МАГАЗИНЫ ═══");
        p.sendMessage("§e/shop create <цена> §7— создать магазин (§e" +
                plugin.getPlayerShopManager().getCreateCost() + " реп.§7)");
        p.sendMessage("§e/shop remove §7— удалить свой магазин");
        p.sendMessage("§e/shop list [страница] §7— список магазинов");
        p.sendMessage("§e/shop info §7— информация о магазине");
        if (p.hasPermission("vkchat.market.admin")) {
            p.sendMessage("§e/shop admin remove <игрок> §7— удалить все магазины игрока");
        }
    }

    private void handleCreate(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§c/shop create <цена>");
            return;
        }
        int price;
        try {
            price = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            p.sendMessage("§cЦена — число!");
            return;
        }
        if (price < 1) {
            p.sendMessage("§cЦена должна быть больше 0!");
            return;
        }
        plugin.getPlayerShopManager().createShop(p, price);
    }

    private void handleRemove(Player p) {
        Block target = p.getTargetBlockExact(5);
        if (target == null) {
            p.sendMessage("§cСмотри на табличку магазина!");
            return;
        }
        PlayerShop shop = plugin.getPlayerShopManager().getShopAt(target);
        if (shop == null) {
            p.sendMessage("§cЭто не табличка магазина!");
            return;
        }
        plugin.getPlayerShopManager().removeShop(p, shop);
    }

    private void handleList(Player p, int page) {
        List<PlayerShop> all = plugin.getPlayerShopManager().getAllShops();
        if (all.isEmpty()) {
            p.sendMessage("§7Нет активных магазинов.");
            return;
        }
        int perPage = 10;
        int pages = (int) Math.ceil(all.size() / (double) perPage);
        page = Math.max(1, Math.min(page, pages));

        int start = (page - 1) * perPage;
        int end = Math.min(all.size(), start + perPage);

        p.sendMessage("§6§l═══ МАГАЗИНЫ §8(§f" + all.size() + "§8) §8[§f" + page + "/" + pages + "§8] ═══");
        for (int i = start; i < end; i++) {
            PlayerShop shop = all.get(i);
            String worldName = shop.getLocation().getWorld().getName();
            int x = shop.getLocation().getBlockX();
            int y = shop.getLocation().getBlockY();
            int z = shop.getLocation().getBlockZ();
            int itemsCount = (int) shop.getItems().stream().filter(it -> it != null && it.getType() != Material.AIR).count();
            p.sendMessage("§e" + (i + 1) + ". §f" + shop.getOwnerName()
                    + " §7— §e" + shop.getPrice() + " реп. §7| §f" + itemsCount + "§7/§f" + plugin.getPlayerShopManager().getMaxItemsPerShop()
                    + " §7| §8" + worldName + " " + x + " " + y + " " + z);
        }
        if (pages > 1) {
            p.sendMessage("§7Страница §f" + page + "§7/§f" + pages + " §7— /shop list <номер>");
        }
    }

    private void handleInfo(Player p) {
        Block target = p.getTargetBlockExact(5);
        if (target == null) {
            p.sendMessage("§cСмотри на табличку магазина!");
            return;
        }
        PlayerShop shop = plugin.getPlayerShopManager().getShopAt(target);
        if (shop == null) {
            p.sendMessage("§cЭто не табличка магазина!");
            return;
        }
        p.sendMessage("§6§l═══ ИНФО О МАГАЗИНЕ ═══");
        p.sendMessage("§7Владелец: §f" + shop.getOwnerName());
        p.sendMessage("§7Цена: §e" + shop.getPrice() + " реп.");
        p.sendMessage("§7Предметов: §f" + shop.getItems().size());
        p.sendMessage("§7Локация: §8" + shop.locationKey());
        if (shop.getItems().isEmpty()) {
            p.sendMessage("§7Товары: §7нет");
        } else {
            p.sendMessage("§7Товары:");
            for (int i = 0; i < shop.getItems().size() && i < 9; i++) {
                ItemStack item = shop.getItems().get(i);
                if (item != null && item.getType() != Material.AIR) {
                    p.sendMessage("  §e" + (i + 1) + ". §f" + (item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name()));
                }
            }
        }
    }

    private void handleAdmin(Player p, String[] args) {
        if (!p.hasPermission("vkchat.market.admin")) {
            p.sendMessage("§cНет прав!");
            return;
        }
        if (args.length < 3) {
            p.sendMessage("§c/shop admin remove <игрок>");
            return;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("remove") || sub.equals("удалить")) {
            String targetName = args[2];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
                if (offline == null || !offline.hasPlayedBefore()) {
                    p.sendMessage("§cИгрок не найден: " + targetName);
                    return;
                }
                boolean removed = plugin.getPlayerShopManager().adminRemovePlayerShops(offline.getUniqueId());
                if (removed) {
                    p.sendMessage("§a✓ Все магазины игрока §f" + offline.getName() + " §aудалены.");
                } else {
                    p.sendMessage("§7У игрока §f" + offline.getName() + " §7нет магазинов.");
                }
                return;
            }
            boolean removed = plugin.getPlayerShopManager().adminRemovePlayerShops(target.getUniqueId());
            if (removed) {
                p.sendMessage("§a✓ Все магазины игрока §f" + target.getName() + " §aудалены.");
            } else {
                p.sendMessage("§7У игрока §f" + target.getName() + " §7нет магазинов.");
            }
        } else {
            p.sendMessage("§c/shop admin remove <игрок>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> options = new ArrayList<>(Arrays.asList("create", "remove", "list", "info"));
            if (sender.hasPermission("vkchat.market.admin")) {
                options.add("admin");
            }
            return options.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("admin"))) {
            if (sender.hasPermission("vkchat.market.admin")) {
                return Arrays.asList("remove").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove")) {
            String prefix = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private int tryParseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
