package ru.example.vkchatmarket.providers;

import ru.example.vkchat.api.MotdProvider;
import ru.example.vkchatmarket.VKChatMarketPlugin;

/**
 * Провайдер MOTD для модуля рынка.
 */
public class MarketMotdProvider implements MotdProvider {
    private final VKChatMarketPlugin plugin;

    public MarketMotdProvider(VKChatMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean hasActiveEvent() {
        return plugin.getMarketService().prices().hasActiveEvent();
    }

    @Override
    public String getMotdLine() {
        if (!hasActiveEvent()) return null;
        String name = plugin.getMarketService().prices().getActiveEventName();
        return "&a&l📈 БИРЖА: На динамическом рынке началось событие " + name + "!";
    }
}
