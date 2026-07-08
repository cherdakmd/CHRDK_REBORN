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
            int max = plugin.getNationManager().getMaxClaimsFor(p);
            int cur = plugin.getNationManager().getClaimCount(p.getUniqueId());
            p.sendMessage(ChatColor.GOLD + "/claim home " + ChatColor.GRAY + "— телепорт к дому привата (" + plugin.getConfig().getInt("claim.teleport-cost", 20) + " реп)");
            p.sendMessage(ChatColor.GOLD + "/claim info " + ChatColor.GRAY + "— информация о привате на текущем месте");
            p.sendMessage(ChatColor.GOLD + "/claim limits " + ChatColor.GRAY + "— твой лимит (" + cur + "/" + max + ")");
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
            if (claim == null) {
                p.sendMessage(ChatColor.RED + "Вы не находитесь внутри привата.");
                return true;
            }
            String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
            p.sendMessage(ChatColor.GOLD + "=== Приват ===");
            p.sendMessage(ChatColor.GRAY + "Владелец: " + ChatColor.WHITE + (ownerName != null ? ownerName : "???"));
            p.sendMessage(ChatColor.GRAY + "Название: " + ChatColor.WHITE + claim.getName());
            p.sendMessage(ChatColor.GRAY + "Уровень: " + ChunkClaim.getLevelColor(claim.getLevel()) + claim.getLevel());
            p.sendMessage(ChatColor.GRAY + "Прочность: " + ChatColor.GREEN + claim.getDurability() + "/" + claim.getMaxDurability());
            p.sendMessage(ChatColor.GRAY + "Радиус: " + ChatColor.YELLOW + claim.getRadius() + " блоков");
            int expansions = claim.getExtraRadius() / 3;
            if (expansions > 0) p.sendMessage(ChatColor.GRAY + "Расширений: " + ChatColor.YELLOW + expansions);
            int baseDecay = plugin.getConfig().getInt("claim.durability-decay.base", 2);
            int perExp = plugin.getConfig().getInt("claim.durability-decay.per-expansion", 1);
            int decay = baseDecay + expansions * perExp;
            p.sendMessage(ChatColor.GRAY + "Износ в день: " + ChatColor.RED + "-" + decay);
            p.sendMessage(ChatColor.GRAY + "Автопродление: " + (claim.isAutoPayEnabled() ? ChatColor.GREEN + "ВКЛ" : ChatColor.RED + "ВЫКЛ"));
            return true;
        }
        if (args[0].equalsIgnoreCase("limits")) {
            int max = plugin.getNationManager().getMaxClaimsFor(p);
            int cur = plugin.getNationManager().getClaimCount(p.getUniqueId());
            p.sendMessage(ChatColor.GOLD + "=== Лимит приватов ===");
            p.sendMessage(ChatColor.GRAY + "Установлено: " + ChatColor.WHITE + cur + ChatColor.GRAY + "/" + ChatColor.WHITE + max);
            int nextCost = plugin.getNationManager().getClaimCostFor(p);
            p.sendMessage(ChatColor.GRAY + "Цена следующего: " + ChatColor.GOLD + nextCost + " реп.");
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
            if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
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
            List<String> subs = new ArrayList<>(Arrays.asList("home", "info", "limits"));
            subs.removeIf(s -> !s.startsWith(prefix));
            return subs;
        }
        return new ArrayList<>();
    }
}
