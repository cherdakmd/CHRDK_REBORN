package ru.example.vkchat.util;

import org.bukkit.entity.Player;

/**
 * Централизованный резолвер донат-статусов — доступен из ВСЕХ модулей через core.
 *
 * Убирает захардкоженные if-else цепочки проверки vkchat.donate.*
 * из GearManager, ChatListener, TabManager, BroadcastManager, MarketGuiListener и др.
 *
 * Все модули должны использовать этот класс вместо прямых проверок
 * p.hasPermission("vkchat.donate.overlord") и т.д.
 *
 * IMPROVE #11: Перемещён из vkchat_gear в vkchat_core для использования всеми модулями.
 */
public final class DonateStatusResolver {

    private DonateStatusResolver() {}

    /** ID статусов от низшего к высшему */
    private static final String[] STATUS_IDS = {"spark", "flame", "star", "legend", "overlord"};

    /** Скидки на кузню/рынок по статусам */
    private static final double[] FORGE_DISCOUNTS = {0.10, 0.20, 0.35, 0.50, 0.65};

    /** Множители рынка по статусам */
    private static final double[] MARKET_MULTIPLIERS = {1.10, 1.20, 1.35, 1.50, 1.70};

    /** Множители Jobs XP по статусам */
    private static final double[] JOBS_XP_MULTIPLIERS = {1.10, 1.20, 1.35, 1.50, 1.70};

    /** Префиксы для отображения */
    private static final String[] DISPLAY_NAMES = {
            "⚡ Искра", "🔥 Пламя", "⭐ Звезда", "👑 Легенда", "💎 Властелин"
    };

    // ═══════════════════════════════════════
    // ОСНОВНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════

    /**
     * Получить индекс донат-статуса игрока (0=spark, 4=overlord, -1=нет).
     */
    public static int getStatusIndex(Player player) {
        if (player == null) return -1;
        for (int i = STATUS_IDS.length - 1; i >= 0; i--) {
            if (player.hasPermission("vkchat.donate." + STATUS_IDS[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Есть ли у игрока любой донат-статус. */
    public static boolean hasDonateStatus(Player player) {
        return getStatusIndex(player) >= 0;
    }

    /** Получить ID текущего донат-статуса. */
    public static String getStatusId(Player player) {
        int idx = getStatusIndex(player);
        return idx >= 0 ? STATUS_IDS[idx] : null;
    }

    /** Получить отображаемое имя текущего статуса. */
    public static String getStatusDisplay(Player player) {
        int idx = getStatusIndex(player);
        return idx >= 0 ? DISPLAY_NAMES[idx] : "";
    }

    // ═══════════════════════════════════════
    // СКИДКИ И МНОЖИТЕЛИ
    // ═══════════════════════════════════════

    /** Скидка на операции кузни (0.0 — нет, 0.65 — Властелин). */
    public static double getForgeDiscount(Player player) {
        int idx = getStatusIndex(player);
        return idx >= 0 ? FORGE_DISCOUNTS[idx] : 0.0;
    }

    /** Множитель рынка (1.0 — базовый, 1.70 — Властелин). */
    public static double getMarketMultiplier(Player player) {
        int idx = getStatusIndex(player);
        return idx >= 0 ? MARKET_MULTIPLIERS[idx] : 1.0;
    }

    /** Множитель опыта Jobs. */
    public static double getJobsXpMultiplier(Player player) {
        int idx = getStatusIndex(player);
        return idx >= 0 ? JOBS_XP_MULTIPLIERS[idx] : 1.0;
    }

    /** Скидка на репутацию. */
    public static double getRepDiscount(Player player) {
        return getForgeDiscount(player);
    }

    /** Множитель продажи на рынке. */
    public static double getSellMultiplier(Player player) {
        return getMarketMultiplier(player);
    }

    /** Множитель покупки на рынке. */
    public static double getBuyMultiplier(Player player) {
        double discount = getForgeDiscount(player);
        return 1.0 - discount * 0.5;
    }

    // ═══════════════════════════════════════
    // ЧАТ И TAB
    // ═══════════════════════════════════════

    /** Префикс чата по донат-статусу. */
    public static String getChatPrefix(Player player) {
        int idx = getStatusIndex(player);
        return switch (idx) {
            case 0 -> "§b";
            case 1 -> "§6";
            case 2 -> "§e";
            case 3 -> "§5";
            case 4 -> "§d";
            default -> "§7";
        };
    }

    /** Отображаемое имя для TAB. */
    public static String getTabDisplayName(Player player) {
        int idx = getStatusIndex(player);
        return switch (idx) {
            case 0 -> "§b§lИСКРА";
            case 1 -> "§6§lПЛАМЯ";
            case 2 -> "§e§lЗВЕЗДА";
            case 3 -> "§5§lЛЕГЕНДА";
            case 4 -> "§d§lВЛАСТЕЛИН";
            default -> null;
        };
    }

    /** Имя группы для TAB сортировки. */
    public static String getTabGroup(Player player) {
        int idx = getStatusIndex(player);
        return idx >= 0 ? STATUS_IDS[idx] : "default";
    }

    /** Все ID статусов. */
    public static String[] getStatusIds() {
        return STATUS_IDS.clone();
    }

    /** Все отображаемые имена. */
    public static String[] getDisplayNames() {
        return DISPLAY_NAMES.clone();
    }
}
