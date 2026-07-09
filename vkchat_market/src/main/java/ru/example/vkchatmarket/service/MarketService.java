package ru.example.vkchatmarket.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;

import java.util.*;

public class MarketService {
    private final VKChatMarketPlugin plugin;
    private final PriceService priceService;
    private final Map<String, MarketEntry> entries = new LinkedHashMap<>();

    public MarketService(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.priceService = new PriceService(plugin);
    }

    public PriceService prices() { return priceService; }

    public void load() {
        entries.clear();
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("items");
        if (items == null) {
            plugin.getLogger().warning("Секция 'items' не найдена в config.yml!");
            return;
        }
        for (String id : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(id);
            if (sec == null) continue;
            String matName = sec.getString("material");
            if (matName == null) continue;
            Material material;
            try { material = Material.valueOf(matName.toUpperCase()); } catch (Exception e) { continue; }
            String name = sec.getString("name", id);
            String catKey = sec.getString("category", "ores");
            MarketCategory category = MarketCategory.fromConfig(catKey);
            if (category == null) category = MarketCategory.ORES;
            int basePrice = sec.getInt("base-price", 10);
            entries.put(id, new MarketEntry(id, material, name, category, basePrice));
        }
        plugin.getLogger().info("Загружено товаров рынка: " + entries.size());
    }

    public MarketEntry get(String id) { return entries.get(id); }

    public List<MarketEntry> getByCategory(MarketCategory category) {
        List<MarketEntry> result = new ArrayList<>();
        for (MarketEntry e : entries.values()) {
            if (e.category() == category) result.add(e);
        }
        return result;
    }

    public List<MarketEntry> getAll() { return new ArrayList<>(entries.values()); }

    public List<MarketEntry> search(String query) {
        String q = query.toLowerCase().trim();
        List<MarketEntry> result = new ArrayList<>();
        for (MarketEntry e : entries.values()) {
            String name = org.bukkit.ChatColor.stripColor(e.displayName()).toLowerCase();
            if (name.contains(q) || e.id().toLowerCase().contains(q)) result.add(e);
        }
        return result;
    }

    public int countItems(Player p, MarketEntry entry) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == entry.material()) count += item.getAmount();
        }
        return count;
    }

    public boolean takeItems(Player p, MarketEntry entry, int amount) {
        int remaining = amount;
        for (ItemStack item : p.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (item != null && item.getType() == entry.material()) {
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
        return remaining <= 0;
    }

    public void giveItems(Player p, MarketEntry entry, int amount) {
        int maxStack = entry.material().getMaxStackSize();
        while (amount > 0) {
            int give = Math.min(amount, maxStack);
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack(entry.material(), give));
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
            amount -= give;
        }
    }

    public int sellAll(Player p, MarketCategory category, int vkId) {
        List<MarketEntry> list = category == null ? getAll() : getByCategory(category);
        int totalEarned = 0;
        int itemsSold = 0;
        for (MarketEntry entry : list) {
            int owned = countItems(p, entry);
            if (owned <= 0) continue;
            int price = prices().getSellPrice(entry, p);
            int earned = price * owned;
            takeItems(p, entry, owned);
            prices().recordSell(entry, owned);
            totalEarned += earned;
            itemsSold += owned;
        }
        if (totalEarned > 0) {
            ru.example.vkchat.util.VKChatBridge.addPoints(vkId, totalEarned);
        }
        return totalEarned;
    }
}
