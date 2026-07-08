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
        return plugin.getAuthManager().getLinkStorage().getUuid(vkId);
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

    // ==========================================
    //  ДОНАТ-СТАТУСЫ (DonateStatusResolver)
    // ==========================================

    /**
     * Получить ID донат-статуса игрока (spark/flame/star/legend/overlord или null).
     */
    public String getDonateStatus(Player player) {
        return ru.example.vkchat.util.DonateStatusResolver.getStatusId(player);
    }

    /**
     * Скидка на кузню для донатера (0.0 — нет, до 0.65).
     */
    public double getDonateForgeDiscount(Player player) {
        return ru.example.vkchat.util.DonateStatusResolver.getForgeDiscount(player);
    }

    /**
     * Множитель рынка для донатера (1.0 — базовый, до 1.70).
     */
    public double getDonateMarketMultiplier(Player player) {
        return ru.example.vkchat.util.DonateStatusResolver.getMarketMultiplier(player);
    }

    /**
     * Есть ли у игрока донат-статус.
     */
    public boolean hasDonateStatus(Player player) {
        return ru.example.vkchat.util.DonateStatusResolver.hasDonateStatus(player);
    }

    // ==========================================
    //  ПРОФЕССИИ (JobsBridge)
    // ==========================================

    /**
     * Уровень профессии игрока. Возвращает 0 если VKChatJobs не установлен.
     */
    public int getJobLevel(UUID uuid, String job) {
        return ru.example.vkchat.util.JobsBridge.getLevel(uuid, job);
    }

    /**
     * Суммарный уровень всех профессий.
     */
    public int getTotalJobLevel(UUID uuid) {
        return ru.example.vkchat.util.JobsBridge.getTotalLevel(uuid);
    }
}
