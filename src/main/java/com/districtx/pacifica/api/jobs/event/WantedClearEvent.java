package com.districtx.pacifica.api.jobs.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after all wanted stars are cleared. */
public final class WantedClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int oldLevel;
    private final WantedReason reason;

    public WantedClearEvent(Player player, int oldLevel, WantedReason reason) {
        this.player = player;
        this.oldLevel = oldLevel;
        this.reason = reason;
    }

    public Player getPlayer() { return player; }
    public int getOldLevel() { return oldLevel; }
    public WantedReason getReason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}