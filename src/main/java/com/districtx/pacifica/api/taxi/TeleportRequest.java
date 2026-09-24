package com.districtx.pacifica.api.taxi;

import java.util.UUID;
import org.bukkit.Location;

/** Immutable snapshot of a pending Pacifica-II teleport countdown. */
public class TeleportRequest {
    private final UUID playerId;
    private final Location destination;
    private final int remainingSeconds;
    private final TaxiReason reason;

    /**
     * Creates a request snapshot.
     *
     * @param playerId UUID of the player
     * @param destination teleport destination; copied on construction
     * @param remainingSeconds remaining countdown seconds
     * @param reason source of the teleport
     */
    public TeleportRequest(UUID playerId, Location destination, int remainingSeconds, TaxiReason reason) {
        this.playerId = playerId;
        this.destination = destination.clone();
        this.remainingSeconds = remainingSeconds;
        this.reason = reason;
    }

    /** @return UUID of the player with this pending request */
    public UUID getPlayerId() { return playerId; }

    /** @return a copy of the destination location */
    public Location getDestination() { return destination.clone(); }

    /** @return remaining seconds captured when this snapshot was created */
    public int getRemainingSeconds() { return remainingSeconds; }

    /** @return the feature that initiated the teleport */
    public TaxiReason getReason() { return reason; }
}