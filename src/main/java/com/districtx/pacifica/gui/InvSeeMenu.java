package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class InvSeeMenu extends BaseMenu {
    private final Player target;
    private final boolean allowEdit;

    public InvSeeMenu(PacificaPlugin plugin, Player viewer, Player target) {
        super(plugin, viewer, "&8Inventory: &f" + target.getName(), 0);
        this.target = target;
        this.allowEdit = plugin.store().feature("essentials").getBoolean("essentials.invsee.allow-edit", false)
                && viewer.hasPermission("pacifica.essentials.invsee.edit");
        render();
    }

    private void render() {
        ItemStack[] contents = target.getInventory().getContents();
        for (int slot = 0; slot < 36; slot++) set(9 + slot, clone(contents[slot]));
        set(45, clone(target.getInventory().getHelmet()));
        set(46, clone(target.getInventory().getChestplate()));
        set(47, clone(target.getInventory().getLeggings()));
        set(48, clone(target.getInventory().getBoots()));
        set(49, clone(target.getInventory().getItemInOffHand()));
    }

    @Override public void handleClick(InventoryClickEvent event) {
        boolean safeEdit = allowEdit
                && event.getClickedInventory() == inventory
                && event.getRawSlot() >= 9 && event.getRawSlot() <= 49
                && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT);
        if (!safeEdit) event.setCancelled(true);
    }

    @Override public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override public void handleClose(InventoryCloseEvent event) {
        if (!allowEdit || !target.isOnline()) return;
        ItemStack[] contents = target.getInventory().getContents();
        for (int slot = 0; slot < 36; slot++) contents[slot] = clone(inventory.getItem(9 + slot));
        target.getInventory().setContents(contents);
        target.getInventory().setHelmet(clone(inventory.getItem(45)));
        target.getInventory().setChestplate(clone(inventory.getItem(46)));
        target.getInventory().setLeggings(clone(inventory.getItem(47)));
        target.getInventory().setBoots(clone(inventory.getItem(48)));
        target.getInventory().setItemInOffHand(clone(inventory.getItem(49)));
    }

    private ItemStack clone(ItemStack item) { return item == null ? null : item.clone(); }
}