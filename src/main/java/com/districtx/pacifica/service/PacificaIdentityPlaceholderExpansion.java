package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class PacificaIdentityPlaceholderExpansion extends PlaceholderExpansion {
    private final PacificaPlugin plugin;

    public PacificaIdentityPlaceholderExpansion(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "pacificaii";
    }

    @Override
    public String getAuthor() {
        return "DistrictX Developers Official";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) return "";
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "identity" -> plugin.identity().display(player, true);
            case "xp_level" -> plugin.identity().xpLevel(player);
            case "tag" -> plugin.tags() == null ? "" : plugin.tags().display(player.getUniqueId());
            case "wanted" -> plugin.store().config().getBoolean("wanted.placeholder.enabled", true)
                    ? plugin.wantedDisplay().formatStars(player) : "";
            case "wantedlevel" -> Integer.toString(plugin.jobsAndWanted().getWantedLevel(player));
            default -> "";
        };
    }
}