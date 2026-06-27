package ru.example.vkchat.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается, когда игрок успешно привязывает аккаунт к ВКонтакте
 */
public class VKPlayerLinkEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final int vkId;

    public VKPlayerLinkEvent(Player player, int vkId) {
        super(true); // Асинхронно
        this.player = player;
        this.vkId = vkId;
    }

    public Player getPlayer() { return player; }
    public int getVkId() { return vkId; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
