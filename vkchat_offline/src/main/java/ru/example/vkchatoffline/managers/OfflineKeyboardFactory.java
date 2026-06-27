package ru.example.vkchatoffline.managers;

import java.util.List;

/**
 * VK keyboard JSON factory for Offline Adventures.
 * Вынесено из AdventureManager без изменения кнопок/цветов.
 */
public final class OfflineKeyboardFactory {
    private OfflineKeyboardFactory() {}

    public static String btn(String label, String color) {
        return "{\"action\":{\"type\":\"text\",\"label\":\"" + label.replace("\"", "'") + "\"},\"color\":\"" + color + "\"}";
    }

    public static String shopEquipment() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("🗡 Купить оружие", "primary") + "," + btn("🛡 Купить броню", "primary") + "],[" + btn("🔮 Купить талисман", "secondary") + "," + btn("⛏ Купить инструмент", "secondary") + "],[" + btn("🎒 Купить рюкзак", "positive") + "," + btn("🌿 Расходники", "positive") + "],[" + btn("⛺ Походы", "primary") + "]] }";
    }

    public static String shopConsumables() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("❤️ Зелье лечения", "positive") + "," + btn("🧠 Зелье рассудка", "positive") + "],[" + btn("☠ Антидот", "secondary") + "," + btn("📜 Свиток побега", "secondary") + "],[" + btn("🎲 Свиток переброса", "primary") + "," + btn("🕯 Свиток очищения", "primary") + "],[" + btn("⛺ Набор лагеря", "positive") + "," + btn("🛒 Магазин", "primary") + "]] }";
    }

    public static String useConsumables() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("Использовать ❤️", "positive") + "," + btn("Использовать 🧠", "positive") + "],[" + btn("Использовать ☠", "secondary") + "," + btn("Использовать 📜", "secondary") + "],[" + btn("Использовать 🎲", "primary") + "," + btn("Использовать 🕯", "primary") + "],[" + btn("Использовать ⛺", "positive") + "," + btn("⛺ Походы", "primary") + "]] }";
    }

    public static String offlineSkills() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("Навык: Живучесть", "positive") + "," + btn("Навык: Клинок", "negative") + "],[" + btn("Навык: Ловушки", "primary") + "," + btn("Навык: Удача", "primary") + "],[" + btn("Навык: Торговец", "secondary") + "," + btn("Навык: Оккультизм", "secondary") + "],[" + btn("Навык: Травник", "positive") + "," + btn("Навык: Носильщик", "positive") + "],[" + btn("⛺ Походы", "primary") + "]] }";
    }

    public static String sellStash() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("✅ Продать тайник", "positive") + "," + btn("🎒 Тайник", "secondary") + "],[" + btn("⛺ Походы", "primary") + "]] }";
    }

    public static String campaign() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("Глава I", "primary") + "," + btn("Глава II", "primary") + "],[" + btn("Глава III", "primary") + "," + btn("Глава IV", "primary") + "],[" + btn("Глава V", "negative") + "," + btn("Глава VI", "negative") + "],[" + btn("⛺ Походы", "primary") + "]] }";
    }

    public static String hospital() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("🧠 Терапия рассудка", "positive") + "," + btn("✅ Лечение травм", "primary") + "],[" + btn("🕯 Снять фобию", "secondary") + "," + btn("🧠 Психика", "primary") + "],[" + btn("⛺ Походы", "primary") + "]] }";
    }

    /**
     * Главное меню укладывается в безопасный лимит VK: 10 кнопок.
     * Подробные действия вынесены в отдельное меню героя.
     */
    public static String main(List<String> routeLabels) {
        StringBuilder sb = new StringBuilder("{\"one_time\":false,\"inline\":false,\"buttons\":[");
        int addedRows = 0;
        int routes = Math.min(routeLabels.size(), 6);
        for (int i = 0; i < routes; i += 2) {
            if (addedRows++ > 0) sb.append(',');
            sb.append('[').append(btn(routeLabels.get(i), "primary"));
            if (i + 1 < routes) sb.append(',').append(btn(routeLabels.get(i + 1), "primary"));
            sb.append(']');
        }
        sb.append(",[").append(btn("👤 Герой", "secondary")).append(',').append(btn("🎒 Тайник", "positive")).append("]");
        sb.append(",[").append(btn("🛒 Лавка", "positive")).append(',').append(btn("❔ Помощь", "primary")).append("]");
        sb.append("]}");
        return sb.toString();
    }

    public static String hero() {
        return "{\"one_time\":false,\"inline\":false,\"buttons\":["
                + "[" + btn("👤 Персонаж", "secondary") + "," + btn("🎲 Сумка", "secondary") + "],"
                + "[" + btn("🧙 Класс", "primary") + "," + btn("🐾 Спутник", "primary") + "],"
                + "[" + btn("🧠 Навыки", "primary") + "," + btn("🌿 Расходники", "positive") + "],"
                + "[" + btn("📖 Кампания", "primary") + "," + btn("🧠 Психика", "secondary") + "],"
                + "[" + btn("📜 Дневник", "secondary") + "," + btn("⬅ Назад", "primary") + "]"
                + "]}";
    }

    public static String combatChoices() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("⚔ Ударить", "negative") + "," + btn("🛡 Защита", "positive") + "],[" + btn("✨ Приём", "primary") + "," + btn("🏃 Отступить", "secondary") + "]] }"; }
    public static String heal() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("💚 Лечиться", "positive") + "," + btn("⛺ Походы", "primary") + "],[" + btn("👤 Персонаж", "secondary") + "," + btn("🎒 Тайник", "secondary") + "]] }"; }
    public static String choices() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("⚔ Рискнуть", "negative") + "," + btn("🛡 Осторожно", "positive") + "],[" + btn("🔍 Исследовать", "primary") + "," + btn("🏃 Отступить", "secondary") + "]] }"; }
    public static String statusOnly() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("⏳ Статус", "secondary") + "," + btn("🎒 Тайник", "positive") + "],[" + btn("💚 Лечиться", "positive") + "," + btn("🌿 Расходники", "positive") + "],[" + btn("🔥 Отдых", "secondary") + "," + btn("🎲 Сумка", "secondary") + "]] }"; }
    public static String unlock(String routeLabel) { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("🔓 Открыть " + routeLabel, "positive") + "," + btn("🎒 Тайник", "secondary") + "]] }"; }
    public static String stash(int page) { int prev = Math.max(1, page - 1), next = page + 1; return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("◀ Тайник " + prev, "secondary") + "," + btn("Тайник " + next + " ▶", "secondary") + "],[" + btn("⛺ Походы", "primary") + "]] }"; }
    public static String faq() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("❓ Как начать", "primary") + "," + btn("🗺 Маршруты", "primary") + "],[" + btn("⏳ Статус похода", "secondary") + "," + btn("🎒 Награды", "positive") + "],[" + btn("☠ Смерть", "negative") + "," + btn("🛑 Отмена похода", "secondary") + "]] }"; }
    public static String classes() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("⚔ Воин", "negative") + "," + btn("🏹 Следопыт", "positive") + "],[" + btn("🔮 Маг", "primary") + "," + btn("🕯 Жрец", "secondary") + "],[" + btn("⛺ Походы", "primary") + "]] }"; }
    public static String companions() { return "{\"one_time\":false,\"inline\":false,\"buttons\":[[" + btn("🐺 Волк", "negative") + "," + btn("🦅 Ворон", "positive") + "],[" + btn("🧪 Алхимик", "primary") + "," + btn("🐴 Мул", "secondary") + "],[" + btn("⛺ Походы", "primary") + "]] }"; }
}
