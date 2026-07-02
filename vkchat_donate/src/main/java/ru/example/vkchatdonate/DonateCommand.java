package ru.example.vkchatdonate;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;

public class DonateCommand implements CommandExecutor {
    private final VKChatDonatePlugin plugin;

    public DonateCommand(VKChatDonatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(plugin.getDonateManager().getSetupInfo());
            return true;
        }

        if (args[0].equalsIgnoreCase("setup")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Укажи API-токен: " + ChatColor.WHITE + "/donate setup <токен>");
                sender.sendMessage(ChatColor.GRAY + "Токен получить: donatepay.ru → Мои кассы → API");
                return true;
            }

            String token = args[1];
            plugin.getConfig().set("api-token", token);
            plugin.saveConfig();
            plugin.reloadConfig();

            // Настройка LuckPerms групп
            sender.sendMessage(ChatColor.GREEN + "✅ Токен сохранён!");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "═ Настройка LuckPerms — выполни команды в консоли:");
            sender.sendMessage(ChatColor.WHITE + "lp creategroup spark");
            sender.sendMessage(ChatColor.WHITE + "lp creategroup flame");
            sender.sendMessage(ChatColor.WHITE + "lp creategroup star");
            sender.sendMessage(ChatColor.WHITE + "lp creategroup legend");
            sender.sendMessage(ChatColor.WHITE + "lp creategroup overlord");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "✅ Готово! Донат-система активирована.");
            sender.sendMessage(ChatColor.GRAY + "Перезапусти сервер или перезагрузи плагин для старта опроса API.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            plugin.reloadConfig();
            plugin.getDonateManager().shutdown();
            sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен. Перезапусти плагин для старта опроса API.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") && sender instanceof Player) {
            Player p = (Player) sender;
            DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
            if (s == null) {
                p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
                p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
            } else {
                p.sendMessage(ChatColor.GREEN + "Ваш статус: " + s.name);
                p.sendMessage(ChatColor.GRAY + "Скидка: " + (int)(s.repDiscount * 100)
                        + "% | КД ТП: ×" + s.tpCooldownMult
                        + " | Домов: " + s.maxHomes);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("days") && sender instanceof Player) {
            Player p = (Player) sender;
            DonateManager.StatusDef s = plugin.getDonateManager().getPlayerStatus(p);
            if (s == null) {
                p.sendMessage(ChatColor.GRAY + "У вас нет донат-статуса.");
                p.sendMessage(ChatColor.YELLOW + "Поддержи сервер: /donate info");
            } else {
                long days = plugin.getDonateManager().getDaysLeft(p);
                p.sendMessage(ChatColor.GREEN + "Статус: " + s.name);
                p.sendMessage(ChatColor.YELLOW + "Осталось дней: " + ChatColor.WHITE + days);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            List<Map.Entry<String, Double>> top = plugin.getDonateManager().getTopDonors(10);
            sender.sendMessage(ChatColor.GOLD + "═══ ТОП-ДОНАТЕРЫ ═══");
            if (top.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "Пока нет донатов.");
            } else {
                int rank = 1;
                for (Map.Entry<String, Double> e : top) {
                    String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "  ";
                    sender.sendMessage(ChatColor.YELLOW + medal + " " + rank + ". " + ChatColor.WHITE + e.getKey() +
                            ChatColor.GRAY + " — " + ChatColor.GOLD + String.format("%.0f", e.getValue()) + "₽");
                    rank++;
                }
            }
            return true;
        }

        sender.sendMessage(plugin.getDonateManager().getSetupInfo());
        return true;
    }
}
