package ru.example.vkchatstreams.checker;

import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class VKChecker {
    public static Set<StreamEvent> check(VKChatStreamsPlugin plugin) {
        Set<StreamEvent> result = new HashSet<>();
        String token = plugin.getConfig().getString("streams.vk.token", "");
        if (token.isEmpty()) return result;

        for (String group : plugin.getConfig().getStringList("streams.vk.groups")) {
            try {
                // VK API: video.get?owner_id=-group_id&v=5.131&access_token=...
                // Сначала получаем group_id по screen_name
                int gid = resolveGroupId(token, group.trim());
                if (gid == 0) continue;

                URI uri = new URI("https://api.vk.com/method/video.get?owner_id=" + gid
                        + "&v=5.131&access_token=" + token);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                StringBuilder json = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    int c; while ((c = r.read()) != -1) json.append((char) c);
                }

                String data = json.toString();
                boolean live = data.contains("\"live\":1") || data.contains("\"type\":\"live\"");
                String title = "";
                String url = "https://vk.com/" + group.trim();
                if (live) {
                    int tIdx = data.indexOf("\"title\":\"");
                    if (tIdx != -1) {
                        int tEnd = data.indexOf("\"", tIdx + 9);
                        title = data.substring(tIdx + 9, tEnd);
                    }
                }
                result.add(new StreamEvent("VK", group.trim(), title, url, live));
            } catch (Exception ignore) {}
        }

        // VK Video (live.vkvideo.ru)
        for (String channel : plugin.getConfig().getStringList("streams.vkvideo.channels")) {
            try {
                // VK Video API (предполагаем публичный endpoint)
                URI uri = new URI("https://api.vkvideo.ru/v1/channels/" + URLEncoder.encode(channel.trim(), StandardCharsets.UTF_8) + "/live");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                StringBuilder json = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    int c; while ((c = r.read()) != -1) json.append((char) c);
                }

                String data2 = json.toString();
                boolean live = data2.contains("\"status\":\"live\"") || data2.contains("\"isLive\":true");
                String title = "";
                String url = "https://live.vkvideo.ru/" + channel.trim();
                if (live) {
                    int tIdx = data2.indexOf("\"title\":\"");
                    if (tIdx != -1) {
                        int tEnd = data2.indexOf("\"", tIdx + 9);
                        title = data2.substring(tIdx + 9, tEnd);
                    }
                }
                result.add(new StreamEvent("VKVideo", channel.trim(), title, url, live));
            } catch (Exception ignore) {}
        }

        return result;
    }

    private static int resolveGroupId(String token, String screenName) {
        try {
            URI uri = new URI("https://api.vk.com/method/groups.getById?group_id="
                    + URLEncoder.encode(screenName, StandardCharsets.UTF_8)
                    + "&v=5.131&access_token=" + token);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            StringBuilder json = new StringBuilder();
            try (var r = new InputStreamReader(conn.getInputStream())) {
                int c; while ((c = r.read()) != -1) json.append((char) c);
            }
            // Парсим id из ответа: "gid":123456 или "id":-123456
            String data = json.toString();
            int idIdx = data.indexOf("\"id\":");
            if (idIdx == -1) return 0;
            int idStart = idIdx + 5;
            int idEnd = data.indexOf(",", idStart);
            if (idEnd == -1) idEnd = data.indexOf("}", idStart);
            return Integer.parseInt(data.substring(idStart, idEnd).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
