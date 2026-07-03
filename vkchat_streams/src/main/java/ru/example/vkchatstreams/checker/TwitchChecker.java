package ru.example.vkchatstreams.checker;

import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public class TwitchChecker {
    public static Set<StreamEvent> check(VKChatStreamsPlugin plugin) {
        Set<StreamEvent> result = new HashSet<>();
        String clientId = plugin.getConfig().getString("streams.twitch.client-id", "");
        String oauth = plugin.getConfig().getString("streams.twitch.oauth-token", "");
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

                boolean live = json.toString().contains("\"type\":\"live\"");
                String title = "";
                String url = "https://twitch.tv/" + channel.trim().toLowerCase();
                if (live) {
                    int tIdx = json.indexOf("\"title\":\"");
                    if (tIdx != -1) {
                        int tEnd = json.indexOf("\"", tIdx + 9);
                        title = json.substring(tIdx + 9, tEnd);
                    }
                }
                result.add(new StreamEvent("Twitch", channel.trim(), title, url, live));
            } catch (Exception ignore) {}
        }
        return result;
    }
}
