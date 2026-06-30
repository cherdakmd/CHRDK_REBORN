package ru.example.vkchatoffline.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.managers.AdventureManager;

/**
 * Слушатель офлайн-событий
 * Обрабатывает VK команды и сообщения
 */
public class OfflineListener implements Listener {
    private final VKChatOfflinePlugin plugin;

    public OfflineListener(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Перезагрузка конфига
    }

    private AdventureManager getAdventureManager() {
        return plugin.getAdventureManager();
    }

    /**
     * Обработка VK сообщений (только в ЛС)
     */
    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        if (e.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("enabled", true)) return;

        int peer = e.getPeer();
        int sender = e.getSenderId();
        String msg = (e.getMessage() == null ? "" : e.getMessage().toLowerCase().trim());
        String[] args = msg.split(" ");

        // Команда смены
        if (msg.startsWith("!смена")) {
            plugin.getShiftManager().handleCommand(peer, sender, null, args);
            e.setCancelled(true);
            return;
        }
    }

    /**
     * Обработка VK команд (с !)
     */
    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("enabled", true)) return;

        String cmd = e.getCommand().toLowerCase();
        int peer = e.getPeerId();
        int sender = e.getSenderVkId();

        // Команда стеша
        if (cmd.equals("!тайник") || cmd.equals("!stash")) {
            plugin.getStashManager().showStashMenu(sender);
            e.setCancelled(true);
            return;
        }

        // Команда смены
        if (cmd.equals("!смена")) {
            plugin.getShiftManager().handleCommand(peer, sender, null, e.getArgs());
            e.setCancelled(true);
            return;
        }
    }

    /**
     * При входе игрока на сервер
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Проверяем ожидающие награды
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                // Выдать ожидающие награды
                var rewards = plugin.getRewardManager().getPendingRewards(vkId);
                if (rewards != null && !rewards.isEmpty()) {
                    for (var item : rewards) {
                        p.getInventory().addItem(item);
                    }
                    p.sendMessage("§a🎁 Ты получил награды из офлайн-похода!");
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * При выходе игрока с сервера
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        // Сохраняем данные при выходе
        plugin.getAdventureManager().saveAll();
    }
}
