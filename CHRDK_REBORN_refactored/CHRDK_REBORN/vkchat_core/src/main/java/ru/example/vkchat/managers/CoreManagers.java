package ru.example.vkchat.managers;

import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.auth.AuthManager;
import ru.example.vkchat.managers.ChatManager;
import ru.example.vkchat.stats.StatsManager;
import ru.example.vkchat.reputation.ReputationManager;

/**
 * Центральный контейнер для основных менеджеров плагина.
 * Позволяет разгрузить VKChatPlugin и улучшить организацию кода.
 */
public class CoreManagers {

    private final VKChatPlugin plugin;

    private AuthManager authManager;
    private ChatManager chatManager;
    private StatsManager statsManager;
    private ReputationManager reputationManager;

    public CoreManagers(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализирует все основные менеджеры
     */
    public void initialize() {
        this.authManager = new AuthManager(plugin);
        this.chatManager = new ChatManager(plugin);
        this.statsManager = new StatsManager(plugin);
        this.reputationManager = new ReputationManager(plugin);

        plugin.getLogger().info("Core Managers initialized: Auth, Chat, Stats, Reputation");
    }

    // === Getters ===

    public AuthManager getAuthManager() {
        return authManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public ReputationManager getReputationManager() {
        return reputationManager;
    }

    /**
     * Перезагрузка менеджеров (для будущих нужд)
     */
    public void reload() {
        plugin.getLogger().info("Core Managers reloaded");
    }
}
