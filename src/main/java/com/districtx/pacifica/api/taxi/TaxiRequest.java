package com.districtx.pacifica.api.taxi;

import java.util.UUID;
import org.bukkit.Location;

/** Read-only snapshot of a pending teleport initiated by a Taxi action. */
public final class TaxiRequest extends TeleportRequest {
    /**
     * Creates a Taxi request snapshot.
     *
     * @param playerId UUID of the player
     * @param destination teleport destination; copied on construction
     * @param remainingSeconds remaining countdown seconds
     * @param reason source of the teleport
     */
    public TaxiRequest(UUID playerId, Location destination, int remainingSeconds, TaxiReason reason) {
        super(playerId, destination, remainingSeconds, reason);
    }
}