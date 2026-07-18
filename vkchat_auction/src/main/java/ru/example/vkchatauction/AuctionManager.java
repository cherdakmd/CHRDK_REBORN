package ru.example.vkchatauction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.util.VKChatBridge;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionManager {

    private final VKChatAuctionPlugin plugin;
    private final Map<UUID, Auction> auctions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCreateTime = new HashMap<>();
    private final Map<UUID, Long> lastCancelTime = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    private int defaultDurationHours;
    private int maxActivePerPlayer;
    private int minPrice;
    private int bidIncrementPercent;
    private double taxPercent;
    private int createCooldownSec;
    private int cancelCooldownSec;
    private int cancelPenalty;
    private Set<String> blacklistMaterials;
    private Set<String> blacklistNames;
    private ConfigurationSection statusPerksConfig;

    public AuctionManager(VKChatAuctionPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        var cfg = plugin.getConfig().getConfigurationSection("auction");
        if (cfg == null) return;
        defaultDurationHours = cfg.getInt("default-duration-hours", 24);
        maxActivePerPlayer = cfg.getInt("max-active-per-player", 6);
        minPrice = cfg.getInt("min-price", 10);
        bidIncrementPercent = cfg.getInt("bid-increment-percent", 10);
        taxPercent = cfg.getDouble("tax-percent", 5);
        createCooldownSec = cfg.getInt("create-cooldown-seconds", 10);
        cancelCooldownSec = cfg.getInt("cancel-cooldown-seconds", 60);
        cancelPenalty = cfg.getInt("cancel-penalty", 500);
        blacklistMaterials = new HashSet<>();
        blacklistNames = new HashSet<>();
        for (String s : cfg.getStringList("blacklist-materials")) {
            blacklistMaterials.add(s.toUpperCase());
        }
        for (String s : cfg.getStringList("blacklist-names")) {
            blacklistNames.add(s.toLowerCase());
        }
        statusPerksConfig = plugin.getConfig().getConfigurationSection("status-perks");
    }

    public void load() {
        auctions.clear();
        dataFile = new File(plugin.getDataFolder(), "auction_data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать auction_data.yml: " + e.getMessage());
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = dataConfig.getConfigurationSection("auctions");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Auction auction = new Auction(s.getValues(false));
                auctions.put(auction.getId(), auction);
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка загрузки аукциона " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Загружено аукционов: " + auctions.size());
    }

    public void startAutoSave() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkExpired();
            save();
        }, 20L * 60, 20L * 60);
    }

    public void save() {
        if (dataConfig == null) return;
        dataConfig.set("auctions", null);
        ConfigurationSection sec = dataConfig.createSection("auctions");
        for (Auction auction : auctions.values()) {
            sec.set(auction.getId().toString(), auction.serialize());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка сохранения auction_data.yml: " + e.getMessage());
        }
    }

    public void checkExpired() {
        List<Auction> expired = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.getStatus() == Auction.Status.ACTIVE && auction.isExpired()) {
                expired.add(auction);
            }
        }
        for (Auction auction : expired) {
            completeAuction(auction);
        }
    }

    private void completeAuction(Auction auction) {
        if (auction.hasBids()) {
            auction.setStatus(Auction.Status.COMPLETED);
            Player seller = Bukkit.getPlayer(auction.getSeller());
            double effectiveTax = seller != null ? getEffectiveTax(seller) : taxPercent;
            double tax = auction.getCurrentBid() * effectiveTax / 100.0;
            long payout = Math.round(auction.getCurrentBid() - tax);
            if (seller != null && seller.isOnline()) {
                VKChatBridge.addEffectiveRep(seller, (int) payout);
                seller.playSound(seller.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                seller.sendMessage("§a✓ Твой лот продан за §e" + Math.round(auction.getCurrentBid()) + " реп. §a(после налога §7" + Math.round(tax) + "§a)");
            } else {
                plugin.getPendingPayouts().merge(auction.getSeller(), (int) payout, Integer::sum);
            }
            Player buyer = Bukkit.getPlayer(auction.getHighestBidder());
            if (buyer != null && buyer.isOnline()) {
                ItemStack item = auction.getItemStack();
                Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), item);
                    buyer.sendMessage("§e⚠ Инвентарь полон! Предмет выпал на землю.");
                }
                buyer.playSound(buyer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                buyer.sendMessage("§a✓ Ты выиграл аукцион: §f" + formatItemName(auction.getItemStack()));
            } else {
                plugin.getPendingItems().merge(auction.getHighestBidder(), auction.getItemStack(), (a, b) -> {
                    Player p = Bukkit.getPlayer(auction.getHighestBidder());
                    if (p != null) { Map<Integer, ItemStack> r = p.getInventory().addItem(b); return r.isEmpty() ? null : r.get(0); }
                    return b;
                });
            }
        } else {
            auction.setStatus(Auction.Status.EXPIRED);
            Player seller = Bukkit.getPlayer(auction.getSeller());
            if (seller != null && seller.isOnline()) {
                ItemStack item = auction.getItemStack();
                Map<Integer, ItemStack> leftover = seller.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    seller.getWorld().dropItemNaturally(seller.getLocation(), item);
                    seller.sendMessage("§e⚠ Лот истёк. Предмет выпал на землю (инвентарь полон).");
                } else {
                    seller.sendMessage("§e⌛ Лот истёк. Предмет возвращён в инвентарь.");
                }
            } else {
                plugin.getPendingItems().merge(auction.getSeller(), auction.getItemStack(), (a, b) -> {
                    Player p = Bukkit.getPlayer(auction.getSeller());
                    if (p != null) { Map<Integer, ItemStack> r = p.getInventory().addItem(b); return r.isEmpty() ? null : r.get(0); }
                    return b;
                });
            }
        }
    }

    public AuctionResult createAuction(Player player, ItemStack item, double startPrice, double buyItNow) {
        if (item == null || item.getType() == Material.AIR) {
            return new AuctionResult(false, "§c❌ У тебя нет предмета в руке!");
        }
        if (startPrice < minPrice) {
            return new AuctionResult(false, "§c❌ Минимальная цена: §e" + minPrice + " реп.");
        }
        if (buyItNow > 0 && buyItNow <= startPrice) {
            return new AuctionResult(false, "§c❌ Цена выкупа должна быть больше стартовой!");
        }
        if (getActiveCount(player.getUniqueId()) >= getEffectiveMaxLots(player)) {
            return new AuctionResult(false, "§c❌ Максимум активных лотов: §e" + getEffectiveMaxLots(player) + " §7(с учётом статуса)");
        }
        String typeName = item.getType().name();
        if (blacklistMaterials.contains(typeName)) {
            return new AuctionResult(false, "§c❌ Этот предмет запрещён к продаже!");
        }
        if (item.hasItemMeta()) {
            String displayName = item.getItemMeta().getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                for (String banned : blacklistNames) {
                    if (displayName.toLowerCase().contains(banned)) {
                        return new AuctionResult(false, "§c❌ Этот предмет запрещён к продаже!");
                    }
                }
            }
        }
        long last = lastCreateTime.getOrDefault(player.getUniqueId(), 0L);
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        if (elapsed < createCooldownSec) {
            return new AuctionResult(false, "§c❌ Подожди §e" + (createCooldownSec - elapsed) + "c §cперед созданием нового лота.");
        }

        Auction auction = new Auction(
            UUID.randomUUID(), player.getUniqueId(), player.getName(),
            item, startPrice, buyItNow, defaultDurationHours * 3600000L
        );
        auctions.put(auction.getId(), auction);
        lastCreateTime.put(player.getUniqueId(), System.currentTimeMillis());
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        return new AuctionResult(true, "§a✓ Лот создан! ID: §e" + auction.getId().toString().substring(0, 8) + "§a...");
    }

    public AuctionResult placeBid(Player player, UUID auctionId, double amount) {
        Auction auction = auctions.get(auctionId);
        if (auction == null || auction.getStatus() != Auction.Status.ACTIVE) {
            return new AuctionResult(false, "§c❌ Аукцион не найден или завершён.");
        }
        if (auction.getSeller().equals(player.getUniqueId())) {
            return new AuctionResult(false, "§c❌ Нельзя ставить на свой лот!");
        }
        if (auction.isExpired()) {
            completeAuction(auction);
            return new AuctionResult(false, "§c❌ Аукцион уже истёк.");
        }
        double minBid = auction.getMinimumBid();
        if (amount < minBid) {
            return new AuctionResult(false, "§c❌ Минимальная ставка: §e" + Math.round(minBid) + " реп.");
        }
        int rep = VKChatBridge.getEffectiveRep(player);
        if (rep < amount) {
            return new AuctionResult(false, "§c❌ Недостаточно репутации! Нужно: §e" + Math.round(amount) + " реп.");
        }
        if (auction.hasBids()) {
            Player prevBidder = Bukkit.getPlayer(auction.getHighestBidder());
            if (prevBidder != null && prevBidder.isOnline()) {
                double refund = auction.getCurrentBid();
                VKChatBridge.addEffectiveRep(prevBidder, (int) refund);
                prevBidder.playSound(prevBidder.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                prevBidder.sendMessage("§e↩ Твою ставку на §f" + formatItemName(auction.getItemStack()) + " §eперебили! Возвращено §f" + Math.round(refund) + " реп.");
            } else {
                plugin.getPendingPayouts().merge(auction.getHighestBidder(), (int) auction.getCurrentBid(), Integer::sum);
            }
        }
        VKChatBridge.takeEffectiveRep(player, (int) amount);
        auction.placeBid(player.getUniqueId(), player.getName(), amount);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);

        Player seller = Bukkit.getPlayer(auction.getSeller());
        if (seller != null && seller.isOnline()) {
            seller.playSound(seller.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            seller.sendMessage("§eℹ На твой лот §f" + formatItemName(auction.getItemStack()) + " §eпоступила ставка: §f" + Math.round(amount) + " реп. §7(" + player.getName() + ")");
        }
        return new AuctionResult(true, "§a✓ Ставка §e" + Math.round(amount) + " реп. §aпринята!");
    }

    public AuctionResult buyItNow(Player player, UUID auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null || auction.getStatus() != Auction.Status.ACTIVE) {
            return new AuctionResult(false, "§c❌ Аукцион не найден или завершён.");
        }
        if (auction.getSeller().equals(player.getUniqueId())) {
            return new AuctionResult(false, "§c❌ Нельзя выкупить свой лот!");
        }
        if (!auction.hasBuyItNow()) {
            return new AuctionResult(false, "§c❌ У этого лота нет цены выкупа.");
        }
        if (auction.isExpired()) {
            completeAuction(auction);
            return new AuctionResult(false, "§c❌ Аукцион уже истёк.");
        }
        int rep = VKChatBridge.getEffectiveRep(player);
        if (rep < auction.getBuyItNow()) {
            return new AuctionResult(false, "§c❌ Недостаточно репутации! Нужно: §e" + Math.round(auction.getBuyItNow()) + " реп.");
        }
        if (auction.hasBids()) {
            Player prevBidder = Bukkit.getPlayer(auction.getHighestBidder());
            if (prevBidder != null && prevBidder.isOnline()) {
                VKChatBridge.addEffectiveRep(prevBidder, (int) auction.getCurrentBid());
                prevBidder.playSound(prevBidder.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                prevBidder.sendMessage("§e↩ Лот §f" + formatItemName(auction.getItemStack()) + " §eвыкуплен. Возвращено §f" + Math.round(auction.getCurrentBid()) + " реп.");
            } else {
                plugin.getPendingPayouts().merge(auction.getHighestBidder(), (int) auction.getCurrentBid(), Integer::sum);
            }
        }
        VKChatBridge.takeEffectiveRep(player, (int) auction.getBuyItNow());
        auction.setStatus(Auction.Status.COMPLETED);
        ItemStack item = auction.getItemStack();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("§e⚠ Инвентарь полон! Предмет выпал на землю.");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        player.sendMessage("§a✓ Ты выкупил §f" + formatItemName(item) + " §aза §e" + Math.round(auction.getBuyItNow()) + " реп.");
        Player seller = Bukkit.getPlayer(auction.getSeller());
        double effectiveTax = seller != null ? getEffectiveTax(seller) : taxPercent;
        double tax = auction.getBuyItNow() * effectiveTax / 100.0;
        long payout = Math.round(auction.getBuyItNow() - tax);
        if (seller != null && seller.isOnline()) {
            VKChatBridge.addEffectiveRep(seller, (int) payout);
            seller.playSound(seller.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            seller.sendMessage("§a✓ Твой лот выкуплен! Получено §e" + payout + " реп. §a(налог §7" + Math.round(tax) + "§a)");
        } else {
            plugin.getPendingPayouts().merge(auction.getSeller(), (int) payout, Integer::sum);
        }
        return new AuctionResult(true, "");
    }

    public AuctionResult cancelAuction(Player player, UUID auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            return new AuctionResult(false, "§c❌ Аукцион не найден.");
        }
        if (!auction.getSeller().equals(player.getUniqueId())) {
            return new AuctionResult(false, "§c❌ Это не твой лот!");
        }
        if (auction.getStatus() != Auction.Status.ACTIVE) {
            return new AuctionResult(false, "§c❌ Лот уже завершён.");
        }
        if (auction.hasBids()) {
            long last = lastCancelTime.getOrDefault(player.getUniqueId(), 0L);
            long elapsed = (System.currentTimeMillis() - last) / 1000;
            if (elapsed < cancelCooldownSec) {
                return new AuctionResult(false, "§c❌ Кулдаун отмены: §e" + (cancelCooldownSec - elapsed) + "c");
            }
            Player topBidder = Bukkit.getPlayer(auction.getHighestBidder());
            if (topBidder != null && topBidder.isOnline()) {
                VKChatBridge.addEffectiveRep(topBidder, (int) auction.getCurrentBid());
                topBidder.playSound(topBidder.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                topBidder.sendMessage("§e↩ Продавец отменил лот §f" + formatItemName(auction.getItemStack()) + "§e. Ставка возвращена.");
            } else {
                plugin.getPendingPayouts().merge(auction.getHighestBidder(), (int) auction.getCurrentBid(), Integer::sum);
            }
            VKChatBridge.takeEffectiveRep(player, cancelPenalty);
            player.sendMessage("§c⚠ Штраф за отмену: §e" + cancelPenalty + " реп.");
            lastCancelTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
        auction.setStatus(Auction.Status.CANCELLED);
        ItemStack item = auction.getItemStack();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("§e⚠ Предмет выпал на землю (инвентарь полон).");
        }
        player.sendMessage("§a✓ Лот отменён. Предмет возвращён.");
        return new AuctionResult(true, "");
    }

    public void collectPending(Player player) {
        UUID uuid = player.getUniqueId();
        Integer payout = plugin.getPendingPayouts().remove(uuid);
        if (payout != null && payout > 0) {
            VKChatBridge.addEffectiveRep(player, payout);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            player.sendMessage("§a✓ Получено §e" + payout + " реп. §aс продажи/отмены.");
        }
        ItemStack item = plugin.getPendingItems().remove(uuid);
        if (item != null) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage("§e⚠ Предмет выпал на землю (инвентарь полон).");
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                player.sendMessage("§a✓ Предмет получен: §f" + formatItemName(item));
            }
        }
        if ((payout == null || payout <= 0) && item == null) {
            player.sendMessage("§eℹ Нет ожидающих выплат или предметов.");
        }
    }

    // ─── Admin ───

    public void clearAll() {
        auctions.clear();
        save();
    }

    public int removePlayerAuctions(UUID targetUuid) {
        List<Auction> toRemove = auctions.values().stream()
            .filter(a -> a.getSeller().equals(targetUuid) || (a.getHighestBidder() != null && a.getHighestBidder().equals(targetUuid)))
            .collect(Collectors.toList());
        for (Auction a : toRemove) {
            a.setStatus(Auction.Status.CANCELLED);
            Player seller = Bukkit.getPlayer(a.getSeller());
            if (seller != null && seller.isOnline()) {
                ItemStack item = a.getItemStack();
                Map<Integer, ItemStack> leftover = seller.getInventory().addItem(item);
                if (!leftover.isEmpty()) seller.getWorld().dropItemNaturally(seller.getLocation(), item);
            }
            if (a.hasBids()) {
                Player bidder = Bukkit.getPlayer(a.getHighestBidder());
                if (bidder != null && bidder.isOnline()) {
                    VKChatBridge.addEffectiveRep(bidder, (int) a.getCurrentBid());
                } else {
                    plugin.getPendingPayouts().merge(a.getHighestBidder(), (int) a.getCurrentBid(), Integer::sum);
                }
            }
            auctions.remove(a.getId());
        }
        save();
        return toRemove.size();
    }

    public Auction adminRemoveAuction(UUID auctionId) {
        Auction a = auctions.remove(auctionId);
        if (a != null) save();
        return a;
    }

    public List<Auction> getAllAuctions() {
        return new ArrayList<>(auctions.values());
    }

    public Collection<Auction> getAuctionMapValues() {
        return auctions.values();
    }

    // ─── Queries ───

    public List<Auction> getActiveAuctions() {
        return auctions.values().stream()
            .filter(a -> a.getStatus() == Auction.Status.ACTIVE && !a.isExpired())
            .sorted(Comparator.comparingLong(Auction::getEndTime))
            .collect(Collectors.toList());
    }

    public List<Auction> getMyActiveAuctions(UUID playerUuid) {
        return auctions.values().stream()
            .filter(a -> a.getSeller().equals(playerUuid) && a.getStatus() == Auction.Status.ACTIVE)
            .sorted(Comparator.comparingLong(Auction::getEndTime))
            .collect(Collectors.toList());
    }

    public int getActiveCount(UUID playerUuid) {
        return (int) auctions.values().stream()
            .filter(a -> a.getSeller().equals(playerUuid) && a.getStatus() == Auction.Status.ACTIVE)
            .count();
    }

    public Auction getAuction(UUID id) {
        return auctions.get(id);
    }

    public int getMinPrice() { return minPrice; }

    public List<Auction> getAllCompletedOrExpired(UUID playerUuid) {
        return auctions.values().stream()
            .filter(a -> (a.getSeller().equals(playerUuid) || (a.getHighestBidder() != null && a.getHighestBidder().equals(playerUuid))))
            .filter(a -> a.getStatus() == Auction.Status.COMPLETED || a.getStatus() == Auction.Status.EXPIRED)
            .sorted(Comparator.comparingLong(Auction::getEndTime).reversed())
            .collect(Collectors.toList());
    }

    public static String formatItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName() + " §7x" + item.getAmount();
        }
        return item.getType().name() + " §7x" + item.getAmount();
    }

    public int getEffectiveMaxLots(Player player) {
        int base = maxActivePerPlayer;
        if (player == null) return base;
        if (VKChatBridge.hasPass(player)) {
            base += getPerkBonus("pass", "max-lots-bonus", 1);
        }
        String statusId = AuctionDonateBridge.getPlayerStatusId(player);
        if (statusId != null) {
            base += getPerkBonus(statusId, "max-lots-bonus", 0);
        }
        return base;
    }

    public double getEffectiveTax(Player seller) {
        double base = taxPercent;
        if (seller == null) return base;
        double discount = 0;
        String statusId = AuctionDonateBridge.getPlayerStatusId(seller);
        if (statusId != null) {
            discount = Math.max(discount, getPerkDouble(statusId, "commission-discount", 0));
        }
        return base * (1.0 - discount);
    }

    public String getDonatePerksInfo(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6═══ Твои бонусы аукциона ═══\n");
        sb.append("§fМакс. лотов: §e").append(getEffectiveMaxLots(player)).append(" §7(база: ").append(maxActivePerPlayer).append(")\n");
        double discount = 0;
        String statusId = AuctionDonateBridge.getPlayerStatusId(player);
        if (statusId != null) {
            discount = getPerkDouble(statusId, "commission-discount", 0);
        }
        sb.append("§fКомиссия: §e").append(String.format("%.1f", getEffectiveTax(player))).append("%");
        if (discount > 0) {
            sb.append(" §7(скидка ").append(Math.round(discount * 100)).append("%)");
        }
        sb.append("\n");
        String statusName = AuctionDonateBridge.getPlayerStatusName(player);
        if (statusName != null) {
            sb.append("§fСтатус: ").append(statusName);
        } else if (VKChatBridge.hasPass(player)) {
            sb.append("§fСтатус: §eПроходка (+1 лот)");
        } else {
            sb.append("§fСтатус: §7нет (купи проходку за 500₽)");
        }
        return sb.toString();
    }

    private int getPerkBonus(String key, String subKey, int defaultVal) {
        if (statusPerksConfig == null) return defaultVal;
        ConfigurationSection s = statusPerksConfig.getConfigurationSection(key);
        return s != null ? s.getInt(subKey, defaultVal) : defaultVal;
    }

    private double getPerkDouble(String key, String subKey, double defaultVal) {
        if (statusPerksConfig == null) return defaultVal;
        ConfigurationSection s = statusPerksConfig.getConfigurationSection(key);
        return s != null ? s.getDouble(subKey, defaultVal) : defaultVal;
    }

    public static class AuctionResult {
        private final boolean success;
        private final String message;
        public AuctionResult(boolean success, String message) {
            this.success = success; this.message = message;
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
