package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class HitmanMenu extends JobDetailMenu {
    public HitmanMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&8&lHitman Menu");
        standardFrame("GRAY_STAINED_GLASS_PANE");
        mainButton(4, "MAP", "&8&lHitman Info:", "&7Hey, you want to make some money?", "&7You just gotta... kill some people");
        mainButton(21, "WITHER_SKELETON_SKULL", "&8&lHitman Job", "&7Become the Hitman");
        mainButton(22, "PAPER", "&5&lPlace a bounty", "&7Put hit out a player!", "&7Open the Place Bounties menu.");
        set(23, com.districtx.pacifica.util.Items.create("DIAMOND_HOE", "&8&lHitman Kit:"));
        mainButton(47, "REDSTONE", "&c&lBack", "&7Return to the &a&lJob Menu&7!");
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (isAction(event, 21, "WITHER_SKELETON_SKULL")) plugin.jobMenus().selectJobFromMenu(player, JobType.HITMAN);
        else if (isAction(event, 22, "PAPER")) plugin.jobMenus().openPlaceBountiesMenu(player);
        else if (isAction(event, 47, "REDSTONE")) openMainMenu();
    }
}