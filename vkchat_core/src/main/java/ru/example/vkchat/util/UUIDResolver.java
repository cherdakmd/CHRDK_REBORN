package ru.example.vkchat.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Безопасное разрешение имён игроков в OfflinePlayer.
 * Предотвращает спуфинг через Bukkit.getOfflinePlayer(String).
 *
 * FIX: Bukkit.getOfflinePlayer(String) создаёт фейковые OfflinePlayer
 * для ников, которые никогда не заходили на сервер. Используй этот
 * класс вместо прямых вызовов getOfflinePlayer(String).
 */
public final class UUIDResolver {

    private static final Map<String, CacheEntry> nameCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 минут

    private UUIDResolver() {}

    private static final class CacheEntry {
        final UUID uuid;
        final long timestamp;
        CacheEntry(UUID uuid) {
            this.uuid = uuid;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Безопасно получить OfflinePlayer по нику.
     * 1. Проверяет онлайн-игроков
     * 2. Ищет в файловых данных сервера
     * 3. Валидирует через hasPlayedBefore()
     *
     * @param name ник игрока (регистрозависимый)
     * @return OfflinePlayer или null если игрок никогда не заходил
     */
    public static OfflinePlayer resolve(String name) {
        if (name == null || name.isEmpty()) return null;

        // 1. Онлайн-игрок — самый надёжный способ
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // 2. Кеш (если не протух)
        CacheEntry cached = nameCache.get(name.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            return Bukkit.getOfflinePlayer(cached.uuid);
        }

        // 3. Файловые данные сервера
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op != null && op.hasPlayedBefore()) {
            nameCache.put(name.toLowerCase(), new CacheEntry(op.getUniqueId()));
            return op;
        }

        // Игрок никогда не заходил — возвращаем null
        return null;
    }

    /**
     * Resolve by UUID — безопасно, без кеша.
     */
    public static OfflinePlayer resolve(UUID uuid) {
        if (uuid == null) return null;
        return Bukkit.getOfflinePlayer(uuid);
    }

    /**
     * Resolve и получить имя с fallback.
     */
    public static String resolveName(UUID uuid) {
        if (uuid == null) return "???";
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    /**
     * Resolve и получить имя с fallback (по нику).
     */
    public static String resolveName(String name) {
        OfflinePlayer op = resolve(name);
        if (op == null) return name;
        String resolved = op.getName();
        return resolved != null ? resolved : name;
    }

    /**
     * Очистить кеш (вызывать при перезагрузке плагина).
     */
    public static void clearCache() {
        nameCache.clear();
    }
}
