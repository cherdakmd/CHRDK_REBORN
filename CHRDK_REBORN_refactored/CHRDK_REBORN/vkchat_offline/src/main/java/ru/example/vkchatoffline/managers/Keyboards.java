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

    // Главное меню — выбор смены
    public static String shiftMenu() {
        return keyboard(
            row(btn("⛏ Короткая (1 ч)", "!shift start 1h", "primary")),
            row(btn("⛏⛏ Стандартная (3 ч)", "!shift start 3h", "primary")),
            row(btn("⛏⛏⛏ Ночная (8 ч)", "!shift start 8h", "positive")),
            row(btn("⛏⛏⛏⛏ Полный день (12 ч)", "!shift start 12h", "positive")),
            row(btn("📊 Статус", "!shift status", "secondary"))
        );
    }

    // Во время смены
    public static String shiftActive() {
        return keyboard(
            row(btn("📊 Проверить статус", "!shift status", "primary")),
            row(btn("❌ Отменить смену", "!shift cancel", "negative"))
        );
    }

    // Смена завершена
    public static String shiftDone() {
        return keyboard(
            row(btn("🎁 Забрать награды", "!shift claim", "positive")),
            row(btn("📊 Статус", "!shift status", "secondary")),
            row(btn("⛏ Новая смена", "!shift menu", "primary"))
        );
    }

    // Подтверждение отмены
    public static String cancelConfirm() {
        return keyboard(
            row(btn("✅ Да, отменить", "!shift cancel_confirm", "negative")),
            row(btn("❌ Нет, оставить", "!shift status", "positive"))
        );
    }
}
