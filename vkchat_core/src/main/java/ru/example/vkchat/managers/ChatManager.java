package ru.example.vkchat.managers;

import ru.example.vkchat.VKChatPlugin;
import java.util.*;

public class ChatManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, Long> mutes = new HashMap<>();
    private final Map<UUID, Set<UUID>> ignores = new HashMap<>();

    public ChatManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void mutePlayer(UUID target, long durationMillis) {
        mutes.put(target, System.currentTimeMillis() + durationMillis);
    }

    public void unmutePlayer(UUID target) {
        mutes.remove(target);
    }

    public boolean isMuted(UUID target) {
        if (!mutes.containsKey(target)) return false;
        if (System.currentTimeMillis() > mutes.get(target)) {
            mutes.remove(target);
            return false;
        }
        return true;
    }

    public long getMuteRemaining(UUID target) {
        return mutes.getOrDefault(target, System.currentTimeMillis()) - System.currentTimeMillis();
    }

    public boolean toggleIgnore(UUID player, UUID target) {
        ignores.putIfAbsent(player, new HashSet<>());
        Set<UUID> list = ignores.get(player);
        if (list.contains(target)) {
            list.remove(target);
            return false; // unignored
        } else {
            list.add(target);
            return true; // ignored
        }
    }

    public boolean isIgnored(UUID player, UUID target) {
        return ignores.containsKey(player) && ignores.get(player).contains(target);
    }
}
