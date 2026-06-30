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

/**
 * Менеджер проходок
 * Управляет проходками для игроков без ВК
 */
public class PassManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, PassData> activePasses = new ConcurrentHashMap<>();

    private static class PassData {
        UUID uuid;
        String playerName;
        long startTime;
        long endTime;
        String type;

        PassData(UUID uuid, String playerName, long endTime, String type) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.startTime = System.currentTimeMillis();
            this.endTime = endTime;
            this.type = type;
        }

        boolean isActive() {
            return System.currentTimeMillis() < endTime;
        }

        long getRemainingDays() {
            return Math.max(0, (endTime - System.currentTimeMillis()) / (24 * 60 * 60 * 1000));
        }
    }

    public PassManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        loadPasses();
        startExpiryCheckTask();
    }

    /**
     * Загрузить проходки из БД
     */
    private void loadPasses() {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return;

            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT uuid, player_name, start_time, end_time, type FROM vkchat_passes WHERE end_time > " + System.currentTimeMillis()
            );

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String playerName = rs.getString("player_name");
                long startTime = rs.getLong("start_time");
                long endTime = rs.getLong("end_time");
                String type = rs.getString("type");

                PassData pass = new PassData(uuid, playerName, endTime, type);
                pass.startTime = startTime;
                activePasses.put(uuid, pass);
            }

            rs.close();
            plugin.getLogger().info("Загружено " + activePasses.size() + " активных проходок");
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка загрузки проходок: " + e.getMessage());
        }
    }

    /**
     * Выдать проходку игроку
     */
    public boolean grantPass(Player p, int days, String type) {
        UUID uuid = p.getUniqueId();
        long endTime = System.currentTimeMillis() + (days * 24L * 60 * 60 * 1000);

        // Проверяем, есть ли уже проходка
        PassData existing = activePasses.get(uuid);
        if (existing != null && existing.isActive()) {
            // Продлеваем
            endTime = existing.endTime + (days * 24L * 60 * 60 * 1000);
            existing.endTime = endTime;
            existing.type = type;
            savePass(uuid, p.getName(), existing.startTime, endTime, type);
            p.sendMessage("§a🔄 Проходка продлена на " + days + " дней! Действует до: " + formatDate(endTime));
            return true;
        }

        // Создаём новую
        PassData pass = new PassData(uuid, p.getName(), endTime, type);
        activePasses.put(uuid, pass);
        savePass(uuid, p.getName(), pass.startTime, endTime, type);
        p.sendMessage("§a🎉 Проходка выдана на " + days + " дней! Действует до: " + formatDate(endTime));
        return true;
    }

    /**
     * Выдать проходку по UUID (для админов)
     */
    public boolean grantPass(UUID uuid, String playerName, int days, String type) {
        long endTime = System.currentTimeMillis() + (days * 24L * 60 * 60 * 1000);

        PassData existing = activePasses.get(uuid);
        if (existing != null && existing.isActive()) {
            endTime = existing.endTime + (days * 24L * 60 * 60 * 1000);
            existing.endTime = endTime;
            existing.type = type;
            savePass(uuid, playerName, existing.startTime, endTime, type);
            return true;
        }

        PassData pass = new PassData(uuid, playerName, endTime, type);
        activePasses.put(uuid, pass);
        savePass(uuid, playerName, pass.startTime, endTime, type);
        return true;
    }

    /**
     * Отозвать проходку
     */
    public boolean revokePass(UUID uuid) {
        PassData pass = activePasses.remove(uuid);
        if (pass == null) return false;

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return false;

            PreparedStatement ps = conn.prepareStatement("DELETE FROM vkchat_passes WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка удаления проходки: " + e.getMessage());
        }

        return true;
    }

    /**
     * Проверить, имеет ли игрок проходку
     */
    public boolean hasPass(UUID uuid) {
        PassData pass = activePasses.get(uuid);
        return pass != null && pass.isActive();
    }

    /**
     * Получить оставшееся время проходки
     */
    public long getPassRemainingDays(UUID uuid) {
        PassData pass = activePasses.get(uuid);
        if (pass == null) return 0;
        return pass.getRemainingDays();
    }

    /**
     * Получить дату окончания проходки
     */
    public String getPassExpiryDate(UUID uuid) {
        PassData pass = activePasses.get(uuid);
        if (pass == null) return "нет";
        return formatDate(pass.endTime);
    }

    /**
     * Получить тип проходки
     */
    public String getPassType(UUID uuid) {
        PassData pass = activePasses.get(uuid);
        if (pass == null) return "нет";
        return pass.type;
    }

    /**
     * Проверить, истекает ли проходка скоро
     */
    public boolean isExpiringSoon(UUID uuid, int days) {
        PassData pass = activePasses.get(uuid);
        if (pass == null) return false;
        return pass.getRemainingDays() <= days;
    }

    /**
     * Сохранить проходку в БД
     */
    private void savePass(UUID uuid, String playerName, long startTime, long endTime, String type) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) return;

            String sql;
            if (!plugin.getConfig().getBoolean("database.use-mysql", false)) {
                sql = "INSERT OR REPLACE INTO vkchat_passes (uuid, player_name, start_time, end_time, type) VALUES (?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO vkchat_passes (uuid, player_name, start_time, end_time, type) VALUES (?, ?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE end_time = VALUES(end_time), type = VALUES(type)";
            }

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setLong(3, startTime);
            ps.setLong(4, endTime);
            ps.setString(5, type);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка сохранения проходки: " + e.getMessage());
        }
    }

    /**
     * Получить количество активных проходок
     */
    public int getActivePassCount() {
        return (int) activePasses.values().stream().filter(PassData::isActive).count();
    }

    /**
     * Получить количество истёкших проходок
     */
    public int getExpiredPassCount() {
        return (int) activePasses.values().stream().filter(p -> !p.isActive()).count();
    }

    /**
     * Получить список активных проходок
     */
    public String getPassList() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6═══ Проходки ═══\n");

        for (PassData pass : activePasses.values()) {
            if (pass.isActive()) {
                sb.append("§a• ").append(pass.playerName);
                sb.append(" §7— ").append(pass.getRemainingDays()).append(" дней");
                sb.append(" §8(").append(pass.type).append(")\n");
            }
        }

        if (sb.toString().endsWith("═══\n")) {
            sb.append("§7Нет активных проходок");
        }

        return sb.toString();
    }

    /**
     * Получить статистику
     */
    public String getStats() {
        int active = getActivePassCount();
        int expired = getExpiredPassCount();
        return "§6Статистика проходок:\n" +
               "§a• Активных: " + active + "\n" +
               "§c• Истекших: " + expired + "\n" +
               "§7• Всего: " + (active + expired);
    }

    /**
     * Проверка истечения проходок
     */
    private void startExpiryCheckTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            int warningDays = plugin.getConfig().getInt("passes.warning-days", 3);

            for (Map.Entry<UUID, PassData> entry : activePasses.entrySet()) {
                PassData pass = entry.getValue();
                UUID uuid = entry.getKey();

                if (!pass.isActive()) {
                    // Проходка истекла
                    activePasses.remove(uuid);

                    // Кикнуть игрока если онлайн
                    Player p = plugin.getServer().getPlayer(uuid);
                    if (p != null) {
                        p.kickPlayer("§c❌ Твоя проходка истекла! Купи новую или привяжи ВК.");
                    }
                    continue;
                }

                // Предупреждение
                if (pass.getRemainingDays() <= warningDays) {
                    Player p = plugin.getServer().getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage("§e⚠️ Твоя проходка истекает через " + pass.getRemainingDays() + " дней!");
                    }
                }
            }
        }, 6000L, 6000L); // Каждые 5 минут
    }

    /**
     * Форматировать дату
     */
    private String formatDate(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new java.util.Date(timestamp));
    }
}
