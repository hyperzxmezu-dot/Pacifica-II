package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class JobSelectionMenu extends BaseMenu {
    public JobSelectionMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&9&lJob Modes", 0);
        mainButton(21, "BLUE_DYED_LEATHER_HELMET", "&9&lCOP", "&7Click to view the &9COP &7Job!");
        mainButton(22, "WITHER_SKELETON_SKULL", "&8&lHITMAN", "&7Click to view the &8HITMAN &7Job!");
        ItemStack medic = Items.create("POTION", "&c&lMEDIC", "&7Click to view the &cMEDIC &7Job!");
        if (medic != null && medic.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(PotionType.HEALING);
            medic.setItemMeta(meta);
        }
        set(23, medic);
        mainButton(31, "WOODEN_SWORD", "&e&lCRIMINAL", "&7Click to view the &eCRIMINAL &7Job!");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory || event.getCurrentItem() == null
                || !event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;
        int slot = event.getSlot();
        if (slot == 21 && isMaterial(event, "BLUE_DYED_LEATHER_HELMET")) plugin.jobMenus().openPoliceMenu(player);
        else if (slot == 22 && isMaterial(event, "WITHER_SKELETON_SKULL")) plugin.jobMenus().openHitmanMenu(player);
        else if (slot == 23 && isMaterial(event, "POTION")) plugin.jobMenus().openMedicMenu(player);
        else if (slot == 31 && isMaterial(event, "WOODEN_SWORD")) plugin.jobMenus().openCriminalMenu(player);
    }

    private boolean isMaterial(InventoryClickEvent event, String material) {
        ItemStack expected = Items.create(material, "");
        return expected != null && event.getCurrentItem().getType() == expected.getType();
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }
}