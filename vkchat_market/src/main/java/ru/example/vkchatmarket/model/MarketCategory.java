package ru.example.vkchatmarket.model;

import org.bukkit.Material;

public enum MarketCategory {
    ORES(Material.IRON_INGOT),
    FOOD(Material.BREAD),
    WOOD(Material.OAK_LOG),
    BLOCKS(Material.BRICKS),
    STONES(Material.COBBLESTONE),
    MOBS(Material.BONE),
    ARMOR(Material.DIAMOND_CHESTPLATE),
    WEAPONS(Material.DIAMOND_SWORD),
    TOOLS(Material.DIAMOND_PICKAXE),
    DECOR(Material.WHITE_WOOL),
    POTIONS(Material.BREWING_STAND),
    NETHER(Material.NETHERRACK);

    private final Material icon;

    MarketCategory(Material icon) { this.icon = icon; }

    public String configKey() { return name().toLowerCase(); }
    public Material icon() { return icon; }

    public static MarketCategory fromConfig(String key) {
        try { return valueOf(key.toUpperCase()); } catch (Exception e) { return null; }
    }
}
