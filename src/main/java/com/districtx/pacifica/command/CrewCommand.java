package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.crew.Crew;
import com.districtx.pacifica.api.crew.CrewCreateReservation;
import com.districtx.pacifica.api.crew.CrewMember;
import com.districtx.pacifica.api.crew.CrewResult;
import com.districtx.pacifica.gui.CrewMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class CrewCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("create", "invite", "accept", "deny", "leave",
            "kick", "promote", "demote", "transfer", "info", "menu", "top", "reload", "help");
    private final PacificaPlugin plugin;
    private final Map<UUID, PendingTransfer> transfers = new ConcurrentHashMap<>();

    public CrewCommand(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) return tell(sender, "&cThis command is for players only.");
            if (plugin.crews().getCrewForPlayer(player.getUniqueId()).isEmpty()) {
                return tell(player, "&cYou are not in a crew.");
            }
            plugin.guis().open(new CrewMenu(plugin, player, 0));
            return true;
        }
        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("help")) return help(sender);
        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("pacifica.crew.admin")) return tell(sender, "&cYou do not have permission.");
            plugin.store().reload();
            plugin.crews().reloadProgression();
            return tell(sender, "&aCrew configuration reloaded.");
        }
        if (subcommand.equals("top")) return top(sender);
        if (subcommand.equals("info")) return info(sender, args);
        if (!(sender instanceof Player player)) return tell(sender, "&cThis command is for players only.");
        return switch (subcommand) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player, args);
            case "deny" -> deny(player, args);
            case "leave" -> leave(player, args);
            case "kick" -> memberAction(player, args, "kick");
            case "promote" -> memberAction(player, args, "promote");
            case "demote" -> memberAction(player, args, "demote");
            case "transfer" -> transfer(player, args);
            default -> help(player);
        };
    }

    private boolean create(Player player, String[] args) {
        if (args.length != 2) return tell(player, "&eUsage: /crew create <name>");
        int requiredLevel = plugin.crews().minimumPlayerLevel();
        int playerLevel = plugin.identity().pacificaLevel(player.getUniqueId());
        if (playerLevel < requiredLevel) return tell(player, "&cYou need Pacifica Level " + requiredLevel + " to create a crew.");
        double cost = plugin.crews().creationCost();
        BigDecimal fee = BigDecimal.valueOf(cost);
        if (!plugin.economy().isAvailable() || !plugin.economy().hasBalance(player, fee)) {
            return tell(player, "&cYou need $" + fee.toPlainString() + " to create a crew.");
        }
        plugin.crews().reserveCreate(player.getUniqueId(), args[1]).whenComplete((reservation, error) -> runMain(() -> {
            if (error != null || reservation == null) {
                tellIfOnline(player, "&cCrew creation failed. No money was charged.");
                return;
            }
            if (reservation.result() != CrewResult.SUCCESS) {
                tellIfOnline(player, resultMessage(reservation.result()));
                return;
            }
            if (!player.isOnline() || !plugin.economy().withdraw(player, fee)) {
                plugin.crews().cancelCreate(reservation.crewId());
                tellIfOnline(player, "&cCrew creation payment failed. No crew was created.");
                return;
            }
            plugin.crews().activateCreate(reservation.crewId()).whenComplete((result, activationError) -> runMain(() -> {
                if (activationError != null || result != CrewResult.SUCCESS) {
                    plugin.economy().deposit(player, fee);
                    plugin.crews().cancelCreate(reservation.crewId());
                    tellIfOnline(player, "&cCrew creation failed. Your payment was refunded.");
                    return;
                }
                tellIfOnline(player, "&aCrew &f" + args[1] + " &acreated for $" + fee.toPlainString() + ".");
            }));
        }));
        return true;
    }

    private boolean invite(Player player, String[] args) {
        if (args.length != 2) return tell(player, "&eUsage: /crew invite <player>");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return tell(player, "&cThat player must be online to receive an invitation.");
        plugin.crews().invite(player.getUniqueId(), target.getUniqueId()).whenComplete((result, error) -> runMain(() -> {
            CrewResult outcome = error == null ? result : CrewResult.FAILED;
            if (outcome == CrewResult.SUCCESS) {
                tellIfOnline(player, "&aInvitation sent to " + target.getName() + ".");
                tellIfOnline(target, "&eYou were invited to &f" + plugin.crews().getCrewForPlayer(player.getUniqueId()).map(Crew::name).orElse("a crew") + "&e. Use &f/crew accept <crew>&e.");
            } else tellIfOnline(player, resultMessage(outcome));
        }));
        return true;
    }

    private boolean accept(Player player, String[] args) {
        if (args.length != 2) return tell(player, "&eUsage: /crew accept <crew>");
        return complete(player, plugin.crews().accept(player.getUniqueId(), args[1]), result -> {
            if (result == CrewResult.SUCCESS) {
                Crew crew = plugin.crews().getCrewForPlayer(player.getUniqueId()).orElse(null);
                if (crew != null) broadcast(crew, player.getName() + " joined the crew.");
                return "&aYou joined " + (crew == null ? "the crew" : crew.name()) + ".";
            }
            return resultMessage(result);
        });
    }

    private boolean deny(Player player, String[] args) {
        if (args.length != 2) return tell(player, "&eUsage: /crew deny <crew>");
        return complete(player, plugin.crews().deny(player.getUniqueId(), args[1]), this::resultMessage);
    }

    private boolean leave(Player player, String[] args) {
        if (args.length != 1) return tell(player, "&eUsage: /crew leave");
        Crew crew = plugin.crews().getCrewForPlayer(player.getUniqueId()).orElse(null);
        return complete(player, plugin.crews().leave(player.getUniqueId()), result -> {
            if (result == CrewResult.SUCCESS && crew != null) broadcast(crew, player.getName() + " left the crew.");
            return resultMessage(result);
        });
    }

    private boolean memberAction(Player player, String[] args, String action) {
        if (args.length != 2) return tell(player, "&eUsage: /crew " + action + " <player>");
        UUID targetId = resolvePlayerId(args[1]);
        if (targetId == null) return tell(player, "&cPlayer not found.");
        Crew crew = plugin.crews().getCrewForPlayer(player.getUniqueId()).orElse(null);
        CompletableFuture<CrewResult> future = switch (action) {
            case "kick" -> plugin.crews().kick(player.getUniqueId(), targetId);
            case "promote" -> plugin.crews().changeRank(player.getUniqueId(), targetId, true);
            default -> plugin.crews().changeRank(player.getUniqueId(), targetId, false);
        };
        return complete(player, future, result -> {
            if (result == CrewResult.SUCCESS && crew != null) {
                String targetName = playerName(targetId);
                broadcast(crew, targetName + (action.equals("kick") ? " was removed from the crew." : " was " + action + "d."));
            }
            return result == CrewResult.SUCCESS ? "&aCrew member " + action + " completed." : resultMessage(result);
        });
    }

    private boolean transfer(Player player, String[] args) {
        if (args.length == 2) {
            UUID targetId = resolvePlayerId(args[1]);
            if (targetId == null) return tell(player, "&cPlayer not found.");
            transfers.put(player.getUniqueId(), new PendingTransfer(targetId, System.currentTimeMillis() + 30_000));
            return tell(player, "&eConfirm leadership transfer with &f/crew transfer " + args[1] + " confirm&e within 30 seconds.");
        }
        if (args.length != 3 || !args[2].equalsIgnoreCase("confirm")) {
            return tell(player, "&eUsage: /crew transfer <player> [confirm]");
        }
        PendingTransfer pending = transfers.remove(player.getUniqueId());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()
                || !playerName(pending.targetId()).equalsIgnoreCase(args[1])) {
            return tell(player, "&cNo matching leadership transfer is awaiting confirmation.");
        }
        Crew crew = plugin.crews().getCrewForPlayer(player.getUniqueId()).orElse(null);
        return complete(player, plugin.crews().transferLeadership(player.getUniqueId(), pending.targetId()), result -> {
            if (result == CrewResult.SUCCESS && crew != null) broadcast(crew, "Leadership transferred to " + playerName(pending.targetId()) + ".");
            return result == CrewResult.SUCCESS ? "&aLeadership transferred." : resultMessage(result);
        });
    }

    private boolean info(CommandSender sender, String[] args) {
        Crew crew;
        if (args.length == 1 && sender instanceof Player player) {
            crew = plugin.crews().getCrewForPlayer(player.getUniqueId()).orElse(null);
        } else if (args.length == 2) {
            crew = plugin.crews().getCrewByName(args[1]).orElse(null);
            if (crew == null) {
                UUID playerId = resolvePlayerId(args[1]);
                crew = playerId == null ? null : plugin.crews().getCrewForPlayer(playerId).orElse(null);
            }
        } else return tell(sender, "&eUsage: /crew info [crew|player]");
        if (crew == null) return tell(sender, "&cCrew not found.");
        tell(sender, "&6&l" + crew.name() + " &7(" + crew.members().size() + "/" + crew.memberLimit() + ")");
        tell(sender, "&7Leader: &f" + playerName(crew.leaderId()) + " &8| &7Level: &f" + crew.level() + " &8| &7Crew XP: &f" + crew.crewXp());
        tell(sender, "&7Street Cred: &f" + crew.streetCred() + " &8| &7Bank: &f$" + crew.bankBalance().toPlainString());
        return true;
    }

    private boolean top(CommandSender sender) {
        List<Crew> crews = plugin.crews().allCrews().stream()
                .sorted(java.util.Comparator.comparingLong(Crew::streetCred).reversed()
                        .thenComparingLong(Crew::createdAt).thenComparing(Crew::name, String.CASE_INSENSITIVE_ORDER))
                .limit(10).toList();
        tell(sender, "&6&lTop Crews &7(Street Cred)");
        for (int index = 0; index < crews.size(); index++) {
            Crew crew = crews.get(index);
            tell(sender, "&e" + (index + 1) + ". &f" + crew.name() + " &7- &b" + crew.streetCred());
        }
        if (crews.isEmpty()) tell(sender, "&7No crews have been created yet.");
        return true;
    }

    private boolean help(CommandSender sender) {
        tell(sender, "&6&lCrew Commands");
        tell(sender, "&e/crew create <name> &7- Create a crew (Level 5+, $50,000 by default).");
        tell(sender, "&e/crew invite|accept|deny <player|crew> &7- Manage invitations.");
        tell(sender, "&e/crew kick|promote|demote <player> &7- Manage members (authorized ranks only).");
        tell(sender, "&e/crew transfer <player> [confirm] &7- Transfer leadership.");
        tell(sender, "&e/crew leave|info [crew|player]|top|menu &7- View and manage your crew.");
        tell(sender, "&e/crew reload &7- Reload configuration (administrator).");
        return true;
    }

    private boolean complete(Player player, CompletableFuture<CrewResult> future,
                             Function<CrewResult, String> message) {
        future.whenComplete((result, error) -> runMain(() -> tellIfOnline(player,
                message.apply(error == null && result != null ? result : CrewResult.FAILED))));
        return true;
    }

    private void broadcast(Crew crew, String message) {
        crew.members().keySet().forEach(id -> {
            Player member = Bukkit.getPlayer(id);
            if (member != null) tell(member, "&8[&6Crew&8] &f" + message);
        });
    }

    private UUID resolvePlayerId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    private String playerName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    private String resultMessage(CrewResult result) {
        return switch (result) {
            case SUCCESS -> "&aDone.";
            case ALREADY_IN_CREW, ALREADY_MEMBER -> "&cThat player already belongs to a crew.";
            case NAME_TAKEN -> "&cThat crew name is already in use.";
            case NAME_INVALID -> "&cThat crew name is invalid.";
            case NAME_RESERVED -> "&cThat crew name is reserved.";
            case INVITE_EXISTS -> "&cThat player already has an active crew invitation.";
            case INVITATION_NOT_FOUND -> "&cNo matching invitation was found.";
            case INVITATION_EXPIRED -> "&cThat invitation has expired.";
            case CREW_FULL -> "&cThe crew has reached its member limit.";
            case NO_PERMISSION -> "&cYou do not have permission to do that.";
            case CANNOT_TARGET_SELF -> "&cYou cannot target yourself.";
            case LEADER_CANNOT_LEAVE -> "&cThe leader must transfer leadership or disband before leaving.";
            case RANK_PROTECTED -> "&cThat member's rank cannot be changed by you.";
            case INVALID_TARGET -> "&cThat player is not in your crew.";
            case NOT_IN_CREW -> "&cYou are not in a crew.";
            default -> "&cThat crew action could not be completed.";
        };
    }

    private boolean tell(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6Crew&8] " + message));
        return true;
    }

    private void tellIfOnline(Player player, String message) {
        if (player.isOnline()) tell(player, message);
    }

    private void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args.length == 2 && List.of("accept", "deny", "info").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>(plugin.crews().allCrews().stream().map(Crew::name).toList());
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote", "transfer").contains(args[0].toLowerCase())) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream().filter(option -> option.regionMatches(true, 0, input, 0, input.length())).toList();
    }

    private record PendingTransfer(UUID targetId, long expiresAt) {
    }
}