package com.districtx.pacifica.api.tag;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Supplies an additional action entry in Pacifica-II's paginated Tag menu. */
public interface TagGUIProvider {
    /** @return stable provider identifier; IDs are unique ignoring case */
    String getId();

    /**
     * Creates the icon displayed for this action.
     *
     * @param player player viewing the Tag menu
     * @return icon item; Pacifica-II clones it before placing it in the menu
     */
    ItemStack createIcon(Player player);

    /**
     * Performs the provider action after the player clicks its menu icon.
     * Called on the server's main thread.
     *
     * @param player clicking player
     */
    void onClick(Player player);
}