package ru.example.vkchat.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKChatAPI;

import java.util.UUID;

/**
 * Единый фасад для всех вызовов VKChat API.
 * Защищает от NPE, заворачивает все исключения в try-catch.
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

    public static int getLinkedVkId(Player p) {
        if (p == null) return -1;
        VKChatAPI a = api(); if (a == null) return -1;
        try { return a.getLinkedVkId(p); } catch (Exception e) { return -1; }
    }

    public static int getLinkedVkId(UUID uuid) {
        if (uuid == null) return -1;
        VKChatAPI a = api(); if (a == null) return -1;
        try { return a.getLinkedVkId(uuid); } catch (Exception e) { return -1; }
    }

    public static int getReputation(int vkId) {
        if (vkId <= 0) return 0;
        VKChatAPI a = api(); if (a == null) return 0;
        try { return a.getReputation(vkId); } catch (Exception e) { return 0; }
    }

    public static boolean addPoints(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.addReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    public static boolean takeReputation(int vkId, int amount) {
        if (vkId <= 0 || amount <= 0) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.takeReputation(vkId, amount); return true; } catch (Exception e) { return false; }
    }

    public static boolean sendMessage(int peerId, String text) {
        if (text == null) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.sendMessage(peerId, text); return true; } catch (Exception e) { return false; }
    }

    public static boolean sendToMainChat(String text) {
        if (text == null) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.sendToMainChat(text); return true; } catch (Exception e) { return false; }
    }

    public static boolean sendKeyboard(int peerId, String text, String keyboardJson) {
        if (text == null || peerId <= 0) return false;
        VKChatAPI a = api(); if (a == null) return false;
        try { a.sendKeyboard(peerId, text, keyboardJson); return true; } catch (Exception e) { return false; }
    }

    public static int getMainChatPeerId() {
        if (!available || plugin == null) return -1;
        try { return plugin.getConfig().getInt("vk.peer-id", -1); } catch (Exception e) { return -1; }
    }

    public static boolean isBloodMoonActive() {
        if (!available) return false;
        try { return plugin.getBloodMoonManager() != null && plugin.getBloodMoonManager().isActive(); } catch (Exception e) { return false; }
    }

    // ═══ ПРОХОДКА (pass) + ЛОКАЛЬНАЯ РЕПУТАЦИЯ ═══

    public static boolean hasPass(Player p) {
        return p != null && p.hasPermission("vkchat.pass");
    }

    public static boolean hasVkOrPass(Player p) {
        if (p == null) return false;
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return true;
        return hasPass(p);
    }

    public static int getLocalReputation(Player p) {
        if (p == null) return 0;
        try {
            var pdc = p.getPersistentDataContainer();
            var key = new org.bukkit.NamespacedKey("vkchat", "local_rep");
            return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
        } catch (Exception e) { return 0; }
    }

    public static void addLocalReputation(Player p, int amount) {
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

    public static boolean takeLocalReputation(Player p, int amount) {
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

    public static int getEffectiveRep(Player p) {
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return getReputation(vkId);
        if (hasPass(p)) return getLocalReputation(p);
        return 0;
    }

    public static boolean addEffectiveRep(Player p, int amount) {
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return addPoints(vkId, amount);
        if (hasPass(p)) { addLocalReputation(p, amount); return true; }
        return false;
    }

    public static boolean takeEffectiveRep(Player p, int amount) {
        int vkId = getLinkedVkId(p);
        if (vkId != -1) return takeReputation(vkId, amount);
        if (hasPass(p)) return takeLocalReputation(p, amount);
        return false;
    }
}
