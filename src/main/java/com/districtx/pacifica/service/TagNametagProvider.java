package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
        Team entryTeam = scoreboard.getEntryTeam(player.getName());
        if ((team != null || entryTeam != null) && entryTeam != team) return;
        String prefix = plugin.tabs() == null ? "" : plugin.tabs().prefix(player);
        String display = plugin.identity().display(player, true);
        StringBuilder formatted = new StringBuilder();
        if (!prefix.isEmpty()) formatted.append(prefix).append(' ');
        if (!display.isEmpty()) formatted.append(display).append(' ');
        if (formatted.length() == 0) {
            if (team != null && entryTeam == team) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) team.unregister();
            }
            return;
        }
        if (team == null) team = scoreboard.registerNewTeam(teamName(player));
        formatted.append("&r");
        if (plugin.tabs() != null) formatted.append(plugin.tabs().playerNameStyle(player));
        team.setPrefix(Items.color(formatted.toString()));
        ChatColor color = plugin.tabs() == null ? null : plugin.tabs().playerNameColor(player);
        team.setColor(color == null ? ChatColor.WHITE : color);
        team.addEntry(player.getName());
    }

    /** Removes a player's scoreboard-team entry and unregisters its team when empty. */
    public void remove(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) return;
        Team team = scoreboard.getTeam(teamName(player));
        if (team == null) return;
        if (scoreboard.getEntryTeam(player.getName()) != team) return;
        team.removeEntry(player.getName());
        if (team.getEntries().isEmpty()) team.unregister();
    }

    private String teamName(Player player) {
        return "pt" + player.getUniqueId().toString().replace("-", "").substring(0, 14);
    }
}