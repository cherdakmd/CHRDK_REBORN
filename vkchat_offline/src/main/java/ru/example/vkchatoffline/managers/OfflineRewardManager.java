package ru.example.vkchatoffline.managers;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatoffline.VKChatOfflinePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Награды, продажа stash и оценка обычных предметов Offline 2.0.
 *
 * Вынесено из AdventureManager без изменения формата stash и adventures.yml.
 */
public class OfflineRewardManager {
    private final VKChatOfflinePlugin plugin;
    private final BiConsumer<Integer, String> journal;
    private final Runnable saveAll;

    public OfflineRewardManager(VKChatOfflinePlugin plugin, BiConsumer<Integer, String> journal, Runnable saveAll) {
        this.plugin = plugin;
        this.journal = journal;
        this.saveAll = saveAll;
    }

    public String buildSellStashPreview(int vkId) {
        UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
        if (uuid == null) return "❌ Аккаунт не привязан.";
        List<ItemStack> items = plugin.getStashManager().getItems(uuid);
        int[] calc = calculateStashSale(items);
        return "💰 Продажа тайника\n\n" +
                "Будет продано обычных предметов: " + calc[0] + " шт.\n" +
                "Ожидаемая выручка: " + calc[1] + " реп.\n\n" +
                "Редкие предметы, ключи, фрагменты и предметы с именами не продаются.";
    }

    public String sellStash(int vkId) {
        UUID uuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(vkId);
        if (uuid == null) return "❌ Аккаунт не привязан.";
        List<ItemStack> items = plugin.getStashManager().getItems(uuid);
        int rep = 0;
        int sold = 0;
        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack item : items) {
            if (isSellableStashItem(item)) {
                rep += estimateStashItemRep(item);
                sold += item.getAmount();
            } else {
                keep.add(item);
            }
        }
        if (sold <= 0) return "В тайнике нет обычных предметов для продажи.";
        VKChatPlugin.getInstance().getApi().addReputation(vkId, rep);
        plugin.getStashManager().saveItems(uuid, keep);
        journal.accept(vkId, "💰 Продан stash: " + sold + " предметов за " + rep + " реп.");
        saveAll.run();
        return "✅ Продано из тайника: " + sold + " предметов\nПолучено: +" + rep + " репутации ВК";
    }

    public int[] calculateStashSale(List<ItemStack> items) {
        int sold = 0;
        int rep = 0;
        for (ItemStack item : items) {
            if (isSellableStashItem(item)) {
                sold += item.getAmount();
                rep += estimateStashItemRep(item);
            }
        }
        return new int[]{sold, rep};
    }

    public boolean isSellableStashItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName() || item.getItemMeta().hasLore())) return false;
        String n = item.getType().name();
        return !(n.contains("NETHERITE") || n.contains("TOTEM") || n.contains("ENCHANTED") || n.contains("NETHER_STAR"));
    }

    public int estimateStashItemRep(ItemStack item) {
        int amount = item.getAmount();
        String n = item.getType().name();
        double price = 1.0;
        if (n.contains("DIAMOND")) price = 10;
        else if (n.contains("GOLD")) price = 4;
        else if (n.contains("IRON")) price = 2;
        else if (n.contains("EMERALD")) price = 6;
        else if (n.contains("BLAZE") || n.contains("ENDER") || n.contains("GHAST")) price = 5;
        else if (n.contains("LOG") || n.contains("STONE") || n.contains("COBBLE")) price = 0.3;
        return Math.max(1, (int) Math.round(price * amount));
    }
}
