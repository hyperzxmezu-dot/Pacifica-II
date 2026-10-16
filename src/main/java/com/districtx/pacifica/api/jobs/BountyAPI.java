package com.districtx.pacifica.api.jobs;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Public API for player bounties and Hitman target tracking. */
public interface BountyAPI {
    /** Returns the configured bounty for a target, if one exists. */
    Optional<BigDecimal> getBounty(UUID targetId);

    /** Sets a bounty, subject to the configured maximum reward. */
    boolean setBounty(UUID targetId, BigDecimal amount);

    /** Removes a target's bounty. */
    boolean removeBounty(UUID targetId);

    /** Returns whether a target currently has a bounty. */
    boolean hasBounty(UUID targetId);

    /** Atomically claims an active bounty for an online Hitman; call on the server thread. */
    Optional<BigDecimal> claimBounty(UUID targetId, UUID killerId);

    /** Returns a uniformly selected eligible online target, if one exists; call on the server thread. */
    Optional<UUID> getRandomEligibleTarget();

    /** Assigns a bounty target to a Hitman tracker. */
    boolean assignTracker(Player hitman, UUID targetId);

    /** Returns the Hitman's currently tracked target, if any. */
    Optional<UUID> getTrackedTarget(Player hitman);

    /** Clears the Hitman's tracker target. */
    void clearTrackedTarget(Player hitman);
}