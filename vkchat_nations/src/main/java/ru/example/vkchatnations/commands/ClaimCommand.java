package ru.example.vkchatnations.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;

public class ClaimCommand implements CommandExecutor {
    private final VKChatNationsPlugin plugin;

    public ClaimCommand(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            p.sendMessage(ChatColor.GOLD + "/claim home " + ChatColor.GRAY + "— телепорт к дому привата");
            return true;
        }
        if (args[0].equalsIgnoreCase("home")) {
            ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
            if (claim == null) {
                claim = plugin.getNationManager().getClaimsByOwner(p.getUniqueId()).stream().findFirst().orElse(null);
            }
            if (claim == null) {
                p.sendMessage(ChatColor.RED + "У вас нет привата.");
                return true;
            }
            if (!claim.hasHome()) {
                p.sendMessage(ChatColor.RED + "Точка дома не установлена. Установи в меню привата.");
                return true;
            }
            p.teleport(new Location(Bukkit.getWorld(claim.getWorldName()), claim.getHomeX(), claim.getHomeY(), claim.getHomeZ()));
            p.sendMessage(ChatColor.GREEN + "♲ Телепорт к дому привата.");
            return true;
        }
        p.sendMessage(ChatColor.RED + "Неизвестная команда. /claim home");
        return true;
    }
}
