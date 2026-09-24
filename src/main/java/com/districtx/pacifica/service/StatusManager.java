package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StatusManager {
    private final Set<UUID> spawnProtected = new HashSet<>();

    public StatusManager(PacificaPlugin plugin) { }
    public void set(Player player, boolean guarded) {
        if (guarded) spawnProtected.add(player.getUniqueId());
        else spawnProtected.remove(player.getUniqueId());
    }
    public boolean isSpawnProtected(Player player) { return spawnProtected.contains(player.getUniqueId()); }
    public void clear(Player player) { spawnProtected.remove(player.getUniqueId()); }
    public void clear() { spawnProtected.clear(); }
}