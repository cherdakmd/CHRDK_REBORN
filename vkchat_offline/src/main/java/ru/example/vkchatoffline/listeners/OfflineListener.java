package ru.example.vkchatoffline.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import java.util.*;

public class OfflineListener implements Listener {
    private final VKChatOfflinePlugin plugin;
    public OfflineListener(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.isCancelled()) return;
        String cmd = e.getCommand().toLowerCase();
        int peer = e.getPeerId();
        int sender = e.getSenderVkId();

        if (cmd.equals("!смена")) {
            plugin.getShiftManager().handleCommand(peer, sender, null, e.getArgs());
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1 && plugin.getRewardManager().hasPendingRewards(vkId)) {
                List<ItemStack> rewards = plugin.getRewardManager().getPendingRewards(vkId);
                for (ItemStack item : rewards) p.getInventory().addItem(item);
                p.sendMessage("§a🎁 Награды из похода получены!");
            }
        } catch (Exception ignored) {}
    }
}
