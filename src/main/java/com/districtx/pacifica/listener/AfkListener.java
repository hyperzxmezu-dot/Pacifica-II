package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.Bukkit;

public final class AfkListener implements Listener {
    private final PacificaPlugin plugin;
    public AfkListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler public void move(PlayerMoveEvent event) {
        if (event.getTo() != null && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ())) clear(event.getPlayer(), "remove-on-move");
    }
    @EventHandler public void chat(AsyncPlayerChatEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> clear(event.getPlayer(), "remove-on-chat"));
    }
    @EventHandler public void command(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().equalsIgnoreCase("/afk")) clear(event.getPlayer(), "remove-on-command");
    }
    @EventHandler public void interact(PlayerInteractEvent event) { clear(event.getPlayer(), "remove-on-interaction"); }
    @EventHandler public void quit(PlayerQuitEvent event) { plugin.afk().clear(event.getPlayer()); }

    private void clear(org.bukkit.entity.Player player, String setting) {
        if (plugin.afk().isAfk(player) && plugin.store().feature("essentials").getBoolean("essentials.afk." + setting, true)) plugin.afk().setAfk(player, false);
    }
}