package ru.example.vkchatoffline.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

public class OfflineListener implements Listener {
    private final VKChatOfflinePlugin plugin;

    public OfflineListener(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.isCancelled()) return;
        String cmd = e.getCommand().toLowerCase();
        int sender = e.getSenderVkId();
        String[] args = e.getArgs();

        if (isAdventureCmd(cmd)) {
            plugin.getAdventureManager().handleCommand(sender, cmd, args);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        if (e.isCancelled() || e.getPeer() != e.getSenderId()) return;
        String msg = e.getMessage() == null ? "" : e.getMessage().trim().toLowerCase();
        if (msg.startsWith("!adv") || msg.startsWith("!поход") || msg.startsWith("!приключение")) return;
        // Текстовые кнопки обрабатываются через VKCommandEvent (payload)
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(e.getPlayer());
            if (vkId != -1 && !plugin.getStashManager().isEmpty(e.getPlayer().getUniqueId())) {
                e.getPlayer().sendMessage("§a🎁 У вас есть награды в тайнике! Используйте /stash");
            }
        } catch (Exception ignored) {}
    }

    private boolean isAdventureCmd(String cmd) {
        return cmd.startsWith("!adv") || cmd.equals("!поход") || cmd.equals("!приключение");
    }
}
