package com.districtx.pacifica.service;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

public interface InventoryStorage {
    void save(UUID uuid, GameMode gameMode, ItemStack[] contents);
    ItemStack[] load(UUID uuid, GameMode gameMode);
    boolean exists(UUID uuid, GameMode gameMode);
}