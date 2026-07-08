package ru.example.vkchatdonate.pass.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.example.vkchatdonate.pass.PassManager;

/**
 * IMPROVE #8: Событие при конвертации проходки в ВК-привязку.
 * Локальная репутация уже перенесена в ВК на момент вызова.
 */
public class PassConvertEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PassManager.PassHolder passHolder;
    private final int vkId;
    private final int transferredRep;

    public PassConvertEvent(Player player, PassManager.PassHolder passHolder,
                            int vkId, int transferredRep) {
        this.player = player;
        this.passHolder = passHolder;
        this.vkId = vkId;
        this.transferredRep = transferredRep;
    }

    public Player getPlayer() { return player; }
    public PassManager.PassHolder getPassHolder() { return passHolder; }
    public int getVkId() { return vkId; }
    public int getTransferredRep() { return transferredRep; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
