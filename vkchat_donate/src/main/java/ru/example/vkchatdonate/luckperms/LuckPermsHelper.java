package ru.example.vkchatdonate.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.example.vkchatdonate.VKChatDonatePlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LuckPermsHelper — централизованный доступ к LuckPerms API.
 *
 * FIX #1: Все LP-опрации в одном месте (раньше были размазаны по DonateManager)
 * FIX #2: Используем LP API вместо dispatchCommand("lp ...") где возможно
 * FIX #3: Async загрузка User (LP API требует этого для оффлайн-игроков)
 */
public final class LuckPermsHelper {

    private static LuckPerms api;

    private LuckPermsHelper() {}

    public static boolean initialize() {
        try {
            api = Bukkit.getServicesManager().load(LuckPerms.class);
            return api != null;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return api != null;
    }

    // ═══════════════════════════════════════
    // ПОЛУЧЕНИЕ USER
    // ═══════════════════════════════════════

    /**
     * Получить LP User для онлайн-игрока (синхронно из кеша).
     */
    public static User getUser(Player player) {
        if (api == null) return null;
        return api.getUserManager().getUser(player.getUniqueId());
    }

    /**
     * Получить LP User для оффлайн-игрока (асинхронно).
     */
    public static CompletableFuture<User> loadUser(OfflinePlayer player) {
        if (api == null) return CompletableFuture.completedFuture(null);
        UserManager um = api.getUserManager();
        User cached = um.getUser(player.getUniqueId());
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return um.loadUser(player.getUniqueId());
    }

    // ═══════════════════════════════════════
    // ПРАВА (PERMISSIONS)
    // ═══════════════════════════════════════

    /**
     * Выдать временное право (API, без dispatch command).
     */
    public static boolean setTempPermission(UUID uuid, String permission, boolean value, long durationSeconds) {
        if (api == null) return false;
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return false;

        Node node = Node.builder(permission)
                .value(value)
                .expiry(java.time.Duration.ofSeconds(durationSeconds))
                .build();
        user.data().add(node);
        api.getUserManager().saveUser(user);
        return true;
    }

    /**
     * Продлить временное право (accumulate — добавляет время к текущему).
     */
    public static boolean extendTempPermission(UUID uuid, String permission, long additionalSeconds) {
        if (api == null) return false;
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return false;

        // Найти текущий узел и продлить
        for (Node existing : user.getNodes()) {
            if (existing.getKey().equals(permission) && existing.hasExpiry()) {
                long currentExpiry = existing.getExpiry().toEpochMilli() / 1000;
                long now = System.currentTimeMillis() / 1000;
                long remaining = Math.max(0, currentExpiry - now);
                long newDuration = remaining + additionalSeconds;

                user.data().remove(existing);
                Node renewed = Node.builder(permission)
                        .value(true)
                        .expiry(java.time.Duration.ofSeconds(newDuration))
                        .build();
                user.data().add(renewed);
                api.getUserManager().saveUser(user);
                return true;
            }
        }

        // Нет существующего — создаём новое
        return setTempPermission(uuid, permission, true, additionalSeconds);
    }

    /**
     * Удалить право.
     */
    public static boolean unsetPermission(UUID uuid, String permission) {
        if (api == null) return false;
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return false;

        user.data().remove(Node.builder(permission).build());
        api.getUserManager().saveUser(user);
        return true;
    }

    // ═══════════════════════════════════════
    // ГРУППЫ
    // ═══════════════════════════════════════

    /**
     * Добавить игрока в группу.
     */
    public static boolean addToGroup(UUID uuid, String group) {
        if (api == null) return false;
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return false;

        Node node = Node.builder("group." + group).build();
        user.data().add(node);
        api.getUserManager().saveUser(user);
        return true;
    }

    /**
     * Удалить игрока из группы.
     */
    public static boolean removeFromGroup(UUID uuid, String group) {
        if (api == null) return false;
        User user = api.getUserManager().getUser(uuid);
        if (user == null) return false;

        user.data().remove(Node.builder("group." + group).build());
        api.getUserManager().saveUser(user);
        return true;
    }

    // ═══════════════════════════════════════
    // ОСТАТОК ДНЕЙ
    // ═══════════════════════════════════════

    /**
     * Получить количество оставшихся дней для права.
     * FIX #3: Чистый LP API вместо reflection.
     */
    public static long getDaysLeft(Player player, String permission) {
        if (api == null) return 30;
        User user = getUser(player);
        if (user == null) return 30;

        for (Node node : user.getNodes()) {
            if (node.getKey().equals(permission) && node.hasExpiry()) {
                long expiryMs = node.getExpiry().toEpochMilli();
                long secLeft = (expiryMs - System.currentTimeMillis()) / 1000;
                return Math.max(0, secLeft / 86400);
            }
        }
        return 30;
    }

    /**
     * Fallback: dispatch LP command (для оффлайн-игроков где API не работает).
     */
    public static void dispatchCommand(String command) {
        VKChatDonatePlugin instance = VKChatDonatePlugin.getInstance();
        if (instance == null) return;
        Bukkit.getScheduler().runTask(instance, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }
}
