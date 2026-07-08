package ru.example.vkchatdonate.pass.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.example.vkchatdonate.pass.PassManager;

/**
 * IMPROVE #8: Событие при истечении проходки.
 * Срабатывает когда grace-период закончился и проходка полностью истекла.
 */
public class PassExpireEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PassManager.PassHolder passHolder;

    public PassExpireEvent(Player player, PassManager.PassHolder passHolder) {
        this.player = player;
        this.passHolder = passHolder;
    }

    public Player getPlayer() { return player; }
    public PassManager.PassHolder getPassHolder() { return passHolder; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
