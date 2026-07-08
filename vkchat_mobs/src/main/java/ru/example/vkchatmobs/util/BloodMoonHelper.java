package ru.example.vkchatmobs.util;

/**
 * Единая точка проверки состояния Кровавой Луны.
 *
 * FIX #3:  Заменяет 4 дублированных try-catch блока
 *          (MobListener.onMobSpawn, updateNameplate, onMobDeath,
 *           HardcoreMobManager.isBloodMoonLike).
 * IMPROVE #3: Единственный источник истины для Blood Moon state.
 */
public final class BloodMoonHelper {

    private BloodMoonHelper() { /* utility */ }

    /**
     * Проверить, активна ли Кровавая Луна.
     * Делегирует в VKChatBridge, который безопасно обрабатывает NPE.
     */
    public static boolean isBloodMoonActive() {
        return VKChatBridge.isBloodMoonActive();
    }
}
