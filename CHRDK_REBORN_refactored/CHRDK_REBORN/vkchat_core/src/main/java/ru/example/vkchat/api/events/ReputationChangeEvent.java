package ru.example.vkchat.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается при любом изменении баланса репутации ВКонтакте у игрока
 */
public class ReputationChangeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final int vkId;
    private final int oldAmount;
    private final int newAmount;

    public ReputationChangeEvent(int vkId, int oldAmount, int newAmount) {
        super(true); // Асинхронно
        this.vkId = vkId;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
    }

    public int getVkId() { return vkId; }
    public int getOldAmount() { return oldAmount; }
    public int getNewAmount() { return newAmount; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
