package ru.example.vkchat.vk;

import ru.example.vkchat.VKChatPlugin;

/**
 * Центральный менеджер всех VK-фич.
 * Объединяет RiddleManager, MiniGamesManager и GamesManager.
 * Упрощает VKChatPlugin и улучшает масштабируемость.
 */
public class VKFeaturesManager {

    private final VKChatPlugin plugin;

    private RiddleManager riddleManager;
    private MiniGamesManager miniGamesManager;
    private GamesManager gamesManager;

    public VKFeaturesManager(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализирует все VK-фичи
     */
    public void initialize() {
        this.riddleManager = new RiddleManager(plugin);
        this.miniGamesManager = new MiniGamesManager(plugin);
        this.gamesManager = new GamesManager(plugin);

        plugin.getLogger().info("VK Features initialized: Riddles, MiniGames, Games");
    }

    // === Getters ===

    public RiddleManager getRiddleManager() {
        return riddleManager;
    }

    public MiniGamesManager getMiniGamesManager() {
        return miniGamesManager;
    }

    public GamesManager getGamesManager() {
        return gamesManager;
    }

    /**
     * Можно будет расширять (например, добавить VKShop, VKStats и т.д.)
     */
    public void reload() {
        // TODO: Перезагрузка конфигов VK-фич
        plugin.getLogger().info("VK Features reloaded");
    }
}
