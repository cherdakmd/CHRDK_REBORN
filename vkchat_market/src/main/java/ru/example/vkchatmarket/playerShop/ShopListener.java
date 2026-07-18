package ru.example.vkchatmarket.playerShop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShopListener implements Listener {
    private final VKChatMarketPlugin plugin;
    private final NamespacedKey confirmBuyKey;
    private final NamespacedKey cancelBuyKey;
    private final Map<UUID, PlayerShop> viewingShop = new ConcurrentHashMap<>();

    public ShopListener(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.confirmBuyKey = new NamespacedKey(plugin, "shop_confirm_buy");
        this.cancelBuyKey = new NamespacedKey(plugin, "shop_cancel_buy");
    }

    public void setViewingShop(UUID playerUuid, PlayerShop shop) {
        if (shop != null) {
            viewingShop.put(playerUuid, shop);
        } else {
            viewingShop.remove(playerUuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        if (!plugin.getPlayerShopManager().isShopBlock(block)) return;
        e.setCancelled(true);

        Player player = e.getPlayer();
        PlayerShop shop = plugin.getPlayerShopManager().getShopAt(block);
        if (shop == null) {
            player.sendMessage("§cМагазин не найден в данных.");
            return;
        }
        ShopGui.openShopView(plugin, player, shop);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.contains("[ТОРГОВЕЦ]")) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        ItemStack current = e.getCurrentItem();
        if (current == null) return;

        // Confirm buy
        if (current.hasItemMeta()) {
            ItemMeta meta = current.getItemMeta();
            if (meta.getPersistentDataContainer().has(confirmBuyKey, PersistentDataType.STRING)) {
                e.setCancelled(true);
                handleConfirmBuy(player, meta);
                return;
            }
            if (meta.getPersistentDataContainer().has(cancelBuyKey, PersistentDataType.BYTE)) {
                e.setCancelled(true);
                PlayerShop shop = viewingShop.get(player.getUniqueId());
                if (shop != null) {
                    ShopGui.openShopView(plugin, player, shop);
                } else {
                    player.closeInventory();
                }
                return;
            }
        }

        // If it's a confirm dialog, cancel all clicks on other items
        if (title.contains("Подтверждение")) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        PlayerShop shop = viewingShop.get(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§cОшибка: магазин не найден.");
            player.closeInventory();
            return;
        }

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 9) return; // Only slots 0-8 are item slots

        boolean isOwner = shop.getOwnerUuid().equals(player.getUniqueId());

        if (isOwner && current.getType() == Material.AIR) {
            // Owner clicked empty slot - place held item
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                player.sendMessage("§cВозьми предмет в руку, чтобы выставить его на продажу.");
                return;
            }
            if (shop.getItems().size() >= plugin.getPlayerShopManager().getMaxItemsPerShop()) {
                player.sendMessage("§cМаксимум предметов: " + plugin.getPlayerShopManager().getMaxItemsPerShop());
                return;
            }
            ItemStack toAdd = held.clone();
            toAdd.setAmount(1);
            held.setAmount(held.getAmount() - 1);
            if (held.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            }
            shop.getItems().add(toAdd);
            plugin.getPlayerShopManager().save();
            ShopGui.openShopView(plugin, player, shop);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            return;
        }

        if (isOwner && current.getType() != Material.AIR && e.getClick().isShiftClick()) {
            // Owner shift-click to remove item
            int targetSlot = -1;
            for (int i = 0; i < shop.getItems().size() && i < 9; i++) {
                if (i == slot) {
                    targetSlot = i;
                    break;
                }
            }
            if (targetSlot >= 0 && targetSlot < shop.getItems().size()) {
                ItemStack removed = shop.getItems().remove(targetSlot);
                if (removed != null) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(removed);
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    plugin.getPlayerShopManager().save();
                    ShopGui.openShopView(plugin, player, shop);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                }
            }
            return;
        }

        // Buyer clicked on item
        if (!isOwner && current.getType() != Material.AIR) {
            int itemIndex = slot;
            if (itemIndex >= 0 && itemIndex < shop.getItems().size()) {
                ItemStack itemToBuy = shop.getItems().get(itemIndex);
                if (itemToBuy != null && itemToBuy.getType() != Material.AIR) {
                    ShopGui.openConfirmBuy(plugin, player, shop, itemIndex, itemToBuy);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
            }
        }
    }

    private void handleConfirmBuy(Player player, ItemMeta meta) {
        String data = meta.getPersistentDataContainer().get(confirmBuyKey, PersistentDataType.STRING);
        if (data == null) return;
        String[] parts = data.split("\\|", 2);
        if (parts.length < 2) return;
        String locKey = parts[0];
        int itemIndex;
        try {
            itemIndex = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) { return; }

        PlayerShop shop = plugin.getPlayerShopManager().getShopByLocationKey(locKey);
        if (shop == null) {
            player.sendMessage("§cМагазин больше не существует.");
            player.closeInventory();
            return;
        }
        if (itemIndex < 0 || itemIndex >= shop.getItems().size()) {
            player.sendMessage("§cПредмет больше не доступен.");
            player.closeInventory();
            return;
        }
        ItemStack item = shop.getItems().get(itemIndex);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cПредмет больше не доступен.");
            player.closeInventory();
            return;
        }
        if (shop.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§cНельзя купить у самого себя!");
            player.closeInventory();
            return;
        }

        int price = shop.getPrice();
        int commission = (int) (price * plugin.getPlayerShopManager().getCommissionPercent() / 100.0);
        int total = price + commission;

        int vkId = VKChatBridge.getLinkedVkId(player);
        boolean hasVk = vkId != -1;
        boolean hasPass = VKChatBridge.hasPass(player);
        if (!hasVk && !hasPass) {
            player.sendMessage("§c❌ Привяжи ВК: /vklink");
            player.closeInventory();
            return;
        }
        int buyerRep = hasVk ? VKChatBridge.getReputation(vkId) : VKChatBridge.getLocalReputation(player);
        if (buyerRep < total) {
            player.sendMessage("§c❌ Недостаточно репутации! Нужно: §e" + total + " реп.");
            player.closeInventory();
            return;
        }

        // Transfer rep
        if (hasVk) {
            VKChatBridge.takeReputation(vkId, total);
        } else {
            VKChatBridge.takeLocalReputation(player, total);
        }

        int sellerAmount = price;
        Player seller = Bukkit.getPlayer(shop.getOwnerUuid());
        if (seller != null && seller.isOnline()) {
            VKChatBridge.addEffectiveRep(seller, sellerAmount);
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
            seller.sendMessage("§a§lМАГАЗИН §8▸ §f" + player.getName() + " §aкупил §f" + itemName
                    + " §aза §e" + price + " реп. §a(комиссия: " + commission + " реп.)");
        } else {
            // Offline seller - add to VK rep if they have VK linked
            int sellerVk = VKChatBridge.getLinkedVkId(shop.getOwnerUuid());
            if (sellerVk != -1) {
                VKChatBridge.addPoints(sellerVk, sellerAmount);
            }
        }

        // Give item to buyer
        ItemStack bought = item.clone();
        shop.getItems().remove(itemIndex);
        plugin.getPlayerShopManager().save();

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(bought);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        String boughtName = bought.hasItemMeta() && bought.getItemMeta().hasDisplayName() ? bought.getItemMeta().getDisplayName() : bought.getType().name();
        player.sendMessage("§a✓ Куплено §f" + boughtName + " §aза §e" + total + " реп.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        String title = e.getView().getTitle();
        if (title.contains("[ТОРГОВЕЦ]")) {
            viewingShop.remove(e.getPlayer().getUniqueId());
        }
    }
}
