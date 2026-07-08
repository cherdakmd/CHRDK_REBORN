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
        if (p == null) return -1;
        AuthManager a = auth();
        if (a == null) return -1;
        try { return a.getLinkedVkId(p); } catch (Exception e) { return -1; }
    }

    /** Получить VK ID по UUID. */
    public static int getLinkedVkId(java.util.UUID uuid) {
        if (uuid == null) return -1;
        VKChatAPI a = api();
        if (a == null) return -1;
        try { return a.getLinkedVkId(uuid); } catch (Exception e) { return -1; }
    }

    /** Баланс репутации. */
    public static int getReputation(int vkId) {
        if (vkId <= 0) return 0;
        VKChatAPI a = api();
        if (a == null) return 0;
        try { return a.getReputation(vkId); } catch (Exception e) { return 0; }
    }

    /** Начислить репутацию. */
    public static boolean addReputation(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api();
        if (a == null) return false;
        try { a.addReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    /** Списать репутацию. */
    public static boolean takeReputation(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api();
        if (a == null) return false;
        try { a.takeReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    /** Начислить репутацию (аналог ReputationManager.addPoints). */
    public static boolean addPoints(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api();
        if (a == null) return false;
        try { a.addReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    /** Отправить личное сообщение в ВК. */
    public static boolean sendMessage(int vkId, String text) {
        if (vkId <= 0 || text == null) return false;
        VKChatAPI a = api();
        if (a == null) return false;
        try { a.sendMessage(vkId, text); return true; } catch (Exception e) { return false; }
    }

    /** Отправить в общий чат ВК. */
    public static boolean sendToMainChat(String text) {
        if (text == null) return false;
        VKChatAPI a = api();
        if (a == null) return false;
        try { a.sendToMainChat(text); return true; } catch (Exception e) { return false; }
    }

    /** Кровавая Луна активна. */
    public static boolean isBloodMoonActive() {
        if (!available) return false;
        try { return plugin.getBloodMoonManager() != null && plugin.getBloodMoonManager().isActive(); } catch (Exception e) { return false; }
    }
}
