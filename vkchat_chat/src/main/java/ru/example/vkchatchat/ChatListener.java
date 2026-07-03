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
    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final Set<UUID> muted = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ChatListener(VKChatChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) return;
        e.setCancelled(true); // Берём управление полностью

        Player p = e.getPlayer();
        String msg = e.getMessage();

        // Антиспам
        if (!checkAntiSpam(p, msg)) return;

        // Канал игрока
        String channel = playerChannels.getOrDefault(p.getUniqueId(),
                plugin.getConfig().getString("channel-default", "global"));

        // Форматирование
        String prefix = getPrefix(p);
        String formatted = plugin.getConfig().getString("format", "{prefix}&r &7%player%&7: &f{message}")
                .replace("{prefix}", prefix)
                .replace("{player}", p.getName())
                .replace("{suffix}", "")
                .replace("{message}", msg);
        formatted = ChatColor.translateAlternateColorCodes('&', formatted);

        String channelSymbol = plugin.getConfig().getString("channels." + channel + ".symbol", "");
        int radius = plugin.getConfig().getInt("channels." + channel + ".radius", 0);
        String channelPerm = plugin.getConfig().getString("channels." + channel + ".permission", "");

        // Проверка прав на канал
        if (!channelPerm.isEmpty() && !p.hasPermission(channelPerm)) {
            p.sendMessage(ChatColor.RED + "Нет доступа к каналу. Переключён на глобальный.");
            channel = "global";
            channelSymbol = plugin.getConfig().getString("channels.global.symbol", "&7[&6Г&7]");
            radius = 0;
            playerChannels.put(p.getUniqueId(), "global");
        }

        String fullMsg = ChatColor.translateAlternateColorCodes('&', channelSymbol + " ") + formatted;

        // Отправка: по радиусу или всем
        if (radius > 0) {
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (recipient.getWorld().equals(p.getWorld())
                        && recipient.getLocation().distance(p.getLocation()) <= radius) {
                    recipient.sendMessage(fullMsg);
                }
            }
            // Отправитель всегда видит своё
            p.sendMessage(fullMsg);
        } else {
            // Глобальный — всем
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                recipient.sendMessage(fullMsg);
            }
        }

        // ↕ Пересылка в ВК (глобальный канал)
        if (channel.equals("global") && plugin.getConfig().getBoolean("vk.mc-to-vk", true)) {
            try {
                String vkFormat = plugin.getConfig().getString("vk.format-to-vk", "{player}: {message}")
                        .replace("{player}", p.getName()).replace("{message}", msg);
                VKChatPlugin.getInstance().getApi().sendToMainChat(vkFormat);
            } catch (Exception ignored) {}
        }
    }

    private boolean checkAntiSpam(Player p, String msg) {
        if (!plugin.getConfig().getBoolean("anti-spam.enabled", true)) return true;

        // Мут
        if (muted.contains(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Вы замучены.");
            return false;
        }

        // Кулдаун
        int cd = plugin.getConfig().getInt("anti-spam.cooldown-seconds", 2);
        Long last = lastChatTime.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cd * 1000L) {
            p.sendMessage(ChatColor.RED + "Не спамь! Подожди " + cd + " сек.");
            return false;
        }
        lastChatTime.put(p.getUniqueId(), System.currentTimeMillis());

        // Капс
        if (msg.length() > 5) {
            int caps = 0;
            for (char c : msg.toCharArray()) if (Character.isUpperCase(c) || Character.UnicodeBlock.of(c) != Character.UnicodeBlock.CYRILLIC) {
                if (Character.isUpperCase(c)) caps++;
            }
            int maxCaps = plugin.getConfig().getInt("anti-spam.max-caps-percent", 70);
            if (caps * 100 / msg.length() > maxCaps) {
                p.sendMessage(ChatColor.RED + "Слишком много заглавных букв!");
                return false;
            }
        }

        // Повторяющиеся символы
        int maxRepeated = plugin.getConfig().getInt("anti-spam.max-repeated-chars", 5);
        char prev = 0;
        int count = 0;
        for (char c : msg.toCharArray()) {
            if (c == prev) count++;
            else { prev = c; count = 1; }
            if (count > maxRepeated) {
                p.sendMessage(ChatColor.RED + "Слишком много повторяющихся символов!");
                return false;
            }
        }

        return true;
    }

    private String getPrefix(Player p) {
        // LuckPerms via Vault
        try {
            net.milkbowl.vault.chat.Chat vaultChat = Bukkit.getServicesManager()
                    .getRegistration(net.milkbowl.vault.chat.Chat.class).getProvider();
            if (vaultChat != null) {
                String prefix = vaultChat.getPlayerPrefix(p);
                if (prefix != null && !prefix.isEmpty()) return prefix;
            }
        } catch (Exception ignored) {}
        // Запасной вариант: LP permissions
        if (p.hasPermission("vkchat.donate.overlord")) return "&d&lВЛАСТЕЛИН&r";
        if (p.hasPermission("vkchat.donate.legend")) return "&5&lЛЕГЕНДА&r";
        if (p.hasPermission("vkchat.donate.star")) return "&e&lЗВЕЗДА&r";
        if (p.hasPermission("vkchat.donate.flame")) return "&6&lПЛАМЯ&r";
        if (p.hasPermission("vkchat.donate.spark")) return "&b&lИСКРА&r";
        if (p.isOp()) return "&c&lADMIN&r";
        return "&7";
    }

    // Публичные методы для команд
    public void setChannel(Player p, String channel) {
        if (plugin.getConfig().contains("channels." + channel)) {
            playerChannels.put(p.getUniqueId(), channel);
            String symbol = plugin.getConfig().getString("channels." + channel + ".symbol", "");
            p.sendMessage(ChatColor.GREEN + "Канал: " + ChatColor.translateAlternateColorCodes('&', symbol));
        } else {
            p.sendMessage(ChatColor.RED + "Канал не найден: " + channel);
            p.sendMessage(ChatColor.GRAY + "Доступные: " + String.join(", ",
                    plugin.getConfig().getConfigurationSection("channels").getKeys(false)));
        }
    }

    public String getChannel(Player p) {
        return playerChannels.getOrDefault(p.getUniqueId(), "global");
    }

    public void toggleMute(Player target) {
        if (muted.contains(target.getUniqueId())) {
            muted.remove(target.getUniqueId());
        } else {
            muted.add(target.getUniqueId());
        }
    }

    public boolean isMuted(Player p) {
        return muted.contains(p.getUniqueId());
    }

    /**
     * Принять сообщение из ВК и отправить в Minecraft чат
     */
    public void onVkMessage(String playerName, String message) {
        if (!plugin.getConfig().getBoolean("vk.vk-to-mc", true)) return;
        String format = plugin.getConfig().getString("vk.format-to-mc", "&b[ВК] {player}&7: &f{message}");
        String msg = ChatColor.translateAlternateColorCodes('&',
                format.replace("{player}", playerName).replace("{message}", message));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }
}
