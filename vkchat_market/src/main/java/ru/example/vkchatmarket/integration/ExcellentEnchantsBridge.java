package ru.example.vkchatmarket.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExcellentEnchantsBridge — мост между маркетом и ExcellentEnchants.
 *
 * Функциональность:
 * 1. Получение списка кастомных зачарований из ExcellentEnchants по типу предмета
 * 2. Создание зачарованных книг с EE-чарами для продажи на рынке
 * 3. Определение, является ли зачарование кастомным (EE) или ванильным
 * 4. Генерация случайных EE-книг по раритетности (Common → Mythic)
 * 5. Кеширование реестра зачарований (обновляется при reload)
 *
 * Совместимость: EE 4.x–6.x (reflection), graceful fallback при отсутствии EE
 */
public final class ExcellentEnchantsBridge {

    private static boolean enabled = false;
    private static Plugin eePlugin = null;

    /** Кеш: enchantKey → Enchantment */
    private static final Map<String, Enchantment> EE_ENCHANTS = new ConcurrentHashMap<>();
    /** Кеш: rarity → список зачарований */
    private static final Map<String, List<Enchantment>> EE_BY_RARITY = new ConcurrentHashMap<>();
    /** Кеш: material → список подходящих зачарований */
    private static final Map<Material, List<Enchantment>> EE_BY_MATERIAL = new ConcurrentHashMap<>();

    /** Раритности EE (от обычного к мифическому) */
    public static final String[] RARITIES = {"common", "uncommon", "rare", "exotic", "mythic"};

    private ExcellentEnchantsBridge() {}

    // ═══════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════

    /**
     * Инициализировать мост. Вызывать в onEnable().
     */
    public static void initialize() {
        eePlugin = Bukkit.getPluginManager().getPlugin("ExcellentEnchants");
        enabled = eePlugin != null && eePlugin.isEnabled();

        if (enabled) {
            refreshCache();
            Bukkit.getLogger().info("[Market-EE] ExcellentEnchants найден! Кастомных чар: " + EE_ENCHANTS.size());
        } else {
            Bukkit.getLogger().info("[Market-EE] ExcellentEnchants не найден — используются ванильные чары");
        }
    }

    /**
     * Обновить кеш зачарований. Вызывать при reload.
     */
    public static void refreshCache() {
        EE_ENCHANTS.clear();
        EE_BY_RARITY.clear();
        EE_BY_MATERIAL.clear();

        if (!enabled) return;

        try {
            // ExcellentEnchants 4.x–6.x: EnchantRegistry.getRegistered()
            Class<?> registryClass = Class.forName("su.nightexpress.excellentenchants.api.enchantment.EnchantRegistry");
            java.lang.reflect.Method getRegistered = registryClass.getMethod("getRegistered");
            Collection<?> customs = (Collection<?>) getRegistered.invoke(null);

            for (Object custom : customs) {
                try {
                    // Получить Bukkit Enchantment
                    java.lang.reflect.Method getBukkit = custom.getClass().getMethod("getBukkitEnchantment");
                    Enchantment bukkitEnch = (Enchantment) getBukkit.invoke(custom);
                    if (bukkitEnch == null) continue;

                    String key = bukkitEnch.getKey().getKey().toLowerCase();
                    EE_ENCHANTS.put(key, bukkitEnch);

                    // Получить раритет (если доступно)
                    try {
                        java.lang.reflect.Method getRarity = custom.getClass().getMethod("getRarity");
                        Object rarity = getRarity.invoke(custom);
                        String rarityName = rarity != null ? rarity.toString().toLowerCase() : "common";
                        EE_BY_RARITY.computeIfAbsent(rarityName, k -> new ArrayList<>()).add(bukkitEnch);
                    } catch (Exception e) {
                        EE_BY_RARITY.computeIfAbsent("common", k -> new ArrayList<>()).add(bukkitEnch);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Market-EE] Ошибка чтения ExcellentEnchants: " + e.getMessage());
            enabled = false;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ═══════════════════════════════════════
    // ПОЛУЧЕНИЕ ЗАЧАРОВАНИЙ
    // ═══════════════════════════════════════

    /**
     * Получить все EE-зачарования, применимые к данному материалу.
     */
    public static List<Enchantment> getApplicableEnchants(Material mat) {
        if (!enabled) return Collections.emptyList();

        return EE_BY_MATERIAL.computeIfAbsent(mat, m -> {
            List<Enchantment> result = new ArrayList<>();
            ItemStack dummy = new ItemStack(m);
            for (Enchantment ench : EE_ENCHANTS.values()) {
                try {
                    if (ench.canEnchantItem(dummy)) {
                        result.add(ench);
                    }
                } catch (Exception ignored) {}
            }
            return result;
        });
    }

    /**
     * Получить EE-зачарования по раритетности.
     */
    public static List<Enchantment> getEnchantsByRarity(String rarity) {
        if (!enabled) return Collections.emptyList();
        return EE_BY_RARITY.getOrDefault(rarity.toLowerCase(), Collections.emptyList());
    }

    /**
     * Получить EE-зачарование по ключу.
     */
    public static Enchantment getEnchantment(String key) {
        return EE_ENCHANTS.get(key.toLowerCase());
    }

    /**
     * Проверить, является ли зачарование кастомным (из EE).
     */
    public static boolean isCustomEnchantment(Enchantment ench) {
        if (!enabled) return false;
        return EE_ENCHANTS.containsKey(ench.getKey().getKey().toLowerCase());
    }

    /**
     * Получить все зарегистрированные EE-зачарования.
     */
    public static Collection<Enchantment> getAllEnchantments() {
        return Collections.unmodifiableCollection(EE_ENCHANTS.values());
    }

    // ═══════════════════════════════════════
    // СОЗДАНИЕ ПРЕДМЕТОВ
    // ═══════════════════════════════════════

    /**
     * Создать зачарованную книгу с EE-зачарованием.
     * @param enchKey ключ зачарования (например "vampirism")
     * @param level уровень (1-max)
     * @return ENCHANTED_BOOK с EE-чаром, или null если не найден
     */
    public static ItemStack createEEBook(String enchKey, int level) {
        if (!enabled) return null;

        Enchantment ench = EE_ENCHANTS.get(enchKey.toLowerCase());
        if (ench == null) return null;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;

        level = Math.max(1, Math.min(level, ench.getMaxLevel()));
        meta.addEnchant(ench, level, true);
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Создать случайную EE-книгу заданной раритетности.
     * @param rarity раритет (common/uncommon/rare/exotic/mythic)
     * @return зачарованная книга или null
     */
    public static ItemStack createRandomEEBook(String rarity) {
        if (!enabled) return null;

        List<Enchantment> pool = EE_BY_RARITY.get(rarity.toLowerCase());
        if (pool == null || pool.isEmpty()) {
            // Fallback: все EE-зачарования
            pool = new ArrayList<>(EE_ENCHANTS.values());
        }
        if (pool.isEmpty()) return null;

        Enchantment ench = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
        int maxLvl = ench.getMaxLevel();
        int lvl = maxLvl > 3
                ? java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 4)
                : java.util.concurrent.ThreadLocalRandom.current().nextInt(1, maxLvl + 1);

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;
        meta.addEnchant(ench, lvl, true);
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Создать случайную EE-книгу с весами по раритетности.
     * common=40%, uncommon=30%, rare=18%, exotic=9%, mythic=3%
     */
    public static ItemStack createWeightedRandomEEBook() {
        if (!enabled) return null;

        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100;
        String rarity;
        if (roll < 3) rarity = "mythic";
        else if (roll < 12) rarity = "exotic";
        else if (roll < 30) rarity = "rare";
        else if (roll < 60) rarity = "uncommon";
        else rarity = "common";

        return createRandomEEBook(rarity);
    }

    /**
     * Создать зачарованную книгу для продажи на рынке.
     * Если EE доступен и ключ — EE-зачарование — использует EE.
     * Иначе fallback на ванильные.
     */
    public static ItemStack createBookForMarket(String enchKey, int level) {
        // Пробуем EE
        if (enabled && enchKey != null && !enchKey.isEmpty()) {
            ItemStack eeBook = createEEBook(enchKey, level);
            if (eeBook != null) return eeBook;
        }

        // Fallback: ванильное зачарование
        Enchantment vanilla = Enchantment.getByName(enchKey);
        if (vanilla != null) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = book.getItemMeta();
            if (meta != null) {
                level = Math.max(1, Math.min(level, vanilla.getMaxLevel()));
                meta.addEnchant(vanilla, level, true);
                book.setItemMeta(meta);
            }
            return book;
        }

        return null;
    }

    /**
     * Проверить, подходит ли EE-книга для продажи на рынке.
     * Проверяет конфликты через EnchantmentConflictManager.
     */
    public static boolean isBookValidForMarket(ItemStack book) {
        if (book == null || book.getType() != Material.ENCHANTED_BOOK) return false;
        if (!book.hasItemMeta() || !book.getItemMeta().hasEnchants()) return false;

        ru.example.vkchatmarket.conflicts.EnchantmentConflictManager.ConflictResult result =
                ru.example.vkchatmarket.conflicts.EnchantmentConflictManager.validateEnchantedBook(book);
        return result.isValid();
    }

    /**
     * Получить читаемое имя зачарования (EE или ванильное).
     */
    public static String getEnchantDisplayName(Enchantment ench) {
        if (ench == null) return "?";
        String key = ench.getKey().getKey().toLowerCase();

        // EE кастомные имена
        if (isCustomEnchantment(ench)) {
            // Красиво форматируем: vampirism → Vampirism, soul_reaper → Soul Reaper
            StringBuilder sb = new StringBuilder();
            for (String part : key.split("_")) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return sb.toString();
        }

        // Ванильные имена
        return ru.example.vkchatmarket.conflicts.EnchantmentConflictManager.humanName(key);
    }

    /**
     * Получить раритет зачарования в виде текста для lore.
     */
    public static String getRarityDisplay(Enchantment ench) {
        if (!enabled) return "§7Обычное";

        for (Map.Entry<String, List<Enchantment>> entry : EE_BY_RARITY.entrySet()) {
            if (entry.getValue().contains(ench)) {
                return switch (entry.getKey().toLowerCase()) {
                    case "mythic" -> "§5✦ Мифическое";
                    case "exotic" -> "§d✦ Экзотическое";
                    case "rare" -> "§9✦ Редкое";
                    case "uncommon" -> "§a✦ Необычное";
                    default -> "§7✦ Обычное";
                };
            }
        }
        return "§7✦ Обычное";
    }
}
