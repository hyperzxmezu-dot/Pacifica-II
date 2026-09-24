package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public final class BackpackMenu extends BaseMenu {
    private final int offset;
    private final int capacity;

    public BackpackMenu(PacificaPlugin plugin, Player player, int page) {
        super(plugin, player, "&6&lBackpack", page);
        offset = page * 54;
        capacity = plugin.backpacks().capacity(player);
        inventory.clear();
        ItemStack[] contents = plugin.backpacks().contents(player);
        for (int i = 0; i < 54 && offset + i < contents.length; i++) inventory.setItem(i, contents[offset + i]);
        if (offset + 54 < capacity) set(53, com.districtx.pacifica.util.Items.create("ARROW", "&e&lNext Page"));
        if (page > 0) set(45, com.districtx.pacifica.util.Items.create("ARROW", "&e&lPrevious Page"));
    }

    @Override public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == inventory && event.getSlot() == 53 && offset + 54 < capacity) {
            event.setCancelled(true);
            open(new BackpackMenu(plugin, player, page + 1));
        } else if (event.getClickedInventory() == inventory && event.getSlot() == 45 && page > 0) {
            event.setCancelled(true);
            open(new BackpackMenu(plugin, player, page - 1));
        }
    }

    @Override public void handleClose(InventoryCloseEvent event) { plugin.backpacks().save(player, inventory, offset, capacity); }
}