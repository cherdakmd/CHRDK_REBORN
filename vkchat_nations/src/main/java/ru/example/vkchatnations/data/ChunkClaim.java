package ru.example.vkchatnations.data;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ChunkClaim {
    /** Максимальный уровень прокачки привата (5-уровневая система). */
    public static final int MAX_LEVEL = 5;

    private String worldName;
    private int x;
    private int y;
    private int z;
    private int radius;
    private UUID owner;
    private String nation;
    private List<UUID> trusted;
    private int durability;
    private int level;

    // Защита от огня (вкл/выкл)
    private boolean fireProtection = true;

    // Точка возрождения (Home)
    private double homeX = 0;
    private double homeY = 0;
    private double homeZ = 0;
    private boolean hasHome = false;

    public ChunkClaim(String worldName, int x, int y, int z, int radius, UUID owner, String nation) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.owner = owner;
        this.nation = nation;
        this.trusted = new ArrayList<>();
        this.durability = 100; // Стартовая прочность
        this.level = 1;
    }

    public ChunkClaim(String worldName, int x, int y, int z, int radius, UUID owner, String nation, List<UUID> trusted, int durability, int level, boolean fireProtection, double homeX, double homeY, double homeZ, boolean hasHome) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.owner = owner;
        this.nation = nation;
        this.trusted = trusted;
        this.durability = durability;
        this.level = level;
        this.fireProtection = fireProtection;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.hasHome = hasHome;
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getRadius() { return radius; }

    public boolean hasHome() { return hasHome; }
    public double getHomeX() { return homeX; }
    public double getHomeY() { return homeY; }
    public double getHomeZ() { return homeZ; }
    public void setHome(double x, double y, double z) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.hasHome = true;
    }
    public void removeHome() {
        this.hasHome = false;
    }

    public UUID getOwner() { return owner; }
    public String getNation() { return nation; }
    public List<UUID> getTrusted() { return trusted; }

    public int getDurability() { return durability; }
    public void setDurability(int durability) { this.durability = durability; }

    /** Максимальный запас прочности зависит от уровня: 1000 + (level-1)*500 (до 3000 на 5 ур.). */
    public int getMaxDurability() { return 1000 + (level - 1) * 500; }

    public void addDurability(int amount) { this.durability = Math.min(getMaxDurability(), this.durability + amount); }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(MAX_LEVEL, level)); }

    public boolean isFireProtectionEnabled() { return fireProtection; }
    public void setFireProtection(boolean fireProtection) { this.fireProtection = fireProtection; }

    /** Можно ли повысить уровень ещё. */
    public boolean canUpgrade() { return level < MAX_LEVEL; }

    public void addTrusted(UUID uuid) {
        if (!trusted.contains(uuid)) trusted.add(uuid);
    }

    public void removeTrusted(UUID uuid) {
        trusted.remove(uuid);
    }

    // ==========================================================
    //  5-УРОВНЕВАЯ СИСТЕМА ПРОКАЧКИ ПРИВАТА (централизованные данные)
    // ==========================================================

    /** Стоимость повышения С указанного уровня (репутация ВК). */
    public static int getUpgradeCost(int fromLevel) {
        switch (fromLevel) {
            case 1: return 300;
            case 2: return 600;
            case 3: return 1000;
            case 4: return 1500;
            default: return 0;
        }
    }

    /** Стоимость прокачки текущего уровня привата до следующего. */
    public int getNextUpgradeCost() { return getUpgradeCost(level); }

    /** Короткое название тира. */
    public static String getLevelName(int level) {
        switch (level) {
            case 1: return "Базовая защита";
            case 2: return "Антивзрыв";
            case 3: return "Покой";
            case 4: return "Огнеупорность";
            case 5: return "Цитадель";
            default: return "Базовая защита";
        }
    }

    /** Цвет тира для отображения в GUI. */
    public static ChatColor getLevelColor(int level) {
        switch (level) {
            case 1: return ChatColor.GRAY;
            case 2: return ChatColor.RED;
            case 3: return ChatColor.DARK_AQUA;
            case 4: return ChatColor.GOLD;
            case 5: return ChatColor.LIGHT_PURPLE;
            default: return ChatColor.GRAY;
        }
    }

    /** Иконка-материал тира для GUI. */
    public static Material getLevelMaterial(int level) {
        switch (level) {
            case 1: return Material.COBBLESTONE_WALL;
            case 2: return Material.TNT;
            case 3: return Material.SPAWNER;
            case 4: return Material.MAGMA_BLOCK;
            case 5: return Material.NETHERITE_BLOCK;
            default: return Material.COBBLESTONE_WALL;
        }
    }

    /** Краткое описание бонуса тира. */
    public static List<String> getLevelDescription(int level) {
        switch (level) {
            case 1: return Arrays.asList(
                    "§7• Защита от строительства и ломания",
                    "§7  чужаками",
                    "§7• Закрыт доступ к сундукам/дверям",
                    "§7  посторонних игроков");
            case 2: return Arrays.asList(
                    "§c• Полная защита блоков от взрывов:",
                    "§7  TNT, криперы, эндер-кристаллы,",
                    "§7  гасты, воронки, кровати",
                    "§c• Иммунитет к урону от взрывов",
                    "§7  игрокам и существам",
                    "§7• Прочность до §e1500");
            case 3: return Arrays.asList(
                    "§b• Запрет естественного спавна",
                    "§7  враждебных монстров",
                    "§7  (спавнеры продолжают работать)",
                    "§7• Прочность до §e2000");
            case 4: return Arrays.asList(
                    "§6• Защита блоков от поджога",
                    "§6• Запрет растекания огня",
                    "§6• Защита от растекания лавы",
                    "§7• Прочность до §e2500");
            case 5: return Arrays.asList(
                    "§d• Запрет PvP на территории",
                    "§d• Мгновенный рост ферм (ПКМ)",
                    "§d• Максимальный запас прочности",
                    "§7• Прочность до §e3000");
            default: return Arrays.asList("§7Базовая защита территории.");
        }
    }

    /** Полный список всех уровней (для GUI прокачки). */
    public static List<Integer> allLevels() { return Arrays.asList(1, 2, 3, 4, 5); }
}
