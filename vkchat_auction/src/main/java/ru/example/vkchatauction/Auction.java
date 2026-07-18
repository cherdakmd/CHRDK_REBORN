package ru.example.vkchatauction;

import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Auction implements ConfigurationSerializable {

    public enum Status { ACTIVE, COMPLETED, EXPIRED, CANCELLED }

    public static class BidEntry {
        public final UUID bidder;
        public final String bidderName;
        public final double amount;
        public final long timestamp;
        public BidEntry(UUID bidder, String bidderName, double amount, long timestamp) {
            this.bidder = bidder; this.bidderName = bidderName;
            this.amount = amount; this.timestamp = timestamp;
        }
        public Map<String, Object> serialize() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bidder", bidder.toString());
            m.put("bidderName", bidderName);
            m.put("amount", amount);
            m.put("timestamp", timestamp);
            return m;
        }
        public static BidEntry deserialize(Map<String, Object> m) {
            return new BidEntry(
                UUID.fromString((String) m.get("bidder")),
                (String) m.get("bidderName"),
                ((Number) m.get("amount")).doubleValue(),
                ((Number) m.get("timestamp")).longValue()
            );
        }
    }

    private final UUID id;
    private final UUID seller;
    private final String sellerName;
    private ItemStack itemStack;
    private double startPrice;
    private double currentBid;
    private UUID highestBidder;
    private String highestBidderName;
    private double buyItNow;
    private long startTime;
    private long endTime;
    private Status status;
    private final List<BidEntry> bidHistory;

    public Auction(UUID id, UUID seller, String sellerName, ItemStack itemStack,
                   double startPrice, double buyItNow, long durationMs) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.itemStack = itemStack.clone();
        this.startPrice = startPrice;
        this.currentBid = 0;
        this.highestBidder = null;
        this.highestBidderName = null;
        this.buyItNow = buyItNow;
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + durationMs;
        this.status = Status.ACTIVE;
        this.bidHistory = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public Auction(Map<String, Object> map) {
        this.id = UUID.fromString((String) map.get("id"));
        this.seller = UUID.fromString((String) map.get("seller"));
        this.sellerName = (String) map.get("sellerName");
        Object itemObj = map.get("itemStack");
        if (itemObj instanceof ItemStack) {
            this.itemStack = ((ItemStack) itemObj).clone();
        } else if (itemObj instanceof Map) {
            this.itemStack = ItemStack.deserialize((Map<String, Object>) itemObj);
        }
        if (this.itemStack == null || this.itemStack.getType() == Material.AIR) {
            this.itemStack = new ItemStack(Material.STONE);
        }
        this.startPrice = ((Number) map.get("startPrice")).doubleValue();
        this.currentBid = map.containsKey("currentBid") ? ((Number) map.get("currentBid")).doubleValue() : 0;
        String hb = (String) map.get("highestBidder");
        this.highestBidder = hb != null ? UUID.fromString(hb) : null;
        this.highestBidderName = (String) map.get("highestBidderName");
        this.buyItNow = map.containsKey("buyItNow") ? ((Number) map.get("buyItNow")).doubleValue() : 0;
        this.startTime = ((Number) map.get("startTime")).longValue();
        this.endTime = ((Number) map.get("endTime")).longValue();
        this.status = Status.valueOf((String) map.get("status"));
        this.bidHistory = new ArrayList<>();
        Object historyObj = map.get("bidHistory");
        if (historyObj instanceof List) {
            for (Object o : (List<Object>) historyObj) {
                if (o instanceof Map) {
                    try {
                        bidHistory.add(BidEntry.deserialize((Map<String, Object>) o));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id.toString());
        map.put("seller", seller.toString());
        map.put("sellerName", sellerName);
        map.put("itemStack", itemStack.serialize());
        map.put("startPrice", startPrice);
        map.put("currentBid", currentBid);
        if (highestBidder != null) map.put("highestBidder", highestBidder.toString());
        if (highestBidderName != null) map.put("highestBidderName", highestBidderName);
        map.put("buyItNow", buyItNow);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("status", status.name());
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (BidEntry e : bidHistory) historyList.add(e.serialize());
        map.put("bidHistory", historyList);
        return map;
    }

    public boolean isExpired() {
        return status == Status.ACTIVE && System.currentTimeMillis() >= endTime;
    }

    public long getTimeLeftMs() {
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    public UUID getId() { return id; }
    public UUID getSeller() { return seller; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItemStack() { return itemStack.clone(); }
    public double getStartPrice() { return startPrice; }
    public double getCurrentBid() { return currentBid; }
    public UUID getHighestBidder() { return highestBidder; }
    public String getHighestBidderName() { return highestBidderName; }
    public double getBuyItNow() { return buyItNow; }
    public boolean hasBuyItNow() { return buyItNow > 0; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public Status getStatus() { return status; }
    public List<BidEntry> getBidHistory() { return Collections.unmodifiableList(bidHistory); }

    public void setStatus(Status status) { this.status = status; }

    public void placeBid(UUID bidder, String bidderName, double amount) {
        this.currentBid = amount;
        this.highestBidder = bidder;
        this.highestBidderName = bidderName;
        bidHistory.add(new BidEntry(bidder, bidderName, amount, System.currentTimeMillis()));
        if (bidHistory.size() > 20) {
            bidHistory.remove(0);
        }
    }

    public boolean hasBids() { return highestBidder != null; }

    public double getMinimumBid() {
        if (currentBid <= 0) return startPrice;
        double min = currentBid * 1.10;
        min = Math.ceil(min);
        return Math.max(min, startPrice);
    }
}
