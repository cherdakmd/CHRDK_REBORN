package ru.example.vkchatgear.runes;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatgear.GearManager;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * RuneListener — обработка нанесения рун и кристаллов заточки на предметы (drag-and-drop).
 *
 * Покупки рун/кристаллов/свитков теперь обрабатываются в RuneCommand (GUI с категориями).
 * Этот listener отвечает ТОЛЬКО за применение:
 * 1. Кристаллов Заточки (перетаскивание кристалла на предмет)
 * 2. Рун (перетаскивание руны на предмет)
 */
public class RuneListener implements Listener {
    private final VKChatGearPlugin plugin;

    public RuneListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Применение Кристаллов Заточки и Рун на предметы (drag-and-drop в инвентаре).
     */
    @EventHandler
    public void onRuneApply(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) return;

        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();

        if (cursor == null || current == null || cursor.getType() == Material.AIR || current.getType() == Material.AIR) return;

        // --- 1. Кристаллы Заточки ---
        if (cursor.hasItemMeta()) {
            NamespacedKey tierKey = new NamespacedKey(plugin, "crystal_tier");
            if (cursor.getItemMeta().getPersistentDataContainer().has(tierKey, PersistentDataType.STRING)) {
                handleCrystalApply(e, cursor, current, tierKey);
                return;
            }
        }

        // --- 2. Руны ---
        if (cursor.getType() == Material.NETHER_STAR && cursor.hasItemMeta()) {
            NamespacedKey key = new NamespacedKey(plugin, "rune_name");
            if (cursor.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                handleRuneApply(e, cursor, current, key);
                return;
            }
        }
    }

    // ═══════════════════════════════════════════
    // КРИСТАЛЛЫ ЗАТОЧКИ
    // ═══════════════════════════════════════════

    private void handleCrystalApply(InventoryClickEvent e, ItemStack cursor, ItemStack current, NamespacedKey tierKey) {
        Player p = (Player) e.getWhoClicked();
        if (!plugin.getGearManager().isGear(current.getType())) {
            p.sendMessage(ChatColor.RED + "Кристаллы Заточки можно применить только на Оружие, Инструменты или Броню!");
            return;
        }

        String tier = cursor.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        NamespacedKey lvlKey = new NamespacedKey(plugin, "upgrade_level");

        ItemMeta targetMeta = current.getItemMeta();
        int currentLvl = targetMeta.getPersistentDataContainer().getOrDefault(lvlKey, PersistentDataType.INTEGER, 0);

        int maxUpgrade = plugin.getConfig().getInt("hardcore-forging.max-upgrade-level", 25);
        int commonMax = Math.min(plugin.getConfig().getInt("hardcore-forging.crystals.tiers.common.to", 10), maxUpgrade);
        int rareFrom = Math.min(plugin.getConfig().getInt("hardcore-forging.crystals.tiers.rare.from", commonMax), maxUpgrade);
        int rareMax = Math.min(plugin.getConfig().getInt("hardcore-forging.crystals.tiers.rare.to", 15), maxUpgrade);
        int legendaryFrom = Math.min(plugin.getConfig().getInt("hardcore-forging.crystals.tiers.legendary.from", rareMax), maxUpgrade);
        int legendaryMax = Math.min(plugin.getConfig().getInt("hardcore-forging.crystals.tiers.legendary.to", 20), maxUpgrade);
        int ancientFrom = Math.min(plugin.getConfig().getInt("hardcore-forging.crystals.tiers.ancient.from", legendaryMax), maxUpgrade);

        int tierFrom;
        int tierTo;
        if (tier.equals("common")) { tierFrom = 0; tierTo = commonMax; }
        else if (tier.equals("rare")) { tierFrom = rareFrom; tierTo = rareMax; }
        else if (tier.equals("legendary")) { tierFrom = legendaryFrom; tierTo = legendaryMax; }
        else { tierFrom = ancientFrom; tierTo = maxUpgrade; }

        if (currentLvl < tierFrom || currentLvl >= tierTo) {
            p.sendMessage(ChatColor.RED + "Этот кристалл подходит только для заточки от +" + tierFrom + " до +" + tierTo + "!");
            return;
        }

        int applyCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.crystal-apply-cost", 50);
        if (!plugin.getGearManager().takeVkReputation(p, applyCost, "заточка предмета")) {
            e.setCancelled(true);
            return;
        }

        int roll = new java.util.Random().nextInt(100);
        int successChance = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".success",
                tier.equals("common") ? 90 : tier.equals("rare") ? 60 : tier.equals("legendary") ? 35 : 25);
        boolean success = roll < successChance;

        e.setCancelled(true);
        e.getCursor().setAmount(cursor.getAmount() - 1);

        if (success) {
            int newLvl = currentLvl + 1;
            plugin.getGearManager().updateGearUpgradeLevel(current, newLvl);

            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
            p.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            p.sendMessage(ChatColor.GREEN + "✨ Успех! Предмет успешно заточен на +" + newLvl + "!");
            plugin.getGearManager().awakenMilestoneEnchant(current, p, newLvl);
        } else {
            boolean savedByScroll = false;
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() == Material.PAPER && item.hasItemMeta()) {
                    ItemMeta itemMeta = item.getItemMeta();
                    if (itemMeta.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_safety_scroll"), PersistentDataType.INTEGER)) {
                        int amt = item.getAmount();
                        if (amt > 1) {
                            item.setAmount(amt - 1);
                        } else {
                            p.getInventory().setItem(i, null);
                        }
                        savedByScroll = true;
                        break;
                    }
                }
            }

            if (savedByScroll) {
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_SHULKER_TELEPORT, 1f, 1.2f);
                p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "🛡 [Свиток Сохранения] Заточка не удалась, но свиток спас ваш предмет от снижения уровня!");
                return;
            }

            int destroyChance;
            if (tier.equals("ancient")) destroyChance = plugin.getConfig().getInt("hardcore-forging.destroy-chance.crystal-ancient", 10);
            else if (tier.equals("legendary")) destroyChance = plugin.getConfig().getInt("hardcore-forging.destroy-chance.crystal-legendary", 6);
            else if (tier.equals("rare")) destroyChance = plugin.getConfig().getInt("hardcore-forging.destroy-chance.crystal-rare", 2);
            else destroyChance = 0;

            if (destroyChance > 0 && new java.util.Random().nextInt(100) < destroyChance) {
                e.setCurrentItem(null);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1f, 0.5f);
                p.sendMessage(ChatColor.DARK_RED + "💥 Провал заточки уничтожил предмет!");
                return;
            }

            int newLvl = currentLvl;
            String penaltyMsg = "Заточка осталась прежней.";

            if (tier.equals("common")) {
                int downgradeChance = plugin.getConfig().getInt("hardcore-forging.crystals.tiers.common.downgrade-chance", 25);
                if (new java.util.Random().nextInt(100) < downgradeChance) {
                    newLvl = Math.max(0, currentLvl - 1);
                    penaltyMsg = "Заточка снизилась до +" + newLvl + "!";
                }
            } else if (tier.equals("rare")) {
                newLvl = Math.max(tierFrom, currentLvl - 1);
                penaltyMsg = "Заточка снизилась до +" + newLvl + "!";
            } else if (tier.equals("legendary")) {
                if (new java.util.Random().nextInt(100) < 35) {
                    newLvl = Math.max(tierFrom, currentLvl - 2);
                    penaltyMsg = "Заточка резко снизилась до +" + newLvl + "!";
                } else {
                    newLvl = Math.max(tierFrom, currentLvl - 1);
                    penaltyMsg = "Заточка снизилась до +" + newLvl + "!";
                }
            } else if (tier.equals("ancient")) {
                if (new java.util.Random().nextInt(100) < 40) {
                    newLvl = Math.max(tierFrom, currentLvl - 3);
                    penaltyMsg = "Заточка уничтожена! Снижение до +" + newLvl + "!";
                } else {
                    newLvl = Math.max(tierFrom, currentLvl - 1);
                    penaltyMsg = "Заточка снизилась до +" + newLvl + "!";
                }
            }

            plugin.getGearManager().updateGearUpgradeLevel(current, newLvl);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
            p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
            p.sendMessage(ChatColor.RED + "❌ Неудача! " + penaltyMsg);
        }
    }

    // ═══════════════════════════════════════════
    // РУНЫ
    // ═══════════════════════════════════════════

    private void handleRuneApply(InventoryClickEvent e, ItemStack cursor, ItemStack current, NamespacedKey key) {
        Player p = (Player) e.getWhoClicked();

        if (!plugin.getGearManager().isGear(current.getType())) {
            p.sendMessage(ChatColor.RED + "Эту руну можно применить только на Оружие, Инструменты или Броню!");
            return;
        }

        String enchantName = cursor.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        String enchantId = getEnchantIdByName(enchantName);
        if (enchantId == null) {
            p.sendMessage(ChatColor.RED + "❌ Неизвестная руна: " + enchantName);
            return;
        }

        List<String> available = plugin.getGearManager().getAvailableCustomEnchants(current.getType());
        if (!available.contains(enchantId)) {
            p.sendMessage(ChatColor.RED + "❌ Эту руну нельзя наложить на этот тип предмета!");
            return;
        }

        int applyCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.rune-apply-cost", 75);
        if (!plugin.getGearManager().takeVkReputation(p, applyCost, "нанесение руны")) {
            e.setCancelled(true);
            return;
        }

        ItemMeta targetMeta = current.getItemMeta();
        List<String> targetLore = targetMeta.hasLore() ? targetMeta.getLore() : new ArrayList<>();

        // Проверка дубликата
        for (String line : targetLore) {
            if (ChatColor.stripColor(line).contains(enchantName)) {
                p.sendMessage(ChatColor.RED + "На этом предмете уже есть эти чары!");
                return;
            }
        }

        // Проверка конфликтов
        List<String> conflicts = plugin.getEnchantsConfig().getStringList("custom_enchants." + enchantId + ".conflicts");
        for (String line : targetLore) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            for (String conflict : conflicts) {
                if (!conflict.isEmpty() && stripped.contains(conflict.toLowerCase())) {
                    p.sendMessage(ChatColor.RED + "❌ Конфликт рун: " + conflict);
                    return;
                }
            }
        }

        String formattedEnchant = ChatColor.translateAlternateColorCodes('&',
                plugin.getEnchantsConfig().getString("custom_enchants." + enchantId + ".name", "&d✨ " + enchantName));
        targetLore.add(1, formattedEnchant);
        targetMeta.setLore(targetLore);
        GearManager.tagItemWithEnchant(targetMeta, enchantId, 1, plugin);
        current.setItemMeta(targetMeta);

        e.getCursor().setAmount(cursor.getAmount() - 1);
        e.setCancelled(true);

        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        p.sendMessage(ChatColor.GREEN + "Вы успешно наложили " + enchantName + " на предмет за " + applyCost + " реп.!");
    }

    // ═══════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════

    private String getEnchantIdByName(String name) {
        RuneRegistry registry = plugin.getRuneRegistry();
        if (registry != null) {
            String id = registry.getEnchantIdByName(name);
            if (id != null) return id;
        }
        return getEnchantIdByNameFallback(name);
    }

    private String getEnchantIdByNameFallback(String name) {
        switch (name) {
            case "Вампиризм": return "vampirism";
            case "Ядовитое Облако": return "poison_cloud";
            case "Бронебойность": return "armor_piercing";
            case "Уклонение": return "dodge";
            case "Огненная аура": return "fire_aura";
            case "Зеркало": return "reflect_magic";
            case "Казнь": return "execute";
            case "Метеоритный Удар": return "meteor";
            case "Берсерк": return "berserk";
            case "Жнец Душ": return "soul_reaper";
            case "Эгида": return "shield";
            case "Второе Дыхание": return "second_wind";
            case "Похищение Жизни": return "life_steal";
            case "Огненный Удар": return "fire_punch";
            case "Паралич": return "paralyze";
            case "Поглощение": return "absorption";
            case "Аура Спешки": return "haste_aura";
            case "Печать Души": return "rarity_seal";
            case "Аура Вампиризма": return "vampire_aoe";
            case "Распад": return "disintegration";
            case "Полет Ветра": return "wind_glide";
            case "Ледяное Касание": return "frozen_touch";
            case "Связь Душ": return "soul_bond";
            case "Удар Грома": return "thunder_strike";
            case "Критический Удар": return "critical_strike";
            case "Кожа Голема": return "golem_skin";
            case "Аура Исцеления": return "healing_aura";
            case "Магнит Руд": return "ore_magnet";
            case "Рефлексы Паука": return "spider_reflexes";
            case "Магматический Шаг": return "magma_walker";
            case "Метеоритный Дождь": return "meteor_shower";
            default: return null;
        }
    }
}
