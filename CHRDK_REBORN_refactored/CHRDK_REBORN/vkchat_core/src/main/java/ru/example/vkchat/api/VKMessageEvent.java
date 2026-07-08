package ru.example.vkchat.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VKMessageEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final int peer;
    private final int senderId;
    private final String message;
    private boolean cancelled = false;

    public VKMessageEvent(int peer, int senderId, String message) {
        super(true); // Асинхронно
        this.peer = peer;
        this.senderId = senderId;
        this.message = message;
    }

    public int getPeer() { return peer; }
    public int getSenderId() { return senderId; }
    public String getMessage() { return message; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}