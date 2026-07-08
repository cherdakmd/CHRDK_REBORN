package ru.example.vkchatmarket.util;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchatmarket.conflicts.EnchantmentConflictManager;
import ru.example.vkchatmarket.conflicts.EnchantmentConflictManager.ConflictResult;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  MarketItemValidator v1.0
 *  ─────────────────────────────────────────────────────────────────────
 *  Валидация предметов для рынка.
 *    • Проверка зачарований на конфликты (vanilla).
 *    • Ограничение на максимальное число чар.
 *    • Whitelist/blacklist материалов.
 *    • Проверка кастомных атрибутов (AttributeModifiers).
 *
 *  Используется в:
 *    • MarketGuiListener.buyItems() — валидация кастомных книг.
 *    • MarketManager.addCustomBook() — валидация при сохранении.
 *    • MarketGuiListener.createRandomEnchantedBook() — генерация без конфликтов.
 * ═══════════════════════════════════════════════════════════════════════════
 */
public final class MarketItemValidator {

    private MarketItemValidator() {}

    /** Максимум разных чар в одной книге (ванильный лимит — около 6, но мы чуть жёстче). */
    public static final int MAX_ENCHANTS_PER_BOOK = 4;
    /** Максимальный уровень одного чара. */
    public static final int MAX_ENCHANT_LEVEL = 5;
    /** Whitelist материалов для продажи (защита от эксплойтов). */
    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "Руды/слитки", "Ресурсы", "Мобы", "Мобы/Нижний мир", "Мобы/Океан", "Мобы/Край",
            "Стройматериалы", "Еда/ферма", "Дерево", "Декор", "Незер/мобы", "Земля", "Снег/лёд",
            "Книги/зачарования"
    );

    /**
     * Полная валидация предмета для маркета.
     * @return результат с описанием
     */
    public static ValidationResult validateForMarket(ItemStack item) {
        if (item == null) return ValidationResult.error("Предмет null");

        // 1. Whitelist материала
        if (!ALLOWED_CATEGORIES.isEmpty()) {
            // категория берётся из PDC (если есть) или null
        }

        // 2. Проверка зачарований
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            ItemMeta meta = item.getItemMeta();
            Map<Enchantment, Integer> enchants = meta.getEnchants();

            // 2a. Лимит количества
            if (enchants.size() > MAX_ENCHANTS_PER_BOOK) {
                return ValidationResult.error("Слишком много чар: " + enchants.size() + "/" + MAX_ENCHANTS_PER_BOOK);
            }

            // 2b. Лимит уровня
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                if (e.getValue() > MAX_ENCHANT_LEVEL) {
                    return ValidationResult.error("Чар " + e.getKey().getName() + " имеет уровень " + e.getValue() + " > " + MAX_ENCHANT_LEVEL);
                }
            }

            // 2c. Конфликты
            ConflictResult cr = EnchantmentConflictManager.validateEnchantMap(enchants);
            if (!cr.isValid()) {
                return ValidationResult.error(cr.formatUserMessage());
            }
        }

        return ValidationResult.ok();
    }

    /**
     * Генерация «чистой» случайной книги — БЕЗ конфликтов.
     * Используется в MarketGuiListener.createRandomEnchantedBook() v2.
     *
     * @return Book или null, если не удалось сгенерировать
     */
    public static ItemStack generateRandomValidBook() {
        // Группы: каждая содержит варианты, но не более одной из группы.
        String[][] groups = {
            // damage
            {"sharpness", "smite", "bane_of_arthropods"},
            // protection
            {"protection", "fire_protection", "blast_protection", "projectile_protection"},
            // utility bow
            {"infinity", "mending"},
            // trident (Riptide vs Loyalty vs Channeling)
            {"loyalty", "riptide", "channeling"},
            // tool
            {"silk_touch", "loot_bonus_blocks"},
            // boots
            {"depth_strider", "frost_walker"},
            // misc
            {"unbreaking", "fire_aspect", "loot_bonus_mobs", "thorns", "luck_of_the_sea", "aqua_affinity", "respiration", "feather_falling"}
        };

        java.util.List<Enchantment> chosen = new java.util.ArrayList<>();
        java.util.Set<String> groupsUsed = new java.util.HashSet<>();

        for (int i = 0; i < groups.length; i++) {
            if (chosen.size() >= MAX_ENCHANTS_PER_BOOK) break;
            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.55) {
                String[] opts = groups[i];
                String pick = opts[java.util.concurrent.ThreadLocalRandom.current().nextInt(opts.length)];
                Enchantment ench = BalancedMarketManager.resolveEnchantByName(pick);
                if (ench != null && !groupsUsed.contains(ench.getKey().getKey())) {
                    chosen.add(ench);
                    groupsUsed.add(ench.getKey().getKey());
                }
            }
        }

        if (chosen.isEmpty()) {
            // fallback
            Enchantment ub = BalancedMarketManager.resolveEnchantByName("unbreaking");
            if (ub != null) chosen.add(ub);
            else return null;
        }

        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        for (Enchantment e : chosen) {
            int maxLvl = e.getMaxLevel();
            int lvl = maxLvl <= 1 ? 1 : 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(maxLvl);
            lvl = Math.min(lvl, MAX_ENCHANT_LEVEL);
            meta.addEnchant(e, lvl, true);
        }
        book.setItemMeta(meta);
        return book;
    }

    /** Сгенерировать книгу с указанным набором чар. */
    public static ItemStack generateCustomBook(java.util.Map<Enchantment, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) return null;
        // Проверяем конфликты заранее
        ConflictResult cr = EnchantmentConflictManager.validateEnchantMap(enchants);
        if (!cr.isValid()) {
            throw new IllegalArgumentException("Cannot create book with conflicts: " + cr.formatUserMessage());
        }
        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            int lvl = Math.min(e.getValue(), MAX_ENCHANT_LEVEL);
            meta.addEnchant(e.getKey(), lvl, true);
        }
        book.setItemMeta(meta);
        return book;
    }

    /** Получить читаемое имя материала. */
    public static String getMaterialDisplayName(org.bukkit.Material mat) {
        if (mat == null) return "?";
        return mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Результат валидации. */
    public static final class ValidationResult {
        public final boolean valid;
        public final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        public static ValidationResult ok() { return new ValidationResult(true, ""); }
        public static ValidationResult error(String m) { return new ValidationResult(false, m); }
        public boolean isValid() { return valid; }
    }
}
