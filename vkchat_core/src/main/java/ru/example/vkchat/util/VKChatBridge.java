package ru.example.vkchat.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKChatAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Единый фасад для всех вызовов VKChat API.
 * <p>
 * Защищает от NPE, заворачивает все исключения в try-catch,
 * предоставляет единый стиль вызовов для всех модулей.
 * <p>
 * Все методы безопасны для вызова при отсутствии VKChat —
 * возвращают значения по умолчанию (-1, 0, false, null).
 *
 * @see VKChatAPI
 */
public class VKChatBridge {
    private static VKChatPlugin plugin;
    private static boolean available = false;

    /** Инициализация фасада. Вызывается при onLoad() каждого модуля. */
    public static void init() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("VKChat");
        plugin = (p instanceof VKChatPlugin) ? (VKChatPlugin) p : null;
        available = plugin != null && plugin.isEnabled();
    }

    @Nullable
    private static VKChatAPI api() {
        if (!available || plugin == null) return null;
        try { return plugin.getApi(); } catch (Exception e) { return null; }
    }

    /**
     * Получить VK ID игрока.
     *
     * @param p игрок
     * @return VK ID или -1 если не привязан / ошибка
     */
    public static int getLinkedVkId(@Nonnull Player p) {
        if (p == null) return -1;
        VKChatAPI a = api(); if (a == null) return -1;
        try { return a.getLinkedVkId(p); } catch (Exception e) { return -1; }
    }

    /**
     * Получить VK ID по UUID игрока.
     *
     * @param uuid UUID игрока
     * @return VK ID или -1 если не найден / ошибка
     */
    public static int getLinkedVkId(@Nonnull UUID uuid) {
        if (uuid == null) return -1;
        VKChatAPI a = api(); if (a == null) return -1;
        try { return a.getLinkedVkId(uuid); } catch (Exception e) { return -1; }
    }

    /**
     * Получить баланс репутации ВК.
     *
     * @param vkId VK ID игрока
     * @return баланс или 0 при ошибке
     */
    public static int getReputation(int vkId) {
        if (vkId <= 0) return 0;
        VKChatAPI a = api(); if (a == null) return 0;
        try { return a.getReputation(vkId); } catch (Exception e) { return 0; }
    }

    /**
     * Начислить репутацию ВК.
     *
     * @param vkId   VK ID игрока
     * @param amount количество
     * @return true если успешно
     */
    public static boolean addPoints(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.addReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    /**
     * Списать репутацию ВК.
     *
     * @param vkId   VK ID игрока
     * @param amount количество
     * @return true если успешно
     */
    public static boolean takeReputation(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.takeReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    /**
     * Отправить личное сообщение в ВК.
     *
     * @param peerId ID беседы ВК
     * @param text   текст сообщения
     * @return true если успешно
     */
    public static boolean sendMessage(int peerId, @Nonnull String text) {
        if (text == null) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.sendMessage(peerId, text); return true; } catch (Exception e) { return false; }
    }

    /**
     * Отправить сообщение в общий чат ВК.
     *
     * @param text текст сообщения
     * @return true если успешно
     */
    public static boolean sendToMainChat(@Nonnull String text) {
        if (text == null) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.sendToMainChat(text); return true; } catch (Exception e) { return false; }
    }

    /**
     * Отправить клавиатуру в ВК.
     *
     * @param peerId      ID беседы
     * @param text        текст
     * @param keyboardJson JSON клавиатуры
     * @return true если успешно
     */
    public static boolean sendKeyboard(int peerId, @Nonnull String text, @Nullable String keyboardJson) {
        if (text == null || peerId <= 0) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.sendKeyboard(peerId, text, keyboardJson); return true; } catch (Exception e) { return false; }
    }

    /**
     * Получить ID основного чата ВК.
     *
     * @return peer ID или -1
     */
    public static int getMainChatPeerId() {
        if (!available || plugin == null) return -1;
        try { return plugin.getConfig().getInt("vk.peer-id", -1); } catch (Exception e) { return -1; }
    }

    /**
     * Проверить, активна ли Кровавая Луна.
     *
     * @return true если активна
     */
    public static boolean isBloodMoonActive() {
        if (!available) return false;
        try { return plugin.getBloodMoonManager() != null && plugin.getBloodMoonManager().isActive(); } catch (Exception e) { return false; }
    }

    /**
     * Получить UUID игрока по VK ID.
     *
     * @param vkId VK ID
     * @return UUID или null если не найден
     */
    @Nullable
    public static UUID getUuidByVkId(int vkId) {
        if (vkId <= 0) return null;
        VKChatAPI a = api(); if (a == null) return null;
        try { return a.getUuidByVkId(vkId); } catch (Exception e) { return null; }
    }

    /**
     * Проверить, полностью ли авторизован игрок (привязка ВК + вход).
     *
     * @param p игрок
     * @return true если полностью авторизован
     */
    public static boolean isFullyAuthorized(@Nonnull Player p) {
        VKChatAPI a = api(); if (a == null) return false;
        try { return a.isFullyAuthorized(p); } catch (Exception e) { return false; }
    }

    // ═══ ПРОХОДКА (pass) + ЛОКАЛЬНАЯ РЕПУТАЦИЯ ═══

    /**
     * Проверить, имеет ли игрок проходку (vkchat.pass permission).
     *
     * @param p игрок (может быть null)
     * @return true если имеет проходку
     */
    public static boolean hasPass(@Nullable Player p) {
        return p != null && p.hasPermission("vkchat.pass");
    }

    /**
     * Проверить, может ли игрок использовать фичи (привязка ВК или проходка).
     *
     * @param p игрок (может быть null)
     * @return true если может использовать
     */
    public static boolean hasVkOrPass(@Nullable Player p) {
        if (p == null) return false;
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return true;
        return hasPass(p);
    }

    /**
     * Получить локальную репутацию проходчика (из PDC).
     *
     * @param p игрок
     * @return локальная репутация или 0
     */
    public static int getLocalReputation(@Nonnull Player p) {
        if (p == null) return 0;
        try {
            var pdc = p.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
        } catch (Exception e) { return 0; }
    }

    /**
     * Начислить локальную репутацию проходчику.
     *
     * @param p      игрок
     * @param amount количество
     */
    public static void addLocalReputation(@Nonnull Player p, int amount) {
        if (p == null || amount <= 0) return;
        try {
            var pdc = p.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            int cur = pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
            int cap = getLocalRepCap();
            int capped = Math.min(cur + amount, cap > 0 ? cap : Integer.MAX_VALUE);
            pdc.set(key, PersistentDataType.INTEGER, capped);
        } catch (Exception ignored) {}
    }

    private static int getLocalRepCap() {
        try {
            org.bukkit.plugin.Plugin dp = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatDonate");
            if (dp != null && dp.isEnabled()) {
                return dp.getConfig().getInt("pass.local-rep-cap", 50000);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * Списать локальную репутацию проходчика.
     *
     * @param p      игрок
     * @param amount количество
     * @return true если успешно (достаточно средств)
     */
    public static boolean takeLocalReputation(@Nonnull Player p, int amount) {
        if (p == null || amount <= 0) return false;
        try {
            var pdc = p.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            int cur = pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
            if (cur < amount) return false;
            pdc.set(key, PersistentDataType.INTEGER, cur - amount);
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * Получить эффективную репутацию: VK если привязан, локальная если проходка, иначе 0.
     *
     * @param p игрок
     * @return репутация
     */
    public static int getEffectiveRep(@Nonnull Player p) {
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return getReputation(vkId);
        if (hasPass(p)) return getLocalReputation(p);
        return 0;
    }

    /**
     * Начислить эффективную репутацию: VK или локальную (для проходчиков).
     *
     * @param p      игрок
     * @param amount количество
     * @return true если успешно
     */
    public static boolean addEffectiveRep(@Nonnull Player p, int amount) {
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return addPoints(vkId, amount);
        if (hasPass(p)) { addLocalReputation(p, amount); return true; }
        return false;
    }

    /**
     * Списать эффективную репутацию: VK или локальную (для проходчиков).
     *
     * @param p      игрок
     * @param amount количество
     * @return true если успешно
     */
    public static boolean takeEffectiveRep(@Nonnull Player p, int amount) {
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return takeReputation(vkId, amount);
        if (hasPass(p)) return takeLocalReputation(p, amount);
        return false;
    }
}
