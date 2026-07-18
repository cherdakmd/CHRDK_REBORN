package ru.example.vkchatgear.artifacts;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * ArtifactComponent — данные об артефакте ( immutable record ).
 */
public final class ArtifactComponent {
    private final String id;
    private final String name;
    private final Material material;
    private final int customModelData;
    private final String rarity; // "common", "rare", "epic", "legendary", "ancient"
    private final String description;
    private final List<String> lore;
    private final Map<String, Double> stats; // stat-key → value (damage, defense, speed, etc.)
    private final String slot; // "offhand" — только в дополнительной руке

    public ArtifactComponent(String id, String name, Material material, int customModelData,
                             String rarity, String description, List<String> lore,
                             Map<String, Double> stats, String slot) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.customModelData = customModelData;
        this.rarity = rarity;
        this.description = description;
        this.lore = lore;
        this.stats = stats;
        this.slot = slot;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public String getRarity() { return rarity; }
    public String getDescription() { return description; }
    public List<String> getLore() { return lore; }
    public Map<String, Double> getStats() { return stats; }
    public String getSlot() { return slot; }
}
