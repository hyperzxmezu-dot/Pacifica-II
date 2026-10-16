package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class CriminalMenu extends JobDetailMenu {
    public CriminalMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&9&lCriminal");
        standardFrame("WHITE_STAINED_GLASS_PANE");
        mainButton(4, "MAP", "&9&lCriminal Info:", "&7Want to go back the simple criminal life again", "&7and quit your job and go back to being a criminal?");
        mainButton(22, "WHITE_DYED_LEATHER_HELMET", "&9&lCriminal Job", "&7Become a Criminal");
        mainButton(47, "REDSTONE", "&c&lBack", "&7Return to the &a&lJob Menu&7!");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (isAction(event, 22, "WHITE_DYED_LEATHER_HELMET")) plugin.jobMenus().selectJobFromMenu(player, JobType.CRIMINAL);
        else if (isAction(event, 47, "REDSTONE")) openMainMenu();
    }
}