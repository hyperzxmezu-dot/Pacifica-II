package com.districtx.pacifica.api.jobs;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Public API for persistent wanted-star state. */
public interface WantedAPI {
    /** Returns a player's current wanted level. */
    int getWantedLevel(Player player);

    /** Returns a player's current wanted level. */
    int getWantedLevel(UUID playerId);

    /** Returns whether a player has at least one wanted star. */
    boolean isWanted(Player player);

    /** Adds wanted stars, clamped to the configured maximum. */
    void addWantedLevel(Player player, int amount);

    /** Sets wanted stars, clamped to the configured range. */
    void setWantedLevel(Player player, int level);

    /** Clears wanted stars. */
    void clearWantedLevel(Player player);

    /** Applies one configured wanted-star decay step. */
    void decayWantedLevel(Player player);

    /** Returns whether the player currently meets the wanted-based arrest rule. */
    boolean canBeArrested(Player player);
}