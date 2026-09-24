package com.districtx.pacifica.api.tag.event;

import com.districtx.pacifica.api.tag.Tag;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a tag definition is successfully created. */
public final class TagCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Tag tag;

    /** @param tag immutable snapshot of the created definition */
    public TagCreatedEvent(Tag tag) { this.tag = tag; }

    /** @return immutable snapshot of the created definition */
    public Tag getTag() { return tag; }

    /** @return event handler list */
    @Override public HandlerList getHandlers() { return HANDLERS; }

    /** @return static event handler list */
    public static HandlerList getHandlerList() { return HANDLERS; }
}