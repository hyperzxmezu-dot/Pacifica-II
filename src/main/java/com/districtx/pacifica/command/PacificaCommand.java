package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.gui.TaxiMenu;
import com.districtx.pacifica.gui.TrashMenu;
import com.districtx.pacifica.database.ItemPriceDatabase;
import com.districtx.pacifica.service.TeleportManager.TeleportContext;
import com.districtx.pacifica.util.Items;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.GameMode;

public final class PacificaCommand implements CommandExecutor, TabCompleter {
    private final PacificaPlugin plugin;
    public PacificaCommand(PacificaPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("spawn")) { return spawn(sender); }
        if (name.equals("warp")) { return warp(sender); }
        if (name.equals("setspawn")) return setSpawn(sender);
        if (name.equals("setsafespawn")) { return setSafeSpawn(sender, args); }
        if (name.equals("rndmwarpset")) return setRandomWarp(sender, args);
        if (name.equals("taxi")) return taxi(sender, args);
        if (name.equals("trashcan")) return trash(sender, args);
        if (name.equals("tpa")) return tpa(sender, args);
        if (name.equals("tpaccept")) return tpaAccept(sender, args);
        if (name.equals("tpdeny")) return tpaDeny(sender, args);
        if (name.equals("tpcancel")) return tpaCancel(sender, args);
        if (name.equals("ct")) return combatTag(sender);
        if (name.equals("gms")) return gamemode(sender, GameMode.SURVIVAL, "pacifica.gamemode.survival");
        if (name.equals("gma")) return gamemode(sender, GameMode.ADVENTURE, "pacifica.gamemode.adventure");
        if (name.equals("gmc")) return gamemode(sender, GameMode.CREATIVE, "pacifica.gamemode.creative");
        if (name.equals("gmsp")) return gamemode(sender, GameMode.SPECTATOR, "pacifica.gamemode.spectator");
        if (name.equals("heal")) return heal(sender);
        if (name.equals("feed")) return feed(sender);
        if (name.equals("invsee")) return invsee(sender, args);
        if (name.equals("afk")) return afk(sender);
        if (name.equals("ec") || name.equals("enderchest")) return enderchest(sender);
        if (name.equals("pacificareload")) {
            if (!admin(sender, "reload")) return true;
            plugin.reload();
            sender.sendMessage("&aPacifica-II configuration reloaded.");
            return true;
        }
        return true;
    }
    public boolean handleAdminCommand(CommandSender sender, String commandName, String[] args) {
        return switch (commandName) {
            case "setspawn" -> setSpawn(sender);
            case "setsafespawn" -> setSafeSpawn(sender, args);
            case "rndmwarpset" -> setRandomWarp(sender, args);
            case "taxi" -> linkTaxi(sender, args);
            case "trashcan" -> trashAdmin(sender, args);
            case "pacificareload" -> reload(sender);
            default -> true;
        };
    }
    private boolean setSpawn(CommandSender sender) {
        if (!admin(sender, "spawn") || !(sender instanceof Player player)) return true;
        plugin.store().setSpawn(player.getLocation());
        sender.sendMessage(plugin.store().message("admin.spawn-set"));
        return true;
    }
    private boolean setRandomWarp(CommandSender sender, String[] args) {
        if (!admin(sender, "warp") || !(sender instanceof Player player) || args.length != 1) return true;
        plugin.warps().set(args[0], player.getLocation());
        sender.sendMessage(plugin.store().message("admin.random-warp-set", "%warp%", args[0]));
        return true;
    }
    private boolean reload(CommandSender sender) {
        if (!admin(sender, "reload")) return true;
        plugin.reload();
        sender.sendMessage("&aPacifica-II configuration reloaded.");
        return true;
    }
    private boolean spawn(CommandSender sender) { if (!(sender instanceof Player player)) return true; Location location = plugin.store().spawn(); plugin.teleports().start(player, location == null ? player.getWorld().getSpawnLocation() : location, true, TeleportContext.SPAWN, Collections.emptyMap()); return true; }
    private boolean warp(CommandSender sender) { if (!(sender instanceof Player player)) return true; String name = plugin.warps().randomName(); if (name == null) player.sendMessage(plugin.store().message("warp.no-random")); else plugin.teleports().start(player, plugin.warps().get(name), false, TeleportContext.RANDOM_WARP, Collections.singletonMap("%warp%", name)); return true; }
    private boolean setSafeSpawn(CommandSender sender, String[] args) {
        if (!admin(sender, "spawn") || !(sender instanceof Player player)) return true;
        if (args.length == 0) {
            if (plugin.store().safeSpawnPos1() == null || plugin.store().safeSpawnPos2() == null) {
                sender.sendMessage(plugin.store().message("admin.safe-spawn-incomplete"));
            } else {
                sender.sendMessage(plugin.store().message("admin.safe-spawn-set"));
            }
            return true;
        }
        if (args.length != 1 || (!args[0].equals("1") && !args[0].equals("2"))) {
            sender.sendMessage(plugin.store().message("admin.safe-spawn-usage"));
            return true;
        }
        int position = Integer.parseInt(args[0]);
        plugin.store().setSafeSpawnPos(position, player.getLocation());
        sender.sendMessage(plugin.store().message("admin.safe-spawn-pos-set", "%position%", String.valueOf(position)));
        return true;
    }
    private boolean taxi(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length != 0) {
            player.sendMessage(Items.color("&eUse /adminpii taxi link to link a taxi NPC."));
            return true;
        }
        plugin.guis().open(new TaxiMenu(plugin, player));
        return true;
    }
    private boolean linkTaxi(CommandSender sender, String[] args) {
        if (!admin(sender, "taxi") || args.length != 1 || !args[0].equalsIgnoreCase("link")
                || !(sender instanceof Player player)) return true;
        Entity entity = player.getTargetEntity(8);
        if (entity == null) player.sendMessage(plugin.store().message("admin.look-at-npc"));
        else {
            plugin.store().setTaxiNpc(entity.getUniqueId());
            player.sendMessage(plugin.store().message("admin.taxi-linked"));
        }
        return true;
    }
    private boolean trash(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length != 0) {
            player.sendMessage(Items.color("&eUse /adminpii trash for administrative actions."));
            return true;
        }
        if (!player.hasPermission(plugin.store().trashcan().getString("trashcan.use-permission", "pacifica.trashcan.use"))) {
            player.sendMessage(plugin.store().message("trash.no-permission"));
            return true;
        }
        plugin.guis().open(new TrashMenu(plugin, player));
        return true;
    }
    private boolean trashAdmin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("set")) {
            if (!admin(sender, "trash")) return true;
            org.bukkit.block.Block block = player.getTargetBlockExact(6);
            if (block == null) player.sendMessage(plugin.store().message("admin.look-at-block")); else { plugin.store().addTrashCan(block.getLocation()); plugin.store().setTrashHologramEnabled(block.getLocation(), true); plugin.holograms().create(block.getLocation()); player.sendMessage(plugin.store().message("admin.trash-linked")); }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("unlink")) {
            if (!admin(sender, "trash")) return true;
            org.bukkit.block.Block block = player.getTargetBlockExact(6);
            if (block == null || !plugin.store().isTrashCan(block.getLocation())) player.sendMessage(plugin.store().message("admin.trash-not-linked"));
            else { plugin.holograms().remove(block.getLocation()); plugin.store().removeTrashCan(block.getLocation()); player.sendMessage(plugin.store().message("admin.trash-unlinked")); }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("holo") && args[1].equalsIgnoreCase("toggle")) {
            if (!admin(sender, "trash")) return true;
            org.bukkit.block.Block block = player.getTargetBlockExact(6);
            if (block == null || !plugin.store().isTrashCan(block.getLocation())) {
                player.sendMessage(plugin.store().message("admin.trash-hologram-not-linked"));
                return true;
            }
            if (plugin.store().isTrashHologramEnabled(block.getLocation())) {
                plugin.holograms().remove(block.getLocation());
                plugin.store().setTrashHologramEnabled(block.getLocation(), false);
                player.sendMessage(plugin.store().message("admin.trash-hologram-disabled"));
            } else {
                plugin.store().setTrashHologramEnabled(block.getLocation(), true);
                plugin.holograms().create(block.getLocation());
                player.sendMessage(plugin.store().message("admin.trash-hologram-enabled"));
            }
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("addprice")) {
            if (args.length != 2) player.sendMessage(plugin.store().message("admin.trash-price-usage"));
            else addTrashPrice(player, args[1]);
        } else player.sendMessage(Items.color("&e/adminpii trash <set|unlink|holo toggle|addprice <price>>"));
        return true;
    }
    private void addTrashPrice(Player player, String value) {
        if (!player.hasPermission("pacifica.trashcan.addprice") && !hasAdminPiiPermission(player, "trash")) {
            player.sendMessage(plugin.store().message("admin.no-permission"));
            return;
        }
        final BigDecimal price;
        try { price = new BigDecimal(value); }
        catch (NumberFormatException exception) {
            player.sendMessage(plugin.store().message("admin.trash-price-invalid"));
            return;
        }
        if (price.signum() <= 0) {
            player.sendMessage(plugin.store().message("admin.trash-price-invalid"));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(plugin.store().message("admin.trash-price-no-item"));
            return;
        }
        plugin.itemPriceDatabase().setPrice(item, price);
        player.sendMessage(plugin.store().message("admin.trash-price-set", "%price%", price.toPlainString()));
    }
    private boolean tpa(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) { plugin.guis().open(new TaxiMenu(plugin, player)); return true; }
        if (args.length != 1) { player.sendMessage(plugin.store().message("tpa.usage")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) player.sendMessage(plugin.store().message("tpa.target-offline"));
        else plugin.tpa().send(player, target);
        return true;
    }
    private boolean tpaAccept(CommandSender sender, String[] args) { if (sender instanceof Player player && args.length == 0) plugin.tpa().accept(player); return true; }
    private boolean tpaDeny(CommandSender sender, String[] args) { if (sender instanceof Player player && args.length == 0) plugin.tpa().deny(player); return true; }
    private boolean tpaCancel(CommandSender sender, String[] args) { if (sender instanceof Player player && args.length == 0) plugin.tpa().cancelOutgoing(player, true); return true; }
    private boolean combatTag(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (plugin.combatTags().isTagged(player)) player.sendMessage(plugin.store().message("combattag.status", "%time%", String.valueOf(plugin.combatTags().remaining(player))));
        else player.sendMessage(plugin.store().message("combattag.not-in-combat"));
        return true;
    }
    private boolean gamemode(CommandSender sender, GameMode mode, String permission) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission(permission) && !player.hasPermission("pacifica.gamemode")) {
            player.sendMessage(plugin.store().message("admin.no-permission"));
            return true;
        }
        if (player.getGameMode() == mode) return true;
        player.setGameMode(mode);
        return true;
    }
    private boolean heal(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (!enabled(sender, "pacifica.essentials.heal", "essentials.heal")) return true;
        plugin.essentials().heal(player);
        player.sendMessage(plugin.store().message("essentials.healed"));
        return true;
    }
    private boolean feed(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (!enabled(sender, "pacifica.essentials.feed", "essentials.feed")) return true;
        plugin.essentials().feed(player);
        player.sendMessage(plugin.store().message("essentials.fed"));
        return true;
    }
    private boolean invsee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (!enabled(sender, "pacifica.essentials.invsee", "essentials.invsee") || args.length != 1) return true;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) { player.sendMessage(plugin.store().message("essentials.target-offline")); return true; }
        plugin.essentials().openInventory(player, target);
        return true;
    }
    private boolean afk(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (!enabled(sender, "pacifica.essentials.afk", "essentials.afk")) return true;
        plugin.afk().toggle(player);
        return true;
    }
    private boolean enderchest(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return true; }
        if (!enabled(sender, "pacifica.essentials.enderchest", "essentials.enderchest")) return true;
        plugin.essentials().openEnderChest(player);
        return true;
    }
    private boolean enabled(CommandSender sender, String permission, String path) {
        if (!plugin.store().feature("essentials").getBoolean("essentials.enabled", true)
                || !plugin.store().feature("essentials").getBoolean(path + ".enabled", true)) return false;
        if (!sender.hasPermission(permission)) { sender.sendMessage(plugin.store().message("admin.no-permission")); return false; }
        return true;
    }
    private boolean admin(CommandSender sender, String feature) {
        boolean permitted = sender.hasPermission("pacifica.admin") || hasAdminPiiPermission(sender, feature);
        if (!permitted) sender.sendMessage(plugin.store().message("admin.no-permission"));
        return permitted;
    }
    private boolean hasAdminPiiPermission(CommandSender sender, String feature) {
        return sender.hasPermission("pacifica.adminpii") || sender.hasPermission("pacifica.adminpii." + feature);
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("tpa") && args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        return Collections.emptyList();
    }

    private List<String> matching(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}