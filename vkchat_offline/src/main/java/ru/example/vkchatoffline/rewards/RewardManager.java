package ru.example.vkchatoffline.rewards;

import org.bukkit.Material;
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
    private final Map<Integer, List<ItemStack>> pendingRewards = new ConcurrentHashMap<>();

    public RewardManager(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Выдать награду за поход
     */
    public void grantAdventureReward(int vkId, List<ItemStack> loot, int reputation, int xp) {
        if (reputation > 0) {
            try {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, reputation);
            } catch (Exception ignored) {}
        }

        if (xp > 0) {
            plugin.getCharacterManager().addXp(vkId, xp);
        }

        if (loot != null && !loot.isEmpty()) {
            pendingRewards.computeIfAbsent(vkId, k -> new ArrayList<>()).addAll(loot);
        }
    }

    /**
     * Выдать награду за босса
     */
    public void grantBossReward(int vkId, String bossName, int level) {
        List<ItemStack> loot = plugin.getLootManager().generateLoot(level, "boss", true);
        int reputation = level * 10;
        grantAdventureReward(vkId, loot, reputation, level * 20);
    }

    /**
     * Выдать награду за главу кампании
     */
    public void grantChapterReward(int vkId, int chapterId) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = new Random();

        switch (chapterId) {
            case 1: loot.add(new ItemStack(Material.DIAMOND_SWORD)); break;
            case 2: loot.add(new ItemStack(Material.DIAMOND_PICKAXE)); break;
            case 3: loot.add(new ItemStack(Material.NETHER_STAR)); break;
            case 4: loot.add(new ItemStack(Material.TOTEM_OF_UNDYING)); break;
            case 5: loot.add(new ItemStack(Material.DIAMOND_CHESTPLATE)); break;
            case 6: loot.add(new ItemStack(Material.TOTEM_OF_UNDYING)); loot.add(new ItemStack(Material.NETHER_STAR)); break;
            case 7: loot.add(new ItemStack(Material.DRAGON_EGG)); break;
            case 8: loot.add(new ItemStack(Material.NETHER_STAR)); loot.add(new ItemStack(Material.DIAMOND_BLOCK)); break;
            case 9: loot.add(new ItemStack(Material.NETHERITE_SWORD)); break;
            case 10: loot.add(new ItemStack(Material.NETHERITE_CHESTPLATE)); loot.add(new ItemStack(Material.NETHERITE_LEGGINGS)); break;
            case 11: loot.add(new ItemStack(Material.ELYTRA)); loot.add(new ItemStack(Material.NETHER_STAR)); break;
            case 12: loot.add(new ItemStack(Material.NETHERITE_HELMET)); loot.add(new ItemStack(Material.NETHERITE_CHESTPLATE)); loot.add(new ItemStack(Material.NETHERITE_LEGGINGS)); loot.add(new ItemStack(Material.NETHERITE_BOOTS)); break;
            case 13: loot.add(new ItemStack(Material.DRAGON_EGG)); loot.add(new ItemStack(Material.ELYTRA)); loot.add(new ItemStack(Material.NETHER_STAR, 3)); loot.add(new ItemStack(Material.DIAMOND_BLOCK, 5)); break;
        }

        int reputation = chapterId * 100;
        grantAdventureReward(vkId, loot, reputation, chapterId * 50);
    }

    /**
     * Выдать ежедневную награду
     */
    public void grantDailyReward(int vkId) {
        List<ItemStack> loot = new ArrayList<>();
        Random rand = new Random();
        loot.add(new ItemStack(Material.DIAMOND, 1 + rand.nextInt(2)));
        loot.add(new ItemStack(Material.GOLD_INGOT, 3 + rand.nextInt(5)));
        grantAdventureReward(vkId, loot, 50, 100);
    }

    /**
     * Получить ожидающие награды
     */
    public List<ItemStack> getPendingRewards(int vkId) {
        return pendingRewards.remove(vkId);
    }

    /**
     * Проверить наличие ожидающих наград
     */
    public boolean hasPendingRewards(int vkId) {
        List<ItemStack> rewards = pendingRewards.get(vkId);
        return rewards != null && !rewards.isEmpty();
    }
}
