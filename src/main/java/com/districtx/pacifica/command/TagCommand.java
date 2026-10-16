package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.service.Tag;
import com.districtx.pacifica.service.TagRarity;
import com.districtx.pacifica.service.TagSource;
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

public final class TagCommand implements CommandExecutor, TabCompleter {
    private final PacificaPlugin plugin;

    public TagCommand(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.tags().enabled()) return true;
        if (command.getName().equalsIgnoreCase("tag")) return playerCommand(sender, args);
        return onAdminCommand(sender, args);
    }

    public boolean onAdminCommand(CommandSender sender, String[] args) {
        if (!plugin.tags().enabled()) return true;
        return adminCommand(sender, args);
    }

    private boolean playerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!allowed(player, "pacifica.tag.use")) return true;
        if (args.length == 2 && args[0].equalsIgnoreCase("_equip")) {
            Tag tag = plugin.tags().getTag(args[1]);
            if (tag != null && plugin.tags().hasTag(player.getUniqueId(), args[1])
                    && plugin.tags().equipTag(player.getUniqueId(), args[1])) {
                message(player, "tag.equipped", "%tag%", tag.getDisplayName());
            }
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            plugin.tagGui().open(player);
            return true;
        }
        return message(player, "tag.usage");
    }

    private boolean list(Player player) {
        if (!allowed(player, "pacifica.tag.list")) return true;
        if (plugin.tags().getOwnedTags(player.getUniqueId()).isEmpty()) return message(player, "tag.no-tags");
        message(player, "tag.list-header");
        for (Tag tag : plugin.tags().getOwnedTags(player.getUniqueId())) message(player, "tag.list-entry", "%tag%", tag.getDisplayName());
        Tag equipped = plugin.tags().getEquippedTag(player.getUniqueId());
        if (equipped != null) message(player, "tag.list-equipped", "%tag%", equipped.getDisplayName());
        return true;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) return message(sender, "tag.admin-usage");
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("create")) {
            if (!adminAllowed(sender, action)) return true;
            if (args.length < 4) return message(sender, "tag.admin-usage");
            TagRarity rarity = TagRarity.parse(args[args.length - 1]);
            if (rarity == null) return message(sender, "tag.invalid");
            String display = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
            return message(sender, plugin.tags().createTag(args[1], display, rarity, TagSource.EVENT) ? "tag.created" : "tag.invalid");
        }
        if (action.equals("edit")) {
            if (!adminAllowed(sender, action) || args.length < 3) return message(sender, "tag.admin-usage");
            return message(sender, plugin.tags().editTag(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length))) ? "tag.edited" : "tag.invalid");
        }
        if (action.equals("source")) {
            if (!adminAllowed(sender, action) || args.length != 3) return message(sender, "tag.admin-usage");
            TagSource source = TagSource.parse(args[2]);
            return message(sender, source != null && plugin.tags().editSource(args[1], source) ? "tag.edited" : "tag.invalid");
        }
        if (action.equals("rarity")) {
            if (!adminAllowed(sender, action) || args.length != 3) return message(sender, "tag.admin-usage");
            TagRarity rarity = TagRarity.parse(args[2]);
            return message(sender, rarity != null && plugin.tags().editRarity(args[1], rarity) ? "tag.edited" : "tag.invalid");
        }
        if (action.equals("give") || action.equals("remove")) {
            if (!adminAllowed(sender, action)) return true;
            if (args.length != 3) return message(sender, "tag.admin-usage");
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            Tag tag = plugin.tags().getTag(args[2]);
            if (tag == null) return message(sender, "tag.not-found");
            boolean changed = action.equals("give") ? plugin.tags().giveTag(target.getUniqueId(), args[2]) : plugin.tags().removeTag(target.getUniqueId(), args[2]);
            if (!changed) return message(sender, action.equals("give") ? "tag.already-owned" : "tag.not-owned");
            String playerName = target.getName() == null ? args[1] : target.getName();
            message(sender, action.equals("give") ? "tag.given" : "tag.removed", "%player%", playerName, "%tag%", tag.getDisplayName());
            if (target.isOnline()) {
                Player online = target.getPlayer();
                if (action.equals("give")) message(online, "tag.received", "%tag%", tag.getDisplayName());
                else message(online, "tag.removed-player", "%tag%", tag.getDisplayName());
            }
            return true;
        }
        if (action.equals("delete")) {
            if (!adminAllowed(sender, action)) return true;
            return message(sender, args.length == 2 && plugin.tags().deleteTag(args[1]) ? "tag.deleted" : "tag.not-found");
        }
        return message(sender, "tag.admin-usage");
    }

    private boolean allowed(Player player, String permission) {
        if (player.hasPermission("pacifica.tag") || player.hasPermission(permission)) return true;
        return message(player, "admin.no-permission");
    }

    private boolean adminAllowed(CommandSender sender, String action) {
        if (sender.hasPermission("pacifica.tag.admin") || sender.hasPermission("pacifica.tag.admin." + action)
                || sender.hasPermission("pacifica.adminpii") || sender.hasPermission("pacifica.adminpii.tag")) return true;
        return message(sender, "admin.no-permission");
    }

    private boolean message(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(sender instanceof Player player
                ? plugin.store().message(player, path, replacements)
                : plugin.store().message(path, replacements));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tag")) {
            if (!(sender instanceof Player player) || (!player.hasPermission("pacifica.tag")
                    && !player.hasPermission("pacifica.tag.use"))) return Collections.emptyList();
            if (args.length == 1) return matching(Collections.singletonList("list"), args[0]);
            return Collections.emptyList();
        }
        return onAdminTabComplete(sender, args);
    }

    public List<String> onAdminTabComplete(CommandSender sender, String[] args) {
        if (!plugin.tags().enabled()) return Collections.emptyList();
        if (args.length == 1) return matching(Arrays.asList("create", "edit", "give", "remove", "delete", "source", "rarity")
                .stream().filter(action -> hasTagAdminAction(sender, action)).collect(Collectors.toList()), args[0]);
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!hasTagAdminAction(sender, action)) return Collections.emptyList();
        if (args.length == 2 && (action.equals("edit") || action.equals("delete") || action.equals("source") || action.equals("rarity"))) return matching(new ArrayList<>(plugin.tags().getTags().keySet()), args[1]);
        if (args.length == 3 && action.equals("source")) return matching(Arrays.asList("Event", "Cosmetics"), args[2]);
        if (args.length == 3 && action.equals("rarity")) return matching(Arrays.asList("Common", "Uncommon", "Rare", "Epic", "Legendary"), args[2]);
        if (args.length == 2 && (action.equals("give") || action.equals("remove"))) return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        if (args.length == 3 && (action.equals("give") || action.equals("remove"))) return matching(new ArrayList<>(plugin.tags().getTags().keySet()), args[2]);
        return Collections.emptyList();
    }

    private boolean hasTagAdminAction(CommandSender sender, String action) {
        return sender.hasPermission("pacifica.tag.admin") || sender.hasPermission("pacifica.tag.admin." + action)
                || sender.hasPermission("pacifica.adminpii") || sender.hasPermission("pacifica.adminpii.tag");
    }

    private List<String> matching(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}