package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.Location;
import java.util.UUID;

public final class SafeSpawnRegion {
    private final PacificaPlugin plugin;
    private UUID worldId;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private double minZ;
    private double maxZ;

    public SafeSpawnRegion(PacificaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** Refreshes the cached region bounds after its configured positions change. */
    public void refresh() {
        Location pos1 = plugin.store().safeSpawnPos1();
        Location pos2 = plugin.store().safeSpawnPos2();
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null
                || !pos1.getWorld().getUID().equals(pos2.getWorld().getUID())) {
            worldId = null;
            return;
        }
        worldId = pos1.getWorld().getUID();
        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1.0D;
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1.0D;
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1.0D;
    }

    public boolean contains(Location location) {
        return containsCoordinates(location, worldId, minX, maxX, minY, maxY, minZ, maxZ);
    }

    static boolean containsCoordinates(Location location, UUID worldId, double minX, double maxX,
                                      double minY, double maxY, double minZ, double maxZ) {
        if (location == null || location.getWorld() == null || worldId == null
                || !location.getWorld().getUID().equals(worldId)) return false;
        return location.getX() >= minX && location.getX() < maxX
                && location.getY() >= minY && location.getY() < maxY
                && location.getZ() >= minZ && location.getZ() < maxZ;
    }

    static boolean exited(boolean wasInside, boolean isInside) {
        return wasInside && !isInside;
    }
}