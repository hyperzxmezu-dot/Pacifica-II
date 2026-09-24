package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class TagNametagProvider {
    private final PacificaPlugin plugin;

    public TagNametagProvider(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        if (!plugin.store().tag().getBoolean("tag-system.nametag.enabled", true)) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) return;
        Team team = scoreboard.getTeam(teamName(player));
        String display = plugin.tags().display(player.getUniqueId());
        if (display.isEmpty()) {
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) team.unregister();
            }
            return;
        }
        if (team == null) team = scoreboard.registerNewTeam(teamName(player));
        team.setPrefix(display + " ");
        team.addEntry(player.getName());
    }

    private String teamName(Player player) {
        return "pt" + player.getUniqueId().toString().replace("-", "").substring(0, 14);
    }
}