package ru.example.vkchatgear.enhancements;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.example.vkchatgear.VKChatGearPlugin;
import ru.example.vkchatgear.GearManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GearEnhancements — улучшения UX/UX кузни и боёвки.
 *
 * 1.  Звуковые эффекты при слиянии/перековке
 * 2.  Анимация слияния (кольцо частиц + взрыв)
 * 3.  Предупреждение о низкой прочности (Action Bar)
 * 4.  Уведомление о завершении сета
 * 5.  Gear Score в лоре (для инвентаря-тултипа)
 * 6.  Расписание магических событий (/forge schedule)
 * 7.  Предупреждение о конфликте зачарований
 * 8.  Индикатор залоченного предмета
 * 9.  Предупреждение перед опасной заточкой (+20/+25)
 * 10. Уведомление о неймд гире (мировой анонс)
 * 11. Бонус за полный сет при расчёте salvage
 * 12. Улучшенная анимация перековки (огонь + искры)
 * 13. Фоновая музыка в GUI кузни (нотные блоки)
 */
public class GearEnhancements {

    private final VKChatGearPlugin plugin;
    private final GearManager gearManager;

    /** Кулдаун Action Bar прочности (мс) */
    private final Map<UUID, Long> durabilityWarnCooldown = new ConcurrentHashMap<>();
    private static final long DURABILITY_WARN_INTERVAL = 10000;

    /** Кулдаун звука слияния (не спамить) */
    private final Map<UUID, Long> fusionSoundCooldown = new ConcurrentHashMap<>();

    public GearEnhancements(VKChatGearPlugin plugin) {
        this.plugin = plugin;
        this.gearManager = plugin.getGearManager();
    }

    // ═══════════════════════════════════════════════════
    // 1. ЗВУКОВЫЕ ЭФФЕКТЫ
    // ═══════════════════════════════════════════════════

    public void playUpgradeSuccess(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
    }

    public void playUpgradeFail(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
    }

    public void playFusionSuccess(Player p) {
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.5f);
    }

    public void playFusionFail(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 0.3f, 1.5f);
    }

    public void playReforgeSuccess(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.2f, 1.1f);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.2f);
    }

    public void playReforgeFail(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 0.5f);
    }

    public void playSalvageSuccess(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    // ═══════════════════════════════════════════════════
    // 2. АНИМАЦИЯ СЛИЯНИЯ (кольцо частиц)
    // ═══════════════════════════════════════════════════

    public void playFusionAnimation(Player p, boolean success) {
        Location center = p.getLocation().add(0, 1.0, 0);

        if (success) {
            // Взрыв частиц + огонь
            p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, center, 100, 1.0, 1.0, 1.0, 0.5);
            p.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, center, 50, 0.5, 0.5, 0.5, 0.3);
            p.getWorld().spawnParticle(Particle.FLAME, center, 30, 0.3, 0.3, 0.3, 0.05);
            // Кольцо частиц
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                double x = Math.cos(angle) * 2.0;
                double z = Math.sin(angle) * 2.0;
                p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(x, 0.2, z), 3, 0, 0, 0, 0);
            }
        } else {
            // Дым
            p.getWorld().spawnParticle(Particle.SMOKE_LARGE, center, 40, 0.5, 0.5, 0.5, 0.05);
            p.getWorld().spawnParticle(Particle.SMOKE_LARGE, center, 20, 0.3, 0.3, 0.3, 0.02);
        }
    }

    public void playReforgeAnimation(Player p) {
        Location center = p.getLocation().add(0, 1.0, 0);
        p.getWorld().spawnParticle(Particle.LAVA, center, 15, 0.4, 0.4, 0.4, 0);
        p.getWorld().spawnParticle(Particle.CRIT, center, 25, 0.5, 0.5, 0.5, 0.15);
    }

    // ═══════════════════════════════════════════════════
    // 3. ПРЕДУПРЕЖДЕНИЕ О ПРОЧНОСТИ (Action Bar)
    // ═══════════════════════════════════════════════════

    public void checkDurabilityWarning(Player p) {
        if (!plugin.getConfig().getBoolean("enhancements.durability-warning", true)) return;

        long now = System.currentTimeMillis();
        Long lastWarn = durabilityWarnCooldown.get(p.getUniqueId());
        if (lastWarn != null && now - lastWarn < DURABILITY_WARN_INTERVAL) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        if (!(item.getItemMeta() instanceof Damageable)) return;

        Damageable dmg = (Damageable) item.getItemMeta();
        short maxDur = item.getType().getMaxDurability();
        if (maxDur <= 0) return;

        double pct = 1.0 - ((double) dmg.getDamage() / maxDur);

        if (pct <= 0.2 && pct > 0) {
            durabilityWarnCooldown.put(p.getUniqueId(), now);
            String color = pct <= 0.1 ? "§c§l" : "§e";
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(color + "⚠ " + item.getType().name() + " прочность: " + (int)(pct * 100) + "%"));
        }
    }

    // ═══════════════════════════════════════════════════
    // 4. УВЕДОМЛЕНИЕ О ЗАВЕРШЕНИИ СЕТА
    // ═══════════════════════════════════════════════════

    public void checkSetCompletion(Player p) {
        if (!plugin.getConfig().getBoolean("enhancements.set-completion-notification", true)) return;

        PersistentDataContainer pdc;
        Map<String, Integer> setCounts = new HashMap<>();

        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            pdc = armor.getItemMeta().getPersistentDataContainer();
            String set = pdc.get(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING);
            if (set != null) {
                setCounts.merge(set, 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            if (entry.getValue() == 3) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
                p.sendMessage("§d§l✦ §dДля полного сета не хватает ещё 1 предмета!");
                p.sendMessage("§7Текущий прогресс: §f" + entry.getValue() + "/4");
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // 5. GEAR SCORE В ЛОРЕ
    // ═══════════════════════════════════════════════════

    public int calculateGearScore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        int score = 0;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Бонус за заточку
        int upgradeLvl = pdc.getOrDefault(new NamespacedKey(plugin, "upgrade_level"), PersistentDataType.INTEGER, 0);
        score += upgradeLvl * 15;

        // Бонус за редкость
        String rarity = pdc.get(new NamespacedKey(plugin, "gear_rarity"), PersistentDataType.STRING);
        if (rarity != null) {
            switch (rarity.toLowerCase()) {
                case "common": score += 10; break;
                case "uncommon": score += 25; break;
                case "rare": score += 50; break;
                case "epic": score += 100; break;
                case "legendary": score += 200; break;
                case "ancient": score += 400; break;
            }
        }

        // Бонус за чары
        if (meta.hasEnchants()) {
            for (Map.Entry<Enchantment, Integer> ench : meta.getEnchants().entrySet()) {
                score += ench.getValue() * 5;
            }
        }

        // Бонус за кастомные чары (по лору)
        if (meta.hasLore()) {
            int customCount = 0;
            for (String line : meta.getLore()) {
                String stripped = ChatColor.stripColor(line);
                if (stripped.contains("⚔") || stripped.contains("🛡") || stripped.contains("✦")
                        || stripped.contains("🔥") || stripped.contains("❄") || stripped.contains("⚡")
                        || stripped.contains("☠") || stripped.contains("🌀")) {
                    customCount++;
                }
            }
            score += customCount * 20;
        }

        return score;
    }

    public List<String> getGearScoreLore(ItemStack item) {
        int score = calculateGearScore(item);
        List<String> lore = new ArrayList<>();
        if (score > 0) {
            lore.add("");
            String grade;
            if (score >= 500) grade = "§5§lLEGENDARY";
            else if (score >= 300) grade = "§6§lEPIC";
            else if (score >= 150) grade = "§9§lRARE";
            else if (score >= 50) grade = "§a§lUNCOMMON";
            else grade = "§7§lCOMMON";
            lore.add("§8⚡ Gear Score: " + grade + " §8(" + score + ")");
        }
        return lore;
    }

    // ═══════════════════════════════════════════════════
    // 6. РАСПИСАНИЕ МАГИЧЕСКИХ СОБЫТИЙ
    // ═══════════════════════════════════════════════════

    public List<String> getMagicEventSchedule() {
        List<String> lines = new ArrayList<>();
        String activeName = plugin.getActiveMagicEventName();
        long expireTime = plugin.getActiveMagicEventExpireTime();
        double multiplier = plugin.getActiveMagicEventMultiplier();

        if (activeName != null && System.currentTimeMillis() < expireTime) {
            long remaining = (expireTime - System.currentTimeMillis()) / 1000;
            lines.add("§d§l🔮 Активное событие:");
            lines.add("§d  " + activeName);
            lines.add("§7  Множитель: §f" + String.format("%.1fx", multiplier));
            lines.add("§7  Осталось: §f" + remaining + "с");
        } else {
            lines.add("§7Нет активных магических событий.");
        }

        lines.add("");
        lines.add("§e§l📋 Возможные события:");
        lines.add("§a  Двойная Заточка — скидка 50% на кристаллы");
        lines.add("§a  Неделя Защиты — скидка 40% на защитные руны");
        lines.add("§a  Неделя Атаки — скидка 40% на атакующие руны");
        lines.add("§c  Магический Коллапс — +80% ко всем ценам");
        lines.add("");
        lines.add("§7Шанс: §f20%§7 каждые §f30 мин§7 (15 мин)");
        return lines;
    }

    // ═══════════════════════════════════════════════════
    // 7. КОНФЛИКТ ЗАЧАРОВАНИЙ
    // ═══════════════════════════════════════════════════

    private static final Set<String[]> CONFLICT_PAIRS = new HashSet<>(Arrays.asList(
            new String[]{"Острота", "Громкий Звон"},
            new String[]{"Защита", "Огненная Защита"},
            new String[]{"Шипы", "Снегопад"},
            new String[]{"Полёт", "Тяжесть"},
            new String[]{"Вампиризм", "Жнец"}
    ));

    public String checkEnchantConflicts(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;

        List<String> lore = item.getItemMeta().getLore();
        List<String> foundNames = new ArrayList<>();
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line).trim();
            if (!stripped.isEmpty() && !stripped.startsWith("§") && !stripped.startsWith(" ") && stripped.length() > 3) {
                foundNames.add(stripped);
            }
        }

        for (String[] pair : CONFLICT_PAIRS) {
            boolean hasA = false, hasB = false;
            for (String name : foundNames) {
                if (name.contains(pair[0])) hasA = true;
                if (name.contains(pair[1])) hasB = true;
            }
            if (hasA && hasB) {
                return "§c⚠ Конфликт: " + pair[0] + " и " + pair[1] + " могут взаимно блокироваться!";
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    // 8. ИНДИКАТОР ЗАЛОЧЕННОГО ПРЕДМЕТА
    // ═══════════════════════════════════════════════════

    public boolean isItemLocked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "item_locked"), PersistentDataType.BYTE);
    }

    public void lockItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "item_locked"), PersistentDataType.BYTE, (byte) 1);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("§c🔒 ЗАЛОЧЕН — нельзя перековать/сливать");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public void unlockItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "item_locked"));
        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(l -> ChatColor.stripColor(l).contains("ЗАЛОЧЕН"));
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    // ═══════════════════════════════════════════════════
    // 9. ПРЕДУПРЕЖДЕНИЕ ПЕРЕД ОПАСНОЙ ЗАТОЧКОЙ
    // ═══════════════════════════════════════════════════

    public String getUpgradeLevelWarning(int currentLevel) {
        if (currentLevel >= 25) {
            return "§c§l⚠ МАКСИМАЛЬНЫЙ УРОВЕНЬ ДОСТИГНУТ (+25)";
        }
        if (currentLevel >= 20) {
            return "§c⚠ Уровень +20: шанс успеха снижен, провал = потеря 2 уровней!";
        }
        if (currentLevel >= 15) {
            return "§e⚠ Уровень +15: провал понижает на 1 уровень";
        }
        if (currentLevel >= 10) {
            return "§e⚠ Уровень +10: предмет получает визуальный эффект";
        }
        if (currentLevel >= 5) {
            return "§7Уровень +5: дополнительный шанс зачарования";
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    // 10. УВЕДОМЛЕНИЕ О НЕЙМД ГИРЕ
    // ═══════════════════════════════════════════════════

    public void announceNamedGear(Player p, ItemStack item, String rarity) {
        if (!plugin.getConfig().getBoolean("enhancements.named-gear-announce", true)) return;

        String name = GearManager.getNamedGearName(item);
        if (name == null) return;

        String rarityDisplay;
        switch (rarity.toLowerCase()) {
            case "legendary": rarityDisplay = "§6§lЛЕГЕНДАРНЫЙ"; break;
            case "ancient": rarityDisplay = "§5§lДРЕВНИЙ"; break;
            case "epic": rarityDisplay = "§5ЭПИЧЕСКИЙ"; break;
            default: rarityDisplay = rarity;
        }

        Bukkit.broadcastMessage("§8[§d✦§8] §7Игрок §f" + p.getName() + " §7получил: " + rarityDisplay + " §7" + name);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.2f, 1.5f);
        }
    }

    // ═══════════════════════════════════════════════════
    // 11. BONUS ЗА ПОЛНЫЙ СЕТ (для расчёта salvage)
    // ═══════════════════════════════════════════════════

    public double getSetBonusMultiplier(Player p) {
        if (!plugin.getConfig().getBoolean("enhancements.set-salvage-bonus", true)) return 1.0;

        int setCount = 0;
        String currentSet = null;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            String set = armor.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "gear_set"), PersistentDataType.STRING);
            if (set != null) {
                if (currentSet == null) currentSet = set;
                if (set.equals(currentSet)) setCount++;
            }
        }

        if (setCount >= 4) return 1.5; // +50% за полный сет
        if (setCount >= 3) return 1.25; // +25% за 3 из 4
        return 1.0;
    }

    // ═══════════════════════════════════════════════════
    // 12. МАТЕРИАЛЬНЫЙ КАЛЬКУЛЯТОР СЛИЯНИЯ
    // ═══════════════════════════════════════════════════

    public Map<Material, Integer> calculateFusionMaterials(String fromRarity, String toRarity, int itemPower) {
        Map<Material, Integer> materials = new LinkedHashMap<>();

        Material baseMat;
        int baseAmount;
        switch (toRarity.toLowerCase()) {
            case "uncommon": baseMat = Material.IRON_INGOT; baseAmount = 8; break;
            case "rare": baseMat = Material.GOLD_INGOT; baseAmount = 12; break;
            case "epic": baseMat = Material.DIAMOND; baseAmount = 6; break;
            case "legendary": baseMat = Material.NETHERITE_INGOT; baseAmount = 3; break;
            case "ancient": baseMat = Material.NETHERITE_BLOCK; baseAmount = 1; break;
            default: baseMat = Material.COBBLESTONE; baseAmount = 1;
        }

        baseAmount += Math.max(0, itemPower / 12);
        materials.put(baseMat, baseAmount);

        // Дополнительный материал для эпических+
        if (rarityIndex(toRarity) >= 3) {
            materials.put(Material.QUARTZ, 4 + rarityIndex(toRarity) * 2);
        }
        if (toRarity.equalsIgnoreCase("ancient")) {
            materials.put(Material.NETHER_STAR, 1);
            materials.put(Material.DRAGON_EGG, 1);
        }

        return materials;
    }

    // ═══════════════════════════════════════════════════
    // 13. СНИЖЕНИЕ СТОИМОСТИ ДРЕВНЕГО СЛИЯНИЯ
    // ═══════════════════════════════════════════════════

    public int getAncientFusionDiscount(Player p, int baseCost) {
        int blacksmithLevel = gearManager.getBlacksmithLevel(p);
        if (blacksmithLevel < 10) return baseCost;

        // Каждые 5 уровней кузнеца = -5% к стоимости древнего слияния
        double discount = Math.min(0.40, (blacksmithLevel - 10) * 0.01);
        int discounted = (int) Math.round(baseCost * (1.0 - discount));
        if (discounted != baseCost) {
            p.sendMessage("§a🔧 Бонус кузнеца: -" + (int)(discount * 100) + "% к стоимости древнего слияния");
        }
        return Math.max(100, discounted);
    }

    // ═══════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════

    private int rarityIndex(String rarity) {
        switch (rarity.toLowerCase()) {
            case "common": return 0;
            case "uncommon": return 1;
            case "rare": return 2;
            case "epic": return 3;
            case "legendary": return 4;
            case "ancient": return 5;
            default: return 0;
        }
    }
}
