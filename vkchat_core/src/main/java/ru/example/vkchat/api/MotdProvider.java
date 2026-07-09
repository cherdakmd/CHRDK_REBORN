package ru.example.vkchat.api;

/**
 * API для предоставления данных MOTD от других модулей.
 * Модули реализуют этот интерфейс и регистрируются через MotdProviderRegistry.
 */
public interface MotdProvider {
    /**
     * @return Приоритет провайдера (больше = выше). Wrath=100, Cataclysm=90, Airdrop=80, Market=50, Gear=40
     */
    int getPriority();

    /**
     * @return true если есть активное событие для отображения в MOTD
     */
    boolean hasActiveEvent();

    /**
     * @return Строка для MOTD (с цветовыми кодами &) или null если нет события
     */
    String getMotdLine();
}
