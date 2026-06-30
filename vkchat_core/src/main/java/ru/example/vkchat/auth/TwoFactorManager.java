package ru.example.vkchat.auth;

import org.bukkit.entity.Player;
import ru.example.vkchat.VKChatPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер двухфакторной аутентификации
 * Управляет кодами 2FA, попытками и блокировками
 */
public class TwoFactorManager {
    private final VKChatPlugin plugin;
    private final Map<UUID, String> pendingCodes = new ConcurrentHashMap<>();      // UUID -> код
    private final Map<UUID, Long> codeExpiry = new ConcurrentHashMap<>();           // UUID -> время истечения
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();          // UUID -> количество попыток
    private final Map<UUID, Long> lockouts = new ConcurrentHashMap<>();             // UUID -> время окончания блокировки

    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L; // 5 минут

    public TwoFactorManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Сгенерировать и отправить 2FA код
     */
    public boolean trigger2fa(Player p, int vkId) {
        // Проверяем блокировку
        if (isLocked(p.getUniqueId())) {
            long remaining = getLockoutRemaining(p.getUniqueId());
            p.sendMessage("§c🔒 Слишком много неудачных попыток! Подожди " + (remaining / 1000) + " сек.");
            return false;
        }

        // Генерируем код
        int codeLength = plugin.getConfig().getInt("auth.2fa.code-length", 4);
        int min = (int) Math.pow(10, codeLength - 1);
        int max = (int) Math.pow(10, codeLength) - 1;
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(min, max));

        // Сохраняем
        pendingCodes.put(p.getUniqueId(), code);
        long expiryMs = plugin.getConfig().getLong("auth.2fa.expiry-minutes", 5) * 60 * 1000;
        codeExpiry.put(p.getUniqueId(), System.currentTimeMillis() + expiryMs);
        attempts.put(p.getUniqueId(), 0);

        // Отправляем код в ЛС ВК
        try {
            String message = "🔐 Твой код для входа на сервер: " + code + "\n" +
                           "Отправь его в чат Майнкрафта для подтверждения.\n" +
                           "Код действителен " + plugin.getConfig().getInt("auth.2fa.expiry-minutes", 5) + " минут.";
            plugin.getVkManager().sendMessage(vkId, message);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось отправить 2FA код в ВК: " + e.getMessage());
            return false;
        }

        // Уведомляем игрока
        p.sendMessage("§e🔐 Код подтверждения отправлен в твои личные сообщения ВКонтакте!");
        p.sendMessage("§7Введи код в чат для подтверждения входа.");

        return true;
    }

    /**
     * Попробовать подтвердить 2FA код
     */
    public TwoFactorResult confirm2fa(UUID uuid, String code) {
        // Проверяем блокировку
        if (isLocked(uuid)) {
            return TwoFactorResult.LOCKED;
        }

        // Проверяем, есть ли ожидание
        String expectedCode = pendingCodes.get(uuid);
        if (expectedCode == null) {
            return TwoFactorResult.NO_PENDING;
        }

        // Проверяем истечение
        Long expiry = codeExpiry.get(uuid);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            cleanup(uuid);
            return TwoFactorResult.EXPIRED;
        }

        // Проверяем код
        if (!expectedCode.equals(code)) {
            // Неверный код
            int currentAttempts = attempts.getOrDefault(uuid, 0) + 1;
            attempts.put(uuid, currentAttempts);

            if (currentAttempts >= MAX_ATTEMPTS) {
                // Блокировка
                lockouts.put(uuid, System.currentTimeMillis() + LOCKOUT_DURATION_MS);
                cleanup(uuid);
                return TwoFactorResult.LOCKED;
            }

            return TwoFactorResult.WRONG_CODE;
        }

        // Успех!
        cleanup(uuid);
        return TwoFactorResult.SUCCESS;
    }

    /**
     * Проверить, ожидает ли игрок 2FA
     */
    public boolean isWaiting2fa(UUID uuid) {
        return pendingCodes.containsKey(uuid);
    }

    /**
     * Получить оставшееся время блокировки
     */
    public long getLockoutRemaining(UUID uuid) {
        Long lockoutEnd = lockouts.get(uuid);
        if (lockoutEnd == null) return 0;
        return Math.max(0, lockoutEnd - System.currentTimeMillis());
    }

    /**
     * Проверить, заблокирован ли игрок
     */
    public boolean isLocked(UUID uuid) {
        Long lockoutEnd = lockouts.get(uuid);
        if (lockoutEnd == null) return false;
        if (System.currentTimeMillis() > lockoutEnd) {
            lockouts.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Получить оставшиеся попытки
     */
    public int getRemainingAttempts(UUID uuid) {
        int used = attempts.getOrDefault(uuid, 0);
        return Math.max(0, MAX_ATTEMPTS - used);
    }

    /**
     * Проверить, истёк ли код
     */
    public boolean isCodeExpired(UUID uuid) {
        Long expiry = codeExpiry.get(uuid);
        return expiry == null || System.currentTimeMillis() > expiry;
    }

    /**
     * Очистить данные 2FA
     */
    private void cleanup(UUID uuid) {
        pendingCodes.remove(uuid);
        codeExpiry.remove(uuid);
        attempts.remove(uuid);
    }

    /**
     * Очистить при выходе игрока
     */
    public void onPlayerQuit(UUID uuid) {
        cleanup(uuid);
    }

    /**
     * Получить количество ожидающих 2FA
     */
    public int getPendingCount() {
        return pendingCodes.size();
    }

    /**
     * Периодическая очистка истёкших кодов
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();

            // Очистка истёкших кодов
            codeExpiry.entrySet().removeIf(entry -> {
                if (now > entry.getValue()) {
                    pendingCodes.remove(entry.getKey());
                    attempts.remove(entry.getKey());
                    return true;
                }
                return false;
            });

            // Очистка истёкших блокировок
            lockouts.entrySet().removeIf(entry -> now > entry.getValue());
        }, 6000L, 6000L); // Каждые 5 минут
    }

    // Результат 2FA
    public enum TwoFactorResult {
        SUCCESS,         // Код верный
        WRONG_CODE,      // Неверный код
        EXPIRED,         // Код истёк
        LOCKED,          // Заблокирован
        NO_PENDING       // Нет ожидания
    }
}
