package com.districtx.pacifica.api.jobs;

import org.bukkit.entity.Player;

/** Public API for opening the Pacifica-II job menus. */
public interface JobMenuAPI {
    /** Returns whether the job-menu service is available. */
    boolean isAvailable();

    /** Opens the main job-selection menu for an online player. */
    void openJobMenu(Player player);

    /** Opens the Police menu for an online player. */
    void openPoliceMenu(Player player);

    /** Opens the Medic menu for an online player. */
    void openMedicMenu(Player player);

    /** Opens the Warden menu for an online player. */
    void openWardenMenu(Player player);

    /** Opens the Hitman menu for an online player. */
    void openHitmanMenu(Player player);

    /** Opens the Criminal menu for an online player. */
    void openCriminalMenu(Player player);

    /** Starts a bounty-placement session and opens its menu for an online player. */
    void openPlaceBountiesMenu(Player player);

    /** Opens the player chooser for an existing bounty-placement session. */
    void openChoosePlayerMenu(Player player);
}