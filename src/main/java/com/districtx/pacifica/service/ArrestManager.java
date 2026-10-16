package com.districtx.pacifica.service;

import com.cryptomorin.xseries.XMaterial;
import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.ArrestAPI;
import com.districtx.pacifica.api.jobs.ArrestMethod;
import com.districtx.pacifica.api.jobs.ArrestResult;
import com.districtx.pacifica.api.jobs.BribeResult;
import com.districtx.pacifica.api.jobs.JobType;
import com.districtx.pacifica.api.jobs.JailSession;
import com.districtx.pacifica.api.jobs.event.BribeAcceptEvent;
import com.districtx.pacifica.api.jobs.event.BribeDenyEvent;
import com.districtx.pacifica.api.jobs.event.BribeOfferEvent;
import com.districtx.pacifica.api.jobs.event.PlayerArrestEvent;
import com.districtx.pacifica.api.jobs.event.PreArrestEvent;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

/** Implements Cop arrests and expiring, Cop-owned bribe transactions. */
public final class ArrestManager implements ArrestAPI, Listener {
    private final PacificaPlugin plugin;
    private final Map<UUID, HitProgress> hits = new HashMap<>();
    private final Map<UUID, BribeOffer> offers = new HashMap<>();
    private final Set<UUID> arresting = new HashSet<>();
    private BukkitTask cleanupTask;

    public ArrestManager(PacificaPlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (cleanupTask == null) cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 20L, 20L);
    }

    public void stop() {
        if (cleanupTask != null) cleanupTask.cancel();
        cleanupTask = null;
        hits.clear();
        offers.clear();
        arresting.clear();
    }

    @Override public ArrestResult attemptArrest(Player cop, Player target, ArrestMethod method) {
        if (cop == null || target == null || method == null || cop == target || !cop.isOnline() || !target.isOnline()) return ArrestResult.INVALID;
        if (!cop.hasPermission("pacifica.arrest.use")) return ArrestResult.NO_PERMISSION;
        if (plugin.jobsAndWanted().getJob(cop) != JobType.COP) return ArrestResult.NOT_COP;
        if (plugin.jobsAndWanted().getJob(target) == JobType.HITMAN) return ArrestResult.FORBIDDEN_TARGET;
        if (plugin.jails().isJailed(target)) return ArrestResult.ALREADY_JAILED;
        if (!plugin.jobsAndWanted().canBeArrested(target)) return ArrestResult.NOT_ELIGIBLE;
        if (!plugin.store().config().getBoolean("jail.enabled", true) || plugin.jails().getJailLocation() == null) return ArrestResult.JAIL_UNAVAILABLE;
        UUID targetId = target.getUniqueId();
        if (!arresting.add(targetId)) return ArrestResult.IN_PROGRESS;
        try {
            int stars = plugin.jobsAndWanted().getWantedLevel(target);
            BigDecimal reward = arrestReward(stars);
            PreArrestEvent preEvent = new PreArrestEvent(cop, target, stars, method, reward);
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) return ArrestResult.CANCELLED;
            Duration sentence = plugin.jails().configuredSentence(stars);
            plugin.jails().jail(target, sentence, cop.getUniqueId());
            if (!plugin.jails().isJailed(target)) return ArrestResult.JAIL_UNAVAILABLE;
            if (reward.signum() > 0 && !plugin.economy().deposit(cop, reward)) {
                plugin.getLogger().warning("Failed to credit arrest reward to " + cop.getUniqueId());
            }
            Bukkit.getPluginManager().callEvent(new PlayerArrestEvent(cop, target, stars, method, reward));
            cop.sendMessage(Items.color("&aArrest successful. Reward: $" + reward.stripTrailingZeros().toPlainString()));
            target.sendMessage(Items.color("&cYou have been arrested and sent to jail."));
            return ArrestResult.SUCCESS;
        } finally {
            arresting.remove(targetId);
        }
    }

    @Override public BribeResult offerBribe(Player prisoner, BigDecimal amount) {
        if (prisoner == null || amount == null || amount.signum() <= 0) return BribeResult.INVALID;
        if (!prisoner.hasPermission("pacifica.bribe.use")) return BribeResult.INVALID;
        JailSession session = plugin.jails().getSession(prisoner.getUniqueId()).orElse(null);
        if (session == null) return BribeResult.NOT_JAILED;
        Player cop = session.getArrestingCop() == null ? null : Bukkit.getPlayer(session.getArrestingCop());
        if (cop == null || plugin.jobsAndWanted().getJob(cop) != JobType.COP) return BribeResult.COP_OFFLINE;
        if (!plugin.store().config().getBoolean("jobs.cop.bribe.enabled", true)) return BribeResult.INVALID;
        BigDecimal minimum = configAmount("jobs.cop.bribe.minimum", "5000");
        if (!JobsWantedRules.meetsBribeMinimum(amount, minimum)) return BribeResult.BELOW_MINIMUM;
        BribeOfferEvent event = new BribeOfferEvent(cop, prisoner, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return BribeResult.CANCELLED;
        long expiresAt = System.currentTimeMillis() + parseDuration(plugin.store().config().getString("jobs.cop.bribe.expiration", "2m"), 120_000L);
        offers.put(prisoner.getUniqueId(), new BribeOffer(cop.getUniqueId(), amount, expiresAt));
        cop.sendMessage(Items.color("&e" + prisoner.getName() + " offered you $" + amount.stripTrailingZeros().toPlainString()
                + ". Use /bribe accept " + prisoner.getName() + " or /bribe deny " + prisoner.getName() + "."));
        return BribeResult.OFFERED;
    }

    @Override public BribeResult acceptBribe(Player cop, String prisonerName) {
        Player prisoner = onlinePlayer(prisonerName);
        if (cop == null || prisoner == null) return BribeResult.INVALID;
        BribeOffer offer = offers.get(prisoner.getUniqueId());
        if (offer == null) return BribeResult.NO_OFFER;
        BribeResult validation = validateOffer(cop, prisoner, offer);
        if (validation != null) return validation;
        BribeAcceptEvent event = new BribeAcceptEvent(cop, prisoner, offer.amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return BribeResult.CANCELLED;
        if (!plugin.economy().hasBalance(prisoner, offer.amount)) {
            offers.remove(prisoner.getUniqueId());
            return BribeResult.INSUFFICIENT_FUNDS;
        }
        offers.remove(prisoner.getUniqueId());
        if (!plugin.economy().transfer(prisoner, cop, offer.amount)) return BribeResult.ECONOMY_FAILURE;
        plugin.jails().release(prisoner);
        prisoner.sendMessage(Items.color("&aYour bribe was accepted. You have been released."));
        cop.sendMessage(Items.color("&aYou accepted the bribe from " + prisoner.getName() + "."));
        return BribeResult.ACCEPTED;
    }

    @Override public BribeResult denyBribe(Player cop, String prisonerName) {
        Player prisoner = onlinePlayer(prisonerName);
        if (cop == null || prisoner == null) return BribeResult.INVALID;
        BribeOffer offer = offers.get(prisoner.getUniqueId());
        if (offer == null) return BribeResult.NO_OFFER;
        BribeResult validation = validateOffer(cop, prisoner, offer);
        if (validation != null) return validation;
        offers.remove(prisoner.getUniqueId());
        Bukkit.getPluginManager().callEvent(new BribeDenyEvent(cop, prisoner, offer.amount));
        prisoner.sendMessage(Items.color("&cThe Cop denied your bribe offer."));
        cop.sendMessage(Items.color("&eYou denied " + prisoner.getName() + "'s bribe offer."));
        return BribeResult.DENIED;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNightStickHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player cop = directPlayerAttacker(event);
        if (cop == null || plugin.jobsAndWanted().getJob(cop) != JobType.COP) return;
        UUID copId = cop.getUniqueId();
        HitProgress previous = hits.get(copId);
        long now = System.currentTimeMillis();
        if (previous != null && (!previous.targetId.equals(target.getUniqueId()) || now - previous.lastHit > nightStickWindow())) {
            hits.remove(copId);
            previous = null;
        }
        if (!matchesConfiguredItem(cop.getInventory().getItemInMainHand(), "jobs.cop.arrest.night-stick")) {
            hits.remove(copId);
            return;
        }
        if (plugin.jobsAndWanted().getJob(target) == JobType.HITMAN || !plugin.jobsAndWanted().canBeArrested(target)) {
            hits.remove(copId);
            return;
        }
        HitProgress progress = previous == null ? new HitProgress(target.getUniqueId()) : previous;
        progress.count++;
        progress.lastHit = now;
        int required = Math.max(1, plugin.store().config().getInt("jobs.cop.arrest.night-stick.hits", 3));
        if (progress.count < required) {
            hits.put(copId, progress);
            return;
        }
        hits.remove(copId);
        if (attemptArrest(cop, target, ArrestMethod.NIGHT_STICK) == ArrestResult.SUCCESS) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTaserUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player cop = event.getPlayer();
        if (plugin.jobsAndWanted().getJob(cop) != JobType.COP
                || !matchesConfiguredItem(cop.getInventory().getItemInMainHand(), "jobs.cop.arrest.taser")) return;
        event.setCancelled(true);
        attemptArrest(cop, target, ArrestMethod.TASER);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTaserHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Player cop = directPlayerAttacker(event);
        if (cop == null || plugin.jobsAndWanted().getJob(cop) != JobType.COP
                || !matchesConfiguredItem(cop.getInventory().getItemInMainHand(), "jobs.cop.arrest.taser")) return;
        if (attemptArrest(cop, target, ArrestMethod.TASER) == ArrestResult.SUCCESS) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        hits.remove(playerId);
        hits.values().removeIf(progress -> progress.targetId.equals(playerId));
        offers.entrySet().removeIf(entry -> entry.getKey().equals(playerId) || entry.getValue().copId.equals(playerId));
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        hits.remove(playerId);
        hits.values().removeIf(progress -> progress.targetId.equals(playerId));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCopDeathArrest(PlayerDeathEvent event) {
        Player prisoner = event.getEntity();
        Player cop = prisoner.getKiller();
        if (cop == null || plugin.jobsAndWanted().getJob(cop) != JobType.COP
                || plugin.jobsAndWanted().getJob(prisoner) == JobType.HITMAN
                || !plugin.jobsAndWanted().canBeArrested(prisoner)
                || !plugin.store().config().getBoolean("jail.enabled", true)
                || plugin.jails().getJailLocation() == null) return;
        int stars = plugin.jobsAndWanted().getWantedLevel(prisoner);
        BigDecimal reward = arrestReward(stars);
        PreArrestEvent preEvent = new PreArrestEvent(cop, prisoner, stars, ArrestMethod.COP_KILL, reward);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) return;
        plugin.jails().prepareJail(prisoner, plugin.jails().configuredSentence(stars), cop.getUniqueId(), stars);
        if (!plugin.jails().isJailed(prisoner)) return;
        if (reward.signum() > 0 && !plugin.economy().deposit(cop, reward)) plugin.getLogger().warning("Failed to credit arrest reward to " + cop.getUniqueId());
        Bukkit.getPluginManager().callEvent(new PlayerArrestEvent(cop, prisoner, stars, ArrestMethod.COP_KILL, reward));
        cop.sendMessage(Items.color("&aArrest successful. Reward: $" + reward.stripTrailingZeros().toPlainString()));
    }

    private BribeResult validateOffer(Player cop, Player prisoner, BribeOffer offer) {
        if (!cop.hasPermission("pacifica.arrest.use")) return BribeResult.NOT_RESPONSIBLE_COP;
        if (plugin.jobsAndWanted().getJob(cop) != JobType.COP || !offer.copId.equals(cop.getUniqueId())) return BribeResult.NOT_RESPONSIBLE_COP;
        if (System.currentTimeMillis() >= offer.expiresAt) {
            offers.remove(prisoner.getUniqueId());
            return BribeResult.EXPIRED;
        }
        JailSession session = plugin.jails().getSession(prisoner.getUniqueId()).orElse(null);
        if (session == null || !cop.getUniqueId().equals(session.getArrestingCop())) {
            offers.remove(prisoner.getUniqueId());
            return BribeResult.EXPIRED;
        }
        return null;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(entry -> now - entry.getValue().lastHit > nightStickWindow());
        Iterator<Map.Entry<UUID, BribeOffer>> iterator = offers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BribeOffer> entry = iterator.next();
            if (now >= entry.getValue().expiresAt) iterator.remove();
        }
    }

    private Player onlinePlayer(String name) {
        if (name == null || name.isBlank()) return null;
        return Bukkit.getOnlinePlayers().stream().filter(player -> player.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private BigDecimal arrestReward(int stars) {
        return configAmount("jobs.cop.arrest-rewards." + stars, "0");
    }

    private BigDecimal configAmount(String path, String fallback) {
        try { return new BigDecimal(plugin.store().config().getString(path, fallback)); }
        catch (NumberFormatException exception) { return new BigDecimal(fallback); }
    }

    private boolean matchesConfiguredItem(ItemStack item, String path) {
        if (item == null || item.getType().isAir()) return false;
        String materialName = plugin.store().config().getString(path + ".material", "");
        Material material = XMaterial.matchXMaterial(materialName).map(XMaterial::parseMaterial).orElse(null);
        if (material == null || item.getType() != material) return false;
        String requiredName = plugin.store().config().getString(path + ".name", "");
        if (requiredName.isBlank()) return true;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(Items.color(requiredName));
    }

    private Player directPlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private long nightStickWindow() {
        return parseDuration(plugin.store().config().getString("jobs.cop.arrest.night-stick.window", "8s"), 8_000L);
    }

    private static long parseDuration(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        String input = value.trim().toLowerCase(java.util.Locale.ROOT);
        char suffix = input.charAt(input.length() - 1);
        long multiplier = 1_000L;
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
        } catch (ArithmeticException | NumberFormatException exception) { return fallback; }
    }

    private static final class HitProgress {
        private final UUID targetId;
        private int count;
        private long lastHit;
        private HitProgress(UUID targetId) { this.targetId = targetId; }
    }

    private static final class BribeOffer {
        private final UUID copId;
        private final BigDecimal amount;
        private final long expiresAt;
        private BribeOffer(UUID copId, BigDecimal amount, long expiresAt) {
            this.copId = copId;
            this.amount = amount;
            this.expiresAt = expiresAt;
        }
    }

}