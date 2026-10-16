package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JailAPI;
import com.districtx.pacifica.api.jobs.JailSession;
import com.districtx.pacifica.api.jobs.event.PlayerReleaseEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/** Owns persistent jail sessions and centralized jail restrictions. */
public final class JailManager implements JailAPI, Listener {
    private final PacificaPlugin plugin;
    private final Map<UUID, JailRecord> sessions = new LinkedHashMap<>();
    private BukkitTask task;
    private long lastCheckpoint;

    public JailManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        loadSessions();
    }

    public void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        checkpoint();
    }

    @Override public boolean isJailed(Player player) { return player != null && isJailed(player.getUniqueId()); }
    @Override public boolean isJailed(UUID playerId) { return playerId != null && sessions.containsKey(playerId); }

    @Override public Optional<JailSession> getSession(UUID playerId) {
        if (playerId == null) return Optional.empty();
        JailRecord record = sessions.get(playerId);
        return record == null ? Optional.empty() : Optional.of(snapshot(playerId, record, System.currentTimeMillis()));
    }

    @Override public void jail(Player player, Duration duration, UUID arrestingCop) {
        jail(player, duration, arrestingCop, player == null ? 0 : plugin.jobsAndWanted().getWantedLevel(player));
    }

    public void jail(Player player, Duration duration, UUID arrestingCop, int wantedStars) {
        createSession(player, duration, arrestingCop, wantedStars, true);
    }

    public void prepareJail(Player player, Duration duration, UUID arrestingCop, int wantedStars) {
        createSession(player, duration, arrestingCop, wantedStars, false);
    }

    public void resumeAfterRespawn(Player player) {
        if (player == null) return;
        JailRecord record = sessions.get(player.getUniqueId());
        if (record == null) return;
        long now = System.currentTimeMillis();
        record.remainingMillis = remaining(record, now);
        if (record.remainingMillis <= 0L) {
            release(player);
            return;
        }
        record.resumedAt = now;
        persist(player.getUniqueId(), record);
        Location jail = getJailLocation();
        if (jail != null) player.teleport(jail);
    }

    private void createSession(Player player, Duration duration, UUID arrestingCop, int wantedStars, boolean teleport) {
        if (player == null || duration == null || duration.isZero() || duration.isNegative()
                || !plugin.store().config().getBoolean("jail.enabled", true) || getJailLocation() == null) return;
        UUID playerId = player.getUniqueId();
        JailRecord record = new JailRecord();
        record.remainingMillis = duration.toMillis();
        record.resumedAt = teleport ? System.currentTimeMillis() : 0L;
        record.arrestingCop = arrestingCop;
        record.wantedStars = Math.max(0, wantedStars);
        sessions.put(playerId, record);
        persist(playerId, record);
        plugin.jobsAndWanted().clearWantedLevel(player, com.districtx.pacifica.api.jobs.event.WantedReason.ARREST);
        if (teleport) {
            player.closeInventory();
            player.teleport(getJailLocation());
        }
    }

    @Override public void release(Player player) {
        if (player == null) return;
        JailRecord record = sessions.remove(player.getUniqueId());
        if (record == null) return;
        JailSession released = snapshot(player.getUniqueId(), record, System.currentTimeMillis());
        plugin.store().data().set("jail.sessions." + player.getUniqueId(), null);
        plugin.store().save();
        Bukkit.getPluginManager().callEvent(new PlayerReleaseEvent(player, released));
        Location spawn = plugin.store().spawn();
        if (player.isOnline() && spawn != null) player.teleport(spawn);
    }

    @Override public Duration getRemainingTime(Player player) {
        if (player == null) return Duration.ZERO;
        JailRecord record = sessions.get(player.getUniqueId());
        return record == null ? Duration.ZERO : Duration.ofMillis(remaining(record, System.currentTimeMillis()));
    }

    @Override public Location getJailLocation() {
        ConfigurationSection section = plugin.store().data().getConfigurationSection("jail.spawn");
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public void setJailLocation(Location location) {
        if (location == null || location.getWorld() == null) return;
        String path = "jail.spawn";
        plugin.store().data().set(path + ".world", location.getWorld().getName());
        plugin.store().data().set(path + ".x", location.getX());
        plugin.store().data().set(path + ".y", location.getY());
        plugin.store().data().set(path + ".z", location.getZ());
        plugin.store().data().set(path + ".yaw", location.getYaw());
        plugin.store().data().set(path + ".pitch", location.getPitch());
        plugin.store().save();
    }

    public Duration configuredSentence(int wantedStars) {
        int stars = Math.max(1, Math.min(5, wantedStars));
        long defaultMillis = switch (stars) {
            case 1 -> 25 * 60_000L;
            case 2 -> 30 * 60_000L;
            case 3 -> 45 * 60_000L;
            case 4 -> 60 * 60_000L;
            default -> 80 * 60_000L;
        };
        return Duration.ofMillis(parseDuration(plugin.store().config().getString("jail.durations." + stars), defaultMillis));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        JailRecord record = sessions.get(event.getPlayer().getUniqueId());
        if (record == null) return;
        if (remaining(record, System.currentTimeMillis()) <= 0L) {
            release(event.getPlayer());
            return;
        }
        record.resumedAt = System.currentTimeMillis();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        JailRecord record = sessions.get(event.getPlayer().getUniqueId());
        if (record == null) return;
        record.remainingMillis = remaining(record, System.currentTimeMillis());
        record.resumedAt = 0L;
        persist(event.getPlayer().getUniqueId(), record);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isJailed(event.getPlayer()) || event.getPlayer().hasPermission("pacifica.jail.admin")) return;
        Location jail = getJailLocation();
        Location destination = event.getTo();
        double radius = Math.max(1.0D, plugin.store().config().getDouble("jail.restriction.radius", 15.0D));
        if (jail == null || destination == null || !withinJail(destination, jail, radius)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isJailed(player) || player.hasPermission("pacifica.jail.admin") || event.getTo() == null) return;
        Location jail = getJailLocation();
        double radius = Math.max(1.0D, plugin.store().config().getDouble("jail.restriction.radius", 15.0D));
        if (jail == null || !withinJail(event.getTo(), jail, radius)) event.setTo(jail == null ? event.getFrom() : jail);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isJailed(player) || player.hasPermission("pacifica.jail.admin")) return;
        String[] parts = event.getMessage().substring(1).split("\\s+", 2);
        String command = parts.length == 0 ? "" : parts[0].toLowerCase(java.util.Locale.ROOT);
        int namespace = command.indexOf(':');
        if (namespace >= 0) command = command.substring(namespace + 1);
        String normalizedCommand = command;
        boolean allowed = plugin.store().config().getStringList("jail.allowed-commands").stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT).replaceFirst("^/", ""))
                .anyMatch(normalizedCommand::equals);
        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage("&cYou cannot use that command while jailed.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isJailed(player) && !player.hasPermission("pacifica.jail.admin")) {
            event.setCancelled(true);
            player.sendMessage("&cYou cannot open menus while jailed.");
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, JailRecord> entry : sessions.entrySet()) {
            if (entry.getValue().resumedAt > 0L && remaining(entry.getValue(), now) <= 0L) expired.add(entry.getKey());
        }
        for (UUID playerId : expired) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) release(player);
            else sessions.remove(playerId);
        }
        if (now - lastCheckpoint >= 15_000L) checkpoint();
    }

    private void checkpoint() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, JailRecord> entry : sessions.entrySet()) {
            JailRecord record = entry.getValue();
            if (record.resumedAt > 0L) {
                record.remainingMillis = remaining(record, now);
                record.resumedAt = now;
            }
            writeRecord(entry.getKey(), record);
        }
        plugin.store().save();
        lastCheckpoint = now;
    }

    private void persist(UUID playerId, JailRecord record) {
        writeRecord(playerId, record);
        plugin.store().save();
    }

    private void writeRecord(UUID playerId, JailRecord record) {
        String path = "jail.sessions." + playerId;
        plugin.store().data().set(path + ".remaining-millis", record.remainingMillis);
        plugin.store().data().set(path + ".arresting-cop", record.arrestingCop == null ? null : record.arrestingCop.toString());
        plugin.store().data().set(path + ".wanted-stars", record.wantedStars);
    }

    private void loadSessions() {
        ConfigurationSection section = plugin.store().data().getConfigurationSection("jail.sessions");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            UUID playerId;
            try { playerId = UUID.fromString(key); }
            catch (IllegalArgumentException ignored) { continue; }
            ConfigurationSection player = section.getConfigurationSection(key);
            if (player == null) continue;
            JailRecord record = new JailRecord();
            record.remainingMillis = Math.max(0L, player.getLong("remaining-millis", 0L));
            record.wantedStars = Math.max(0, player.getInt("wanted-stars", 0));
            String cop = player.getString("arresting-cop");
            try { record.arrestingCop = cop == null ? null : UUID.fromString(cop); }
            catch (IllegalArgumentException ignored) { record.arrestingCop = null; }
            if (record.remainingMillis > 0L) sessions.put(playerId, record);
            else plugin.store().data().set("jail.sessions." + key, null);
        }
    }

    private JailSession snapshot(UUID playerId, JailRecord record, long now) {
        return new JailSession(playerId, record.arrestingCop, record.wantedStars,
                Duration.ofMillis(remaining(record, now)));
    }

    private long remaining(JailRecord record, long now) {
        if (record.resumedAt <= 0L) return record.remainingMillis;
        return Math.max(0L, record.remainingMillis - Math.max(0L, now - record.resumedAt));
    }

    private static boolean withinJail(Location location, Location jail, double radius) {
        return location.getWorld() != null && jail.getWorld() != null
                && location.getWorld().equals(jail.getWorld()) && location.distanceSquared(jail) <= radius * radius;
    }

    private static long parseDuration(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        String input = value.trim().toLowerCase(java.util.Locale.ROOT);
        char suffix = input.charAt(input.length() - 1);
        long multiplier = 60_000L;
        if (Character.isLetter(suffix)) {
            input = input.substring(0, input.length() - 1).trim();
            multiplier = switch (suffix) {
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                case 's' -> 1_000L;
                default -> -1L;
            };
        }
        if (multiplier < 0L) return fallback;
        try {
            long amount = Long.parseLong(input);
            return amount <= 0L ? fallback : Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException | NumberFormatException exception) {
            return fallback;
        }
    }

    private static final class JailRecord {
        private long remainingMillis;
        private long resumedAt;
        private UUID arrestingCop;
        private int wantedStars;
    }
}