package com.districtx.pacifica.api.jobs;

import org.bukkit.entity.Player;

/** Public formatter for the five-position wanted-star display. */
public interface WantedDisplayAPI {
    /** Formats a level as exactly five legacy-colored filled or empty star positions. */
    static String format(int wantedLevel) {
        int level = Math.max(0, Math.min(5, wantedLevel));
        return "&6" + "★".repeat(level) + "☆".repeat(5 - level);
    }

    /** Formats a wanted level clamped to the range zero through five. */
    String formatStars(int wantedLevel);

    /** Formats the player's current wanted level. */
    String formatStars(Player player);
}