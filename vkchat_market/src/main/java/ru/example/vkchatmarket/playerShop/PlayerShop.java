package ru.example.vkchatmarket.playerShop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerShop {
    private UUID ownerUuid;
    private String ownerName;
    private int price;
    private Location location;
    private List<ItemStack> items;

    public PlayerShop(UUID ownerUuid, String ownerName, int price, Location location) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.price = price;
        this.location = location;
        this.items = new ArrayList<>();
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public int getPrice() { return price; }
    public Location getLocation() { return location; }
    public List<ItemStack> getItems() { return items; }

    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void setPrice(int price) { this.price = price; }
    public void setItems(List<ItemStack> items) { this.items = items; }

    public String locationKey() {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
