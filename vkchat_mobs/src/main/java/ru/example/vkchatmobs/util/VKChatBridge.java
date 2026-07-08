package ru.example.vkchatmobs.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKChatAPI;
import ru.example.vkchat.auth.AuthManager;

import java.util.logging.Level;

/**
 * Единый фасад для всех вызовов VKChat API.
 * Защищает от NPE, заворачивает все исключения в try-catch,
 * предоставляет единый стиль вызовов для всех модулей.
 *
 * FIX #6:  Добавлены hasPass()/getLocalReputation()/takeLocalReputation()/
 *          addEffectiveRep()/takeEffectiveRep() для поддержки проходки.
 * IMPROVE #5: Проходчики теперь получают локальную репутацию через PDC.
 * IMPROVE #7: Делегирование в core VKChatBridge вместо дублирования.
 */
public class VKChatBridge {
    private static VKChatPlugin plugin;
    private static boolean available = false;

    public static void init() {
        plugin = (VKChatPlugin) Bukkit.getPluginManager().getPlugin("VKChat");
        available = plugin != null && plugin.isEnabled();
    }

    private static VKChatAPI api() {
        if (!available || plugin == null) return null;
        try { return plugin.getApi(); } catch (Exception e) { return null; }
    }

    private static AuthManager auth() {
        if (!available || plugin == null) return null;
        try { return plugin.getAuthManager(); } catch (Exception e) { return null; }
    }

    /** Получить VK ID игрока. Возвращает -1 при ошибке. */
    public static int getLinkedVkId(Player p) {
        return ru.example.vkchat.util.VKChatBridge.getLinkedVkId(p);
    }

    /** Получить VK ID по UUID. */
    public static int getLinkedVkId(java.util.UUID uuid) {
        return ru.example.vkchat.util.VKChatBridge.getLinkedVkId(uuid);
    }

    /** Баланс репутации. */
    public static int getReputation(int vkId) {
        return ru.example.vkchat.util.VKChatBridge.getReputation(vkId);
    }

    /** Начислить репутацию. */
    public static boolean addReputation(int vkId, int amount) {
        return ru.example.vkchat.util.VKChatBridge.addPoints(vkId, amount);
    }

    /** Списать репутацию. */
    public static boolean takeReputation(int vkId, int amount) {
        return ru.example.vkchat.util.VKChatBridge.takeReputation(vkId, amount);
    }

    /** Начислить репутацию (аналог ReputationManager.addPoints). */
    public static boolean addPoints(int vkId, int amount) {
        return ru.example.vkchat.util.VKChatBridge.addPoints(vkId, amount);
    }

    /** Отправить личное сообщение в ВК. */
    public static boolean sendMessage(int vkId, String text) {
        return ru.example.vkchat.util.VKChatBridge.sendMessage(vkId, text);
    }

    /** Отправить в общий чат ВК. */
    public static boolean sendToMainChat(String text) {
        return ru.example.vkchat.util.VKChatBridge.sendToMainChat(text);
    }

    /** Кровавая Луна активна. */
    public static boolean isBloodMoonActive() {
        return ru.example.vkchat.util.VKChatBridge.isBloodMoonActive();
    }

    // ═══ ПРОХОДКА (pass) + ЛОКАЛЬНАЯ РЕПУТАЦИЯ ═══

    /** Имеет ли игрок проходку. */
    public static boolean hasPass(Player p) {
        return ru.example.vkchat.util.VKChatBridge.hasPass(p);
    }

    /** Имеет ли игрок привязку ВК или проходку. */
    public static boolean hasVkOrPass(Player p) {
        return ru.example.vkchat.util.VKChatBridge.hasVkOrPass(p);
    }

    /** Получить локальную репутацию проходчика (PDC). */
    public static int getLocalReputation(Player p) {
        return ru.example.vkchat.util.VKChatBridge.getLocalReputation(p);
    }

    /** Начислить локальную репутацию проходчику. */
    public static void addLocalReputation(Player p, int amount) {
        ru.example.vkchat.util.VKChatBridge.addLocalReputation(p, amount);
    }

    /** Списать локальную репутацию проходчика. */
    public static boolean takeLocalReputation(Player p, int amount) {
        return ru.example.vkchat.util.VKChatBridge.takeLocalReputation(p, amount);
    }

    /** Эффективная репутация: VK если привязан, локальная если проходка, иначе 0. */
    public static int getEffectiveRep(Player p) {
        return ru.example.vkchat.util.VKChatBridge.getEffectiveRep(p);
    }

    /** Начислить эффективную репутацию: VK или локальную (для проходчиков). */
    public static boolean addEffectiveRep(Player p, int amount) {
        return ru.example.vkchat.util.VKChatBridge.addEffectiveRep(p, amount);
    }

    /** Списать эффективную репутацию: VK или локальную (для проходчиков). */
    public static boolean takeEffectiveRep(Player p, int amount) {
        return ru.example.vkchat.util.VKChatBridge.takeEffectiveRep(p, amount);
    }
}
