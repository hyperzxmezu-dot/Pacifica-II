package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Selects or clears a Hitman's bounty-compass target. */
public final class BountyTrackCommand implements CommandExecutor, TabCompleter {
    private final PacificaPlugin plugin;

    public BountyTrackCommand(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("pacifica.bounty.track")) {
            player.sendMessage(Items.color("&cYou do not have permission."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            plugin.bounties().clearTrackedTarget(player);
            player.sendMessage(Items.color("&eBounty tracking cleared."));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Items.color("&e/bountytrack <player|clear>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) player.sendMessage(Items.color("&cThat player is not online."));
        else if (plugin.bounties().assignTracker(player, target.getUniqueId())) player.sendMessage(Items.color("&aTracking " + target.getName() + "'s bounty."));
        else player.sendMessage(Items.color("&cYou must be a Hitman and the target must have a bounty."));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pacifica.bounty.track") || args.length != 1) return Collections.emptyList();
        List<String> players = Bukkit.getOnlinePlayers().stream().filter(player -> plugin.bounties().hasBounty(player.getUniqueId()))
                .map(Player::getName).collect(Collectors.toList());
        players.add("clear");
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return players.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}