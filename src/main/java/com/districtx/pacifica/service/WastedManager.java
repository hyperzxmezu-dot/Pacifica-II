package com.districtx.pacifica.service;

import com.cryptomorin.xseries.XSound;
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
        UUID playerId = player.getUniqueId();
        if (states.containsKey(playerId)) return;
        Location deathLocation = player.getLocation().clone();
        WastedState state = new WastedState(deathLocation, killer == null ? null : killer.getUniqueId(), player.getWalkSpeed(), player.getFlySpeed(), player.getGameMode(), player.getAllowFlight(), player.isFlying());
        states.put(playerId, state);
        String message = randomMessage(type);
        long durationTicks = Math.max(1, plugin.store().wasted().getInt("wasted.duration-seconds", 8)) * 20L;
        player.sendTitle(com.districtx.pacifica.util.Items.color("&c&lWASTED"), com.districtx.pacifica.util.Items.color("&7" + message), 0, (int) durationTicks, 0);
        XSound.matchXSound("ENTITY_WITHER_SPAWN").ifPresent(sound -> sound.play(player, 1.0f, 1.0f));
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setGameMode(GameMode.SPECTATOR);
        state.respawnTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> finish(player), durationTicks);
        plugin.getServer().getScheduler().runTask(plugin, player.spigot()::respawn);
    }

    public void track(Player player) {
        WastedState state = states.get(player.getUniqueId());
        if (state == null || !player.isOnline() || player.isDead()) return;
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
        Player killer = state.killer == null ? null : plugin.getServer().getPlayer(state.killer);
        boolean validKiller = killer != null && killer.isOnline() && killer.isValid() && !killer.isDead() && killer.getWorld().equals(player.getWorld());
        if (validKiller) {
            if (player.getSpectatorTarget() != killer) player.setSpectatorTarget(killer);
        } else if (player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }
    }

    /** Clears spectator cameras tracking a player who is no longer a valid target. */
    public void targetUnavailable(Player target) {
        UUID targetId = target.getUniqueId();
        for (Map.Entry<UUID, WastedState> entry : states.entrySet()) {
            if (!targetId.equals(entry.getValue().killer)) continue;
            Player spectator = plugin.getServer().getPlayer(entry.getKey());
            if (spectator != null && spectator.isOnline() && spectator.getSpectatorTarget() == target) {
                spectator.setSpectatorTarget(null);
            }
        }
    }

    public boolean isWasted(Player player) { return states.containsKey(player.getUniqueId()); }

    public Location deathLocation(Player player) {
        WastedState state = states.get(player.getUniqueId());
        return state == null ? null : state.location.clone();
    }

    private void finish(Player player) {
        WastedState state = states.remove(player.getUniqueId());
        if (state == null) return;
        cancelTasks(state);
        if (!player.isOnline()) return;
        restoreTemporaryState(player, state);
        Location spawn = plugin.store().spawn();
        teleportInternally(player, spawn == null ? player.getWorld().getSpawnLocation() : spawn);
        plugin.combatTags().clear(player);
    }

    private void teleportInternally(Player player, Location location) {
        internalTeleports.add(player.getUniqueId());
        try { player.teleport(location); }
        finally { internalTeleports.remove(player.getUniqueId()); }
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
        if (state == null) return;
        cancelTasks(state);
        if (player.isOnline()) restoreTemporaryState(player, state);
    }

    public void clear() {
        states.forEach((playerId, state) -> {
            cancelTasks(state);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) restoreTemporaryState(player, state);
        });
        states.clear();
        internalTeleports.clear();
    }

    private void cancelTasks(WastedState state) {
        if (state.respawnTask != null) state.respawnTask.cancel();
    }

    private void restoreTemporaryState(Player player, WastedState state) {
        player.setSpectatorTarget(null);
        player.setGameMode(state.gameMode);
        player.setAllowFlight(state.allowFlight);
        if (state.allowFlight) player.setFlying(state.flying);
        player.setWalkSpeed(state.walkSpeed);
        player.setFlySpeed(state.flySpeed);
    }

    private static final class WastedState {
        private final Location location;
        private final UUID killer;
        private final float walkSpeed;
        private final float flySpeed;
        private final GameMode gameMode;
        private final boolean allowFlight;
        private final boolean flying;
        private BukkitTask respawnTask;
        private WastedState(Location location, UUID killer, float walkSpeed, float flySpeed, GameMode gameMode, boolean allowFlight, boolean flying) {
            this.location = location;
            this.killer = killer;
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
            this.gameMode = gameMode;
            this.allowFlight = allowFlight;
            this.flying = flying;
        }
    }
}