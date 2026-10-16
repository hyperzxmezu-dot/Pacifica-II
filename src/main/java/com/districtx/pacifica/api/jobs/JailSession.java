package com.districtx.pacifica.api.jobs;

import java.time.Duration;
import java.util.UUID;

/** Immutable public snapshot of a player's active jail session. */
public final class JailSession {
    private final UUID playerId;
    private final UUID arrestingCop;
    private final int wantedStars;
    private final Duration remaining;

    public JailSession(UUID playerId, UUID arrestingCop, int wantedStars, Duration remaining) {
        this.playerId = playerId;
        this.arrestingCop = arrestingCop;
        this.wantedStars = wantedStars;
        this.remaining = remaining;
    }

    public UUID getPlayerId() { return playerId; }
    public UUID getArrestingCop() { return arrestingCop; }
    public int getWantedStars() { return wantedStars; }
    public Duration getRemaining() { return remaining; }
}