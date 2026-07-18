package ru.example.vkchatgear.artifacts;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatgear.VKChatGearPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ArtifactsManager — создание, лор, статы, управление активными артефактами.
 *
 * Артефакты:
 * - Надеваются в offhand (доп. руку)
 * - Максимум 5 активных одновременно (считая offhand)
 * - Пассивные бонусы.apply при экипировке
 * - Активные эффекты (по клику ПКМ или в бою)
 */
public class ArtifactsManager {
    private final VKChatGearPlugin plugin;
    private static final int MAX_ACTIVE_ARTIFACTS = 5;
    private static final NamespacedKey KEY_ARTIFACT_ID = new NamespacedKey(VKChatGearPlugin.getInstance(), "artifact_id");
    private static final NamespacedKey KEY_ARTIFACT_RARITY = new NamespacedKey(VKChatGearPlugin.getInstance(), "artifact_rarity");

    public ArtifactsManager(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════
    // СОЗДАНИЕ
    // ═══════════════════════════════════════════

    public ItemStack createArtifact(ArtifactComponent def) {
        ItemStack it = new ItemStack(def.getMaterial());
        ItemMeta meta = it.getItemMeta();

        // Имя
        ChatColor color = rarityColor(def.getRarity());
        meta.setDisplayName(color + "" + ChatColor.BOLD + def.getName());

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Рарность: " + color + def.getRarity().toUpperCase());
        lore.add(ChatColor.GRAY + def.getDescription());
        lore.add("");

        // Статы
        for (Map.Entry<String, Double> entry : def.getStats().entrySet()) {
            String statName = formatStatName(entry.getKey());
            double val = entry.getValue();
            lore.add(ChatColor.GREEN + "+ " + statName + ": " + formatStatValue(entry.getKey(), val));
        }

        if (!def.getStats().isEmpty()) lore.add("");

        // Слот
        lore.add(ChatColor.YELLOW + "Слот: " + (def.getSlot().equals("offhand") ? "Доп. рука" : def.getSlot()));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Артефакт работает в дополнительной руке.");

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // PDC
        meta.getPersistentDataContainer().set(KEY_ARTIFACT_ID, PersistentDataType.STRING, def.getId());
        meta.getPersistentDataContainer().set(KEY_ARTIFACT_RARITY, PersistentDataType.STRING, def.getRarity());
        meta.setCustomModelData(def.getCustomModelData());

        it.setItemMeta(meta);
        return it;
    }

    /**
     * Случайный артефакт по шансам редкости из конфига.
     */
    public ArtifactComponent rollArtifact() {
        ArtifactRegistry registry = plugin.getArtifactRegistry();
        if (registry == null) return null;

        double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
        String rarity;
        if (roll < 1) rarity = "ancient";
        else if (roll < 6) rarity = "legendary";
        else if (roll < 18) rarity = "epic";
        else if (roll < 45) rarity = "rare";
        else rarity = "common";

        List<ArtifactComponent> pool = registry.getByRarity(rarity);
        if (pool.isEmpty()) {
            // Fallback: любая редкость
            pool = new ArrayList<>(registry.getAll());
        }
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ═══════════════════════════════════════════
    // УПРАВЛЕНИЕ АКТИВНЫМИ
    // ═══════════════════════════════════════════

    /**
     * Получить количество активных артефактов у игрока (в offhand + инвентарь с меткой).
     */
    public int getActiveArtifactCount(Player p) {
        int count = 0;
        // Offhand
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (isArtifact(offhand)) count++;
        // Инвентарь (артефакты в инвентаре считаются "активными" если у игрока есть артефакт в offhand)
        // Но по правилу: макс 5 в инвентаре
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && isArtifact(item)) count++;
        }
        return count;
    }

    /**
     * Проверить, может ли игрок поднять артефакт (лимит 5).
     */
    public boolean canPickupArtifact(Player p) {
        return getActiveArtifactCount(p) < MAX_ACTIVE_ARTIFACTS;
    }

    /**
     * Получить все артефакты в инвентаре игрока.
     */
    public List<ItemStack> getPlayerArtifacts(Player p) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && isArtifact(item)) result.add(item);
        }
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (isArtifact(offhand)) result.add(offhand);
        return result;
    }

    /**
     * Применить пассивные бонусы артефактов (вызывается при экипировке/снятии).
     */
    public void applyArtifactBonuses(Player p) {
        // Снять старые эффекты
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);

        double damageBonus = 0;
        double defenseBonus = 0;
        double speedBonus = 0;
        double hasteBonus = 0;
        double regenTicks = 0;

        for (ItemStack item : getPlayerArtifacts(p)) {
            ArtifactComponent art = getArtifactDef(item);
            if (art == null) continue;
            for (Map.Entry<String, Double> entry : art.getStats().entrySet()) {
                switch (entry.getKey()) {
                    case "damage": damageBonus += entry.getValue(); break;
                    case "defense": defenseBonus += entry.getValue(); break;
                    case "speed": speedBonus += entry.getValue(); break;
                    case "haste": hasteBonus += entry.getValue(); break;
                    case "regen": regenTicks += entry.getValue(); break;
                }
            }
        }

        // Применяем как potion effects (1.16.5 compatible)
        if (damageBonus > 0) {
            int amp = (int)(damageBonus * 10); // 0.05 → level 0 (I)
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INCREASE_DAMAGE, 600, Math.max(0, amp), true, false));
        }
        if (defenseBonus > 0) {
            int amp = (int)(defenseBonus * 10);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE, 600, Math.max(0, amp), true, false));
        }
        if (speedBonus > 0) {
            int amp = (int)(speedBonus * 5);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, 600, Math.max(0, amp), true, false));
        }
        if (hasteBonus > 0) {
            int amp = (int)(hasteBonus * 5);
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.FAST_DIGGING, 600, Math.max(0, amp), true, false));
        }
    }

    // ═══════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════

    public static boolean isArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_ARTIFACT_ID, PersistentDataType.STRING);
    }

    public static String getArtifactId(ItemStack item) {
        if (!isArtifact(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ARTIFACT_ID, PersistentDataType.STRING);
    }

    public static String getArtifactRarity(ItemStack item) {
        if (!isArtifact(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ARTIFACT_RARITY, PersistentDataType.STRING);
    }

    public ArtifactComponent getArtifactDef(ItemStack item) {
        String id = getArtifactId(item);
        if (id == null) return null;
        ArtifactRegistry registry = plugin.getArtifactRegistry();
        return registry != null ? registry.getArtifact(id) : null;
    }

    /**
     * Пересобрать lore артефакта с учётом заточки (level) и перековки (changed stats).
     */
    public void rebuildArtifactLore(ItemStack item) {
        if (item == null || !isArtifact(item)) return;
        ItemMeta meta = item.getItemMeta();
        ArtifactComponent def = getArtifactDef(item);
        if (def == null) return;

        int level = 0;
        NamespacedKey lvlKey = new NamespacedKey(plugin, "artifact_level");
        if (meta.getPersistentDataContainer().has(lvlKey, PersistentDataType.INTEGER)) {
            level = meta.getPersistentDataContainer().get(lvlKey, PersistentDataType.INTEGER);
        }

        // Читаем перекованные статы (если есть)
        NamespacedKey statsKey = new NamespacedKey(plugin, "artifact_stats");
        Map<String, Double> stats = new LinkedHashMap<>(def.getStats());
        if (meta.getPersistentDataContainer().has(statsKey, PersistentDataType.STRING)) {
            String json = meta.getPersistentDataContainer().get(statsKey, PersistentDataType.STRING);
            parseStatsJson(json, stats);
        }

        double multiplier = 1.0 + level * 0.10;

        List<String> lore = new ArrayList<>();
        ChatColor color = rarityColor(def.getRarity());
        lore.add(ChatColor.GRAY + "Рарность: " + color + def.getRarity().toUpperCase());
        if (level > 0) lore.add(ChatColor.YELLOW + "Заточка: " + level + " уровень (+" + (level * 10) + "%)");
        lore.add(ChatColor.GRAY + def.getDescription());
        lore.add("");
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            double val = e.getValue() * multiplier;
            lore.add(ChatColor.GREEN + "+ " + formatStatName(e.getKey()) + ": " + formatStatValue(e.getKey(), val));
        }
        if (!stats.isEmpty()) lore.add("");
        lore.add(ChatColor.YELLOW + "Слот: " + (def.getSlot().equals("offhand") ? "Доп. рука" : def.getSlot()));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Артефакт работает в дополнительной руке.");

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Перековка артефакта — случайно перераспределяет один стат на другой.
     */
    public void reforgeArtifact(ItemStack item) {
        if (item == null || !isArtifact(item)) return;
        ItemMeta meta = item.getItemMeta();
        ArtifactComponent def = getArtifactDef(item);
        if (def == null || def.getStats().isEmpty()) return;

        Map<String, Double> stats = new LinkedHashMap<>(def.getStats());
        // Читаем текущие статы (учитываем предыдущие перековки)
        NamespacedKey statsKey = new NamespacedKey(plugin, "artifact_stats");
        if (meta.getPersistentDataContainer().has(statsKey, PersistentDataType.STRING)) {
            String json = meta.getPersistentDataContainer().get(statsKey, PersistentDataType.STRING);
            parseStatsJson(json, stats);
        }

        List<String> statKeys = new ArrayList<>(stats.keySet());
        if (statKeys.size() < 2) return;

        // Выбираем два разных стата: source (откуда забираем) и target (куда добавляем)
        int srcIdx = ThreadLocalRandom.current().nextInt(statKeys.size());
        int tgtIdx;
        do { tgtIdx = ThreadLocalRandom.current().nextInt(statKeys.size()); } while (tgtIdx == srcIdx);

        String srcKey = statKeys.get(srcIdx);
        String tgtKey = statKeys.get(tgtIdx);
        double srcVal = stats.get(srcKey);
        double tgtVal = stats.get(tgtKey);

        // Перекидываем 20-50% от source на target
        double transfer = srcVal * (0.20 + ThreadLocalRandom.current().nextDouble() * 0.30);
        transfer = Math.min(transfer, srcVal * 0.8); // не более 80% от source
        stats.put(srcKey, srcVal - transfer);
        stats.put(tgtKey, tgtVal + transfer);

        // Сохраняем перекованные статы
        meta.getPersistentDataContainer().set(statsKey, PersistentDataType.STRING, statsToJson(stats));
        item.setItemMeta(meta);
        rebuildArtifactLore(item);
    }

    /**
     * Создать артефакт слияния — рандомный артефакт следующей редкости.
     */
    public ItemStack createMergedArtifact(String newRarity) {
        ArtifactRegistry registry = plugin.getArtifactRegistry();
        if (registry == null) return null;
        List<ArtifactComponent> pool = registry.getByRarity(newRarity);
        if (pool.isEmpty()) return null;
        ArtifactComponent def = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return createArtifact(def);
    }

    /**
     * Пересобрать lore без ссылки на def (для перекованных артефактов).
     */
    public void rebuildArtifactLoreFromItem(ItemStack item) {
        rebuildArtifactLore(item);
    }

    // ═══════════════════════════════════════════
    // УТИЛИТЫ СТАТОВ
    // ═══════════════════════════════════════════

    private static String statsToJson(Map<String, Double> stats) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            if (!first) sb.append(",");
            sb.append(e.getKey()).append(":").append(String.format("%.4f", e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private static void parseStatsJson(String json, Map<String, Double> stats) {
        if (json == null || json.isEmpty()) return;
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    stats.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public static ChatColor rarityColor(String rarity) {
        switch (rarity) {
            case "ancient": return ChatColor.DARK_PURPLE;
            case "legendary": return ChatColor.GOLD;
            case "epic": return ChatColor.BLUE;
            case "rare": return ChatColor.AQUA;
            case "uncommon": return ChatColor.GREEN;
            default: return ChatColor.WHITE;
        }
    }

    public static String formatStatName(String key) {
        switch (key) {
            case "damage": return "Урон";
            case "defense": return "Защита";
            case "speed": return "Скорость";
            case "haste": return "Спешка";
            case "regen": return "Регенерация";
            case "health": return "Здоровье";
            case "lifesteal": return "Вампиризм";
            case "crit_chance": return "Шанс крита";
            case "crit_damage": return "Урон крита";
            default: return key;
        }
    }

    private static String formatStatValue(String key, double val) {
        if (key.contains("chance") || key.contains("damage_bonus") || key.contains("lifesteal")) {
            return String.format("+%.1f%%", val * 100);
        }
        if (key.equals("health")) return String.format("+%.0f ❤", val);
        return String.format("+%.2f", val);
    }
}
