package com.districtx.pacifica.api.jobs.event;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a randomly selected online player's bounty is persisted. */
public final class RandomBountyAssignedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player target;
    private final BigDecimal amount;

    public RandomBountyAssignedEvent(Player target, BigDecimal amount) {
        this.target = target;
        this.amount = amount;
    }

    public Player getTarget() { return target; }
    public BigDecimal getAmount() { return amount; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}