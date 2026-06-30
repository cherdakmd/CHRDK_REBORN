package ru.example.vkchatoffline.managers;

import java.util.ArrayList;
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
        return mainPage(routeLabels, routeIds, 0);
    }

    public static String mainPage(List<String> routeLabels, List<String> routeIds, int page) {
        int perPage = 6;
        int total = routeLabels.size();
        int totalPages = (total + perPage - 1) / perPage;
        int start = page * perPage;
        int end = Math.min(start + perPage, total);

        List<String> rows = new ArrayList<>();

        // Маршруты этой страницы (до 6 штук = 3 ряда)
        for (int i = start; i < end; i += 2) {
            String a = btn(routeLabels.get(i), "!пойти " + routeIds.get(i), "primary");
            String b = (i + 1 < end) ? btn(routeLabels.get(i + 1), "!пойти " + routeIds.get(i + 1), "primary") : null;
            rows.add(b != null ? row(a, b) : row(a));
        }

        // Навигация по страницам
        if (totalPages > 1) {
            List<String> nav = new ArrayList<>();
            if (page > 0) nav.add(btn("◀ Назад", "!походы " + (page - 1), "secondary"));
            nav.add(btn("📄 " + (page + 1) + "/" + totalPages, "!походы " + page, "secondary"));
            if (page < totalPages - 1) nav.add(btn("Вперёд ▶", "!походы " + (page + 1), "secondary"));
            rows.add(row(nav.toArray(new String[0])));
        }

        // Фиксированные кнопки
        rows.add(row(
                btn("👤 Герой", "!герой", "secondary"),
                btn("🎒 Тайник", "!тайник 1", "positive")));
        rows.add(row(
                btn("🛒 Лавка", "!лавка", "positive"),
                btn("❔ Помощь", "!вопрос", "primary")));

        return keyboard(rows.toArray(new String[0]));
    }

    public static String hero() {
        return keyboard(
                row(btn("📊 Характеристики", "!характеристики", "primary"), btn("🎲 Сумка", "!сумка", "secondary")),
                row(btn("⚔ Класс", "!класс", "primary"), btn("🐾 Спутник", "!спутник", "primary")),
                row(btn("🌳 Навыки", "!навыки", "primary"), btn("🌿 Расходники", "!расходники", "positive")),
                row(btn("📖 Кампания", "!кампания", "primary"), btn("📜 Дневник", "!дневник", "secondary")),
                row(btn("🏥 Госпиталь", "!госпиталь", "secondary"), btn("⬅ Назад", "!походы", "primary"))
        );
    }

    public static String combatChoices() {
        return keyboard(
                row(btn("⚔ Атака", "!выбор 1", "negative"), btn("🛡 Защита", "!выбор 2", "positive")),
                row(btn("🔥 Способность", "!выбор 3", "primary"), btn("🧪 Предмет", "!выбор 4", "secondary")),
                row(btn("🏃 Побег", "!выбор 5", "secondary"))
        );
    }

    public static String choices() {
        return keyboard(
                row(btn("⚔ Рискнуть", "!выбор 1", "negative"), btn("🛡 Осторожно", "!выбор 2", "positive")),
                row(btn("🔍 Исследовать", "!выбор 3", "primary"), btn("🏃 Отступить", "!выбор 4", "secondary"))
        );
    }

    public static String afterChoice() {
        return keyboard(
                row(btn("▶ Продолжить", "!продолжить", "primary")),
                row(btn("🎒 Тайник", "!тайник 1", "positive"), btn("👤 Герой", "!герой", "secondary"))
        );
    }

    public static String combatActive() {
        return keyboard(
                row(btn("⚔ Атака", "!выбор 1", "negative"), btn("🛡 Защита", "!выбор 2", "positive")),
                row(btn("🔥 Способность", "!выбор 3", "primary"), btn("🧪 Предмет", "!выбор 4", "secondary")),
                row(btn("🏃 Побег", "!выбор 5", "secondary"))
        );
    }

    public static String combatVictory() {
        return keyboard(
                row(btn("🎉 Забрать лут", "!забрать", "positive")),
                row(btn("▶ Продолжить поход", "!продолжить", "primary")),
                row(btn("🎒 Тайник", "!тайник 1", "secondary"), btn("👤 Герой", "!герой", "secondary"))
        );
    }

    public static String combatDefeat() {
        return keyboard(
                row(btn("🏥 Лечение", "!лечиться", "positive")),
                row(btn("🎒 Тайник", "!тайник 1", "secondary"), btn("👤 Герой", "!герой", "secondary")),
                row(btn("🏠 На главную", "!походы", "primary"))
        );
    }

    public static String classSelection() {
        return keyboard(
                row(btn("⚔ Воин", "!класс warrior", "negative"), btn("🏹 Следопыт", "!класс scout", "positive")),
                row(btn("🔮 Маг", "!класс mage", "primary"), btn("🕯 Жрец", "!класс cleric", "secondary")),
                row(btn("🗡 Разбойник", "!класс rogue", "negative"), btn("🛡 Паладин", "!класс paladin", "positive")),
                row(btn("🎯 Рейнджер", "!класс ranger", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String companionSelection() {
        return keyboard(
                row(btn("🐺 Волк", "!спутник wolf", "negative"), btn("🦅 Ворон", "!спутник raven", "positive")),
                row(btn("🧪 Алхимик", "!спутник alchemist", "primary"), btn("🐴 Мул", "!спутник mule", "secondary")),
                row(btn("🐻 Медведь", "!спутник bear", "negative"), btn("🦉 Сова", "!спутник owl", "positive")),
                row(btn("🐍 Змея", "!спутник snake", "primary"), btn("🐲 Дракон", "!спутник dragon_whelp", "secondary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String shopMain() {
        return keyboard(
                row(btn("⚔ Оружие", "!лавка оружие", "negative"), btn("🛡 Броня", "!лавка броня", "positive")),
                row(btn("🧪 Расходники", "!лавка расходники", "primary"), btn("💍 Аксессуары", "!лавка аксессуары", "secondary")),
                row(btn("📜 Свитки", "!лавка свитки", "primary"), btn("🔮 Зелья", "!лавка зелья", "positive")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

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

    public static String skillTree() {
        return keyboard(
                row(btn("⚔ Боевая", "!навыки combat", "negative"), btn("🛡 Выживание", "!навыки survival", "positive")),
                row(btn("🔮 Магия", "!навыки magic", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String skillsCombat() {
        return keyboard(
                row(btn("⚔ Удар", "!навык combat_strike", "negative"), btn("🔥 Мощный удар", "!навык combat_power", "positive")),
                row(btn("💥 Рассечение", "!навык combat_cleave", "primary"), btn("😡 Ярость", "!навыk combat_fury", "secondary")),
                row(btn("💀 Берсерк", "!навыk combat_berserk", "negative")),
                row(btn("⬅ Назад", "!навыки", "secondary"))
        );
    }

    public static String skillsSurvival() {
        return keyboard(
                row(btn("🏃 Уклонение", "!навыk survival_dodge", "positive"), btn("💚 Лечение", "!навыk survival_heal", "positive")),
                row(btn("🪤 Ловушка", "!навыk survival_trap", "primary"), btn("👻 Невидимость", "!навыk survival_stealth", "secondary")),
                row(btn("🗡 Теневой удар", "!навыk survival_shadow", "negative")),
                row(btn("⬅ Назад", "!навыки", "secondary"))
        );
    }

    public static String skillsMagic() {
        return keyboard(
                row(btn("🔥 Огненный шар", "!навыk magic_fire", "negative"), btn("❄️ Ледяная стрела", "!навыk magic_ice", "positive")),
                row(btn("⚡ Молния", "!навыk magic_lightning", "primary"), btn("☄️ Метеор", "!навыk magic_meteor", "secondary")),
                row(btn("🌀 Бездна", "!навыk magic_abyss", "negative")),
                row(btn("⬅ Назад", "!навыки", "secondary"))
        );
    }

    public static String hospital() {
        return keyboard(
                row(btn("💊 Лечение травм", "!лечение травм", "positive"), btn("🧠 Терапия рассудка", "!лечение рассудок", "primary")),
                row(btn("😨 Снять фобию", "!лечение фобия", "secondary"), btn("💀 Снять проклятие", "!лечение проклятие", "negative")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String dailyQuest() {
        return keyboard(
                row(btn("📋 Квест дня", "!квест", "primary")),
                row(btn("🎁 Награда", "!награда", "positive")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    public static String settings() {
        return keyboard(
                row(btn("🔔 Уведомления", "!настройки уведомления", "secondary"), btn("📱 Клавиатура", "!настройки клавиатура", "secondary")),
                row(btn("🌐 Язык", "!настройки язык", "secondary"), btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    // ═══ ДОПОЛНИТЕЛЬНЫЕ КЛАВИАТУРЫ ═══

    public static String heal() {
        return keyboard(
                row(btn("💊 Лечение", "!лечиться", "positive")),
                row(btn("🏥 Госпиталь", "!госпиталь", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String statusOnly() {
        return keyboard(
                row(btn("📊 Статус", "!статуспохода", "primary")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    public static String unlock(String label, String route) {
        return keyboard(
                row(btn("🔓 Открыть " + label, "!открыть " + route, "positive")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    public static String stash(int page) {
        return keyboard(
                row(btn("📦 Тайник", "!тайник " + page, "primary")),
                row(btn("💰 Продать всё", "!продать тайник", "negative")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    public static String faq() {
        return keyboard(
                row(btn("📖 Как начать", "!как начать", "primary")),
                row(btn("🗺️ Маршруты", "!маршруты", "primary")),
                row(btn("📊 Статус похода", "!статус похода", "primary")),
                row(btn("🎁 Награды", "!награды", "positive")),
                row(btn("💀 Смерть", "!смерть", "negative")),
                row(btn("⬅ Назад", "!походы", "secondary"))
        );
    }

    public static String classes() {
        return keyboard(
                row(btn("⚔ Воин", "!класс warrior", "negative"), btn("🏹 Следопыт", "!класс scout", "positive")),
                row(btn("🔮 Маг", "!класс mage", "primary"), btn("🕯 Жрец", "!класс cleric", "secondary")),
                row(btn("🗡 Разбойник", "!класс rogue", "negative"), btn("🛡 Паладин", "!класс paladin", "positive")),
                row(btn("🎯 Рейнджер", "!класс ranger", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String companions() {
        return keyboard(
                row(btn("🐺 Волк", "!спутник wolf", "negative"), btn("🦅 Ворон", "!спутник raven", "positive")),
                row(btn("🧪 Алхимик", "!спутник alchemist", "primary"), btn("🐴 Мул", "!спутник mule", "secondary")),
                row(btn("🐻 Медведь", "!спутник bear", "negative"), btn("🦉 Сова", "!спутник owl", "positive")),
                row(btn("🐍 Змея", "!спутник snake", "primary"), btn("🐲 Дракон", "!спутник dragon_whelp", "secondary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String shopEquipment() {
        return keyboard(
                row(btn("⚔ Оружие", "!купить оружие", "negative"), btn("🛡 Броня", "!купить броня", "positive")),
                row(btn("💍 Талисман", "!купить талисман", "primary"), btn("🔧 Инструмент", "!купить инструмент", "secondary")),
                row(btn("🎒 Рюкзак", "!купить рюкзак", "primary")),
                row(btn("⬅ Назад", "!лавка", "secondary"))
        );
    }

    public static String shopConsumables() {
        return keyboard(
                row(btn("❤️ Зелье лечения", "!купить зелье лечения", "positive"), btn("🧠 Зелье рассудка", "!купить зелье рассудка", "primary")),
                row(btn("☠️ Антидот", "!купить антидот", "secondary"), btn("📜 Свиток побега", "!купить свиток побега", "negative")),
                row(btn("🎲 Свиток переброса", "!купить свиток переброса", "primary"), btn("🕯 Свиток очищения", "!купить свиток очищения", "positive")),
                row(btn("⛺ Набор лагеря", "!купить набор лагеря", "secondary")),
                row(btn("⬅ Назад", "!лавка", "secondary"))
        );
    }

    public static String useConsumables() {
        return keyboard(
                row(btn("❤️ Зелье лечения", "!использовать зелье лечения", "positive"), btn("🧠 Зелье рассудка", "!использовать зелье рассудка", "primary")),
                row(btn("☠️ Антидот", "!использовать антидот", "secondary"), btn("📜 Свиток побега", "!использовать свиток побега", "negative")),
                row(btn("🎲 Свиток переброса", "!использовать свиток переброса", "primary"), btn("🕯 Свиток очищения", "!использовать свиток очищения", "positive")),
                row(btn("⛺ Набор лагеря", "!использовать набор лагеря", "secondary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String offlineSkills() {
        return keyboard(
                row(btn("❤️ Живучесть", "!навык: живучесть", "positive"), btn("🗡 Клинок", "!навык: клинок", "negative")),
                row(btn("🪤 Ловушки", "!навык: ловушки", "primary"), btn("🍀 Удача", "!навык: удача", "positive")),
                row(btn("💰 Торговец", "!навык: торговец", "secondary"), btn("🔮 Оккультизм", "!навык: оккультизм", "primary")),
                row(btn("🌿 Травник", "!навык: травник", "positive"), btn("🎒 Носильщик", "!навык: носильщик", "secondary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }

    public static String sellStash() {
        return keyboard(
                row(btn("💰 Продать всё", "!продать тайник подтвердить", "negative")),
                row(btn("❌ Отмена", "!тайник 1", "secondary"))
        );
    }

    public static String campaign() {
        return keyboard(
                row(btn("📖 Глава I", "!глава i", "primary"), btn("📖 Глава II", "!глава ii", "primary")),
                row(btn("📖 Глава III", "!глава iii", "primary"), btn("📖 Глава IV", "!глава iv", "primary")),
                row(btn("📖 Глава V", "!глава v", "primary"), btn("📖 Глава VI", "!глава vi", "primary")),
                row(btn("⬅ Назад", "!герой", "secondary"))
        );
    }
}
