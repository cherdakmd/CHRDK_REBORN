package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Менеджер сессий игроков
 * Отслеживает состояние авторизации каждого игрока
 */
public class SessionManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    // Состояния сессии
    public enum SessionState {
        UNLINKED,        // Не привязан к ВК, нет проходки
        WAITING_2FA,     // Ожидает ввода 2FA кода
        LOGGED_IN,       // Полностью авторизован
        PASS_HOLDER      // Имеет проходку (без ВК)
    }

    // Данные сессии игрока
    public static class PlayerSession {
        public final UUID uuid;
        public int vkId;
        public String ip;
        public long joinTime;
        public long lastActivity;
        public SessionState state;
        public boolean hasPass;
        public long passExpiry;

        public PlayerSession(UUID uuid) {
            this.uuid = uuid;
            this.vkId = -1;
            this.ip = "";
            this.joinTime = System.currentTimeMillis();
            this.lastActivity = System.currentTimeMillis();
            this.state = SessionState.UNLINKED;
            this.hasPass = false;
            this.passExpiry = 0;
        }
    }

    public SessionManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Создать сессию при входе игрока
     */
    public PlayerSession createSession(Player p) {
        PlayerSession session = new PlayerSession(p.getUniqueId());
        session.ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
        sessions.put(p.getUniqueId(), session);
        return session;
    }

    /**
     * Получить сессию игрока
     */
    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /**
     * Получить сессию игрока
     */
    public PlayerSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    /**
     * Удалить сессию при выходе
     */
    public void destroySession(UUID uuid) {
        sessions.remove(uuid);
    }

    /**
     * Обновить активность
     */
    public void updateActivity(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            session.lastActivity = System.currentTimeMillis();
        }
    }

    /**
     * Проверить, авторизован ли игрок
     */
    public boolean isFullyAuthorized(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return false;
        return session.state == SessionState.LOGGED_IN || session.state == SessionState.PASS_HOLDER;
    }

    /**
     * Проверить, имеет ли проходку
     */
    public boolean hasPass(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return false;
        return session.hasPass && session.passExpiry > System.currentTimeMillis();
    }

    /**
     * Установить состояние сессии
     */
    public void setState(UUID uuid, SessionState state) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            session.state = state;
        }
    }

    /**
     * Установить ВК ID
     */
    public void setVkId(UUID uuid, int vkId) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            session.vkId = vkId;
        }
    }

    /**
     * Установить проходку
     */
    public void setPass(UUID uuid, boolean hasPass, long expiry) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            session.hasPass = hasPass;
            session.passExpiry = expiry;
            if (hasPass && expiry > System.currentTimeMillis()) {
                session.state = SessionState.PASS_HOLDER;
            }
        }
    }

    /**
     * Получить количество активных сессий
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Получить количество авторизованных
     */
    public int getAuthorizedCount() {
        int count = 0;
        for (PlayerSession session : sessions.values()) {
            if (session.state == SessionState.LOGGED_IN || session.state == SessionState.PASS_HOLDER) {
                count++;
            }
        }
        return count;
    }

    /**
     * Очистка неактивных сессий
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            long timeout = plugin.getConfig().getLong("auth.session.timeout-minutes", 30) * 60 * 1000;

            sessions.entrySet().removeIf(entry -> {
                PlayerSession session = entry.getValue();
                // Удаляем сессии неактивных игроков
                if (now - session.lastActivity > timeout) {
                    return true;
                }
                // Удаляем сессии игроков которые оффлайн
                if (plugin.getServer().getPlayer(entry.getKey()) == null) {
                    return true;
                }
                return false;
            });
        }, 1200L, 1200L); // Каждую минуту
    }
}
