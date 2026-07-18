package ru.example.vkchatmarket.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.gui.MarketGui;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;
import ru.example.vkchatmarket.service.MarketService;

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

        if (args.length > 0) {
            String cat = args[0].toLowerCase();
            if (cat.equals("menu") || cat.equals("меню")) {
                MarketGui.openMainMenu(plugin, p);
                return true;
            }
            if (cat.equals("sellall") || cat.equals("sell_all")) {
                MarketService svc = plugin.getMarketService();
                int total = 0, count = 0;
                for (MarketEntry en : svc.getAll()) {
                    int owned = svc.countItems(p, en);
                    if (owned > 0) {
                        total += svc.prices().getSellPrice(en, p) * owned;
                        count += owned;
                    }
                }
                if (count == 0) { p.sendMessage("§7Нет предметов для продажи."); return true; }
                MarketGui.openSellAllConfirm(plugin, p, "all", total, count);
                return true;
            }
            if (cat.equals("balance") || cat.equals("баланс")) {
                int vkId = VKChatBridge.getLinkedVkId(p);
                if (vkId == -1) {
                    p.sendMessage("§cВК не привязан. /vklink");
                } else {
                    int rep = VKChatBridge.getReputation(vkId);
                    String donorTag = plugin.getMarketService().prices().donorTag(p);
                    p.sendMessage("§6§lБИРЖА §8▸ §eБаланс: §f" + rep + " реп." + (donorTag != null ? " §7(§6⭐ " + donorTag + "§7)" : ""));
                }
                return true;
            }
            if (cat.equals("alert") || cat.equals("алерт")) {
                if (args.length < 3) {
                    p.sendMessage("§e/markет alert <id> <цена> §7— уведомить когда цена ≥");
                    p.sendMessage("§e/markет alerts §7— мои алерты");
                    p.sendMessage("§e/markет alert remove <id> §7— удалить алерт");
                    return true;
                }
                String sub = args[1].toLowerCase();
                if (sub.equals("remove") || sub.equals("удалить")) {
                    if (args.length < 3) { p.sendMessage("§c/markет alert remove <id>"); return true; }
                    plugin.getMarketService().prices().dynamics().removePriceAlert(p.getUniqueId(), args[2]);
                    p.sendMessage("§a✓ Алерт на §f" + args[2] + " §aудалён.");
                    return true;
                }
                if (sub.equals("list") || sub.equals("список") || sub.equals("alerts")) {
                    var alerts = plugin.getMarketService().prices().dynamics().getPlayerAlerts(p.getUniqueId());
                    if (alerts.isEmpty()) {
                        p.sendMessage("§7Нет алертов.");
                    } else {
                        p.sendMessage("§6§l═══ АЛЕРТЫ ═══");
                        for (var entry : alerts.entrySet()) {
                            MarketEntry me = plugin.getMarketService().get(entry.getKey());
                            String name = me != null ? me.displayName() : entry.getKey();
                            p.sendMessage("§e" + name + " §7→ §f≥ " + entry.getValue() + " реп.");
                        }
                    }
                    return true;
                }
                MarketEntry entry = plugin.getMarketService().get(args[1]);
                if (entry == null) { p.sendMessage("§cТовар не найден: " + args[1]); return true; }
                int targetPrice;
                try { targetPrice = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
                    p.sendMessage("§cЦена — число!"); return true;
                }
                plugin.getMarketService().prices().dynamics().setPriceAlert(p.getUniqueId(), entry.id(), targetPrice);
                p.sendMessage("§a✓ Алерт: §f" + entry.displayName() + " §7→ §e≥ " + targetPrice + " реп.");
                return true;
            }
            if (cat.equals("reload") || cat.equals("перезагрузить")) {
                if (!sender.hasPermission("vkchat.market.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                plugin.reloadConfig();
                plugin.reloadSubConfigs();
                plugin.getMarketService().load();
                sender.sendMessage("§a✓ Конфиг перезагружен. Товаров: " + plugin.getMarketService().getAll().size());
                return true;
            }
            if (cat.equals("stats") || cat.equals("статы")) {
                if (!sender.hasPermission("vkchat.market.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                var svc = plugin.getMarketService();
                var prices = svc.prices();
                sender.sendMessage("§6§l═══ СТАТИСТИКА БИРЖИ ═══");
                sender.sendMessage("§7Товаров: §f" + svc.getAll().size());
                sender.sendMessage("§7Категорий: §f" + MarketCategory.values().length);
                sender.sendMessage("§7Динамика: §f" + (plugin.getSettingsConfig().getBoolean("settings.dynamic-pricing", true) ? "вкл" : "выкл"));
                sender.sendMessage("§7Событие: " + (prices.hasActiveEvent() ? "§c" + prices.getActiveEventName() : "§7нет"));
                sender.sendMessage("§6§l═══════════════════");
                return true;
            }
            MarketCategory mc = MarketCategory.fromConfig(cat);
            if (mc != null || cat.equals("all") || cat.equals("все")) {
                MarketGui.openCategory(plugin, p, mc != null ? mc.configKey() : "all", 0);
                return true;
            }
        }

        MarketGui.openMainMenu(plugin, p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("menu", "меню", "sellall", "sell_all", "all", "все", "balance", "баланс", "alert", "алерт", "alerts"));
            if (sender.hasPermission("vkchat.market.admin")) {
                options.addAll(Arrays.asList("reload", "stats"));
            }
            for (MarketCategory cat : MarketCategory.values()) {
                options.add(cat.configKey());
            }
            String last = args[0].toLowerCase();
            return options.stream().filter(s -> s.startsWith(last)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("alert") || args[0].equalsIgnoreCase("алерт"))) {
            String last = args[1].toLowerCase();
            List<String> ids = plugin.getMarketService().getAll().stream()
                    .map(MarketEntry::id).filter(id -> id.startsWith(last)).collect(Collectors.toList());
            ids.add("remove");
            ids.add("list");
            return ids;
        }
        return new ArrayList<>();
    }
}
