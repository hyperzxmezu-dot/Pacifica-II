package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.Location;

public final class SafeSpawnRegion {
    private final PacificaPlugin plugin;

    public SafeSpawnRegion(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean contains(Location location) {
        Location pos1 = plugin.store().safeSpawnPos1();
        Location pos2 = plugin.store().safeSpawnPos2();
        if (location == null || pos1 == null || pos2 == null || location.getWorld() == null
                || pos1.getWorld() == null || pos2.getWorld() == null
                || !pos1.getWorld().getUID().equals(pos2.getWorld().getUID())
                || !location.getWorld().getUID().equals(pos1.getWorld().getUID())) return false;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        return location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }
}