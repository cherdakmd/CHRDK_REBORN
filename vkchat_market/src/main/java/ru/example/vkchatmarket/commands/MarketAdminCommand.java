package ru.example.vkchatmarket.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MarketAdminCommand implements CommandExecutor, TabCompleter {
    private final VKChatMarketPlugin plugin;

    public MarketAdminCommand(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vkchat.market.admin")) {
            sender.sendMessage("§cНет прав!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                plugin.reloadConfig();
                plugin.getMarketService().load();
                sender.sendMessage("§a✓ Конфиг перезагружен. Товаров: " + plugin.getMarketService().getAll().size());
                break;
            case "stats":
                showStats(sender);
                break;
            case "setprice":
                setPrice(sender, args);
                break;
            case "events":
                showEvents(sender);
                break;
            case "startevent":
                startEvent(sender, args);
                break;
            case "stopevent":
                plugin.getMarketService().prices().clearEvent();
                sender.sendMessage("§a✓ Событие остановлено.");
                break;
            case "balance":
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    int vkId = ru.example.vkchat.util.VKChatBridge.getLinkedVkId(p);
                    if (vkId == -1) {
                        p.sendMessage("§cВК не привязан.");
                    } else {
                        int rep = ru.example.vkchat.util.VKChatBridge.getReputation(vkId);
                        p.sendMessage("§eБаланс: §f" + rep + " реп.");
                    }
                } else {
                    sender.sendMessage("§cТолько для игроков.");
                }
                break;
            case "menu":
                if (sender instanceof Player) {
                    MarketGui.openMainMenu(plugin, (Player) sender);
                } else {
                    sender.sendMessage("§cТолько для игроков.");
                }
                break;
            default:
                sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lБИРЖА §8▸ §7Команды админа:");
        sender.sendMessage("§e/mkta reload §7— перезагрузить конфиг");
        sender.sendMessage("§e/mkta stats §7— статистика экономики");
        sender.sendMessage("§e/mkta setprice <id> <цена> §7— установить базовую цену");
        sender.sendMessage("§e/mkta events §7— список событий");
        sender.sendMessage("§e/mkta startevent <id> §7— запустить событие");
        sender.sendMessage("§e/mkta stopevent §7— остановить событие");
        sender.sendMessage("§e/mkta balance §7— мой баланс");
        sender.sendMessage("§e/mkta menu §7— открыть меню биржи");
    }

    private void showStats(CommandSender sender) {
        var svc = plugin.getMarketService();
        var prices = svc.prices();
        int totalItems = svc.getAll().size();
        int totalCategories = MarketCategory.values().length;

        sender.sendMessage("§6§l═══ СТАТИСТИКА БИРЖИ ═══");
        sender.sendMessage("§7Товаров: §f" + totalItems);
        sender.sendMessage("§7Категорий: §f" + totalCategories);
        sender.sendMessage("§7Динамическое ценообразование: §f" + (plugin.getConfig().getBoolean("settings.dynamic-pricing", true) ? "вкл" : "выкл"));
        sender.sendMessage("§7Событие: " + (prices.hasActiveEvent() ? "§c" + prices.getActiveEventName() : "§7нет"));
        sender.sendMessage("§6§l═══════════════════");
    }

    private void setPrice(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c/mkta setprice <id> <цена>");
            return;
        }
        String id = args[1];
        int price;
        try { price = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage("§cЦена — число!");
            return;
        }
        MarketEntry entry = plugin.getMarketService().get(id);
        if (entry == null) {
            sender.sendMessage("§cТовар не найден: " + id);
            return;
        }
        plugin.getConfig().set("items." + id + ".base-price", price);
        plugin.saveConfig();
        plugin.getMarketService().load();
        sender.sendMessage("§a✓ Цена " + entry.displayName() + " → §e" + price + " реп.");
    }

    private void showEvents(CommandSender sender) {
        var sec = plugin.getConfig().getConfigurationSection("events.list");
        if (sec == null) { sender.sendMessage("§7Нет событий."); return; }
        sender.sendMessage("§6§l═══ СОБЫТИЯ ═══");
        for (String key : sec.getKeys(false)) {
            var ev = sec.getConfigurationSection(key);
            if (ev == null) continue;
            String name = ev.getString("name", key);
            String cat = ev.getString("category", "все");
            double buyM = ev.getDouble("buy-mult", 1.0);
            double sellM = ev.getDouble("sell-mult", 1.0);
            sender.sendMessage("§e" + key + " §7— " + name + " §7(§f" + cat + "§7) покупка §c" + buyM + "§7, продажа §a" + sellM);
        }
        var prices = plugin.getMarketService().prices();
        if (prices.hasActiveEvent()) {
            long rem = (prices.getActiveEventEnd() - System.currentTimeMillis()) / 60000;
            sender.sendMessage("§cАктивное: " + prices.getActiveEventName() + " (§e" + rem + " мин.§c)");
        }
        sender.sendMessage("§6§l═════════════════");
    }

    private void startEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c/mkta startevent <id>");
            return;
        }
        String id = args[1];
        var sec = plugin.getConfig().getConfigurationSection("events.list." + id);
        if (sec == null) {
            sender.sendMessage("§cСобытие не найдено: " + id);
            return;
        }
        plugin.getMarketService().prices().forceStartEvent(id, sec.getString("name", id));
        sender.sendMessage("§a✓ Событие запущено: " + sec.getString("name", id));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vkchat.market.admin")) return new ArrayList<>();
        if (args.length == 1) {
            return Arrays.asList("reload", "stats", "setprice", "events", "startevent", "stopevent", "balance", "menu")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("setprice") || args[0].equalsIgnoreCase("startevent"))) {
            String prefix = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("startevent")) {
                var sec = plugin.getConfig().getConfigurationSection("events.list");
                if (sec != null) {
                    return new ArrayList<>(sec.getKeys(false)).stream()
                            .filter(k -> k.startsWith(prefix)).collect(Collectors.toList());
                }
            } else {
                return plugin.getMarketService().getAll().stream()
                        .map(MarketEntry::id).filter(id -> id.startsWith(prefix)).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
