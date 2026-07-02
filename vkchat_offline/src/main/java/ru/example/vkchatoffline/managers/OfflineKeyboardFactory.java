package ru.example.vkchatoffline.managers;

import java.util.*;

/**
 * VK клавиатуры для офлайн-походов
 * Каждое действие = своя клавиатура
 */
public final class OfflineKeyboardFactory {
    private OfflineKeyboardFactory() {}

    public static String btn(String label, String command, String color) {
        String payload = "{\"cmd\":\"" + command.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return "{\"action\":{\"type\":\"text\",\"label\":\"" + label.replace("\"", "'") + "\",\"payload\":\"" + payload.replace("\"", "\\\"") + "\"},\"color\":\"" + color + "\"}";
    }

    private static String keyboard(String... rows) {
        StringBuilder sb = new StringBuilder("{\"one_time\":false,\"inline\":false,\"buttons\":[");
        for (int i = 0; i < rows.length; i++) { if (i > 0) sb.append(','); sb.append(rows[i]); }
        sb.append("]}");
        return sb.toString();
    }

    private static String row(String... buttons) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < buttons.length; i++) { if (i > 0) sb.append(','); sb.append(buttons[i]); }
        sb.append("]");
        return sb.toString();
    }

    // ═══ ГЛАВНОЕ МЕНЮ (выбор маршрута) ═══
    public static String routeSelection() {
        return keyboard(
                row(btn("🌲 Лес", "!пойти лес", "primary"), btn("⛏ Шахты", "!пойти шахты", "primary")),
                row(btn("🏛 Руины", "!пойти руины", "primary"), btn("🌿 Болота", "!пойти болота", "primary")),
                row(btn("🏰 Замок", "!пойти замок", "primary"), btn("🔥 Незер", "!пойти незер", "primary")),
                row(btn("👤 Герой", "!герой", "secondary"), btn("📖 Кампания", "!кампания", "secondary"))
        );
    }

    // ═══ КЛАВИАТУРА ВО ВРЕМЯ ПОХОДА (выбор действия) ═══
    public static String adventureChoices() {
        return keyboard(
                row(btn("⚔ Рискнуть", "!выбор 1", "negative"), btn("🛡 Осторожно", "!выбор 2", "positive")),
                row(btn("🔍 Исследовать", "!выбор 3", "primary"), btn("🏃 Отступить", "!выбор 4", "secondary")),
                row(btn("📊 Статус", "!статус", "secondary"), btn("👤 Герой", "!герой", "secondary"))
        );
    }

    // ═══ КЛАВИАТУРА БОЯ (пошаговый бой) ═══
    public static String combatActions() {
        return keyboard(
                row(btn("⚔ Атака", "!выбор 1", "negative"), btn("🛡 Защита", "!выбор 2", "positive")),
                row(btn("🔥 Способность", "!выбор 3", "primary"), btn("🧪 Зелье", "!выбор 4", "secondary")),
                row(btn("🏃 Побег", "!выбор 5", "secondary"))
        );
    }

    // ═══ КЛАВИАТУРА ПОСЛЕ ВЫБОРА (продолжить) ═══
    public static String afterChoice() {
        return keyboard(
                row(btn("▶ Продолжить", "!продолжить", "primary")),
                row(btn("📊 Статус", "!статус", "secondary"), btn("🎒 Тайник", "!тайник", "positive"))
        );
    }

    // ═══ КЛАВИАТУРА ПОСЛЕ ПОБЕДЫ В БОЮ ═══
    public static String afterVictory() {
        return keyboard(
                row(btn("🎉 Забрать лут", "!забрать", "positive")),
                row(btn("▶ Продолжить поход", "!продолжить", "primary")),
                row(btn("📊 Статус", "!статус", "secondary"), btn("🎒 Тайник", "!тайник", "positive"))
        );
    }

    // ═══ КЛАВИАТУРА ПОСЛЕ ПОРАЖЕНИЯ ═══
    public static String afterDefeat() {
        return keyboard(
                row(btn("🏥 Лечение", "!лечиться", "positive")),
                row(btn("🏠 На главную", "!походы", "primary"))
        );
    }

    // ═══ КЛАВИАТУРА ПОСЛЕ ЗАВЕРШЕНИЯ ПОХОДА ═══
    public static String afterAdventure() {
        return keyboard(
                row(btn("🎒 Забрать лут", "!забрать", "positive")),
                row(btn("🏠 На главную", "!походы", "primary")),
                row(btn("👤 Герой", "!герой", "secondary"), btn("📖 Кампания", "!кампания", "secondary"))
        );
    }

    // ═══ МЕНЮ ГЕРОЯ ═══
    public static String heroMenu() {
        return keyboard(
                row(btn("📊 Характеристики", "!характеристики", "primary"), btn("🌳 Навыки", "!навыки", "primary")),
                row(btn("📖 Кампания", "!кампания", "primary"), btn("🎒 Тайник", "!тайник", "positive")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    // ═══ МЕНЮ НАВЫКОВ ═══
    public static String skillTree() {
        return keyboard(
                row(btn("⚔ Боевая", "!навыки combat", "negative"), btn("🛡 Выживание", "!навыки survival", "positive")),
                row(btn("🔮 Магия", "!навыки magic", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    // ═══ МЕНЮ КАМПАНИИ ═══
    public static String campaignMenu() {
        return keyboard(
                row(btn("📖 Глава 1", "!глава 1", "primary"), btn("📖 Глава 2", "!глава 2", "primary")),
                row(btn("📖 Глава 3", "!глава 3", "primary"), btn("📖 Глава 4", "!глава 4", "primary")),
                row(btn("📖 Глава 5", "!глава 5", "primary"), btn("📖 Глава 6", "!глава 6", "primary")),
                row(btn("📖 Глава 7", "!глава 7", "primary"), btn("📖 Глава 8", "!глава 8", "primary")),
                row(btn("📖 Глава 9", "!глава 9", "primary"), btn("📖 Глава 10", "!глава 10", "primary")),
                row(btn("📖 Глава 11", "!глава 11", "primary"), btn("📖 Глава 12", "!глава 12", "primary")),
                row(btn("📖 Глава 13", "!глава 13", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    // ═══ МЕНЮ КЛАССОВ ═══
    public static String classSelection() {
        return keyboard(
                row(btn("⚔ Воин", "!класс warrior", "negative"), btn("🏹 Следопыт", "!класс scout", "positive")),
                row(btn("🔮 Маг", "!класс mage", "primary"), btn("🕯 Жрец", "!класс cleric", "secondary")),
                row(btn("🗡 Разбойник", "!класс rogue", "negative"), btn("🛡 Паладин", "!класс paladin", "positive")),
                row(btn("🎯 Рейнджер", "!класс ranger", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    // ═══ МЕНЮ СПУТНИКОВ ═══
    public static String companionSelection() {
        return keyboard(
                row(btn("🐺 Волк", "!спутник wolf", "negative"), btn("🦅 Ворон", "!спутник raven", "positive")),
                row(btn("🧪 Алхимик", "!спутник alchemist", "primary"), btn("🐴 Мул", "!спутник mule", "secondary")),
                row(btn("🐻 Медведь", "!спутник bear", "negative"), btn("🦉 Сова", "!спутник owl", "positive")),
                row(btn("🐍 Змея", "!спутник snake", "primary"), btn("🐲 Дракон", "!спутник dragon_whelp", "secondary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    // ═══ МЕНЮ ЛАВКИ ═══
    public static String shopMenu() {
        return keyboard(
                row(btn("⚔ Оружие", "!лавка оружие", "negative"), btn("🛡 Броня", "!лавка броня", "positive")),
                row(btn("🧪 Расходники", "!лавка расходники", "primary")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    // ═══ ГОСПИТАЛЬ ═══
    public static String hospital() {
        return keyboard(
                row(btn("💊 Лечение", "!лечиться", "positive")),
                row(btn("🏥 Госпиталь", "!госпиталь", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    // ═══ ТАЙНИК ═══
    public static String stashMenu() {
        return keyboard(
                row(btn("📦 Тайник", "!тайник", "positive")),
                row(btn("🏠 На главную", "!походы", "primary"))
        );
    }
}
