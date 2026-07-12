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
import ru.example.vkchatgear.VKChatGearPlugin;
import ru.example.vkchatgear.runes.RuneRegistry;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;

public class RuneListener implements Listener {
    private final VKChatGearPlugin plugin;

    public RuneListener(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("РУНЫ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            
            Player p = (Player) e.getWhoClicked();
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            NamespacedKey priceKey = new NamespacedKey(plugin, "rune_price");
            NamespacedKey crystalPriceKey = new NamespacedKey(plugin, "crystal_price");
            NamespacedKey safetyScrollPriceKey = new NamespacedKey(plugin, "safety_scroll_price");
            NamespacedKey fusionScrollPriceKey = new NamespacedKey(plugin, "fusion_scroll_price");
            
            if (meta.getPersistentDataContainer().has(priceKey, PersistentDataType.INTEGER)) {
                int price = meta.getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
                String name = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rune_name"), PersistentDataType.STRING);
                String id = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "rune_id"), PersistentDataType.STRING);
                
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
                    p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
                    p.closeInventory();
                    return;
                }
                
                if (VKChatPlugin.getInstance().getApi().getReputation(vkId) >= price) {
                    VKChatPlugin.getInstance().getApi().takeReputation(vkId, price);
                    
                    ItemStack rune = new ItemStack(Material.NETHER_STAR);
                    ItemMeta rMeta = rune.getItemMeta();
                    rMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "✨ Руна: " + name);
                    List<String> rLore = new ArrayList<>();
                    if (id != null) {
                        String desc = plugin.getConfig().getString("custom_enchants." + id + ".name", null);
                        if (desc != null) {
                            rLore.add(ChatColor.translateAlternateColorCodes('&', desc));
                            rLore.add("");
                        }
                    }
                    rLore.add(ChatColor.GRAY + "Перетащите эту руну на");
                    rLore.add(ChatColor.GRAY + "ваше снаряжение в инвентаре,");
                    rLore.add(ChatColor.GRAY + "чтобы наложить чары!");
                    rMeta.setLore(rLore);
                    rMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rune_name"), PersistentDataType.STRING, name);
                    Integer runeCmd = getRuneCustomModelData(id);
                    if (runeCmd != null) rMeta.setCustomModelData(runeCmd);
                    rune.setItemMeta(rMeta);
                    
                    p.getInventory().addItem(rune).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                    p.sendMessage(ChatColor.GREEN + "Вы успешно купили Руну: " + name + " за " + price + " реп.");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    
                    if (id != null) {
                        plugin.getRuneMarketManager().recordPurchase(id);
                    }
                    
                    // Обновляем GUI
                    p.performCommand("runes");
                } else {
                    p.sendMessage(ChatColor.RED + "Недостаточно репутации ВКонтакте!");
                }
            } else if (meta.getPersistentDataContainer().has(crystalPriceKey, PersistentDataType.INTEGER)) {
                int price = meta.getPersistentDataContainer().get(crystalPriceKey, PersistentDataType.INTEGER);
                String name = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "crystal_name"), PersistentDataType.STRING);
                String tier = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "crystal_tier"), PersistentDataType.STRING);
                
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
                    p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
                    p.closeInventory();
                    return;
                }
                
                if (VKChatPlugin.getInstance().getApi().getReputation(vkId) >= price) {
                    VKChatPlugin.getInstance().getApi().takeReputation(vkId, price);
                    
                    Material mat = Material.EMERALD;
                    String color = "§a";
                    if (tier.equals("rare")) {
                        mat = Material.DIAMOND;
                        color = "§9";
                    } else if (tier.equals("legendary")) {
                        mat = Material.PRISMARINE_SHARD;
                        color = "§6§l";
                    }
                    
                    ItemStack crystal = new ItemStack(mat);
                    ItemMeta cMeta = crystal.getItemMeta();
                    cMeta.setDisplayName(color + "💎 Кристалл Заточки: " + name);
                    
                    List<String> cLore = new ArrayList<>();
                    cLore.add("§7Позволяет затачивать снаряжение.");
                    cLore.add("");
                    int from = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".from",
                        tier.equals("common") ? 0 : tier.equals("rare") ? 10 : tier.equals("legendary") ? 15 : 20);
                    int to = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".to",
                        tier.equals("common") ? 10 : tier.equals("rare") ? 15 : tier.equals("legendary") ? 20 : 25);
                    int success = plugin.getConfig().getInt("hardcore-forging.crystals.tiers." + tier + ".success",
                        tier.equals("common") ? 90 : tier.equals("rare") ? 60 : tier.equals("legendary") ? 35 : 25);
                    cLore.add("§e• Диапазон заточки: §f+" + from + " ➔ +" + to);
                    cLore.add("§e• Шанс успеха: §a" + success + "%");
                    if (tier.equals("common")) cLore.add("§c• При провале: редко снижает заточку на -1");
                    else if (tier.equals("rare")) cLore.add("§c• При провале: может снизить заточку на -1, но не ниже +" + from);
                    else if (tier.equals("legendary")) cLore.add("§c• При провале: может снизить заточку на -1/-2, но не ниже +" + from);
                    else cLore.add("§4• При провале: может снизить заточку на -1/-3, но не ниже +" + from);
                    cLore.add("");
                    cLore.add("§7Перетащите этот кристалл на предмет");
                    cLore.add("§7в инвентаре для его заточки!");
                    
                    cMeta.setLore(cLore);
                    Integer crystalCmd = getCrystalCustomModelData(tier);
                    if (crystalCmd != null) cMeta.setCustomModelData(crystalCmd);
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_tier"), PersistentDataType.STRING, tier);
                    cMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crystal_name"), PersistentDataType.STRING, name);
                    crystal.setItemMeta(cMeta);
                    
                    p.getInventory().addItem(crystal).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                    p.sendMessage(ChatColor.GREEN + "Вы успешно купили Кристалл: " + name + " за " + price + " реп.!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    
                    plugin.getRuneMarketManager().recordPurchase("crystal_" + tier);
                    
                    // Обновляем GUI
                    p.performCommand("runes");
                } else {
                    p.sendMessage(ChatColor.RED + "Недостаточно репутации ВКонтакте!");
                }
            } else if (meta.getPersistentDataContainer().has(safetyScrollPriceKey, PersistentDataType.INTEGER)) {
                int price = meta.getPersistentDataContainer().get(safetyScrollPriceKey, PersistentDataType.INTEGER);
                
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
                    p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
                    p.closeInventory();
                    return;
                }
                
                if (VKChatPlugin.getInstance().getApi().getReputation(vkId) >= price) {
                    VKChatPlugin.getInstance().getApi().takeReputation(vkId, price);
                    
                    ItemStack scroll = new ItemStack(Material.PAPER);
                    ItemMeta sMeta = scroll.getItemMeta();
                    sMeta.setDisplayName("§d§lСвиток Сохранения");
                    sMeta.setCustomModelData(54);
                    List<String> sLore = new ArrayList<>();
                    sLore.add("§7Защищает предмет от отката");
                    sLore.add("§7уровня заточки при неудаче!");
                    sLore.add("");
                    sLore.add("§e• Как использовать:");
                    sLore.add("§e  Просто держите этот свиток в инвентаре");
                    sLore.add("§e  в момент проведения заточки кристаллом.");
                    sLore.add("§e  Свиток автоматически спасет предмет");
                    sLore.add("§e  и будет израсходован при неудаче.");
                    sMeta.setLore(sLore);
                    sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_safety_scroll"), PersistentDataType.INTEGER, 1);
                    scroll.setItemMeta(sMeta);
                    
                    p.getInventory().addItem(scroll).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                    p.sendMessage(ChatColor.GREEN + "Вы успешно купили Свиток Сохранения за " + price + " реп.!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    
                    plugin.getRuneMarketManager().recordPurchase("safety_scroll");
                    
                    // Обновляем GUI
                    p.performCommand("runes");
                } else {
                    p.sendMessage(ChatColor.RED + "Недостаточно репутации ВКонтакте!");
                }
            } else if (meta.getPersistentDataContainer().has(fusionScrollPriceKey, PersistentDataType.INTEGER)) {
                int price = meta.getPersistentDataContainer().get(fusionScrollPriceKey, PersistentDataType.INTEGER);
                int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
                if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
                    p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте! (/vklink)");
                    p.closeInventory();
                    return;
                }
                if (VKChatPlugin.getInstance().getApi().getReputation(vkId) >= price) {
                    VKChatPlugin.getInstance().getApi().takeReputation(vkId, price);
                    ItemStack scroll = new ItemStack(Material.NETHER_STAR);
                    ItemMeta fMeta = scroll.getItemMeta();
                    fMeta.setDisplayName("§6§lСвиток Идеального Слияния");
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Следующее слияние редкости в /forge");
                    lore.add("§7будет §a100% успешным§7.");
                    lore.add("§8Расходуется автоматически.");
                    fMeta.setLore(lore);
                    fMeta.setCustomModelData(30);
                    fMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_fusion_scroll"), PersistentDataType.INTEGER, 1);
                    scroll.setItemMeta(fMeta);
                    p.getInventory().addItem(scroll).values().forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
                    p.sendMessage(ChatColor.GOLD + "Вы купили Свиток Идеального Слияния за " + price + " реп. ВК!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    p.performCommand("runes");
                } else {
                    p.sendMessage(ChatColor.RED + "Недостаточно репутации ВКонтакте!");
                }
            }
        }
    }

    @EventHandler
    public void onRuneApply(InventoryClickEvent e) {
        if (e.getClickedInventory() == null) return;
        
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        
        if (cursor == null || current == null || cursor.getType() == Material.AIR || current.getType() == Material.AIR) return;
        
        // --- 1. Работа с Кристаллами Заточки ---
        if (cursor.hasItemMeta()) {
            NamespacedKey tierKey = new NamespacedKey(plugin, "crystal_tier");
            if (cursor.getItemMeta().getPersistentDataContainer().has(tierKey, PersistentDataType.STRING)) {
                Player p = (Player) e.getWhoClicked();
                if (!plugin.getGearManager().isGear(current.getType())) {
                    p.sendMessage(ChatColor.RED + "Кристаллы Заточки можно применить только на Оружие, Инструменты или Броню!");
                    return;
                }
                
                String tier = cursor.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
                NamespacedKey lvlKey = new NamespacedKey(plugin, "upgrade_level");
                
                ItemMeta targetMeta = current.getItemMeta();
                int currentLvl = targetMeta.getPersistentDataContainer().getOrDefault(lvlKey, PersistentDataType.INTEGER, 0);
                
                int maxUpgrade = plugin.getConfig().getInt("hardcore-forging.max-upgrade-level", plugin.getConfig().getInt("settings.max-upgrade-level", 25));
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
                        p.sendMessage(ChatColor.LIGHT_PURPLE + "🛡️ [Свиток Сохранения] Заточка не удалась, но свиток спас ваш предмет от снижения уровня заточки!");
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
                        int downgradeRoll = new java.util.Random().nextInt(100);
                        if (downgradeRoll < 35) {
                            newLvl = Math.max(tierFrom, currentLvl - 2);
                            penaltyMsg = "Заточка резко снизилась до +" + newLvl + "!";
                        } else {
                            newLvl = Math.max(tierFrom, currentLvl - 1);
                            penaltyMsg = "Заточка снизилась до +" + newLvl + "!";
                        }
                    } else if (tier.equals("ancient")) {
                        int downgradeRoll = new java.util.Random().nextInt(100);
                        if (downgradeRoll < 40) {
                            newLvl = Math.max(tierFrom, currentLvl - 3);
                            penaltyMsg = "Заточка была уничтожена! Снижение до +" + newLvl + "!";
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
                return;
            }
        }
        
        // --- 2. Работа с Рунами ---
        if (cursor.getType() == Material.NETHER_STAR && cursor.hasItemMeta()) {
            NamespacedKey key = new NamespacedKey(plugin, "rune_name");
            if (cursor.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                
                if (!plugin.getGearManager().isGear(current.getType())) {
                    e.getWhoClicked().sendMessage(ChatColor.RED + "Эту руну можно применить только на Оружие, Инструменты или Броню!");
                    return;
                }
                
                String enchantName = cursor.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                String enchantId = getEnchantIdByName(enchantName);
                if (enchantId == null) {
                    e.getWhoClicked().sendMessage(ChatColor.RED + "❌ Неизвестная руна: " + enchantName);
                    return;
                }
                List<String> available = plugin.getGearManager().getAvailableCustomEnchants(current.getType());
                
                if (!available.contains(enchantId)) {
                    e.getWhoClicked().sendMessage(ChatColor.RED + "❌ Эту руну нельзя наложить на этот тип предмета!");
                    return;
                }
                
                Player p = (Player) e.getWhoClicked();
                int applyCost = plugin.getGearManager().getDiscountedCost(p, "hardcore-forging.rune-apply-cost", 75);
                if (!plugin.getGearManager().takeVkReputation(p, applyCost, "нанесение руны")) {
                    e.setCancelled(true);
                    return;
                }

                ItemMeta targetMeta = current.getItemMeta();
                List<String> targetLore = targetMeta.hasLore() ? targetMeta.getLore() : new ArrayList<>();
                for (String line : targetLore) {
                    if (ChatColor.stripColor(line).contains(enchantName)) {
                        e.getWhoClicked().sendMessage(ChatColor.RED + "На этом предмете уже есть эти чары!");
                        return;
                    }
                }
                java.util.List<String> conflicts = plugin.getConfig().getStringList("custom_enchants." + enchantId + ".conflicts");
                for (String line : targetLore) {
                    String stripped = ChatColor.stripColor(line).toLowerCase();
                    for (String conflict : conflicts) {
                        if (!conflict.isEmpty() && stripped.contains(conflict.toLowerCase())) {
                            e.getWhoClicked().sendMessage(ChatColor.RED + "❌ Конфликт рун: " + conflict);
                            return;
                        }
                    }
                }
                
                String formattedEnchant = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("custom_enchants." + enchantId + ".name", "&d✨ " + enchantName));
                targetLore.add(1, formattedEnchant);
                targetMeta.setLore(targetLore);
                current.setItemMeta(targetMeta);
                
                e.getCursor().setAmount(cursor.getAmount() - 1);
                e.setCancelled(true);
                
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
                p.sendMessage(ChatColor.GREEN + "Вы успешно наложили " + enchantName + " на свой предмет за " + applyCost + " репутации ВК!");
            }
        }
    }
    
    /**
     * FIX #8: Делегирует в RuneRegistry вместо хардкода 30+ маппингов.
     * Старая реализация с if-else цепочкой заменена на конфиг-управляемый реестр.
     */
    private String getEnchantIdByName(String name) {
        RuneRegistry registry = plugin.getRuneRegistry();
        if (registry != null) {
            String id = registry.getEnchantIdByName(name);
            if (id != null) return id;
        }
        // Fallback для обратной совместимости при отсутствии в конфиге
        return getEnchantIdByNameFallback(name);
    }

    /**
     * Legacy fallback маппинг — только для рун, не загруженных из конфига.
     */
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
    
    private static Integer getRuneCustomModelData(String id) {
        if (id == null) return null;
        switch (id) {
            case "blood_rune": case "chaos_rune": return 10;
            case "frost_rune": return 11;
            case "poison_rune": return 12;
            case "lightning_rune": case "holy_rune": return 13;
            case "shadow_rune": case "void_rune": return 14;
            case "arcane_rune": return 15;
            case "darkness_rune": return 16;
            case "death_rune": return 17;
            case "earth_rune": return 18;
            case "farming_rune": return 19;
            case "fire_rune": return 20;
            case "flame_rune": return 21;
            case "fishing_rune": return 22;
            case "health_rune": return 23;
            case "ice_rune": return 24;
            case "iron_rune": return 25;
            case "light_rune": return 26;
            case "loot_rune": return 27;
            case "luck_rune": return 28;
            case "mining_rune": return 29;
            case "nature_rune": return 31;
            case "speed_rune": return 32;
            case "spirit_rune": return 33;
            case "stone_rune": return 34;
            case "strength_rune": return 35;
            case "thunder_rune": return 36;
            case "time_rune": return 37;
            case "water_rune": return 38;
            case "wind_rune": return 39;
            case "xp_rune": return 40;
            default: return null;
        }
    }
    
    private static Integer getCrystalCustomModelData(String tier) {
        if (tier == null) return null;
        switch (tier) {
            case "common": return 50;
            case "rare": return 51;
            case "legendary": return 6;
            case "ancient": return 52;
            default: return null;
        }
    }
}
