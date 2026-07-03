package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommand implements CommandExecutor {
    private final VKChatChatPlugin plugin;

    public ChatCommand(VKChatChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equals("mute")) {
            if (!sender.hasPermission("vkchat.chat.mute")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "/mute <игрок>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден.");
                return true;
            }
            plugin.getChatListener().toggleMute(target);
            boolean muted = plugin.getChatListener().isMuted(target);
            sender.sendMessage(ChatColor.YELLOW + target.getName() + (muted ? " замучен." : " размучен."));
            target.sendMessage(muted ? ChatColor.RED + "Вы замучены." : ChatColor.GREEN + "Вы размучены.");
            return true;
        }

        // /channel или /chat
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player p = (Player) sender;
        p.sendMessage(ChatColor.GRAY + "Чат по умолчанию — локальный.");
        p.sendMessage(ChatColor.YELLOW + "  !сообщение — глобальный чат");
        p.sendMessage(ChatColor.YELLOW + "  $сообщение — торговый чат");
        p.sendMessage(ChatColor.YELLOW + "  /channel nation — чат нации");
        p.sendMessage(ChatColor.YELLOW + "  текст — локальный чат");
        return true;
    }
}
