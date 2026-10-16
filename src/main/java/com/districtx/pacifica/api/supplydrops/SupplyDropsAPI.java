package com.districtx.pacifica.api.supplydrops;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Public API for player-owned virtual SupplyDrops. */
public interface SupplyDropsAPI {
    boolean isAvailable();
    int getAmount(UUID playerId, SupplyDropRarity rarity);
    boolean grantAmount(UUID playerId, SupplyDropRarity rarity, int amount);
    boolean removeAmount(UUID playerId, SupplyDropRarity rarity, int amount);
    boolean summon(Player player, SupplyDropRarity rarity);
}