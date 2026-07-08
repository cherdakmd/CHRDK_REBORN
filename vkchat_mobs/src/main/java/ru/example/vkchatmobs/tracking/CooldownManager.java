package ru.example.vkchatmobs.tracking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulated cooldown & tracking maps extracted from MobListener.
 *
 * FIX #9:  7 Map полей MobListener инкапсулированы в отдельный менеджер.
 * IMPROVE #9: Единая точка очистки, таймеры, антифарм.
 */
public class CooldownManager {

    // --- Миньон-кулдаун (per mob UUID) ---
    private final Map<UUID, Long> minionCooldowns = new ConcurrentHashMap<>();

    // --- Таймеры способностей супер-боссов (per mob UUID string) ---
    private final Map<String, Long> lastSpellTime = new ConcurrentHashMap<>();

    // --- Антифарм супер-боссов ---
    private long lastSuperBossSpawnTime = 0L;
    private final Map<String, Integer> recentSpawnCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> spawnCountResetTimes = new ConcurrentHashMap<>();

    // --- VK-кулдаун сообщений боссов ---
    private final Map<String, Long> vkMessageCooldowns = new ConcurrentHashMap<>();

    // --- Константы ---
    private long superBossCooldownMs = 600000L; // 10 минут
    private long vkMsgCooldownMs = 5000L;       // 5 секунд

    // ═══════════════════════════════════════════
    //  Миньоны
    // ═══════════════════════════════════════════

    public boolean isMinionOnCooldown(UUID mobUuid, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = minionCooldowns.get(mobUuid);
        if (last != null && now - last < cooldownMs) return true;
        minionCooldowns.put(mobUuid, now);
        return false;
    }

    // ═══════════════════════════════════════════
    //  Способности боссов
    // ═══════════════════════════════════════════

    public boolean isSpellOnCooldown(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = lastSpellTime.get(key);
        if (last != null && now - last < cooldownMs) return true;
        lastSpellTime.put(key, now);
        return false;
    }

    public void setSpellTime(String key, long time) {
        lastSpellTime.put(key, time);
    }

    public long getSpellTime(String key) {
        return lastSpellTime.getOrDefault(key, 0L);
    }

    // ═══════════════════════════════════════════
    //  Антифарм супер-боссов
    // ═══════════════════════════════════════════

    public boolean canSpawnSuperBoss() {
        return System.currentTimeMillis() - lastSuperBossSpawnTime >= superBossCooldownMs;
    }

    public void markSuperBossSpawned() {
        lastSuperBossSpawnTime = System.currentTimeMillis();
    }

    /**
     * Проверить, является ли зона мобофабрикой.
     * @return true если спавн разрешён (не мобофабрика)
     */
    public boolean checkAntiFarm(String areaKey) {
        long now = System.currentTimeMillis();

        Integer count = recentSpawnCounts.getOrDefault(areaKey, 0);
        recentSpawnCounts.put(areaKey, count + 1);

        Long resetTime = spawnCountResetTimes.getOrDefault(areaKey, 0L);
        if (now - resetTime > 10000L) {
            recentSpawnCounts.put(areaKey, 1);
            spawnCountResetTimes.put(areaKey, now);
        }

        return recentSpawnCounts.getOrDefault(areaKey, 0) < 15;
    }

    // ═══════════════════════════════════════════
    //  VK-сообщения боссов
    // ═══════════════════════════════════════════

    public boolean canSendVkMessage(String key) {
        long now = System.currentTimeMillis();
        Long last = vkMessageCooldowns.get(key);
        if (last != null && now - last < vkMsgCooldownMs) return false;
        vkMessageCooldowns.put(key, now);
        return true;
    }

    // ═══════════════════════════════════════════
    //  Конфигурация
    // ═══════════════════════════════════════════

    public void setSuperBossCooldownMs(long ms) { this.superBossCooldownMs = ms; }
    public void setVkMsgCooldownMs(long ms) { this.vkMsgCooldownMs = ms; }

    // ═══════════════════════════════════════════
    //  Очистка
    // ═══════════════════════════════════════════

    public void cleanup(long now) {
        lastSpellTime.entrySet().removeIf(e -> now - e.getValue() > 600000);
        vkMessageCooldowns.entrySet().removeIf(e -> now - e.getValue() > 60000);
        minionCooldowns.entrySet().removeIf(e -> now - e.getValue() > 300000);
    }
}
