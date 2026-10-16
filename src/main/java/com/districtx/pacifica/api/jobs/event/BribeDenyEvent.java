package com.districtx.pacifica.api.jobs.event;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when the responsible Cop denies a pending bribe offer. */
public final class BribeDenyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player cop;
    private final Player prisoner;
    private final BigDecimal amount;

    public BribeDenyEvent(Player cop, Player prisoner, BigDecimal amount) {
        this.cop = cop;
        this.prisoner = prisoner;
        this.amount = amount;
    }

    public Player getCop() { return cop; }
    public Player getPrisoner() { return prisoner; }
    public BigDecimal getAmount() { return amount; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}