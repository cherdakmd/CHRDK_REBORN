package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.util.ArrayList;
import java.util.HashSet;
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
    private final Map<String, Long> lastAnnounceTime = new ConcurrentHashMap<>();
    private int taskId = -1;

    public StreamChecker(VKChatStreamsPlugin plugin) { this.plugin = plugin; }

    public void start() {
        int interval = plugin.getConfig().getInt("check-interval-minutes", 5) * 1200;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAll, 200L, Math.max(200, interval)).getTaskId();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    }

    public void restart() { stop(); start(); }

    public void checkAll() {
        Set<StreamEvent> events = TwitchChecker.check(plugin);

        for (String key : new HashSet<>(announced)) {
            if (events.stream().noneMatch(e -> key.equals(e.getChannel()))) {
                activeStreams.remove(key);
                announced.remove(key);
                claimedRewards.remove(key);
            }
        }

        int cooldownSec = plugin.getConfig().getInt("announcement.cooldown-seconds", 300);
        for (StreamEvent e : events) {
            if (!e.isLive() || announced.contains(e.getChannel())) continue;

            long now = System.currentTimeMillis();
            Long last = lastAnnounceTime.get(e.getChannel());
            if (last != null && (now - last) < cooldownSec * 1000L) continue;

            lastAnnounceTime.put(e.getChannel(), now);
            announced.add(e.getChannel());
            claimedRewards.putIfAbsent(e.getChannel(), ConcurrentHashMap.newKeySet());
            activeStreams.put(e.getChannel(), e);
            Bukkit.getScheduler().runTask(plugin, () -> announce(e));
        }
    }

    public void forceAnnounce(StreamEvent e) {
        if (announced.contains(e.getChannel())) return;
        announced.add(e.getChannel());
        claimedRewards.putIfAbsent(e.getChannel(), ConcurrentHashMap.newKeySet());
        activeStreams.put(e.getChannel(), e);
        Bukkit.getScheduler().runTask(plugin, () -> announce(e));
    }

    public List<StreamEvent> getLiveStreams() {
        return new ArrayList<>(activeStreams.values());
    }

    private void announce(StreamEvent e) {
        // ВК чат (беседа)
        for (String line : plugin.getConfig().getStringList("announcement.chat")) {
            String msg = format(line, e).trim();
            if (!msg.isEmpty()) VKChatBridge.sendToMainChat(msg);
        }

        // ЛС игрокам
        String dmTemplate = plugin.getConfig().getString("announcement.player-dm", "");
        if (!dmTemplate.isEmpty()) {
            String dm = format(dmTemplate, e).trim();
            if (!dm.isEmpty()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    int vkId = VKChatBridge.getLinkedVkId(p);
                    if (vkId != -1) VKChatBridge.sendMessage(vkId, dm);
                }
            }
        }
    }

    public boolean claimReward(Player p) {
        if (announced.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
            return false;
        }

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

        int rep = plugin.getConfig().getInt("rewards.reputation", 150);
        int vkId = VKChatBridge.getLinkedVkId(p);
        if (vkId != -1) VKChatBridge.addPoints(vkId, rep);

        for (String cmd : plugin.getConfig().getStringList("rewards.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }

        String info = stream != null ? " (" + stream.getChannel() + ")" : "";
        p.sendMessage(ChatColor.GREEN + "✓ Награда получена" + info + "! +" + rep + " репутации ВК.");
        return true;
    }

    private String format(String template, StreamEvent e) {
        String title = e.getTitle();
        if (title == null || title.isEmpty()) title = "Без названия";

        return template.replace("{channel}", e.getChannel())
                .replace("{title}", title)
                .replace("{game}", e.getGame() != null ? e.getGame() : "")
                .replace("{viewers}", e.getViewerCount() > 0 ? String.valueOf(e.getViewerCount()) : "0")
                .replace("{url}", e.getUrl() != null ? e.getUrl() : "");
    }

    public Set<String> getAnnounced() { return announced; }
    public void resetAnnounced() { announced.clear(); activeStreams.clear(); claimedRewards.clear(); lastAnnounceTime.clear(); }
    public void reload() { lastAnnounceTime.clear(); TwitchChecker.resetToken(); }
}
