package com.districtx.pacifica.api.jobs;

/** Outcome of a job-selection or quit request. */
public enum JobResult {
    SELECTED,
    QUIT,
    ALREADY_ACTIVE,
    LEVEL_REQUIRED,
    COOLDOWN,
    NO_PERMISSION,
    CANCELLED,
    INVALID
}