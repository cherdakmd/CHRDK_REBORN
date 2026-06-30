package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер привязки ВК аккаунтов
 * Управляет кодами привязки и процессом связывания
 */
public class LinkManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, String> linkCodes = new ConcurrentHashMap<>();      // UUID -> код
    private final Map<String, Integer> codeToVk = new ConcurrentHashMap<>();    // код -> VK ID
    private final Map<String, Long> codeExpiry = new ConcurrentHashMap<>();     // код -> время истечения

    private static final long CODE_EXPIRY_MS = 10 * 60 * 1000L; // 10 минут

    public LinkManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Сгенерировать код привязки для игрока
     */
    public String generateLinkCode(Player p) {
        // Удаляем старый код если есть
        String oldCode = linkCodes.remove(p.getUniqueId());
        if (oldCode != null) {
            codeToVk.remove(oldCode);
            codeExpiry.remove(oldCode);
        }

        // Генерируем новый код
        int codeLength = plugin.getConfig().getInt("auth.link.code-length", 6);
        int min = (int) Math.pow(10, codeLength - 1);
        int max = (int) Math.pow(10, codeLength) - 1;
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(min, max));

        // Сохраняем
        linkCodes.put(p.getUniqueId(), code);
        codeToVk.put(code, -1); // -1 = ожидает привязки
        codeExpiry.put(code, System.currentTimeMillis() + CODE_EXPIRY_MS);

        return code;
    }

    /**
     * Проверить, является ли код действительным
     */
    public boolean isValidCode(String code) {
        if (!codeToVk.containsKey(code)) return false;
        Long expiry = codeExpiry.get(code);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            // Код истек
            codeToVk.remove(code);
            codeExpiry.remove(code);
            return false;
        }
        return true;
    }

    /**
     * Получить UUID по коду
     */
    public UUID getUuidByCode(String code) {
        for (Map.Entry<UUID, String> entry : linkCodes.entrySet()) {
            if (entry.getValue().equals(code)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Получить код по UUID
     */
    public String getCodeByUuid(UUID uuid) {
        return linkCodes.get(uuid);
    }

    /**
     * Попробовать привязать ВК
     */
    public LinkResult tryLink(int vkId, String code, int peerId) {
        // Проверяем код
        if (!isValidCode(code)) {
            return LinkResult.INVALID_CODE;
        }

        UUID uuid = getUuidByCode(code);
        if (uuid == null) {
            return LinkResult.INVALID_CODE;
        }

        // Проверяем, не привязан ли уже этот ВК к другому игроку (анти-твинк)
        if (plugin.getConfig().getBoolean("auth.link.anti-twink", true)) {
            if (isVkLinkedToAnother(vkId, uuid)) {
                return LinkResult.VK_ALREADY_LINKED;
            }
        }

        // Проверяем членство в группе
        if (plugin.getConfig().getBoolean("auth.link.require-membership", true)) {
            if (!plugin.getVkManager().isMemberOfGroupAndChat(vkId)) {
                return LinkResult.NOT_MEMBER;
            }
        }

        // Сохраняем привязку в БД
        try {
            saveLink(uuid, vkId);
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка сохранения привязки: " + e.getMessage());
            return LinkResult.DATABASE_ERROR;
        }

        // Очищаем код
        cleanupCode(code, uuid);

        // Начисляем стартовую репутацию
        int starterRep = plugin.getConfig().getInt("bonus.starter-reputation", 1000);
        if (starterRep > 0) {
            try {
                plugin.getReputationManager().addPoints(vkId, starterRep);
            } catch (Exception ignored) {}
        }

        // Добавляем в вайтлист
        if (plugin.getConfig().getBoolean("auth.auto-whitelist", true)) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                plugin.getServer().getWhitelistedPlayers().add(p);
            }
        }

        return LinkResult.SUCCESS;
    }

    /**
     * Проверить, привязан ли ВК к другому игроку
     */
    private boolean isVkLinkedToAnother(int vkId, UUID currentUuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return false;

            PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM vkchat_auth WHERE vk_id = ? AND uuid != ?"
            );
            ps.setInt(1, vkId);
            ps.setString(2, currentUuid.toString());
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close();
            ps.close();
            return exists;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Сохранить привязку в БД
     */
    private void saveLink(UUID uuid, int vkId) throws SQLException {
        Connection conn = plugin.getDatabaseManager().getConnection();
        if (conn == null) throw new SQLException("Нет подключения к БД");

        // SQLite или MySQL совместимый запрос
        String sql;
        if (!plugin.getConfig().getBoolean("database.use-mysql", false)) {
            sql = "INSERT OR REPLACE INTO vkchat_auth (uuid, vk_id) VALUES (?, ?)";
        } else {
            sql = "INSERT INTO vkchat_auth (uuid, vk_id) VALUES (?, ?) " +
                  "ON DUPLICATE KEY UPDATE vk_id = VALUES(vk_id)";
        }

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, uuid.toString());
        ps.setInt(2, vkId);
        ps.executeUpdate();
        ps.close();
    }

    /**
     * Очистить код привязки
     */
    private void cleanupCode(String code, UUID uuid) {
        linkCodes.remove(uuid);
        codeToVk.remove(code);
        codeExpiry.remove(code);
    }

    /**
     * Очистить код при выходе игрока
     */
    public void onPlayerQuit(UUID uuid) {
        String code = linkCodes.remove(uuid);
        if (code != null) {
            codeToVk.remove(code);
            codeExpiry.remove(code);
        }
    }

    /**
     * Получить количество активных кодов
     */
    public int getActiveCodeCount() {
        return linkCodes.size();
    }

    /**
     * Периодическая очистка истёкших кодов
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            codeExpiry.entrySet().removeIf(entry -> {
                if (now > entry.getValue()) {
                    String code = entry.getKey();
                    UUID uuid = getUuidByCode(code);
                    if (uuid != null) {
                        linkCodes.remove(uuid);
                    }
                    codeToVk.remove(code);
                    return true;
                }
                return false;
            });
        }, 6000L, 6000L); // Каждые 5 минут
    }

    // Результат привязки
    public enum LinkResult {
        SUCCESS,
        INVALID_CODE,
        VK_ALREADY_LINKED,
        NOT_MEMBER,
        DATABASE_ERROR
    }
}
