package ru.example.vkchatgear;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class GearManager {
    private final VKChatGearPlugin plugin;
    
    private final List<Enchantment> swordEnchants = Arrays.asList(Enchantment.DAMAGE_ALL, Enchantment.FIRE_ASPECT, Enchantment.KNOCKBACK, Enchantment.SWEEPING_EDGE, Enchantment.DURABILITY, Enchantment.LOOT_BONUS_MOBS);
    private final List<Enchantment> axeEnchants = Arrays.asList(Enchantment.DAMAGE_ALL, Enchantment.DURABILITY, Enchantment.LOOT_BONUS_MOBS, Enchantment.DIG_SPEED);
    private final List<Enchantment> bowEnchants = Arrays.asList(Enchantment.ARROW_DAMAGE, Enchantment.ARROW_FIRE, Enchantment.ARROW_INFINITE, Enchantment.ARROW_KNOCKBACK, Enchantment.DURABILITY);
    private final List<Enchantment> crossbowEnchants = Arrays.asList(Enchantment.MULTISHOT, Enchantment.QUICK_CHARGE, Enchantment.PIERCING, Enchantment.DURABILITY);
    private final List<Enchantment> armorEnchants = Arrays.asList(Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_PROJECTILE, Enchantment.THORNS, Enchantment.DURABILITY);
    private final List<Enchantment> toolEnchants = Arrays.asList(Enchantment.DIG_SPEED, Enchantment.DURABILITY, Enchantment.LOOT_BONUS_BLOCKS);

    public GearManager(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    public int getBlacksmithLevel(Player p) {
        if (p == null) return 0;
        try {
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                Object dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                return (int) dataManager.getClass().getMethod("getLevel", java.util.UUID.class, String.class).invoke(dataManager, p.getUniqueId(), "blacksmith");
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public int getDiscountedCost(Player p, String path, int def) {
        int base = plugin.getConfig().getInt(path, def);
        int lvl = getBlacksmithLevel(p);
        double per = plugin.getConfig().getDouble("hardcore-forging.blacksmith.discount-per-level", 0.01);
        double max = plugin.getConfig().getDouble("hardcore-forging.blacksmith.max-discount", 0.45);
        double discount = Math.min(max, Math.max(0, lvl * per));
        double donor = getDonateDiscount(p);
        double totalDiscount = Math.min(0.95, discount + donor);
        return Math.max(0, (int) Math.round(base * (1.0 - totalDiscount)));
    }

    private double getDonateDiscount(Player p) {
        if (p == null) return 0.0;
        if (p.hasPermission("vkchat.donate.gear.legend") || p.hasPermission("vkchat.donate.status.legend")) return plugin.getConfig().getDouble("forge2.donate-discount.legend", 0.50);
        if (p.hasPermission("vkchat.donate.gear.star") || p.hasPermission("vkchat.donate.status.star")) return plugin.getConfig().getDouble("forge2.donate-discount.star", 0.50);
        if (p.hasPermission("vkchat.donate.gear.flame") || p.hasPermission("vkchat.donate.status.flame")) return plugin.getConfig().getDouble("forge2.donate-discount.flame", 0.25);
        if (p.hasPermission("vkchat.donate.gear.spark") || p.hasPermission("vkchat.donate.status.spark")) return plugin.getConfig().getDouble("forge2.donate-discount.spark", 0.10);
        return 0.0;
    }

    public boolean takeVkReputation(Player p, int cost, String actionName) {
        if (cost <= 0) return true;
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Для действия '" + actionName + "' нужно привязать ВКонтакте (/vklink).");
            return false;
        }
        int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        if (rep < cost) {
            p.sendMessage(ChatColor.RED + "Недостаточно репутации ВК для '" + actionName + "'. Нужно: " + cost + ", у тебя: " + rep + ".");
            return false;
        }
        VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
        return true;
    }

    public int getRuneSlotLimit(String rarityKey) {
        return plugin.getConfig().getInt("hardcore-forging.rune-slots." + rarityKey, rarityKey.equals("ancient") ? 4 : rarityKey.equals("legendary") ? 3 : rarityKey.equals("epic") ? 2 : rarityKey.equals("rare") ? 1 : 0);
    }

    public String getRarityKey(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return "common";
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            if (stripped.contains("древн")) return "ancient";
            if (stripped.contains("легендар")) return "legendary";
            if (stripped.contains("эпичес")) return "epic";
            if (stripped.contains("редкий")) return "rare";
            if (stripped.contains("необыч")) return "uncommon";
            if (stripped.contains("обыч")) return "common";
        }
        return "common";
    }

    public double getRarityDamageBonus(String rarityKey) {
        return plugin.getConfig().getDouble("rarity-properties." + rarityKey + ".damage-bonus", defaultRarityDamageBonus(rarityKey));
    }

    public double getRarityDefenseBonus(String rarityKey) {
        return plugin.getConfig().getDouble("rarity-properties." + rarityKey + ".defense-bonus", defaultRarityDefenseBonus(rarityKey));
    }

    public String getRarityPropertyLine(String rarityKey) {
        String line = plugin.getConfig().getString("rarity-properties." + rarityKey + ".lore", "");
        if (line == null || line.isEmpty()) return null;
        return ChatColor.translateAlternateColorCodes('&', line);
    }

    private double defaultRarityDamageBonus(String rarityKey) {
        switch (rarityKey) {
            case "uncommon": return 0.02;
            case "rare": return 0.05;
            case "epic": return 0.09;
            case "legendary": return 0.15;
            case "ancient": return 0.20;
            default: return 0.0;
        }
    }

    private double defaultRarityDefenseBonus(String rarityKey) {
        switch (rarityKey) {
            case "uncommon": return 0.01;
            case "rare": return 0.03;
            case "epic": return 0.06;
            case "legendary": return 0.10;
            case "ancient": return 0.18;
            default: return 0.0;
        }
    }

    public int countCustomRuneLines(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return 0;
        int count = 0;
        List<String> lore = item.getItemMeta().getLore();
        List<String> all = getAvailableCustomEnchants(item.getType());
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).toLowerCase();
            for (String key : all) {
                String rawName = plugin.getConfig().getString("custom_enchants." + key + ".name", "");
                String cfg = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName)).toLowerCase();
                if (!cfg.isEmpty() && stripped.contains(cfg.split(" ")[0].toLowerCase())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    public boolean hasDefect(ItemStack item, String defectKey) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "defect_" + defectKey), PersistentDataType.INTEGER);
    }

    public void applyRandomDefect(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        List<String> keys = new ArrayList<>(plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list").getKeys(false));
        if (keys.isEmpty()) return;
        String key = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "defect_" + key), PersistentDataType.INTEGER)) return;
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        String line = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("hardcore-forging.defects.list." + key));
        lore.add(1, line);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "defect_" + key), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
    }

    public boolean cleanseDefects(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            int before = lore.size();
            lore.removeIf(line -> ChatColor.stripColor(line).startsWith("Дефект:"));
            if (lore.size() != before) {
                meta.setLore(lore);
                changed = true;
            }
        }
        java.util.List<String> defectKeys = new java.util.ArrayList<>();
        if (plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list") != null) {
            defectKeys.addAll(plugin.getConfig().getConfigurationSection("hardcore-forging.defects.list").getKeys(false));
        }
        if (defectKeys.isEmpty()) defectKeys.addAll(Arrays.asList("fragile", "heavy", "dull"));
        for (String key : defectKeys) {
            NamespacedKey nk = new NamespacedKey(plugin, "defect_" + key);
            if (meta.getPersistentDataContainer().has(nk, PersistentDataType.INTEGER)) {
                meta.getPersistentDataContainer().remove(nk);
                changed = true;
            }
        }
        item.setItemMeta(meta);
        return changed;
    }

    public boolean isLegalSetPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String set = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING);
        if (set == null) return false;
        if (!plugin.getConfig().getBoolean("hardcore-forging.set-fragments.require-origin", true)) return true;
        String origin = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "gear_set_origin"), PersistentDataType.STRING);
        if (origin == null || origin.trim().isEmpty()) {
            return plugin.getConfig().getBoolean("hardcore-forging.set-fragments.allow-legacy", false);
        }
        origin = origin.toLowerCase(Locale.ROOT);
        if (origin.equals("fragment") || origin.equals("admin") || origin.equals("nation") || origin.equals("mob_drop") || origin.equals("offline_reward")) return true;
        if (origin.equals("legacy")) return plugin.getConfig().getBoolean("hardcore-forging.set-fragments.allow-legacy", false);
        return false;
    }

    public void warnIllegalSetPiece(Player p, ItemStack item) {
        if (p == null || item == null || !item.hasItemMeta()) return;
        if (!plugin.getConfig().getBoolean("hardcore-forging.set-fragments.notify-illegal", true)) return;
        long now = System.currentTimeMillis();
        String metaKey = "vkchat_illegal_set_warn";
        if (p.hasMetadata(metaKey)) {
            try {
                long last = p.getMetadata(metaKey).get(0).asLong();
                if (now - last < 30000L) return;
            } catch (Exception ignored) {}
        }
        p.setMetadata(metaKey, new org.bukkit.metadata.FixedMetadataValue(plugin, now));
        String name = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
        p.sendMessage(ChatColor.RED + "⚠ Сетовый предмет " + name + ChatColor.RED + " не имеет легального происхождения и не активирует бонус сета.");
        plugin.getLogger().warning("Illegal set item blocked for " + p.getName() + ": " + ChatColor.stripColor(name));
    }

    private void markSetOrigin(ItemMeta meta, String origin) {
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_set_origin"), PersistentDataType.STRING, origin);
    }

    private int rarityIndex(String key) {
        switch (key) {
            case "uncommon": return 1;
            case "rare": return 2;
            case "epic": return 3;
            case "legendary": return 4;
            case "ancient": return 5;
            default: return 0;
        }
    }

    private String rarityByIndex(int idx) {
        if (idx >= 5) return "ancient";
        if (idx == 4) return "legendary";
        if (idx == 3) return "epic";
        if (idx == 2) return "rare";
        if (idx == 1) return "uncommon";
        return "common";
    }

    private String getMaterialGroup(Material mat) {
        String n = mat.name();
        if (n.contains("NETHERITE")) return "NETHERITE";
        if (n.contains("DIAMOND")) return "DIAMOND";
        if (n.contains("IRON")) return "IRON";
        if (n.contains("GOLD") || n.contains("GOLDEN")) return "GOLD";
        if (n.contains("STONE")) return "STONE";
        if (n.contains("WOOD") || n.contains("WOODEN") || n.contains("LEATHER")) return "WOOD";
        return "OTHER";
    }

    private String capRarityByMaterialAndJob(String rolled, Material mat, int blacksmithLvl) {
        String cap = plugin.getConfig().getString("hardcore-forging.rarity-nerf.material-cap." + getMaterialGroup(mat), "rare");
        int capIdx = rarityIndex(cap);
        int rareLvl = plugin.getConfig().getInt("hardcore-forging.blacksmith.rare-unlock-level", 5);
        int epicLvl = plugin.getConfig().getInt("hardcore-forging.blacksmith.epic-unlock-level", 15);
        int legLvl = plugin.getConfig().getInt("hardcore-forging.blacksmith.legendary-unlock-level", 25);
        int ancientLvl = plugin.getConfig().getInt("hardcore-forging.blacksmith.ancient-unlock-level", 35);
        int jobCap = blacksmithLvl >= ancientLvl ? 5 : blacksmithLvl >= legLvl ? 4 : blacksmithLvl >= epicLvl ? 3 : blacksmithLvl >= rareLvl ? 2 : 1;
        return rarityByIndex(Math.min(rarityIndex(rolled), Math.min(capIdx, jobCap)));
    }

    private String consumeSetFragment(Player p) {
        if (!plugin.getConfig().getBoolean("hardcore-forging.set-fragments.enabled", true)) return null;
        Material expectedMaterial = Material.PAPER;
        try {
            expectedMaterial = Material.valueOf(plugin.getConfig().getString("hardcore-forging.set-fragments.item-material", "PAPER").toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {}

        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack == null || stack.getType() != expectedMaterial || !stack.hasItemMeta()) continue;
            ItemMeta meta = stack.getItemMeta();

            // ВАЖНО: фрагмент сета легален только по PDC-метке set_fragment.
            // Название предмета больше не проверяется, чтобы игрок не мог переименовать бумагу на наковальне.
            String set = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "set_fragment"), PersistentDataType.STRING);

            if (set != null && plugin.getConfig().contains("sets." + set)) {
                if (plugin.getConfig().getBoolean("hardcore-forging.set-fragments.consume-on-craft", true)) {
                    stack.setAmount(stack.getAmount() - 1);
                }
                return set;
            }
        }
        return null;
    }

    public boolean isGear(Material mat) {
        String n = mat.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") ||
               n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.equals("BOW") || n.equals("CROSSBOW");
    }

    private List<Enchantment> getExcellentEnchants(Material mat) {
        List<Enchantment> list = new ArrayList<>();
        if (Bukkit.getPluginManager().getPlugin("ExcellentEnchants") == null) return list;
        try {
            Class<?> registryClass = Class.forName("su.nightexpress.excellentenchants.api.enchantment.EnchantRegistry");
            java.lang.reflect.Method getRegistered = registryClass.getMethod("getRegistered");
            Collection<?> customs = (Collection<?>) getRegistered.invoke(null);
            
            for (Object custom : customs) {
                java.lang.reflect.Method getBukkit = custom.getClass().getMethod("getBukkitEnchantment");
                Enchantment e = (Enchantment) getBukkit.invoke(custom);
                if (e != null && e.canEnchantItem(new ItemStack(mat))) {
                    list.add(e);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public List<String> getAvailableCustomEnchants(Material mat) {
        List<String> list = new ArrayList<>();
        String n = mat.name();
        
        // 1. ОРУЖИЕ (Мечи, Луки, Арбалеты)
        if (n.endsWith("_SWORD") || n.equals("BOW") || n.equals("CROSSBOW")) {
            list.addAll(Arrays.asList(
                "vampirism", "venom", "lightning", "frost", "execute", "blindness", "bleeding", "meteor", 
                "wither_strike", "armor_piercing", "soul_reaper", "berserk", "disarm", "levitation_strike", 
                "wither_burst", "thunder_strike", "poison_cloud", "critical_strike", "lifesteal_aura", 
                "meteor_shower", "frozen_touch", "disintegration", "rarity_seal", "vampire_aoe",
                "telekenesis", "experience_boost", "soul_drain", "chain_lightning", "void_strike"
            ));
        } 
        // 2. ТОПОРЫ (Боевое оружие + Дровосек / Спешка)
        else if (n.endsWith("_AXE")) {
            list.addAll(Arrays.asList(
                "vampirism", "venom", "lightning", "frost", "execute", "blindness", "bleeding", "meteor", 
                "wither_strike", "armor_piercing", "soul_reaper", "berserk", "disarm", "levitation_strike", 
                "wither_burst", "thunder_strike", "poison_cloud", "critical_strike", "lifesteal_aura", 
                "meteor_shower", "frozen_touch", "disintegration", "rarity_seal", "vampire_aoe",
                "telekenesis", "experience_boost", "haste", "timber", "soul_drain", "chain_lightning", "void_strike"
            ));
        } 
        // 3. БРОНЯ (Шлемы, Нагрудники, Поножи, Сапоги)
        else if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) {
            list.addAll(Arrays.asList(
                "thorns", "dodge", "fire_aura", "shield", "health_boost", "reflect_magic", "second_wind", 
                "heavy_weight", "magma_walker", "ender_shield", "wind_step", "golem_skin", "spider_reflexes", 
                "healing_aura", "aquatic_life", "soul_bond", "wind_glide", "rarity_seal", "stone_skin", "life_link"
            ));
        } 
        // 4. КИРКИ (Шахтерские инструменты: Спешка, Телекинез, Опыт, Магнит руд, Автоплавка)
        else if (n.endsWith("_PICKAXE")) {
            list.addAll(Arrays.asList("haste", "telekenesis", "experience_boost", "ore_magnet", "auto_smelt"));
        } 
        // 5. ЛОПАТЫ И МОТЫГИ (Копание и Фермерство: Спешка, Телекинез, Опыт)
        else if (n.endsWith("_SHOVEL") || n.endsWith("_HOE")) {
            list.addAll(Arrays.asList("haste", "telekenesis", "experience_boost"));
        } 
        // 6. ВСЕ ОСТАЛЬНЫЕ ПРЕДМЕТЫ
        else {
            list.addAll(Arrays.asList("haste", "telekenesis", "experience_boost"));
        }
        
        return list;
    }

    public ItemStack generateGear(ItemStack item, Player crafter, boolean force) {
        if (item == null || item.getType() == Material.AIR) return item;
        
        ItemMeta meta = item.getItemMeta();
        if (!force && meta != null && meta.hasLore() && meta.getLore().toString().contains("Редкость")) {
            return item; 
        }

        if (force) {
            for (Enchantment e : item.getEnchantments().keySet()) {
                item.removeEnchantment(e);
            }
        }

        int luckPoints = 0;
        int blacksmithLvl = getBlacksmithLevel(crafter);
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(crafter);
            if (vkId != -1) {
                int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
                int divider = plugin.getConfig().getInt("reputation.rep_per_luck_point", 50);
                if (divider > 0) {
                    luckPoints = rep / divider; 
                }
            }
            
            // Check Blacksmith Legend skill
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                Object dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                int blacksmithLvlJob = (int) dataManager.getClass().getMethod("getLevel", java.util.UUID.class, String.class).invoke(dataManager, crafter.getUniqueId(), "blacksmith");
                if (blacksmithLvlJob >= 30) {
                    boolean hasSkill = (boolean) dataManager.getClass().getMethod("hasSkill", java.util.UUID.class, String.class, String.class).invoke(dataManager, crafter.getUniqueId(), "blacksmith", "black_leg");
                    if (hasSkill) {
                        luckPoints += 50; // Extra 50 luck points (~ +5% to rare/legendary rolls based on logic)
                    }
                }
            }
        } catch (Exception ignored) {}

        String rarityKey = capRarityByMaterialAndJob(rollRarity(luckPoints), item.getType(), blacksmithLvl);
        String rarityName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("rarities." + rarityKey + ".name"));
        int vanillaAmount = plugin.getConfig().getInt("rarities." + rarityKey + ".vanilla_enchants", 0);
        int excellentAmount = plugin.getConfig().getInt("rarities." + rarityKey + ".excellent_enchants", 0);
        int customAmount = plugin.getConfig().getInt("rarities." + rarityKey + ".custom_enchants", 0);

        if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(item.getType());

        Set<String> appliedEnchantKeys = new HashSet<>();

        // Vanilla
        if (vanillaAmount > 0) {
            List<Enchantment> pool = getPool(item.getType());
            Collections.shuffle(pool);
            for (int i = 0; i < Math.min(vanillaAmount, pool.size()); i++) {
                Enchantment e = pool.get(i);
                int lvl = ThreadLocalRandom.current().nextInt(e.getMaxLevel()) + 1;
                meta.addEnchant(e, lvl, true);
                appliedEnchantKeys.add(e.getKey().getKey().toLowerCase());
            }
        }

        // Excellent Enchants
        if (excellentAmount > 0) {
            List<Enchantment> eePool = getExcellentEnchants(item.getType());
            if (!eePool.isEmpty()) {
                Collections.shuffle(eePool);
                int applied = 0;
                for (Enchantment e : eePool) {
                    if (applied >= excellentAmount) break;
                    String eeKey = e.getKey().getKey().toLowerCase();
                    if (!appliedEnchantKeys.contains(eeKey)) {
                        int lvl = ThreadLocalRandom.current().nextInt(e.getMaxLevel()) + 1;
                        meta.addEnchant(e, lvl, true);
                        appliedEnchantKeys.add(eeKey);
                        applied++;
                    }
                }
            }
        }

        // Custom Enchants with Conflict Checking
        List<String> customLoreLines = new ArrayList<>();
        if (customAmount > 0) {
            List<String> available = getAvailableCustomEnchants(item.getType());
            Collections.shuffle(available);
            int applied = 0;
            for (String cKey : available) {
                if (applied >= customAmount) break;
                
                List<String> conflicts = plugin.getConfig().getStringList("custom_enchants." + cKey + ".conflicts");
                boolean conflictFound = false;
                for (String conflict : conflicts) {
                    for (String appliedKey : appliedEnchantKeys) {
                        if (appliedKey.contains(conflict.toLowerCase())) {
                            conflictFound = true;
                            break;
    }
}
                    if (conflictFound) break;
                }
                
                if (!conflictFound) {
                    String cName = plugin.getConfig().getString("custom_enchants." + cKey + ".name");
                    if (cName != null) {
                        customLoreLines.add(ChatColor.translateAlternateColorCodes('&', cName));
                        appliedEnchantKeys.add(cKey.toLowerCase());
                        applied++;
                    }
                }
            }
        }

        // Name Generation
        String baseName = getBaseName(item.getType());
        String generatedName = generateCoolName(baseName);
        meta.setDisplayName(rarityName + " " + ChatColor.WHITE + generatedName);

        // Lore Generation
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Редкость: " + rarityName);
        String rarityPropertyLine = getRarityPropertyLine(rarityKey);
        if (rarityPropertyLine != null) lore.add(rarityPropertyLine);
        lore.add(ChatColor.DARK_GRAY + "Создано кузнецом: " + crafter.getName());
        
        lore.add(ChatColor.YELLOW + "Заточка: +0");
        if (luckPoints > 0) {
            lore.add(ChatColor.AQUA + "Удача ВКонтакте: +" + luckPoints + "%");
        }
        
        if (!customLoreLines.isEmpty()) {
            lore.add("");
            lore.addAll(customLoreLines);
        }

        // Sets
        if (item.getType().name().endsWith("_HELMET") || item.getType().name().endsWith("_CHESTPLATE") || item.getType().name().endsWith("_LEGGINGS") || item.getType().name().endsWith("_BOOTS")) {
            String setKey = consumeSetFragment(crafter);
            if (setKey != null) {
                lore.add("");
                lore.add(ChatColor.GOLD + "Часть сета: " + plugin.getConfig().getString("sets." + setKey + ".name"));
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING, setKey);
                markSetOrigin(meta, "fragment");
            }
        }
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);

        item.setItemMeta(meta);

        if (rarityKey.equals("ancient") || rarityKey.equals("legendary") || rarityKey.equals("epic")) {
            crafter.getWorld().strikeLightningEffect(crafter.getLocation());
            crafter.playSound(crafter.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 0.5f);
        }

        if (rarityKey.equals("ancient")) {
            String msg = " ДРЕВНЕЕ ПРОБУЖДЕНИЕ! Кузнец " + crafter.getName() + " сковал предмет запредельной силы: " + rarityName + " " + generatedName + "!";
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + msg);
            try {
                VKChatPlugin.getInstance().getApi().sendToMainChat(msg);
            } catch (Exception ignored) {}
        } else if (rarityKey.equals("legendary")) {
            String msg = " ВЕЛИКОЕ СОБЫТИЕ! Кузнец " + crafter.getName() + " сковал предмет мифической силы: " + rarityName + " " + generatedName + "!";
            Bukkit.broadcastMessage(ChatColor.GOLD + msg);
            try {
                VKChatPlugin.getInstance().getApi().sendToMainChat(msg);
            } catch (Exception ignored) {}
        }

        return item;
    }

    public String generateCoolName(String base) {
        boolean isSoviet = ThreadLocalRandom.current().nextBoolean();
        List<String> dict = plugin.getConfig().getStringList(isSoviet ? "names.soviet" : "names.slavic");
        if (dict.isEmpty()) return base;
        
        String prefix = dict.get(ThreadLocalRandom.current().nextInt(dict.size()));
        return prefix + " " + base;
    }

    private String getBaseName(Material mat) {
        String n = mat.name();
        if (n.endsWith("_SWORD")) return "Меч";
        if (n.endsWith("_AXE")) return "Топор";
        if (n.endsWith("_PICKAXE")) return "Кирка";
        if (n.endsWith("_SHOVEL")) return "Лопата";
        if (n.endsWith("_HOE")) return "Мотыга";
        if (n.endsWith("_HELMET")) return "Шлем";
        if (n.endsWith("_CHESTPLATE")) return "Нагрудник";
        if (n.endsWith("_LEGGINGS")) return "Поножи";
        if (n.endsWith("_BOOTS")) return "Сапоги";
        if (n.equals("BOW")) return "Лук";
        if (n.equals("CROSSBOW")) return "Арбалет";
        return "Предмет";
    }

    private String rollSet() {
        // Сетовые части больше не появляются случайно: только через фрагменты/чертежи сета.
        return null;
    }

    private String rollRarity(int luckBonus) {
        // Ограничиваем максимальный бонус удачи для предотвращения багов с чрезмерно высокими шансами
        if (luckBonus > 100) {
            luckBonus = 100;
        } else if (luckBonus < 0) {
            luckBonus = 0;
        }

        double legChance = plugin.getConfig().getInt("rarities.legendary.chance", 1);
        double epicChance = plugin.getConfig().getInt("rarities.epic.chance", 5);
        double rareChance = plugin.getConfig().getInt("rarities.rare.chance", 14);
        double uncChance = plugin.getConfig().getInt("rarities.uncommon.chance", 30);

        // Каждая единица luckBonus (из репутации и навыка кузнеца) умеренно и сбалансированно повышает шансы на 1.5% относительно базовых
        double multiplier = 1.0 + (luckBonus * plugin.getConfig().getDouble("hardcore-forging.rarity-nerf.luck-multiplier-per-point", 0.004));

        legChance *= multiplier;
        epicChance *= multiplier;
        rareChance *= multiplier;
        uncChance *= multiplier;

        double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
        double current = 0;

        if (roll < (current += legChance)) return "legendary";
        if (roll < (current += epicChance)) return "epic";
        if (roll < (current += rareChance)) return "rare";
        if (roll < (current += uncChance)) return "uncommon";
        
        return "common";
    }

    private List<Enchantment> getPool(Material mat) {
        String n = mat.name();
        if (n.equals("BOW")) return bowEnchants;
        if (n.equals("CROSSBOW")) return crossbowEnchants;
        if (n.endsWith("_SWORD")) return swordEnchants;
        if (n.endsWith("_AXE")) return axeEnchants;
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return armorEnchants;
        return toolEnchants;
    }
    
    
    public void downgradeGear(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return;
        
        List<String> lore = meta.getLore();
        String currentRarity = null;
        int rarityLineIndex = -1;
        
        for (int i = 0; i < lore.size(); i++) {
            if (org.bukkit.ChatColor.stripColor(lore.get(i)).startsWith("Редкость:")) {
                currentRarity = lore.get(i);
                rarityLineIndex = i;
                break;
            }
        }
        
        if (currentRarity == null) return;
        
        String newRarityName = null;
        String newRarityKey = null;
        
        // Определяем понижение
        if (currentRarity.contains("ДРЕВНИЙ")) {
            newRarityKey = "legendary";
        } else if (currentRarity.contains("ЛЕГЕНДАРНЫЙ")) {
            newRarityKey = "epic";
        } else if (currentRarity.contains("Эпический")) {
            newRarityKey = "rare";
        } else if (currentRarity.contains("Редкий")) {
            newRarityKey = "uncommon";
        } else if (currentRarity.contains("Необычный")) {
            newRarityKey = "common";
        } else {
            return; // Обычный не понижается (или ломается, но пока оставим так)
        }
        
        newRarityName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("rarities." + newRarityKey + ".name"));
        
        // Заменяем Лор
        lore.set(rarityLineIndex, ChatColor.GRAY + "Редкость: " + newRarityName);
        
        // Добавляем пометку о деградации
        lore.add(1, ChatColor.DARK_RED + "☠ Испорчено смертью");
        
        meta.setLore(lore);
        
        // Заменяем имя (меняем цвет редкости)
        String displayName = meta.getDisplayName();
        String pureName = ChatColor.stripColor(displayName);
        // Убираем старую редкость из имени
        pureName = pureName.replace("[ДРЕВНИЙ] ", "").replace("[ЛЕГЕНДАРНЫЙ] ", "").replace("[Эпический] ", "").replace("[Редкий] ", "").replace("[Необычный] ", "").replace("[Обычный] ", "");
        meta.setDisplayName(newRarityName + " " + ChatColor.WHITE + pureName);
        
        item.setItemMeta(meta);
    }

    private void removeSetPotionEffects(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.LUCK);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        // HEALTH_BOOST не снимаем здесь: этот эффект могут выдавать артефакты/донат-статусы/зелья.
        // Снятие каждую секунду вызывало мигание дополнительных сердец.
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION);
    }

    public void checkSetBonus(Player p) {
        if (p == null) return;
        Map<String, Integer> setCounts = new HashMap<>();
        Map<String, java.util.Set<String>> setPieceTypes = new HashMap<>();
        int totalLvl = 0;
        int pieceCount = 0;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) {
                String set = armor.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING);
                if (set != null) {
                    if (isLegalSetPiece(armor)) {
                        setCounts.put(set, setCounts.getOrDefault(set, 0) + 1);
                        String pieceType = getArmorPieceType(armor.getType());
                        setPieceTypes.computeIfAbsent(set, k -> new java.util.HashSet<>()).add(pieceType);
                        int lvl = armor.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
                        totalLvl += lvl;
                        pieceCount++;
                    } else {
                        warnIllegalSetPiece(p, armor);
                    }
                }
            }
        }
        
        int avgLvl = pieceCount >= 4 ? totalLvl / 4 : 0;
        
        boolean hasFullSet = false;
        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            if (entry.getValue() >= 4 && setPieceTypes.getOrDefault(entry.getKey(), java.util.Collections.emptySet()).size() >= 4) {
                hasFullSet = true;
                break;
            }
        }
        if (!hasFullSet) {
            if (p.hasMetadata("vkchat_set_bonus_active")) {
                removeSetPotionEffects(p);
                p.removeMetadata("vkchat_set_bonus_active", plugin);
            }
            return;
        }

        // Не сбрасываем эффекты каждую секунду: это вызывало визуальное мигание баффов и сердец.
        // Эффекты просто обновляются длинной длительностью, а снимаются только когда сет больше не активен.
        p.setMetadata("vkchat_set_bonus_active", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        
        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            if (entry.getValue() >= 4 && setPieceTypes.getOrDefault(entry.getKey(), java.util.Collections.emptySet()).size() >= 4) { // Фулл сет: 4 предмета и 4 разных слота
                String set = entry.getKey();
                
                // Рассчитываем силу баффов в зависимости от средней заточки сета!
                int strAmp = 0; // Strength I
                int resAmp = 1; // Resistance II
                int speedAmp = 2; // Speed III
                int jumpAmp = 2; // Jump III
                int hasteAmp = 4; // Haste V
                int regenAmp = 1; // Regen II
                
                if (avgLvl >= 15) {
                    strAmp += 1;
                    resAmp += 1;
                    speedAmp += 1;
                    jumpAmp += 1;
                    hasteAmp += 1;
                    regenAmp += 1;
                }
                if (avgLvl >= 20) {
                    strAmp += 1;
                    resAmp += 1;
                    speedAmp += 1;
                    jumpAmp += 1;
                    hasteAmp += 1;
                    regenAmp += 1;
                }

                if (set.equals("bogatyr")) {
                    // Светлая Империя: Сила + Сопротивление. Дебафф: Утомление II (-15% скорость атаки)
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, strAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, resAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING, 1200, 1, true, false));
                } else if (set.equals("sokol")) {
                    // Темная Империя: Скорость + Прыгучесть
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, speedAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, jumpAmp, true, false));
                } else if (set.equals("udarnik")) {
                    // Светлый Совет: Спешка. Дебафф: Слабость II (-20% урон)
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING, 1200, hasteAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 1200, 1, true, false));
                } else if (set.equals("tankist")) {
                    // Темный Совет: Сопротивление + Невидимость. Дебафф: Медлительность III (-30% скорость)
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, resAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 1200, 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 1200, 2, true, false));
                    if (avgLvl >= 15) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 1200, avgLvl >= 20 ? 3 : 1, true, false));
                    }
                } else if (set.equals("volhv")) {
                    // Светлые Язычники: Дыхание под водой + Грация Дельфина.
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE, 1200, 0, true, false));
                    
                    // Регенерация под солнцем днем
                    boolean isDay = p.getLocation().getBlock().getLightFromSky() > 10 && p.getWorld().getTime() < 12000;
                    if (avgLvl >= 20) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 1200, isDay ? regenAmp + 1 : regenAmp, true, false));
                    } else if (avgLvl >= 15) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 1200, isDay ? regenAmp + 1 : 0, true, false));
                    } else if (isDay) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 1200, regenAmp, true, false));
                    }
                    
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 1200, 0, true, false)); // Дебафф силы
                } else if (set.equals("koshchey")) {
                    // Темные Язычники: Сила. Дебафф: Медлительность II
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, strAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 1200, 1, true, false));
                } else if (set.equals("perun")) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, strAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, speedAmp - 2, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, resAmp, true, false));
                } else if (set.equals("chernobog")) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, strAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, speedAmp - 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 1200, 0, true, false));
                } else if (set.equals("gagarin")) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, jumpAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, speedAmp, true, false));
                } else if (set.equals("el_carpo")) {
                    // EL CARPO: Сопротивление урону III, Увеличение здоровья III, Замедление I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, resAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 1200, 2, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 1200, 0, true, false));
                } else if (set.equals("shadow_monarch")) {
                    // Теневой Монарх: Невидимость, Скорость II, Сила III в темноте
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, 1, true, false));
                    int light = p.getLocation().getBlock().getLightLevel();
                    if (light <= 7) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, 2, true, false));
                    }
                } else if (set.equals("red_cat")) {
                    // РЫЖИЙ КОТ: Скорость III, Прыгучесть IV, Регенерация I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, 2, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, 3, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 1200, 0, true, false));
                } else if (set.equals("dragon_slayer")) {
                    // Драконоборец: Сопротивление урону III, Огнестойкость, Сила II
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, resAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, 1, true, false));
                } else if (set.equals("leshy")) {
                    // Леший: Скорость II, Прыгучесть II
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, 1, true, false));
                } else if (set.equals("proletarian")) {
                    // Пролетарий: Защита от Огня + Регенерация I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 1200, 0, true, false));
                } else if (set.equals("cosmonaut")) {
                    // Космонавт: Плавное падение + Прыгучесть II
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, 1, true, false));
                } else if (set.equals("pioneer")) {
                    // Пионер: Герой деревни + Удача II
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LUCK, 1200, 1, true, false));
                } else if (set.equals("bone_armor")) {
                    // Костяной Доспех: Сопротивление II + Увеличение здоровья II. Дебафф: Медлительность I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, resAmp, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 1200, 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 1200, 0, true, false));
                } else if (set.equals("shadow_blade")) {
                    // Клинок Тени: Сила II + Скорость II. Дебафф: Слабость I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, strAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, speedAmp - 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 1200, 0, true, false));
                } else if (set.equals("ember_crown")) {
                    // Пепельная Корона: Огнестойкость + Сила II. Дебафф: Утомление копания
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, strAmp + 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING, 1200, 1, true, false));
                } else if (set.equals("plague_mist")) {
                    // Моровой Туман: Яд AoE + Регенерация I. Дебафф: Медлительность II
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 1200, 1, true, false));
                } else if (set.equals("starforged")) {
                    // Звёздная Ковка: Удача III + Прыгучесть III. Дебафф: Невидимость (только ночью)
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LUCK, 1200, 2, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, jumpAmp, true, false));
                    boolean isNight = p.getWorld().getTime() >= 13000 && p.getWorld().getTime() <= 23000;
                    if (isNight) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 1200, 0, true, false));
                    }
                } else if (set.equals("slavic_mage")) {
                    // Славянский Волхв: Сила I + Скорость I. Дебафф: Слабость I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 1200, 0, true, false));
                } else if (set.equals("soviet_engineer")) {
                    // Инженер: Спешка I + Сопротивление I. Дебафф: Медлительность I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 1200, 0, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 1200, 0, true, false));
                } else if (set.equals("assassin_cloak")) {
                    // Мантия Убийцы: Скорость II + Прыгучесть II. Дебафф: Слабость I
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 1200, 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 1200, 1, true, false));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 1200, 0, true, false));
                }
            }
        }
    }

    public boolean isWearingSet(Player p, String setName) {
        int count = 0;
        java.util.Set<String> pieceTypes = new java.util.HashSet<>();
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) {
                String set = armor.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING
                );
                if (setName.equalsIgnoreCase(set)) {
                    if (isLegalSetPiece(armor)) {
                        count++;
                        pieceTypes.add(getArmorPieceType(armor.getType()));
                    } else {
                        warnIllegalSetPiece(p, armor);
                    }
                }
            }
        }
        return count >= 4 && pieceTypes.size() >= 4;
    }

    private String getArmorPieceType(Material mat) {
        String n = mat.name();
        if (n.endsWith("_HELMET")) return "helmet";
        if (n.endsWith("_CHESTPLATE")) return "chestplate";
        if (n.endsWith("_LEGGINGS")) return "leggings";
        if (n.endsWith("_BOOTS")) return "boots";
        return n;
    }

    public void updateGearUpgradeLevel(ItemStack item, int newLvl) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        
        NamespacedKey lvlKey = new NamespacedKey(plugin, "upgrade_level");
        meta.getPersistentDataContainer().set(lvlKey, PersistentDataType.INTEGER, newLvl);
        
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (int i = 0; i < lore.size(); i++) {
                String stripped = ChatColor.stripColor(lore.get(i));
                if (stripped.startsWith("Заточка:")) {
                    lore.set(i, ChatColor.YELLOW + "Заточка: +" + newLvl);
                    break;
                }
            }
            meta.setLore(lore);
        }
        
        item.setItemMeta(meta);
    }

    public int calculateGearScore(Player p) {
        if (p == null) return 0;
        int totalScore = 0;
        List<ItemStack> gear = new ArrayList<>();
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) gear.add(armor);
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand != null && hand.hasItemMeta() && isGear(hand.getType())) gear.add(hand);

        for (ItemStack item : gear) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            int upgradeLvl = meta.getPersistentDataContainer().getOrDefault(
                new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
            String rarityKey = getRarityKey(item);
            int rarityBonus = 0;
            switch (rarityKey) {
                case "uncommon": rarityBonus = 10; break;
                case "rare": rarityBonus = 25; break;
                case "epic": rarityBonus = 50; break;
                case "legendary": rarityBonus = 100; break;
                default: rarityBonus = 0; break;
            }
            int enchantCount = countCustomRuneLines(item) + item.getEnchantments().size();
            boolean isSetPiece = meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING);
            int setBonus = (isSetPiece && isLegalSetPiece(item)) ? 1 : 0;
            totalScore += (upgradeLvl * 10) + rarityBonus + (enchantCount * 5) + (setBonus * 20);
        }
        return totalScore;
    }

    public List<String> getGearScoreLore(int score) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "Gear Score: " + ChatColor.YELLOW + score);
        return lore;
    }

    public void awakenMilestoneEnchant(ItemStack item, Player p, int newLvl) {
        if (item == null || !item.hasItemMeta()) return;
        if (newLvl != 5 && newLvl != 10 && newLvl != 15 && newLvl != 20 && newLvl != 25 && newLvl != 30) return;

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        List<String> available = getAvailableCustomEnchants(item.getType());
        Collections.shuffle(available);

        String selectedEnchantKey = null;
        String selectedEnchantName = null;

        Set<String> appliedKeys = new HashSet<>();
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).toLowerCase().trim();
            for (String enchantKey : available) {
                String rawName = plugin.getConfig().getString("custom_enchants." + enchantKey + ".name", "");
                String translated = ChatColor.translateAlternateColorCodes('&', rawName);
                String nameInConfig = ChatColor.stripColor(translated).toLowerCase().trim();
                if (!nameInConfig.isEmpty() && stripped.contains(nameInConfig)) {
                    appliedKeys.add(enchantKey.toLowerCase());
                }
            }
        }

        for (String cKey : available) {
            if (appliedKeys.contains(cKey.toLowerCase())) continue;

            List<String> conflicts = plugin.getConfig().getStringList("custom_enchants." + cKey + ".conflicts");
            boolean conflictFound = false;
            for (String conflict : conflicts) {
                for (String applied : appliedKeys) {
                    if (applied.contains(conflict.toLowerCase())) {
                        conflictFound = true;
                        break;
                    }
                }
                if (conflictFound) break;
            }

            if (!conflictFound) {
                selectedEnchantKey = cKey;
                selectedEnchantName = plugin.getConfig().getString("custom_enchants." + cKey + ".name");
                break;
            }
        }

        if (selectedEnchantName != null) {
            String formatted = ChatColor.translateAlternateColorCodes('&', selectedEnchantName);
            lore.add(Math.min(lore.size(), 4), formatted);
            meta.setLore(lore);
            item.setItemMeta(meta);

            p.sendMessage(ChatColor.GOLD + "🌟 ПРОБУЖДЕНИЕ СИЛЫ! На уровне +" + newLvl + " ваше снаряжение пробудило скрытую силу и получило чары: " + formatted + "!");
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.getWorld().strikeLightningEffect(p.getLocation());
        }
    }
    }
