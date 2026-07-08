package ru.example.vkchat.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается, когда пользователь отправляет ЛЮБОЕ сообщение, начинающееся с "!"
 * в личные сообщения боту ВКонтакте или в основную беседу.
 * Идеально для добавления новых команд ВКонтакте через сторонние плагины.
 */
public class VKCommandEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    
    private final int peerId;
    private final int senderVkId;
    private final String command; // Команда (например, "!профиль")
    private final String[] args;  // Аргументы команды
    private boolean cancelled = false;

    public VKCommandEvent(int peerId, int senderVkId, String command, String[] args) {
        super(true); // Async
        this.peerId = peerId;
        this.senderVkId = senderVkId;
        this.command = command.toLowerCase();
        this.args = args;
    }

    public int getPeerId() { return peerId; }
    public int getSenderVkId() { return senderVkId; }
    public String getCommand() { return command; }
    public String[] getArgs() { return args; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
