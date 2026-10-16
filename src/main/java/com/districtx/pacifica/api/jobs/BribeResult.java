package com.districtx.pacifica.api.jobs;

/** Outcome of a bribe offer or response. */
public enum BribeResult {
    OFFERED,
    ACCEPTED,
    DENIED,
    INVALID,
    NOT_JAILED,
    BELOW_MINIMUM,
    COP_OFFLINE,
    NOT_RESPONSIBLE_COP,
    NO_OFFER,
    EXPIRED,
    INSUFFICIENT_FUNDS,
    ECONOMY_FAILURE,
    CANCELLED
}