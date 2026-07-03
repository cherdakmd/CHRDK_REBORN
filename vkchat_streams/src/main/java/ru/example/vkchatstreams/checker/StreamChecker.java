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

        currentVkToken = plugin.getConfig().getString("streams.vk.token", "");

        Bukkit.broadcastMessage(ChatColor.GOLD + "🎯 Подпишись на группу ВК → /stream reward (награда!)");

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
        if (currentVkToken.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Система наград временно недоступна.");
            return false;
        }

        // Проверяем через VK API: подписан ли игрок на группу
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId == -1) {
            p.sendMessage(ChatColor.RED + "Сначала привяжи ВК (/vklink)!");
            return false;
        }

        try {
            String groupId = plugin.getConfig().getString("streams.vk.group-id", "0");
            URI uri = new URI("https://api.vk.com/method/groups.isMember?group_id=" + groupId
                    + "&user_id=" + vkId
                    + "&v=5.131&access_token=" + currentVkToken);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            StringBuilder json = new StringBuilder();
            try (var r = new InputStreamReader(conn.getInputStream())) {
                int c; while ((c = r.read()) != -1) json.append((char) c);
            }
            String resp = json.toString();

            if (!resp.contains("\"member\":1")) {
                p.sendMessage(ChatColor.RED + "❌ Ты не подписан на группу! Вступи в группу ВК и попробуй снова.");
                return false;
            }
        } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "Ошибка проверки подписки ВК. Попробуй позже.");
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
    public void resetAnnounced() { announced.clear(); claimedRewards.clear(); }
}

