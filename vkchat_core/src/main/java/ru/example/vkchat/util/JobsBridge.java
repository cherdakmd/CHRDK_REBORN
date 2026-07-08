package ru.example.vkchat.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JobsBridge — типизированный фасад для доступа к VKChatJobs API.
 *
 * Заменяет 9+ reflection-вызовов getJobsDataManager()/getLevel()
 * едиными статическими методами.
 */
public final class JobsBridge {

    private static Object dataManager;
    private static boolean available = false;

    // Кэш уровней (TTL 30 секунд)
    private static final ConcurrentHashMap<String, CachedLevel> levelCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000L;

    private static final class CachedLevel {
        final int level;
        final long timestamp;
        CachedLevel(int level) { this.level = level; this.timestamp = System.currentTimeMillis(); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }

    private JobsBridge() {}

    /** Инициализация. Вызывать после загрузки плагинов. */
    public static void init() {
        try {
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null && jobsPlugin.isEnabled()) {
                dataManager = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                available = dataManager != null;
            }
        } catch (Exception e) {
            available = false;
        }
    }

    /** VKChatJobs установлен и доступен. */
    public static boolean isAvailable() {
        return available;
    }

    /** Получить уровень профессии игрока. Возвращает 0 при ошибке. */
    public static int getLevel(UUID uuid, String job) {
        if (!available || uuid == null || job == null) return 0;

        String key = uuid.toString() + ":" + job;
        CachedLevel cached = levelCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.level;

        try {
            int level = (int) dataManager.getClass()
                    .getMethod("getLevel", UUID.class, String.class)
                    .invoke(dataManager, uuid, job);
            levelCache.put(key, new CachedLevel(level));
            return level;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Получить уровень профессии игрока (по Player). */
    public static int getLevel(Player player, String job) {
        return player != null ? getLevel(player.getUniqueId(), job) : 0;
    }

    /** Суммарный уровень всех профессий. */
    public static int getTotalLevel(UUID uuid) {
        return getTotalLevel(uuid, "miner", "woodcutter", "farmer", "alchemist", "blacksmith");
    }

    /** Суммарный уровень указанных профессий. */
    public static int getTotalLevel(UUID uuid, String... jobs) {
        int total = 0;
        for (String job : jobs) {
            total += getLevel(uuid, job);
        }
        return total;
    }

    /** Суммарный уровень всех профессий (по Player). */
    public static int getTotalLevel(Player player) {
        return player != null ? getTotalLevel(player.getUniqueId()) : 0;
    }

    /** Проверить, имеет ли игрок навык в профессии. */
    public static boolean hasSkill(UUID uuid, String job, String skill) {
        if (!available || uuid == null || job == null || skill == null) return false;
        try {
            return (boolean) dataManager.getClass()
                    .getMethod("hasSkill", UUID.class, String.class, String.class)
                    .invoke(dataManager, uuid, job, skill);
        } catch (Exception e) {
            return false;
        }
    }

    /** Получить общий уровень игрока (старый API). */
    public static int getTotalLevelPlayer(Player player) {
        if (!available || player == null) return 0;
        try {
            org.bukkit.plugin.Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobsPlugin != null) {
                Object dm = jobsPlugin.getClass().getMethod("getJobsDataManager").invoke(jobsPlugin);
                return (int) dm.getClass().getMethod("getTotalLevel", Player.class).invoke(dm, player);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Очистить кэш. */
    public static void clearCache() {
        levelCache.clear();
    }

    /** Очистить устаревшие записи кэша. */
    public static void cleanupCache() {
        levelCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
