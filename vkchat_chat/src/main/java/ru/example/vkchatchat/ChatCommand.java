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
            if (muted) target.sendMessage(ChatColor.RED + "Вы замучены.");
            else target.sendMessage(ChatColor.GREEN + "Вы размучены.");
            return true;
        }

        // /channel
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage(ChatColor.GRAY + "Твой канал: " + plugin.getChatListener().getChannel(p));
            p.sendMessage(ChatColor.YELLOW + "/channel <global|local|trade|nation>");
            return true;
        }

        plugin.getChatListener().setChannel(p, args[0].toLowerCase());
        return true;
    }
}
