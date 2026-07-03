package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreamChecker {
    private final VKChatStreamsPlugin plugin;
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<UUID>> claimedRewards = new ConcurrentHashMap<>();
    private final Map<String, String[]> manualLinks = new LinkedHashMap<>();
    private int taskId = -1;

    public StreamChecker(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
        loadManualLinks();
    }

    private void loadManualLinks() {
        manualLinks.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("streams.manual");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String vk = sec.getString(key + ".vk", "");
            String yt = sec.getString(key + ".youtube", "");
            String tw = sec.getString(key + ".twitch", "");
            manualLinks.put(key.toLowerCase(), new String[]{vk, yt, tw});
        }
    }

    public void start() {
        int interval = plugin.getConfig().getInt("check-interval-minutes", 5) * 1200;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAll, 200L, Math.max(200, interval)).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    }

    public void checkAll() {
        Set<StreamEvent> events = new HashSet<>();

        if (plugin.getConfig().getBoolean("streams.twitch.enabled", true))
            events.addAll(TwitchChecker.check(plugin));
        if (plugin.getConfig().getBoolean("streams.youtube.enabled", true))
            events.addAll(YouTubeChecker.check(plugin));
        if (plugin.getConfig().getBoolean("streams.vk.enabled", true))
            events.addAll(VKChecker.check(plugin));

        for (String key : new HashSet<>(announced)) {
            if (events.stream().noneMatch(e -> key.equals(e.getPlatform() + ":" + e.getChannel()))) {
                announced.remove(key);
                claimedRewards.remove(key);
            }
        }

        for (StreamEvent e : events) {
            String key = e.getPlatform() + ":" + e.getChannel();
            if (!e.isLive() || announced.contains(key)) continue;

            announced.add(key);
            claimedRewards.putIfAbsent(key, ConcurrentHashMap.newKeySet());
            StreamEvent enriched = enrichWithManualLinks(e);
            Bukkit.getScheduler().runTask(plugin, () -> announce(enriched));
        }
    }

    public void forceAnnounce(StreamEvent e) {
        String key = e.getPlatform() + ":" + e.getChannel();
        if (announced.contains(key)) return;
        announced.add(key);
        claimedRewards.putIfAbsent(key, ConcurrentHashMap.newKeySet());
        StreamEvent enriched = enrichWithManualLinks(e);
        Bukkit.getScheduler().runTask(plugin, () -> announce(enriched));
    }

    private StreamEvent enrichWithManualLinks(StreamEvent e) {
        String vkUrl = "", ytUrl = "", twUrl = "";

        for (Map.Entry<String, String[]> entry : manualLinks.entrySet()) {
            String name = entry.getKey();
            String[] links = entry.getValue();
            // Match by channel name (case-insensitive contains)
            if (e.getChannel().toLowerCase().contains(name)) {
                if (!links[0].isEmpty()) vkUrl = links[0];
                if (!links[1].isEmpty()) ytUrl = links[1];
                if (!links[2].isEmpty()) twUrl = links[2];
                break;
            }
        }

        if (!e.getVkUrl().isEmpty()) vkUrl = e.getVkUrl();
        if (!e.getYoutubeUrl().isEmpty()) ytUrl = e.getYoutubeUrl();
        if (!e.getTwitchUrl().isEmpty()) twUrl = e.getTwitchUrl();

        return new StreamEvent(e.getPlatform(), e.getChannel(), e.getTitle(), e.getUrl(), e.isLive(), vkUrl, ytUrl, twUrl);
    }

    private void announce(StreamEvent e) {
        String linksVk = buildVkLinks(e);
        String linksGame = buildGameLinks(e);

        // In-game announcement
        for (String line : plugin.getConfig().getStringList("announcement.game")) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    format(line, e, linksVk, linksGame));
            Bukkit.broadcastMessage(msg);
        }

        // VK chat announcement
        if (plugin.getConfig().getBoolean("announcement.vk-enabled", true)) {
            for (String line : plugin.getConfig().getStringList("announcement.vk")) {
                VKChatBridge.sendToMainChat(format(line, e, linksVk, linksGame));
            }
        }

        // VK wall post
        postToVkWall(e, linksVk);
    }

    private void postToVkWall(StreamEvent e, String linksVk) {
        String token = plugin.getConfig().getString("streams.vk.token", "");
        String groupId = plugin.getConfig().getString("streams.vk.group-id", "");
        boolean wallEnabled = plugin.getConfig().getBoolean("streams.vk.wall-post.enabled", true);
        if (!wallEnabled || token.isEmpty() || groupId.isEmpty()) return;

        String template = plugin.getConfig().getString("streams.vk.post-template", "⚡ {channel} запустил стрим!\n{title}\n{url}\n{links}");
        String message = format(template, e, linksVk, "");

        try {
            String photoAttachment = plugin.getConfig().getString("streams.vk.wall-post.photo-attachment", "");
            String attachments = photoAttachment.isEmpty() ? "" : "&attachments=" + URLEncoder.encode(photoAttachment, StandardCharsets.UTF_8);

            URI uri = new URI("https://api.vk.com/method/wall.post?owner_id=-" + groupId
                    + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
                    + "&v=5.131&access_token=" + token
                    + attachments);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (var is = conn.getInputStream()) {
                byte[] buf = new byte[256];
                while (is.read(buf) != -1) { /* drain */ }
            }
        } catch (Exception ignored) {}
    }

    private String buildVkLinks(StreamEvent e) {
        StringBuilder sb = new StringBuilder();
        if (!e.getVkUrl().isEmpty())
            sb.append("\n").append("🔵 VK: ").append(e.getVkUrl());
        if (!e.getYoutubeUrl().isEmpty())
            sb.append("\n").append("🔴 YouTube: ").append(e.getYoutubeUrl());
        if (!e.getTwitchUrl().isEmpty())
            sb.append("\n").append("🟣 Twitch: ").append(e.getTwitchUrl());
        return sb.toString();
    }

    private String buildGameLinks(StreamEvent e) {
        StringBuilder sb = new StringBuilder();
        if (!e.getVkUrl().isEmpty())
            sb.append("&7  &9VK: &b&n").append(e.getVkUrl());
        if (!e.getYoutubeUrl().isEmpty())
            sb.append("\n&7  &cYouTube: &b&n").append(e.getYoutubeUrl());
        if (!e.getTwitchUrl().isEmpty())
            sb.append("\n&7  &5Twitch: &b&n").append(e.getTwitchUrl());
        return sb.toString();
    }

    public boolean claimReward(Player p) {
        if (announced.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
            return false;
        }

        for (String key : announced) {
            Set<UUID> claimers = claimedRewards.get(key);
            if (claimers != null && claimers.contains(p.getUniqueId())) {
                p.sendMessage(ChatColor.RED + "Ты уже получил награду за этот стрим!");
                return false;
            }
        }

        String firstKey = announced.iterator().next();
        claimedRewards.get(firstKey).add(p.getUniqueId());

        int rep = plugin.getConfig().getInt("rewards.reputation", 150);
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId != -1) VKChatBridge.addPoints(vkId, rep);

        for (String cmd : plugin.getConfig().getStringList("rewards.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        p.sendMessage(ChatColor.GREEN + "✓ Спасибо за подписку на канал! +" + rep + " репутации ВК.");
        return true;
    }

    private String format(String template, StreamEvent e, String linksVk, String linksGame) {
        return template.replace("{platform}", e.getPlatform())
                .replace("{platform_emoji}", platformEmoji(e.getPlatform()))
                .replace("{channel}", e.getChannel())
                .replace("{title}", e.getTitle() != null ? e.getTitle() : "")
                .replace("{url}", e.getUrl() != null ? e.getUrl() : "")
                .replace("{links}", buildPlainLinks(e))
                .replace("{links_vk}", linksVk)
                .replace("{links_game}", linksGame)
                .replace("{vk_url}", e.getVkUrl())
                .replace("{youtube_url}", e.getYoutubeUrl())
                .replace("{twitch_url}", e.getTwitchUrl());
    }

    private String buildPlainLinks(StreamEvent e) {
        StringBuilder sb = new StringBuilder();
        if (!e.getVkUrl().isEmpty()) sb.append("🔵 VK: ").append(e.getVkUrl()).append("\n");
        if (!e.getYoutubeUrl().isEmpty()) sb.append("🔴 YouTube: ").append(e.getYoutubeUrl()).append("\n");
        if (!e.getTwitchUrl().isEmpty()) sb.append("🟣 Twitch: ").append(e.getTwitchUrl());
        return sb.toString().trim();
    }

    private String platformEmoji(String platform) {
        return switch (platform.toLowerCase()) {
            case "twitch" -> "\uD83D\uDFE3";
            case "youtube" -> "\uD83D\uDD34";
            case "vk", "vkvideo" -> "\uD83D\uDD35";
            default -> "\u26A1";
        };
    }

    public Set<String> getAnnounced() { return announced; }
    public void resetAnnounced() { announced.clear(); claimedRewards.clear(); }
    public void reload() { loadManualLinks(); }
}
