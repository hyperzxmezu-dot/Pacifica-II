package com.districtx.pacifica.api.jobs.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a player's wanted-star level changes. */
public final class WantedLevelChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int oldLevel;
    private final int newLevel;
    private final WantedReason reason;
    private boolean cancelled;

    public WantedLevelChangeEvent(Player player, int oldLevel, int newLevel, WantedReason reason) {
        this.player = player;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.reason = reason;
    }

    public Player getPlayer() { return player; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }
    public WantedReason getReason() { return reason; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}