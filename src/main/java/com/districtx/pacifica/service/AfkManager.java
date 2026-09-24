package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class AfkManager {
    private final PacificaPlugin plugin;
    private final Set<UUID> afkPlayers = new HashSet<>();

    public AfkManager(PacificaPlugin plugin) { this.plugin = plugin; }

    public synchronized boolean isAfk(UUID playerId) { return afkPlayers.contains(playerId); }
    public boolean isAfk(Player player) { return isAfk(player.getUniqueId()); }
    public synchronized void setAfk(Player player, boolean afk) {
        if (afk) afkPlayers.add(player.getUniqueId());
        else afkPlayers.remove(player.getUniqueId());
        player.sendMessage(plugin.store().message(afk ? "essentials.afk-on" : "essentials.afk-off", "%player%", player.getName()));
    }
    public void toggle(Player player) { setAfk(player, !isAfk(player)); }
    public synchronized void clear(Player player) { afkPlayers.remove(player.getUniqueId()); }
}