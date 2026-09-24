package com.districtx.pacifica.api.combat;

/** Describes why a combat-tag lifecycle event occurred. */
public enum CombatReason {
    /** Combat initiated by player damage. */
    DAMAGE,
    /** Combat initiated or removed through the public API. */
    API,
    /** The configured combat duration expired. */
    EXPIRED,
    /** Combat ended because of a player death. */
    DEATH,
    /** Combat ended because a player disconnected. */
    QUIT,
    /** Combat ended through an internal reset or unspecified action. */
    OTHER
}