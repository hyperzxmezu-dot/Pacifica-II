package com.districtx.pacifica.api.jobs;

/** Public service entry point for Pacifica-II job and wanted APIs. */
public interface PacificaJobsAPI {
    /** Returns whether the service is available. */
    boolean isAvailable();

    /** Returns the job API. */
    JobAPI jobs();

    /** Returns the wanted API. */
    WantedAPI wanted();

    /** Returns the five-star wanted-level display formatter. */
    WantedDisplayAPI wantedDisplay();

    /** Returns the arrest and bribe API. */
    ArrestAPI arrests();

    /** Returns the jail API. */
    JailAPI jail();

    /** Returns the bounty API. */
    BountyAPI bounty();

    /** Returns the Medic API. */
    MedicAPI medic();
}