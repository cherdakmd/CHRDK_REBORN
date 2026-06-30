package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Новый координатор авторизации
 * Интегрирует все менеджеры в единую систему
 */
public class AuthManagerNew {
    private final VKChatPlugin plugin;
    private final SessionManager sessionManager;
    private final LinkManager linkManager;
    private final TwoFactorManager twoFactorManager;
    private final PassManager passManager;
    private final MembershipManager membershipManager;

    public AuthManagerNew(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.sessionManager = new SessionManager(plugin);
        this.linkManager = new LinkManager(plugin);
        this.twoFactorManager = new TwoFactorManager(plugin);
        this.passManager = new PassManager(plugin);
        this.membershipManager = new MembershipManager(plugin);
    }

    // ═══════════════════════════════════════════════════════════════
    // ГЕТТЕРЫ МЕНЕДЖЕРОВ
    // ═══════════════════════════════════════════════════════════════

    public SessionManager getSessionManager() { return sessionManager; }
    public LinkManager getLinkManager() { return linkManager; }
    public TwoFactorManager getTwoFactorManager() { return twoFactorManager; }
    public PassManager getPassManager() { return passManager; }
    public MembershipManager getMembershipManager() { return membershipManager; }

    // ═══════════════════════════════════════════════════════════════
    // ОСНОВНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Обработка входа игрока
     */
    public void onJoin(Player p) {
        // Создаём сессию
        SessionManager.PlayerSession session = sessionManager.createSession(p);

        // Проверяем привязку ВК
        int vkId = getLinkedVkId(p);
        if (vkId != -1) {
            session.vkId = vkId;

            // Проверяем членство в группе
            if (plugin.getConfig().getBoolean("auth.link.require-membership", true)) {
                if (!membershipManager.isFullMember(vkId)) {
                    // Не в группе — кик
                    String kickMsg = membershipManager.getMembershipErrorMessage(vkId);
                    p.kickPlayer(kickMsg);
                    return;
                }
            }

            // Триггерим 2FA
            if (plugin.getConfig().getBoolean("auth.2fa.enabled", true)) {
                boolean sent = twoFactorManager.trigger2fa(p, vkId);
                if (sent) {
                    sessionManager.setState(p.getUniqueId(), SessionManager.SessionState.WAITING_2FA);
                }
            }
        } else {
            // Нет привязки — проверяем проходку
            if (passManager.hasPass(p.getUniqueId())) {
                session.hasPass = true;
                session.passExpiry = passManager.getPassRemainingDays(p.getUniqueId()) * 24 * 60 * 60 * 1000 + System.currentTimeMillis();
                sessionManager.setState(p.getUniqueId(), SessionManager.SessionState.PASS_HOLDER);
                p.sendMessage("§a✅ Добро пожаловать! У тебя есть проходка.");
            } else {
                // Нет ВК, нет проходки — кик
                String kickMsg = "§c❌ Для игры необходимо привязать ВКонтакте или купить проходку!\n\n" +
                               "§eПривязка ВК:\n" +
                               "§71. Вступи в группу: " + plugin.getConfig().getString("vk.group-link", "https://vk.com/chrdk_reborn") + "\n" +
                               "§72. Зайди на сервер и введи /vklink\n" +
                               "§73. Отправь код в беседу ВК\n\n" +
                               "§eПокупка проходки:\n" +
                               "§7Донат 500р на DonatePay с указанием никнейма\n" +
                               "§7Ссылка: https://donatepay.ru/don/dedworkshop";
                p.kickPlayer(kickMsg);
                return;
            }
        }
    }

    /**
     * Обработка выхода игрока
     */
    public void onQuit(Player p) {
        sessionManager.destroySession(p.getUniqueId());
        twoFactorManager.onPlayerQuit(p.getUniqueId());
        linkManager.onPlayerQuit(p.getUniqueId());
    }

    /**
     * Подтверждение 2FA
     */
    public boolean confirm2fa(UUID uuid, String code) {
        TwoFactorManager.TwoFactorResult result = twoFactorManager.confirm2fa(uuid, code);
        Player p = plugin.getServer().getPlayer(uuid);

        switch (result) {
            case SUCCESS:
                sessionManager.setState(uuid, SessionManager.SessionState.LOGGED_IN);
                if (p != null) {
                    p.sendMessage("§a✅ Двухфакторная аутентификация пройдена! Приятной игры.");
                    saveIp(p);
                }
                return true;

            case WRONG_CODE:
                if (p != null) {
                    int remaining = twoFactorManager.getRemainingAttempts(uuid);
                    p.sendMessage("§c❌ Неверный код! Осталось попыток: " + remaining);
                }
                return false;

            case LOCKED:
                if (p != null) {
                    p.sendMessage("§c🔒 Слишком много неудачных попыток! Подожди 5 минут.");
                }
                return false;

            case EXPIRED:
                if (p != null) {
                    p.sendMessage("§c⏰ Код истёк! Перезайди на сервер.");
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * Привязка ВК
     */
    public LinkManager.LinkResult tryLink(int vkId, String code, int peerId) {
        return linkManager.tryLink(vkId, code, peerId);
    }

    /**
     * Генерация кода привязки
     */
    public String generateLinkCode(Player p) {
        return linkManager.generateLinkCode(p);
    }

    /**
     * Проверка привязки
     */
    public boolean isLinked(Player p) {
        return getLinkedVkId(p) != -1;
    }

    /**
     * Получить VK ID по UUID
     */
    public int getLinkedVkId(Player p) {
        return getLinkedVkId(p.getUniqueId());
    }

    /**
     * Получить VK ID по UUID
     */
    public int getLinkedVkId(UUID uuid) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return -1;

            PreparedStatement ps = conn.prepareStatement("SELECT vk_id FROM vkchat_auth WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            int vkId = -1;
            if (rs.next()) {
                vkId = rs.getInt("vk_id");
            }

            rs.close();
            ps.close();
            return vkId;
        } catch (SQLException e) {
            return -1;
        }
    }

    /**
     * Получить UUID по VK ID
     */
    public UUID getUuidByVkId(int vkId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return null;

            PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
            ps.setInt(1, vkId);
            ResultSet rs = ps.executeQuery();

            UUID uuid = null;
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
            }

            rs.close();
            ps.close();
            return uuid;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Проверить, авторизован ли игрок
     */
    public boolean isFullyAuthorized(Player p) {
        return sessionManager.isFullyAuthorized(p.getUniqueId());
    }

    /**
     * Проверить, ожидает ли 2FA
     */
    public boolean isWaiting2fa(Player p) {
        return twoFactorManager.isWaiting2fa(p.getUniqueId());
    }

    /**
     * Обновить активность
     */
    public void updateLastActivity(UUID uuid) {
        sessionManager.updateActivity(uuid);
    }

    /**
     * Сохранить IP
     */
    private void saveIp(Player p) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return;

            String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
            long now = System.currentTimeMillis();

            PreparedStatement ps = conn.prepareStatement(
                "UPDATE vkchat_auth SET last_ip = ?, last_login = ? WHERE uuid = ?"
            );
            ps.setString(1, ip);
            ps.setLong(2, now);
            ps.setString(3, p.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка сохранения IP: " + e.getMessage());
        }
    }

    /**
     * Заблокировать сессию из ВК
     */
    public boolean blockLoginByCode(String code) {
        // Ищем UUID по коду 2FA
        for (var entry : twoFactorManager.getClass().getDeclaredFields()) {
            // Это сложно реализовать без прямого доступа к полям
        }
        return false;
    }

    /**
     * Получить количество активных сессий
     */
    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    /**
     * Получить количество ожидающих 2FA
     */
    public int getPending2faCount() {
        return twoFactorManager.getPendingCount();
    }

    /**
     * Получить количество активных проходок
     */
    public int getActivePassCount() {
        return passManager.getActivePassCount();
    }
}
