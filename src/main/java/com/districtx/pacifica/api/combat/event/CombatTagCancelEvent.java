package com.districtx.pacifica.api.combat.event;

import com.districtx.pacifica.api.combat.CombatReason;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when an attempted combat-tag action is cancelled before it starts. */
public final class CombatTagCancelEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Player opponent;
    private final CombatReason reason;

    /**
     * Creates the event.
     *
     * @param player player whose tag action was cancelled
     * @param opponent opponent, if present
     * @param reason reason for the attempted tag
     */
    public CombatTagCancelEvent(Player player, Player opponent, CombatReason reason) {
        this.player = player;
        this.opponent = opponent;
        this.reason = reason;
    }

    /** @return player whose tag action was cancelled */
    public Player getPlayer() { return player; }

    /** @return opponent, if present */
    public Optional<Player> getOpponent() { return Optional.ofNullable(opponent); }

    /** @return reason for the attempted tag */
    public CombatReason getReason() { return reason; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}