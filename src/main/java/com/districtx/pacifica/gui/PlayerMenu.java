package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class PlayerMenu extends BaseMenu {
    private static final int[] CONTENT = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42, 47, 48, 49, 50, 51};
    private final List<Player> players = new ArrayList<>();
    public PlayerMenu(PacificaPlugin plugin, Player player, int page) {
        super(plugin, player, "&7&lPlayers", page);
        for (Player online : Bukkit.getOnlinePlayers()) if (!online.equals(player)) players.add(online);
        int start = page * CONTENT.length;
        for (int i = 0; i < CONTENT.length && start + i < players.size(); i++) {
            Player target = players.get(start + i);
            set(CONTENT[i], Items.head(target, "&e&l" + target.getName(), "&7Click to send teleport request!"));
        }
        set(45, Items.create("REDSTONE", "&c&lBack", "&7Return to the taxi page!"));
        if ((page + 1) * CONTENT.length < players.size()) set(53, Items.create("ARROW", "&e&lNext Page", "&7Page " + (page + 2)));
    }
    @Override public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (event.getSlot() == 45) { main(); return; }
        if (event.getSlot() == 53 && (page + 1) * CONTENT.length < players.size()) { open(new PlayerMenu(plugin, player, page + 1)); return; }
        for (int i = 0; i < CONTENT.length; i++) if (CONTENT[i] == event.getSlot() && page * CONTENT.length + i < players.size()) {
            Player target = players.get(page * CONTENT.length + i);
            plugin.tpa().send(player, target);
            player.closeInventory();
            return;
        }
    }
}