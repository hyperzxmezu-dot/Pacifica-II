package com.districtx.pacifica.api.jobs;

/** Outcome of a player-selected bounty transaction. */
public enum BountyPlacementResult {
    SUCCESS,
    NOT_HITMAN,
    INVALID_TARGET,
    INVALID_AMOUNT,
    AMOUNT_TOO_LOW,
    AMOUNT_TOO_HIGH,
    MAXIMUM_REWARD_EXCEEDED,
    INSUFFICIENT_FUNDS,
    TRANSACTION_FAILED
}