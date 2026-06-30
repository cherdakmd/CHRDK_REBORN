package ru.example.vkchatend.managers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatend.VKChatEndPlugin;

import java.util.*;

/**
 * [7] Система улучшения шалкер-боксов
 * [8] Система улучшения элитр
 */
public class EndShulkerManager {
    private final VKChatEndPlugin plugin;

    // Уровни шалкер-боксов
    public enum ShulkerTier {
        BASIC("Базовый", 27, ChatColor.WHITE),
        ENHANCED("Улучшенный", 36, ChatColor.GREEN),
        SUPERIOR("Превосходный", 45, ChatColor.BLUE),
        LEGENDARY("Легендарный", 54, ChatColor.LIGHT_PURPLE),
        MYTHICAL("Мифический", 63, ChatColor.GOLD);

        public final String displayName;
        public final int slots;
        public final ChatColor color;

        ShulkerTier(String displayName, int slots, ChatColor color) {
            this.displayName = displayName;
            this.slots = slots;
            this.color = color;
        }
    }

    // Уровни элитр
    public enum ElytraTier {
        BASIC("Базовые", 1.0, ChatColor.WHITE),
        ENHANCED("Улучшенные", 1.25, ChatColor.GREEN),
        SUPERIOR("Превосходные", 1.5, ChatColor.BLUE),
        LEGENDARY("Легендарные", 2.0, ChatColor.LIGHT_PURPLE),
        MYTHICAL("Мифические", 3.0, ChatColor.GOLD);

        public final String displayName;
        public final double speedMultiplier;
        public final ChatColor color;

        ElytraTier(String displayName, double speedMultiplier, ChatColor color) {
            this.displayName = displayName;
            this.speedMultiplier = speedMultiplier;
            this.color = color;
        }
    }

    public EndShulkerManager(VKChatEndPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Улучшить шалкер-бокс
     */
    public boolean upgradeShulkerBox(Player p, ItemStack shulker) {
        if (shulker.getType() != Material.SHULKER_BOX) {
            p.sendMessage(ChatColor.RED + "Это не шалкер-бокс!");
            return false;
        }

        ShulkerTier currentTier = getShulkerTier(shulker);
        ShulkerTier nextTier = getNextTier(currentTier);
        if (nextTier == null) {
            p.sendMessage(ChatColor.RED + "Шалкер максимального уровня!");
            return false;
        }

        int cost = getUpgradeCost(currentTier);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп. (у тебя " + rep + ")");
            return false;
        }

        // Материалы
        int shellsNeeded = (currentTier.ordinal() + 1) * 2;
        if (!p.getInventory().containsAtLeast(new ItemStack(Material.SHULKER_SHELL), shellsNeeded)) {
            p.sendMessage(ChatColor.RED + "Нужно " + shellsNeeded + " панцирей шалкера!");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        p.getInventory().removeItem(new ItemStack(Material.SHULKER_SHELL, shellsNeeded));

        // Улучшение
        applyShulkerUpgrade(shulker, nextTier);

        p.sendMessage(nextTier.color + "✦ Шалкер-бокс улучшен до: " + nextTier.displayName + " (" + nextTier + " слотов)");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);

        return true;
    }

    /**
     * Улучшить элитры
     */
    public boolean upgradeElytra(Player p, ItemStack elytra) {
        if (elytra.getType() != Material.ELYTRA) {
            p.sendMessage(ChatColor.RED + "Это не элитры!");
            return false;
        }

        ElytraTier currentTier = getElytraTier(elytra);
        ElytraTier nextTier = getNextElytraTier(currentTier);
        if (nextTier == null) {
            p.sendMessage(ChatColor.RED + "Элитры максимального уровня!");
            return false;
        }

        int cost = getElytraUpgradeCost(currentTier);
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) return false;

        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Нужно " + cost + " реп.");
            return false;
        }

        // Материалы
        int membranesNeeded = (currentTier.ordinal() + 1) * 3;
        if (!p.getInventory().containsAtLeast(new ItemStack(Material.PHANTOM_MEMBRANE), membranesNeeded)) {
            p.sendMessage(ChatColor.RED + "Нужно " + membranesNeeded + " мембран фантома!");
            return false;
        }

        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        p.getInventory().removeItem(new ItemStack(Material.PHANTOM_MEMBRANE, membranesNeeded));

        // Улучшение
        applyElytraUpgrade(elytra, nextTier);

        p.sendMessage(nextTier.color + "✦ Элитры улучшены до: " + nextTier.displayName + " (скорость x" + String.format("%.1f", nextTier.speedMultiplier) + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 0.5f);

        return true;
    }

    /**
     * Получить уровень шалкера
     */
    public ShulkerTier getShulkerTier(ItemStack shulker) {
        if (!shulker.hasItemMeta()) return ShulkerTier.BASIC;
        ItemMeta meta = shulker.getItemMeta();
        String tierName = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "shulker_tier"), PersistentDataType.STRING);
        if (tierName == null) return ShulkerTier.BASIC;
        try { return ShulkerTier.valueOf(tierName); } catch (Exception e) { return ShulkerTier.BASIC; }
    }

    /**
     * Получить уровень элитр
     */
    public ElytraTier getElytraTier(ItemStack elytra) {
        if (!elytra.hasItemMeta()) return ElytraTier.BASIC;
        ItemMeta meta = elytra.getItemMeta();
        String tierName = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "elytra_tier"), PersistentDataType.STRING);
        if (tierName == null) return ElytraTier.BASIC;
        try { return ElytraTier.valueOf(tierName); } catch (Exception e) { return ElytraTier.BASIC; }
    }

    private ShulkerTier getNextTier(ShulkerTier current) {
        int next = current.ordinal() + 1;
        ShulkerTier[] values = ShulkerTier.values();
        return next < values.length ? values[next] : null;
    }

    private ElytraTier getNextElytraTier(ElytraTier current) {
        int next = current.ordinal() + 1;
        ElytraTier[] values = ElytraTier.values();
        return next < values.length ? values[next] : null;
    }

    private int getUpgradeCost(ShulkerTier current) {
        return 1000 * (current.ordinal() + 1);
    }

    private int getElytraUpgradeCost(ElytraTier current) {
        return 2000 * (current.ordinal() + 1);
    }

    private void applyShulkerUpgrade(ItemStack shulker, ShulkerTier tier) {
        ItemMeta meta = shulker.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shulker_tier"), PersistentDataType.STRING, tier.name());
        meta.setDisplayName(tier.color + "✦ " + tier.displayName + " Шалкер-бокс");
        shulker.setItemMeta(meta);
    }

    private void applyElytraUpgrade(ItemStack elytra, ElytraTier tier) {
        ItemMeta meta = elytra.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "elytra_tier"), PersistentDataType.STRING, tier.name());
        meta.setDisplayName(tier.color + "✦ " + tier.displayName + " Элитры");
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add(ChatColor.GRAY + "Скорость: x" + String.format("%.1f", tier.speedMultiplier));
        meta.setLore(lore);
        elytra.setItemMeta(meta);
    }
}
