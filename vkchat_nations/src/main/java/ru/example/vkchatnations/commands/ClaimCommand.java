package ru.example.vkchatnations.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimCommand implements CommandExecutor, TabCompleter {
    private final VKChatNationsPlugin plugin;
    private final Map<UUID, Long> homeCooldown = new ConcurrentHashMap<>();

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
            p.sendMessage(ChatColor.GOLD + "/claim home " + ChatColor.GRAY + "— телепорт к дому привата (20 реп, кулдаун 5 мин)");
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
            long last = homeCooldown.getOrDefault(p.getUniqueId(), 0L);
            if (System.currentTimeMillis() - last < 300000) {
                long left = 300000 - (System.currentTimeMillis() - last);
                p.sendMessage(ChatColor.RED + "⏳ Кулдаун: " + (left / 60000) + " мин " + ((left % 60000) / 1000) + " сек");
                return true;
            }
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "❌ Привяжите ВК! (/vklink)");
                return true;
            }
            int cost = plugin.getConfig().getInt("claim.teleport-cost", 20);
            if (VKChatPlugin.getInstance().getApi().getReputation(vkId) < cost) {
                p.sendMessage(ChatColor.RED + "❌ Нужно " + cost + " репутации для телепорта к дому.");
                return true;
            }
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            homeCooldown.put(p.getUniqueId(), System.currentTimeMillis());
            World w = Bukkit.getWorld(claim.getWorldName());
            if (w != null) {
                p.teleport(new Location(w, claim.getHomeX(), claim.getHomeY(), claim.getHomeZ()));
            }
            p.sendMessage(ChatColor.GREEN + "♲ Телепорт к дому привата. Списано " + cost + " реп.");
            return true;
        }
        p.sendMessage(ChatColor.RED + "Неизвестная команда. /claim home");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> subs = new ArrayList<>(Arrays.asList("home"));
            subs.removeIf(s -> !s.startsWith(prefix));
            return subs;
        }
        return new ArrayList<>();
    }
}
