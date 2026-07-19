package ru.example.vkchatstreams.checker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchatstreams.VKChatStreamsPlugin;
import ru.example.vkchatstreams.StreamEvent;
import ru.example.vkchatstreams.announce.KeyboardBuilder;
import ru.example.vkchatstreams.announce.TemplateEngine;

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
    private final Map<String, Long> streamStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> claimedRewards = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAnnounceTime = new ConcurrentHashMap<>();
    private final Map<String, StreamerLinks> streamerLinks = new HashMap<>();
    private int taskId = -1;

    private static final int INITIAL_DELAY_TICKS = 200;
    private static final int TICKS_PER_MINUTE = 1200;
    private static final long MILLIS_PER_SECOND = 1000L;

    public record StreamerLinks(String youtube, String vk) {
        public boolean isEmpty() { return (youtube == null || youtube.isEmpty()) && (vk == null || vk.isEmpty()); }
    }

    public StreamChecker(VKChatStreamsPlugin plugin) {
        this.plugin = plugin;
        loadStreamerLinks();
    }

    // ═══ Lifecycle ═══

    public void start() {
        int intervalTicks = plugin.getConfig().getInt("check-interval-minutes", 5) * TICKS_PER_MINUTE;
        taskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::checkAll, INITIAL_DELAY_TICKS, Math.max(INITIAL_DELAY_TICKS, intervalTicks))
                .getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void restart() { stop(); start(); }

    public void reload() {
        loadStreamerLinks();
        lastAnnounceTime.clear();
        TwitchChecker.resetToken();
    }

    // ═══ Config ═══

    private void loadStreamerLinks() {
        streamerLinks.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("streamers");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String yt = sec.getString(key + ".youtube", "");
            String vk = sec.getString(key + ".vk", "");
            if (!yt.isEmpty() || !vk.isEmpty()) {
                streamerLinks.put(key.toLowerCase(), new StreamerLinks(yt, vk));
            }
        }
    }

    private StreamerLinks getLinks(String channel) {
        return streamerLinks.getOrDefault(channel.toLowerCase(), new StreamerLinks("", ""));
    }

    private int getCooldownMillis() {
        return plugin.getConfig().getInt("announcement.cooldown-seconds", 300) * (int) MILLIS_PER_SECOND;
    }

    private boolean isGameAnnounceEnabled() {
        return plugin.getConfig().getBoolean("announcement.game-enabled", true);
    }

    private boolean isKeyboardEnabled() {
        return plugin.getConfig().getBoolean("announcement.keyboard", true);
    }

    // ═══ Core Logic ═══

    public void checkAll() {
        Set<StreamEvent> events = TwitchChecker.check(plugin);
        handleOfflineStreams(events);
        handleNewStreams(events);
    }

    private void handleOfflineStreams(Set<StreamEvent> events) {
        for (String key : new HashSet<>(announced)) {
            if (events.stream().noneMatch(e -> key.equals(e.getChannel()))) {
                StreamEvent old = activeStreams.remove(key);
                announced.remove(key);
                if (old != null) {
                    long start = streamStartTimes.remove(key);
                    int claimed = claimedRewards.getOrDefault(key, Set.of()).size();
                    claimedRewards.remove(key);
                    long uptimeSeconds = (System.currentTimeMillis() - start) / MILLIS_PER_SECOND;
                    Bukkit.getScheduler().runTask(plugin, () -> notifyOffline(old, uptimeSeconds, claimed));
                }
            }
        }
    }

    private void handleNewStreams(Set<StreamEvent> events) {
        int cooldownMs = getCooldownMillis();
        for (StreamEvent e : events) {
            if (!e.isLive() || announced.contains(e.getChannel())) continue;

            long now = System.currentTimeMillis();
            Long last = lastAnnounceTime.get(e.getChannel());
            if (last != null && (now - last) < cooldownMs) continue;

            registerStream(e, now);
            Bukkit.getScheduler().runTask(plugin, () -> announce(e));
        }
    }

    private void registerStream(StreamEvent e, long announceTime) {
        lastAnnounceTime.put(e.getChannel(), announceTime);
        announced.add(e.getChannel());
        claimedRewards.computeIfAbsent(e.getChannel(), k -> ConcurrentHashMap.newKeySet());
        streamStartTimes.putIfAbsent(e.getChannel(), e.getStartTime());
        activeStreams.put(e.getChannel(), e);
    }

    public void forceAnnounce(StreamEvent e) {
        loadStreamerLinks();
        if (announced.contains(e.getChannel())) return;
        registerStream(e, System.currentTimeMillis());
        Bukkit.getScheduler().runTask(plugin, () -> announce(e));
    }

    // ═══ Announcements ═══

    private void announce(StreamEvent e) {
        StreamerLinks links = getLinks(e.getChannel());
        if (isGameAnnounceEnabled()) announceToGame(e, links);
        announceToVK(e, links);
        announceToPlayers(e, links);
    }

    private void announceToGame(StreamEvent e, StreamerLinks links) {
        for (String line : plugin.getConfig().getStringList("announcement.game")) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    TemplateEngine.format(line, e, links.youtube(), links.vk(), 0, null));
            if (!msg.trim().isEmpty()) Bukkit.broadcastMessage(msg);
        }
    }

    private void announceToVK(StreamEvent e, StreamerLinks links) {
        StringBuilder chatText = new StringBuilder();
        for (String line : plugin.getConfig().getStringList("announcement.chat")) {
            String msg = TemplateEngine.format(line, e, links.youtube(), links.vk(), 0, null).trim();
            if (!msg.isEmpty()) chatText.append(chatText.length() > 0 ? "\n" : "").append(msg);
        }
        String fullText = chatText.toString().trim();
        if (fullText.isEmpty()) return;

        String keyboard = isKeyboardEnabled() ? KeyboardBuilder.build(e, links.youtube(), links.vk()) : null;
        sendToChat(fullText, keyboard);
    }

    private void sendToChat(String text, String keyboard) {
        if (keyboard != null) {
            int peerId = VKChatBridge.getMainChatPeerId();
            if (peerId > 0) {
                VKChatBridge.sendKeyboard(peerId, text, keyboard);
                return;
            }
        }
        VKChatBridge.sendToMainChat(text);
    }

    private void announceToPlayers(StreamEvent e, StreamerLinks links) {
        String dmTemplate = plugin.getConfig().getString("announcement.player-dm", "");
        if (dmTemplate.isEmpty()) return;

        String dm = TemplateEngine.format(dmTemplate, e, links.youtube(), links.vk(), 0, null).trim();
        if (dm.isEmpty()) return;

        String keyboard = isKeyboardEnabled() ? KeyboardBuilder.build(e, links.youtube(), links.vk()) : null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (vkId != -1) {
                if (keyboard != null) VKChatBridge.sendKeyboard(vkId, dm, keyboard);
                else VKChatBridge.sendMessage(vkId, dm);
            }
        }
    }

    private void notifyOffline(StreamEvent e, long uptimeSeconds, int claimed) {
        String template = plugin.getConfig().getString("announcement.offline", "");
        if (template.isEmpty()) return;
        String msg = TemplateEngine.formatOffline(template, e.getChannel(), e.getTitle(), claimed, uptimeSeconds, e.getViewerCount());
        VKChatBridge.sendToMainChat(msg.trim());
    }

    // ═══ Rewards ═══

    public boolean claimReward(Player p) {
        if (announced.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Сейчас нет активных стримов.");
            return false;
        }

        String keyToClaim = findUnclaimedStream(p.getUniqueId());
        if (keyToClaim == null) {
            p.sendMessage(ChatColor.RED + "Ты уже получил награду за все активные стримы!");
            return false;
        }

        claimedRewards.computeIfAbsent(keyToClaim, k -> ConcurrentHashMap.newKeySet()).add(p.getUniqueId());
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

    private String findUnclaimedStream(UUID playerId) {
        for (String key : announced) {
            Set<UUID> claimers = claimedRewards.get(key);
            if (claimers == null || !claimers.contains(playerId)) return key;
        }
        return null;
    }

    // ═══ Public Getters ═══

    public List<StreamEvent> getLiveStreams() { return new ArrayList<>(activeStreams.values()); }

    public int getClaimedCount(String channel) {
        Set<UUID> s = claimedRewards.get(channel);
        return s != null ? s.size() : 0;
    }

    public long getStartTime(String channel) {
        return streamStartTimes.getOrDefault(channel, System.currentTimeMillis());
    }

    public Set<String> getAnnounced() { return announced; }

    public void resetAnnounced() {
        announced.clear();
        activeStreams.clear();
        claimedRewards.clear();
        streamStartTimes.clear();
        lastAnnounceTime.clear();
    }
}
