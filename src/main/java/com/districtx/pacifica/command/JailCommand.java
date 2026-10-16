package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JailSession;
import com.districtx.pacifica.util.Items;
import java.time.Duration;
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

/** Admin command handler for jail configuration and sessions. */
public final class JailCommand implements CommandExecutor, TabCompleter {
    private final PacificaPlugin plugin;

    public JailCommand(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Items.color("&e/adminpii jail <setspawn|spawn|release|status|reload>"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> {
                if (!permitted(sender, "pacifica.jail.admin.setspawn") || !(sender instanceof Player player)) return true;
                plugin.jails().setJailLocation(player.getLocation());
                sender.sendMessage(Items.color("&aJail spawn set."));
            }
            case "spawn" -> {
                if (!permitted(sender, "pacifica.jail.admin.spawn") || !(sender instanceof Player player)) return true;
                org.bukkit.Location location = plugin.jails().getJailLocation();
                if (location == null) sender.sendMessage(Items.color("&cJail spawn is not configured."));
                else player.teleport(location);
            }
            case "release" -> {
                if (!permitted(sender, "pacifica.jail.admin.release")) return true;
                if (args.length != 2) { sender.sendMessage(Items.color("&cUsage: /adminpii jail release <player>")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !plugin.jails().isJailed(target)) sender.sendMessage(Items.color("&cThat player is not online and jailed."));
                else { plugin.jails().release(target); sender.sendMessage(Items.color("&aReleased " + target.getName() + " from jail.")); }
            }
            case "status" -> {
                if (!permitted(sender, "pacifica.jail.admin.status")) return true;
                if (args.length != 2) { sender.sendMessage(Items.color("&cUsage: /adminpii jail status <player>")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) sender.sendMessage(Items.color("&cThat player is not online."));
                else {
                    JailSession session = plugin.jails().getSession(target.getUniqueId()).orElse(null);
                    if (session == null) sender.sendMessage(Items.color("&e" + target.getName() + " is not jailed."));
                    else sender.sendMessage(Items.color("&e" + target.getName() + " is jailed for " + format(session.getRemaining()) + " with " + session.getWantedStars() + " stars."));
                }
            }
            case "reload" -> {
                if (!permitted(sender, "pacifica.jail.admin.reload")) return true;
                plugin.reload();
                sender.sendMessage(Items.color("&aPacifica-II configuration reloaded."));
            }
            default -> sender.sendMessage(Items.color("&e/adminpii jail <setspawn|spawn|release|status|reload>"));
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matching(List.of("setspawn", "spawn", "release", "status", "reload").stream()
                .filter(action -> canUse(sender, "pacifica.jail.admin." + action)).collect(Collectors.toList()), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("release") || args[0].equalsIgnoreCase("status"))
                && canUse(sender, "pacifica.jail.admin." + args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean permitted(CommandSender sender, String permission) {
        if (canUse(sender, permission)) return true;
        sender.sendMessage(Items.color("&cYou do not have permission."));
        return false;
    }

    private boolean canUse(CommandSender sender, String permission) {
        return sender.hasPermission("pacifica.jail.admin") || sender.hasPermission(permission)
                || sender.hasPermission("pacifica.adminpii") || sender.hasPermission("pacifica.adminpii.jail");
    }

    private List<String> matching(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }

    private String format(Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remainder + "s";
        return remainder + "s";
    }
}