package com.districtx.pacifica.api.taxi.event;

import com.districtx.pacifica.api.taxi.TaxiReason;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before Pacifica-II begins a managed teleport countdown. */
public final class TaxiStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location destination;
    private final TaxiReason reason;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param player player being teleported
     * @param destination destination; copied on construction
     * @param reason teleport source
     */
    public TaxiStartEvent(Player player, Location destination, TaxiReason reason) {
        this.player = player;
        this.destination = destination.clone();
        this.reason = reason;
    }

    /** @return player being teleported */
    public Player getPlayer() { return player; }

    /** @return a copy of the requested destination */
    public Location getDestination() { return destination.clone(); }

    /** @return teleport source */
    public TaxiReason getReason() { return reason; }

    /** @return whether a listener cancelled the countdown */
    @Override public boolean isCancelled() { return cancelled; }

    /** @param cancelled whether to prevent the countdown from starting */
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}