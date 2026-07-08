package ru.example.vkchatjobs.resolver;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MaterialResolver — конфиг-управляемый каталог материалов для профессий.
 *
 * Извлечён из JobsListener (44+ хардкоженных Material).
 * Заменяет:
 * - isOre() — хардкод 8+ руд → config.yml jobs.materials.ores
 * - isCrop() — хардкод 4+ культур → config.yml jobs.materials.crops
 * - getIngotFromOre() — хардкод конвертаций → config.yml jobs.materials.ore-to-ingot
 * - getSeedFromCrop() — хардкод конвертаций → config.yml jobs.materials.crop-to-seed
 * - onBreak() _LOG pattern → config.yml jobs.materials.log-suffixes
 * - onCraft() суффиксы брони → config.yml jobs.materials.blacksmith-suffixes
 *
 * Поддержка горячей перезагрузки: reloadConfig() + loadFromConfig()
 */
public class MaterialResolver {

    private final JavaPlugin plugin;

    // ─── Кешированные множества ───
    private Set<Material> oreMaterials = new HashSet<>();
    private Set<Material> cropMaterials = new HashSet<>();
    private Set<String> logSuffixes = new HashSet<>();
    private Set<String> blacksmithSuffixes = new HashSet<>();

    // ─── Маппинги конвертаций ───
    private Map<Material, Material> oreToIngot = new EnumMap<>(Material.class);
    private Map<Material, Material> cropToSeed = new EnumMap<>(Material.class);

    // ─── Кеш для быстрого isOre/isCrop (включая pattern matching) ───
    private Set<String> oreNamePatterns = new HashSet<>();
    private Set<String> cropNamePatterns = new HashSet<>();

    public MaterialResolver(JavaPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    /**
     * Загрузить все определения из config.yml.
     * Вызывается при инициализации и при /jobsadmin reload.
     */
    public void loadFromConfig() {
        oreMaterials.clear();
        cropMaterials.clear();
        logSuffixes.clear();
        blacksmithSuffixes.clear();
        oreToIngot.clear();
        cropToSeed.clear();
        oreNamePatterns.clear();
        cropNamePatterns.clear();

        loadOres();
        loadCrops();
        loadLogSuffixes();
        loadBlacksmithSuffixes();
        loadOreToIngot();
        loadCropToSeed();

        plugin.getLogger().info("[MaterialResolver] Загружено: " +
                oreMaterials.size() + " руд (+ " + oreNamePatterns.size() + " паттернов), " +
                cropMaterials.size() + " культур (+ " + cropNamePatterns.size() + " паттернов), " +
                oreToIngot.size() + " конвертаций руда→слиток, " +
                cropToSeed.size() + " конвертаций культура→семена, " +
                logSuffixes.size() + " суффиксов бревна, " +
                blacksmithSuffixes.size() + " суффиксов кузнеца");
    }

    // ═══════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════

    /** Является ли материал рудой? */
    public boolean isOre(Material m) {
        if (oreMaterials.contains(m)) return true;
        String name = m.name();
        for (String pattern : oreNamePatterns) {
            if (name.contains(pattern)) return true;
        }
        return false;
    }

    /** Является ли материал культурой? */
    public boolean isCrop(Material m) {
        if (cropMaterials.contains(m)) return true;
        String name = m.name();
        for (String pattern : cropNamePatterns) {
            if (name.contains(pattern)) return true;
        }
        return false;
    }

    /** Является ли материал бревном (дерево)? */
    public boolean isLog(Material m) {
        String name = m.name();
        for (String suffix : logSuffixes) {
            if (name.endsWith(suffix)) return true;
        }
        return false;
    }

    /** Является ли предмет крафта кузнечным (оружие/броня)? */
    public boolean isBlacksmithItem(Material m) {
        String name = m.name();
        for (String suffix : blacksmithSuffixes) {
            if (name.endsWith(suffix)) return true;
        }
        return false;
    }

    /** Получить слиток из руды, или null */
    public Material getIngotFromOre(Material ore) {
        Material direct = oreToIngot.get(ore);
        if (direct != null) return direct;
        // Fallback: pattern matching
        String name = ore.name();
        for (Map.Entry<Material, Material> entry : oreToIngot.entrySet()) {
            if (name.contains(entry.getKey().name())) return entry.getValue();
        }
        return null;
    }

    /** Получить семена из культуры, или null */
    public Material getSeedFromCrop(Material crop) {
        return cropToSeed.get(crop);
    }

    // ═══════════════════════════════════════
    // ЗАГРУЗКА ИЗ КОНФИГА
    // ═══════════════════════════════════════

    private void loadOres() {
        List<String> ores = plugin.getConfig().getStringList("jobs.materials.ores");
        for (String entry : ores) {
            try {
                // Поддержка формата "MATERIAL" и "pattern:PATTERN"
                if (entry.startsWith("pattern:")) {
                    oreNamePatterns.add(entry.substring("pattern:".length()).toUpperCase());
                } else {
                    oreMaterials.add(Material.valueOf(entry.toUpperCase()));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        // Дефолт если пусто
        if (oreMaterials.isEmpty() && oreNamePatterns.isEmpty()) {
            registerDefaultOres();
        }
    }

    private void registerDefaultOres() {
        Collections.addAll(oreMaterials,
                Material.DIAMOND_ORE, Material.IRON_ORE, Material.GOLD_ORE,
                Material.COAL_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE,
                Material.REDSTONE_ORE, Material.ANCIENT_DEBRIS);
        Collections.addAll(oreNamePatterns,
                "COPPER", "DEEPSLATE", "NETHER_GOLD", "QUARTZ_ORE");
    }

    private void loadCrops() {
        List<String> crops = plugin.getConfig().getStringList("jobs.materials.crops");
        for (String entry : crops) {
            try {
                if (entry.startsWith("pattern:")) {
                    cropNamePatterns.add(entry.substring("pattern:".length()).toUpperCase());
                } else {
                    cropMaterials.add(Material.valueOf(entry.toUpperCase()));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        if (cropMaterials.isEmpty() && cropNamePatterns.isEmpty()) {
            registerDefaultCrops();
        }
    }

    private void registerDefaultCrops() {
        Collections.addAll(cropMaterials,
                Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
        Collections.addAll(cropNamePatterns, "CROPS", "PUMPKIN", "MELON");
    }

    private void loadLogSuffixes() {
        List<String> suffixes = plugin.getConfig().getStringList("jobs.materials.log-suffixes");
        if (suffixes.isEmpty()) {
            logSuffixes.add("_LOG");
        } else {
            for (String s : suffixes) logSuffixes.add(s.toUpperCase());
        }
    }

    private void loadBlacksmithSuffixes() {
        List<String> suffixes = plugin.getConfig().getStringList("jobs.materials.blacksmith-suffixes");
        if (suffixes.isEmpty()) {
            Collections.addAll(blacksmithSuffixes,
                    "_SWORD", "_CHESTPLATE", "_PICKAXE", "_HELMET",
                    "_LEGGINGS", "_BOOTS", "_AXE");
        } else {
            for (String s : suffixes) blacksmithSuffixes.add(s.toUpperCase());
        }
    }

    private void loadOreToIngot() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("jobs.materials.ore-to-ingot");
        if (sec == null) {
            registerDefaultOreToIngot();
            return;
        }
        for (String key : sec.getKeys(false)) {
            try {
                Material ore = Material.valueOf(key.toUpperCase());
                Material ingot = Material.valueOf(sec.getString(key).toUpperCase());
                oreToIngot.put(ore, ingot);
            } catch (IllegalArgumentException ignored) {}
        }
        if (oreToIngot.isEmpty()) registerDefaultOreToIngot();
    }

    private void registerDefaultOreToIngot() {
        try { oreToIngot.put(Material.IRON_ORE, Material.IRON_INGOT); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.GOLD_ORE, Material.GOLD_INGOT); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.COPPER_ORE, Material.valueOf("COPPER_INGOT")); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.DEEPSLATE_COPPER_ORE, Material.valueOf("COPPER_INGOT")); } catch (Exception ignored) {}
        try { oreToIngot.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP); } catch (Exception ignored) {}
    }

    private void loadCropToSeed() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("jobs.materials.crop-to-seed");
        if (sec == null) {
            registerDefaultCropToSeed();
            return;
        }
        for (String key : sec.getKeys(false)) {
            try {
                Material crop = Material.valueOf(key.toUpperCase());
                Material seed = Material.valueOf(sec.getString(key).toUpperCase());
                cropToSeed.put(crop, seed);
            } catch (IllegalArgumentException ignored) {}
        }
        if (cropToSeed.isEmpty()) registerDefaultCropToSeed();
    }

    private void registerDefaultCropToSeed() {
        try { cropToSeed.put(Material.WHEAT, Material.WHEAT_SEEDS); } catch (Exception ignored) {}
        try { cropToSeed.put(Material.CARROTS, Material.CARROT); } catch (Exception ignored) {}
        try { cropToSeed.put(Material.POTATOES, Material.POTATO); } catch (Exception ignored) {}
        try { cropToSeed.put(Material.BEETROOTS, Material.BEETROOT_SEEDS); } catch (Exception ignored) {}
    }
}
