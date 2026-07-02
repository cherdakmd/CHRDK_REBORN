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
     */
    public static String mainDmMenu() {
        return new VKKeyboardBuilder()
                .oneTime(false)
                .positive("👤 Аккаунт", "!аккаунт")
                .secondary("📊 Профиль", "!профиль")
                .row()
                .secondary("📊 Профиль", "!профиль")
                .secondary("⭐ Рейтинг", "!рейтинг")
                .row()
                .secondary("🎁 Бонус", "!бонус")
                .secondary("💼 Работы", "!работы")
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
                .textButton("🎁 Бонус", "!бонус", "secondary")
                .row()
                .textButton("🛟 Меню", "!меню", "secondary")
                .build();
    }
}
