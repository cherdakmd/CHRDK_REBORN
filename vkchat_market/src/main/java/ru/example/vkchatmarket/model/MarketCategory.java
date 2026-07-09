package ru.example.vkchatmarket.model;

public enum MarketCategory {
    ORES, FOOD, WOOD, BLOCKS, MOBS, DECOR, POTIONS, NETHER;

    public String configKey() {
        return name().toLowerCase();
    }

    public static MarketCategory fromConfig(String key) {
        try { return valueOf(key.toUpperCase()); } catch (Exception e) { return null; }
    }
}
