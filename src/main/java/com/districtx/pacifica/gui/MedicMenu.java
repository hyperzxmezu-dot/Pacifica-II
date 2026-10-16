package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobType;
import com.districtx.pacifica.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class MedicMenu extends JobDetailMenu {
    public MedicMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&c&lMedic Menu");
        medicFrame();
        mainButton(4, "MAP", "&c&lMedic Info:",
                "&7Be the &cMedic &7who fights in the shadows and draws",
                "&7the poison from the souls of his companions");
        mainButton(21, "RED_DYED_LEATHER_HELMET", "&c&lMedic Job", "&7Become a Medic");
        ItemStack kit = Items.create("POTION", "&c&lMedic Kit:");
        if (kit != null && kit.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(PotionType.HEALING);
            kit.setItemMeta(meta);
        }
        set(23, kit);
        mainButton(47, "REDSTONE", "&c&lBack", "&7Return to the &a&lJob Menu&7!");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (isAction(event, 21, "RED_DYED_LEATHER_HELMET")) plugin.jobMenus().selectJobFromMenu(player, JobType.MEDIC);
        else if (isAction(event, 47, "REDSTONE")) openMainMenu();
    }
}