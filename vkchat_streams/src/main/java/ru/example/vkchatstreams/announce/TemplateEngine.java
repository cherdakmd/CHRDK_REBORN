package ru.example.vkchatstreams.announce;

import ru.example.vkchatstreams.StreamEvent;

import java.util.Map;

public final class TemplateEngine {

    private TemplateEngine() {}

    public static String format(String template, StreamEvent e, String ytUrl, String vkUrl, int claimed, Map<String, String> extra) {
        String title = (e.getTitle() == null || e.getTitle().isEmpty()) ? "Без названия" : e.getTitle();
        String game = e.getGame() != null ? e.getGame() : "";
        String url = e.getUrl() != null ? e.getUrl() : "";
        String viewers = e.getViewerCount() > 0 ? String.valueOf(e.getViewerCount()) : "0";

        String links = "📺 Twitch: " + url;
        if (!ytUrl.isEmpty()) links += "\n🔴 YouTube: " + ytUrl;
        if (!vkUrl.isEmpty()) links += "\n🔵 VK: " + vkUrl;

        String result = template
                .replace("{channel}", e.getChannel())
                .replace("{title}", title)
                .replace("{game}", game)
                .replace("{viewers}", viewers)
                .replace("{url}", url)
                .replace("{youtube_url}", ytUrl)
                .replace("{vk_url}", vkUrl)
                .replace("{links}", links)
                .replace("{uptime}", e.getUptime())
                .replace("{claimed}", String.valueOf(claimed));

        if (extra != null) {
            for (var entry : extra.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    public static String formatOffline(String template, String channel, String title, int claimed, long uptimeSeconds, int viewers) {
        return template
                .replace("{channel}", channel)
                .replace("{title}", title != null ? title : "")
                .replace("{claimed}", String.valueOf(claimed))
                .replace("{uptime}", StreamEvent.formatUptime(uptimeSeconds))
                .replace("{viewers}", String.valueOf(viewers));
    }
}
