package ru.example.vkchatstreams.announce;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ru.example.vkchatstreams.StreamEvent;

public final class KeyboardBuilder {

    private KeyboardBuilder() {}

    public static String build(StreamEvent e, String ytUrl, String vkUrl) {
        JsonArray rows = new JsonArray();

        JsonArray twitchRow = new JsonArray();
        if (e.getUrl() != null && !e.getUrl().isEmpty()) {
            twitchRow.add(linkButton(e.getUrl(), "\uD83D\uDCFA Twitch"));
        }
        if (twitchRow.size() > 0) rows.add(twitchRow);

        if (!ytUrl.isEmpty()) {
            JsonArray ytRow = new JsonArray();
            ytRow.add(linkButton(ytUrl, "\uD83D\uDD34 YouTube"));
            rows.add(ytRow);
        }

        if (!vkUrl.isEmpty()) {
            JsonArray vkRow = new JsonArray();
            vkRow.add(linkButton(vkUrl, "\uD83D\uDD35 VK"));
            rows.add(vkRow);
        }

        JsonObject keyboard = new JsonObject();
        keyboard.addProperty("inline", false);
        keyboard.add("buttons", rows);
        return keyboard.toString();
    }

    private static JsonObject linkButton(String url, String label) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "open_link");
        action.addProperty("link", url);
        action.addProperty("label", label);

        JsonObject button = new JsonObject();
        button.add("action", action);
        return button;
    }
}
