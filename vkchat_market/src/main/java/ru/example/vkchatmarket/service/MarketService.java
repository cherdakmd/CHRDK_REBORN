package ru.example.vkchatmarket.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatmarket.VKChatMarketPlugin;
import ru.example.vkchatmarket.log.TransactionLog;
import ru.example.vkchatmarket.model.MarketCategory;
import ru.example.vkchatmarket.model.MarketEntry;
import ru.example.vkchat.util.VKChatBridge;

import java.util.*;

public class MarketService {
    private final VKChatMarketPlugin plugin;
    private final PriceService priceService;
    private final Map<String, MarketEntry> entries = new LinkedHashMap<>();
    private final Map<String, String> setPieceMap = new HashMap<>();
    private TransactionLog transactionLog;

    public MarketService(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
        this.priceService = new PriceService(plugin);
    }

    public void setTransactionLog(TransactionLog log) { this.transactionLog = log; }

    public PriceService prices() { return priceService; }

    public void load() {
        entries.clear();
        setPieceMap.clear();
        ConfigurationSection items = plugin.getCategoriesConfig().getConfigurationSection("items");
        if (items == null) {
            plugin.getLogger().warning("Секция 'items' не найдена в categories.yml!");
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
        initSetPieceMap();
        plugin.getLogger().info("Загружено товаров рынка: " + entries.size() + " (сетовых: " + setPieceMap.size() + ")");
    }

    private static final String[] SET_KEYS = {
        "bogatyr", "leshy", "koshchey", "volhv", "sokol",
        "proletarian", "udarnik", "cosmonaut", "tankist", "pioneer",
        "red_cat", "dragon_slayer", "el_carpo", "shadow_monarch", "perun",
        "chernobog", "gagarin",
        "bone_armor", "shadow_blade", "ember_crown", "plague_mist", "starforged",
        "slavic_mage", "soviet_engineer", "assassin_cloak"
    };

    private static final String[] PIECE_SUFFIXES = {"helmet", "chestplate", "leggings", "boots"};

    private void initSetPieceMap() {
        for (String setKey : SET_KEYS) {
            for (String piece : PIECE_SUFFIXES) {
                setPieceMap.put(setKey + "_" + piece, setKey);
            }
        }
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

    public List<MarketEntry> searchWithPrefix(String query) {
        String q = query.toLowerCase().trim();
        String[] parts = q.split(":", 2);
        if (parts.length < 2 || parts[1].isEmpty()) return search(q);

        String prefix = parts[0].trim();
        String searchTerm = parts[1].trim();

        MarketCategory cat = MarketCategory.fromConfig(prefix);
        List<MarketEntry> pool;
        if (cat != null) {
            pool = getByCategory(cat);
        } else {
            pool = getAll();
        }

        if (searchTerm.isEmpty()) return pool;

        if (searchTerm.contains("..")) {
            String[] range = searchTerm.split("\\.\\.", 2);
            try {
                int min = range[0].isEmpty() ? 0 : Integer.parseInt(range[0].trim());
                int max = range[1].isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(range[1].trim());
                List<MarketEntry> result = new ArrayList<>();
                for (MarketEntry e : pool) {
                    if (e.basePrice() >= min && e.basePrice() <= max) result.add(e);
                }
                return result;
            } catch (NumberFormatException ignored) {}
        }

        List<MarketEntry> result = new ArrayList<>();
        for (MarketEntry e : pool) {
            String name = org.bukkit.ChatColor.stripColor(e.displayName()).toLowerCase();
            if (name.contains(searchTerm) || e.id().toLowerCase().contains(searchTerm)) result.add(e);
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
        String setKey = setPieceMap.get(entry.id());
        int maxStack = entry.material().getMaxStackSize();
        while (amount > 0) {
            int give = Math.min(amount, maxStack);
            if (setKey != null) {
                for (int i = 0; i < give; i++) {
                    ItemStack item = createSetPiece(entry, setKey);
                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
                    }
                }
            } else {
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack(entry.material(), give));
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
            amount -= give;
        }
    }

    private ItemStack createSetPiece(MarketEntry entry, String setKey) {
        ItemStack item = new ItemStack(entry.material(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(entry.displayName());
            NamespacedKey gearSetKey = new NamespacedKey(plugin, "gear_set");
            NamespacedKey originKey = new NamespacedKey(plugin, "gear_set_origin");
            meta.getPersistentDataContainer().set(gearSetKey, PersistentDataType.STRING, setKey);
            meta.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, "market");
            item.setItemMeta(meta);
        }
        return item;
    }

    public int sellAll(Player p, MarketCategory category) {
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
            if (transactionLog != null) {
                transactionLog.log(p, "SELL_ALL", entry.id(), owned, price, "cat=" + (category != null ? category.configKey() : "all"));
            }
        }
        if (totalEarned > 0) {
            VKChatBridge.addEffectiveRep(p, totalEarned);
        }
        return totalEarned;
    }

    public Map<String, Integer> getCategoryBreakdown(Player p) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        for (MarketCategory cat : MarketCategory.values()) {
            int total = 0;
            for (MarketEntry entry : getByCategory(cat)) {
                total += countItems(p, entry);
            }
            if (total > 0) {
                breakdown.put(cat.configKey(), total);
            }
        }
        return breakdown;
    }
}
