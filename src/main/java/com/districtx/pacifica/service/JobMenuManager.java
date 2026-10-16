package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.jobs.JobMenuAPI;
import com.districtx.pacifica.api.jobs.BountyPlacementResult;
import com.districtx.pacifica.api.jobs.JobResult;
import com.districtx.pacifica.api.jobs.JobType;
import com.districtx.pacifica.gui.CriminalMenu;
import com.districtx.pacifica.gui.HitmanMenu;
import com.districtx.pacifica.gui.JobSelectionMenu;
import com.districtx.pacifica.gui.MedicMenu;
import com.districtx.pacifica.gui.PoliceMenu;
import com.districtx.pacifica.gui.WardenMenu;
import com.districtx.pacifica.gui.PlaceBountiesMenu;
import com.districtx.pacifica.gui.ChoosePlayerMenu;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Opens the job menus and routes menu-based job changes through the existing jobs service. */
public final class JobMenuManager implements JobMenuAPI, Listener {
    private final PacificaPlugin plugin;
    private final Map<UUID, BountySession> bountySessions = new HashMap<>();
    private List<OfflinePlayer> historicalPlayers = List.of();
    private long historicalPlayersExpiresAt;

    public JobMenuManager(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        return plugin.isEnabled();
    }

    @Override
    public void openJobMenu(Player player) {
        onMainThread(player, online -> plugin.guis().open(new JobSelectionMenu(plugin, online)));
    }

    @Override
    public void openPoliceMenu(Player player) {
        onMainThread(player, online -> plugin.guis().open(new PoliceMenu(plugin, online)));
    }

    @Override
    public void openMedicMenu(Player player) {
        onMainThread(player, online -> plugin.guis().open(new MedicMenu(plugin, online)));
    }

    @Override
    public void openWardenMenu(Player player) {
        onMainThread(player, online -> plugin.guis().open(new WardenMenu(plugin, online)));
    }

    @Override
    public void openHitmanMenu(Player player) {
        onMainThread(player, online -> plugin.guis().open(new HitmanMenu(plugin, online)));
    }

    @Override
    public void openCriminalMenu(Player player) {
        onMainThread(player, online -> plugin.guis().open(new CriminalMenu(plugin, online)));
    }

    @Override
    public void openPlaceBountiesMenu(Player player) {
        onMainThread(player, online -> {
            boolean anonymous = plugin.store().config().getBoolean("bounty.player-placement.anonymous-default", true);
            BountySession session = new BountySession(online.getUniqueId(), anonymous);
            bountySessions.put(online.getUniqueId(), session);
            openPlaceBountiesMenu(online, session);
        });
    }

    @Override
    public void openChoosePlayerMenu(Player player) {
        openChoosePlayerMenu(player, 0);
    }

    public void openChoosePlayerMenu(Player player, int page) {
        onMainThread(player, online -> {
            BountySession session = bountySession(online);
            if (session != null) plugin.guis().open(new ChoosePlayerMenu(plugin, online, Math.max(0, page)));
        });
    }

    public void returnToPlaceBountiesMenu(Player player) {
        onMainThread(player, online -> {
            BountySession session = bountySession(online);
            if (session != null) openPlaceBountiesMenu(online, session);
        });
    }

    public UUID selectedBountyTarget(Player player) {
        BountySession session = bountySession(player);
        return session == null ? null : session.targetId;
    }

    public BigDecimal selectedBountyAmount(Player player) {
        BountySession session = bountySession(player);
        return session == null ? null : session.amount;
    }

    public boolean isBountyAnonymous(Player player) {
        BountySession session = bountySession(player);
        return session != null && session.anonymous;
    }

    public List<OfflinePlayer> historicalPlayers() {
        long now = System.currentTimeMillis();
        if (now < historicalPlayersExpiresAt) return historicalPlayers;
        List<OfflinePlayer> players = new ArrayList<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getUniqueId() != null && offlinePlayer.getName() != null
                    && (offlinePlayer.isOnline() || offlinePlayer.hasPlayedBefore())) players.add(offlinePlayer);
        }
        players.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));
        historicalPlayers = List.copyOf(players);
        historicalPlayersExpiresAt = now + 60_000L;
        return historicalPlayers;
    }

    public void selectBountyTarget(Player player, UUID targetId) {
        BountySession session = bountySession(player);
        if (session == null || session.completed || session.transactionInProgress) return;
        if (!plugin.bounties().isValidPlayerBountyTarget(targetId)) {
            player.sendMessage(plugin.store().message(player, "bounty.player.target-invalid"));
            return;
        }
        session.targetId = targetId;
        openPlaceBountiesMenu(player, session);
    }

    public void toggleBountyAnonymity(Player player) {
        BountySession session = bountySession(player);
        if (session == null || session.completed || session.transactionInProgress) return;
        session.anonymous = !session.anonymous;
        openPlaceBountiesMenu(player, session);
    }

    public void openBountyAmountInput(Player player) {
        BountySession session = bountySession(player);
        if (session == null || session.completed || session.transactionInProgress) return;
        session.amountInputOpen = true;
        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(Items.color("&5&lChoose Amount"))
                .text(session.amount == null ? "§7" : session.amount.toPlainString())
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return List.of();
                    String text = snapshot.getText();
                    if (text == null || !text.matches("[0-9]+")) {
                        snapshot.getPlayer().sendMessage(plugin.store().message(snapshot.getPlayer(), "bounty.player.amount-invalid"));
                        return List.of(AnvilGUI.ResponseAction.replaceInputText(""));
                    }
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(text);
                    } catch (NumberFormatException exception) {
                        snapshot.getPlayer().sendMessage(plugin.store().message(snapshot.getPlayer(), "bounty.player.amount-invalid"));
                        return List.of(AnvilGUI.ResponseAction.replaceInputText(""));
                    }
                    BountyPlacementResult validation = plugin.bounties().validatePlayerBountyAmount(amount);
                    if (validation != BountyPlacementResult.SUCCESS) {
                        sendPlacementError(snapshot.getPlayer(), validation);
                        return List.of(AnvilGUI.ResponseAction.replaceInputText(""));
                    }
                    BountySession activeSession = bountySession(snapshot.getPlayer());
                    if (activeSession == null || activeSession != session || activeSession.completed) {
                        return List.of(AnvilGUI.ResponseAction.close());
                    }
                    activeSession.amount = amount.stripTrailingZeros();
                    activeSession.amountInputOpen = false;
                    return List.of(AnvilGUI.ResponseAction.close(), AnvilGUI.ResponseAction.run(() -> openPlaceBountiesMenu(snapshot.getPlayer(), activeSession)));
                })
                .open(player);
    }

    public void confirmPlayerBounty(Player player) {
        BountySession session = bountySession(player);
        if (session == null || session.completed || session.transactionInProgress) return;
        if (session.targetId == null) {
            player.sendMessage(plugin.store().message(player, "bounty.player.target-required"));
            return;
        }
        if (session.amount == null) {
            player.sendMessage(plugin.store().message(player, "bounty.player.amount-required"));
            return;
        }
        session.transactionInProgress = true;
        BountyPlacementResult result = plugin.bounties().placePlayerBounty(player, session.targetId, session.amount);
        if (result != BountyPlacementResult.SUCCESS) {
            session.transactionInProgress = false;
            sendPlacementError(player, result);
            return;
        }
        session.completed = true;
        String targetName = playerName(session.targetId);
        String amount = session.amount.stripTrailingZeros().toPlainString();
        player.sendMessage(plugin.store().message(player, "bounty.player.success", "%target%", targetName, "%amount%", amount));
        if (plugin.store().config().getBoolean("bounty.broadcast.enabled", true)) {
            String message = session.anonymous
                    ? plugin.store().message("bounty.player.placed-anonymous", "%player%", targetName, "%amount%", amount)
                    : plugin.store().message("bounty.player.placed", "%creator%", player.getName(),
                            "%player%", targetName, "%amount%", amount);
            Bukkit.broadcastMessage(message);
        }
        bountySessions.remove(player.getUniqueId(), session);
        openJobMenu(player);
    }

    @EventHandler
    public void onBountyWorkflowClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        BountySession session = bountySession(player);
        if (session == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (bountySessions.get(player.getUniqueId()) != session) return;
            if (!player.isOnline() || session.completed) {
                bountySessions.remove(player.getUniqueId(), session);
                return;
            }
            var openInventory = player.getOpenInventory().getTopInventory();
            boolean bountyMenuOpen = openInventory.getHolder() instanceof PlaceBountiesMenu
                    || openInventory.getHolder() instanceof ChoosePlayerMenu;
            boolean amountInputOpen = session.amountInputOpen && openInventory.getType() == InventoryType.ANVIL;
            if (!bountyMenuOpen && !amountInputOpen) bountySessions.remove(player.getUniqueId(), session);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        historicalPlayersExpiresAt = 0L;
    }

    @EventHandler
    public void onBountyWorkflowQuit(PlayerQuitEvent event) {
        bountySessions.remove(event.getPlayer().getUniqueId());
    }

    public void selectJobFromMenu(Player player, JobType job) {
        onMainThread(player, online -> {
            if (job == null) return;
            JobResult result = job == JobType.CRIMINAL
                    ? plugin.jobsAndWanted().quitJob(online)
                    : plugin.jobsAndWanted().selectJob(online, job);
            sendResult(online, job, result);
            if (result == JobResult.SELECTED || result == JobResult.QUIT) openJobMenu(online);
        });
    }

    private void sendResult(Player player, JobType job, JobResult result) {
        if (result == JobResult.QUIT) {
            player.sendMessage(Items.color(plugin.store().config().getString("jobs.messages.quit", "&aYou are now a CRIMINAL.")));
            return;
        }
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
        String displayJob = job == JobType.COP ? "COP" : job.name();
        player.sendMessage(Items.color(plugin.store().config().getString(key, fallback).replace("%job%", displayJob)));
    }

    private void openPlaceBountiesMenu(Player player, BountySession session) {
        if (bountySessions.get(player.getUniqueId()) == session && !session.completed) {
            plugin.guis().open(new PlaceBountiesMenu(plugin, player));
        }
    }

    private BountySession bountySession(Player player) {
        if (player == null) return null;
        BountySession session = bountySessions.get(player.getUniqueId());
        return session != null && session.creatorId.equals(player.getUniqueId()) ? session : null;
    }

    private String playerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString() : player.getName();
    }

    private void sendPlacementError(Player player, BountyPlacementResult result) {
        String key = switch (result) {
            case NOT_HITMAN -> "bounty.player.job-required";
            case INVALID_TARGET -> "bounty.player.target-invalid";
            case INVALID_AMOUNT -> "bounty.player.amount-invalid";
            case AMOUNT_TOO_LOW -> "bounty.player.amount-too-low";
            case AMOUNT_TOO_HIGH -> "bounty.player.amount-too-high";
            case MAXIMUM_REWARD_EXCEEDED -> "bounty.player.maximum-exceeded";
            case INSUFFICIENT_FUNDS -> "bounty.player.insufficient-funds";
            default -> "bounty.player.failed";
        };
        player.sendMessage(plugin.store().message(player, key));
    }

    private void onMainThread(Player player, Consumer<Player> action) {
        if (player == null) return;
        Runnable task = () -> {
            if (plugin.isEnabled() && player.isOnline()) action.accept(player);
        };
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    private static final class BountySession {
        private final UUID creatorId;
        private UUID targetId;
        private BigDecimal amount;
        private boolean anonymous;
        private boolean amountInputOpen;
        private boolean transactionInProgress;
        private boolean completed;

        private BountySession(UUID creatorId, boolean anonymous) {
            this.creatorId = creatorId;
            this.anonymous = anonymous;
        }
    }
}