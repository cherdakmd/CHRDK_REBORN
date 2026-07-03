package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StreamChecker {
    private final VKChatStreamsPlugin plugin;
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
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

        // Чистим старые ключи (снимаем флаг, если стрим закончился)
        for (String key : new HashSet<>(announced)) {
            if (events.stream().noneMatch(e -> key.equals(e.getPlatform() + ":" + e.getChannel()))) {
                announced.remove(key);
            }
        }

        for (StreamEvent e : events) {
            String key = e.getPlatform() + ":" + e.getChannel();
            if (!e.isLive() || announced.contains(key)) continue;

            announced.add(key);
            Bukkit.getScheduler().runTask(plugin, () -> announce(e));
        }
    }

    private void announce(StreamEvent e) {
        // Игра
        for (String line : plugin.getConfig().getStringList("announcement.game")) {
            String msg = ChatColor.translateAlternateColorCodes('&', format(line, e));
            Bukkit.broadcastMessage(msg);
        }
        // ВК
        if (plugin.getConfig().getBoolean("announcement.vk-enabled", true)) {
            for (String line : plugin.getConfig().getStringList("announcement.vk")) {
                ru.example.vkchat.util.VKChatBridge.sendToMainChat(format(line, e));
            }
        }
    }

    private String format(String template, StreamEvent e) {
        return template.replace("{platform}", e.getPlatform())
                .replace("{channel}", e.getChannel())
                .replace("{title}", e.getTitle() != null ? e.getTitle() : "")
                .replace("{url}", e.getUrl() != null ? e.getUrl() : "");
    }

    public Set<String> getAnnounced() { return announced; }
    public void resetAnnounced() { announced.clear(); }
}
