package com.districtx.pacifica.api.jobs;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Public API for jail sessions and the configured jail destination. */
public interface JailAPI {
    /** Returns whether the player is currently jailed. */
    boolean isJailed(Player player);

    /** Returns whether the UUID has an active jail session. */
    boolean isJailed(UUID playerId);

    /** Returns a snapshot of an active jail session. */
    Optional<JailSession> getSession(UUID playerId);

    /** Starts a jail session, if jail is enabled and a jail location is configured. */
    void jail(Player player, Duration duration, UUID arrestingCop);

    /** Releases the player and returns them to the configured server spawn when online. */
    void release(Player player);

    /** Returns the online-adjusted remaining jail time. */
    Duration getRemainingTime(Player player);

    /** Returns the configured jail location, or null when it is not configured. */
    Location getJailLocation();
}