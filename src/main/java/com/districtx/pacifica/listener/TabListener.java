package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.entity.Player;

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

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        plugin.tags().removeNametag(event.getPlayer());
    }

    @EventHandler
    public void worldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            plugin.tags().refreshNametag(player);
            plugin.tabs().update(player);
        });
    }

    @EventHandler
    public void commandSend(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(command -> !canSeeCommand(event.getPlayer(), command));
    }

    private boolean canSeeCommand(Player player, String commandName) {
        int separator = commandName.lastIndexOf(':');
        if (separator >= 0) {
            if (!commandName.substring(0, separator).equalsIgnoreCase(plugin.getName())) return true;
            commandName = commandName.substring(separator + 1);
        }
        return switch (commandName.toLowerCase(Locale.ROOT)) {
            case "adminpii" -> hasAnyAdminFeature(player);
            case "tag", "tags" -> player.hasPermission("pacifica.tag") || player.hasPermission("pacifica.tag.use");
            case "trashcan", "trash" -> player.hasPermission(plugin.store().trashcan().getString("trashcan.use-permission", "pacifica.trashcan.use"))
                    || player.hasPermission("pacifica.admin") || player.hasPermission("pacifica.trashcan.addprice")
                    || adminFeature(player, "trash");
            case "bribe" -> player.hasPermission("pacifica.bribe.use") || player.hasPermission("pacifica.arrest.use");
            case "bountytrack" -> player.hasPermission("pacifica.bounty.track");
            case "gms" -> player.hasPermission("pacifica.gamemode") || player.hasPermission("pacifica.gamemode.survival");
            case "gma" -> player.hasPermission("pacifica.gamemode") || player.hasPermission("pacifica.gamemode.adventure");
            case "gmc" -> player.hasPermission("pacifica.gamemode") || player.hasPermission("pacifica.gamemode.creative");
            case "gmsp" -> player.hasPermission("pacifica.gamemode") || player.hasPermission("pacifica.gamemode.spectator");
            case "heal" -> player.hasPermission("pacifica.essentials.heal");
            case "feed" -> player.hasPermission("pacifica.essentials.feed");
            case "invsee" -> player.hasPermission("pacifica.essentials.invsee");
            case "afk" -> player.hasPermission("pacifica.essentials.afk");
            case "ec", "enderchest" -> player.hasPermission("pacifica.essentials.enderchest");
            default -> true;
        };
    }

    private boolean adminFeature(Player player, String feature) {
        return player.hasPermission("pacifica.adminpii") || player.hasPermission("pacifica.adminpii." + feature);
    }

    private boolean hasAnyAdminFeature(Player player) {
        for (String feature : List.of("spawn", "warp", "taxi", "trash", "jail", "job", "tag", "supplydrop", "reload")) {
            if (adminFeature(player, feature)) return true;
        }
        return player.hasPermission("pacifica.admin") || player.hasPermission("pacifica.job.admin.npc")
                || player.hasPermission("pacifica.trashcan.addprice") || player.hasPermission("pacifica.jail.admin")
                || hasAnyPermission(player, "pacifica.jail.admin.setspawn", "pacifica.jail.admin.spawn",
                        "pacifica.jail.admin.release", "pacifica.jail.admin.status", "pacifica.jail.admin.reload")
                || player.hasPermission("pacifica.tag.admin") || hasAnyTagAdminPermission(player);
    }

    private boolean hasAnyTagAdminPermission(Player player) {
        for (String action : List.of("create", "edit", "give", "remove", "delete", "source", "rarity")) {
            if (player.hasPermission("pacifica.tag.admin." + action)) return true;
        }
        return false;
    }

    private boolean hasAnyPermission(Player player, String... permissions) {
        for (String permission : permissions) if (player.hasPermission(permission)) return true;
        return false;
    }
}