package com.districtx.pacifica.api.jobs.event;

import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after an active bounty has been removed and persisted. */
public final class BountyRemovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID targetId;
    private final BigDecimal amount;

    public BountyRemovedEvent(UUID targetId, BigDecimal amount) {
        this.targetId = targetId;
        this.amount = amount;
    }

    public UUID getTargetId() { return targetId; }
    public BigDecimal getAmount() { return amount; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}