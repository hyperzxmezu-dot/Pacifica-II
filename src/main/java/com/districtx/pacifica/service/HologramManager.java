package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

public final class HologramManager {
    private final PacificaPlugin plugin;
    public HologramManager(PacificaPlugin plugin) { this.plugin = plugin; }
    public void load() {
        for (String id : plugin.store().holograms()) {
            try {
                Entity entity = plugin.getServer().getEntity(UUID.fromString(id));
                if (entity != null) entity.remove();
            } catch (IllegalArgumentException ignored) { }
        }
        plugin.store().setHolograms(new ArrayList<>());
        for (Location location : plugin.store().trashCanLocations()) {
            if (plugin.store().isTrashHologramEnabled(location)) create(location);
            else removeExisting(location);
        }
    }
    public void create(Location block) {
        removeExisting(block);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Location location = block.clone().add(0.5, 1.25 + ((2 - i) * 0.25), 0.5);
            ArmorStand stand = block.getWorld().spawn(location, ArmorStand.class);
            stand.setVisible(false);
            stand.setInvulnerable(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(Items.color(new String[]{"&c&lTRASH CAN", "&7&lSELL YOUR STUFF", "&7&lFOR SOME CASH"}[i]));
            ids.add(stand.getUniqueId().toString());
        }
        plugin.store().setTrashHolograms(block, ids);
    }
    public void remove(Location block) {
        removeExisting(block);
        plugin.store().removeTrashHolograms(block);
    }
    private void removeExisting(Location location) {
        Set<UUID> ids = new HashSet<>();
        for (String id : plugin.store().trashHolograms(location)) {
            try { ids.add(UUID.fromString(id)); }
            catch (IllegalArgumentException ignored) { }
        }
        location.getChunk().load();
        for (Entity entity : location.getChunk().getEntities()) if (ids.contains(entity.getUniqueId())) entity.remove();
        plugin.store().setTrashHolograms(location, new ArrayList<>());
    }
}