package ru.example.vkchatstreams;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StreamsCommand implements CommandExecutor {
    private final VKChatStreamsPlugin plugin;

    public StreamsCommand(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Команды:");
            sender.sendMessage(ChatColor.GRAY + "/stream reward " + ChatColor.WHITE + "— награда за подписку на канал");
            sender.sendMessage(ChatColor.GRAY + "/stream list " + ChatColor.WHITE + "— список активных стримов");
            if (sender.hasPermission("vkchat.streams.admin")) {
                sender.sendMessage(ChatColor.GRAY + "/stream start <platform> <channel> <title> <url> " + ChatColor.WHITE + "— ручной анонс стрима");
                sender.sendMessage(ChatColor.GRAY + "/streams check " + ChatColor.WHITE + "— проверить стримы сейчас");
                sender.sendMessage(ChatColor.GRAY + "/streams reset " + ChatColor.WHITE + "— сбросить объявленные");
                sender.sendMessage(ChatColor.GRAY + "/streams reload " + ChatColor.WHITE + "— перезагрузить конфиг");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reward")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Только для игроков.");
                return true;
            }
            plugin.getStreamChecker().claimReward(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            var streams = plugin.getStreamChecker().getLiveStreams();
            if (streams.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "=== Активные стримы ===");
            for (var s : streams) {
                sender.sendMessage(ChatColor.WHITE + "  " + platformEmoji(s.getPlatform()) + " " + s.getPlatform() + ChatColor.GRAY + " | " + ChatColor.WHITE + s.getChannel());
                sender.sendMessage(ChatColor.GRAY + "    " + (s.getTitle() != null ? s.getTitle() : "Без названия"));
                sender.sendMessage(ChatColor.AQUA + "    " + s.getUrl());
            }
            return true;
        }

        if (!sender.hasPermission("vkchat.streams.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (args.length < 5) {
                sender.sendMessage(ChatColor.RED + "Использование: /stream start <platform> <channel> <title> <url>");
                return true;
            }
            String platform = args[1];
            String channel = args[2];
            String title = args[3];
            String url = args[4];
            StreamEvent e = new StreamEvent(platform, channel, title, url, true);
            plugin.getStreamChecker().forceAnnounce(e);
            sender.sendMessage(ChatColor.GREEN + "✓ Анонс стрима запущен!");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check":
                plugin.getStreamChecker().checkAll();
                sender.sendMessage(ChatColor.GREEN + "✓ Проверка стримов запущена.");
                break;
            case "reset":
                plugin.getStreamChecker().resetAnnounced();
                sender.sendMessage(ChatColor.GREEN + "✓ Список объявленных стримов сброшен.");
                break;
            case "reload":
                plugin.reloadConfig();
                plugin.getStreamChecker().reload();
                sender.sendMessage(ChatColor.GREEN + "✓ Конфиг перезагружен.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда.");
        }
        return true;
    }

    private String platformEmoji(String platform) {
        return switch (platform.toLowerCase()) {
            case "twitch" -> "\uD83D\uDFE3";
            case "youtube" -> "\uD83D\uDD34";
            case "vk", "vkvideo" -> "\uD83D\uDD35";
            default -> "\u26A1";
        };
    }
}
