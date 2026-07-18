package ru.example.vkchatgear.runes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RuneRegistry — конфиг-управляемый реестр рун.
 *
 * FIX #8: Заменяет захардкоженный getEnchantIdByName() в RuneListener
 * IMPROVE #6: Руны и цены настраиваются из config.yml
 */
public class RuneRegistry {

    private final JavaPlugin plugin;
    private final Map<String, RuneDef> runesById = new LinkedHashMap<>();
    private final Map<String, String> nameToId = new ConcurrentHashMap<>();

    public static final class RuneDef {
        private final String id;
        private final String name;
        private final String displayName;
        private final int basePrice;
        private final List<String> conflicts;
        private final String category; // "weapon", "armor", "tool"

        public RuneDef(String id, String name, String displayName, int basePrice,
                       List<String> conflicts, String category) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.basePrice = basePrice;
            this.conflicts = conflicts;
            this.category = category;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public int getBasePrice() { return basePrice; }
        public List<String> getConflicts() { return conflicts; }
        public String getCategory() { return category; }
    }

    public RuneRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
        buildNameMappings();
    }

    private void loadFromConfig() {
        runesById.clear();
        ConfigurationSection sec = ((ru.example.vkchatgear.VKChatGearPlugin) plugin).getEnchantsConfig().getConfigurationSection("custom_enchants");
        if (sec == null) {
            plugin.getLogger().warning("[RuneRegistry] Секция custom_enchants не найдена в конфиге!");
            return;
        }

        for (String id : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(id);
            if (sub == null) continue;

            String rawName = sub.getString("name", id);
            // Убираем цветовые коды для маппинга имени → id
            String cleanName = org.bukkit.ChatColor.stripColor(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&', rawName)).trim();
            String displayName = rawName;
            List<String> conflicts = sub.getStringList("conflicts");
            String category = sub.getString("category", inferCategory(id));

            // Цена из rune-prices секции или дефолт
            int price = plugin.getConfig().getInt("rune-prices." + id, 1000);

            runesById.put(id, new RuneDef(id, cleanName, displayName, price, conflicts, category));
        }

        plugin.getLogger().info("[RuneRegistry] Загружено " + runesById.size() + " рун из конфига");
    }

    private void buildNameMappings() {
        nameToId.clear();
        for (RuneDef def : runesById.values()) {
            nameToId.put(def.getName().toLowerCase(), def.getId());
            // Также маппим по первому слову (для частичных совпадений)
            String firstWord = def.getName().split(" ")[0].toLowerCase();
            if (!firstWord.isEmpty()) {
                nameToId.putIfAbsent(firstWord, def.getId());
            }
        }
    }

    /**
     * Получить ID руны по отображаемому имени.
     * Заменяет getEnchantIdByName() в RuneListener.
     */
    public String getEnchantIdByName(String name) {
        if (name == null || name.isEmpty()) return null;

        // Точное совпадение
        String id = nameToId.get(name.toLowerCase());
        if (id != null) return id;

        // Частичное совпадение по первому слову
        String firstWord = name.split(" ")[0].toLowerCase();
        id = nameToId.get(firstWord);
        if (id != null) return id;

        // Поиск по содержанию (для длинных имён)
        for (Map.Entry<String, String> entry : nameToId.entrySet()) {
            if (entry.getKey().contains(name.toLowerCase()) || name.toLowerCase().contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Получить определение руны по ID.
     */
    public RuneDef getRune(String id) {
        return runesById.get(id);
    }

    /**
     * Получить все руны для указанного типа предмета.
     */
    public List<RuneDef> getRunesForCategory(String category) {
        List<RuneDef> result = new ArrayList<>();
        for (RuneDef def : runesById.values()) {
            if (def.getCategory().equals(category) || def.getCategory().equals("all")) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Получить все руны.
     */
    public Collection<RuneDef> getAllRunes() {
        return Collections.unmodifiableCollection(runesById.values());
    }

    /**
     * Получить ID руны по PDC-метке rune_name.
     */
    public String resolveRuneId(String runeName) {
        return getEnchantIdByName(runeName);
    }

    /**
     * Обновить реестр (при reload).
     */
    public void reload() {
        loadFromConfig();
        buildNameMappings();
    }

    /**
     * Определить категорию руны по её ID (для обратной совместимости).
     */
    private String inferCategory(String id) {
        // Оружейные чары
        Set<String> weapon = Set.of(
                "vampirism", "venom", "lightning", "frost", "execute", "blindness",
                "bleeding", "meteor", "wither_strike", "armor_piercing", "soul_reaper",
                "berserk", "disarm", "levitation_strike", "wither_burst", "thunder_strike",
                "poison_cloud", "critical_strike", "lifesteal_aura", "meteor_shower",
                "frozen_touch", "disintegration", "vampire_aoe", "soul_drain",
                "chain_lightning", "void_strike", "life_steal", "fire_punch", "paralyze"
        );
        // Броневые чары
        Set<String> armor = Set.of(
                "thorns", "dodge", "fire_aura", "shield", "health_boost", "reflect_magic",
                "second_wind", "heavy_weight", "magma_walker", "ender_shield", "wind_step",
                "golem_skin", "spider_reflexes", "healing_aura", "aquatic_life", "soul_bond",
                "wind_glide", "stone_skin", "life_link", "absorption", "haste_aura"
        );
        // Инструментальные чары
        Set<String> tool = Set.of(
                "haste", "telekenesis", "experience_boost", "ore_magnet", "auto_smelt",
                "timber", "rarity_seal"
        );

        if (weapon.contains(id)) return "weapon";
        if (armor.contains(id)) return "armor";
        if (tool.contains(id)) return "tool";
        return "all";
    }
}
