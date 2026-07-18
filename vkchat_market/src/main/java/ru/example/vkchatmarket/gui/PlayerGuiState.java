package ru.example.vkchatmarket.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerGuiState {
    private final Map<UUID, GuiState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> carts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dailyRewards = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loyaltyCounters = new ConcurrentHashMap<>();

    public enum SortMode { PRICE, NAME, TREND }

    public void set(UUID uuid, String categoryKey, int page, String searchQuery) {
        GuiState old = states.getOrDefault(uuid, GuiState.DEFAULT);
        states.put(uuid, new GuiState(categoryKey, page, searchQuery, old.sortMode(), old.bulkMode()));
    }

    public void setSort(UUID uuid, SortMode mode) {
        GuiState old = states.getOrDefault(uuid, GuiState.DEFAULT);
        states.put(uuid, new GuiState(old.categoryKey(), old.page(), old.searchQuery(), mode, old.bulkMode()));
    }

    public void toggleBulkMode(UUID uuid) {
        GuiState old = states.getOrDefault(uuid, GuiState.DEFAULT);
        states.put(uuid, new GuiState(old.categoryKey(), old.page(), old.searchQuery(), old.sortMode(), !old.bulkMode()));
    }

    public GuiState get(UUID uuid) {
        return states.getOrDefault(uuid, GuiState.DEFAULT);
    }

    public void clear(UUID uuid) {
        states.remove(uuid);
    }

    // Cart
    public Map<String, Integer> getCart(UUID uuid) {
        return carts.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    public void addToCart(UUID uuid, String itemId, int amount) {
        getCart(uuid).merge(itemId, amount, Integer::sum);
    }

    public void removeFromCart(UUID uuid, String itemId) {
        getCart(uuid).remove(itemId);
    }

    public void clearCart(UUID uuid) {
        carts.remove(uuid);
    }

    // Daily reward
    public long getLastDailyReward(UUID uuid) {
        return dailyRewards.getOrDefault(uuid, 0L);
    }

    public void setLastDailyReward(UUID uuid, long time) {
        dailyRewards.put(uuid, time);
    }

    // Loyalty counter
    public int getLoyaltyCount(UUID uuid) {
        return loyaltyCounters.getOrDefault(uuid, 0);
    }

    public void incrementLoyalty(UUID uuid) {
        loyaltyCounters.merge(uuid, 1, Integer::sum);
    }

    public void resetLoyalty(UUID uuid) {
        loyaltyCounters.put(uuid, 0);
    }

    public record GuiState(String categoryKey, int page, String searchQuery, SortMode sortMode, boolean bulkMode) {
        public static final GuiState DEFAULT = new GuiState("all", 0, null, SortMode.PRICE, false);
        public boolean isSearch() { return searchQuery != null; }
    }
}
