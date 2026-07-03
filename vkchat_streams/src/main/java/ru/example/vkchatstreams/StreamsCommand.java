package ru.example.vkchatstreams;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StreamsCommand implements CommandExecutor {
    private final VKChatStreamsPlugin plugin;

    public StreamsCommand(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Команды:");
            sender.sendMessage(ChatColor.GRAY + "/streams check " + ChatColor.WHITE + "— проверить стримы сейчас");
            sender.sendMessage(ChatColor.GRAY + "/streams reset " + ChatColor.WHITE + "— сбросить уже объявленные");
            sender.sendMessage(ChatColor.GRAY + "/streams reload " + ChatColor.WHITE + "— перезагрузить конфиг");
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
                sender.sendMessage(ChatColor.GREEN + "✓ Конфиг перезагружен.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда.");
        }
        return true;
    }
}
