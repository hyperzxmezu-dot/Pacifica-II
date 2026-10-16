package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.ArrayList;
import java.util.Arrays;
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

public final class AdminPiiCommand implements CommandExecutor, TabCompleter {
    private static final List<String> JOB_NPC_TYPES = List.of("cop", "hitman", "medic", "criminal-quit", "warden");
    private static final List<String> TAG_ACTIONS = List.of("create", "edit", "give", "remove", "delete", "source", "rarity");
    private final PacificaPlugin plugin;
    private final PacificaCommand pacificaCommand;
    private final TagCommand tagCommand;
    private final JailCommand jailCommand;
    private final JobNpcCommand jobNpcCommand;

    public AdminPiiCommand(PacificaPlugin plugin) {
        this.plugin = plugin;
        pacificaCommand = new PacificaCommand(plugin);
        tagCommand = new TagCommand(plugin);
        jailCommand = new JailCommand(plugin);
        jobNpcCommand = new JobNpcCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return sendUsage(sender);
        String feature = args[0].toLowerCase(Locale.ROOT);
        if (!canUseFeature(sender, feature)) {
            sender.sendMessage(Items.color("&cYou do not have permission."));
            return true;
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return switch (feature) {
            case "spawn" -> spawn(sender, subArgs);
            case "warp" -> warp(sender, subArgs);
            case "taxi" -> taxi(sender, subArgs);
            case "trash" -> trash(sender, subArgs);
            case "jail" -> jailCommand.onCommand(sender, null, "adminpii", subArgs);
            case "job" -> job(sender, subArgs);
            case "tag" -> tagCommand.onAdminCommand(sender, subArgs);
            case "supplydrop" -> plugin.supplyDrops().handleAdminCommand(sender, subArgs);
            case "reload" -> subArgs.length == 0
                    ? pacificaCommand.handleAdminCommand(sender, "pacificareload", new String[0])
                    : sendUsage(sender);
            default -> sendUsage(sender);
        };
    }

    private boolean spawn(CommandSender sender, String[] args) {
        if (args.length == 0) return sendUsage(sender);
        if (args[0].equalsIgnoreCase("set") && args.length == 1) return pacificaCommand.handleAdminCommand(sender, "setspawn", new String[0]);
        if (args[0].equalsIgnoreCase("safespawn")) return pacificaCommand.handleAdminCommand(sender, "setsafespawn", Arrays.copyOfRange(args, 1, args.length));
        return sendUsage(sender);
    }

    private boolean warp(CommandSender sender, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("set")) return sendUsage(sender);
        return pacificaCommand.handleAdminCommand(sender, "rndmwarpset", Arrays.copyOfRange(args, 1, args.length));
    }

    private boolean taxi(CommandSender sender, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("link")) return sendUsage(sender);
        return pacificaCommand.handleAdminCommand(sender, "taxi", new String[]{"link"});
    }

    private boolean trash(CommandSender sender, String[] args) {
        if (args.length == 0) return sendUsage(sender);
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("set") || action.equals("unlink")) {
            if (args.length != 1) return sendUsage(sender);
            return pacificaCommand.handleAdminCommand(sender, "trashcan", args);
        }
        if (action.equals("holo") && args.length == 2 && args[1].equalsIgnoreCase("toggle")) {
            return pacificaCommand.handleAdminCommand(sender, "trashcan", args);
        }
        if (action.equals("addprice") && args.length == 2) return pacificaCommand.handleAdminCommand(sender, "trashcan", args);
        return sendUsage(sender);
    }

    private boolean job(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("npc")) return sendUsage(sender);
        return jobNpcCommand.onCommand(sender, null, "adminpii", Arrays.copyOfRange(args, 1, args.length));
    }

    private boolean canUseFeature(CommandSender sender, String feature) {
        if (hasAdminPiiPermission(sender, feature)) return true;
        return switch (feature) {
            case "spawn", "warp", "taxi", "reload" -> sender.hasPermission("pacifica.admin");
            case "supplydrop" -> sender.hasPermission("pacifica.admin");
            case "trash" -> sender.hasPermission("pacifica.admin") || sender.hasPermission("pacifica.trashcan.addprice");
            case "jail" -> sender.hasPermission("pacifica.jail.admin") || hasAnyJailPermission(sender);
            case "job" -> sender.hasPermission("pacifica.job.admin.npc");
            case "tag" -> sender.hasPermission("pacifica.tag.admin") || hasAnyTagPermission(sender);
            default -> false;
        };
    }

    private boolean canUseAction(CommandSender sender, String feature, String action) {
        if (hasAdminPiiPermission(sender, feature)) return true;
        return switch (feature) {
            case "spawn", "warp", "taxi", "trash", "reload" -> sender.hasPermission("pacifica.admin")
                    || (feature.equals("trash") && action.equals("addprice") && sender.hasPermission("pacifica.trashcan.addprice"));
            case "supplydrop" -> sender.hasPermission("pacifica.admin");
            case "jail" -> sender.hasPermission("pacifica.jail.admin") || sender.hasPermission("pacifica.jail.admin." + action);
            case "job" -> sender.hasPermission("pacifica.job.admin.npc");
            case "tag" -> sender.hasPermission("pacifica.tag.admin") || sender.hasPermission("pacifica.tag.admin." + action);
            default -> false;
        };
    }

    private boolean hasAdminPiiPermission(CommandSender sender, String feature) {
        return sender.hasPermission("pacifica.adminpii") || sender.hasPermission("pacifica.adminpii." + feature);
    }

    private boolean hasAnyJailPermission(CommandSender sender) {
        return List.of("setspawn", "spawn", "release", "status", "reload").stream()
                .anyMatch(action -> sender.hasPermission("pacifica.jail.admin." + action));
    }

    private boolean hasAnyTagPermission(CommandSender sender) {
        return TAG_ACTIONS.stream().anyMatch(action -> sender.hasPermission("pacifica.tag.admin." + action));
    }

    private boolean sendUsage(CommandSender sender) {
        List<String> features = List.of("spawn", "warp", "taxi", "trash", "jail", "job", "tag", "supplydrop", "reload").stream()
                .filter(feature -> canUseFeature(sender, feature)).collect(Collectors.toList());
        sender.sendMessage(Items.color("&e/adminpii <" + String.join("|", features) + "> ..."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(List.of("spawn", "warp", "taxi", "trash", "jail", "job", "tag", "supplydrop", "reload").stream()
                    .filter(feature -> canUseFeature(sender, feature)).collect(Collectors.toList()), args[0]);
        }
        String feature = args[0].toLowerCase(Locale.ROOT);
        if (!canUseFeature(sender, feature)) return Collections.emptyList();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        if (subArgs.length == 1) return featureSuggestions(sender, feature, subArgs[0]);
        return nestedSuggestions(sender, feature, subArgs);
    }

    private List<String> featureSuggestions(CommandSender sender, String feature, String prefix) {
        List<String> options = switch (feature) {
            case "spawn" -> List.of("set", "safespawn");
            case "warp" -> List.of("set");
            case "taxi" -> canUseAction(sender, feature, "link") ? List.of("link") : List.of();
            case "trash" -> List.of("set", "unlink", "holo", "addprice").stream()
                    .filter(action -> canUseAction(sender, feature, action)).collect(Collectors.toList());
            case "jail" -> List.of("setspawn", "spawn", "release", "status", "reload").stream()
                    .filter(action -> canUseAction(sender, feature, action)).collect(Collectors.toList());
            case "job" -> canUseAction(sender, feature, "npc") ? List.of("npc") : List.of();
            case "tag" -> TAG_ACTIONS.stream().filter(action -> canUseAction(sender, feature, action)).collect(Collectors.toList());
            case "supplydrop" -> canUseAction(sender, feature, "edit") ? List.of("edit", "warp", "chance", "grant", "remove", "set", "inspect", "reload", "active") : List.of();
            default -> List.of();
        };
        return matching(options, prefix);
    }

    private List<String> nestedSuggestions(CommandSender sender, String feature, String[] args) {
        if (feature.equals("supplydrop") && canUseAction(sender, feature, "edit")) {
            List<String> rarities = List.of("common", "uncommon", "rare", "epic", "legendary");
            if (args.length == 2 && args[0].equalsIgnoreCase("edit")) return matching(rarities, args[1]);
            if (args.length == 2 && args[0].equalsIgnoreCase("warp")) {
                return matching(List.of("list", "add", "remove", "info", "clear"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("warp")
                    && List.of("add", "remove", "info").contains(args[1].toLowerCase(Locale.ROOT))) {
                List<String> names = args[1].equalsIgnoreCase("add")
                        ? plugin.supplyDrops().taxiWarpNames() : plugin.supplyDrops().automaticWarpNames();
                return matching(names, args[2]);
            }
            if (args.length == 2 && List.of("grant", "remove", "set", "inspect").contains(args[0].toLowerCase(Locale.ROOT))) {
                return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
            if (args.length == 3 && List.of("grant", "remove", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
                return matching(rarities, args[2]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("active")) return matching(List.of("remove"), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("active") && args[1].equalsIgnoreCase("remove")) {
                return matching(plugin.supplyDrops().activeDropIds().stream().map(Object::toString).collect(Collectors.toList()), args[2]);
            }
        }
        if (feature.equals("warp") && args.length == 2 && args[0].equalsIgnoreCase("set")
                && canUseAction(sender, feature, "set")) return matching(plugin.warps().names(), args[1]);
        if (feature.equals("spawn") && args.length == 2 && args[0].equalsIgnoreCase("safespawn")
                && canUseAction(sender, feature, "safespawn")) return matching(List.of("1", "2"), args[1]);
        if (feature.equals("trash") && args.length == 2 && args[0].equalsIgnoreCase("holo")
                && canUseAction(sender, feature, "holo")) return matching(List.of("toggle"), args[1]);
        if (feature.equals("job") && args.length == 2 && args[0].equalsIgnoreCase("npc")
                && canUseAction(sender, feature, "npc")) {
            List<String> options = new ArrayList<>(JOB_NPC_TYPES);
            options.add("clear");
            return matching(options, args[1]);
        }
        if (feature.equals("job") && args.length == 3 && args[0].equalsIgnoreCase("npc")
                && args[1].equalsIgnoreCase("clear") && canUseAction(sender, feature, "npc")) return matching(JOB_NPC_TYPES, args[2]);
        if (feature.equals("jail") && args.length == 2
                && (args[0].equalsIgnoreCase("release") || args[0].equalsIgnoreCase("status"))
                && canUseAction(sender, feature, args[0].toLowerCase(Locale.ROOT))) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (feature.equals("tag") && args.length >= 2 && canUseAction(sender, feature, args[0].toLowerCase(Locale.ROOT))) {
            return tagCommand.onAdminTabComplete(sender, args);
        }
        return Collections.emptyList();
    }

    private List<String> matching(List<String> choices, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}