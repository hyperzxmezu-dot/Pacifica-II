package com.districtx.pacifica.service;

import com.districtx.pacifica.database.PlayerDatabase;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

public final class DatabaseInventoryStorage implements InventoryStorage {
    private final PlayerDatabase database;

    public DatabaseInventoryStorage(PlayerDatabase database) {
        this.database = database;
    }

    @Override
    public void save(UUID uuid, GameMode gameMode, ItemStack[] contents) {
        database.saveInventory(uuid, gameMode, contents);
    }

    @Override
    public ItemStack[] load(UUID uuid, GameMode gameMode) {
        return database.loadInventory(uuid, gameMode);
    }

    @Override
    public boolean exists(UUID uuid, GameMode gameMode) {
        return database.hasInventory(uuid, gameMode);
    }
}