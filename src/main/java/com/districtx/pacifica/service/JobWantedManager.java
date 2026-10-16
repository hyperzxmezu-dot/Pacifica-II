package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobAPI;
import com.districtx.pacifica.api.jobs.ArrestAPI;
import com.districtx.pacifica.api.jobs.BountyAPI;
import com.districtx.pacifica.api.jobs.JailAPI;
import com.districtx.pacifica.api.jobs.MedicAPI;
import com.districtx.pacifica.api.jobs.JobResult;
import com.districtx.pacifica.api.jobs.JobType;
import com.districtx.pacifica.api.jobs.PacificaJobsAPI;
import com.districtx.pacifica.api.jobs.WantedAPI;
import com.districtx.pacifica.api.jobs.WantedDisplayAPI;
import com.districtx.pacifica.api.jobs.event.JobChangeEvent;
import com.districtx.pacifica.api.jobs.event.JobCooldownStartEvent;
import com.districtx.pacifica.api.jobs.event.JobQuitEvent;
import com.districtx.pacifica.api.jobs.event.JobSelectEvent;
import com.districtx.pacifica.api.jobs.event.WantedCrimeEvent;
import com.districtx.pacifica.api.jobs.event.WantedLevelChangeEvent;
import com.districtx.pacifica.api.jobs.event.WantedClearEvent;
import com.districtx.pacifica.api.jobs.event.WantedDecayEvent;
import com.districtx.pacifica.api.jobs.event.WantedReason;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

/** Persistent job and wanted state manager. */
public final class JobWantedManager implements Listener, JobAPI, WantedAPI, WantedDisplayAPI, PacificaJobsAPI {
    private final PacificaPlugin plugin;
    private final Map<UUID, PlayerState> states = new LinkedHashMap<>();
    private final Map<UUID, Long> lastWardenTransactions = new LinkedHashMap<>();
    private final Set<UUID> wardenTransactions = new HashSet<>();
    private BukkitTask decayTask;

    public JobWantedManager(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the single shared wanted-decay task. */
    public void start() {
        if (decayTask == null) decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDecay, 20L, 20L);
    }

    /** Cancels scheduled work and persists loaded player states. */
    public void stop() {
        if (decayTask != null) decayTask.cancel();
        decayTask = null;
        for (Map.Entry<UUID, PlayerState> entry : states.entrySet()) saveState(entry.getKey(), entry.getValue());
    }

    @Override public boolean isAvailable() { return plugin.isEnabled(); }
    @Override public JobAPI jobs() { return this; }
    @Override public WantedAPI wanted() { return this; }
    @Override public WantedDisplayAPI wantedDisplay() { return this; }
    @Override public ArrestAPI arrests() { return plugin.arrests(); }
    @Override public JailAPI jail() { return plugin.jails(); }
    @Override public BountyAPI bounty() { return plugin.bounties(); }
    @Override public MedicAPI medic() { return plugin.medics(); }

    @Override public JobType getJob(Player player) {
        return player == null ? JobType.CRIMINAL : getJob(player.getUniqueId());
    }

    @Override public JobType getJob(UUID playerId) {
        return playerId == null ? JobType.CRIMINAL : state(playerId).job;
    }

    @Override public boolean hasJob(Player player, JobType job) {
        return player != null && job != null && getJob(player).equals(job);
    }

    @Override public boolean canSelectJob(Player player, JobType job) {
        if (player == null || job == null) return false;
        if (!player.hasPermission("pacifica.job." + job.name().toLowerCase(java.util.Locale.ROOT))
                && !player.hasPermission("pacifica.job.bypass.level")) return false;
        if (isRestrictedJob(job) && !player.hasPermission("pacifica.job.bypass.cooldown")
                && getRemainingJobCooldown(player.getUniqueId()) > 0) return false;
        int requiredLevel = plugin.store().config().getInt("jobs.unlocks." + job.name().toLowerCase(java.util.Locale.ROOT) + ".level", defaultUnlockLevel(job));
        return player.hasPermission("pacifica.job.bypass.level") || plugin.identity().pacificaLevel(player.getUniqueId()) >= requiredLevel;
    }

    @Override public JobResult selectJob(Player player, JobType job) {
        if (player == null || job == null) return JobResult.INVALID;
        if (getJob(player) == job) return JobResult.ALREADY_ACTIVE;
        if (!player.hasPermission("pacifica.job." + job.name().toLowerCase(java.util.Locale.ROOT))
                && !player.hasPermission("pacifica.job.bypass.level")) return JobResult.NO_PERMISSION;
        if (isRestrictedJob(job) && !player.hasPermission("pacifica.job.bypass.cooldown")
                && getRemainingJobCooldown(player.getUniqueId()) > 0) return JobResult.COOLDOWN;
        int requiredLevel = plugin.store().config().getInt("jobs.unlocks." + job.name().toLowerCase(java.util.Locale.ROOT) + ".level", defaultUnlockLevel(job));
        if (!player.hasPermission("pacifica.job.bypass.level") && plugin.identity().pacificaLevel(player.getUniqueId()) < requiredLevel) return JobResult.LEVEL_REQUIRED;
        return changeJob(player, job) ? JobResult.SELECTED : JobResult.CANCELLED;
    }

    @Override public JobResult quitJob(Player player) {
        if (player == null) return JobResult.INVALID;
        if (getJob(player) == JobType.CRIMINAL) return JobResult.ALREADY_ACTIVE;
        return changeJob(player, JobType.CRIMINAL) ? JobResult.QUIT : JobResult.CANCELLED;
    }

    @Override public long getRemainingJobCooldown(UUID playerId) {
        if (playerId == null) return 0L;
        return Math.max(0L, state(playerId).jobCooldownUntil - System.currentTimeMillis());
    }

    @Override public boolean isJobLocked(Player player) {
        return player != null && getRemainingJobCooldown(player.getUniqueId()) > 0L;
    }

    @Override public void setJob(Player player, JobType job) {
        if (player != null && job != null) changeJob(player, job);
    }

    @Override public void resetJob(Player player) {
        if (player != null) changeJob(player, JobType.CRIMINAL);
    }

    @Override public int getWantedLevel(Player player) {
        return player == null ? 0 : getWantedLevel(player.getUniqueId());
    }

    @Override public int getWantedLevel(UUID playerId) {
        return playerId == null ? 0 : state(playerId).wantedLevel;
    }

    @Override public String formatStars(int wantedLevel) {
        return WantedDisplayAPI.format(wantedLevel);
    }

    @Override public String formatStars(Player player) {
        return formatStars(getWantedLevel(player));
    }

    @Override public boolean isWanted(Player player) {
        return getWantedLevel(player) > 0;
    }

    @Override public void addWantedLevel(Player player, int amount) {
        if (player == null || amount <= 0) return;
        setWantedLevel(player, getWantedLevel(player) + amount, WantedReason.API);
    }

    @Override public void setWantedLevel(Player player, int level) {
        if (player != null) setWantedLevel(player, level, WantedReason.API);
    }

    @Override public void clearWantedLevel(Player player) {
        clearWantedLevel(player, WantedReason.DEATH);
    }

    public void clearWantedLevel(Player player, WantedReason reason) {
        if (player == null) return;
        PlayerState state = state(player.getUniqueId());
        if (setWantedLevel(player, 0, reason)) {
            state.lastCrimeAt = 0L;
            saveState(player.getUniqueId(), state);
        }
    }

    public boolean setWantedLevel(Player player, int level, WantedReason reason) {
        return player != null && setWantedLevelInternal(player, level, reason);
    }

    @Override public void decayWantedLevel(Player player) {
        if (player == null) return;
        PlayerState state = state(player.getUniqueId());
        if (state.wantedLevel <= 0) return;
        int amount = Math.max(1, plugin.store().config().getInt("wanted.decay.amount", 1));
        if (setWantedLevel(player, state.wantedLevel - amount, WantedReason.DECAY)) {
            state.lastCrimeAt = System.currentTimeMillis();
            saveState(player.getUniqueId(), state);
        }
    }

    @Override public boolean canBeArrested(Player player) {
        return player != null && getWantedLevel(player) > 0;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerState state = state(player.getUniqueId());
        saveState(player.getUniqueId(), state);
        decayIfDue(player, state, System.currentTimeMillis());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        PlayerState state = states.remove(event.getPlayer().getUniqueId());
        if (state != null) saveState(event.getPlayer().getUniqueId(), state);
        lastWardenTransactions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        clearWantedLevel(victim);
        JobType victimJob = getJob(victim);
        if (plugin.store().config().getBoolean("wanted.enabled", true) && killer != null && killer != victim && getJob(killer) == JobType.CRIMINAL) {
            PlayerState killerState = state(killer.getUniqueId());
            killerState.qualifyingKills++;
            int previous = killerState.wantedLevel;
            int target = victimJob == JobType.COP
                    ? JobsWantedRules.copKillLevel(previous, plugin.store().config().getInt("wanted.cop-kill.stars", 2), maxWantedStars())
                    : Math.max(previous, thresholdStars(killerState.qualifyingKills));
            WantedReason reason = victimJob == JobType.COP ? WantedReason.COP_KILL : WantedReason.PLAYER_KILL;
            WantedCrimeEvent crimeEvent = new WantedCrimeEvent(killer, victim, Math.max(0, target - previous), reason);
            Bukkit.getPluginManager().callEvent(crimeEvent);
            if (!crimeEvent.isCancelled()) {
                killerState.lastCrimeAt = System.currentTimeMillis();
                setWantedLevel(killer, target, reason);
                saveState(killer.getUniqueId(), killerState);
            } else {
                saveState(killer.getUniqueId(), killerState);
            }
        }
        if (isRestrictedJob(victimJob) && plugin.store().config().getBoolean("jobs.death-cooldown.enabled", true)) {
            long cooldown = deathCooldownMillis(victim);
            PlayerState victimState = state(victim.getUniqueId());
            victimState.jobCooldownUntil = System.currentTimeMillis() + cooldown;
            changeJob(victim, JobType.CRIMINAL);
            saveState(victim.getUniqueId(), victimState);
            Bukkit.getPluginManager().callEvent(new JobCooldownStartEvent(victim, victimJob, victimState.jobCooldownUntil));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void preventCopFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) attacker = direct;
        else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) attacker = player;
        }
        if (attacker != null && attacker != victim
                && getJob(attacker) == JobType.COP && getJob(victim) == JobType.COP) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interactWithJobNpc(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        String type = npcType(event.getRightClicked());
        if (type == null) return;
        event.setCancelled(true);
        if (plugin.jails().isJailed(player)) return;
        switch (type) {
            case "cop" -> plugin.jobMenus().openPoliceMenu(player);
            case "hitman" -> plugin.jobMenus().openHitmanMenu(player);
            case "medic" -> plugin.jobMenus().openMedicMenu(player);
            case "criminal-quit" -> plugin.jobMenus().openCriminalMenu(player);
            case "warden" -> plugin.jobMenus().openWardenMenu(player);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void protectedNpcKilled(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || getJob(killer) != JobType.CRIMINAL || !plugin.store().config().getBoolean("wanted.enabled", true)) return;
        if (!plugin.store().config().getStringList("wanted.protected-npcs").contains(event.getEntity().getUniqueId().toString())) return;
        PlayerState state = state(killer.getUniqueId());
        int amount = Math.max(1, plugin.store().config().getInt("wanted.npc-kill-stars", 1));
        int oldLevel = state.wantedLevel;
        WantedCrimeEvent crime = new WantedCrimeEvent(killer, null, Math.min(maxWantedStars(), oldLevel + amount) - oldLevel, WantedReason.NPC_KILL);
        Bukkit.getPluginManager().callEvent(crime);
        if (crime.isCancelled()) return;
        state.lastCrimeAt = System.currentTimeMillis();
        setWantedLevel(killer, oldLevel + amount, WantedReason.NPC_KILL);
        saveState(killer.getUniqueId(), state);
    }

    public void linkNpc(String type, UUID entityId) {
        String normalized = normalizeNpcType(type);
        if (normalized == null || entityId == null) return;
        plugin.store().data().set("jobs.npc-links." + normalized, entityId.toString());
        plugin.store().save();
    }

    public void clearNpc(String type) {
        String normalized = normalizeNpcType(type);
        if (normalized == null) return;
        plugin.store().data().set("jobs.npc-links." + normalized, null);
        plugin.store().save();
    }

    private boolean changeJob(Player player, JobType job) {
        PlayerState state = state(player.getUniqueId());
        if (state.job == job) return true;
        org.bukkit.inventory.ItemStack medicSyringe = null;
        if (job == JobType.MEDIC && !hasSyringe(player)) {
            medicSyringe = plugin.medics().createSyringe();
            if (medicSyringe == null || player.getInventory().firstEmpty() == -1) {
                player.sendMessage(Items.color("&cMake space in your inventory before selecting Medic."));
                return false;
            }
        }
        if (job == JobType.CRIMINAL) {
            JobQuitEvent quitEvent = new JobQuitEvent(player, state.job, job);
            Bukkit.getPluginManager().callEvent(quitEvent);
            if (quitEvent.isCancelled()) return false;
        } else {
            JobSelectEvent selectEvent = new JobSelectEvent(player, state.job, job);
            Bukkit.getPluginManager().callEvent(selectEvent);
            if (selectEvent.isCancelled()) return false;
        }
        JobChangeEvent event = new JobChangeEvent(player, state.job, job);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        if (state.job == JobType.MEDIC && job != JobType.MEDIC) removeSyringes(player);
        state.job = job;
        saveState(player.getUniqueId(), state);
        if (medicSyringe != null) player.getInventory().addItem(medicSyringe);
        return true;
    }

    private boolean hasSyringe(Player player) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (plugin.medics().isSyringe(item)) return true;
        }
        return false;
    }

    private void removeSyringes(Player player) {
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (plugin.medics().isSyringe(contents[slot])) player.getInventory().setItem(slot, null);
        }
    }

    private boolean setWantedLevelInternal(Player player, int requestedLevel, WantedReason reason) {
        PlayerState state = state(player.getUniqueId());
        int oldLevel = state.wantedLevel;
        int newLevel = Math.max(0, Math.min(maxWantedStars(), requestedLevel));
        if (state.wantedLevel == newLevel) return true;
        WantedLevelChangeEvent event = new WantedLevelChangeEvent(player, state.wantedLevel, newLevel, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        state.wantedLevel = newLevel;
        saveState(player.getUniqueId(), state);
        if (reason == WantedReason.DECAY && newLevel < oldLevel) {
            Bukkit.getPluginManager().callEvent(new WantedDecayEvent(player, oldLevel, newLevel));
        }
        if (oldLevel > 0 && newLevel == 0) {
            Bukkit.getPluginManager().callEvent(new WantedClearEvent(player, oldLevel, reason));
        }
        return true;
    }

    private String npcType(Entity entity) {
        String[] types = {"cop", "hitman", "medic", "criminal-quit", "warden"};
        String customName = entity.getCustomName() == null ? "" : ChatColor.stripColor(entity.getCustomName());
        for (String type : types) {
            String linkedId = plugin.store().data().getString("jobs.npc-links." + type, "");
            String configuredId = plugin.store().config().getString("jobs.npcs." + type, "");
            if (entity.getUniqueId().toString().equalsIgnoreCase(linkedId)
                    || (!configuredId.isBlank() && customName.equalsIgnoreCase(configuredId))) return type;
        }
        return null;
    }

    private String normalizeNpcType(String type) {
        if (type == null) return null;
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        return java.util.List.of("cop", "hitman", "medic", "criminal-quit", "warden").contains(normalized) ? normalized : null;
    }

    private void sendSelectionResult(Player player, JobType job) {
        JobResult result = selectJob(player, job);
        String key = switch (result) {
            case SELECTED -> "jobs.messages.selected";
            case ALREADY_ACTIVE -> "jobs.messages.already-active";
            case LEVEL_REQUIRED -> "jobs.messages.level-required";
            case COOLDOWN -> "jobs.messages.cooldown";
            case NO_PERMISSION -> "jobs.messages.no-permission";
            case CANCELLED -> "jobs.messages.cancelled";
            default -> "jobs.messages.failed";
        };
        String fallback = switch (result) {
            case SELECTED -> "&aYou are now a " + job.name() + ".";
            case ALREADY_ACTIVE -> "&eYou already have that job.";
            case LEVEL_REQUIRED -> "&cYou have not reached the level required for that job.";
            case COOLDOWN -> "&cYou cannot select that job yet.";
            case NO_PERMISSION -> "&cYou do not have permission for that job.";
            case CANCELLED -> "&cThat job change was cancelled.";
            default -> "&cYou could not select that job.";
        };
        player.sendMessage(Items.color(plugin.store().config().getString(key, fallback).replace("%job%", job.name())));
    }

    private void sendQuitResult(Player player) {
        JobResult result = quitJob(player);
        String message = result == JobResult.QUIT ? "&aYou are now a CRIMINAL." : "&eYou are already a CRIMINAL.";
        player.sendMessage(Items.color(plugin.store().config().getString("jobs.messages.quit", message)));
    }

    public BigDecimal getWardenRemovalCost(int levels) {
        if (levels <= 0) return BigDecimal.ZERO;
        try {
            BigDecimal perStar = new BigDecimal(plugin.store().config().getString("wanted.warden.price-per-star", "10000"));
            return perStar.multiply(BigDecimal.valueOf(levels));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    public boolean purchaseWardenRemoval(Player player, boolean removeAll) {
        if (player == null || !plugin.store().config().getBoolean("wanted.warden.enabled", true)) return false;
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTransaction = lastWardenTransactions.get(playerId);
        if (lastTransaction != null && now - lastTransaction < 500L) return false;
        if (!wardenTransactions.add(playerId)) return false;
        try {
            int stars = getWantedLevel(player);
            if (stars <= 0) {
                player.sendMessage(Items.color(plugin.store().config().getString("wanted.warden.messages.not-wanted", "&eYou have no wanted stars.")));
                return false;
            }
            int levelsToRemove = removeAll ? stars : 1;
            int newLevel = stars - levelsToRemove;
            BigDecimal cost = getWardenRemovalCost(levelsToRemove);
            if (cost.signum() <= 0 || !plugin.economy().hasBalance(player, cost) || !plugin.economy().withdraw(player, cost)) {
                player.sendMessage(Items.color(plugin.store().config().getString("wanted.warden.messages.insufficient-funds", "&cYou cannot afford to clear your wanted stars.")));
                return false;
            }
            if (!setWantedLevel(player, newLevel, WantedReason.WARDEN)) {
                if (!plugin.economy().deposit(player, cost)) plugin.getLogger().severe("Failed to refund a cancelled Warden transaction for " + playerId);
                return false;
            }
            PlayerState state = state(playerId);
            if (newLevel == 0) state.lastCrimeAt = 0L;
            saveState(playerId, state);
            String messageKey = removeAll ? "wanted.warden.messages.success" : "wanted.warden.messages.remove-one-success";
            String fallback = removeAll
                    ? "&aYou paid $%cost% to clear your wanted stars."
                    : "&aYou paid $%cost% to remove one wanted level. %level% remaining.";
            String message = plugin.store().config().getString(messageKey, fallback)
                    .replace("%cost%", cost.stripTrailingZeros().toPlainString())
                    .replace("%level%", Integer.toString(newLevel));
            player.sendMessage(Items.color(message));
            lastWardenTransactions.put(playerId, now);
            return true;
        } finally {
            wardenTransactions.remove(playerId);
        }
    }

    private PlayerState state(UUID playerId) {
        return states.computeIfAbsent(playerId, this::loadState);
    }

    private PlayerState loadState(UUID playerId) {
        ConfigurationSection section = plugin.store().data().getConfigurationSection("jobs.players." + playerId);
        if (section == null) return new PlayerState();
        JobType job;
        try { job = JobType.valueOf(section.getString("job", "CRIMINAL").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { job = JobType.CRIMINAL; }
        PlayerState state = new PlayerState();
        state.job = job;
        state.jobCooldownUntil = Math.max(0L, section.getLong("job-cooldown-until", 0L));
        state.wantedLevel = Math.max(0, Math.min(maxWantedStars(), section.getInt("wanted-level", 0)));
        state.qualifyingKills = Math.max(0, section.getInt("qualifying-kills", 0));
        state.lastCrimeAt = Math.max(0L, section.getLong("last-crime-at", 0L));
        return state;
    }

    private void saveState(UUID playerId, PlayerState state) {
        String path = "jobs.players." + playerId;
        plugin.store().data().set(path + ".job", state.job.name());
        plugin.store().data().set(path + ".job-cooldown-until", state.jobCooldownUntil);
        plugin.store().data().set(path + ".wanted-level", state.wantedLevel);
        plugin.store().data().set(path + ".qualifying-kills", state.qualifyingKills);
        plugin.store().data().set(path + ".last-crime-at", state.lastCrimeAt);
        plugin.store().save();
    }

    private int maxWantedStars() {
        return Math.max(1, plugin.store().config().getInt("wanted.max-stars", 5));
    }

    private int thresholdStars(int qualifyingKills) {
        ConfigurationSection thresholds = plugin.store().config().getConfigurationSection("wanted.kill-thresholds");
        if (thresholds == null) return qualifyingKills > 0 ? 1 : 0;
        Map<Integer, Integer> configured = new LinkedHashMap<>();
        for (String key : thresholds.getKeys(false)) {
            try {
                int threshold = Integer.parseInt(key);
                if (threshold > 0) configured.put(threshold, thresholds.getInt(key));
            } catch (NumberFormatException ignored) { }
        }
        return JobsWantedRules.starsForKills(qualifyingKills, configured, maxWantedStars());
    }

    private void tickDecay() {
        if (!plugin.store().config().getBoolean("wanted.decay.enabled", true)) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) decayIfDue(player, state(player.getUniqueId()), now);
    }

    private void decayIfDue(Player player, PlayerState state, long now) {
        if (!plugin.store().config().getBoolean("wanted.decay.enabled", true) || state.wantedLevel <= 0 || state.lastCrimeAt <= 0L) return;
        long interval = durationMillis(plugin.store().config().getString("wanted.decay.interval", "5m"), 300_000L);
        long elapsed = now - state.lastCrimeAt;
        if (elapsed < interval) return;
        long steps = elapsed / interval;
        int amount = Math.max(1, plugin.store().config().getInt("wanted.decay.amount", 1));
        int newLevel = (int) Math.max(0L, state.wantedLevel - Math.min(Integer.MAX_VALUE, steps * amount));
        if (setWantedLevel(player, newLevel, WantedReason.DECAY)) {
            state.lastCrimeAt = newLevel == 0 ? 0L : state.lastCrimeAt + steps * interval;
            saveState(player.getUniqueId(), state);
        }
    }

    private long deathCooldownMillis(Player player) {
        long configured = durationMillis(plugin.store().config().getString("jobs.death-cooldown.duration", "3h"), 10_800_000L);
        ConfigurationSection perks = plugin.store().config().getConfigurationSection("jobs.death-cooldown.perks");
        if (perks == null) return configured;
        long reduced = configured;
        for (String key : perks.getKeys(false)) {
            String permission = perks.getString(key + ".permission", "");
            if (!permission.isBlank() && player.hasPermission(permission)) {
                reduced = Math.min(reduced, durationMillis(perks.getString(key + ".duration", "3h"), configured));
            }
        }
        return reduced;
    }

    private static boolean isRestrictedJob(JobType job) {
        return job == JobType.COP || job == JobType.HITMAN;
    }

    private static int defaultUnlockLevel(JobType job) {
        return job == JobType.COP ? 25 : job == JobType.HITMAN ? 40 : 0;
    }

    private static long durationMillis(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        String input = value.trim().toLowerCase(java.util.Locale.ROOT);
        long multiplier = 1_000L;
        char suffix = input.charAt(input.length() - 1);
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

    private static final class PlayerState {
        private JobType job = JobType.CRIMINAL;
        private long jobCooldownUntil;
        private int wantedLevel;
        private int qualifyingKills;
        private long lastCrimeAt;
    }
}