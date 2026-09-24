package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.gui.TagMenu;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public final class TagGuiManager {
    private final PacificaPlugin plugin;

    public TagGuiManager(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) { open(player, 0); }

    public void open(Player player, int page) {
        plugin.guis().open(new TagMenu(plugin, player, Math.max(0, page)));
    }

    public void refresh(Player player) { open(player); }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    public void close(Player player) { player.closeInventory(); }
}