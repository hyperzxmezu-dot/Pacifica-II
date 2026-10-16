package com.districtx.pacifica.api.jobs.event;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before the configured head-drop roll for a completed bounty claim. */
public final class BountyHeadDropEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player hitman;
    private final Player target;
    private final BigDecimal bountyAmount;
    private final double chance;
    private boolean cancelled;

    public BountyHeadDropEvent(Player hitman, Player target, BigDecimal bountyAmount, double chance) {
        this.hitman = hitman;
        this.target = target;
        this.bountyAmount = bountyAmount;
        this.chance = chance;
    }

    public Player getHitman() { return hitman; }
    public Player getTarget() { return target; }
    public BigDecimal getBountyAmount() { return bountyAmount; }
    public double getChance() { return chance; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}