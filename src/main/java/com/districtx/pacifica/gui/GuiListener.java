package com.districtx.pacifica.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {
    private final GuiManager manager;
    public GuiListener(GuiManager manager) { this.manager = manager; }
    @EventHandler public void click(InventoryClickEvent event) { manager.click(event); }
    @EventHandler public void close(InventoryCloseEvent event) { manager.close(event); }
    @EventHandler public void drag(InventoryDragEvent event) { manager.drag(event); }
}