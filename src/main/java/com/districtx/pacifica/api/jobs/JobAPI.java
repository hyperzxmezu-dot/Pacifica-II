package com.districtx.pacifica.api.jobs;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Public API for reading and changing a player's active job. */
public interface JobAPI {
    /** Returns the active job, defaulting to {@link JobType#CRIMINAL}. */
    JobType getJob(Player player);

    /** Returns the active job, defaulting to {@link JobType#CRIMINAL}. */
    JobType getJob(UUID playerId);

    /** Returns whether the player currently has the requested job. */
    boolean hasJob(Player player, JobType job);

    /** Checks permission, level unlocks, and job cooldowns. */
    boolean canSelectJob(Player player, JobType job);

    /** Attempts to select a job after validating its requirements. */
    JobResult selectJob(Player player, JobType job);

    /** Changes a non-criminal player back to the default job. */
    JobResult quitJob(Player player);

    /** Returns remaining job cooldown in milliseconds. */
    long getRemainingJobCooldown(UUID playerId);

    /** Returns whether the player is currently locked out of restricted jobs. */
    boolean isJobLocked(Player player);

    /** Force-sets the player's job, bypassing selection requirements. */
    void setJob(Player player, JobType job);

    /** Force-resets the player's job to {@link JobType#CRIMINAL}. */
    void resetJob(Player player);
}