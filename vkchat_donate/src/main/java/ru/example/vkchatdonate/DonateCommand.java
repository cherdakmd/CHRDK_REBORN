package ru.example.vkchatdonate;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            sender.sendMessage(ChatColor.GREEN + "═══ Настройка DonatePay ═══");
            sender.sendMessage(ChatColor.YELLOW + "1. " + ChatColor.WHITE + "Зайди на donatepay.ru → Мои кассы");
            sender.sendMessage(ChatColor.YELLOW + "2. " + ChatColor.WHITE + "Создай кассу, скопируй API-токен");
            sender.sendMessage(ChatColor.YELLOW + "3. " + ChatColor.WHITE + "В config.yml пропиши api-token: \"ТВОЙ_ТОКЕН\"");
            sender.sendMessage(ChatColor.YELLOW + "4. " + ChatColor.WHITE + "Перезапусти сервер или /donate reload");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Игроки указывают ник в комментарии: " + ChatColor.WHITE + "ник PlayerName");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vkchat.donate.admin")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            plugin.reloadConfig();
            plugin.getDonateManager().shutdown();
            // Пересоздаётся при следующем onEnable... проще перезапустить плагин
            sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен. Перезапусти сервер для применения API-токена.");
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

        sender.sendMessage(plugin.getDonateManager().getSetupInfo());
        return true;
    }
}
