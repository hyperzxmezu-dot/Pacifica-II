package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.taxi.TaxiReason;
import com.districtx.pacifica.api.taxi.TaxiRequest;
import com.districtx.pacifica.api.taxi.TaxiResult;
import com.districtx.pacifica.api.taxi.TeleportRequest;
import com.districtx.pacifica.api.taxi.event.TaxiCancelEvent;
import com.districtx.pacifica.api.taxi.event.TaxiCompleteEvent;
import com.districtx.pacifica.api.taxi.event.TaxiStartEvent;
import com.districtx.pacifica.util.Items;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class TeleportManager {
    public enum TeleportContext { SPAWN, QUICK_PLAY, RANDOM_WARP, NAMED_WARP, PLAYER_TPA, TAXI }

    private final PacificaPlugin plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, BukkitTask> protectionTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> externalProtectionTasks = new HashMap<>();
    private final Set<UUID> managedTeleports = new HashSet<>();
    private final Set<UUID> externalProtectionRequests = new HashSet<>();

    public TeleportManager(PacificaPlugin plugin) { this.plugin = plugin; }

    public void start(Player player, Location destination, boolean guarded, Runnable success) {
        start(player, destination, guarded, null, java.util.Collections.emptyMap(), success, reasonFor(null), false, false);
    }

    public void start(Player player, Location destination, boolean guarded, TeleportContext context, Map<String, String> values) {
        start(player, destination, guarded, context, values, null, reasonFor(context), false, context == TeleportContext.TAXI);
    }

    public void start(Player player, Location destination, boolean guarded, TeleportContext context, Map<String, String> values, TaxiReason reason) {
        start(player, destination, guarded, context, values, null, reason, false, context == TeleportContext.TAXI);
    }

    public TaxiResult startApi(Player player, Location destination, TaxiReason reason) {
        TaxiReason effectiveReason = reason == null ? TaxiReason.OTHER : reason;
        return start(player, destination, false, contextFor(effectiveReason), java.util.Collections.emptyMap(), null,
                effectiveReason, true, effectiveReason == TaxiReason.TAXI || effectiveReason == TaxiReason.HOUSE);
    }

    public boolean isTeleporting(Player player) {
        return player != null && pendingTeleports.containsKey(player.getUniqueId());
    }

    public java.util.Optional<TeleportRequest> getTeleportRequest(Player player) {
        if (player == null) return java.util.Optional.empty();
        PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
        return pending == null ? java.util.Optional.empty() : java.util.Optional.of(pending.snapshot(player.getUniqueId()));
    }

    public java.util.Optional<TaxiRequest> getTaxiRequest(Player player) {
        if (player == null) return java.util.Optional.empty();
        PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
        if (pending == null || !pending.taxiRequest) return java.util.Optional.empty();
        return java.util.Optional.of(new TaxiRequest(player.getUniqueId(), pending.destination, pending.remainingSeconds, pending.reason));
    }

    private TaxiResult start(Player player, Location destination, boolean guarded, TeleportContext context, Map<String, String> values, Runnable success, TaxiReason reason, boolean rejectIfAlreadyTeleporting, boolean taxiRequest) {
        if (plugin.combatTags().isTagged(player)) {
            player.sendMessage(plugin.store().message(player, "combattag.teleport-blocked"));
            return TaxiResult.COMBAT_TAGGED;
        }
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(plugin.store().message(player, "teleport.destination-unavailable"));
            return TaxiResult.INVALID_DESTINATION;
        }
        if (rejectIfAlreadyTeleporting && isTeleporting(player)) {
            player.sendMessage(plugin.store().message(player, "teleport.failed"));
            return TaxiResult.ALREADY_TELEPORTING;
        }
        Location target = destination.clone();
        TaxiReason effectiveReason = reason == null ? TaxiReason.OTHER : reason;
        TaxiStartEvent startEvent = new TaxiStartEvent(player, target, effectiveReason);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            player.sendMessage(plugin.store().message(player, "teleport.failed"));
            return TaxiResult.TELEPORT_CANCELLED;
        }
        cancel(player, null);
        Location origin = player.getLocation().clone();
        int total = countdownSeconds(effectiveReason,
                plugin.store().teleport().getInt("teleport.quick-play-countdown-seconds", 5),
                plugin.store().teleport().getInt("teleport.countdown-seconds", 10));
        int effectDuration = plugin.store().teleport().getInt("teleport.final-effects-duration-seconds", 2) * 20;
        String sound = plugin.store().teleport().getString("teleport.sound", "UI_BUTTON_CLICK");
        BukkitTask task = new BukkitRunnable() {
            int elapsed;
            @Override public void run() {
                if (!player.isOnline()) { TeleportManager.this.cancel(player, null); return; }
                if (plugin.store().teleport().getBoolean("teleport.cancel-on-move", true) && moved(origin, player.getLocation())) {
                    TeleportManager.this.cancel(player, plugin.store().message(player, "teleport.cancelled-move"));
                    return;
                }
                elapsed++;
                int remaining = total - elapsed;
                PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
                if (pending != null) pending.remainingSeconds = Math.max(0, remaining);
                if (remaining == 1) {
                    Items.blindness(player, effectDuration);
                    Items.invisibility(player, effectDuration);
                }
                if (remaining > 0 && remaining <= 5) player.sendMessage(countdownMessage(context, values, remaining));
                if (remaining > 0 && plugin.store().teleport().getIntegerList("teleport.countdown-sound-thresholds").contains(remaining)) Items.clickSound(player, sound);
                if (remaining <= 0) {
                    tasks.remove(player.getUniqueId());
                    pendingTeleports.remove(player.getUniqueId());
                    managedTeleports.add(player.getUniqueId());
                    boolean teleported;
                    try {
                        teleported = player.teleport(target);
                    } finally {
                        managedTeleports.remove(player.getUniqueId());
                    }
                    if (teleported) {
                        updateProtectionForTransition(player, origin, player.getLocation());
                        Items.clickSound(player, plugin.store().teleport().getString("teleport.success-sound", "BLOCK_FIRE_EXTINGUISH"));
                        player.sendMessage(successMessage(effectiveReason, values));
                        Bukkit.getPluginManager().callEvent(new TaxiCompleteEvent(player, target, effectiveReason));
                        if (success != null) success.run();
                    } else {
                        player.sendMessage(plugin.store().message(player, "teleport.failed"));
                        Bukkit.getPluginManager().callEvent(new TaxiCancelEvent(player, effectiveReason));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        tasks.put(player.getUniqueId(), task);
        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(target, total, effectiveReason, taxiRequest));
        player.sendMessage(plugin.store().message(player, "teleport.called"));
        if (total > 5) player.sendMessage(plugin.store().message(player, "teleport.countdown-start", "%time%", String.valueOf(total)));
        else if (total > 0) player.sendMessage(countdownMessage(context, values, total));
        Items.clickSound(player, sound);
        return TaxiResult.SUCCESS;
    }

    private void scheduleProtectionExpiry(Player player) {
        BukkitTask previous = protectionTasks.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        long duration = Math.max(0L, plugin.store().spawnConfig().getLong("spawn-protection.duration-seconds", 7L)) * 20L;
        if (duration == 0L) {
            plugin.statuses().set(player, false);
            return;
        }
        plugin.statuses().set(player, true);
        player.sendMessage(plugin.store().message(player, "spawn-protection.started", "%time%", String.valueOf(duration / 20L)));
        protectionTasks.put(player.getUniqueId(), new BukkitRunnable() {
            @Override public void run() {
                protectionTasks.remove(player.getUniqueId());
                if (player.isOnline()) plugin.statuses().set(player, false);
            }
        }.runTaskLater(plugin, duration));
    }

    public void applySpawnProtection(Player player) {
        if (plugin.safeSpawn().contains(player.getLocation())) clearProtection(player);
    }

    public void handleSafeSpawnMovement(Player player, Location from, Location to) {
        boolean wasInside = plugin.safeSpawn().contains(from);
        boolean isInside = plugin.safeSpawn().contains(to);
        if (wasInside == isInside) return;
        if (SafeSpawnRegion.exited(wasInside, isInside)) scheduleProtectionExpiry(player);
        else clearProtection(player);
    }

    private void updateProtectionForTransition(Player player, Location source, Location destination) {
        boolean wasInside = source != null && plugin.safeSpawn().contains(source);
        boolean isInside = destination != null && plugin.safeSpawn().contains(destination);
        if (SafeSpawnRegion.exited(wasInside, isInside)) {
            scheduleProtectionExpiry(player);
        } else {
            clearProtection(player);
        }
    }

    static int countdownSeconds(TaxiReason reason, int quickPlaySeconds, int normalSeconds) {
        return reason == TaxiReason.QUICK_PLAY ? quickPlaySeconds : normalSeconds;
    }

    private void clearProtection(Player player) {
        cancelProtectionTask(player);
        plugin.statuses().set(player, false);
    }

    /** Clears a player's active spawn-protection session and transition state. */
    public void clearSpawnProtection(Player player) {
        UUID playerId = player.getUniqueId();
        cancelProtectionTask(player);
        BukkitTask externalTask = externalProtectionTasks.remove(playerId);
        if (externalTask != null) externalTask.cancel();
        externalProtectionRequests.remove(playerId);
        plugin.statuses().clear(player);
    }

    public void handleExternalTeleport(Player player, Location destination) {
        handleExternalTeleport(player, player.getLocation(), destination);
    }

    /** Applies Safe Spawn transition rules to a successful external teleport. */
    public void handleExternalTeleport(Player player, Location source, Location destination) {
        UUID playerId = player.getUniqueId();
        externalProtectionRequests.remove(playerId);
        BukkitTask requestTask = externalProtectionTasks.remove(playerId);
        if (requestTask != null) requestTask.cancel();
        updateProtectionForTransition(player, source, destination);
    }

    public void requestExternalProtection(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask previous = externalProtectionTasks.remove(playerId);
        if (previous != null) previous.cancel();
        externalProtectionRequests.add(playerId);
        externalProtectionTasks.put(playerId, new BukkitRunnable() {
            @Override public void run() {
                externalProtectionRequests.remove(playerId);
                externalProtectionTasks.remove(playerId);
            }
        }.runTaskLater(plugin, 200L));
    }

    public boolean isManagedTeleport(Player player) {
        return managedTeleports.contains(player.getUniqueId());
    }

    private String successMessage(TaxiReason reason, Map<String, String> values) {
        String path = switch (reason == null ? TaxiReason.OTHER : reason) {
            case SPAWN -> "teleport.spawn-success";
            case QUICK_PLAY, RANDOM_WARP, WARP -> "teleport.warp-success";
            case TPA -> "teleport.player-success";
            case HOUSE, TAXI, OTHER -> "teleport.success";
        };
        String message = plugin.store().message(path);
        for (Map.Entry<String, String> entry : values.entrySet()) message = message.replace(entry.getKey(), entry.getValue());
        return message;
    }

    private String countdownMessage(TeleportContext context, Map<String, String> values, int remaining) {
        String path = context == TeleportContext.PLAYER_TPA ? "teleport.player-countdown" : "teleport.countdown";
        String message = plugin.store().message(path, "%time%", String.valueOf(remaining));
        for (Map.Entry<String, String> entry : values.entrySet()) message = message.replace(entry.getKey(), entry.getValue());
        return message;
    }

    private boolean moved(Location a, Location b) {
        return a.getWorld() != b.getWorld() || a.getBlockX() != b.getBlockX() || a.getBlockY() != b.getBlockY() || a.getBlockZ() != b.getBlockZ();
    }

    public void cancel(Player player, String message) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (task != null) task.cancel();
        if (pending != null) Bukkit.getPluginManager().callEvent(new TaxiCancelEvent(player, pending.reason));
        if (message != null) player.sendMessage(message);
    }

    private void cancelProtectionTask(Player player) {
        BukkitTask task = protectionTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void clear() {
        tasks.values().forEach(BukkitTask::cancel);
        pendingTeleports.clear();
        protectionTasks.values().forEach(BukkitTask::cancel);
        externalProtectionTasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
        protectionTasks.clear();
        externalProtectionTasks.clear();
        managedTeleports.clear();
        externalProtectionRequests.clear();
    }

    private TaxiReason reasonFor(TeleportContext context) {
        if (context == null) return TaxiReason.OTHER;
        return switch (context) {
            case SPAWN -> TaxiReason.SPAWN;
            case QUICK_PLAY -> TaxiReason.QUICK_PLAY;
            case RANDOM_WARP -> TaxiReason.RANDOM_WARP;
            case NAMED_WARP -> TaxiReason.WARP;
            case PLAYER_TPA -> TaxiReason.TPA;
            case TAXI -> TaxiReason.TAXI;
        };
    }

    private TeleportContext contextFor(TaxiReason reason) {
        return switch (reason) {
            case SPAWN -> TeleportContext.SPAWN;
            case QUICK_PLAY -> TeleportContext.QUICK_PLAY;
            case RANDOM_WARP -> TeleportContext.RANDOM_WARP;
            case WARP -> TeleportContext.NAMED_WARP;
            case TPA -> TeleportContext.PLAYER_TPA;
            case TAXI, HOUSE, OTHER -> TeleportContext.TAXI;
        };
    }

    private static final class PendingTeleport {
        private final Location destination;
        private final TaxiReason reason;
        private final boolean taxiRequest;
        private int remainingSeconds;

        private PendingTeleport(Location destination, int remainingSeconds, TaxiReason reason, boolean taxiRequest) {
            this.destination = destination.clone();
            this.remainingSeconds = remainingSeconds;
            this.reason = reason;
            this.taxiRequest = taxiRequest;
        }

        private TeleportRequest snapshot(UUID playerId) {
            return new TeleportRequest(playerId, destination, remainingSeconds, reason);
        }
    }
}