package ru.example.vkchatgear.artifacts;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * ArtifactRegistry — реестр артефактов, загружаемых из config.yml (секция artifacts).
 */
public class ArtifactRegistry {
    private final JavaPlugin plugin;
    private final Map<String, ArtifactComponent> artifacts = new LinkedHashMap<>();

    public ArtifactRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    private void loadFromConfig() {
        artifacts.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("artifacts");
        if (sec == null) {
            plugin.getLogger().info("[ArtifactRegistry] Секция artifacts не найдена — пропуск.");
            return;
        }

        for (String id : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(id);
            if (sub == null) continue;

            String name = sub.getString("name", id);
            String matName = sub.getString("material", "NETHER_STAR");
            Material mat;
            try {
                mat = Material.valueOf(matName.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                mat = Material.NETHER_STAR;
            }
            int cmd = sub.getInt("custom-model-data", 0);
            String rarity = sub.getString("rarity", "rare");
            String description = sub.getString("description", "");
            String slot = sub.getString("slot", "offhand");

            // Lore
            List<String> lore = sub.getStringList("lore");
            if (lore.isEmpty()) {
                lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + description);
            }

            // Stats
            Map<String, Double> stats = new LinkedHashMap<>();
            ConfigurationSection statsSec = sub.getConfigurationSection("stats");
            if (statsSec != null) {
                for (String key : statsSec.getKeys(false)) {
                    stats.put(key, statsSec.getDouble(key));
                }
            }

            artifacts.put(id, new ArtifactComponent(id, name, mat, cmd, rarity, description, lore, stats, slot));
        }

        plugin.getLogger().info("[ArtifactRegistry] Загружено " + artifacts.size() + " артефактов.");
    }

    public ArtifactComponent getArtifact(String id) {
        return artifacts.get(id);
    }

    public Collection<ArtifactComponent> getAll() {
        return Collections.unmodifiableCollection(artifacts.values());
    }

    public List<ArtifactComponent> getByRarity(String rarity) {
        List<ArtifactComponent> result = new ArrayList<>();
        for (ArtifactComponent a : artifacts.values()) {
            if (a.getRarity().equals(rarity)) result.add(a);
        }
        return result;
    }

    public void reload() {
        loadFromConfig();
    }
}
