package ru.example.vkchat.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import ru.example.vkchat.VKChatPlugin;

public class TopBroadcastTask extends BukkitRunnable {
    private final VKChatPlugin plugin;

    public TopBroadcastTask(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String top = plugin.getStatsManager().getTopPlayersString();
        String msg = plugin.getConfigManager().getMessage("vk_cmd_top").replace("{top}", top);
        
        plugin.getServer().broadcastMessage(msg);
        plugin.getVkManager().sendToMainChat(org.bukkit.ChatColor.stripColor(msg));
    }
}