package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    private volatile String cachedPhotoAttachment;
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

        for (String line : plugin.getConfig().getStringList("announcement.game")) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    format(line, e, linksVk, linksGame));
            Bukkit.broadcastMessage(msg);
        }

        if (plugin.getConfig().getBoolean("announcement.vk-enabled", true)) {
            for (String line : plugin.getConfig().getStringList("announcement.vk")) {
                VKChatBridge.sendToMainChat(format(line, e, linksVk, linksGame));
            }
        }

        postToVkWall(e, linksVk);
    }

    private String getPhotoAttachment() {
        String manual = plugin.getConfig().getString("streams.vk.wall-post.photo-attachment", "");
        if (!manual.isEmpty()) return manual;

        if (cachedPhotoAttachment != null && !cachedPhotoAttachment.isEmpty())
            return cachedPhotoAttachment;

        String photoUrl = plugin.getConfig().getString("streams.vk.wall-post.photo-url", "");
        if (photoUrl.isEmpty()) return "";

        String token = plugin.getConfig().getString("streams.vk.token", "");
        String groupId = plugin.getConfig().getString("streams.vk.group-id", "");
        if (token.isEmpty() || groupId.isEmpty()) return "";

        try {
            // 1. Скачиваем картинку
            byte[] imageBytes = downloadImage(photoUrl);
            if (imageBytes == null || imageBytes.length == 0) return "";

            // 2. Получаем upload URL
            URI uploadServerUri = new URI("https://api.vk.com/method/photos.getWallUploadServer"
                    + "?group_id=" + groupId
                    + "&v=5.131&access_token=" + token);
            String uploadUrl = getJsonField(requestGet(uploadServerUri), "upload_url");
            if (uploadUrl.isEmpty()) return "";

            // 3. Загружаем фото на upload сервер
            String uploadResponse = uploadPhoto(uploadUrl, imageBytes);
            if (uploadResponse.isEmpty()) return "";

            String server = getJsonField(uploadResponse, "server");
            String photo = getJsonField(uploadResponse, "photo");
            String hash = getJsonField(uploadResponse, "hash");
            if (server.isEmpty() || photo.isEmpty() || hash.isEmpty()) return "";

            // 4. Сохраняем фото в альбоме
            URI saveUri = new URI("https://api.vk.com/method/photos.saveWallPhoto"
                    + "?group_id=" + groupId
                    + "&server=" + server
                    + "&photo=" + URLEncoder.encode(photo, StandardCharsets.UTF_8)
                    + "&hash=" + URLEncoder.encode(hash, StandardCharsets.UTF_8)
                    + "&v=5.131&access_token=" + token);
            String saveResponse = requestGet(saveUri);

            String ownerId = getJsonField(saveResponse, "\"owner_id\":");
            String photoId = getJsonField(saveResponse, "\"id\":");
            if (!ownerId.isEmpty() && !photoId.isEmpty()) {
                cachedPhotoAttachment = "photo" + ownerId + "_" + photoId;
                plugin.getLogger().info("Фото загружено в ВК: " + cachedPhotoAttachment);
                return cachedPhotoAttachment;
            }

            // fallback: парсим как photo_xxx из ответа "aid"
            String aid = getJsonField(saveResponse, "aid");
            String pid = getJsonField(saveResponse, "pid");
            if (!aid.isEmpty() && !pid.isEmpty()) {
                cachedPhotoAttachment = "photo" + aid + "_" + pid;
                return cachedPhotoAttachment;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки фото в ВК: " + e.getMessage());
        }
        return "";
    }

    private byte[] downloadImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "VKChatStreams/1.0");
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String uploadPhoto(String uploadUrl, byte[] imageBytes) {
        try {
            String boundary = "----VKChatStreams" + System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URI(uploadUrl).toURL().openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"banner.jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
                os.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                os.write(imageBytes);
                os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            StringBuilder sb = new StringBuilder();
            try (InputStreamReader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                int c; while ((c = r.read()) != -1) sb.append((char) c);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String requestGet(URI uri) {
        try {
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            StringBuilder sb = new StringBuilder();
            try (InputStreamReader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                int c; while ((c = r.read()) != -1) sb.append((char) c);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getJsonField(String json, String field) {
        int idx = json.indexOf(field);
        if (idx == -1) return "";
        int start = idx + field.length();
        while (start < json.length() && (json.charAt(start) == '"' || json.charAt(start) == ':' || json.charAt(start) == ' '))
            start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            if (end == -1) return "";
            return json.substring(start, end);
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '_'))
                end++;
            return json.substring(start, end);
        }
    }

    private void postToVkWall(StreamEvent e, String linksVk) {
        String token = plugin.getConfig().getString("streams.vk.token", "");
        String groupId = plugin.getConfig().getString("streams.vk.group-id", "");
        boolean wallEnabled = plugin.getConfig().getBoolean("streams.vk.wall-post.enabled", true);
        if (!wallEnabled || token.isEmpty() || groupId.isEmpty()) return;

        String template = plugin.getConfig().getString("streams.vk.post-template", "⚡ {channel} запустил стрим!\n{title}\n{url}\n{links}");
        String message = format(template, e, linksVk, "");

        try {
            String photoAtt = getPhotoAttachment();
            String attParam = photoAtt.isEmpty() ? "" : "&attachments=" + URLEncoder.encode(photoAtt, StandardCharsets.UTF_8);

            URI uri = new URI("https://api.vk.com/method/wall.post?owner_id=-" + groupId
                    + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
                    + "&v=5.131&access_token=" + token
                    + attParam);
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
    public void resetAnnounced() { announced.clear(); claimedRewards.clear(); cachedPhotoAttachment = null; }
    public void reload() { loadManualLinks(); cachedPhotoAttachment = null; }
}
