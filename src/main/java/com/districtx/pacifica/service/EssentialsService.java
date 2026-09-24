package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.gui.InvSeeMenu;
import org.bukkit.entity.Player;

public final class EssentialsService implements EssentialsManager {
    private final PacificaPlugin plugin;

    public EssentialsService(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public void heal(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
    }

    @Override public void feed(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
    }

    @Override public void openInventory(Player viewer, Player target) {
        plugin.guis().open(new InvSeeMenu(plugin, viewer, target));
    }

    @Override public void openEnderChest(Player player) { player.openInventory(player.getEnderChest()); }
}