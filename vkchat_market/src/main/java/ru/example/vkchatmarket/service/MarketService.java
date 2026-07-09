package ru.example.vkchatmarket.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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

    public List<MarketEntry> getAll() {
        return new ArrayList<>(entries.values());
    }

    public int countItems(org.bukkit.entity.Player p, MarketEntry entry) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == entry.material()) count += item.getAmount();
        }
        return count;
    }

    public boolean takeItems(org.bukkit.entity.Player p, MarketEntry entry, int amount) {
        int remaining = amount;
        for (org.bukkit.inventory.ItemStack item : p.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (item != null && item.getType() == entry.material()) {
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
        return remaining <= 0;
    }

    public void giveItems(org.bukkit.entity.Player p, MarketEntry entry, int amount) {
        int maxStack = entry.material().getMaxStackSize();
        while (amount > 0) {
            int give = Math.min(amount, maxStack);
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(entry.material(), give));
            amount -= give;
        }
    }
}
