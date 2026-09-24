package com.districtx.pacifica.gui;

import org.bukkit.entity.Player;

public final class GuiManager {
    public void open(BaseMenu menu) {
        menu.player.openInventory(menu.getInventory());
    }
    public void click(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu menu) menu.handleClick(event);
    }
    public void close(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BaseMenu menu) menu.handleClose(event);
    }
    public void drag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu menu) menu.handleDrag(event);
    }
}