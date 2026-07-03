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

public class YouTubeChecker {
    public static Set<StreamEvent> check(VKChatStreamsPlugin plugin) {
        Set<StreamEvent> result = new HashSet<>();
        String apiKey = plugin.getConfig().getString("streams.youtube.api-key", "");
        if (apiKey.isEmpty()) return result;

        for (String channelId : plugin.getConfig().getStringList("streams.youtube.channels")) {
            try {
                // YouTube Data API v3: search.list?part=snippet&channelId=UC...&eventType=live&type=video&key=API_KEY
                URI uri = new URI("https://www.googleapis.com/youtube/v3/search?part=snippet"
                        + "&channelId=" + URLEncoder.encode(channelId.trim(), StandardCharsets.UTF_8)
                        + "&eventType=live&type=video&key=" + apiKey);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                StringBuilder json = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    int c; while ((c = r.read()) != -1) json.append((char) c);
                }

                String data = json.toString();
                boolean live = !data.contains("\"items\":[]");
                String title = "";
                String videoId = "";
                String channelName = channelId.trim();

                if (live) {
                    int tIdx = data.indexOf("\"title\":\"");
                    if (tIdx != -1) {
                        int tEnd = data.indexOf("\"", tIdx + 9);
                        title = data.substring(tIdx + 9, tEnd).replace("\\\"", "\"");
                    }
                    int vIdx = data.indexOf("\"videoId\":\"");
                    if (vIdx != -1) {
                        int vEnd = data.indexOf("\"", vIdx + 11);
                        videoId = data.substring(vIdx + 11, vEnd);
                    }
                    int cIdx = data.indexOf("\"channelTitle\":\"");
                    if (cIdx != -1) {
                        int cEnd = data.indexOf("\"", cIdx + 16);
                        channelName = data.substring(cIdx + 16, cEnd).replace("\\\"", "\"");
                    }
                }

                String url = videoId.isEmpty() ? "https://youtube.com/channel/" + channelId.trim()
                        : "https://youtube.com/watch?v=" + videoId;
                result.add(new StreamEvent("YouTube", channelName, title, url, live));
            } catch (Exception ignore) {}
        }
        return result;
    }
}
