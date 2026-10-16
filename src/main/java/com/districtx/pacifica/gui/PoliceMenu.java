package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class PoliceMenu extends JobDetailMenu {
    public PoliceMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&9&lPolice Menu");
        policeFrame();
        mainButton(4, "MAP", "&9&lPolice Info:", "&7Join the force and arrest", "&7the wanted players on the streets!");
        mainButton(21, "BLUE_DYED_LEATHER_HELMET", "&9&lCop Job", "&7Become a Cop");
        set(23, com.districtx.pacifica.util.Items.create("DIAMOND_AXE", "&3&lCop Kit:"));
        mainButton(47, "REDSTONE", "&c&lBack", "&7Return to the &a&lJob Menu&7!");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (isAction(event, 21, "BLUE_DYED_LEATHER_HELMET")) plugin.jobMenus().selectJobFromMenu(player, JobType.COP);
        else if (isAction(event, 47, "REDSTONE")) openMainMenu();
    }
}