package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.BountyAPI;
import com.districtx.pacifica.api.jobs.BountyPlacementResult;
import com.districtx.pacifica.api.jobs.JobType;
import com.districtx.pacifica.api.jobs.event.BountyClaimedEvent;
import com.districtx.pacifica.api.jobs.event.BountyCreatedEvent;
import com.districtx.pacifica.api.jobs.event.BountyHeadDropEvent;
import com.districtx.pacifica.api.jobs.event.BountyRemovedEvent;
import com.districtx.pacifica.api.jobs.event.RandomBountyAssignedEvent;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Persists bounties and manages compass tracking and one-time Hitman claims. */
public final class BountyManager implements BountyAPI, Listener {
    private final PacificaPlugin plugin;
    private final Map<UUID, BigDecimal> bounties = new HashMap<>();
    private final Map<UUID, UUID> trackers = new HashMap<>();
    private final Set<UUID> claimsInProgress = new HashSet<>();
    private BukkitTask trackerTask;
    private BukkitTask randomBountyTask;

    public BountyManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void start() {
        if (trackerTask == null) trackerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTrackers, 20L, 20L);
        if (randomBountyTask == null && plugin.store().config().getBoolean("bounty.random.enabled", true)) {
            long intervalTicks = ticksForDuration(plugin.store().config().getString("bounty.random.interval", "30m"));
            randomBountyTask = Bukkit.getScheduler().runTaskTimer(plugin, this::attemptRandomBounty, intervalTicks, intervalTicks);
        }
    }

    public void stop() {
        if (trackerTask != null) trackerTask.cancel();
        trackerTask = null;
        if (randomBountyTask != null) randomBountyTask.cancel();
        randomBountyTask = null;
        for (UUID hitmanId : trackers.keySet()) {
            Player hitman = Bukkit.getPlayer(hitmanId);
            if (hitman != null) hitman.setCompassTarget(hitman.getLocation());
        }
        trackers.clear();
        claimsInProgress.clear();
    }

    @Override public synchronized Optional<BigDecimal> getBounty(UUID targetId) {
        return targetId == null ? Optional.empty() : Optional.ofNullable(bounties.get(targetId));
    }

    @Override public synchronized boolean setBounty(UUID targetId, BigDecimal amount) {
        if (!Bukkit.isPrimaryThread() || targetId == null || !JobsWantedRules.isValidBounty(amount, maximumBounty())) return false;
        BigDecimal normalized = amount.stripTrailingZeros();
        BountyCreatedEvent createdEvent = new BountyCreatedEvent(targetId, normalized);
        Bukkit.getPluginManager().callEvent(createdEvent);
        if (createdEvent.isCancelled()) return false;
        BigDecimal previous = bounties.put(targetId, normalized);
        plugin.store().data().set("bounty.targets." + targetId, normalized.toPlainString());
        try {
            plugin.store().save();
        } catch (RuntimeException exception) {
            if (previous == null) {
                bounties.remove(targetId);
                plugin.store().data().set("bounty.targets." + targetId, null);
            } else {
                bounties.put(targetId, previous);
                plugin.store().data().set("bounty.targets." + targetId, previous.toPlainString());
            }
            plugin.getLogger().warning("Could not persist bounty for " + targetId + ": " + exception.getMessage());
            return false;
        }
        return true;
    }

    public synchronized BountyPlacementResult validatePlayerBountyAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return BountyPlacementResult.INVALID_AMOUNT;
        BigDecimal minimum = minimumPlayerBounty();
        BigDecimal maximum = maximumPlayerBounty();
        if (minimum.compareTo(maximum) > 0) return BountyPlacementResult.TRANSACTION_FAILED;
        if (amount.compareTo(minimum) < 0) return BountyPlacementResult.AMOUNT_TOO_LOW;
        if (amount.compareTo(maximum) > 0) return BountyPlacementResult.AMOUNT_TOO_HIGH;
        return BountyPlacementResult.SUCCESS;
    }

    public synchronized boolean isValidPlayerBountyTarget(UUID targetId) {
        if (!Bukkit.isPrimaryThread() || targetId == null) return false;
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetId);
        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget == null && (!offlineTarget.hasPlayedBefore() || offlineTarget.getName() == null)) return false;
        if (onlineTarget != null && (!onlineTarget.isOnline() || !onlineTarget.isValid() || onlineTarget.isDead()
                || onlineTarget.hasMetadata("NPC"))) return false;
        if (plugin.store().config().getBoolean("bounty.random.exclude-operators", true) && offlineTarget.isOp()) return false;
        String name = offlineTarget.getName();
        if (name != null && plugin.store().config().getStringList("bounty.random.excluded-players").stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(name) || entry.equalsIgnoreCase(targetId.toString()))) return false;
        String excludedPermission = plugin.store().config().getString("bounty.random.excluded-permission", "pacifica.bounty.random.bypass");
        return onlineTarget == null || excludedPermission == null || excludedPermission.isBlank()
                || !onlineTarget.hasPermission(excludedPermission);
    }

    public synchronized BountyPlacementResult placePlayerBounty(Player creator, UUID targetId, BigDecimal amount) {
        if (!Bukkit.isPrimaryThread() || creator == null || !creator.isOnline()) return BountyPlacementResult.TRANSACTION_FAILED;
        if (plugin.jobsAndWanted().getJob(creator) != JobType.HITMAN) return BountyPlacementResult.NOT_HITMAN;
        if (!isValidPlayerBountyTarget(targetId)) return BountyPlacementResult.INVALID_TARGET;
        BountyPlacementResult amountResult = validatePlayerBountyAmount(amount);
        if (amountResult != BountyPlacementResult.SUCCESS) return amountResult;

        BigDecimal previous = bounties.get(targetId);
        BigDecimal updated = (previous == null ? BigDecimal.ZERO : previous).add(amount).stripTrailingZeros();
        if (!JobsWantedRules.isValidBounty(updated, maximumBounty())) return BountyPlacementResult.MAXIMUM_REWARD_EXCEEDED;
        if (!plugin.economy().hasBalance(creator, amount)) return BountyPlacementResult.INSUFFICIENT_FUNDS;
        if (!setBounty(targetId, updated)) return BountyPlacementResult.TRANSACTION_FAILED;
        if (!plugin.economy().hasBalance(creator, amount) || !plugin.economy().withdraw(creator, amount)) {
            if (!restoreBounty(targetId, updated, previous)) return BountyPlacementResult.TRANSACTION_FAILED;
            return BountyPlacementResult.INSUFFICIENT_FUNDS;
        }
        return BountyPlacementResult.SUCCESS;
    }

    @Override public synchronized boolean removeBounty(UUID targetId) {
        if (!Bukkit.isPrimaryThread() || targetId == null) return false;
        BigDecimal amount = bounties.get(targetId);
        if (amount == null) return false;
        bounties.remove(targetId);
        plugin.store().data().set("bounty.targets." + targetId, null);
        try {
            plugin.store().save();
        } catch (RuntimeException exception) {
            bounties.put(targetId, amount);
            plugin.store().data().set("bounty.targets." + targetId, amount.toPlainString());
            plugin.getLogger().warning("Could not persist bounty removal for " + targetId + ": " + exception.getMessage());
            return false;
        }
        clearTrackersFor(targetId);
        Bukkit.getPluginManager().callEvent(new BountyRemovedEvent(targetId, amount));
        return true;
    }

    @Override public synchronized boolean hasBounty(UUID targetId) {
        return targetId != null && bounties.containsKey(targetId);
    }

    @Override public synchronized Optional<BigDecimal> claimBounty(UUID targetId, UUID killerId) {
        Optional<BigDecimal> claimed = claimBountyInternal(targetId, killerId);
        if (claimed.isPresent()) {
            Player target = Bukkit.getPlayer(targetId);
            Player killer = Bukkit.getPlayer(killerId);
            if (target != null && killer != null) {
                Bukkit.getPluginManager().callEvent(new BountyClaimedEvent(killer, target, claimed.get()));
            }
        }
        return claimed;
    }

    private synchronized Optional<BigDecimal> claimBountyInternal(UUID targetId, UUID killerId) {
        if (!Bukkit.isPrimaryThread() || targetId == null || killerId == null || !claimsInProgress.add(targetId)) {
            return Optional.empty();
        }
        try {
            Player target = Bukkit.getPlayer(targetId);
            Player killer = Bukkit.getPlayer(killerId);
            BigDecimal amount = bounties.get(targetId);
            if (target == null || !target.isOnline() || killer == null || !killer.isOnline()
                    || plugin.jobsAndWanted().getJob(killer) != JobType.HITMAN || amount == null) return Optional.empty();
            bounties.remove(targetId);
            plugin.store().data().set("bounty.targets." + targetId, null);
            try {
                plugin.store().save();
            } catch (RuntimeException exception) {
                bounties.put(targetId, amount);
                plugin.store().data().set("bounty.targets." + targetId, amount.toPlainString());
                plugin.getLogger().warning("Could not persist bounty claim for " + targetId + ": " + exception.getMessage());
                return Optional.empty();
            }
            clearTrackersFor(targetId);
            Bukkit.getPluginManager().callEvent(new BountyRemovedEvent(targetId, amount));
            return Optional.of(amount);
        } finally {
            claimsInProgress.remove(targetId);
        }
    }

    @Override public Optional<UUID> getRandomEligibleTarget() {
        if (!Bukkit.isPrimaryThread()) return Optional.empty();
        List<Player> candidates = new ArrayList<>();
        String excludedPermission = plugin.store().config().getString("bounty.random.excluded-permission", "pacifica.bounty.random.bypass");
        Set<String> excludedPlayers = new HashSet<>(plugin.store().config().getStringList("bounty.random.excluded-players"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || !player.isValid() || player.isDead() || player.hasMetadata("NPC")
                    || hasBounty(player.getUniqueId())) continue;
            if (plugin.store().config().getBoolean("bounty.random.exclude-operators", true) && player.isOp()) continue;
            if (excludedPermission != null && !excludedPermission.isBlank() && player.hasPermission(excludedPermission)) continue;
            boolean excluded = excludedPlayers.stream().anyMatch(entry -> entry.equalsIgnoreCase(player.getName())
                    || entry.equalsIgnoreCase(player.getUniqueId().toString()));
            if (!excluded) candidates.add(player);
        }
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).getUniqueId());
    }

    @Override public boolean assignTracker(Player hitman, UUID targetId) {
        if (hitman == null || targetId == null || !hitman.isOnline()
                || plugin.jobsAndWanted().getJob(hitman) != JobType.HITMAN || !hitman.hasPermission("pacifica.bounty.track")
                || !hasBounty(targetId)) return false;
        trackers.put(hitman.getUniqueId(), targetId);
        updateTracker(hitman.getUniqueId(), targetId);
        return true;
    }

    @Override public Optional<UUID> getTrackedTarget(Player hitman) {
        return hitman == null ? Optional.empty() : Optional.ofNullable(trackers.get(hitman.getUniqueId()));
    }

    @Override public void clearTrackedTarget(Player hitman) {
        if (hitman == null) return;
        trackers.remove(hitman.getUniqueId());
        hitman.setCompassTarget(hitman.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player target = event.getEntity();
        Player killer = target.getKiller();
        UUID targetId = target.getUniqueId();
        if (killer != null && killer.isOnline() && plugin.jobsAndWanted().getJob(killer) == JobType.HITMAN) {
            claimBountyInternal(targetId, killer.getUniqueId()).ifPresent(reward -> {
                if (plugin.economy().deposit(killer, reward)) {
                    killer.sendMessage(Items.color("&aBounty collected: $" + reward.toPlainString()));
                    Bukkit.getPluginManager().callEvent(new BountyClaimedEvent(killer, target, reward));
                    processHeadDrop(event, killer, target, reward);
                } else {
                    setBounty(targetId, reward);
                }
            });
        }
        clearTrackersFor(targetId);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        clearTrackedTarget(event.getPlayer());
        clearTrackersFor(playerId);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID targetId = event.getPlayer().getUniqueId();
        for (Map.Entry<UUID, UUID> entry : trackers.entrySet()) {
            if (entry.getValue().equals(targetId)) updateTracker(entry.getKey(), targetId);
        }
    }

    private void load() {
        ConfigurationSection section = plugin.store().data().getConfigurationSection("bounty.targets");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID targetId = UUID.fromString(key);
                BigDecimal amount = new BigDecimal(section.getString(key, "0"));
                if (amount.signum() > 0 && amount.compareTo(maximumBounty()) <= 0) bounties.put(targetId, amount);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void attemptRandomBounty() {
        Optional<UUID> targetId = getRandomEligibleTarget();
        if (targetId.isEmpty()) return;
        BigDecimal amount = randomBountyAmount();
        if (amount == null || !setBounty(targetId.get(), amount)) return;
        Player target = Bukkit.getPlayer(targetId.get());
        if (target == null) return;
        if (plugin.store().config().getBoolean("bounty.broadcast.enabled", true)) {
            Bukkit.broadcastMessage(plugin.store().message("bounty.assigned",
                    "%player%", target.getName(),
                    "%uuid%", target.getUniqueId().toString(),
                    "%amount%", amount.toPlainString()));
        }
        Bukkit.getPluginManager().callEvent(new RandomBountyAssignedEvent(target, amount));
    }

    private void processHeadDrop(PlayerDeathEvent deathEvent, Player hitman, Player target, BigDecimal amount) {
        if (!plugin.store().config().getBoolean("bounty.head-drop.enabled", true)) return;
        double configuredChance = plugin.store().config().getDouble("bounty.head-drop.chance-percent", 0.10D);
        double chance = Double.isFinite(configuredChance) ? Math.max(0.0D, Math.min(100.0D, configuredChance)) : 0.10D;
        BountyHeadDropEvent headDropEvent = new BountyHeadDropEvent(hitman, target, amount, chance);
        Bukkit.getPluginManager().callEvent(headDropEvent);
        if (headDropEvent.isCancelled() || !shouldDropHead(chance, ThreadLocalRandom.current().nextDouble())) return;
        org.bukkit.inventory.ItemStack head = Items.head(target, "&c" + target.getName() + "'s Head", "&6Bounty target");
        if (head == null) return;
        deathEvent.getDrops().add(head);
        if (plugin.store().config().getBoolean("bounty.head-drop.broadcast", false)) {
            Bukkit.broadcastMessage(plugin.store().message("bounty.head-drop",
                    "%hitman%", hitman.getName(), "%target%", target.getName()));
        }
    }

    static boolean shouldDropHead(double chancePercent, double roll) {
        return Double.isFinite(chancePercent) && chancePercent > 0.0D && chancePercent <= 100.0D
                && roll < chancePercent / 100.0D;
    }

    private BigDecimal randomBountyAmount() {
        try {
            BigDecimal minimum = new BigDecimal(plugin.store().config().getString("bounty.random.minimum", "10000"));
            BigDecimal maximum = new BigDecimal(plugin.store().config().getString("bounty.random.maximum", "1000000"));
            BigDecimal configuredMaximum = maximumBounty();
            if (maximum.compareTo(configuredMaximum) > 0) maximum = configuredMaximum;
            if (minimum.signum() <= 0 || minimum.compareTo(maximum) > 0) return null;
            int scale = Math.max(0, Math.max(minimum.scale(), maximum.scale()));
            BigInteger lower = minimum.setScale(scale).unscaledValue();
            BigInteger upper = maximum.setScale(scale).unscaledValue();
            BigInteger bound = upper.subtract(lower).add(BigInteger.ONE);
            BigInteger offset;
            do {
                offset = new BigInteger(bound.bitLength(), ThreadLocalRandom.current());
            } while (offset.compareTo(bound) >= 0);
            return new BigDecimal(lower.add(offset), scale).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private long ticksForDuration(String value) {
        long fallbackMillis = 1_800_000L;
        long millis = parseDuration(value, fallbackMillis);
        return Math.max(1L, millis / 50L + (millis % 50L == 0L ? 0L : 1L));
    }

    private long parseDuration(String value, long fallback) {
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

    private BigDecimal maximumBounty() {
        try { return new BigDecimal(plugin.store().config().getString("bounty.max-reward", "1000000")); }
        catch (NumberFormatException exception) { return new BigDecimal("1000000"); }
    }

    private BigDecimal minimumPlayerBounty() {
        try {
            BigDecimal minimum = new BigDecimal(plugin.store().config().getString("bounty.player-placement.minimum", "1"));
            return minimum.signum() > 0 ? minimum : BigDecimal.ONE;
        } catch (NumberFormatException exception) {
            return BigDecimal.ONE;
        }
    }

    private BigDecimal maximumPlayerBounty() {
        BigDecimal configured;
        try {
            configured = new BigDecimal(plugin.store().config().getString("bounty.player-placement.maximum", "1000000"));
        } catch (NumberFormatException exception) {
            configured = maximumBounty();
        }
        if (configured.signum() <= 0) configured = maximumBounty();
        return configured.min(maximumBounty());
    }

    private boolean restoreBounty(UUID targetId, BigDecimal committedAmount, BigDecimal previous) {
        if (previous == null) {
            bounties.remove(targetId);
            plugin.store().data().set("bounty.targets." + targetId, null);
        } else {
            bounties.put(targetId, previous);
            plugin.store().data().set("bounty.targets." + targetId, previous.toPlainString());
        }
        try {
            plugin.store().save();
            return true;
        } catch (RuntimeException exception) {
            bounties.put(targetId, committedAmount);
            plugin.store().data().set("bounty.targets." + targetId, committedAmount.toPlainString());
            plugin.getLogger().severe("Could not roll back an uncharged bounty for " + targetId + ": " + exception.getMessage());
            return false;
        }
    }

    private void updateTrackers() {
        Iterator<Map.Entry<UUID, UUID>> iterator = trackers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            Player target = Bukkit.getPlayer(entry.getValue());
            Player hitman = Bukkit.getPlayer(entry.getKey());
            if (target == null || hitman == null || !hasBounty(entry.getValue())
                    || plugin.jobsAndWanted().getJob(hitman) != JobType.HITMAN) {
                if (hitman != null) hitman.setCompassTarget(hitman.getLocation());
                iterator.remove();
                continue;
            }
            setCompassTarget(hitman, target);
        }
    }

    private void updateTracker(UUID hitmanId, UUID targetId) {
        Player hitman = Bukkit.getPlayer(hitmanId);
        Player target = Bukkit.getPlayer(targetId);
        if (hitman == null || target == null || !hasBounty(targetId)) return;
        setCompassTarget(hitman, target);
    }

    private void setCompassTarget(Player hitman, Player target) {
        Location targetLocation = target.getWorld().equals(hitman.getWorld()) ? target.getLocation() : hitman.getLocation();
        hitman.setCompassTarget(targetLocation);
    }

    private void clearTrackersFor(UUID targetId) {
        Iterator<Map.Entry<UUID, UUID>> iterator = trackers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (!entry.getValue().equals(targetId)) continue;
            Player hitman = Bukkit.getPlayer(entry.getKey());
            if (hitman != null) hitman.setCompassTarget(hitman.getLocation());
            iterator.remove();
        }
    }
}