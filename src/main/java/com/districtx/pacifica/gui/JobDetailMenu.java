package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.Arrays;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

abstract class JobDetailMenu extends BaseMenu {
    JobDetailMenu(PacificaPlugin plugin, Player player, String title) {
        super(plugin, player, title, 0);
        inventory.clear();
    }

    protected void policeFrame() {
        panes("LIGHT_BLUE_STAINED_GLASS_PANE", 0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53);
        panes("BLUE_STAINED_GLASS_PANE", 1, 10, 19, 28, 37, 46, 7, 16, 25, 34, 43, 52);
        panes("WHITE_STAINED_GLASS_PANE", 2, 3, 5, 6, 48, 49, 50, 51);
    }

    protected void medicFrame() {
        panes("WHITE_STAINED_GLASS_PANE", allSlots());
        panes("PINK_STAINED_GLASS_PANE", 0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53);
        panes("RED_STAINED_GLASS_PANE", 1, 10, 19, 28, 37, 46, 7, 16, 25, 34, 43, 52);
        panes("MAGENTA_STAINED_GLASS_PANE", 2, 3, 5, 6, 48, 49, 50, 51);
    }

    private int[] allSlots() {
        int[] slots = new int[54];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = slot;
        return slots;
    }

    protected void mainButton(int slot, String material, String name, String... lore) {
        String[] parsedLore = Arrays.stream(lore)
                .map(line -> plugin.placeholders().parse(player, line))
                .toArray(String[]::new);
        set(slot, Items.create(material, plugin.placeholders().parse(player, name), parsedLore));
    }

    protected void standardFrame(String interiorMaterial) {
        ItemStack filler = Items.create(interiorMaterial, "&7");
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, filler.clone());
        panes("LIGHT_GRAY_STAINED_GLASS_PANE", 0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53);
        panes("WHITE_STAINED_GLASS_PANE", 1, 10, 19, 28, 37, 46, 7, 16, 25, 34, 43, 52);
        panes("BLACK_STAINED_GLASS_PANE", 2, 3, 5, 6, 48, 49, 50, 51);
    }

    private void panes(String material, int... slots) {
        ItemStack pane = Items.create(material, "&7");
        for (int slot : slots) inventory.setItem(slot, pane.clone());
    }

    protected boolean isAction(InventoryClickEvent event, int slot, String material) {
        if (event.getClickedInventory() != inventory || event.getSlot() != slot || event.getCurrentItem() == null
                || !event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return false;
        ItemStack expected = Items.create(material, "");
        return expected != null && event.getCurrentItem().getType() == expected.getType();
    }

    protected void openMainMenu() {
        player.closeInventory();
        plugin.jobMenus().openJobMenu(player);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }
}