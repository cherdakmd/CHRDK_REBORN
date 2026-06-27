package ru.example.vkchatoffline.data;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.io.File;
import java.util.*;

public class StashManager {
    private final VKChatOfflinePlugin plugin;
    private final File file;
    private FileConfiguration data;

    public StashManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stash.yml");
        load();
    }

    public synchronized void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        try { data.save(file); } catch (Exception e) { plugin.getLogger().warning("Не удалось сохранить stash.yml: " + e.getMessage()); }
    }

    public synchronized List<ItemStack> getItems(UUID uuid) {
        List<String> raw = data.getStringList("players." + uuid + ".items");
        List<ItemStack> items = new ArrayList<>();
        for (String line : raw) {
            ItemStack item = parseItem(line);
            if (item != null) items.add(item);
        }
        return items;
    }

    public synchronized void saveItems(UUID uuid, List<ItemStack> items) {
        List<String> raw = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                String encoded = item.getType().name() + ";" + item.getAmount();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    encoded += ";name=" + item.getItemMeta().getDisplayName().replace(";", ",");
                }
                raw.add(encoded);
            }
        }
        data.set("players." + uuid + ".items", raw);
        save();
    }

    public synchronized void addItem(UUID uuid, ItemStack item) {
        List<ItemStack> items = getItems(uuid);
        items.add(item);
        saveItems(uuid, items);
    }

    public synchronized void addItems(UUID uuid, List<ItemStack> rewardItems) {
        List<ItemStack> items = getItems(uuid);
        items.addAll(rewardItems);
        saveItems(uuid, items);
    }

    public synchronized boolean isEmpty(UUID uuid) {
        return getItems(uuid).isEmpty();
    }

    public synchronized boolean consumeNamedItem(UUID uuid, Material material, String displayName) {
        List<ItemStack> items = getItems(uuid);
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item.getType() == material && item.hasItemMeta() && displayName.equals(item.getItemMeta().getDisplayName())) {
                if (item.getAmount() <= 1) items.remove(i);
                else item.setAmount(item.getAmount() - 1);
                saveItems(uuid, items);
                return true;
            }
        }
        return false;
    }

    public String renderPage(UUID uuid, int page, int pageSize) {
        List<ItemStack> items = getItems(uuid);
        if (items.isEmpty()) return "🎒 Тайник пуст.";
        int pages = Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
        page = Math.max(1, Math.min(page, pages));
        int from = (page - 1) * pageSize;
        int to = Math.min(items.size(), from + pageSize);
        StringBuilder sb = new StringBuilder("🎒 Тайник — страница " + page + "/" + pages + "\n");
        for (int i = from; i < to; i++) {
            ItemStack item = items.get(i);
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
            sb.append(i + 1).append(". ").append(name).append(" x").append(item.getAmount()).append("\n");
        }
        sb.append("\nЗабрать предметы в игре: /stash");
        return sb.toString();
    }

    public ItemStack namedKey(String displayName) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Ключ для открытия маршрута оффлайн-походов");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack parseItem(String line) {
        try {
            String[] parts = line.split(";");
            Material material = Material.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            amount = Math.max(1, Math.min(64, amount));
            ItemStack item = new ItemStack(material, amount);
            for (String part : parts) {
                if (part.startsWith("name=")) {
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(part.substring("name=".length()));
                    item.setItemMeta(meta);
                }
            }
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }
}
