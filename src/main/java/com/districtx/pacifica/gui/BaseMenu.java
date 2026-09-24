package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class BaseMenu implements InventoryHolder {
    protected final PacificaPlugin plugin;
    protected final Player player;
    protected final int page;
    protected final Inventory inventory;

    protected BaseMenu(PacificaPlugin plugin, Player player, String title, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = page;
        inventory = Bukkit.createInventory(this, 54, ChatColor.translateAlternateColorCodes('&', title));
        frame();
    }

    protected void frame() {
        ItemStack light = Items.create("LIGHT_GRAY_STAINED_GLASS_PANE", "&7");
        for (int i = 0; i < 54; i++) inventory.setItem(i, light);
        ItemStack white = Items.create("WHITE_STAINED_GLASS_PANE", "&7");
        int[] whiteSlots = {0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53};
        for (int slot : whiteSlots) inventory.setItem(slot, white);
        ItemStack black = Items.create("BLACK_STAINED_GLASS_PANE", "&7");
        int[] blackSlots = {1, 2, 3, 4, 5, 6, 7, 10, 16, 19, 25, 28, 34, 37, 43, 46, 52};
        for (int slot : blackSlots) inventory.setItem(slot, black);
    }

    protected void set(int slot, ItemStack item) { inventory.setItem(slot, item); }
    protected void mainButton(int slot, String material, String name, String lore) {
        set(slot, Items.create(material, plugin.placeholders().parse(player, name), plugin.placeholders().parse(player, lore)));
    }
    protected void open(BaseMenu menu) { plugin.guis().open(menu); }
    protected void main() { open(new TaxiMenu(plugin, player)); }
    @Override public Inventory getInventory() { return inventory; }
    public void handleClose(InventoryCloseEvent event) { }
    public void handleDrag(InventoryDragEvent event) { }
    public abstract void handleClick(InventoryClickEvent event);
}