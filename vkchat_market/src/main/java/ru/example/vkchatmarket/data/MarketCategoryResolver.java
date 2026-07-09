package ru.example.vkchatmarket.data;

import org.bukkit.Material;
import ru.example.vkchatmarket.VKChatMarketPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * MarketCategoryResolver — единый источник логики категорий рынка.
 *
 * Консолидирует разбросанную по MarketGuiListener и MarketItemFactory логику:
 * - Нормализация и сопоставление категорий
 * - Получение списков товаров по категории
 * - Определение категории по названию предмета
 * - Разбор заголовка инвентаря (страница/категория)
 * - Иконки и отображаемые имена категорий
 * - Проверка редких предметов
 */
public final class MarketCategoryResolver {

    private MarketCategoryResolver() {}

    // ═══════════════════════════════════════════════════════════════
    // НОРМАЛИЗАЦИЯ КАТЕГОРИЙ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Нормализовать название категории (RU/EN/алиасы → стандартный ключ).
     */
    public static String normalizeCategory(String category) {
        if (category == null) return "all";
        String c = category.toLowerCase();
        if (c.equals("руды") || c.equals("ore") || c.equals("ores")) return "ores";
        if (c.equals("еда") || c.equals("food")) return "food";
        if (c.equals("дерево") || c.equals("wood")) return "wood";
        if (c.equals("блоки") || c.equals("blocks") || c.equals("building")) return "blocks";
        if (c.equals("земля") || c.equals("earth")) return "earth";
        if (c.equals("лёд") || c.equals("ice")) return "ice";
        if (c.equals("незер") || c.equals("nether")) return "nether";
        if (c.equals("мобы") || c.equals("mob") || c.equals("mobs")) return "mob";
        if (c.equals("декор") || c.equals("decor")) return "decor";
        if (c.equals("декор2") || c.equals("decor2")) return "decor2";
        if (c.equals("limited") || c.equals("лимит") || c.equals("редкости")) return "limited";
        if (c.equals("rare") || c.equals("редкое") || c.equals("книги")) return "rare";
        if (c.equals("trends") || c.equals("тренды")) return "trends";
        if (c.equals("menu") || c.equals("категории")) return "menu";
        if (c.equals("all") || c.equals("все")) return "all";
        return "all";
    }

    // ═══════════════════════════════════════════════════════════════
    // ОТОБРАЖАЕМЫЕ ИМЕНА И ИКОНКИ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Получить отображаемое имя категории на русском.
     */
    public static String getCategoryDisplayName(String category) {
        return switch (category) {
            case "ores" -> "Руды и слитки";
            case "food" -> "Еда и ферма";
            case "wood" -> "Дерево";
            case "blocks" -> "Стройматериалы";
            case "earth" -> "Земля и природа";
            case "ice" -> "Снег и лёд";
            case "nether" -> "Незер";
            case "mob" -> "Лут мобов";
            case "decor" -> "Декор";
            case "decor2" -> "Декор 2";
            case "limited" -> "Редкости дня";
            case "rare" -> "Книги и редкости";
            case "all" -> "Все товары";
            default -> "Биржа";
        };
    }

    /**
     * Получить иконку материала для категории.
     */
    public static Material getCatIcon(String cat) {
        return switch (cat) {
            case "ores" -> Material.IRON_INGOT;
            case "food" -> Material.GOLDEN_CARROT;
            case "wood" -> Material.OAK_LOG;
            case "blocks" -> Material.STONE_BRICKS;
            case "earth" -> Material.GRASS_BLOCK;
            case "mob" -> Material.BONE;
            case "decor" -> Material.PINK_WOOL;
            case "all" -> Material.CHEST;
            default -> Material.PAPER;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // СОПОСТАВЛЕНИЕ КАТЕГОРИЙ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Проверить, принадлежит ли товар к указанной категории.
     * Использует конфигурацию и эвристику по Material-именам.
     */
    public static boolean categoryMatches(VKChatMarketPlugin plugin, String id, String category) {
        category = normalizeCategory(category);
        if (isRareShopItem(id)) return false;
        if (category.equals("limited")) return false;

        // Используем эвристику по ID (config-категории могут быть повреждены кодировкой)
        String guessed = guessCategory(id).toLowerCase();

        if (category.equals("rare")) return guessed.contains("редкост") || id.contains("ENCHANTED_BOOK") || id.contains("ENCHANT");
        if (category.equals("all")) return true;

        if (category.equals("ores"))    return id.contains("INGOT") || id.contains("ORE") || id.contains("DIAMOND") || id.contains("COPPER") || id.contains("EMERALD") || id.contains("SCRAP") || id.contains("DEBRIS") || id.contains("COAL") || id.contains("REDSTONE") || id.contains("GLOWSTONE") || id.contains("QUARTZ") || id.contains("LAPIS") || id.contains("GOLD_") || id.contains("IRON_") || id.contains("RAW_");
        if (category.equals("food"))    return id.contains("BREAD") || id.contains("APPLE") || id.contains("CARROT") || id.contains("POTATO") || id.contains("WHEAT") || id.contains("PUMPKIN") || id.contains("MELON") || id.contains("BERRY") || id.contains("BEETROOT") || id.contains("SUGAR_CANE") || id.contains("BAMBOO") || id.contains("CACTUS");
        if (category.equals("wood"))    return id.contains("LOG") || id.contains("WOOD");
        if (category.equals("blocks"))  return id.contains("STONE") || id.contains("SAND") || id.contains("GLASS") || id.contains("BRICK") || id.contains("BASALT") || id.contains("BLACKSTONE") || id.contains("OBSIDIAN") || id.contains("CLAY") || id.contains("GRANITE") || id.contains("DIORITE") || id.contains("ANDESITE") || id.contains("DIRT") || id.contains("GRASS") || id.contains("GRAVEL") || id.contains("MUD") || id.contains("MOSS") || id.contains("SNOW") || id.contains("ICE") || id.contains("TERRACOTTA");
        if (category.equals("earth"))   return id.contains("DIRT") || id.contains("GRASS") || id.contains("GRAVEL") || id.contains("MUD") || id.contains("MOSS");
        if (category.equals("ice"))     return id.contains("ICE") || id.contains("SNOW");
        if (category.equals("nether"))  return id.contains("SOUL") || id.contains("NETHERRACK") || id.contains("BLACKSTONE") || id.contains("BASALT");
        if (category.equals("mob"))     return id.contains("BONE") || id.contains("STRING") || id.contains("GUNPOWDER") || id.contains("LEATHER") || id.contains("FEATHER") || id.contains("ROTTEN") || id.contains("SPIDER") || id.contains("SLIME") || id.contains("BLAZE") || id.contains("GHAST") || id.contains("MAGMA") || id.contains("PHANTOM") || id.contains("ENDER_PEARL") || id.contains("PRISMARINE");
        if (category.equals("decor"))   return id.contains("WOOL") || id.contains("DYE") || id.contains("INK") || id.contains("AMETHYST") || id.contains("EXPERIENCE_BOTTLE");
        if (category.equals("decor2"))  return id.contains("TERRACOTTA") || id.contains("MUD_BRICK");

        return true;
    }

    /**
     * Эвристическое определение категории по Material-имени.
     */
    public static String guessCategory(String id) {
        if (id.contains("LOG") || id.contains("WOOD") || id.contains("PLANKS")) return "Дерево";
        if (id.contains("ORE") || id.contains("INGOT") || id.contains("DIAMOND") || id.contains("NETHERITE") || id.contains("GOLD")) return "Руды/слитки";
        if (id.contains("APPLE") || id.contains("BREAD") || id.contains("CARROT") || id.contains("POTATO") || id.contains("BEEF") || id.contains("PORK") || id.contains("CHICKEN") || id.contains("WHEAT")) return "Еда/ферма";
        if (id.contains("DIRT") || id.contains("GRASS") || id.contains("GRAVEL") || id.contains("MUD")) return "Земля";
        if (id.contains("ICE") || id.contains("SNOW")) return "Снег/лёд";
        if (id.contains("WOOL") || id.contains("DYE") || id.contains("TERRACOTTA")) return "Декор";
        if (id.contains("BLAZE") || id.contains("ENDER") || id.contains("GHAST") || id.contains("SOUL")) return "Незер/мобы";
        return "Ресурсы";
    }

    // ═══════════════════════════════════════════════════════════════
    // СПИСКИ ТОВАРОВ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Получить список ID товаров для указанной категории.
     */
    public static List<String> getConfiguredItems(VKChatMarketPlugin plugin, String category) {
        List<String> result = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("Секция 'items' отсутствует в config.yml! Проверьте конфиг.");
            return result;
        }
        java.util.Set<String> keys = itemsSection.getKeys(false);
        if (keys.isEmpty()) {
            plugin.getLogger().warning("Секция 'items' пуста! Проверьте конфиг.");
            return result;
        }
        for (String id : keys) {
            if (categoryMatches(plugin, id, category)) result.add(id);
        }
        return result;
    }

    /**
     * Получить список ID лимитированных товаров дня.
     */
    public static List<String> getLimitedItems(VKChatMarketPlugin plugin) {
        List<String> result = new ArrayList<>();
        if (plugin.getConfig().contains("limited-items") && plugin.getConfig().getConfigurationSection("limited-items") != null) {
            result.addAll(plugin.getMarketManager().getRotatedLimitedItems());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // РАЗБОР ЗАГОЛОВКОВ ИНВЕНТАРЯ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Извлечь номер страницы из заголовка инвентаря.
     * Формат: "... 3/5" → вернёт 2 (0-indexed).
     */
    public static int getPageFromTitle(String title) {
        try {
            int slash = title.lastIndexOf('/');
            if (slash < 0) return 0;
            int space = title.lastIndexOf(' ', slash);
            if (space < 0) return 0;
            return Math.max(0, Integer.parseInt(title.substring(space + 1, slash)) - 1);
        } catch (Exception ignored) { return 0; }
    }

    /**
     * Извлечь категорию из заголовка инвентаря.
     */
    public static String getCategoryFromTitle(String title) {
        if (title.contains("Руды")) return "ores";
        if (title.contains("Еда")) return "food";
        if (title.contains("Дерево")) return "wood";
        if (title.contains("Строй")) return "blocks";
        if (title.contains("Земля")) return "earth";
        if (title.contains("Снег") || title.contains("лёд")) return "ice";
        if (title.contains("Незер")) return "nether";
        if (title.contains("Лут") || title.contains("моб")) return "mob";
        if (title.contains("Декор 2")) return "decor2";
        if (title.contains("Декор")) return "decor";
        if (title.contains("Редкости")) return "limited";
        if (title.contains("Тренды")) return "trends";
        return "all";
    }

    // ═══════════════════════════════════════════════════════════════
    // РЕДКИЕ ПРЕДМЕТЫ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Проверить, является ли предмет редким (не продаётся через обычный магазин).
     */
    public static boolean isRareShopItem(String id) {
        return id.contains("TOTEM") || id.contains("ENCHANTED_GOLDEN_APPLE")
                || id.contains("NETHERITE_INGOT") || id.contains("ECHO_SHARD")
                || id.contains("NETHER_STAR") || id.contains("HEART_OF_THE_SEA");
    }
}
