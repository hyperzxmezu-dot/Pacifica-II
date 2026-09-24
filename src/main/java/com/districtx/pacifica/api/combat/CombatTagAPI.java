package com.districtx.pacifica.api.combat;

import java.util.Optional;
import org.bukkit.entity.Player;

/** Public access to Pacifica-II's combat-tag state and supported actions. */
public interface CombatTagAPI {
    /** @return {@code true} when the Combat Tag service is enabled */
    boolean isAvailable();

    /**
     * Checks whether the player has an active combat session.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return {@code true} if the player is combat tagged
     */
    boolean isInCombat(Player player);

    /**
     * Returns a read-only snapshot of the player's current combat session.
     * Must be called on the server's main thread.
     *
     * @param player player to inspect
     * @return active session, if present
     */
    Optional<CombatSession> getCombatSession(Player player);

    /**
     * Returns the remaining combat duration.
     * Must be called on the server's main thread.
     *
     * @param player player to inspect
     * @return remaining whole seconds, or zero when not tagged
     */
    int getRemainingSeconds(Player player);

    /**
     * Applies an independent combat tag to a player without an opponent.
     * Must be called on the server's main thread.
     *
     * @param player player to tag
     */
    void tag(Player player);

    /**
     * Tags both players as opponents, following Pacifica-II's configured duration.
     * Must be called on the server's main thread.
     *
     * @param player first player to tag
     * @param opponent opposing player to tag
     */
    void tag(Player player, Player opponent);

    /**
     * Removes the player's combat tag if present.
     * Must be called on the server's main thread.
     *
     * @param player player to untag
     */
    void untag(Player player);
}