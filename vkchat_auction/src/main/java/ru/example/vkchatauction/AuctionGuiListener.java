package ru.example.vkchatauction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AuctionGuiListener implements Listener {

    private final VKChatAuctionPlugin plugin;
    private final Map<UUID, Map<String, Object>> pendingConfirmations = new HashMap<>();

    public AuctionGuiListener(VKChatAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title.startsWith("§8▸ §6§lПОДТВЕРЖДЕНИЕ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            Player player = (Player) event.getWhoClicked();
            if (event.getSlot() == 11) {
                handleConfirmation(player, true);
            } else if (event.getSlot() == 15) {
                handleConfirmation(player, false);
            }
            return;
        }
        if (!title.startsWith("§8▸ §6§lАУКЦИОН") && !title.startsWith("§8▸ §6§lМОИ ЛОТЫ") && !title.startsWith("§8▸ §6§lИСТОРИЯ")) {
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        AuctionManager manager = plugin.getAuctionManager();

        if (title.startsWith("§8▸ §6§lАУКЦИОН")) {
            handleBrowseClick(player, title, slot, event.getCurrentItem(), event.isLeftClick(), event.isRightClick());
        } else if (title.startsWith("§8▸ §6§lМОИ ЛОТЫ")) {
            handleMyClick(player, slot, event.getCurrentItem(), event.isRightClick());
        } else if (title.startsWith("§8▸ §6§lИСТОРИЯ")) {
            if (slot == 49) new AuctionGUI(plugin, player).openMainMenu();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith("§8▸ §6§lАУКЦИОН") || title.startsWith("§8▸ §6§lМОИ ЛОТЫ") || title.startsWith("§8▸ §6§lИСТОРИЯ") || title.startsWith("§8▸ §6§lПОДТВЕРЖДЕНИЕ")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith("§8▸ §6§lПОДТВЕРЖДЕНИЕ")) {
            pendingConfirmations.remove(event.getPlayer().getUniqueId());
        }
    }

    private void handleConfirmation(Player player, boolean accepted) {
        Map<String, Object> props = pendingConfirmations.remove(player.getUniqueId());
        if (props == null) return;
        String actionType = (String) props.get("actionType");
        UUID auctionId = UUID.fromString((String) props.get("auctionId"));
        double amount = (double) props.get("amount");

        if (!accepted) {
            player.sendMessage("§c✗ Действие отменено.");
            new AuctionGUI(plugin, player).openMainMenu();
            return;
        }
        AuctionManager manager = plugin.getAuctionManager();
        if ("bid".equals(actionType)) {
            AuctionManager.AuctionResult result = manager.placeBid(player, auctionId, amount);
            player.sendMessage(result.getMessage());
        } else if ("buy".equals(actionType)) {
            AuctionManager.AuctionResult result = manager.buyItNow(player, auctionId);
            if (!result.getMessage().isEmpty()) player.sendMessage(result.getMessage());
        }
        new AuctionGUI(plugin, player).openMainMenu();
    }

    private void handleBrowseClick(Player player, String title, int slot, ItemStack current, boolean left, boolean right) {
        AuctionManager manager = plugin.getAuctionManager();
        List<Auction> active = manager.getActiveAuctions();
        int maxPerPage = 45;

        int currentPage = 0;
        if (title.contains("Стр.")) {
            try {
                String pageStr = title.split("Стр\\.")[1].split("/")[0].trim();
                currentPage = Integer.parseInt(pageStr) - 1;
            } catch (Exception ignored) {}
        }
        if (slot == 45 && current.getType() == Material.ARROW) {
            new AuctionGUI(plugin, player).openBrowsePage(currentPage - 1);
            return;
        }
        if (slot == 53 && current.getType() == Material.ARROW) {
            new AuctionGUI(plugin, player).openBrowsePage(currentPage + 1);
            return;
        }
        if (slot == 49) { new AuctionGUI(plugin, player).openMyAuctions(); return; }
        if (slot == 50) { new AuctionGUI(plugin, player).openHistory(); return; }
        if (slot == 48) { new AuctionGUI(plugin, player).openCreateMenu(); return; }
        if (slot == 51) { manager.collectPending(player); return; }

        int index = currentPage * maxPerPage + slot;
        if (index >= 0 && index < active.size()) {
            Auction auction = active.get(index);
            if (right && auction.hasBuyItNow()) {
                Map<String, Object> props = new HashMap<>();
                props.put("actionType", "buy");
                props.put("auctionId", auction.getId().toString());
                props.put("amount", auction.getBuyItNow());
                pendingConfirmations.put(player.getUniqueId(), props);
                new AuctionGUI(plugin, player).openConfirmMenu(
                    auction.getItemStack(), "§dВыкуп", auction.getBuyItNow(), auction.getId());
            } else {
                player.closeInventory();
                double minBid = auction.getMinimumBid();
                player.sendMessage("§6═══ Ставка на лот ═══");
                player.sendMessage("§fПредмет: §e" + AuctionManager.formatItemName(auction.getItemStack()));
                player.sendMessage("§fТекущая ставка: §e" + (auction.hasBids() ? Math.round(auction.getCurrentBid()) : "0") + " реп.");
                player.sendMessage("§fМинимальная ставка: §e" + Math.round(minBid) + " реп.");
                player.sendMessage(" ");
                player.sendMessage("§fНапиши: §e/ah bid " + auction.getId().toString().substring(0, 8) + " <сумма>");
            }
        }
    }

    private void handleMyClick(Player player, int slot, ItemStack current, boolean rightClick) {
        AuctionManager manager = plugin.getAuctionManager();
        if (slot == 49 && current.getType() == Material.BARRIER) {
            new AuctionGUI(plugin, player).openMainMenu();
            return;
        }
        if (slot >= 0 && slot < 45 && rightClick) {
            List<Auction> myAuctions = manager.getMyActiveAuctions(player.getUniqueId());
            if (slot < myAuctions.size()) {
                Auction auction = myAuctions.get(slot);
                AuctionManager.AuctionResult result = manager.cancelAuction(player, auction.getId());
                player.sendMessage(result.getMessage());
                new AuctionGUI(plugin, player).openMyAuctions();
            }
        }
    }
}
