package ru.example.vkchat.managers;

import ru.example.vkchat.VKChatPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChatManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, Long> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, String> muteReasons = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();

    public ChatManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        loadMutesFromDatabase();
    }

    public void mutePlayer(UUID target, long durationMillis) {
        mutePlayer(target, durationMillis, null);
    }

    public void mutePlayer(UUID target, long durationMillis, String reason) {
        long expiry = System.currentTimeMillis() + durationMillis;
        mutes.put(target, expiry);
        if (reason != null) {
            muteReasons.put(target, reason);
        }
        saveMuteToDatabase(target, expiry, reason);
    }

    public void unmutePlayer(UUID target) {
        mutes.remove(target);
        muteReasons.remove(target);
        deleteMuteFromDatabase(target);
    }

    public boolean isMuted(UUID target) {
        if (!mutes.containsKey(target)) return false;
        if (System.currentTimeMillis() > mutes.get(target)) {
            mutes.remove(target);
            muteReasons.remove(target);
            deleteMuteFromDatabase(target);
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

    private void loadMutesFromDatabase() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT uuid, expiry, reason FROM vkchat_mutes");
                ResultSet rs = ps.executeQuery();
                long now = System.currentTimeMillis();
                while (rs.next()) {
                    long expiry = rs.getLong("expiry");
                    if (expiry > now) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String reason = rs.getString("reason");
                        mutes.put(uuid, expiry);
                        if (reason != null) muteReasons.put(uuid, reason);
                    }
                }
                plugin.getLogger().info("[ChatManager] Loaded " + mutes.size() + " active mutes from database.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[ChatManager] Failed to load mutes from database: " + e.getMessage());
            }
        });
    }

    private void saveMuteToDatabase(UUID uuid, long expiry, String reason) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps;
                if (plugin.getConfig().getBoolean("database.use-mysql", false)) {
                    ps = conn.prepareStatement("INSERT INTO vkchat_mutes (uuid, expiry, reason) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE expiry = ?, reason = ?");
                } else {
                    ps = conn.prepareStatement("INSERT INTO vkchat_mutes (uuid, expiry, reason) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET expiry = ?, reason = ?");
                }
                ps.setString(1, uuid.toString());
                ps.setLong(2, expiry);
                ps.setString(3, reason);
                ps.setLong(4, expiry);
                ps.setString(5, reason);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[ChatManager] Failed to save mute to database: " + e.getMessage());
            }
        });
    }

    private void deleteMuteFromDatabase(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("DELETE FROM vkchat_mutes WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[ChatManager] Failed to delete mute from database: " + e.getMessage());
            }
        });
    }
}
