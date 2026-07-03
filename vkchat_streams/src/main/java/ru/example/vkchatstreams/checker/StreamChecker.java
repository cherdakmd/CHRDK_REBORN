package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final Map<String, String[]> streamerLinks = new HashMap<>();
    private int taskId = -1;

    public StreamChecker(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
        loadStreamerLinks();
    }

    private void loadStreamerLinks() {
        streamerLinks.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("streamers");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String yt = sec.getString(key + ".youtube", "");
            String vk = sec.getString(key + ".vk", "");
            if (!yt.isEmpty() || !vk.isEmpty()) {
                streamerLinks.put(key.toLowerCase(), new String[]{yt, vk});
            }
        }
    }

    public void start() {
        int interval = plugin.getConfig().getInt("check-interval-minutes", 5) * 1200;
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAll, 200L, Math.max(200, interval)).getTaskId();
    }

    public void stop() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); }
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
        loadStreamerLinks();
        if (announced.contains(e.getChannel())) return;
        announced.add(e.getChannel());
        claimedRewards.putIfAbsent(e.getChannel(), ConcurrentHashMap.newKeySet());
        activeStreams.put(e.getChannel(), e);
        Bukkit.getScheduler().runTask(plugin, () -> announce(e));
    }

    public List<StreamEvent> getLiveStreams() { return new ArrayList<>(activeStreams.values()); }

    private void announce(StreamEvent e) {
        String ytUrl = "", vkUrl = "";
        String[] links = streamerLinks.get(e.getChannel().toLowerCase());
        if (links != null) {
            if (!links[0].isEmpty()) ytUrl = links[0];
            if (!links[1].isEmpty()) vkUrl = links[1];
        }

        // Основной текст для чата
        StringBuilder chatText = new StringBuilder();
        for (String line : plugin.getConfig().getStringList("announcement.chat")) {
            String msg = format(line, e, ytUrl, vkUrl).trim();
            if (!msg.isEmpty()) {
                if (chatText.length() > 0) chatText.append("\n");
                chatText.append(msg);
            }
        }

        String fullText = chatText.toString().trim();
        if (fullText.isEmpty()) return;

        // Отправка в беседу ВК — с клавиатурой или без
        boolean useKeyboard = plugin.getConfig().getBoolean("announcement.keyboard", true);
        if (useKeyboard) {
            String keyboard = buildKeyboard(e, ytUrl, vkUrl);
            int peerId = VKChatBridge.getMainChatPeerId();
            if (peerId > 0 && !keyboard.isEmpty()) {
                VKChatBridge.sendKeyboard(peerId, fullText, keyboard);
            } else {
                VKChatBridge.sendToMainChat(fullText);
            }
        } else {
            VKChatBridge.sendToMainChat(fullText);
        }

        // ЛС игрокам
        String dmTemplate = plugin.getConfig().getString("announcement.player-dm", "");
        if (!dmTemplate.isEmpty()) {
            String dm = format(dmTemplate, e, ytUrl, vkUrl).trim();
            if (!dm.isEmpty()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    int vkId = VKChatBridge.getLinkedVkId(p);
                    if (vkId != -1) {
                        if (useKeyboard) {
                            String kb = buildKeyboard(e, ytUrl, vkUrl);
                            VKChatBridge.sendKeyboard(vkId, dm, kb);
                        } else {
                            VKChatBridge.sendMessage(vkId, dm);
                        }
                    }
                }
            }
        }
    }

    private String buildKeyboard(StreamEvent e, String ytUrl, String vkUrl) {
        StringBuilder kb = new StringBuilder("{\"inline\":false,\"buttons\":[");
        boolean first = true;

        // Кнопка Twitch
        if (e.getUrl() != null && !e.getUrl().isEmpty()) {
            if (!first) kb.append(","); first = false;
            kb.append("[{\"action\":{\"type\":\"open_link\",\"link\":\"")
              .append(escapeJson(e.getUrl())).append("\",\"label\":\"📺 Смотреть Twitch\"}}]");
        }

        // Кнопка YouTube
        if (!ytUrl.isEmpty()) {
            if (!first) kb.append(","); first = false;
            kb.append("[{\"action\":{\"type\":\"open_link\",\"link\":\"")
              .append(escapeJson(ytUrl)).append("\",\"label\":\"🔴 YouTube\"}}]");
        }

        // Кнопка VK
        if (!vkUrl.isEmpty()) {
            if (!first) kb.append(","); first = false;
            kb.append("[{\"action\":{\"type\":\"open_link\",\"link\":\"")
              .append(escapeJson(vkUrl)).append("\",\"label\":\"🔵 ВК группа\"}}]");
        }

        kb.append("]}");
        return kb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean claimReward(Player p) {
        if (announced.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
            return false;
        }

        String keyToClaim = null;
        for (String key : announced) {
            Set<UUID> claimers = claimedRewards.get(key);
            if (claimers == null || !claimers.contains(p.getUniqueId())) { keyToClaim = key; break; }
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

    private String format(String template, StreamEvent e, String ytUrl, String vkUrl) {
        String title = e.getTitle();
        if (title == null || title.isEmpty()) title = "Без названия";

        String linksStr = "📺 Twitch: " + (e.getUrl() != null ? e.getUrl() : "");
        if (!ytUrl.isEmpty()) linksStr += "\n🔴 YouTube: " + ytUrl;
        if (!vkUrl.isEmpty()) linksStr += "\n🔵 VK: " + vkUrl;

        return template.replace("{channel}", e.getChannel())
                .replace("{title}", title)
                .replace("{game}", e.getGame() != null ? e.getGame() : "")
                .replace("{viewers}", e.getViewerCount() > 0 ? String.valueOf(e.getViewerCount()) : "0")
                .replace("{url}", e.getUrl() != null ? e.getUrl() : "")
                .replace("{youtube_url}", ytUrl)
                .replace("{vk_url}", vkUrl)
                .replace("{links}", linksStr);
    }

    public Set<String> getAnnounced() { return announced; }
    public void resetAnnounced() { announced.clear(); activeStreams.clear(); claimedRewards.clear(); lastAnnounceTime.clear(); }
    public void reload() { loadStreamerLinks(); lastAnnounceTime.clear(); TwitchChecker.resetToken(); }
}
