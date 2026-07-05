package ru.example.vkchatstreams;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StreamsCommand implements CommandExecutor, TabCompleter {
    private final VKChatStreamsPlugin plugin;

    public StreamsCommand(VKChatStreamsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Команды:");
            sender.sendMessage(ChatColor.GRAY + "/stream reward " + ChatColor.WHITE + "— награда за просмотр стрима");
            sender.sendMessage(ChatColor.GRAY + "/stream list " + ChatColor.WHITE + "— список активных стримов");
            if (sender.hasPermission("vkchat.streams.admin")) {
                sender.sendMessage(ChatColor.GRAY + "/stream start <channel> <title...> <url> " + ChatColor.WHITE + "— ручной анонс");
                sender.sendMessage(ChatColor.GRAY + "/streams check " + ChatColor.WHITE + "— проверить стримы");
                sender.sendMessage(ChatColor.GRAY + "/streams reset " + ChatColor.WHITE + "— сбросить");
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
            sender.sendMessage(ChatColor.GOLD + "=== Активные Twitch стримы ===");
            for (var s : streams) {
                int claimed = plugin.getStreamChecker().getClaimedCount(s.getChannel());
                sender.sendMessage(ChatColor.WHITE + "  \uD83D\uDFE3 " + s.getChannel());
                sender.sendMessage(ChatColor.GRAY + "    " + (s.getTitle() != null ? s.getTitle() : "Без названия"));
                if (!s.getGame().isEmpty()) sender.sendMessage(ChatColor.GRAY + "    Игра: " + s.getGame());
                sender.sendMessage(ChatColor.GRAY + "    Зрителей: " + s.getViewerCount() + " | Идёт: " + s.getUptime());
                sender.sendMessage(ChatColor.GRAY + "    Наград выдано: " + claimed);
                sender.sendMessage(ChatColor.AQUA + "    " + s.getUrl());
            }
            return true;
        }

        if (!sender.hasPermission("vkchat.streams.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "/stream start <channel> <title...> <url>");
                return true;
            }
            String channel = args[1];
            String url = args[args.length - 1];
            String title = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length - 1));
            plugin.getStreamChecker().forceAnnounce(new StreamEvent(channel, title, url, true));
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
                sender.sendMessage(ChatColor.GREEN + "✓ Сброшено.");
                break;
            case "reload":
                plugin.reloadConfig();
                plugin.getStreamChecker().reload();
                plugin.getStreamChecker().restart();
                sender.sendMessage(ChatColor.GREEN + "✓ Конфиг перезагружен.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("reward", "list"));
            if (sender.hasPermission("vkchat.streams.admin")) subs.add("start");
            String prefix = args[0].toLowerCase();
            subs.removeIf(s -> !s.startsWith(prefix));
            return subs;
        }
        return new ArrayList<>();
    }
}
