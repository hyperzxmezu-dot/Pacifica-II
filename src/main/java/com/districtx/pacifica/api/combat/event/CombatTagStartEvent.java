package com.districtx.pacifica.api.combat.event;

import com.districtx.pacifica.api.combat.CombatReason;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before Pacifica-II applies a combat tag; listeners may cancel it. */
public final class CombatTagStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Player opponent;
    private final CombatReason reason;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param player player being tagged
     * @param opponent opponent, or {@code null} for a standalone tag
     * @param reason reason for starting the tag
     */
    public CombatTagStartEvent(Player player, Player opponent, CombatReason reason) {
        this.player = player;
        this.opponent = opponent;
        this.reason = reason;
    }

    /** @return player being tagged */
    public Player getPlayer() { return player; }

    /** @return opponent, if this tag was initiated by a player encounter */
    public Optional<Player> getOpponent() { return Optional.ofNullable(opponent); }

    /** @return reason for starting the tag */
    public CombatReason getReason() { return reason; }

    /** @return whether a listener cancelled this combat-tag action */
    @Override public boolean isCancelled() { return cancelled; }

    /** @param cancelled whether to prevent this combat-tag action */
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}