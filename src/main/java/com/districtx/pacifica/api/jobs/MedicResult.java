package com.districtx.pacifica.api.jobs;

/** Outcome of a Medic syringe use. */
public enum MedicResult {
    SUCCESS,
    INVALID,
    NOT_MEDIC,
    NO_SYRINGE,
    INVALID_TARGET,
    NO_KIT_EFFECT,
    COOLDOWN,
    CANCELLED
}