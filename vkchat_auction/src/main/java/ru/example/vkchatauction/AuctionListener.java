package ru.example.vkchatauction;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AuctionListener implements Listener {

    private final VKChatAuctionPlugin plugin;

    public AuctionListener(VKChatAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getAuctionManager().getMyActiveAuctions(player.getUniqueId()).forEach(auction -> {
            if (auction.hasBids()) {
                player.sendMessage("§eℹ Твой лот §f" + AuctionManager.formatItemName(auction.getItemStack()) + " §eимеет ставку §f" + Math.round(auction.getCurrentBid()) + " реп.");
            }
        });
        plugin.getAuctionManager().collectPending(player);
    }
}
