package com.districtx.pacifica.service;

import org.bukkit.entity.Player;

public interface EssentialsManager {
    void heal(Player player);
    void feed(Player player);
    void openInventory(Player viewer, Player target);
    void openEnderChest(Player player);
}