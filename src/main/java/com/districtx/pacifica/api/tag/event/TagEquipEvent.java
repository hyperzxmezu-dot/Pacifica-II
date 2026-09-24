package com.districtx.pacifica.api.tag.event;

import com.districtx.pacifica.api.tag.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a player equips a tag; listeners may cancel the change. */
public final class TagEquipEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Tag tag;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param player player equipping the tag
     * @param tag immutable tag snapshot
     */
    public TagEquipEvent(Player player, Tag tag) { this.player = player; this.tag = tag; }

    /** @return player equipping the tag */
    public Player getPlayer() { return player; }

    /** @return immutable tag snapshot */
    public Tag getTag() { return tag; }

    /** @return whether the equip was cancelled */
    @Override public boolean isCancelled() { return cancelled; }

    /** @param cancelled whether to prevent the equip */
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}