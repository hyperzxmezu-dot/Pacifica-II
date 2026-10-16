package com.districtx.pacifica.api.jobs.event;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a Hitman successfully claims an active bounty. */
public final class BountyClaimedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player hitman;
    private final Player target;
    private final BigDecimal amount;

    public BountyClaimedEvent(Player hitman, Player target, BigDecimal amount) {
        this.hitman = hitman;
        this.target = target;
        this.amount = amount;
    }

    public Player getHitman() { return hitman; }
    public Player getTarget() { return target; }
    public BigDecimal getAmount() { return amount; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}