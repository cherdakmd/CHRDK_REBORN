package ru.example.vkchatoffline.managers;

import java.util.List;

/**
 * VK keyboard factory for Offline Adventures.
 * Все кнопки имеют payload для надёжной обработки на любом клиенте VK.
 */
public final class OfflineKeyboardFactory {
    private OfflineKeyboardFactory() {}

    public static String btn(String label, String command, String color) {
        String payload = "{\"cmd\":\"" + command.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return "{\"action\":{\"type\":\"text\",\"label\":\"" + label.replace("\"", "'")
                + "\",\"payload\":\"" + payload.replace("\"", "\\\"")
                + "\"},\"color\":\"" + color + "\"}";
    }

    private static String keyboard(String... rows) {
        StringBuilder sb = new StringBuilder("{\"one_time\":false,\"inline\":false,\"buttons\":[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(rows[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String row(String... buttons) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(buttons[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public static String main(List<String> routeLabels, List<String> routeIds) {
        int routes = Math.min(routeLabels.size(), 6);
        int rows = (routes + 1) / 2 + 2;
        String[] r = new String[rows];
        int ri = 0;
        for (int i = 0; i < routes; i += 2) {
            String a = btn(routeLabels.get(i), "!пойти " + routeIds.get(i), "primary");
            String b = (i + 1 < routes) ? btn(routeLabels.get(i + 1), "!пойти " + routeIds.get(i + 1), "primary") : null;
            r[ri++] = b != null ? row(a, b) : row(a);
        }
        r[ri++] = row(
                btn("👤 Герой", "!герой", "secondary"),
                btn("🎒 Тайник", "!тайник 1", "positive"));
        r[ri++] = row(
                btn("🛒 Лавка", "!лавка", "positive"),
                btn("❔ Помощь", "!вопрос", "primary"));
        return keyboard(r);
    }

    public static String hero() {
        return keyboard(
                row(btn("👤 Персонаж", "!персонаж", "secondary"), btn("🎲 Сумка", "!сумка", "secondary")),
                row(btn("🧙 Класс", "!класс", "primary"), btn("🐾 Спутник", "!спутник", "primary")),
                row(btn("🧠 Навыки", "!навыки", "primary"), btn("🌿 Расходники", "!расходники", "positive")),
                row(btn("📖 Кампания", "!кампания", "primary"), btn("🧠 Психика", "!психика", "secondary")),
                row(btn("📜 Дневник", "!дневник", "secondary"), btn("⬅ Назад", "!походы", "primary"))
        );
    }

    public static String combatChoices() {
        return keyboard(
                row(btn("⚔ Ударить", "!выбор 1", "negative"), btn("🛡 Защита", "!выбор 2", "positive")),
                row(btn("✨ Приём", "!выбор 3", "primary"), btn("🏃 Отступить", "!выбор 4", "secondary"))
        );
    }

    public static String choices() {
        return keyboard(
                row(btn("⚔ Рискнуть", "!выбор 1", "negative"), btn("🛡 Осторожно", "!выбор 2", "positive")),
                row(btn("🔍 Исследовать", "!выбор 3", "primary"), btn("🏃 Отступить", "!выбор 4", "secondary"))
        );
    }

    public static String statusOnly() {
        return keyboard(
                row(btn("⏳ Статус", "!статуспохода", "secondary"), btn("🎒 Тайник", "!тайник 1", "positive")),
                row(btn("💚 Лечиться", "!лечиться", "positive"), btn("🌿 Расходники", "!расходники", "positive")),
                row(btn("🔥 Отдых", "!отдых", "secondary"), btn("🎲 Сумка", "!сумка", "secondary"))
        );
    }

    public static String heal() {
        return keyboard(
                row(btn("💚 Лечиться", "!лечиться", "positive"), btn("⛺ Походы", "!походы", "primary")),
                row(btn("👤 Персонаж", "!персонаж", "secondary"), btn("🎒 Тайник", "!тайник 1", "secondary"))
        );
    }

    public static String shopEquipment() {
        return keyboard(
                row(btn("🗡 Купить оружие", "!купить equip_weapon_iron", "primary"), btn("🛡 Купить броню", "!купить equip_armor_chain", "primary")),
                row(btn("🔮 Купить талисман", "!купить equip_talisman_sanity", "secondary"), btn("⛏ Купить инструмент", "!купить equip_tool_lockpick", "secondary")),
                row(btn("🎒 Купить рюкзак", "!купить equip_backpack_big", "positive"), btn("🌿 Расходники", "!расходники", "positive")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String shopConsumables() {
        return keyboard(
                row(btn("❤️ Зелье лечения", "!купить potion_heal", "positive"), btn("🧠 Зелье рассудка", "!купить potion_sanity", "positive")),
                row(btn("☠ Антидот", "!купить potion_antidote", "secondary"), btn("📜 Свиток побега", "!купить scroll_escape", "secondary")),
                row(btn("🎲 Свиток переброса", "!купить scroll_reroll", "primary"), btn("🕯 Свиток очищения", "!купить scroll_cleanse", "primary")),
                row(btn("⛺ Набор лагеря", "!купить camp_kit", "positive"), btn("🛒 Магазин", "!лавка", "primary"))
        );
    }

    public static String useConsumables() {
        return keyboard(
                row(btn("Использовать ❤️", "!юз potion_heal", "positive"), btn("Использовать 🧠", "!юз potion_sanity", "positive")),
                row(btn("Использовать ☠", "!юз potion_antidote", "secondary"), btn("Использовать 📜", "!юз scroll_escape", "secondary")),
                row(btn("Использовать 🎲", "!юз scroll_reroll", "primary"), btn("Использовать 🕯", "!юз scroll_cleanse", "primary")),
                row(btn("Использовать ⛺", "!юз camp_kit", "positive"), btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String offlineSkills() {
        return keyboard(
                row(btn("Навык: Живучесть", "!навык tough", "positive"), btn("Навык: Клинок", "!навык sharp", "negative")),
                row(btn("Навык: Ловушки", "!навык trap_sense", "primary"), btn("Навык: Удача", "!навык lucky", "primary")),
                row(btn("Навык: Торговец", "!навык trader", "secondary"), btn("Навык: Оккультизм", "!навык occult", "secondary")),
                row(btn("Навык: Травник", "!навык herbalist", "positive"), btn("Навык: Носильщик", "!навык packer", "positive")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String sellStash() {
        return keyboard(
                row(btn("✅ Продать тайник", "!продатьтайник", "positive"), btn("🎒 Тайник", "!тайник 1", "secondary")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String campaign() {
        return keyboard(
                row(btn("Глава I", "!глава 1", "primary"), btn("Глава II", "!глава 2", "primary")),
                row(btn("Глава III", "!глава 3", "primary"), btn("Глава IV", "!глава 4", "primary")),
                row(btn("Глава V", "!глава 5", "negative"), btn("Глава VI", "!глава 6", "negative")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String hospital() {
        return keyboard(
                row(btn("🧠 Терапия рассудка", "!госпиталь sanity", "positive"), btn("✅ Лечение травм", "!госпиталь trauma", "primary")),
                row(btn("🕯 Снять фобию", "!госпиталь fear", "secondary"), btn("🧠 Психика", "!психика", "primary")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String unlock(String routeLabel, String routeId) {
        return keyboard(
                row(btn("🔓 Открыть " + routeLabel, "!открыть " + routeId, "positive"), btn("🎒 Тайник", "!тайник 1", "secondary"))
        );
    }

    public static String stash(int page) {
        int prev = Math.max(1, page - 1), next = page + 1;
        return keyboard(
                row(btn("◀ Тайник " + prev, "!тайник " + prev, "secondary"), btn("Тайник " + next + " ▶", "!тайник " + next, "secondary")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String faq() {
        return keyboard(
                row(btn("❓ Как начать", "!вопрос 1", "primary"), btn("🗺 Маршруты", "!вопрос 2", "primary")),
                row(btn("⏳ Статус похода", "!вопрос 3", "secondary"), btn("🎒 Награды", "!вопрос 4", "positive")),
                row(btn("☠ Смерть", "!вопрос 5", "negative"), btn("🛑 Отмена похода", "!вопрос 6", "secondary"))
        );
    }

    public static String classes() {
        return keyboard(
                row(btn("⚔ Воин", "!класс warrior", "negative"), btn("🏹 Следопыт", "!класс scout", "positive")),
                row(btn("🔮 Маг", "!класс mage", "primary"), btn("🕯 Жрец", "!класс cleric", "secondary")),
                row(btn("🗡 Разбойник", "!класс rogue", "negative"), btn("🛡 Паладин", "!класс paladin", "positive")),
                row(btn("🎯 Рейнджер", "!класс ranger", "primary"), btn("⛺ Походы", "!походы", "secondary"))
        );
    }

    public static String companions() {
        return keyboard(
                row(btn("🐺 Волк", "!спутник wolf", "negative"), btn("🦅 Ворон", "!спутник raven", "positive")),
                row(btn("🧪 Алхимик", "!спутник alchemist", "primary"), btn("🐴 Мул", "!спутник mule", "secondary")),
                row(btn("🐻 Медведь", "!спутник bear", "negative"), btn("🦉 Сова", "!спутник owl", "positive")),
                row(btn("🐍 Змея", "!спутник snake", "primary"), btn("⛺ Походы", "!походы", "secondary"))
        );
    }

    public static String relationships() {
        return keyboard(
                row(btn("📖 Кампания", "!кампания", "primary")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String daily() {
        return keyboard(
                row(btn("📅 Дейлик", "!ежедневка", "primary")),
                row(btn("⛺ Походы", "!походы", "primary"))
        );
    }

    public static String mainMenuKeyboard() {
        return keyboard(
                row(btn("⛺ Походы", "!походы", "primary"), btn("👤 Герой", "!герой", "secondary")),
                row(btn("🎒 Тайник", "!тайник 1", "positive"), btn("❔ Помощь", "!вопрос", "primary"))
        );
    }
}
