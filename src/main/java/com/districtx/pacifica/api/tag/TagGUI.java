package com.districtx.pacifica.api.tag;

import org.bukkit.entity.Player;

/** Safe handle for a player's open Pacifica-II Tag interface. */
public interface TagGUI {
    /** @return player associated with this Tag interface */
    Player getPlayer();

    /** @return stable identifier for this GUI type */
    String getId();

    /** Opens this GUI for its player on the server's main thread. */
    void open();

    /** Closes this GUI if it is currently open for its player. */
    void close();

    /** @return whether this GUI is currently open for its player */
    boolean isOpen();
}