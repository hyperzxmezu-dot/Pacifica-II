package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class WastedManager {
    private final PacificaPlugin plugin;
    private final Map<UUID, WastedState> states = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();

    public WastedManager(PacificaPlugin plugin) { this.plugin = plugin; }

    public void start(Player player, String type, Player killer) {
        cancel(player);
        Location deathLocation = player.getLocation().clone();
        WastedState state = new WastedState(deathLocation, killer == null ? null : killer.getUniqueId(), null);
        states.put(player.getUniqueId(), state);
        player.setGameMode(GameMode.SPECTATOR);
        String message = randomMessage(type);
        int duration = Math.max(1, plugin.store().wasted().getInt("wasted.duration-seconds", 5));
        state.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancel(player); return; }
            if (state.elapsed >= duration * 100) { finish(player); return; }
            maintain(player, state);
            if (state.elapsed % 100 == 0) player.sendTitle(com.districtx.pacifica.util.Items.color("&c&lWasted"), com.districtx.pacifica.util.Items.color("&7" + message), 0, 100, 0);
            state.elapsed++;
        }, 0L, 1L);
        plugin.getServer().getScheduler().runTask(plugin, player.spigot()::respawn);
    }

    public void track(Player player) {
        WastedState state = states.get(player.getUniqueId());
        if (state != null) maintain(player, state);
    }

    public boolean isWasted(Player player) { return states.containsKey(player.getUniqueId()); }

    public Location deathLocation(Player player) {
        WastedState state = states.get(player.getUniqueId());
        return state == null ? null : state.location.clone();
    }

    private void finish(Player player) {
        WastedState state = states.remove(player.getUniqueId());
        if (state == null || !player.isOnline()) return;
        if (state.task != null) state.task.cancel();
        player.setSpectatorTarget(null);
        Location spawn = plugin.store().spawn();
        player.setGameMode(GameMode.ADVENTURE);
        teleportInternally(player, spawn == null ? player.getWorld().getSpawnLocation() : spawn);
        plugin.teleports().applySpawnProtection(player);
        plugin.combatTags().clear(player);
    }

    private void maintain(Player player, WastedState state) {
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
        if (!samePosition(player.getLocation(), state.location)) teleportInternally(player, state.location);
        Player killer = state.killer == null ? null : plugin.getServer().getPlayer(state.killer);
        double range = Math.max(0.0, plugin.store().wasted().getDouble("wasted.killer-tracking-range", 32.0));
        boolean validKiller = killer != null && killer.isOnline() && killer.getWorld().equals(state.location.getWorld()) && killer.getLocation().distanceSquared(state.location) <= range * range;
        if (validKiller) {
            if (player.getSpectatorTarget() != killer) player.setSpectatorTarget(killer);
        } else if (player.getSpectatorTarget() != null) player.setSpectatorTarget(null);
    }

    private void teleportInternally(Player player, Location location) {
        internalTeleports.add(player.getUniqueId());
        try { player.teleport(location); }
        finally { internalTeleports.remove(player.getUniqueId()); }
    }

    private boolean samePosition(Location first, Location second) {
        return first.getWorld() == second.getWorld()
            && first.getX() == second.getX()
            && first.getY() == second.getY()
            && first.getZ() == second.getZ();
    }

    public boolean isInternalTeleport(Player player) { return internalTeleports.contains(player.getUniqueId()); }

    private String randomMessage(String type) {
        List<String> values = plugin.store().combattag().getStringList("messages." + type);
        if (values.isEmpty()) values = plugin.store().combattag().getStringList(type);
        if (values.isEmpty()) values = plugin.store().combattag().getStringList("messages.pvp");
        if (values.isEmpty()) values = plugin.store().combattag().getStringList("pvp");
        return values.isEmpty() ? "You were eliminated." : values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    public void cancel(Player player) {
        WastedState state = states.remove(player.getUniqueId());
        if (state != null && state.task != null) state.task.cancel();
    }

    public void clear() {
        states.values().forEach(state -> {
            if (state.task != null) state.task.cancel();
        });
        states.clear();
        internalTeleports.clear();
    }

    private static final class WastedState {
        private final Location location;
        private final UUID killer;
        private BukkitTask task;
        private int elapsed;
        private WastedState(Location location, UUID killer, BukkitTask task) {
            this.location = location;
            this.killer = killer;
            this.task = task;
        }
    }
}