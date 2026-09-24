package com.districtx.pacifica.api.taxi.event;

import com.districtx.pacifica.api.taxi.TaxiReason;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a managed Pacifica-II teleport countdown is cancelled or fails. */
public final class TaxiCancelEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final TaxiReason reason;

    /**
     * Creates the event.
     *
     * @param player player whose teleport was cancelled
     * @param reason source of the teleport
     */
    public TaxiCancelEvent(Player player, TaxiReason reason) {
        this.player = player;
        this.reason = reason;
    }

    /** @return player whose teleport was cancelled */
    public Player getPlayer() { return player; }

    /** @return source of the cancelled teleport */
    public TaxiReason getReason() { return reason; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}