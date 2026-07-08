package ru.example.vkchatdonate.pass.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.example.vkchatdonate.pass.PassManager;

/**
 * IMPROVE #8: Событие при выдаче проходки.
 * Другие плагины могут слушать это событие для дополнительных действий.
 */
public class PassGrantEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PassManager.PassHolder passHolder;

    public PassGrantEvent(Player player, PassManager.PassHolder passHolder) {
        this.player = player;
        this.passHolder = passHolder;
    }

    public Player getPlayer() { return player; }
    public PassManager.PassHolder getPassHolder() { return passHolder; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
