package com.districtx.pacifica.api.taxi.event;

import com.districtx.pacifica.api.taxi.TaxiReason;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a managed Pacifica-II teleport successfully completes. */
public final class TaxiCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location destination;
    private final TaxiReason reason;

    /**
     * Creates the event.
     *
     * @param player player who was teleported
     * @param destination completed destination; copied on construction
     * @param reason source of the teleport
     */
    public TaxiCompleteEvent(Player player, Location destination, TaxiReason reason) {
        this.player = player;
        this.destination = destination.clone();
        this.reason = reason;
    }

    /** @return player who was teleported */
    public Player getPlayer() { return player; }

    /** @return a copy of the completed destination */
    public Location getDestination() { return destination.clone(); }

    /** @return source of the teleport */
    public TaxiReason getReason() { return reason; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}