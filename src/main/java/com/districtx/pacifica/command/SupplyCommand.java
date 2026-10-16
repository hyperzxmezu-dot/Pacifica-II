package com.districtx.pacifica.command;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.gui.SupplyMenu;
import com.districtx.pacifica.util.Items;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SupplyCommand implements CommandExecutor {
    private final PacificaPlugin plugin;

    public SupplyCommand(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Items.color("&cOnly players can use this command."));
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Items.color("&e/supply"));
            return true;
        }
        plugin.guis().open(new SupplyMenu(plugin, player));
        return true;
    }
}