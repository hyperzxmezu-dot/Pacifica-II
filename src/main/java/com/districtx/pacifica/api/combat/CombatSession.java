package com.districtx.pacifica.api.combat;

import java.util.Optional;
import java.util.UUID;

/** Read-only representation of a player's current combat-tag session. */
public interface CombatSession {
    /** @return UUID of the tagged player */
    UUID getPlayerId();

    /** @return session start time as milliseconds since the Unix epoch */
    long getStartTime();

    /** @return current session expiry time as milliseconds since the Unix epoch */
    long getEndTime();

    /** @return remaining whole seconds captured when this snapshot was created */
    int getRemainingSeconds();

    /** @return {@code true} when the snapshot was created for an active session */
    boolean isActive();

    /** @return snapshot state */
    CombatState getState();

    /** @return last known opponent UUID, if this session has an opponent */
    Optional<UUID> getLastOpponent();
}