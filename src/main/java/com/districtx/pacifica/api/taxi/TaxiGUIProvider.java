package com.districtx.pacifica.api.taxi;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Supplies an additional action button for the Pacifica-II Taxi menu. */
public interface TaxiGUIProvider {
    /** @return stable provider identifier; IDs are unique ignoring case */
    String getId();

    /** @return display name shown on the provider's menu icon */
    String getDisplayName();

    /**
     * Creates the icon displayed for this action.
     *
     * @param player player viewing the Taxi menu
     * @return icon item; Pacifica-II clones it before placing it in the menu
     */
    ItemStack getIcon(Player player);

    /**
     * Performs the provider action after the player clicks its menu icon.
     * Called on the server's main thread.
     *
     * @param player clicking player
     */
    void execute(Player player);
}