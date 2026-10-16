package com.districtx.pacifica.api.jobs.event;

import com.districtx.pacifica.api.jobs.JailSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a jail session is removed from a player. */
public final class PlayerReleaseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final JailSession session;

    public PlayerReleaseEvent(Player player, JailSession session) {
        this.player = player;
        this.session = session;
    }

    public Player getPlayer() { return player; }
    public JailSession getSession() { return session; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}