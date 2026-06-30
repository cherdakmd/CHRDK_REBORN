package ru.example.vkchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Команда управления проходками
 * /pass give <игрок> <дни> — выдать проходку
 * /pass revoke <игрок> — отозвать проходку
 * /pass list — список активных проходок
 * /pass stats — статистика
 * /pass status — статус проходки игрока
 */
public class PassCommand implements CommandExecutor, TabCompleter {
    private final VKChatPlugin plugin;

    public PassCommand(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give":
                handleGive(sender, args);
                break;
            case "revoke":
            case "remove":
                handleRevoke(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "stats":
                handleStats(sender);
                break;
            case "status":
                handleStatus(sender, args);
                break;
            case "extend":
                handleExtend(sender, args);
                break;
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Показать помощь
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ Управление проходками ═══");
        sender.sendMessage(ChatColor.YELLOW + "/pass give <игрок> <дни>" + ChatColor.GRAY + " — выдать проходку");
        sender.sendMessage(ChatColor.YELLOW + "/pass revoke <игрок>" + ChatColor.GRAY + " — отозвать проходку");
        sender.sendMessage(ChatColor.YELLOW + "/pass extend <игрок> <дни>" + ChatColor.GRAY + " — продлить проходку");
        sender.sendMessage(ChatColor.YELLOW + "/pass status [игрок]" + ChatColor.GRAY + " — статус проходки");
        sender.sendMessage(ChatColor.YELLOW + "/pass list" + ChatColor.GRAY + " — список активных");
        sender.sendMessage(ChatColor.YELLOW + "/pass stats" + ChatColor.GRAY + " — статистика");
    }

    /**
     * Выдать проходку
     */
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vkchat.admin.pass")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /pass give <игрок> <дни>");
            return;
        }

        String playerName = args[1];
        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Неверное количество дней!");
            return;
        }

        if (days <= 0) {
            sender.sendMessage(ChatColor.RED + "Количество дней должно быть больше 0!");
            return;
        }

        // Ищем игрока
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }

        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : playerName;

        // Выдаём проходку
        boolean success = plugin.getPassManager().grantPass(uuid, name, days, "admin");
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✅ Проходка выдана игроку " + name + " на " + days + " дней!");

            // Уведомляем игрока если онлайн
            Player onlineTarget = Bukkit.getPlayer(uuid);
            if (onlineTarget != null) {
                onlineTarget.sendMessage(ChatColor.GREEN + "🎉 Тебе выдана проходка на " + days + " дней!");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Ошибка выдачи проходки!");
        }
    }

    /**
     * Отозвать проходку
     */
    private void handleRevoke(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vkchat.admin.pass")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /pass revoke <игрок>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }

        boolean success = plugin.getPassManager().revokePass(target.getUniqueId());
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✅ Проходка отозвана у игрока " + playerName + "!");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "У игрока нет активной проходки.");
        }
    }

    /**
     * Продлить проходку
     */
    private void handleExtend(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vkchat.admin.pass")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /pass extend <игрок> <дни>");
            return;
        }

        String playerName = args[1];
        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Неверное количество дней!");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден!");
            return;
        }

        if (!plugin.getPassManager().hasPass(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "У игрока нет активной проходки!");
            return;
        }

        boolean success = plugin.getPassManager().grantPass(target.getUniqueId(), playerName, days, "admin");
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✅ Проходка продлена на " + days + " дней!");
        } else {
            sender.sendMessage(ChatColor.RED + "Ошибка продления проходки!");
        }
    }

    /**
     * Показать список активных проходок
     */
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("vkchat.admin.pass")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return;
        }

        sender.sendMessage(plugin.getPassManager().getPassList());
    }

    /**
     * Показать статистику
     */
    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("vkchat.admin.pass.stats")) {
            sender.sendMessage(ChatColor.RED + "Нет прав!");
            return;
        }

        sender.sendMessage(plugin.getPassManager().getStats());
    }

    /**
     * Показать статус проходки
     */
    private void handleStatus(CommandSender sender, String[] args) {
        UUID uuid;

        if (args.length >= 2) {
            // Статус другого игрока
            if (!sender.hasPermission("vkchat.admin.pass")) {
                sender.sendMessage(ChatColor.RED + "Нет прав!");
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден!");
                return;
            }
            uuid = target.getUniqueId();
        } else {
            // Свой статус
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Укажите игрока!");
                return;
            }
            uuid = ((Player) sender).getUniqueId();
        }

        boolean hasPass = plugin.getPassManager().hasPass(uuid);
        if (hasPass) {
            long remaining = plugin.getPassManager().getPassRemainingDays(uuid);
            String expiry = plugin.getPassManager().getPassExpiryDate(uuid);
            sender.sendMessage(ChatColor.GREEN + "✅ Проходка активна!");
            sender.sendMessage(ChatColor.GRAY + "Осталось: " + ChatColor.YELLOW + remaining + " дней");
            sender.sendMessage(ChatColor.GRAY + "Истекает: " + ChatColor.YELLOW + expiry);
        } else {
            sender.sendMessage(ChatColor.RED + "❌ Нет активной проходки.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "revoke", "extend", "status", "list", "stats")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("revoke") || 
            args[0].equalsIgnoreCase("extend") || args[0].equalsIgnoreCase("status"))) {
            return null; // Возвращает список игроков
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("extend"))) {
            return Arrays.asList("7", "14", "30", "60", "90");
        }

        return new ArrayList<>();
    }
}
