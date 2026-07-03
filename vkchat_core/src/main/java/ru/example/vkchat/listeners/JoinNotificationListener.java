package ru.example.vkchat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.example.vkchat.VKChatPlugin;

/**
 * Слушатель уведомлений о входе/выходе игроков
 * Отправляет уведомления в ВК чат для ВСЕХ игроков
 */
public class JoinNotificationListener implements Listener {
    private final VKChatPlugin plugin;

    public JoinNotificationListener(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Уведомление о входе игрока
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (Bukkit.getPluginManager().isPluginEnabled("VKChatChat")) return;
        if (!plugin.getConfig().getBoolean("notifications.join.enabled", true)) return;

        Player p = e.getPlayer();

        // Отправляем уведомление асинхронно
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Определяем статус ВК
                String vkStatus = getVkStatus(p);

                // Формируем сообщение
                String message = "🟢 Игрок " + p.getName() + " зашёл на сервер! (ВК: " + vkStatus + ")";

                // Отправляем в ВК
                plugin.getVkManager().sendToMainChat(ChatColor.stripColor(message));
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка отправки уведомления о входе: " + ex.getMessage());
            }
        });
    }

    /**
     * Уведомление о выходе игрока
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (Bukkit.getPluginManager().isPluginEnabled("VKChatChat")) return;
        if (!plugin.getConfig().getBoolean("notifications.leave.enabled", true)) return;

        Player p = e.getPlayer();

        // Отправляем уведомление асинхронно
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Определяем статус ВК
                String vkStatus = getVkStatus(p);

                // Формируем сообщение
                String message = "🔴 Игрок " + p.getName() + " покинул сервер. (ВК: " + vkStatus + ")";

                // Отправляем в ВК
                plugin.getVkManager().sendToMainChat(ChatColor.stripColor(message));
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка отправки уведомления о выходе: " + ex.getMessage());
            }
        });
    }

    /**
     * Получить статус ВК игрока
     */
    private String getVkStatus(Player p) {
        // Проверяем, привязан ли к ВК
        int vkId = plugin.getAuthManager().getLinkedVkId(p);
        if (vkId != -1) {
            return "привязан";
        }

        return "нет ВК";
    }
}
