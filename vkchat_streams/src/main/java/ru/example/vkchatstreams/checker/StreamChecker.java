package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreamChecker {
    private final VKChatStreamsPlugin plugin;
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<UUID>> claimedRewards = new ConcurrentHashMap<>();
    private volatile int currentPostId = 0;
    private volatile String currentVkToken = "";
    private int taskId = -1;

    public StreamChecker(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
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
            Bukkit.getScheduler().runTask(plugin, () -> announce(e));
        }
    }

    private void announce(StreamEvent e) {
        for (String line : plugin.getConfig().getStringList("announcement.game")) {
            String msg = ChatColor.translateAlternateColorCodes('&', format(line, e));
            Bukkit.broadcastMessage(msg);
        }

        // Публикуем анонс на стену группы ВК
        currentVkToken = plugin.getConfig().getString("streams.vk.token", "");
        String groupId = plugin.getConfig().getString("streams.vk.group-id", "");
        if (!currentVkToken.isEmpty() && !groupId.isEmpty()) {
            try {
                String text = "⚡ СТРИМ ⚡\n" + e.getPlatform() + " " + e.getChannel() + " — " + e.getTitle() + "\n" + e.getUrl();
                URI uri = new URI("https://api.vk.com/method/wall.post?owner_id=-" + groupId
                        + "&message=" + java.net.URLEncoder.encode(text, "UTF-8")
                        + "&v=5.131&access_token=" + currentVkToken);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                StringBuilder json = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    int c; while ((c = r.read()) != -1) json.append((char) c);
                }
                String resp = json.toString();
                int pIdx = resp.indexOf("\"post_id\":");
                if (pIdx != -1) {
                    int pStart = pIdx + 10;
                    int pEnd = resp.indexOf(",", pStart);
                    if (pEnd == -1) pEnd = resp.indexOf("}", pStart);
                    currentPostId = Integer.parseInt(resp.substring(pStart, pEnd).trim());
                }
            } catch (Exception ignored) {}
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "🎯 Поставь лайк посту в группе ВК → /stream reward");

        if (plugin.getConfig().getBoolean("announcement.vk-enabled", true)) {
            for (String line : plugin.getConfig().getStringList("announcement.vk")) {
                VKChatBridge.sendToMainChat(format(line, e));
            }
        }
    }

    public boolean claimReward(Player p) {
        if (announced.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
            return false;
        }
        if (currentPostId == 0 || currentVkToken.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Пост ещё не создан. Попробуй через минуту.");
            return false;
        }

        // Проверяем через VK API: поставил ли игрок лайк посту
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Сначала привяжи ВК (/vklink)!");
            return false;
        }

        try {
            URI uri = new URI("https://api.vk.com/method/likes.isLiked?user_id=" + vkId
                    + "&type=post&owner_id=-" + plugin.getConfig().getString("streams.vk.group-id", "0")
                    + "&item_id=" + currentPostId
                    + "&v=5.131&access_token=" + currentVkToken);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            StringBuilder json = new StringBuilder();
            try (var r = new InputStreamReader(conn.getInputStream())) {
                int c; while ((c = r.read()) != -1) json.append((char) c);
            }
            String resp = json.toString();

            if (!resp.contains("\"liked\":1")) {
                p.sendMessage(ChatColor.RED + "Ты ещё не поставил лайк! Зайди в группу ВК и лайкни пост.");
                return false;
            }
        } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "Ошибка проверки ВК. Попробуй позже.");
            return false;
        }

        // Проверяем не получал ли уже награду
        for (String key : announced) {
            Set<UUID> claimers = claimedRewards.get(key);
            if (claimers != null && claimers.contains(p.getUniqueId())) {
                p.sendMessage(ChatColor.RED + "Ты уже получил награду за этот стрим!");
                return false;
            }
        }

        // Выдаём награду
        String firstKey = announced.iterator().next();
        claimedRewards.get(firstKey).add(p.getUniqueId());

        int rep = plugin.getConfig().getInt("rewards.reputation", 50);
        if (vkId != -1) VKChatBridge.addPoints(vkId, rep);

        for (String cmd : plugin.getConfig().getStringList("rewards.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        p.sendMessage(ChatColor.GREEN + "✓ Спасибо за просмотр стрима! +" + rep + " репутации.");
        return true;
    }

    private String format(String template, StreamEvent e) {
        return template.replace("{platform}", e.getPlatform())
                .replace("{channel}", e.getChannel())
                .replace("{title}", e.getTitle() != null ? e.getTitle() : "")
                .replace("{url}", e.getUrl() != null ? e.getUrl() : "");
    }

    public Set<String> getAnnounced() { return announced; }
    public int getCurrentPostId() { return currentPostId; }
    public void resetAnnounced() { announced.clear(); claimedRewards.clear(); currentPostId = 0; }
}

