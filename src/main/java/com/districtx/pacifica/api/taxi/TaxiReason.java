package com.districtx.pacifica.api.taxi;

/** Identifies the feature that initiated a Pacifica-II teleport. */
public enum TaxiReason {
    /** Teleport to the configured spawn. */
    SPAWN,
    /** Quick Play destination selection. */
    QUICK_PLAY,
    /** A configured random warp destination. */
    RANDOM_WARP,
    /** A named warp destination. */
    WARP,
    /** A player-to-player teleport request. */
    TPA,
    /** A teleport to a player-owned home or house. */
    HOUSE,
    /** An action initiated through the Taxi interface or API. */
    TAXI,
    /** A teleport source not represented by another constant. */
    OTHER
}