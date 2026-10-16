package com.districtx.pacifica.api.jobs.event;

import com.districtx.pacifica.api.jobs.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Cancellable event fired when a player quits a non-default job. */
public final class JobQuitEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final JobType previousJob;
    private final JobType newJob;
    private boolean cancelled;

    public JobQuitEvent(Player player, JobType previousJob, JobType newJob) {
        this.player = player;
        this.previousJob = previousJob;
        this.newJob = newJob;
    }

    public Player getPlayer() { return player; }
    public JobType getPreviousJob() { return previousJob; }
    public JobType getNewJob() { return newJob; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}