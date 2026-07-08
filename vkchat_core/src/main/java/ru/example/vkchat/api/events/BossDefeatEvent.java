package ru.example.vkchat.api.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Вызывается когда Мировой Супер-Босс повержен.
 * Модули (events, chat, nations) могут подписаться для оповещений, наград.
 */
public class BossDefeatEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player killer;
    private final LivingEntity bossEntity;
    private final String bossType;
    private final boolean isSuperBoss;

    public BossDefeatEvent(Player killer, LivingEntity bossEntity, String bossType, boolean isSuperBoss) {
        this.killer = killer;
        this.bossEntity = bossEntity;
        this.bossType = bossType;
        this.isSuperBoss = isSuperBoss;
    }

    public Player getKiller() { return killer; }
    public LivingEntity getBossEntity() { return bossEntity; }
    public String getBossType() { return bossType; }
    public boolean isSuperBoss() { return isSuperBoss; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
