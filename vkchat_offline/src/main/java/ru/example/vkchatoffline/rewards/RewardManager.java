package ru.example.vkchatoffline.rewards;

import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер наград для сервера
 */
public class RewardManager {
    private final VKChatOfflinePlugin plugin;

    public RewardManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Выдать награду за поход
     */
    public void grantAdventureReward(int vkId, List<ItemStack> loot, int reputation, int xp) {
        // Добавить репутацию
        if (reputation > 0) {
            try {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, reputation);
            } catch (Exception ignored) {}
        }

        // Добавить опыт персонажа
        if (xp > 0) {
            plugin.getCharacterManager().addXp(vkId, xp);
        }

        // Предметы сохраняются в кэш для выдачи при входе на сервер
        if (loot != null && !loot.isEmpty()) {
            pendingRewards.computeIfAbsent(vkId, k -> new ArrayList<>()).addAll(loot);
        }
    }

    // Кэш ожидающих наград
    private final Map<Integer, List<ItemStack>> pendingRewards = new ConcurrentHashMap<>();

    /**
     * Получить ожидающие награды
     */
    public List<ItemStack> getPendingRewards(int vkId) {
        return pendingRewards.remove(vkId);
    }

    /**
     * Выдать награду за босса
     */
    public void grantBossReward(int vkId, String bossName, int level) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = new Random();

        // Гарантированный лут
        loot.add(new ItemStack(org.bukkit.Material.DIAMOND, 3 + rand.nextInt(5)));
        loot.add(new ItemStack(org.bukkit.Material.GOLD_INGOT, 5 + rand.nextInt(10)));

        // Шанс на редкие предметы
        if (rand.nextInt(100) < 20) {
            loot.add(new ItemStack(org.bukkit.Material.TOTEM_OF_UNDYING));
        }
        if (rand.nextInt(100) < 10) {
            loot.add(new ItemStack(org.bukkit.Material.NETHER_STAR));
        }
        if (rand.nextInt(100) < 5) {
            loot.add(new ItemStack(org.bukkit.Material.ELYTRA));
        }

        // Репутация
        int reputation = level * 10;

        // Выдать награды
        grantAdventureReward(vkId, loot, reputation, level * 20);
    }

    /**
     * Выдать награду за главу кампании
     */
    public void grantChapterReward(int vkId, int chapterId) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = new Random();

        // Предметы по главам
        switch (chapterId) {
            case 1: // Редкий меч
                loot.add(new ItemStack(org.bukkit.Material.DIAMOND_SWORD));
                break;
            case 2: // Эпическая кирка
                loot.add(new ItemStack(org.bukkit.Material.DIAMOND_PICKAXE));
                break;
            case 3: // Легендарный артефакт
                loot.add(new ItemStack(org.bukkit.Material.NETHER_STAR));
                break;
            case 4: // Мифическое зелье
                loot.add(new ItemStack(org.bukkit.Material.TOTEM_OF_UNDYING));
                break;
            case 5: // Эпическая броня
                loot.add(new ItemStack(org.bukkit.Material.DIAMOND_CHESTPLATE));
                break;
            case 6: // Легендарный тотем
                loot.add(new ItemStack(org.bukkit.Material.TOTEM_OF_UNDYING));
                loot.add(new ItemStack(org.bukkit.Material.NETHER_STAR));
                break;
            case 7: // Мифический артефакт
                loot.add(new ItemStack(org.bukkit.Material.DRAGON_EGG));
                break;
            case 8: // Легендарные часы
                loot.add(new ItemStack(org.bukkit.Material.NETHER_STAR));
                loot.add(new ItemStack(org.bukkit.Material.DIAMOND_BLOCK));
                break;
            case 9: // Мифический меч
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_SWORD));
                break;
            case 10: // Легендарная броня
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_CHESTPLATE));
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_LEGGINGS));
                break;
            case 11: // Мифические крылья
                loot.add(new ItemStack(org.bukkit.Material.ELYTRA));
                loot.add(new ItemStack(org.bukkit.Material.NETHER_STAR));
                break;
            case 12: // Легендарный набор
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_HELMET));
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_CHESTPLATE));
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_LEGGINGS));
                loot.add(new ItemStack(org.bukkit.Material.NETHERITE_BOOTS));
                break;
            case 13: // Мифический набор
                loot.add(new ItemStack(org.bukkit.Material.DRAGON_EGG));
                loot.add(new ItemStack(org.bukkit.Material.ELYTRA));
                loot.add(new ItemStack(org.bukkit.Material.NETHER_STAR, 3));
                loot.add(new ItemStack(org.bukkit.Material.DIAMOND_BLOCK, 5));
                break;
        }

        // Репутация
        int reputation = chapterId * 100;

        // Выдать награды
        grantAdventureReward(vkId, loot, reputation, chapterId * 50);
    }

    /**
     * Выдать ежедневную награду
     */
    public void grantDailyReward(int vkId) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = new Random();

        loot.add(new ItemStack(org.bukkit.Material.DIAMOND, 1 + rand.nextInt(2)));
        loot.add(new ItemStack(org.bukkit.Material.GOLD_INGOT, 3 + rand.nextInt(5)));
        loot.add(new ItemStack(org.bukkit.Material.IRON_INGOT, 5 + rand.nextInt(10)));

        grantAdventureReward(vkId, loot, 50, 100);
    }
}
