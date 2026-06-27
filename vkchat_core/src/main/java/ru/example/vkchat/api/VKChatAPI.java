package ru.example.vkchat.api;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import java.util.UUID;

public class VKChatAPI {
    private final VKChatPlugin plugin;

    public VKChatAPI(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    //  ВЗАИМОДЕЙСТВИЕ С ВКОНТАКТЕ
    // ==========================================

    /**
     * Отправить сообщение в главную беседу ВКонтакте
     * @param text Текст сообщения
     */
    public void sendToMainChat(String text) {
        plugin.getVkManager().sendToMainChat(text);
    }

    /**
     * Отправить сообщение в конкретную беседу или ЛС ВКонтакте
     * @param peerID ID чата/пользователя ВК
     * @param text Текст сообщения
     */
    public void sendMessage(int peerID, String text) {
        plugin.getVkManager().sendMessage(peerID, text);
    }

    /**
     * Отправить сообщение с прикрепленной клавиатурой ВКонтакте
     * @param peerID ID чата/пользователя ВК
     * @param text Текст сообщения
     * @param jsonKeyboard JSON-строка клавиатуры ВК
     */
    public void sendKeyboard(int peerID, String text, String jsonKeyboard) {
        plugin.getVkManager().sendKeyboard(peerID, text, jsonKeyboard); 
    }

    // ==========================================
    //  РЕПУТАЦИЯ ВКОНТАКТЕ
    // ==========================================

    /**
     * Получить текущий баланс репутации игрока
     * @param vkId ID пользователя ВК
     * @return Количество репутации (очков)
     */
    public int getReputation(int vkId) {
        return plugin.getReputationManager().getPoints(vkId);
    }

    /**
     * Добавить очки репутации
     */
    public void addReputation(int vkId, int amount) {
        plugin.getReputationManager().addPoints(vkId, amount);
    }

    /**
     * Забрать очки репутации
     */
    public void takeReputation(int vkId, int amount) {
        plugin.getReputationManager().deductPoints(vkId, amount);
    }

    /**
     * Узнать место в рейтинге чата ВКонтакте
     */
    public int getReputationRank(int vkId) {
        return plugin.getReputationManager().getRank(vkId);
    }

    // ==========================================
    //  ПРИВЯЗКА АККАУНТОВ
    // ==========================================

    /**
     * Получить VK ID, привязанный к игроку (возвращает -1, если не привязан)
     */
    public int getLinkedVkId(Player player) {
        return plugin.getAuthManager().getLinkedVkId(player);
    }
    
    public int getLinkedVkId(UUID uuid) {
        return plugin.getAuthManager().getLinkedVkId(uuid);
    }

    /**
     * Получить UUID игрока по его VK ID (возвращает null, если не привязан)
     */
    public UUID getUuidByVkId(int vkId) {
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
            java.sql.PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?");
            ps.setInt(1, vkId);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Получить игрока в Minecraft по его VK ID (возвращает null, если не найден или оффлайн)
     */
    public Player getPlayerByVkId(int vkId) {
        UUID uuid = getUuidByVkId(vkId);
        return uuid != null ? org.bukkit.Bukkit.getPlayer(uuid) : null;
    }

    /**
     * Проверить, полностью ли авторизован игрок (привязал ВК + вошел по паролю)
     */
    public boolean isFullyAuthorized(Player player) {
        return plugin.getAuthManager().isFullyAuthorized(player);
    }

    // ==========================================
    //  ИГРОВАЯ СТАТИСТИКА (STATS)
    // ==========================================

    public int getKills(UUID uuid) {
        return plugin.getStatsManager().getKills(uuid);
    }

    public int getDeaths(UUID uuid) {
        return plugin.getStatsManager().getDeaths(uuid);
    }

    public int getBlocksBroken(UUID uuid) {
        return plugin.getStatsManager().getBlocks(uuid);
    }

    public int getAchievements(UUID uuid) {
        return plugin.getStatsManager().getAchievements(uuid);
    }
    
    public int getServerRank(UUID uuid) {
        return plugin.getStatsManager().getRank(uuid);
    }
}
