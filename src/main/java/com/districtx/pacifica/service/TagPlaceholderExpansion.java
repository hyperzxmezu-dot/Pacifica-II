package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class TagPlaceholderExpansion extends PlaceholderExpansion {
    private final PacificaPlugin plugin;

    public TagPlaceholderExpansion(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "TAG";
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
        if (player == null || (!params.isEmpty() && !params.equalsIgnoreCase("tag"))) return "";
        return plugin.tags().display(player.getUniqueId());
    }
}