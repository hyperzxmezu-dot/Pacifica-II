package com.districtx.pacifica.api.jobs.event;

import com.districtx.pacifica.api.jobs.ArrestMethod;
import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Cancellable event fired before an eligible arrest is committed. */
public final class PreArrestEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player cop;
    private final Player prisoner;
    private final int wantedStars;
    private final ArrestMethod method;
    private final BigDecimal reward;
    private boolean cancelled;

    public PreArrestEvent(Player cop, Player prisoner, int wantedStars, ArrestMethod method, BigDecimal reward) {
        this.cop = cop;
        this.prisoner = prisoner;
        this.wantedStars = wantedStars;
        this.method = method;
        this.reward = reward;
    }

    public Player getCop() { return cop; }
    public Player getPrisoner() { return prisoner; }
    public int getWantedStars() { return wantedStars; }
    public ArrestMethod getMethod() { return method; }
    public BigDecimal getReward() { return reward; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}