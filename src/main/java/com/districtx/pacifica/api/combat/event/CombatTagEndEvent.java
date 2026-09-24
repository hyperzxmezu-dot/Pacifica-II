package com.districtx.pacifica.api.combat.event;

import com.districtx.pacifica.api.combat.CombatReason;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when an active combat-tag session ends. */
public final class CombatTagEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final UUID opponentId;
    private final CombatReason reason;

    /**
     * Creates the event.
     *
     * @param player player whose session ended
     * @param opponentId last opponent UUID, or {@code null} when unavailable
     * @param reason reason the session ended
     */
    public CombatTagEndEvent(Player player, UUID opponentId, CombatReason reason) {
        this.player = player;
        this.opponentId = opponentId;
        this.reason = reason;
    }

    /** @return player whose session ended */
    public Player getPlayer() { return player; }

    /** @return last opponent UUID, if one was recorded */
    public Optional<UUID> getLastOpponent() { return Optional.ofNullable(opponentId); }

    /** @return reason the session ended */
    public CombatReason getReason() { return reason; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}