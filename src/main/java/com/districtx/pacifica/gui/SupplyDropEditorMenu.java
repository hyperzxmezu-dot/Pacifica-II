package com.districtx.pacifica.gui;

import com.districtx.pacifica.api.supplydrops.SupplyDropRarity;
import com.districtx.pacifica.service.SupplyDropsManager;
import com.districtx.pacifica.util.Items;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SupplyDropEditorMenu implements InventoryHolder {
    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 27, 36, 45, 17, 26, 35, 44, 53, 46, 47, 48, 49, 50, 51, 52};
    private final SupplyDropRarity rarity;
    private final Inventory inventory;

    public SupplyDropEditorMenu(SupplyDropsManager manager, SupplyDropRarity rarity) {
        this.rarity = rarity;
        inventory = Bukkit.createInventory(this, 54,
                Items.color("&2&lSupply Drops " + manager.rarityDisplay(rarity) + " Settings"));
        for (int slot : BORDER) inventory.setItem(slot, Items.create("BLACK_STAINED_GLASS_PANE", "&7"));
        List<SupplyDropsManager.RewardEntry> entries = manager.rewards(rarity);
        int[] usable = usableSlots();
        for (int i = 0; i < entries.size() && i < usable.length; i++) {
            inventory.setItem(usable[i], entries.get(i).item());
        }
    }

    private int[] usableSlots() {
        return java.util.stream.IntStream.range(0, 54).filter(slot -> {
            for (int border : BORDER) if (slot == border) return false;
            return true;
        }).toArray();
    }

    public SupplyDropRarity rarity() { return rarity; }
    @Override public Inventory getInventory() { return inventory; }
}