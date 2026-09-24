package com.districtx.pacifica.api.taxi;

/** Typed outcomes for Taxi and teleport API requests. */
public enum TaxiResult {
    /** The teleport countdown was accepted. */
    SUCCESS,
    /** The player argument is missing or the player is offline. */
    PLAYER_NOT_FOUND,
    /** The destination or its world is unavailable. */
    INVALID_DESTINATION,
    /** The player already has a Pacifica-II teleport countdown. */
    ALREADY_TELEPORTING,
    /** Combat rules prevent the teleport. */
    COMBAT_TAGGED,
    /** The destination is in a location where teleporting is prohibited. */
    IN_SAFE_ZONE,
    /** A public start event or other integration cancelled the request. */
    TELEPORT_CANCELLED,
    /** A configured cooldown prevents the request. */
    COOLDOWN,
    /** The player lacks permission for the operation. */
    NO_PERMISSION,
    /** Pacifica-II or the requested feature is disabled. */
    DISABLED,
    /** The request failed because of an unexpected internal error. */
    ERROR
}