package ru.example.vkchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.example.vkchat.VKChatPlugin;

/**
 * ChatListener — только пересылка сообщений в VK
 * Форматирование чата полностью через EssentialsX
 */
public class ChatListener implements Listener {
    private final VKChatPlugin plugin;

    public ChatListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMonitor(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) return;

        // Если модуль чата загружен — он сам отправляет в VK, не дублируем
        if (Bukkit.getPluginManager().isPluginEnabled("VKChatChat")) return;

        Player p = e.getPlayer();
        String message = e.getMessage();

        // Проверяем начинается ли сообщение с "!" для пересылки в VK
        String globalSymbol = plugin.getConfig().getString("chat.global-symbol", "!");
        if (globalSymbol == null || globalSymbol.trim().isEmpty()) {
            globalSymbol = "!";
        }

        boolean isGlobal = message.startsWith(globalSymbol) || message.startsWith("!");
        if (!isGlobal) return;

        // Вырезаем символ "!" для ВК
        String cleanMsg = message;
        if (cleanMsg.startsWith(globalSymbol)) {
            cleanMsg = cleanMsg.substring(globalSymbol.length()).trim();
        } else if (cleanMsg.startsWith("!")) {
            cleanMsg = cleanMsg.substring(1).trim();
        }

        // Убираем цвета
        final String strippedMsg = ChatColor.stripColor(cleanMsg);

        // Получаем нацию игрока для ВК
        String nation = "";
        try {
            org.bukkit.plugin.Plugin nationsPlugin = Bukkit.getPluginManager().getPlugin("VKChatNations");
            if (nationsPlugin != null && nationsPlugin.isEnabled()) {
                Object nationMgr = nationsPlugin.getClass().getMethod("getNationManager").invoke(nationsPlugin);
                String c = (String) nationMgr.getClass().getMethod("getPlayerNation", org.bukkit.entity.Player.class).invoke(nationMgr, p);
                if (c != null) {
                    String prefix = nationsPlugin.getConfig().getString("nations." + c + ".prefix", "[" + c + "]");
                    if (prefix.contains("[") || prefix.contains("]")) {
                        nation = ChatColor.translateAlternateColorCodes('&', prefix + " ");
                    } else {
                        nation = ChatColor.translateAlternateColorCodes('&', "&8[" + prefix + "&8] ");
                    }
                }
            }
        } catch (Exception ignored) {}

        final String finalNation = ChatColor.stripColor(nation).trim();

        // Отправляем в ВК асинхронно
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int mainChat = plugin.getConfig().getInt("vk.peer-id", 2000000001);
            String prefixText = finalNation.isEmpty() ? "" : finalNation + " ";
            plugin.getVkManager().sendMessage(mainChat, "[Игра] " + prefixText + p.getName() + ": " + strippedMsg);
        });
    }
}
