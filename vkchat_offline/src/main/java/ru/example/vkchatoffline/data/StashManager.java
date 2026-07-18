package ru.example.vkchatoffline.data;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StashManager {
    private final VKChatOfflinePlugin plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> cache = new ConcurrentHashMap<>();

    private static final long AUTO_SAVE_INTERVAL_TICKS = 6000;

    public StashManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stash_data.yml");
        load();
        startAutoSave();
    }

    private void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create stash file: " + e.getMessage());
                return;
            }
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        if (!data.contains("players")) return;

        for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> base64List = data.getStringList("players." + uuidStr + ".items");
                List<ItemStack> items = new ArrayList<>();
                for (String base64 : base64List) {
                    try {
                        ItemStack item = itemFromBase64(base64);
                        if (item != null && item.getType() != Material.AIR) {
                            items.add(item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to deserialize stash item for " + uuidStr + ": " + e.getMessage());
                    }
                }
                cache.put(uuid, items);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load stash for UUID " + uuidStr + ": " + e.getMessage());
            }
        }
    }

    public void save() {
        FileConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, List<ItemStack>> entry : cache.entrySet()) {
            List<String> base64List = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                if (item != null && item.getType() != Material.AIR) {
                    try {
                        base64List.add(itemToBase64(item));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to serialize item: " + e.getMessage());
                    }
                }
            }
            data.set("players." + entry.getKey().toString() + ".items", base64List);
        }
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save stash data: " + e.getMessage());
        }
    }

    public List<ItemStack> getItems(UUID uuid) {
        List<ItemStack> items = cache.get(uuid);
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public void saveItems(UUID uuid, List<ItemStack> items) {
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                filtered.add(item.clone());
            }
        }
        if (filtered.isEmpty()) {
            cache.remove(uuid);
        } else {
            cache.put(uuid, filtered);
        }
    }

    public void addItems(UUID uuid, List<ItemStack> newItems) {
        List<ItemStack> items = cache.getOrDefault(uuid, new ArrayList<>());
        for (ItemStack item : newItems) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        cache.put(uuid, items);
    }

    public boolean isEmpty(UUID uuid) {
        List<ItemStack> items = cache.get(uuid);
        return items == null || items.isEmpty();
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::save, AUTO_SAVE_INTERVAL_TICKS, AUTO_SAVE_INTERVAL_TICKS);
    }

    private static String itemToBase64(ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private static ItemStack itemFromBase64(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }
}
