package ru.example.vkchatgear.providers;

import ru.example.vkchat.api.MotdProvider;
import ru.example.vkchatgear.VKChatGearPlugin;

/**
 * Провайдер MOTD для модуля кузни (магические события).
 */
public class GearMotdProvider implements MotdProvider {
    private final VKChatGearPlugin plugin;

    public GearMotdProvider(VKChatGearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean hasActiveEvent() {
        String name = plugin.getActiveMagicEventName();
        long expire = plugin.getActiveMagicEventExpireTime();
        return name != null && System.currentTimeMillis() < expire;
    }

    @Override
    public String getMotdLine() {
        if (!hasActiveEvent()) return null;
        String name = plugin.getActiveMagicEventName();
        return "&5&l🔮 МАГИЯ: В магазине рун активировано событие &d&l" + name + "&5&l!";
    }
}
