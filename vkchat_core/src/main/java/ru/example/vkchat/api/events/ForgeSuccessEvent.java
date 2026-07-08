package ru.example.vkchat.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается когда игрок успешно завершает заточку предмета в кузнице.
 * Модули могут подписаться для наград, оповещений, логирования.
 */
public class ForgeSuccessEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final int newLevel;
    private final boolean success;

    public ForgeSuccessEvent(Player player, int newLevel, boolean success) {
        this.player = player;
        this.newLevel = newLevel;
        this.success = success;
    }

    public Player getPlayer() { return player; }
    public int getNewLevel() { return newLevel; }
    public boolean isSuccess() { return success; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
