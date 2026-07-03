package ru.example.vkchatstreams.checker;

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

public class TwitchChecker {
    private static volatile String cachedToken;
    private static volatile long tokenExpiresAt;
    private static final Object tokenLock = new Object();

    public static String getToken(VKChatStreamsPlugin plugin) {
        String manualToken = plugin.getConfig().getString("streams.twitch.oauth-token", "");
        if (!manualToken.isEmpty() && !manualToken.startsWith("YOUR_")) return manualToken;

        synchronized (tokenLock) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000)
                return cachedToken;

            String clientId = plugin.getConfig().getString("streams.twitch.client-id", "");
            String clientSecret = plugin.getConfig().getString("streams.twitch.client-secret", "");
            if (clientId.isEmpty() || clientSecret.isEmpty() || clientId.startsWith("YOUR_")) return "";

            try {
                URI uri = new URI("https://id.twitch.tv/oauth2/token"
                        + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                        + "&grant_type=client_credentials");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream())) { w.write(""); w.flush(); }

                StringBuilder json = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    int c; while ((c = r.read()) != -1) json.append((char) c);
                }
                String data = json.toString();
                int tIdx = data.indexOf("\"access_token\":\"");
                int eIdx = data.indexOf("\"expires_in\":");
                if (tIdx != -1 && eIdx != -1) {
                    int tEnd = data.indexOf("\"", tIdx + 16);
                    cachedToken = data.substring(tIdx + 16, tEnd);
                    int expStart = eIdx + 13;
                    int expEnd = expStart;
                    while (expEnd < data.length() && Character.isDigit(data.charAt(expEnd))) expEnd++;
                    long expiresIn = Long.parseLong(data.substring(expStart, expEnd));
                    tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000;
                    plugin.getLogger().info("Twitch токен обновлён (истекает через " + expiresIn + "с)");
                    return cachedToken;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка получения Twitch токена: " + e.getMessage());
            }
            return "";
        }
    }

    public static Set<StreamEvent> check(VKChatStreamsPlugin plugin) {
        Set<StreamEvent> result = new HashSet<>();
        String clientId = plugin.getConfig().getString("streams.twitch.client-id", "");
        String oauth = getToken(plugin);
        if (clientId.isEmpty() || oauth.isEmpty()) return result;

        for (String channel : plugin.getConfig().getStringList("streams.twitch.channels")) {
            try {
                URI uri = new URI("https://api.twitch.tv/helix/streams?user_login=" + channel.trim().toLowerCase());
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("Client-ID", clientId);
                conn.setRequestProperty("Authorization", "Bearer " + oauth);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                StringBuilder json = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    int c; while ((c = r.read()) != -1) json.append((char) c);
                }

                String data = json.toString();
                boolean live = data.contains("\"type\":\"live\"");
                String title = "";
                String url = "https://twitch.tv/" + channel.trim().toLowerCase();
                int viewers = 0;
                String game = "";

                if (live) {
                    title = extractJsonString(data, "\"title\":\"");
                    game = extractJsonString(data, "\"game_name\":\"");
                    String vc = extractJsonString(data, "\"viewer_count\":");
                    try { viewers = Integer.parseInt(vc); } catch (NumberFormatException ignored) {}
                }
                result.add(new StreamEvent("Twitch", channel.trim(), title, url, live,
                        "", "", "", viewers, game));
            } catch (Exception ignore) {}
        }
        return result;
    }

    private static String extractJsonString(String json, String field) {
        int idx = json.indexOf(field);
        if (idx == -1) return "";
        int start = idx + field.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end).replace("\\\"", "\"");
    }

    public static void resetToken() { cachedToken = null; tokenExpiresAt = 0; }
}
