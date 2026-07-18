package ru.example.vkchatmarket.playerShop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShopGui {
    private static final int SHOP_SIZE = 27;
    private static final int CONFIRM_SIZE = 27;

    public static void openShopView(VKChatMarketPlugin plugin, Player player, PlayerShop shop) {
        Inventory inv = Bukkit.createInventory(null, SHOP_SIZE,
                "§8▸ §6[ТОРГОВЕЦ] §8◂ §7" + shop.getOwnerName());

        for (int i = 0; i < SHOP_SIZE; i++) {
            inv.setItem(i, bg());
        }

        int slot = 0;
        for (ItemStack item : shop.getItems()) {
            if (slot >= 9) break;
            if (item != null && item.getType() != Material.AIR) {
                inv.setItem(slot, makeShopItem(item));
            }
            slot++;
        }

        boolean isOwner = shop.getOwnerUuid().equals(player.getUniqueId());
        if (isOwner) {
            inv.setItem(22, item(Material.CHEST, "§aУправление",
                    "§7Кликни по пустому слоту",
                    "§7с предметом в руке, чтобы",
                    "§7выставить его на продажу.",
                    "",
                    "§7Shift+ПКМ по предмету — убрать."));
        } else {
            int rep = getRep(player);
            int price = shop.getPrice();
            int commission = (int) (price * plugin.getPlayerShopManager().getCommissionPercent() / 100.0);
            int total = price + commission;
            inv.setItem(22, item(Material.GOLD_INGOT, "§eЦена: " + price + " реп. + комиссия " + commission + " реп.",
                    "§7Итого: §e" + total + " реп.",
                    "",
                    "§7Твой баланс: §e" + rep + " реп."));
        }

        inv.setItem(26, item(Material.BARRIER, "§c✕ Закрыть"));

        plugin.getShopListener().setViewingShop(player.getUniqueId(), shop);
        player.openInventory(inv);
    }

    public static void openConfirmBuy(VKChatMarketPlugin plugin, Player player, PlayerShop shop, int itemSlot, ItemStack itemToBuy) {
        Inventory inv = Bukkit.createInventory(null, CONFIRM_SIZE,
                "§8▸ §6[ТОРГОВЕЦ] §8◂ §eПодтверждение");

        for (int i = 0; i < CONFIRM_SIZE; i++) {
            inv.setItem(i, bg());
        }

        ItemStack display = itemToBuy.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) lore.addAll(meta.getLore());
            int price = shop.getPrice();
            int commission = (int) (price * plugin.getPlayerShopManager().getCommissionPercent() / 100.0);
            int total = price + commission;
            lore.add("");
            lore.add("§7Цена: §e" + price + " реп.");
            lore.add("§7Комиссия: §e" + commission + " реп.");
            lore.add("§7Итого: §e" + total + " реп.");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }

        inv.setItem(13, display);

        ItemStack confirm = item(Material.EMERALD_BLOCK, "§a§lКупить",
                "§7Нажми для подтверждения");
        ItemMeta cm = confirm.getItemMeta();
        cm.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_confirm_buy"), PersistentDataType.STRING,
                shop.locationKey() + "|" + itemSlot);
        confirm.setItemMeta(cm);
        inv.setItem(11, confirm);

        ItemStack cancel = item(Material.REDSTONE_BLOCK, "§c§lОтмена");
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_cancel_buy"), PersistentDataType.BYTE, (byte) 1);
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        player.openInventory(inv);
    }

    private static ItemStack makeShopItem(ItemStack original) {
        ItemStack copy = original.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§e▶ Нажми, чтобы купить");
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    static ItemStack item(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(java.util.Arrays.asList(lore));
            i.setItemMeta(m);
        }
        return i;
    }

    static ItemStack bg() {
        ItemStack i = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        if (m != null) { m.setDisplayName(" "); i.setItemMeta(m); }
        return i;
    }

    private static int getRep(Player p) {
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId != -1) return VKChatBridge.getReputation(vkId);
        if (VKChatBridge.hasPass(p)) return VKChatBridge.getLocalReputation(p);
        return 0;
    }
}
