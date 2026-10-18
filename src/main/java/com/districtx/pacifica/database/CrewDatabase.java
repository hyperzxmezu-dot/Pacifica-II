package com.districtx.pacifica.database;

import com.districtx.pacifica.api.crew.Crew;
import com.districtx.pacifica.api.crew.CrewCreateReservation;
import com.districtx.pacifica.api.crew.CrewMember;
import com.districtx.pacifica.api.crew.CrewRank;
import com.districtx.pacifica.api.crew.CrewResult;
import com.districtx.pacifica.PacificaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CrewDatabase {
    private final File file;
    private Connection connection;

    public CrewDatabase(PacificaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "crew.db");
    }

    public synchronized void initialize() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new SQLException("Could not create crew database directory");
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("CREATE TABLE IF NOT EXISTS crews (id TEXT PRIMARY KEY, name TEXT NOT NULL, normalized_name TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, leader_uuid TEXT NOT NULL, crew_xp INTEGER NOT NULL DEFAULT 0, crew_level INTEGER NOT NULL DEFAULT 1, street_cred INTEGER NOT NULL DEFAULT 0, bank_balance TEXT NOT NULL DEFAULT '0', member_limit INTEGER NOT NULL, status INTEGER NOT NULL DEFAULT 1)");
                statement.execute("CREATE TABLE IF NOT EXISTS crew_members (player_uuid TEXT PRIMARY KEY, crew_id TEXT NOT NULL, rank TEXT NOT NULL, joined_at INTEGER NOT NULL, contributed_xp INTEGER NOT NULL DEFAULT 0, contributed_street_cred INTEGER NOT NULL DEFAULT 0, bank_contributions TEXT NOT NULL DEFAULT '0', FOREIGN KEY(crew_id) REFERENCES crews(id) ON DELETE CASCADE)");
                statement.execute("CREATE INDEX IF NOT EXISTS crew_members_crew_id ON crew_members(crew_id)");
                statement.execute("CREATE TABLE IF NOT EXISTS crew_invites (target_uuid TEXT PRIMARY KEY, crew_id TEXT NOT NULL, invited_by TEXT NOT NULL, expires_at INTEGER NOT NULL, FOREIGN KEY(crew_id) REFERENCES crews(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS crew_kill_cooldowns (killer_uuid TEXT NOT NULL, victim_uuid TEXT NOT NULL, last_rewarded INTEGER NOT NULL, PRIMARY KEY(killer_uuid, victim_uuid))");
                statement.executeUpdate("DELETE FROM crews WHERE status = 0");
                statement.executeUpdate("DELETE FROM crew_invites WHERE expires_at <= " + System.currentTimeMillis());
            }
        } catch (SQLException exception) {
            close();
            throw new IllegalStateException("Could not initialize crew database", exception);
        }
    }

    public synchronized List<Crew> loadCrews() {
        Map<UUID, CrewBuilder> crews = new LinkedHashMap<>();
        String sql = "SELECT c.id, c.name, c.created_at, c.leader_uuid, c.crew_xp, c.crew_level, c.street_cred, c.bank_balance, c.member_limit, m.player_uuid, m.rank, m.joined_at, m.contributed_xp, m.contributed_street_cred, m.bank_contributions FROM crews c LEFT JOIN crew_members m ON m.crew_id = c.id WHERE c.status = 1 ORDER BY c.created_at, m.joined_at";
        try (PreparedStatement statement = connection().prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID crewId = UUID.fromString(result.getString("id"));
                CrewBuilder crew = crews.computeIfAbsent(crewId, ignored -> new CrewBuilder(
                        crewId, resultString(result, "name"), resultLong(result, "created_at"),
                        resultUuid(result, "leader_uuid"), resultLong(result, "crew_xp"),
                        resultInt(result, "crew_level"), resultLong(result, "street_cred"),
                        resultString(result, "bank_balance"), resultInt(result, "member_limit")));
                String playerValue = result.getString("player_uuid");
                if (playerValue != null) {
                    UUID playerId = UUID.fromString(playerValue);
                    crew.members.put(playerId, new CrewMember(playerId,
                            CrewRank.valueOf(result.getString("rank")), result.getLong("joined_at"),
                            result.getLong("contributed_xp"), result.getLong("contributed_street_cred"),
                            new BigDecimal(result.getString("bank_contributions")).doubleValue()));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load crews", exception);
        }
        List<Crew> loaded = new ArrayList<>();
        for (CrewBuilder crew : crews.values()) loaded.add(crew.build());
        return List.copyOf(loaded);
    }

    public synchronized CrewCreateReservation reserveCreate(UUID leaderId, String name, String normalizedName,
                                                              int memberLimit) {
        return transaction(() -> {
            if (anyCrewIdForPlayer(leaderId) != null) return new CrewCreateReservation(CrewResult.ALREADY_IN_CREW, null);
            if (crewIdForName(normalizedName) != null) return new CrewCreateReservation(CrewResult.NAME_TAKEN, null);
            UUID crewId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection().prepareStatement("INSERT INTO crews(id, name, normalized_name, created_at, leader_uuid, member_limit, status) VALUES (?, ?, ?, ?, ?, ?, 0)")) {
                statement.setString(1, crewId.toString());
                statement.setString(2, name);
                statement.setString(3, normalizedName);
                statement.setLong(4, now);
                statement.setString(5, leaderId.toString());
                statement.setInt(6, memberLimit);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection().prepareStatement("INSERT INTO crew_members(player_uuid, crew_id, rank, joined_at) VALUES (?, ?, 'LEADER', ?)")) {
                statement.setString(1, leaderId.toString());
                statement.setString(2, crewId.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
            return new CrewCreateReservation(CrewResult.SUCCESS, crewId);
        });
    }

    public synchronized boolean activateCreate(UUID crewId) {
        return transaction(() -> {
            try (PreparedStatement statement = connection().prepareStatement("UPDATE crews SET status = 1 WHERE id = ? AND status = 0")) {
                statement.setString(1, crewId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public synchronized void cancelCreate(UUID crewId) {
        transaction(() -> {
            try (PreparedStatement statement = connection().prepareStatement("DELETE FROM crews WHERE id = ? AND status = 0")) {
                statement.setString(1, crewId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public synchronized CrewResult invite(UUID actorId, UUID targetId, long expiresAt) {
        return transaction(() -> {
            if (actorId.equals(targetId)) return CrewResult.CANNOT_TARGET_SELF;
            UUID crewId = crewIdForPlayer(actorId);
            if (crewId == null) return CrewResult.NOT_IN_CREW;
            CrewRank actorRank = rank(crewId, actorId);
            if (actorRank != CrewRank.LEADER && actorRank != CrewRank.CO_LEADER) return CrewResult.NO_PERMISSION;
            if (anyCrewIdForPlayer(targetId) != null) return CrewResult.ALREADY_MEMBER;
            int memberLimit = memberLimit(crewId);
            if (memberCount(crewId) + invitationCount(crewId) >= memberLimit) return CrewResult.CREW_FULL;
            try (PreparedStatement query = connection().prepareStatement("SELECT expires_at FROM crew_invites WHERE target_uuid = ?")) {
                query.setString(1, targetId.toString());
                try (ResultSet result = query.executeQuery()) {
                    if (result.next()) {
                        if (result.getLong(1) > System.currentTimeMillis()) return CrewResult.INVITE_EXISTS;
                        try (PreparedStatement delete = connection().prepareStatement("DELETE FROM crew_invites WHERE target_uuid = ?")) {
                            delete.setString(1, targetId.toString());
                            delete.executeUpdate();
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection().prepareStatement("INSERT INTO crew_invites(target_uuid, crew_id, invited_by, expires_at) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, targetId.toString());
                statement.setString(2, crewId.toString());
                statement.setString(3, actorId.toString());
                statement.setLong(4, expiresAt);
                statement.executeUpdate();
            }
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult accept(UUID playerId, String normalizedName) {
        return transaction(() -> {
            if (anyCrewIdForPlayer(playerId) != null) return CrewResult.ALREADY_IN_CREW;
            UUID crewId = null;
            long expiresAt = 0;
            try (PreparedStatement statement = connection().prepareStatement("SELECT i.crew_id, i.expires_at FROM crew_invites i JOIN crews c ON c.id = i.crew_id WHERE i.target_uuid = ? AND c.normalized_name = ? AND c.status = 1")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalizedName);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        crewId = UUID.fromString(result.getString(1));
                        expiresAt = result.getLong(2);
                    }
                }
            }
            if (crewId == null) return CrewResult.INVITATION_NOT_FOUND;
            if (expiresAt <= System.currentTimeMillis()) {
                deleteInvite(playerId);
                return CrewResult.INVITATION_EXPIRED;
            }
            if (memberCount(crewId) >= memberLimit(crewId)) return CrewResult.CREW_FULL;
            try (PreparedStatement statement = connection().prepareStatement("INSERT INTO crew_members(player_uuid, crew_id, rank, joined_at) VALUES (?, ?, 'MEMBER', ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crewId.toString());
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            deleteInvite(playerId);
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult deny(UUID playerId, String normalizedName) {
        return transaction(() -> {
            try (PreparedStatement statement = connection().prepareStatement("DELETE FROM crew_invites WHERE target_uuid = ? AND crew_id = (SELECT id FROM crews WHERE normalized_name = ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalizedName);
                return statement.executeUpdate() == 1 ? CrewResult.SUCCESS : CrewResult.INVITATION_NOT_FOUND;
            }
        });
    }

    public synchronized CrewResult leave(UUID playerId) {
        return transaction(() -> {
            UUID crewId = crewIdForPlayer(playerId);
            if (crewId == null) return CrewResult.NOT_IN_CREW;
            if (rank(crewId, playerId) == CrewRank.LEADER) return CrewResult.LEADER_CANNOT_LEAVE;
            try (PreparedStatement statement = connection().prepareStatement("DELETE FROM crew_members WHERE player_uuid = ? AND crew_id = ?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, crewId.toString());
                statement.executeUpdate();
            }
            deleteInvite(playerId);
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult kick(UUID actorId, UUID targetId) {
        return transaction(() -> {
            if (actorId.equals(targetId)) return CrewResult.CANNOT_TARGET_SELF;
            UUID crewId = crewIdForPlayer(actorId);
            if (crewId == null) return CrewResult.NOT_IN_CREW;
            if (!crewId.equals(crewIdForPlayer(targetId))) return CrewResult.INVALID_TARGET;
            CrewRank actorRank = rank(crewId, actorId);
            CrewRank targetRank = rank(crewId, targetId);
            if (actorRank != CrewRank.LEADER && actorRank != CrewRank.CO_LEADER) return CrewResult.NO_PERMISSION;
            if (targetRank == CrewRank.LEADER || actorRank.ordinal() >= targetRank.ordinal()) return CrewResult.RANK_PROTECTED;
            try (PreparedStatement statement = connection().prepareStatement("DELETE FROM crew_members WHERE player_uuid = ? AND crew_id = ?")) {
                statement.setString(1, targetId.toString());
                statement.setString(2, crewId.toString());
                statement.executeUpdate();
            }
            deleteInvite(targetId);
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult changeRank(UUID actorId, UUID targetId, boolean promote) {
        return transaction(() -> {
            if (actorId.equals(targetId)) return CrewResult.CANNOT_TARGET_SELF;
            UUID crewId = crewIdForPlayer(actorId);
            if (crewId == null) return CrewResult.NOT_IN_CREW;
            if (!crewId.equals(crewIdForPlayer(targetId))) return CrewResult.INVALID_TARGET;
            CrewRank actorRank = rank(crewId, actorId);
            CrewRank targetRank = rank(crewId, targetId);
            if (actorRank != CrewRank.LEADER && actorRank != CrewRank.CO_LEADER) return CrewResult.NO_PERMISSION;
            if (targetRank == CrewRank.LEADER || actorRank.ordinal() >= targetRank.ordinal()) return CrewResult.RANK_PROTECTED;
            CrewRank newRank = promote ? switch (targetRank) {
                case MEMBER -> CrewRank.OFFICER;
                case OFFICER -> CrewRank.CO_LEADER;
                default -> null;
            } : switch (targetRank) {
                case CO_LEADER -> CrewRank.OFFICER;
                case OFFICER -> CrewRank.MEMBER;
                default -> null;
            };
            if (newRank == null) return CrewResult.RANK_PROTECTED;
            if ((targetRank == CrewRank.CO_LEADER || newRank == CrewRank.CO_LEADER)
                    && actorRank != CrewRank.LEADER) return CrewResult.NO_PERMISSION;
            try (PreparedStatement statement = connection().prepareStatement("UPDATE crew_members SET rank = ? WHERE player_uuid = ? AND crew_id = ?")) {
                statement.setString(1, newRank.name());
                statement.setString(2, targetId.toString());
                statement.setString(3, crewId.toString());
                statement.executeUpdate();
            }
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult transferLeadership(UUID actorId, UUID targetId) {
        return transaction(() -> {
            if (actorId.equals(targetId)) return CrewResult.CANNOT_TARGET_SELF;
            UUID crewId = crewIdForPlayer(actorId);
            if (crewId == null) return CrewResult.NOT_IN_CREW;
            if (rank(crewId, actorId) != CrewRank.LEADER) return CrewResult.NO_PERMISSION;
            if (!crewId.equals(crewIdForPlayer(targetId))) return CrewResult.INVALID_TARGET;
            try (PreparedStatement updateCrew = connection().prepareStatement("UPDATE crews SET leader_uuid = ? WHERE id = ?");
                 PreparedStatement oldLeader = connection().prepareStatement("UPDATE crew_members SET rank = 'MEMBER' WHERE player_uuid = ? AND crew_id = ?");
                 PreparedStatement newLeader = connection().prepareStatement("UPDATE crew_members SET rank = 'LEADER' WHERE player_uuid = ? AND crew_id = ?")) {
                updateCrew.setString(1, targetId.toString());
                updateCrew.setString(2, crewId.toString());
                updateCrew.executeUpdate();
                oldLeader.setString(1, actorId.toString());
                oldLeader.setString(2, crewId.toString());
                oldLeader.executeUpdate();
                newLeader.setString(1, targetId.toString());
                newLeader.setString(2, crewId.toString());
                newLeader.executeUpdate();
            }
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult awardCrewExperience(UUID playerId, long amount, int maximumLevel, long totalXp) {
        if (amount <= 0) return CrewResult.FAILED;
        return transaction(() -> {
            UUID crewId = crewIdForPlayer(playerId);
            if (crewId == null) return CrewResult.NOT_IN_CREW;
            addCrewExperience(crewId, playerId, amount, maximumLevel, totalXp);
            return CrewResult.SUCCESS;
        });
    }

    public synchronized CrewResult recordPlayerKill(UUID killerId, UUID victimId, long baseXp,
                                                     long cooldownMillis, int maximumLevel, long totalXp) {
        if (killerId == null || victimId == null || killerId.equals(victimId) || baseXp <= 0) return CrewResult.FAILED;
        return transaction(() -> {
            UUID killerCrew = crewIdForPlayer(killerId);
            if (killerCrew == null) return CrewResult.NOT_IN_CREW;
            UUID victimCrew = crewIdForPlayer(victimId);
            if (killerCrew.equals(victimCrew)) return CrewResult.SAME_CREW;
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection().prepareStatement("SELECT last_rewarded FROM crew_kill_cooldowns WHERE killer_uuid = ? AND victim_uuid = ?")) {
                statement.setString(1, killerId.toString());
                statement.setString(2, victimId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && now - result.getLong(1) < cooldownMillis) return CrewResult.COOLDOWN;
                }
            }
            long reward = victimCrew == null ? baseXp
                    : Math.round(BigDecimal.valueOf(baseXp).multiply(BigDecimal.valueOf(2.5)).doubleValue());
            addCrewExperience(killerCrew, killerId, reward, maximumLevel, totalXp);
            try (PreparedStatement statement = connection().prepareStatement("INSERT INTO crew_kill_cooldowns(killer_uuid, victim_uuid, last_rewarded) VALUES (?, ?, ?) ON CONFLICT(killer_uuid, victim_uuid) DO UPDATE SET last_rewarded = excluded.last_rewarded")) {
                statement.setString(1, killerId.toString());
                statement.setString(2, victimId.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
            return CrewResult.SUCCESS;
        });
    }

    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }

    private void deleteInvite(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM crew_invites WHERE target_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private UUID crewIdForPlayer(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT m.crew_id FROM crew_members m JOIN crews c ON c.id = m.crew_id WHERE m.player_uuid = ? AND c.status = 1")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UUID.fromString(result.getString(1)) : null;
            }
        }
    }

    private UUID anyCrewIdForPlayer(UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT crew_id FROM crew_members WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UUID.fromString(result.getString(1)) : null;
            }
        }
    }

    private UUID crewIdForName(String normalizedName) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT id FROM crews WHERE normalized_name = ?")) {
            statement.setString(1, normalizedName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UUID.fromString(result.getString(1)) : null;
            }
        }
    }

    private CrewRank rank(UUID crewId, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT rank FROM crew_members WHERE crew_id = ? AND player_uuid = ?")) {
            statement.setString(1, crewId.toString());
            statement.setString(2, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? CrewRank.valueOf(result.getString(1)) : null;
            }
        }
    }

    private int memberCount(UUID crewId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT COUNT(*) FROM crew_members WHERE crew_id = ?")) {
            statement.setString(1, crewId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int memberLimit(UUID crewId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT member_limit FROM crews WHERE id = ? AND status = 1")) {
            statement.setString(1, crewId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int invitationCount(UUID crewId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT COUNT(*) FROM crew_invites WHERE crew_id = ? AND expires_at > ?")) {
            statement.setString(1, crewId.toString());
            statement.setLong(2, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private void addCrewExperience(UUID crewId, UUID contributorId, long amount,
                                   int maximumLevel, long totalXp) throws SQLException {
        long currentXp;
        try (PreparedStatement statement = connection().prepareStatement("SELECT crew_xp FROM crews WHERE id = ? AND status = 1")) {
            statement.setString(1, crewId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Crew is unavailable");
                currentXp = result.getLong(1);
            }
        }
        long updatedXp = currentXp > Long.MAX_VALUE - amount ? Long.MAX_VALUE : currentXp + amount;
        int level = new com.districtx.pacifica.api.crew.CrewProgression(maximumLevel, totalXp)
                .levelForExperience(updatedXp);
        try (PreparedStatement statement = connection().prepareStatement("UPDATE crews SET crew_xp = ?, crew_level = ? WHERE id = ?")) {
            statement.setLong(1, updatedXp);
            statement.setInt(2, level);
            statement.setString(3, crewId.toString());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection().prepareStatement("UPDATE crew_members SET contributed_xp = MIN(?, contributed_xp + ?) WHERE player_uuid = ? AND crew_id = ?")) {
            statement.setLong(1, Long.MAX_VALUE);
            statement.setLong(2, amount);
            statement.setString(3, contributorId.toString());
            statement.setString(4, crewId.toString());
            statement.executeUpdate();
        }
    }

    private Connection connection() {
        if (connection == null) throw new IllegalStateException("Crew database is closed");
        return connection;
    }

    private <T> T transaction(SqlOperation<T> operation) {
        try {
            connection().setAutoCommit(false);
            T result = operation.run();
            connection().commit();
            return result;
        } catch (SQLException exception) {
            try { connection().rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Crew database transaction failed", exception);
        } finally {
            try { connection().setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    private String resultString(ResultSet result, String column) {
        try { return result.getString(column); }
        catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    private long resultLong(ResultSet result, String column) {
        try { return result.getLong(column); }
        catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    private int resultInt(ResultSet result, String column) {
        try { return result.getInt(column); }
        catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    private UUID resultUuid(ResultSet result, String column) {
        try { return UUID.fromString(result.getString(column)); }
        catch (SQLException | IllegalArgumentException exception) { throw new IllegalStateException(exception); }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }

    private static final class CrewBuilder {
        private final UUID id;
        private final String name;
        private final long createdAt;
        private final UUID leaderId;
        private final long crewXp;
        private final int level;
        private final long streetCred;
        private final BigDecimal bankBalance;
        private final int memberLimit;
        private final Map<UUID, CrewMember> members = new LinkedHashMap<>();

        private CrewBuilder(UUID id, String name, long createdAt, UUID leaderId, long crewXp, int level,
                            long streetCred, String bankBalance, int memberLimit) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.leaderId = leaderId;
            this.crewXp = crewXp;
            this.level = level;
            this.streetCred = streetCred;
            this.bankBalance = new BigDecimal(bankBalance);
            this.memberLimit = memberLimit;
        }

        private Crew build() {
            return new Crew(id, name, createdAt, leaderId, crewXp, level, streetCred,
                    bankBalance, memberLimit, members);
        }
    }
}