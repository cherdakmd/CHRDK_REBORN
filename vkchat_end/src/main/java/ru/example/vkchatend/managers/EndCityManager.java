package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер эндер-городов — procedural генерация и лут
 */
public class EndCityManager {
    private final VKChatEndPlugin plugin;
    private final Map<String, CityData> discoveredCities = new ConcurrentHashMap<>();

    // Типы городов
    public enum CityType {
        RUINS("Руины", 0, ChatColor.GRAY),
        OUTPOST("Аванпост", 1, ChatColor.GREEN),
        FORTRESS("Крепость", 2, ChatColor.BLUE),
        CITADEL("Цитадель", 3, ChatColor.LIGHT_PURPLE),
        PALACE("Дворец", 4, ChatColor.GOLD),
        VOID_TEMPLE("Храм Бездны", 5, ChatColor.DARK_PURPLE);

        public final String displayName;
        public final int difficulty;
        public final ChatColor color;

        CityType(String displayName, int difficulty, ChatColor color) {
            this.displayName = displayName;
            this.difficulty = difficulty;
            this.color = color;
        }
    }

    private static class CityData {
        String name;
        CityType type;
        Location location;
        boolean explored;
        long discoveryTime;

        CityData(String name, CityType type, Location location) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.explored = false;
            this.discoveryTime = System.currentTimeMillis();
        }
    }

    public EndCityManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Получить лут из города
     */
    public List<ItemStack> getCityLoot(CityType cityType, Player p) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = ThreadLocalRandom.current();

        switch (cityType) {
            case RUINS:
                loot.add(new ItemStack(Material.ENDER_PEARL, 2 + rand.nextInt(4)));
                loot.add(new ItemStack(Material.OBSIDIAN, 4 + rand.nextInt(8)));
                if (rand.nextInt(100) < 20) loot.add(new ItemStack(Material.SHULKER_SHELL, 1));
                break;

            case OUTPOST:
                loot.add(new ItemStack(Material.ENDER_PEARL, 4 + rand.nextInt(6)));
                loot.add(new ItemStack(Material.DIAMOND, 1 + rand.nextInt(3)));
                loot.add(new ItemStack(Material.SHULKER_SHELL, 1 + rand.nextInt(2)));
                if (rand.nextInt(100) < 30) loot.add(new ItemStack(Material.ELYTRA));
                break;

            case FORTRESS:
                loot.add(new ItemStack(Material.DIAMOND, 3 + rand.nextInt(5)));
                loot.add(new ItemStack(Material.SHULKER_SHELL, 2 + rand.nextInt(3)));
                loot.add(new ItemStack(Material.ELYTRA));
                loot.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + rand.nextInt(2)));
                break;

            case CITADEL:
                loot.add(new ItemStack(Material.DIAMOND, 5 + rand.nextInt(8)));
                loot.add(new ItemStack(Material.SHULKER_SHELL, 3 + rand.nextInt(4)));
                loot.add(new ItemStack(Material.ELYTRA));
                loot.add(new ItemStack(Material.NETHERITE_SCRAP, 2 + rand.nextInt(3)));
                loot.add(new ItemStack(Material.NETHER_STAR, 1));
                break;

            case PALACE:
                loot.add(new ItemStack(Material.DIAMOND, 8 + rand.nextInt(12)));
                loot.add(new ItemStack(Material.SHULKER_SHELL, 5 + rand.nextInt(5)));
                loot.add(new ItemStack(Material.ELYTRA, 2));
                loot.add(new ItemStack(Material.NETHERITE_INGOT, 1));
                loot.add(new ItemStack(Material.NETHER_STAR, 1 + rand.nextInt(2)));
                break;

            case VOID_TEMPLE:
                loot.add(new ItemStack(Material.NETHERITE_INGOT, 2 + rand.nextInt(3)));
                loot.add(new ItemStack(Material.NETHER_STAR, 2));
                loot.add(new ItemStack(Material.ELYTRA, 3));
                loot.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
                break;
        }

        return loot;
    }

    /**
     * Сгенерировать название города
     */
    public String generateCityName() {
        String[] prefixes = {"Древний", "Забытый", "Проклятый", "Таинственный", "Утерянный", "Великий", "Тёмный", "Светлый"};
        String[] suffixes = {"город", "град", "крепость", "обитель", "святилище", "башня", "замок", "храм"};
        return prefixes[ThreadLocalRandom.current().nextInt(prefixes.length)] + " " +
               suffixes[ThreadLocalRandom.current().nextInt(suffixes.length)];
    }

    /**
     * Получить тип города по уровню игрока
     */
    public CityType getCityTypeForLevel(int endLevel) {
        if (endLevel >= 8) return CityType.VOID_TEMPLE;
        if (endLevel >= 6) return CityType.PALACE;
        if (endLevel >= 4) return CityType.CITADEL;
        if (endLevel >= 3) return CityType.FORTRESS;
        if (endLevel >= 1) return CityType.OUTPOST;
        return CityType.RUINS;
    }

    /**
     * Получить количество открытых городов
     */
    public int getDiscoveredCityCount() {
        return discoveredCities.size();
    }

    /**
     * Получить информацию о городе
     */
    public String getCityInfo(CityType type) {
        return type.color + type.displayName + ChatColor.GRAY + " (сложность: " + type.difficulty + ")";
    }
}
