package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreamChecker {
    private final VKChatStreamsPlugin plugin;
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private final Map<String, StreamEvent> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> claimedRewards = new ConcurrentHashMap<>();
    private final Map<String, String[]> manualLinks = new LinkedHashMap<>();
    private final Map<String, Long> lastAnnounceTime = new ConcurrentHashMap<>();
    private volatile String cachedPhotoAttachment;
    private volatile boolean photoUploading;
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

    public void restart() {
        stop();
        start();
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

        // Убираем стримы которые закончились
        for (String key : new HashSet<>(announced)) {
            if (events.stream().noneMatch(e -> key.equals(e.getPlatform() + ":" + e.getChannel()))) {
                StreamEvent old = activeStreams.remove(key);
                announced.remove(key);
                claimedRewards.remove(key);
                if (old != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> notifyOffline(old));
                }
            }
        }

        // Новые стримы
        int cooldownSec = plugin.getConfig().getInt("announcement.cooldown-seconds", 300);
        for (StreamEvent e : events) {
            String key = e.getPlatform() + ":" + e.getChannel();
            if (!e.isLive() || announced.contains(key)) continue;

            long now = System.currentTimeMillis();
            Long last = lastAnnounceTime.get(key);
            if (last != null && (now - last) < cooldownSec * 1000L) continue;

            lastAnnounceTime.put(key, now);
            announced.add(key);
            claimedRewards.putIfAbsent(key, ConcurrentHashMap.newKeySet());
            StreamEvent enriched = enrichWithManualLinks(e);
            activeStreams.put(key, enriched);
            Bukkit.getScheduler().runTask(plugin, () -> announce(enriched));
        }
    }

    public void forceAnnounce(StreamEvent e) {
        loadManualLinks();
        String key = e.getPlatform() + ":" + e.getChannel();
        if (announced.contains(key)) return;
        announced.add(key);
        claimedRewards.putIfAbsent(key, ConcurrentHashMap.newKeySet());
        StreamEvent enriched = enrichWithManualLinks(e);
        activeStreams.put(key, enriched);
        Bukkit.getScheduler().runTask(plugin, () -> announce(enriched));
    }

    public List<StreamEvent> getLiveStreams() {
        return new ArrayList<>(activeStreams.values());
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

        return new StreamEvent(e.getPlatform(), e.getChannel(), e.getTitle(), e.getUrl(), e.isLive(),
                vkUrl, ytUrl, twUrl, e.getViewerCount(), e.getGame());
    }

    private void announce(StreamEvent e) {
        String linksVk = buildVkLinks(e);
        String linksGame = buildGameLinks(e);

        // In-game
        for (String line : plugin.getConfig().getStringList("announcement.game")) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    format(line, e, linksVk, linksGame));
            Bukkit.broadcastMessage(msg);
        }

        // Звук всем игрокам
        String soundName = plugin.getConfig().getString("announcement.sound", "ENTITY_PLAYER_LEVELUP");
        try {
            Sound sound = Sound.valueOf(soundName);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        } catch (IllegalArgumentException ignored) {}

        // VK чат
        if (plugin.getConfig().getBoolean("announcement.vk-enabled", true)) {
            for (String line : plugin.getConfig().getStringList("announcement.vk")) {
                VKChatBridge.sendToMainChat(format(line, e, linksVk, linksGame));
            }
        }

        // VK ЛС админам
        for (int vkId : plugin.getConfig().getIntegerList("streams.admin-vk-ids")) {
            String dmTemplate = plugin.getConfig().getString("announcement.admin-dm",
                    "⚡ {channel} запустил стрим на {platform}!\n{title}\n{url}");
            VKChatBridge.sendMessage(vkId, format(dmTemplate, e, linksVk, linksGame));
        }

        // Пост на стену
        postToVkWall(e, linksVk);
    }

    private void notifyOffline(StreamEvent e) {
        if (!plugin.getConfig().getBoolean("announcement.vk-enabled", true)) return;
        String template = plugin.getConfig().getString("announcement.offline",
                "⭕ {channel} завершил стрим на {platform}.");
        VKChatBridge.sendToMainChat(template
                .replace("{platform}", e.getPlatform())
                .replace("{channel}", e.getChannel())
                .replace("{title}", e.getTitle() != null ? e.getTitle() : ""));
    }

    // ---- Photo upload (async, cached) ----

    private String getPhotoAttachment() {
        String manual = plugin.getConfig().getString("streams.vk.wall-post.photo-attachment", "");
        if (!manual.isEmpty()) return manual;
        if (cachedPhotoAttachment != null && !cachedPhotoAttachment.isEmpty())
            return cachedPhotoAttachment;
        return "";
    }

    private void ensurePhotoAttachmentAsync(Runnable onReady) {
        if (!getPhotoAttachment().isEmpty()) { onReady.run(); return; }

        String token = plugin.getConfig().getString("streams.vk.token", "");
        String groupId = plugin.getConfig().getString("streams.vk.group-id", "");
        if (token.isEmpty() || groupId.isEmpty()) { onReady.run(); return; }

        synchronized (this) {
            if (photoUploading) { onReady.run(); return; }
            if (cachedPhotoAttachment != null && !cachedPhotoAttachment.isEmpty()) { onReady.run(); return; }
            photoUploading = true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                byte[] imageBytes = loadImageBytes();
                if (imageBytes != null && imageBytes.length > 0) {
                    String result = uploadToVkAlbum(token, groupId, imageBytes);
                    if (result != null && !result.isEmpty()) cachedPhotoAttachment = result;
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка загрузки фото в ВК: " + ex.getMessage());
            } finally {
                photoUploading = false;
                Bukkit.getScheduler().runTask(plugin, onReady);
            }
        });
    }

    private byte[] loadImageBytes() {
        String photoFile = plugin.getConfig().getString("streams.vk.wall-post.photo-file", "banner.jpg");
        java.io.File file = new java.io.File(plugin.getDataFolder(), photoFile);
        if (file.exists()) {
            try {
                plugin.getLogger().info("Читаю картинку из файла: " + file.getName());
                return java.nio.file.Files.readAllBytes(file.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка чтения " + file.getName() + ": " + e.getMessage());
            }
        }
        String photoUrl = plugin.getConfig().getString("streams.vk.wall-post.photo-url", "");
        if (!photoUrl.isEmpty()) {
            plugin.getLogger().info("Скачиваю картинку: " + photoUrl);
            return downloadImage(photoUrl);
        }
        return null;
    }

    private String uploadToVkAlbum(String token, String groupId, byte[] imageBytes) {
        try {
            URI uploadServerUri = new URI("https://api.vk.com/method/photos.getWallUploadServer"
                    + "?group_id=" + groupId + "&v=5.131&access_token=" + token);
            String resp = requestGet(uploadServerUri);
            String uploadUrl = getJsonField(resp, "upload_url");
            if (uploadUrl.isEmpty()) { plugin.getLogger().warning("VK: не получен upload_url"); return null; }

            String uploadResponse = uploadPhoto(uploadUrl, imageBytes);
            if (uploadResponse.isEmpty()) { plugin.getLogger().warning("VK: загрузка фото не удалась"); return null; }

            String server = getJsonField(uploadResponse, "server");
            String photo = getJsonField(uploadResponse, "photo");
            String hash = getJsonField(uploadResponse, "hash");
            if (server.isEmpty() || photo.isEmpty() || hash.isEmpty()) {
                plugin.getLogger().warning("VK: неполный ответ upload сервера"); return null;
            }

            URI saveUri = new URI("https://api.vk.com/method/photos.saveWallPhoto"
                    + "?group_id=" + groupId
                    + "&server=" + server
                    + "&photo=" + URLEncoder.encode(photo, StandardCharsets.UTF_8)
                    + "&hash=" + URLEncoder.encode(hash, StandardCharsets.UTF_8)
                    + "&v=5.131&access_token=" + token);
            String saveResponse = requestGet(saveUri);

            String ownerId = getJsonField(saveResponse, "owner_id");
            String photoId = getJsonField(saveResponse, "id");
            if (!ownerId.isEmpty() && !photoId.isEmpty()) {
                String att = "photo" + ownerId + "_" + photoId;
                plugin.getLogger().info("Фото загружено в ВК: " + att);
                return att;
            }
            plugin.getLogger().warning("VK: не распарсить saveWallPhoto");
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки фото в ВК: " + e.getMessage());
        }
        return null;
    }

    private byte[] downloadImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "VKChatStreams/1.0");
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка скачивания " + url + ": " + e.getMessage());
            return null;
        }
    }

    private String uploadPhoto(String uploadUrl, byte[] imageBytes) {
        try {
            String boundary = "----VKChatStreams" + System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URI(uploadUrl).toURL().openConnection();
            conn.setDoOutput(true); conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(15000); conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"banner.jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
                os.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                os.write(imageBytes);
                os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder sb = new StringBuilder();
            try (InputStreamReader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                int c; while ((c = r.read()) != -1) sb.append((char) c);
            }
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки фото: " + e.getMessage());
            return "";
        }
    }

    private String requestGet(URI uri) {
        try {
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            if (code != 200) {
                StringBuilder err = new StringBuilder();
                try (InputStreamReader r = new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)) {
                    int c; while ((c = r.read()) != -1) err.append((char) c);
                } catch (Exception ignored) {}
                plugin.getLogger().warning("VK API ответил " + code + ": " + err);
                return "";
            }
            StringBuilder sb = new StringBuilder();
            try (InputStreamReader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                int c; while ((c = r.read()) != -1) sb.append((char) c);
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String getJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        int start = idx + search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            start++;
            int end = start;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && (end == 0 || json.charAt(end - 1) != '\\')) break;
                end++;
            }
            if (end >= json.length()) return "";
            return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            return json.substring(start, end);
        }
    }

    private void postToVkWall(StreamEvent e, String linksVk) {
        String token = plugin.getConfig().getString("streams.vk.token", "");
        String groupId = plugin.getConfig().getString("streams.vk.group-id", "");
        if (!plugin.getConfig().getBoolean("streams.vk.wall-post.enabled", true)) return;
        if (token.isEmpty() || groupId.isEmpty()) return;

        String template = plugin.getConfig().getString("streams.vk.post-template",
                "⚡ {channel} запустил стрим!\n{title}\n{url}\n{links}");
        String message = escapeNewlines(format(template, e, linksVk, ""));
        String finalMsg = message;

        Runnable doPost = () -> {
            try {
                String att = getPhotoAttachment();
                String attParam = att.isEmpty() ? "" : "&attachments=" + URLEncoder.encode(att, StandardCharsets.UTF_8);

                URI uri = new URI("https://api.vk.com/method/wall.post?owner_id=-" + groupId
                        + "&message=" + URLEncoder.encode(finalMsg, StandardCharsets.UTF_8)
                        + "&v=5.131&access_token=" + token + attParam);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(5000); conn.setReadTimeout(5000);

                StringBuilder resp = new StringBuilder();
                try (InputStreamReader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    int c; while ((c = r.read()) != -1) resp.append((char) c);
                }
                String body = resp.toString();
                if (body.contains("\"post_id\":")) {
                    plugin.getLogger().info("Пост ВК опубликован: " + e.getChannel());
                } else {
                    plugin.getLogger().warning("VK wall.post ошибка: " + body);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("VK wall.post ошибка: " + ex.getMessage());
            }
        };

        if (!getPhotoAttachment().isEmpty()) {
            doPost.run();
        } else {
            ensurePhotoAttachmentAsync(doPost);
        }
    }

    private String escapeNewlines(String text) {
        return text.replace("\\n", "\n");
    }

    // ---- Link builders ----

    private String buildVkLinks(StreamEvent e) {
        StringBuilder sb = new StringBuilder();
        if (!e.getVkUrl().isEmpty()) sb.append("\n🔵 VK: ").append(e.getVkUrl());
        if (!e.getYoutubeUrl().isEmpty()) sb.append("\n🔴 YouTube: ").append(e.getYoutubeUrl());
        if (!e.getTwitchUrl().isEmpty()) sb.append("\n🟣 Twitch: ").append(e.getTwitchUrl());
        return sb.toString();
    }

    private String buildGameLinks(StreamEvent e) {
        StringBuilder sb = new StringBuilder();
        if (!e.getVkUrl().isEmpty()) sb.append("&7  &9VK: &b&n").append(e.getVkUrl());
        if (!e.getYoutubeUrl().isEmpty()) sb.append("\n&7  &cYouTube: &b&n").append(e.getYoutubeUrl());
        if (!e.getTwitchUrl().isEmpty()) sb.append("\n&7  &5Twitch: &b&n").append(e.getTwitchUrl());
        return sb.toString();
    }

    // ---- Reward ----

    public boolean claimReward(Player p) {
        if (announced.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
            return false;
        }

        // Ищем первый стрим за который игрок ещё не получал награду
        String keyToClaim = null;
        for (String key : announced) {
            Set<UUID> claimers = claimedRewards.get(key);
            if (claimers == null || !claimers.contains(p.getUniqueId())) {
                keyToClaim = key;
                break;
            }
        }
        if (keyToClaim == null) {
            p.sendMessage(ChatColor.RED + "Ты уже получил награду за все активные стримы!");
            return false;
        }

        claimedRewards.get(keyToClaim).add(p.getUniqueId());
        StreamEvent stream = activeStreams.get(keyToClaim);
        String platform = stream != null ? stream.getPlatform().toLowerCase() : "default";

        double multiplier = plugin.getConfig().getDouble("rewards.multipliers." + platform, 1.0);
        int rep = (int) (plugin.getConfig().getInt("rewards.reputation", 150) * multiplier);
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId != -1) VKChatBridge.addPoints(vkId, rep);

        for (String cmd : plugin.getConfig().getStringList("rewards.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        String streamInfo = stream != null ? " (" + stream.getPlatform() + ": " + stream.getChannel() + ")" : "";
        p.sendMessage(ChatColor.GREEN + "✓ Награда получена" + streamInfo + "! +" + rep + " репутации ВК.");
        return true;
    }

    // ---- Template formatting ----

    private String format(String template, StreamEvent e, String linksVk, String linksGame) {
        String title = e.getTitle();
        if (title == null || title.isEmpty()) title = "Без названия";

        return template.replace("{platform}", e.getPlatform())
                .replace("{platform_emoji}", platformEmoji(e.getPlatform()))
                .replace("{channel}", e.getChannel())
                .replace("{title}", title)
                .replace("{game}", e.getGame() != null ? e.getGame() : "")
                .replace("{viewers}", e.getViewerCount() > 0 ? String.valueOf(e.getViewerCount()) : "")
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
    public void resetAnnounced() { announced.clear(); activeStreams.clear(); claimedRewards.clear(); cachedPhotoAttachment = null; lastAnnounceTime.clear(); }
    public void reload() { loadManualLinks(); cachedPhotoAttachment = null; lastAnnounceTime.clear(); TwitchChecker.resetToken(); }
}
