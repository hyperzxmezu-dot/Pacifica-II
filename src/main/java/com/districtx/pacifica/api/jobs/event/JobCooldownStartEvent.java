package com.districtx.pacifica.api.jobs.event;

import com.districtx.pacifica.api.jobs.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when death starts a persistent restricted-job cooldown. */
public final class JobCooldownStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final JobType job;
    private final long cooldownUntil;

    public JobCooldownStartEvent(Player player, JobType job, long cooldownUntil) {
        this.player = player;
        this.job = job;
        this.cooldownUntil = cooldownUntil;
    }

    public Player getPlayer() { return player; }
    public JobType getJob() { return job; }
    public long getCooldownUntil() { return cooldownUntil; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}