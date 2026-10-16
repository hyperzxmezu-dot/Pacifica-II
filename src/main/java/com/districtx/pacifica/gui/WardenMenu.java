package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class WardenMenu extends BaseMenu {
    public WardenMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&c&lWarden", 0);

        ItemStack grayPane = Items.create("GRAY_STAINED_GLASS_PANE", "&7");
        for (int slot = 0; slot < 54; slot++) set(slot, grayPane);

        ItemStack lightGrayPane = Items.create("LIGHT_GRAY_STAINED_GLASS_PANE", "&7");
        int[] lightGraySlots = {0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53};
        for (int slot : lightGraySlots) set(slot, lightGrayPane);

        ItemStack blackPane = Items.create("BLACK_STAINED_GLASS_PANE", "&7");
        int[] blackSlots = {1, 2, 3, 5, 6, 7, 10, 19, 28, 37, 46, 16, 25, 34, 43, 52, 47, 48, 50, 51};
        for (int slot : blackSlots) set(slot, blackPane);

        set(4, Items.create("MAP", "&c&lWarden Info:",
                "&7Everyone has a price.",
                "&7N' here is mine to cover up",
                "&7yo' dirty crimes booyyy..."));

        BigDecimal oneLevelCost = plugin.jobsAndWanted().getWardenRemovalCost(1);
        set(21, Items.create("GOLD_INGOT", "&aRemove (1) Wanted Level",
                "&7Cost: &a$" + formatPrice(oneLevelCost)));

        int wantedLevel = plugin.jobsAndWanted().getWantedLevel(player);
        BigDecimal totalCost = plugin.jobsAndWanted().getWardenRemovalCost(wantedLevel);
        set(23, Items.create("GOLD_BLOCK", "&aRemove All Wanted Levels",
                "&7Cost: &a$" + formatPrice(totalCost)));

        set(49, Items.create("NETHER_STAR", "&c&lCurrent Wanted Level:",
                plugin.wantedDisplay().formatStars(player) + " &f(" + wantedLevel + ")"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory || !(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        int slot = event.getSlot();
        if (slot != 21 && slot != 23) return;
        if (plugin.jobsAndWanted().purchaseWardenRemoval(clicker, slot == 23)) {
            plugin.jobMenus().openWardenMenu(clicker);
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    private String formatPrice(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}