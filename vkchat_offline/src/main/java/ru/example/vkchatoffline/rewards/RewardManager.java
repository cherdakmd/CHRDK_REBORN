package ru.example.vkchatoffline.rewards;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RewardManager {
    private final VKChatOfflinePlugin plugin;
    private final Map<Integer, List<ItemStack>> pendingRewards = new ConcurrentHashMap<>();

    public RewardManager(VKChatOfflinePlugin plugin) { this.plugin = plugin; }

    public void grantAdventureReward(int vkId, List<ItemStack> loot, int reputation, int xp) {
        if (reputation > 0) try { VKChatPlugin.getInstance().getApi().addReputation(vkId, reputation); } catch (Exception ignored) {}
        if (xp > 0) plugin.getCharacterManager().addXp(vkId, xp);
        if (loot != null && !loot.isEmpty()) pendingRewards.computeIfAbsent(vkId, k -> new ArrayList<>()).addAll(loot);
    }

    public void grantBossReward(int vkId, String bossName, int level) {
        List<ItemStack> loot = plugin.getLootManager().generateLoot(level, "boss", true);
        grantAdventureReward(vkId, loot, level * 10, level * 20);
    }

    public void grantChapterReward(int vkId, int chapterId) {
        List<ItemStack> loot = new ArrayList<>();
        switch (chapterId) {
            case 1: loot.add(new ItemStack(Material.DIAMOND_SWORD)); break;
            case 2: loot.add(new ItemStack(Material.DIAMOND_PICKAXE)); break;
            case 3: loot.add(new ItemStack(Material.NETHER_STAR)); break;
            case 4: loot.add(new ItemStack(Material.TOTEM_OF_UNDYING)); break;
            case 5: loot.add(new ItemStack(Material.DIAMOND_CHESTPLATE)); break;
            case 7: loot.add(new ItemStack(Material.DRAGON_EGG)); break;
            case 13: loot.add(new ItemStack(Material.ELYTRA)); loot.add(new ItemStack(Material.NETHER_STAR, 3)); break;
        }
        grantAdventureReward(vkId, loot, chapterId * 100, chapterId * 50);
    }

    public List<ItemStack> getPendingRewards(int vkId) { return pendingRewards.remove(vkId); }
    public boolean hasPendingRewards(int vkId) { List<ItemStack> r = pendingRewards.get(vkId); return r != null && !r.isEmpty(); }
}
