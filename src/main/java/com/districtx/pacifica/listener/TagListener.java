package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public final class TagListener implements Listener {
    private final PacificaPlugin plugin;

    public TagListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        plugin.tagItems().handleInteract(event);
    }
}