package com.districtx.pacifica.api.jobs;

/** Outcome of an arrest attempt. */
public enum ArrestResult {
    SUCCESS,
    INVALID,
    NO_PERMISSION,
    NOT_COP,
    NOT_ELIGIBLE,
    FORBIDDEN_TARGET,
    ALREADY_JAILED,
    JAIL_UNAVAILABLE,
    CANCELLED,
    IN_PROGRESS
}