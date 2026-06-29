package ru.example.vkchat.vk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Построитель клавиатур для VK Bot API.
 */
public class VKKeyboardBuilder {
    private final List<List<JSONObject>> rows = new ArrayList<>();
    private List<JSONObject> currentRow = new ArrayList<>();
    private boolean inline = false;
    private boolean oneTime = false;

    public VKKeyboardBuilder inline(boolean inline) {
        this.inline = inline;
        return this;
    }

    public VKKeyboardBuilder oneTime(boolean oneTime) {
        this.oneTime = oneTime;
        return this;
    }

    public VKKeyboardBuilder row() {
        if (!currentRow.isEmpty()) {
            rows.add(new ArrayList<>(currentRow));
            currentRow.clear();
        }
        return this;
    }

    public VKKeyboardBuilder button(String label, String payload, String color) {
        JSONObject action = new JSONObject();
        action.put("type", "text");
        action.put("label", label);
        if (payload != null && !payload.isEmpty()) {
            action.put("payload", payload);
        }
        JSONObject btn = new JSONObject();
        btn.put("action", action);
        if (color != null && !color.isEmpty()) {
            btn.put("color", color);
        }
        currentRow.add(btn);
        return this;
    }

    public VKKeyboardBuilder textButton(String label, String command) {
        return button(label, "{\"cmd\":\"" + escapeJson(command) + "\"}", "primary");
    }

    public VKKeyboardBuilder textButton(String label, String command, String color) {
        return button(label, "{\"cmd\":\"" + escapeJson(command) + "\"}", color);
    }

    public VKKeyboardBuilder positive(String label, String command) {
        return textButton(label, command, "positive");
    }

    public VKKeyboardBuilder negative(String label, String command) {
        return textButton(label, command, "negative");
    }

    public VKKeyboardBuilder secondary(String label, String command) {
        return textButton(label, command, "secondary");
    }

    public String build() {
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        JSONArray buttons = new JSONArray();
        for (List<JSONObject> row : rows) {
            JSONArray rowArray = new JSONArray();
            for (JSONObject btn : row) {
                rowArray.put(btn);
            }
            buttons.put(rowArray);
        }
        JSONObject keyboard = new JSONObject();
        keyboard.put("inline", inline);
        if (!inline) {
            keyboard.put("one_time", oneTime);
        }
        keyboard.put("buttons", buttons);
        return keyboard.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== ГЛАВНОЕ МЕНЮ В ЛС БОТА ====================
    
    /**
     * Главное меню при входе в ЛС бота.
     * Два режима: Походы и Управление аккаунтом.
     */
    public static String mainDmMenu() {
        return new VKKeyboardBuilder()
                .oneTime(false)
                .positive("🏕 Походы", "!поход")
                .textButton("👤 Аккаунт", "!аккаунт", "primary")
                .row()
                .textButton("🎰 Рулетка", "!рулетка", "negative")
                .secondary("📊 Профиль", "!профиль")
                .row()
                .secondary("⭐ Рейтинг", "!рейтинг")
                .secondary("🎁 Бонус", "!бонус")
                .row()
                .secondary("🛟 Помощь", "!помощь")
                .build();
    }

    /**
     * Меню управления аккаунтом.
     */
    public static String accountMenu() {
        return new VKKeyboardBuilder()
                .oneTime(false)
                .positive("🔑 Войти", "!вход")
                .negative("🔒 Заблокировать", "!блок")
                .row()
                .textButton("📊 Статус", "!мойстатус", "primary")
                .secondary("🛡 Безопасность", "!безопасность")
                .row()
                .secondary("📋 История входов", "!история")
                .secondary("🔑 Сменить пароль", "!сменитьпароль")
                .row()
                .secondary("🔗 Отвязать ВК", "!отвязать")
                .secondary("🚪 Выйти", "!выйти")
                .row()
                .secondary("◀ Назад", "!меню")
                .build();
    }

    /**
     * Меню походов (перенаправляет в offline модуль).
     */
    public static String adventureMenu() {
        return new VKKeyboardBuilder()
                .oneTime(false)
                .positive("🗺 Маршруты", "!походы")
                .secondary("🎒 Тайник", "!тайник 1")
                .row()
                .secondary("👤 Герой", "!герой")
                .secondary("📖 Кампания", "!кампания")
                .row()
                .secondary("🧠 Навыки", "!навыки")
                .secondary("🛒 Магазин", "!магазин")
                .row()
                .secondary("◀ Назад", "!меню")
                .build();
    }

    // ==================== МЕНЮ РУЛЕТКИ ====================

    public static String rouletteMenu(int currentBet) {
        StringBuilder kb = new StringBuilder("{\"inline\":true,\"buttons\":[");

        // Ряд 1: Ставки
        kb.append("[");
        int[] bets = {100, 250, 500, 1000, 2500};
        for (int i = 0; i < bets.length; i++) {
            if (i > 0) kb.append(",");
            String color = bets[i] == currentBet ? "positive" : "secondary";
            kb.append("{\"action\":{\"type\":\"text\",\"label\":\"").append(bets[i]).append("\",\"payload\":\"{\\\"cmd\\\":\\\"!ставка ").append(bets[i]).append("\\\"}\"},\"color\":\"").append(color).append("\"}");
        }
        kb.append("],");

        // Ряд 2: Крутить / Русская
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🎰 Крутить\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткакрутить\\\"}\"},\"color\":\"positive\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"☠ Русская\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткарусская\\\"}\"},\"color\":\"negative\"}");
        kb.append("],");

        // Ряд 3: Double / Бокс / Достижения
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"⚡ Double\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткадабл\\\"}\"},\"color\":\"primary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"📦 Бокс\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткабокс\\\"}\"},\"color\":\"primary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🏅 Достижения\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткадостижения\\\"}\"},\"color\":\"secondary\"}");
        kb.append("],");

        // Ряд 4: Призы / Стат / Топ
        kb.append("[");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"📦 Призы\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткапризы\\\"}\"},\"color\":\"primary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"📊 Стат\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткастат\\\"}\"},\"color\":\"secondary\"}");
        kb.append(",");
        kb.append("{\"action\":{\"type\":\"text\",\"label\":\"🏆 Топ\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткатоп\\\"}\"},\"color\":\"secondary\"}");
        kb.append("]");

        kb.append("]}");
        return kb.toString();
    }

    public static String rouletteAfterSpin() {
        return "{\"inline\":true,\"buttons\":[" +
                "[{\"action\":{\"type\":\"text\",\"label\":\"🎰 Ещё раз\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткакрутить\\\"}\"},\"color\":\"positive\"}," +
                "{\"action\":{\"type\":\"text\",\"label\":\"☠ Русская\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткарусская\\\"}\"},\"color\":\"negative\"}," +
                "{\"action\":{\"type\":\"text\",\"label\":\"📦 Призы\",\"payload\":\"{\\\"cmd\\\":\\\"!рулеткапризы\\\"}\"},\"color\":\"primary\"}]" +
                "]}";
    }

    // ==================== 2FA КЛАВИАТУРА ====================

    /**
     * Клавиатура 2FA при входе на сервер.
     * Inline — отображается прямо под сообщением.
     */
    public static String twoFaKeyboard(String code) {
        return new VKKeyboardBuilder()
                .inline(true)
                .positive("🔑 Войти: " + code, "!2fa " + code)
                .negative("❌ Блокировка", "!блок " + code)
                .build();
    }

    /**
     * Клавиатура подтверждения смены пароля.
     */
    public static String confirmKeyboard(String action) {
        return new VKKeyboardBuilder()
                .inline(true)
                .positive("✅ Подтвердить", "!подтвердить " + action)
                .negative("❌ Отмена", "!отмена")
                .build();
    }

    // ==================== СТАРЫЕ КЛАВИАТУРЫ (совместимость) ====================

    public static String mainMenuKeyboard() {
        return new VKKeyboardBuilder()
                .oneTime(false)
                .textButton("👤 Профиль", "!профиль", "primary")
                .textButton("⭐ Рейтинг", "!рейтинг", "primary")
                .row()
                .textButton("💼 Работы", "!работы", "secondary")
                .textButton("🎁 Бонус", "!бонус", "secondary")
                .row()
                .textButton("🎒 Поход", "!поход", "positive")
                .textButton("🎰 Казино", "!казино 100", "negative")
                .row()
                .textButton("🛟 Помощь", "!помощь", "secondary")
                .build();
    }

    public static String helpInlineKeyboard() {
        return new VKKeyboardBuilder()
                .inline(true)
                .textButton("👤 Профиль", "!профиль", "primary")
                .textButton("⭐ Рейтинг", "!рейтинг", "primary")
                .row()
                .textButton("💼 Работы", "!работы", "secondary")
                .textButton("🎁 Бонус", "!бонус", "secondary")
                .row()
                .textButton("🎒 Поход", "!поход", "positive")
                .textButton("🛟 Меню", "!меню", "secondary")
                .build();
    }
}
