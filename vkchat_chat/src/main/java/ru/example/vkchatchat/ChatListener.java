package ru.example.vkchatchat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final VKChatChatPlugin plugin;
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final Set<UUID> muted = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

        String prefix = getPrefix(p);
        String name = p.getName();

        if (msg.startsWith("!")) {
            // Глобальный чат + ВК
            msg = msg.substring(1).trim();
            if (msg.isEmpty()) return;
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    "&7[&6Г&7] " + prefix + "&r &7" + name + "&7: &f" + msg);
            broadcast(formatted);
            sendToVk(name, msg);
        } else if (msg.startsWith("$")) {
            // Торговый чат
            msg = msg.substring(1).trim();
            if (msg.isEmpty()) return;
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    "&7[&eТ&7] " + prefix + "&r &7" + name + "&7: &f" + msg);
            broadcast(formatted);
        } else {
            // Локальный чат (по радиусу)
            String formatted = ChatColor.translateAlternateColorCodes('&',
                    prefix + "&r &7" + name + "&7: &f" + msg);
            int radius = plugin.getConfig().getInt("channels.local.radius", 100);
            for (Player r : Bukkit.getOnlinePlayers()) {
                if (r.getWorld().equals(p.getWorld())
                        && r.getLocation().distance(p.getLocation()) <= radius) {
                    r.sendMessage(formatted);
                }
            }
            p.sendMessage(formatted);
        }
    }

    private void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void sendToVk(String player, String msg) {
        if (!plugin.getConfig().getBoolean("vk.mc-to-vk", true)) return;
        try {
            String vkFormat = plugin.getConfig().getString("vk.format-to-vk", "{player}: {message}")
                    .replace("{player}", player).replace("{message}", msg);
            VKChatPlugin.getInstance().getApi().sendToMainChat(vkFormat);
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
            net.milkbowl.vault.chat.Chat vc = Bukkit.getServicesManager()
                    .getRegistration(net.milkbowl.vault.chat.Chat.class).getProvider();
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
