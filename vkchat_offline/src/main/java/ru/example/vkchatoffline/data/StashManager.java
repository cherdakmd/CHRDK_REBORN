package ru.example.vkchatoffline.data;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
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

    private void load() {
        if (!file.exists()) try { plugin.getDataFolder().mkdirs(); file.createNewFile(); } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); } catch (Exception ignored) {}
    }

    public List<ItemStack> getItems(UUID uuid) {
        List<ItemStack> items = new ArrayList<>();
        String path = "players." + uuid.toString() + ".items";
        if (data.contains(path)) {
            for (Map<?, ?> map : data.getMapList(path)) {
                try {
                    ItemStack item = ItemStack.deserialize((Map<String, Object>) map);
                    if (item != null) items.add(item);
                } catch (Exception ignored) {}
            }
        }
        return items;
    }

    public void saveItems(UUID uuid, List<ItemStack> items) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) serialized.add(item.serialize());
        }
        data.set("players." + uuid.toString() + ".items", serialized);
        save();
    }

    public void addItems(UUID uuid, List<ItemStack> newItems) {
        List<ItemStack> items = getItems(uuid);
        items.addAll(newItems);
        saveItems(uuid, items);
    }

    public boolean isEmpty(UUID uuid) { return getItems(uuid).isEmpty(); }
}
