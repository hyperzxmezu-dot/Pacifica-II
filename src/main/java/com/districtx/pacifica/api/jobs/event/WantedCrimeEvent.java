package com.districtx.pacifica.api.jobs.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a qualifying player-kill crime updates wanted state. */
public final class WantedCrimeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Player victim;
    private final int wantedIncrease;
    private final WantedReason reason;
    private boolean cancelled;

    public WantedCrimeEvent(Player player, Player victim, int wantedIncrease, WantedReason reason) {
        this.player = player;
        this.victim = victim;
        this.wantedIncrease = wantedIncrease;
        this.reason = reason;
    }

    public Player getPlayer() { return player; }
    public Player getVictim() { return victim; }
    public int getWantedIncrease() { return wantedIncrease; }
    public WantedReason getReason() { return reason; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}