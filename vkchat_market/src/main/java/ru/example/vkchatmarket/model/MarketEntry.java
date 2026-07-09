package ru.example.vkchatmarket.model;

import org.bukkit.Material;

public class MarketEntry {
    private final String id;
    private final Material material;
    private final String displayName;
    private final MarketCategory category;
    private final int basePrice;

    public MarketEntry(String id, Material material, String displayName, MarketCategory category, int basePrice) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.category = category;
        this.basePrice = basePrice;
    }

    public String id() { return id; }
    public Material material() { return material; }
    public String displayName() { return displayName; }
    public MarketCategory category() { return category; }
    public int basePrice() { return basePrice; }
}
