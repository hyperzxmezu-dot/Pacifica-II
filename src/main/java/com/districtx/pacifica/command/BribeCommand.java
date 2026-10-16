package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.BribeResult;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.util.ArrayList;
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

/** Handles prisoner bribe offers and Cop responses. */
public final class BribeCommand implements CommandExecutor, TabCompleter {
    private final PacificaPlugin plugin;

    public BribeCommand(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("pacifica.bribe.use") && !player.hasPermission("pacifica.arrest.use")) {
            player.sendMessage(Items.color("&cYou do not have permission."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
            player.sendMessage(message(plugin.arrests().acceptBribe(player, "")));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            player.sendMessage(message(plugin.arrests().acceptBribe(player, args[1])));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("deny")) {
            player.sendMessage(message(plugin.arrests().denyBribe(player, args[1])));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Items.color("&e/bribe <amount> | /bribe <accept|deny> <prisoner>"));
            return true;
        }
        try {
            BigDecimal amount = new BigDecimal(args[0]);
            player.sendMessage(message(plugin.arrests().offerBribe(player, amount)));
        } catch (NumberFormatException exception) {
            player.sendMessage(Items.color("&cEnter a valid bribe amount."));
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("pacifica.bribe.use") && !sender.hasPermission("pacifica.arrest.use")) return Collections.emptyList();
        if (args.length == 1) return matching(List.of("accept", "deny"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) if (plugin.jails().isJailed(player)) names.add(player.getName());
            return matching(names, args[1]);
        }
        return Collections.emptyList();
    }

    private String message(BribeResult result) {
        return Items.color(switch (result) {
            case OFFERED -> "&aBribe offer sent to the responsible Cop.";
            case ACCEPTED -> "&aBribe accepted. The prisoner has been released.";
            case DENIED -> "&eBribe offer denied.";
            case NOT_JAILED -> "&cYou are not currently jailed.";
            case BELOW_MINIMUM -> "&cThe bribe is below the configured minimum.";
            case COP_OFFLINE -> "&cThe responsible Cop is offline; the offer cannot be sent.";
            case NOT_RESPONSIBLE_COP -> "&cYou are not responsible for that prisoner.";
            case NO_OFFER -> "&cThere is no pending offer for that prisoner.";
            case EXPIRED -> "&cThat bribe offer has expired.";
            case INSUFFICIENT_FUNDS -> "&cYou do not have enough money for that bribe.";
            case ECONOMY_FAILURE -> "&cThe bribe transfer failed; no release was made.";
            case CANCELLED -> "&cThat bribe action was cancelled.";
            default -> "&cInvalid bribe request.";
        });
    }

    private List<String> matching(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}