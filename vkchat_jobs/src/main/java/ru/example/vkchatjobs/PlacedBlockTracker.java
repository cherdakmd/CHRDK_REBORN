package ru.example.vkchatjobs;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class PlacedBlockTracker implements Listener {
    private static final int MAX_TRACKED = 500000;
    private final VKChatJobsPlugin plugin;
    private final Set<String> placed = new HashSet<>();
    private final File file;
    private FileConfiguration data;

    public PlacedBlockTracker(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed_blocks.yml");
        load();
    }

    private void load() {
        data = YamlConfiguration.loadConfiguration(file);
        placed.clear();
        placed.addAll(data.getStringList("blocks"));
    }

    public void save() {
        try {
            data.set("blocks", new java.util.ArrayList<>(placed));
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить placed_blocks.yml: " + e.getMessage());
        }
    }

    public void startAutoSave() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::pruneAndSave, 12000L, 12000L);
    }

    public void shutdown() {
        save();
    }

    private void pruneAndSave() {
        if (placed.size() > MAX_TRACKED) {
            Iterator<String> it = placed.iterator();
            int toRemove = placed.size() - MAX_TRACKED;
            while (it.hasNext() && toRemove > 0) {
                it.next();
                it.remove();
                toRemove--;
            }
        }
        save();
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("anti-abuse.track-placed-blocks", true)) return;
        Material m = e.getBlockPlaced().getType();
        if (isTrackedMaterial(m)) placed.add(key(e.getBlockPlaced()));
    }

    public boolean consumeIfPlaced(Block block) {
        if (block == null) return false;
        String key = key(block);
        if (placed.remove(key)) {
            if (plugin.getConfig().getBoolean("anti-abuse.notify-placed-block", false)) {
                // notification deliberately silent by default
            }
            return true;
        }
        return false;
    }

    public boolean isTrackedMaterial(Material m) {
        String n = m.name();
        return isOreName(n) || n.endsWith("_LOG") || n.endsWith("_WOOD");
    }

    public boolean isOreName(String n) {
        return n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || n.equals("NETHER_GOLD_ORE");
    }

    private String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }
}
