package ru.example.vkchatauction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AuctionGUI {

    private final VKChatAuctionPlugin plugin;
    private final Player player;

    public AuctionGUI(VKChatAuctionPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void openMainMenu() {
        openBrowsePage(0);
    }

    public void openBrowsePage(int page) {
        List<Auction> active = plugin.getAuctionManager().getActiveAuctions();
        int maxPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil(active.size() / (double) maxPerPage));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(null, 54,
            "§8▸ §6§lАУКЦИОН §8◂ §7Стр. " + (page + 1) + "/" + totalPages);

        int start = page * maxPerPage;
        int end = Math.min(start + maxPerPage, active.size());
        for (int i = start; i < end; i++) {
            Auction auction = active.get(i);
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§6▸ Старт: §e" + Math.round(auction.getStartPrice()) + " реп.");
            if (auction.hasBids()) {
                lore.add("§6▸ Текущая: §e" + Math.round(auction.getCurrentBid()) + " реп.");
                lore.add("§6▸ Игрок: §f" + auction.getHighestBidderName());
            } else {
                lore.add("§7▸ Ставок пока нет");
            }
            if (auction.hasBuyItNow()) {
                lore.add("§6▸ Выкуп: §e" + Math.round(auction.getBuyItNow()) + " реп.");
            }
            lore.add("§6▸ Продавец: §f" + auction.getSellerName());
            lore.add("§6▸ Осталось: §f" + formatTime(auction.getTimeLeftMs()));
            List<Auction.BidEntry> history = auction.getBidHistory();
            if (!history.isEmpty()) {
                lore.add("");
                lore.add("§7┌ История ставок:");
                int startIdx = Math.max(0, history.size() - 5);
                for (int j = history.size() - 1; j >= startIdx; j--) {
                    Auction.BidEntry e = history.get(j);
                    lore.add("§7│ §f" + e.bidderName + " §7— §e" + Math.round(e.amount) + " реп.");
                }
                lore.add("§7└");
            }
            lore.add("");
            lore.add("§aЛКМ — сделать ставку");
            if (auction.hasBuyItNow()) {
                lore.add("§dПКМ — выкупить за §e" + Math.round(auction.getBuyItNow()) + " реп.");
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(i - start, display);
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createSpacer());
        }
        if (page > 0) inv.setItem(45, createItem(Material.ARROW, "§f← Предыдущая страница"));
        if (page < totalPages - 1) inv.setItem(53, createItem(Material.ARROW, "§fСледующая страница →"));
        inv.setItem(49, createItem(Material.BOOK, "§6§lМОИ ЛОТЫ", "§7Список твоих активных лотов"));
        inv.setItem(50, createItem(Material.CLOCK, "§6§lИСТОРИЯ", "§7Завершённые и истёкшие лоты"));
        inv.setItem(48, createItem(Material.CHEST, "§6§lСОЗДАТЬ ЛОТ", "§7Создать новый аукцион"));
        inv.setItem(51, createItem(Material.GOLD_NUGGET, "§6§lЗАБРАТЬ", "§7Забрать выплаты и предметы"));

        player.openInventory(inv);
    }

    public void openMyAuctions() {
        List<Auction> myAuctions = plugin.getAuctionManager().getMyActiveAuctions(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lМОИ ЛОТЫ §8◂ §7" + myAuctions.size());

        for (int i = 0; i < Math.min(myAuctions.size(), 45); i++) {
            Auction auction = myAuctions.get(i);
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§6▸ ID: §7" + auction.getId().toString().substring(0, 8) + "...");
            lore.add("§6▸ Старт: §e" + Math.round(auction.getStartPrice()) + " реп.");
            if (auction.hasBids()) {
                lore.add("§6▸ Ставка: §e" + Math.round(auction.getCurrentBid()) + " реп. §7(" + auction.getHighestBidderName() + ")");
            } else {
                lore.add("§7▸ Ставок нет");
            }
            if (auction.hasBuyItNow()) {
                lore.add("§6▸ Выкуп: §e" + Math.round(auction.getBuyItNow()) + " реп.");
            }
            lore.add("§6▸ Осталось: §f" + formatTime(auction.getTimeLeftMs()));
            lore.add("");
            lore.add("§cПКМ — отменить лот");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, createSpacer());
        inv.setItem(49, createItem(Material.BARRIER, "§f← Назад"));

        player.openInventory(inv);
    }

    public void openHistory() {
        List<Auction> history = plugin.getAuctionManager().getAllCompletedOrExpired(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8▸ §6§lИСТОРИЯ §8◂ §7" + history.size());

        for (int i = 0; i < Math.min(history.size(), 45); i++) {
            Auction auction = history.get(i);
            ItemStack display = auction.getItemStack().clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            String statusStr;
            switch (auction.getStatus()) {
                case COMPLETED: statusStr = "§a✓ Продан/Куплен"; break;
                case EXPIRED: statusStr = "§e⌛ Истёк"; break;
                case CANCELLED: statusStr = "§c✗ Отменён"; break;
                default: statusStr = "§7" + auction.getStatus();
            }
            lore.add("§6▸ Статус: " + statusStr);
            if (auction.hasBids()) {
                lore.add("§6▸ Финальная цена: §e" + Math.round(auction.getCurrentBid()) + " реп.");
                lore.add("§6▸ Покупатель: §f" + auction.getHighestBidderName());
            }
            if (auction.getSeller().equals(player.getUniqueId())) {
                lore.add("§6▸ Роль: §aПродавец");
            } else {
                lore.add("§6▸ Роль: §bПокупатель");
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, createSpacer());
        inv.setItem(49, createItem(Material.BARRIER, "§f← Назад"));

        player.openInventory(inv);
    }

    public void openCreateMenu() {
        player.closeInventory();
        player.sendMessage("§6═══ Создание аукциона ═══");
        player.sendMessage("§f1. §7Возьми предмет в руку");
        player.sendMessage("§f2. §7Напиши: §e/ah sell <цена> [выкуп]");
        player.sendMessage("§f   §e<цена> §7— стартовая цена (мин. " + plugin.getAuctionManager().getMinPrice() + ")");
        player.sendMessage("§f   §e[выкуп] §7— цена выкупа (опционально)");
    }

    public void openConfirmMenu(ItemStack item, String actionType, double amount, UUID auctionId) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8▸ §6§lПОДТВЕРЖДЕНИЕ §8◂");
        for (int i = 0; i < 27; i++) inv.setItem(i, createSpacer());

        ItemStack confirm = createItem(Material.LIME_WOOL, "§a§l✓ ПОДТВЕРДИТЬ");
        ItemStack cancel = createItem(Material.RED_WOOL, "§c§l✗ ОТМЕНА");
        ItemStack info = item.clone();
        ItemMeta meta = info.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add("");
        lore.add("§6▸ Действие: " + actionType);
        lore.add("§6▸ Цена: §e" + Math.round(amount) + " реп.");
        meta.setLore(lore);
        info.setItemMeta(meta);

        inv.setItem(11, confirm);
        inv.setItem(13, info);
        inv.setItem(15, cancel);

        Map<String, Object> props = new HashMap<>();
        props.put("actionType", actionType);
        props.put("auctionId", auctionId.toString());
        props.put("amount", amount);

        player.openInventory(inv);
    }

    public static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        sb.append(minutes).append("м");
        return sb.toString().trim();
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (loreLines.length > 0) meta.setLore(Arrays.asList(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSpacer() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
