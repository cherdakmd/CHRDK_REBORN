package ru.example.vkchat.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается когда игрок присоединяется к нации.
 * Модули могут подписаться для приветствий, бонусов.
 */
public class NationJoinEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String nationId;

    public NationJoinEvent(Player player, String nationId) {
        this.player = player;
        this.nationId = nationId;
    }

    public Player getPlayer() { return player; }
    public String getNationId() { return nationId; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
