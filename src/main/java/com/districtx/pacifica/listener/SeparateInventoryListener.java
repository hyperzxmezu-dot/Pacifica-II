package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SeparateInventoryListener implements Listener {
    private final PacificaPlugin plugin;

    public SeparateInventoryListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        org.bukkit.GameMode oldMode = player.getGameMode();
        org.bukkit.GameMode newMode = event.getNewGameMode();
        if (plugin.separateInventories().isDeathTransition(player)) {
            if (newMode == org.bukkit.GameMode.SPECTATOR) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getGameMode() == newMode) {
                    plugin.separateInventories().completeDeathTransition(player, newMode);
                }
            });
            return;
        }
        if (plugin.wasted() != null && plugin.wasted().isWasted(player) && newMode == org.bukkit.GameMode.SPECTATOR) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getGameMode() == newMode) {
                plugin.separateInventories().switchInventory(player, oldMode, newMode);
            }
        });
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) plugin.separateInventories().initialize(event.getPlayer());
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { plugin.separateInventories().saveInventory(event.getPlayer()); }
}