package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ChatCommand implements CommandExecutor, TabCompleter {
    private final VKChatChatPlugin plugin;

    public ChatCommand(VKChatChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("mute")) {
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

        if (cmdName.equals("msg") || cmdName.equals("tell") || cmdName.equals("w") || cmdName.equals("m")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "/msg <игрок> <сообщение>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден.");
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(args[i]);
            }
            String message = sb.toString();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7[&6Я → " + target.getName() + "&7] &f" + message));
            target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7[&6" + sender.getName() + " → Я&7] &f" + message));
            return true;
        }

        if (cmdName.equals("ignore")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Только для игроков.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "/ignore <игрок>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден.");
                return true;
            }
            plugin.getChatListener().toggleIgnore((Player) sender, target);
            boolean ignored = plugin.getChatListener().isIgnored((Player) sender, target);
            sender.sendMessage(ChatColor.YELLOW + "Вы " + (ignored ? "теперь игнорируете" : "больше не игнорируете")
                    + " " + target.getName() + ".");
            return true;
        }

        if (cmdName.equals("cc")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Только для игроков.");
                return true;
            }
            Player p = (Player) sender;
            for (int i = 0; i < 100; i++) p.sendMessage("");
            return true;
        }

        if (cmdName.equals("channel") || cmdName.equals("chat") || cmdName.equals("ch")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только для игроков.");
                    return true;
                }
                Player p = (Player) sender;
                for (int i = 0; i < 100; i++) p.sendMessage("");
                return true;
            }

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

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String cmdName = cmd.getName().toLowerCase();
        if (args.length == 1 && (cmdName.equals("mute") || cmdName.equals("ignore") || cmdName.equals("msg"))) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) names.add(p.getName());
            }
            return names;
        }
        return new ArrayList<>();
    }
}
