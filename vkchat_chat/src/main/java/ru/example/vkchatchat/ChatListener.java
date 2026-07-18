package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.example.vkchat.util.VKChatBridge;
import net.milkbowl.vault.chat.Chat;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatListener implements Listener {
    private final VKChatChatPlugin plugin;
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final Set<UUID> muted = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();

    public ChatListener(VKChatChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        String raw = e.getMessage();
        String msg = raw.trim();

        if (!checkAntiSpam(p, msg)) return;

        if (plugin.getWordFilter().isTempMuted(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Вы замучены за нарушение правил чата.");
            return;
        }

        WordFilter.FilterResult filterResult = plugin.getWordFilter().check(msg);
        if (filterResult != null && filterResult.filtered) {
            String mode = plugin.getWordFilter().getMode();
            switch (mode) {
                case "delete":
                    if (plugin.getWordFilter().isWarnPlayer()) {
                        p.sendMessage(ChatColor.RED + "Ваше сообщение удалено за нецензурную лексику.");
                    }
                    return;
                case "mute":
                    plugin.getWordFilter().tempMute(p.getUniqueId());
                    p.sendMessage(ChatColor.RED + "Вы замучены на " + plugin.getWordFilter().getMuteDuration() + " сек. за нецензурную лексику.");
                    return;
                case "replace":
                default:
                    msg = plugin.getWordFilter().applyFilter(msg);
                    if (plugin.getWordFilter().isWarnPlayer()) {
                        p.sendMessage(ChatColor.YELLOW + "Ваше сообщение было отфильтровано.");
                    }
                    break;
            }
        }

        String prefix = getPrefix(p);
        String name = p.getName();
        String color = getDonorColor(p);

        msg = applyMentions(msg, p, color);

        String ts = plugin.getConfig().getBoolean("timestamps", false)
                ? "&7[" + new SimpleDateFormat("HH:mm").format(new Date()) + "] "
                : "";

        if (msg.startsWith("!")) {
            msg = msg.substring(1).trim();
            if (msg.isEmpty()) return;
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    ts + "&7[&6Г&7] " + prefix + "&r &7" + name + "&7: " + color + msg);
            broadcastFiltered(formatted, p);
            sendToVk(name, msg);
        } else if (msg.startsWith("$")) {
            msg = msg.substring(1).trim();
            if (msg.isEmpty()) return;
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    ts + "&7[&eТ&7] " + prefix + "&r &7" + name + "&7: " + color + msg);
            broadcastFiltered(formatted, p);
        } else {
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    ts + prefix + "&r &7" + name + "&7: " + color + msg);
            int radius = plugin.getConfig().getInt("channels.local.radius", 100);
            double radiusSq = radius * (double) radius;
            for (Player r : Bukkit.getOnlinePlayers()) {
                if (r.getWorld().equals(p.getWorld())
                        && r.getLocation().distanceSquared(p.getLocation()) <= radiusSq) {
                    if (!isIgnoredBy(r, p)) r.sendMessage(formatted);
                }
            }
            if (!isIgnoredBy(p, p)) p.sendMessage(formatted);
        }
    }

    private void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void broadcastFiltered(String msg, Player sender) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isIgnoredBy(p, sender)) p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private String getDonorColor(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return "&d";
        if (p.hasPermission("vkchat.donate.legend")) return "&5";
        if (p.hasPermission("vkchat.donate.star")) return "&e";
        if (p.hasPermission("vkchat.donate.flame")) return "&6";
        if (p.hasPermission("vkchat.donate.spark")) return "&b";
        return "&f";
    }

    private String applyMentions(String msg, Player sender, String color) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(sender)) continue;
            String name = target.getName();
            if (msg.toLowerCase().contains(name.toLowerCase())) {
                msg = msg.replaceAll("(?i)" + Pattern.quote(name),
                        "&e" + name + "&r" + color);
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
        return msg;
    }

    public void toggleIgnore(Player ignorer, Player target) {
        Set<UUID> set = ignored.computeIfAbsent(ignorer.getUniqueId(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        if (set.contains(target.getUniqueId())) set.remove(target.getUniqueId());
        else set.add(target.getUniqueId());
    }

    public boolean isIgnored(Player ignorer, Player target) {
        Set<UUID> set = ignored.get(ignorer.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    private boolean isIgnoredBy(Player viewer, Player sender) {
        Set<UUID> set = ignored.get(viewer.getUniqueId());
        return set != null && set.contains(sender.getUniqueId());
    }

    private void sendToVk(String player, String msg) {
        if (!plugin.getConfig().getBoolean("vk.mc-to-vk", true)) return;
        try {
            String vkFormat = plugin.getConfig().getString("vk.format-to-vk", "{player}: {message}")
                    .replace("{player}", player).replace("{message}", msg);
            VKChatBridge.sendToMainChat(vkFormat);
        } catch (Exception ignored) {}
    }

    private boolean checkAntiSpam(Player p, String msg) {
        if (!plugin.getConfig().getBoolean("anti-spam.enabled", true)) return true;
        if (muted.contains(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Вы замучены.");
            return false;
        }
        int cd = plugin.getConfig().getInt("anti-spam.cooldown-seconds", 2);
        Long last = lastChatTime.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cd * 1000L) {
            p.sendMessage(ChatColor.RED + "Не спамь! Подожди " + cd + " сек.");
            return false;
        }
        lastChatTime.put(p.getUniqueId(), System.currentTimeMillis());

        if (msg.length() > 5) {
            int caps = 0;
            for (char c : msg.toCharArray()) if (Character.isUpperCase(c)) caps++;
            int maxCaps = plugin.getConfig().getInt("anti-spam.max-caps-percent", 70);
            if (caps * 100 / msg.length() > maxCaps) {
                p.sendMessage(ChatColor.RED + "Слишком много заглавных!");
                return false;
            }
        }
        int maxRep = plugin.getConfig().getInt("anti-spam.max-repeated-chars", 5);
        char prev = 0; int cnt = 0;
        for (char c : msg.toCharArray()) {
            if (c == prev) cnt++; else { prev = c; cnt = 1; }
            if (cnt > maxRep) { p.sendMessage(ChatColor.RED + "Слишком много повторений!"); return false; }
        }
        return true;
    }

    private String getPrefix(Player p) {
        try {
            Chat vc = Bukkit.getServicesManager()
                    .getRegistration(Chat.class).getProvider();
            if (vc != null) {
                String pr = vc.getPlayerPrefix(p);
                if (pr != null && !pr.isEmpty()) return pr;
            }
        } catch (Exception ignored) {}
        if (p.hasPermission("vkchat.donate.overlord")) return "&d&lВЛАСТЕЛИН";
        if (p.hasPermission("vkchat.donate.legend")) return "&5&lЛЕГЕНДА";
        if (p.hasPermission("vkchat.donate.star")) return "&e&lЗВЕЗДА";
        if (p.hasPermission("vkchat.donate.flame")) return "&6&lПЛАМЯ";
        if (p.hasPermission("vkchat.donate.spark")) return "&b&lИСКРА";
        if (p.isOp()) return "&c&lADMIN";
        return "&7";
    }

    public void toggleMute(Player target) {
        if (muted.contains(target.getUniqueId())) muted.remove(target.getUniqueId());
        else muted.add(target.getUniqueId());
    }

    public boolean isMuted(Player p) { return muted.contains(p.getUniqueId()); }

    public void onVkMessage(String playerName, String message) {
        if (!plugin.getConfig().getBoolean("vk.vk-to-mc", true)) return;
        String fmt = plugin.getConfig().getString("vk.format-to-mc", "&b[ВК] {player}&7: &f{message}");
        String msg = ChatColor.translateAlternateColorCodes('&',
                fmt.replace("{player}", playerName).replace("{message}", message));
        broadcast(msg);
    }
}
