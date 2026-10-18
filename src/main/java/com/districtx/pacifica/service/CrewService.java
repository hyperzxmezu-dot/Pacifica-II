package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.crew.Crew;
import com.districtx.pacifica.api.crew.CrewAPI;
import com.districtx.pacifica.api.crew.CrewCreateReservation;
import com.districtx.pacifica.api.crew.CrewMember;
import com.districtx.pacifica.api.crew.CrewProgression;
import com.districtx.pacifica.api.crew.CrewResult;
import com.districtx.pacifica.database.CrewDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CrewService implements CrewAPI {
    private final PacificaPlugin plugin;
    private final CrewDatabase database;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Pacifica-II-Crew-Database");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Map<UUID, Crew> crewsById = Map.of();
    private volatile Map<String, UUID> crewIdsByName = Map.of();
    private volatile Map<UUID, UUID> crewIdsByPlayer = Map.of();
    private volatile CrewProgression progression;

    public CrewService(PacificaPlugin plugin) {
        this.plugin = plugin;
        database = new CrewDatabase(plugin);
        database.initialize();
        reloadProgression();
        refreshCache();
    }

    @Override
    public Optional<Crew> getCrewForPlayer(UUID playerId) {
        UUID crewId = playerId == null ? null : crewIdsByPlayer.get(playerId);
        return crewId == null ? Optional.empty() : getCrewById(crewId);
    }

    @Override
    public Optional<Crew> getCrewById(UUID crewId) {
        return Optional.ofNullable(crewId == null ? null : crewsById.get(crewId));
    }

    @Override
    public Optional<Crew> getCrewByName(String name) {
        UUID crewId = name == null ? null : crewIdsByName.get(name.toLowerCase(Locale.ROOT));
        return crewId == null ? Optional.empty() : getCrewById(crewId);
    }

    public List<Crew> allCrews() {
        return List.copyOf(crewsById.values());
    }

    @Override
    public Optional<CrewMember> getMember(UUID playerId) {
        return getCrewForPlayer(playerId).map(crew -> crew.members().get(playerId));
    }

    @Override
    public int getCrewLevel(UUID playerId) {
        return getCrewForPlayer(playerId).map(Crew::level).orElse(0);
    }

    @Override
    public long getCrewExperience(UUID playerId) {
        return getCrewForPlayer(playerId).map(Crew::crewXp).orElse(0L);
    }

    @Override
    public long getStreetCred(UUID playerId) {
        return getCrewForPlayer(playerId).map(Crew::streetCred).orElse(0L);
    }

    @Override
    public int getPerkLevel(UUID playerId, String perkId) {
        return 0;
    }

    @Override
    public CompletableFuture<CrewResult> awardCrewExperience(UUID playerId, long amount) {
        CrewProgression currentProgression = progression;
        return mutate(() -> database.awardCrewExperience(playerId, amount,
                currentProgression.maximumLevel(), currentProgression.totalXp()));
    }

    public CompletableFuture<CrewResult> recordPlayerKill(UUID killerId, UUID victimId,
                                                           long baseXp, long cooldownMillis) {
        CrewProgression currentProgression = progression;
        return mutate(() -> database.recordPlayerKill(killerId, victimId, baseXp, cooldownMillis,
                currentProgression.maximumLevel(), currentProgression.totalXp()));
    }

    public CrewProgression progression() {
        return progression;
    }

    public void reloadProgression() {
        var config = plugin.store().config();
        int maximumLevel = config.getInt("crew.progression.maximum-level", CrewProgression.DEFAULT_MAX_LEVEL);
        long totalXp = config.getLong("crew.progression.total-xp", CrewProgression.DEFAULT_TOTAL_XP);
        progression = new CrewProgression(maximumLevel, totalXp);
    }

    public CompletableFuture<CrewCreateReservation> reserveCreate(UUID leaderId, String name) {
        CrewResult validation = validateName(name);
        if (validation != CrewResult.SUCCESS) {
            return CompletableFuture.completedFuture(new CrewCreateReservation(validation, null));
        }
        int memberLimit = Math.max(1, plugin.store().config().getInt("crew.base-member-limit", 10));
        String normalized = name.toLowerCase(Locale.ROOT);
        return submit(() -> database.reserveCreate(leaderId, name, normalized, memberLimit));
    }

    public CompletableFuture<CrewResult> activateCreate(UUID crewId) {
        return submit(() -> {
            if (!database.activateCreate(crewId)) return CrewResult.FAILED;
            refreshCache();
            return CrewResult.SUCCESS;
        });
    }

    public CompletableFuture<Void> cancelCreate(UUID crewId) {
        return submit(() -> {
            database.cancelCreate(crewId);
            return null;
        });
    }

    public CompletableFuture<CrewResult> invite(UUID actorId, UUID targetId) {
        long duration = Math.max(1, plugin.store().config().getLong("crew.invitation-expiration-hours", 24));
        long expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(duration);
        return mutate(() -> database.invite(actorId, targetId, expiresAt));
    }

    public CompletableFuture<CrewResult> accept(UUID playerId, String crewName) {
        return mutate(() -> database.accept(playerId, crewName.toLowerCase(Locale.ROOT)));
    }

    public CompletableFuture<CrewResult> deny(UUID playerId, String crewName) {
        return mutate(() -> database.deny(playerId, crewName.toLowerCase(Locale.ROOT)));
    }

    public CompletableFuture<CrewResult> leave(UUID playerId) {
        return mutate(() -> database.leave(playerId));
    }

    public CompletableFuture<CrewResult> kick(UUID actorId, UUID targetId) {
        return mutate(() -> database.kick(actorId, targetId));
    }

    public CompletableFuture<CrewResult> changeRank(UUID actorId, UUID targetId, boolean promote) {
        return mutate(() -> database.changeRank(actorId, targetId, promote));
    }

    public CompletableFuture<CrewResult> transferLeadership(UUID actorId, UUID targetId) {
        return mutate(() -> database.transferLeadership(actorId, targetId));
    }

    public double creationCost() {
        double value = plugin.store().config().getDouble("crew.creation-cost", 50000.0);
        return Double.isFinite(value) && value > 0 ? value : 50000.0;
    }

    public int minimumPlayerLevel() {
        return Math.max(0, plugin.store().config().getInt("crew.minimum-player-level", 5));
    }

    private CrewResult validateName(String name) {
        if (name == null) return CrewResult.NAME_INVALID;
        var config = plugin.store().config();
        int minimum = Math.max(1, config.getInt("crew.name-min-length", 3));
        int maximum = Math.max(minimum, config.getInt("crew.name-max-length", 16));
        if (name.length() < minimum || name.length() > maximum) return CrewResult.NAME_INVALID;
        String regex = config.getString("crew.name-pattern", "[A-Za-z0-9_]+");
        try {
            if (!Pattern.matches(regex, name)) return CrewResult.NAME_INVALID;
        } catch (PatternSyntaxException exception) {
            plugin.getLogger().warning("Invalid crew.name-pattern; using the safe default.");
            if (!Pattern.matches("[A-Za-z0-9_]+", name)) return CrewResult.NAME_INVALID;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (config.getStringList("crew.reserved-names").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals)) {
            return CrewResult.NAME_RESERVED;
        }
        return CrewResult.SUCCESS;
    }

    private CompletableFuture<CrewResult> mutate(Supplier<CrewResult> operation) {
        return submit(() -> {
            CrewResult result = operation.get();
            if (result == CrewResult.SUCCESS) refreshCache();
            return result;
        });
    }

    private <T> CompletableFuture<T> submit(Supplier<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        databaseExecutor.execute(() -> {
            try {
                future.complete(operation.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
                plugin.getLogger().severe("Crew database operation failed: " + throwable.getMessage());
            }
        });
        return future;
    }

    private void refreshCache() {
        Map<UUID, Crew> nextCrews = new HashMap<>();
        Map<String, UUID> nextNames = new HashMap<>();
        Map<UUID, UUID> nextPlayers = new HashMap<>();
        for (Crew crew : database.loadCrews()) {
            nextCrews.put(crew.id(), crew);
            nextNames.put(crew.name().toLowerCase(Locale.ROOT), crew.id());
            crew.members().keySet().forEach(playerId -> nextPlayers.put(playerId, crew.id()));
        }
        crewsById = Map.copyOf(nextCrews);
        crewIdsByName = Map.copyOf(nextNames);
        crewIdsByPlayer = Map.copyOf(nextPlayers);
    }

    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) databaseExecutor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            databaseExecutor.shutdownNow();
        }
        database.close();
    }
}