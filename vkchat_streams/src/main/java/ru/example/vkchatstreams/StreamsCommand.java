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
            sender.sendMessage(ChatColor.GRAY + "/stream reward " + ChatColor.WHITE + "— получить награду за просмотр стрима");
            if (sender.hasPermission("vkchat.streams.admin")) {
                sender.sendMessage(ChatColor.GRAY + "/streams check " + ChatColor.WHITE + "— проверить стримы сейчас");
                sender.sendMessage(ChatColor.GRAY + "/streams reset " + ChatColor.WHITE + "— сбросить уже объявленные");
                sender.sendMessage(ChatColor.GRAY + "/streams reload " + ChatColor.WHITE + "— перезагрузить конфиг");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reward")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Только для игроков.");
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(ChatColor.RED + "Использование: /stream reward <код>");
                p.sendMessage(ChatColor.GRAY + "Код называет стример в эфире!");
                return true;
            }
            plugin.getStreamChecker().claimReward(p, args[1]);
            return true;
        }

        if (!sender.hasPermission("vkchat.streams.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check":
                plugin.getStreamChecker().checkAll();
                sender.sendMessage(ChatColor.GREEN + "✓ Проверка стримов запущена.");
                break;
            case "setcode":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /streams setcode <код>");
                    return true;
                }
                plugin.getStreamChecker().setCurrentCode(args[1]);
                sender.sendMessage(ChatColor.GREEN + "✓ Код награды установлен: " + args[1]);
                break;
            case "code":
                sender.sendMessage(ChatColor.GOLD + "Текущий код: " + plugin.getStreamChecker().getCurrentCode());
                break;
            case "reset":
                plugin.getStreamChecker().resetAnnounced();
                sender.sendMessage(ChatColor.GREEN + "✓ Список объявленных стримов сброшен.");
                break;
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "✓ Конфиг перезагружен.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда.");
        }
        return true;
    }
}
