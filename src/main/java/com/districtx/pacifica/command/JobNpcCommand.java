package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Links existing server NPC entities to job and Warden interactions. */
public final class JobNpcCommand implements CommandExecutor, TabCompleter {
    private static final List<String> TYPES = List.of("cop", "hitman", "medic", "criminal-quit", "warden");
    private final PacificaPlugin plugin;

    public JobNpcCommand(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pacifica.job.admin.npc") && !sender.hasPermission("pacifica.adminpii")
                && !sender.hasPermission("pacifica.adminpii.job")) {
            sender.sendMessage(Items.color("&cYou do not have permission."));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            if (!TYPES.contains(args[1].toLowerCase(Locale.ROOT))) { sender.sendMessage(Items.color("&cUnknown NPC type.")); return true; }
            plugin.jobsAndWanted().clearNpc(args[1]);
            sender.sendMessage(Items.color("&aNPC link cleared."));
            return true;
        }
        if (!(sender instanceof Player player) || args.length != 1 || !TYPES.contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(Items.color("&e/adminpii job npc <cop|hitman|medic|criminal-quit|warden> | clear <type>"));
            return true;
        }
        Entity target = player.getTargetEntity(8);
        if (target == null) sender.sendMessage(Items.color("&cLook at an NPC within 8 blocks."));
        else {
            plugin.jobsAndWanted().linkNpc(args[0], target.getUniqueId());
            sender.sendMessage(Items.color("&aLinked that NPC as " + args[0].toLowerCase(Locale.ROOT) + "."));
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pacifica.job.admin.npc") && !sender.hasPermission("pacifica.adminpii")
                && !sender.hasPermission("pacifica.adminpii.job")) return List.of();
        List<String> choices = args.length == 1 ? new java.util.ArrayList<>(TYPES) : args.length == 2 && args[0].equalsIgnoreCase("clear") ? TYPES : List.of();
        if (args.length == 1) choices.add("clear");
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}