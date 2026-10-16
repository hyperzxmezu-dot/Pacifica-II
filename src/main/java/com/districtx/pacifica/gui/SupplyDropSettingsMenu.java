package com.districtx.pacifica.gui;

import com.districtx.pacifica.api.supplydrops.SupplyDropRarity;
import com.districtx.pacifica.service.SupplyDropsManager;
import com.districtx.pacifica.util.Items;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SupplyDropSettingsMenu implements InventoryHolder {
    private final SupplyDropRarity rarity;
    private final UUID rewardId;
    private final Inventory inventory;

    public SupplyDropSettingsMenu(SupplyDropsManager manager, SupplyDropRarity rarity, UUID rewardId) {
        this.rarity = rarity;
        this.rewardId = rewardId;
        SupplyDropsManager.RewardEntry reward = manager.reward(rewardId);
        String name = reward == null ? "Reward" : reward.displayName();
        inventory = Bukkit.createInventory(this, 27, Items.color(name + " &7&lSettings"));
        if (reward == null) return;
        inventory.setItem(4, reward.item());
        inventory.setItem(12, Items.create("STONE", "&7Weight: &a" + reward.weight(), "&eClick to edit"));
        inventory.setItem(14, Items.create("WHEAT_SEEDS", "&7Chance: &a" + reward.chance() + "%", "&eClick to edit"));
        inventory.setItem(18, Items.create("REDSTONE", "&c&lBack"));
    }

    public SupplyDropRarity rarity() { return rarity; }
    public UUID rewardId() { return rewardId; }
    @Override public Inventory getInventory() { return inventory; }
}