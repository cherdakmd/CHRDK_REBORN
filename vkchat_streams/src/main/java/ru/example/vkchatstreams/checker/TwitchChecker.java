package ru.example.vkchatstreams.checker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class TwitchChecker {
    private static volatile String cachedToken;
    private static volatile long tokenExpiresAt;
    private static final Object tokenLock = new Object();

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final long TOKEN_SAFETY_MARGIN_MS = 60_000;

    public static String getToken(VKChatStreamsPlugin plugin) {
        String manualToken = plugin.getConfig().getString("twitch.oauth-token", "");
        if (!manualToken.isEmpty() && !manualToken.startsWith("YOUR_")) return manualToken;

        synchronized (tokenLock) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - TOKEN_SAFETY_MARGIN_MS)
                return cachedToken;

            String clientId = plugin.getConfig().getString("twitch.client-id", "");
            String clientSecret = plugin.getConfig().getString("twitch.client-secret", "");
            if (clientId.isEmpty() || clientSecret.isEmpty() || clientId.startsWith("YOUR_")) return "";

            HttpURLConnection conn = null;
            try {
                URI uri = new URI("https://id.twitch.tv/oauth2/token"
                        + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                        + "&grant_type=client_credentials");
                conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                try (OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream())) { w.write(""); w.flush(); }

                int code = conn.getResponseCode();
                if (code != 200) {
                    String error = readErrorStream(conn);
                    plugin.getLogger().warning("Twitch OAuth ошибка " + code + ": " + error);
                    return "";
                }

                JsonObject json = parseJson(conn);
                if (json == null || !json.has("access_token") || !json.has("expires_in")) {
                    plugin.getLogger().warning("Twitch OAuth: неожиданный ответ (нет access_token)");
                    return "";
                }
                cachedToken = json.get("access_token").getAsString();
                long expiresIn = json.get("expires_in").getAsLong();
                tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000;
                plugin.getLogger().info("Twitch токен обновлён (истекает через " + expiresIn + "с)");
                return cachedToken;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка получения Twitch токена", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return "";
        }
    }

    public static Set<StreamEvent> check(VKChatStreamsPlugin plugin) {
        Set<StreamEvent> result = new HashSet<>();
        String clientId = plugin.getConfig().getString("twitch.client-id", "");
        String oauth = getToken(plugin);
        if (clientId.isEmpty() || oauth.isEmpty()) return result;

        for (String channel : plugin.getConfig().getStringList("twitch.channels")) {
            HttpURLConnection conn = null;
            try {
                URI uri = new URI("https://api.twitch.tv/helix/streams?user_login=" + channel.trim().toLowerCase());
                conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("Client-ID", clientId);
                conn.setRequestProperty("Authorization", "Bearer " + oauth);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

                int code = conn.getResponseCode();
                if (code == 401) {
                    plugin.getLogger().warning("Twitch API: невалидный токен, сброс...");
                    resetToken();
                    return result;
                }
                if (code == 429) {
                    plugin.getLogger().warning("Twitch API: rate limit превышен (канал: " + channel + ")");
                    return result;
                }
                if (code != 200) {
                    String error = readErrorStream(conn);
                    plugin.getLogger().warning("Twitch API ошибка " + code + " (канал " + channel + "): " + error);
                    return result;
                }

                JsonObject json = parseJson(conn);
                if (json == null || !json.has("data")) {
                    plugin.getLogger().warning("Twitch API: неожиданный ответ для " + channel);
                    return result;
                }

                JsonArray data = json.getAsJsonArray("data");
                boolean live = false;
                String title = "";
                String url = "https://twitch.tv/" + channel.trim().toLowerCase();
                int viewers = 0;
                String game = "";

                if (data.size() > 0) {
                    JsonObject stream = data.get(0).getAsJsonObject();
                    String type = stream.has("type") ? stream.get("type").getAsString() : "";
                    if ("live".equals(type)) {
                        live = true;
                        title = stream.has("title") ? stream.get("title").getAsString() : "";
                        game = stream.has("game_name") ? stream.get("game_name").getAsString() : "";
                        viewers = stream.has("viewer_count") ? stream.get("viewer_count").getAsInt() : 0;
                    }
                }
                result.add(new StreamEvent(channel.trim(), title, url, live, viewers, game));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка проверки стрима " + channel, e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return result;
    }

    private static JsonObject parseJson(HttpURLConnection conn) {
        try (InputStreamReader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readErrorStream(HttpURLConnection conn) {
        try (InputStreamReader r = new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            int c; while ((c = r.read()) != -1) sb.append((char) c);
            return sb.toString();
        } catch (Exception e) {
            return "(нет тела ошибки)";
        }
    }

    public static void resetToken() { cachedToken = null; tokenExpiresAt = 0; }
}
