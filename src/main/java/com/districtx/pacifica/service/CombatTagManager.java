package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.combat.CombatReason;
import com.districtx.pacifica.api.combat.CombatSession;
import com.districtx.pacifica.api.combat.event.CombatTagCancelEvent;
import com.districtx.pacifica.api.combat.event.CombatTagEndEvent;
import com.districtx.pacifica.api.combat.event.CombatTagStartEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class CombatTagManager {
    private final PacificaPlugin plugin;
    private final NamespacedKey statusKey;
    private final Map<UUID, CombatState> states = new HashMap<>();

    public CombatTagManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        statusKey = new NamespacedKey(plugin, "combattag.status");
    }

    public void tag(Player attacker, Player victim) {
        applyTag(attacker, victim, CombatReason.DAMAGE);
    }

    public void tag(Player player) {
        applyTag(player, null, CombatReason.API);
    }

    private void applyTag(Player player, Player opponent, CombatReason reason) {
        if (player == null || (opponent != null && player.equals(opponent))) return;
        CombatTagStartEvent event = new CombatTagStartEvent(player, opponent, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Bukkit.getPluginManager().callEvent(new CombatTagCancelEvent(player, opponent, reason));
            return;
        }
        int duration = Math.max(1, plugin.store().combattag().getInt("combattag.duration-seconds", 20));
        tagOne(player, opponent, duration);
        if (opponent != null) tagOne(opponent, player, duration);
    }

    private void tagOne(Player player, Player opponent, int duration) {
        CombatState state = states.get(player.getUniqueId());
        if (state == null) {
            state = new CombatState(opponent == null ? null : opponent.getUniqueId(), duration * 20);
            states.put(player.getUniqueId(), state);
            state.bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
            state.bar.addPlayer(player);
        } else {
            if (state.task != null) state.task.cancel();
            state.opponent = opponent == null ? null : opponent.getUniqueId();
            state.durationTicks = duration * 20;
            state.remainingTicks = state.durationTicks;
        }
        state.endTime = System.currentTimeMillis() + duration * 1000L;
        setStatus(player, true);
        state.remainingTicks = state.durationTicks;
        updateBar(player, state);
        state.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player), 1L, 1L);
    }

    private void tick(Player player) {
        CombatState state = states.get(player.getUniqueId());
        if (state == null) return;
        if (!player.isOnline()) {
            clear(player, CombatReason.QUIT);
            return;
        }
        state.remainingTicks--;
        if (state.remainingTicks <= 0) {
            Player opponent = state.opponent == null ? null : plugin.getServer().getPlayer(state.opponent);
            clear(player, CombatReason.EXPIRED);
            if (opponent != null && isTagged(opponent)) clear(opponent, CombatReason.EXPIRED);
            if (plugin.store().combattag().getBoolean("combattag.send-completion-message", false)) {
                player.sendMessage(plugin.store().message(player, "combattag.safe"));
                if (opponent != null && opponent.isOnline()) opponent.sendMessage(plugin.store().message(opponent, "combattag.safe"));
            }
            return;
        }
        updateBar(player, state);
    }

    private void updateBar(Player player, CombatState state) {
        if (state.bar == null) return;
        int seconds = remaining(player);
        String title = plugin.store().combattag().getString("messages.combattag.bossbar", "&c&lCombatTag &7| &fYou are in combat for &e%time%s");
        state.bar.setTitle(com.districtx.pacifica.util.Items.color(title.replace("%time%", String.valueOf(seconds))));
        state.bar.setProgress(Math.max(0.0, Math.min(1.0, state.remainingTicks / (double) state.durationTicks)));
        state.bar.setVisible(true);
    }

    public boolean isTagged(Player player) {
        if (player == null) return false;
        CombatState state = states.get(player.getUniqueId());
        return state != null && state.remainingTicks > 0;
    }

    public int remaining(Player player) {
        if (player == null) return 0;
        CombatState state = states.get(player.getUniqueId());
        return state == null ? 0 : Math.max(0, (state.remainingTicks + 19) / 20);
    }

    public Player opponent(Player player) {
        if (player == null) return null;
        CombatState state = states.get(player.getUniqueId());
        return state == null || state.opponent == null ? null : plugin.getServer().getPlayer(state.opponent);
    }

    public void clear(Player player) {
        clear(player, CombatReason.OTHER);
    }

    public void clear(Player player, CombatReason reason) {
        if (player == null) return;
        CombatState state = states.remove(player.getUniqueId());
        if (state != null) {
            Bukkit.getPluginManager().callEvent(new CombatTagEndEvent(player, state.opponent, reason));
            if (state.task != null) state.task.cancel();
            if (state.bar != null) {
                state.bar.removeAll();
                state.bar.setVisible(false);
            }
        }
        setStatus(player, false);
    }

    public Optional<CombatSession> session(Player player) {
        if (player == null) return Optional.empty();
        CombatState state = states.get(player.getUniqueId());
        if (state == null || state.remainingTicks <= 0) return Optional.empty();
        return Optional.of(new CombatSessionSnapshot(player.getUniqueId(), state.startTime, state.endTime,
            remaining(player), state.opponent));
    }

    public void handleQuit(Player player) {
        if (!isTagged(player)) {
            clear(player, CombatReason.QUIT);
            return;
        }
        Player opponent = opponent(player);
        if (opponent != null && opponent.isOnline()) {
            player.setHealth(0.0);
            clear(opponent, CombatReason.QUIT);
            opponent.sendMessage(plugin.store().message("combattag.opponent-quit"));
        }
        clear(player, CombatReason.QUIT);
    }

    public void handleDeath(Player victim, Player killer) {
        Player opponent = killer == null ? opponent(victim) : killer;
        clear(victim, CombatReason.DEATH);
        if (opponent != null && opponent.isOnline() && isTagged(opponent)) clear(opponent, CombatReason.DEATH);
        if (opponent != null && opponent.isOnline() && plugin.store().combattag().getBoolean("combattag.economy.enabled", true)) {
            double percentage = Math.max(0.0, plugin.store().combattag().getDouble("combattag.economy.victim-loss-percent", 20.0)) / 100.0;
            plugin.economy().transfer(opponent, victim, percentage);
        }
    }

    private void setStatus(Player player, boolean tagged) {
        String value = tagged ? "true" : "false";
        player.getPersistentDataContainer().set(statusKey, PersistentDataType.STRING, value);
        player.setMetadata("combattag.status", new FixedMetadataValue(plugin, value));
    }

    public boolean isAllowedCommand(String command) {
        String normalized = command.toLowerCase().split(" ", 2)[0];
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        for (String allowed : plugin.store().combattag().getStringList("combattag.allowed-commands")) {
            String value = allowed.toLowerCase().replaceFirst("^/", "");
            if (normalized.equals(value)) return true;
        }
        return normalized.equals("ct");
    }

    public void clear() {
        states.forEach((id, state) -> {
            if (state.task != null) state.task.cancel();
            if (state.bar != null) {
                state.bar.removeAll();
                state.bar.setVisible(false);
            }
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) setStatus(player, false);
        });
        states.clear();
    }

    private static final class CombatState {
        private UUID opponent;
        private int remainingTicks;
        private int durationTicks;
        private final long startTime;
        private long endTime;
        private BukkitTask task;
        private BossBar bar;

        private CombatState(UUID opponent, int durationTicks) {
            this.opponent = opponent;
            this.durationTicks = durationTicks;
            this.remainingTicks = durationTicks;
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + durationTicks * 50L;
        }
    }

    private static final class CombatSessionSnapshot implements CombatSession {
        private final UUID playerId;
        private final long startTime;
        private final long endTime;
        private final int remainingSeconds;
        private final UUID opponentId;

        private CombatSessionSnapshot(UUID playerId, long startTime, long endTime, int remainingSeconds, UUID opponentId) {
            this.playerId = playerId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.remainingSeconds = remainingSeconds;
            this.opponentId = opponentId;
        }

        @Override public UUID getPlayerId() { return playerId; }
        @Override public long getStartTime() { return startTime; }
        @Override public long getEndTime() { return endTime; }
        @Override public int getRemainingSeconds() { return remainingSeconds; }
        @Override public boolean isActive() { return remainingSeconds > 0; }
        @Override public com.districtx.pacifica.api.combat.CombatState getState() {
            return isActive() ? com.districtx.pacifica.api.combat.CombatState.ACTIVE : com.districtx.pacifica.api.combat.CombatState.ENDED;
        }
        @Override public Optional<UUID> getLastOpponent() { return Optional.ofNullable(opponentId); }
    }
}