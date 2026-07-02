package ru.example.vkchatoffline.managers;

public final class Keyboards {
    private Keyboards() {}

    private static String btn(String label, String command, String color) {
        String safe = command.replace("\"", "\\\"");
        return "{\"action\":{\"type\":\"text\",\"label\":\"" + label + "\",\"payload\":\"{\\\"cmd\\\":\\\"" + safe + "\\\"}\"},\"color\":\"" + color + "\"}";
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

    // ===== ГЛАВНОЕ МЕНЮ =====
    public static String mainMenu() {
        return keyboard(
            row(btn("🌲 Тёмный лес", "!adv start dark_forest", "primary"), btn("⛏ Глубокие шахты", "!adv start deep_mines", "primary")),
            row(btn("🏛 Древние руины", "!adv start ancient_ruins", "primary"), btn("🔥 Незер-пустоши", "!adv start nether_wastes", "negative")),
            row(btn("❄️ Ледяная тундра", "!adv start frozen_tundra", "primary"), btn("🌑 Грань Бездны", "!adv start void_edge", "negative")),
            row(btn("👤 Герой", "!adv hero", "secondary"), btn("📜 Инфо", "!adv info", "secondary"))
        );
    }

    // ===== МЕНЮ ГЕРОЯ =====
    public static String heroMenu() {
        return keyboard(
            row(btn("⚔ Класс", "!adv class", "primary"), btn("📊 Статистика", "!adv stats", "primary")),
            row(btn("🎒 Сумка", "!adv bag", "positive"), btn("🛒 Лавка", "!adv shop", "positive")),
            row(btn("⬅ Назад", "!adv menu", "secondary"))
        );
    }

    // ===== ВЫБОР КЛАССА =====
    public static String classSelect() {
        return keyboard(
            row(btn("⚔ Воин", "!adv setclass WARRIOR", "negative"), btn("🏹 Следопыт", "!adv setclass RANGER", "positive")),
            row(btn("🔮 Маг", "!adv setclass MAGE", "primary"), btn("🛡 Паладин", "!adv setclass PALADIN", "positive")),
            row(btn("🗡 Убийца", "!adv setclass ASSASSIN", "negative")),
            row(btn("⬅ Назад", "!adv menu", "secondary"))
        );
    }

    // ===== ДЕЙСТВИЯ В ПОХОДЕ =====
    public static String adventureActions() {
        return keyboard(
            row(btn("⚔ Рискнуть", "!adv act risk", "negative"), btn("🛡 Осторожно", "!adv act cautious", "positive")),
            row(btn("🔍 Исследовать", "!adv act search", "primary"), btn("🏃 Отступить", "!adv act retreat", "secondary")),
            row(btn("📊 Статус", "!adv status", "secondary"))
        );
    }

    // ===== БОЕВЫЕ ДЕЙСТВИЯ =====
    public static String combatActions(boolean hasSkills, int skillCount) {
        String row1 = row(btn("⚔ Атака", "!adv atk", "negative"), btn("🛡 Защита", "!adv def", "positive"));
        String row3 = row(btn("🧪 Зелье", "!adv potion", "secondary"), btn("🏃 Побег", "!adv flee", "secondary"));

        if (hasSkills) {
            String[] skillColors = {"primary", "primary", "primary", "primary"};
            String[] skillIcons = {"💥", "❄", "⚡", "☠"};
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < skillCount; i++) {
                if (i > 0) sb.append(',');
                sb.append(btn(skillIcons[i] + " Навык " + (i + 1), "!adv skill " + (i + 1), skillColors[i]));
            }
            sb.append("]");
            return keyboard(row1, sb.toString(), row3);
        }
        return keyboard(row1, row3);
    }

    // ===== ПОСЛЕ БОЯ (ПОБЕДА) =====
    public static String afterCombatWin() {
        return keyboard(
            row(btn("▶ Продолжить", "!adv continue", "positive")),
            row(btn("📊 Статус", "!adv status", "secondary"), btn("⬅ В лагерь", "!adv menu", "secondary"))
        );
    }

    // ===== ПОСЛЕ БОЯ (ПОРАЖЕНИЕ) =====
    public static String afterCombatLose() {
        return keyboard(
            row(btn("💊 Лечиться", "!adv heal", "positive")),
            row(btn("🏠 На главную", "!adv menu", "secondary"))
        );
    }

    // ===== ПОСЛЕ СОБЫТИЯ =====
    public static String afterEvent() {
        return keyboard(
            row(btn("▶ Продолжить", "!adv continue", "positive"), btn("📊 Статус", "!adv status", "secondary"))
        );
    }

    // ===== ЗАВЕРШЕНИЕ ПОХОДА =====
    public static String adventureComplete() {
        return keyboard(
            row(btn("🎉 Забрать награды", "!adv claim", "positive")),
            row(btn("📊 Статистика", "!adv stats", "secondary"), btn("🏠 Главное меню", "!adv menu", "secondary"))
        );
    }

    // ===== ПОДТВЕРЖДЕНИЕ =====
    public static String confirm(String action) {
        return keyboard(
            row(btn("✅ Да", "!adv confirm " + action, "positive"), btn("❌ Нет", "!adv cancel", "negative"))
        );
    }

    // ===== ЛАВКА =====
    public static String shopMenu() {
        return keyboard(
            row(btn("🧪 Зелья", "!adv shop potions", "positive"), btn("📦 Ресурсы", "!adv shop resources", "primary")),
            row(btn("🛡 Части сетов", "!adv shop pieces", "positive")),
            row(btn("⬅ Назад", "!adv hero", "secondary"))
        );
    }

    public static String shopBack() {
        return keyboard(row(btn("⬅ Назад в лавку", "!adv shop", "secondary")));
    }
}
