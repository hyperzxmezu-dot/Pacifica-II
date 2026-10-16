package com.districtx.pacifica.api.jobs.event;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Cancellable event fired before a prisoner's offer is sent to their Cop. */
public final class BribeOfferEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player cop;
    private final Player prisoner;
    private final BigDecimal amount;
    private boolean cancelled;

    public BribeOfferEvent(Player cop, Player prisoner, BigDecimal amount) {
        this.cop = cop;
        this.prisoner = prisoner;
        this.amount = amount;
    }

    public Player getCop() { return cop; }
    public Player getPrisoner() { return prisoner; }
    public BigDecimal getAmount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}