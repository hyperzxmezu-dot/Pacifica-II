package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TabListener implements Listener {
    private final PacificaPlugin plugin;

    public TabListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.tabs().updateAll();
                plugin.tags().refreshAll();
            }
        });
    }
}