package com.districtx.pacifica.api.jobs.event;

/** Reason associated with a wanted-level change. */
public enum WantedReason {
    API,
    PLAYER_KILL,
    COP_KILL,
    NPC_KILL,
    DECAY,
    DEATH,
    ARREST,
    WARDEN,
    ADMIN
}