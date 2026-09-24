package com.districtx.pacifica.database;

import com.districtx.pacifica.ConfigStore;
import com.districtx.pacifica.PacificaPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class PlayerDatabase extends AbstractSqliteDatabase {
    public PlayerDatabase(PacificaPlugin plugin) {
        super(new java.io.File(plugin.getDataFolder(), "player.db"));
    }

    @Override
    public synchronized void createTables() {
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, last_name TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_inventories (uuid TEXT NOT NULL, gamemode TEXT NOT NULL, inventory_data BLOB NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(uuid, gamemode), FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_tags (uuid TEXT NOT NULL, tag_id TEXT NOT NULL, equipped INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(uuid, tag_id), FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS plugin_state (state_id INTEGER PRIMARY KEY CHECK(state_id = 1), state_yaml TEXT NOT NULL)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create player database tables", exception);
        }
    }

    public synchronized void saveInventory(UUID uuid, GameMode gameMode, ItemStack[] contents) {
        long now = System.currentTimeMillis();
        try {
            ensurePlayer(uuid, null, now);
            try (PreparedStatement statement = getConnection().prepareStatement("INSERT INTO player_inventories(uuid, gamemode, inventory_data, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, gamemode) DO UPDATE SET inventory_data = excluded.inventory_data, updated_at = excluded.updated_at")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, gameMode.name());
                statement.setBytes(3, serialize(contents));
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Could not save inventory for " + uuid, exception);
        }
    }

    public synchronized ItemStack[] loadInventory(UUID uuid, GameMode gameMode) {
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT inventory_data FROM player_inventories WHERE uuid = ? AND gamemode = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, gameMode.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? deserialize(result.getBytes(1)) : null;
            }
        } catch (SQLException | IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load inventory for " + uuid, exception);
        }
    }

    public synchronized boolean hasInventory(UUID uuid, GameMode gameMode) {
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT 1 FROM player_inventories WHERE uuid = ? AND gamemode = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, gameMode.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not check inventory for " + uuid, exception);
        }
    }

    public synchronized Map<UUID, TagData> loadTags() {
        Map<UUID, TagDataBuilder> builders = new LinkedHashMap<>();
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT uuid, tag_id, equipped FROM player_tags ORDER BY uuid, tag_id"); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(result.getString("uuid"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                TagDataBuilder builder = builders.computeIfAbsent(uuid, ignored -> new TagDataBuilder());
                String tagId = result.getString("tag_id");
                builder.owned.add(tagId);
                if (result.getInt("equipped") != 0) builder.equipped = tagId;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load player tags", exception);
        }
        Map<UUID, TagData> result = new LinkedHashMap<>();
        builders.forEach((uuid, builder) -> result.put(uuid, new TagData(builder.owned, builder.equipped)));
        return result;
    }

    public synchronized void saveTags(UUID uuid, Set<String> owned, String equipped) {
        try {
            getConnection().setAutoCommit(false);
            ensurePlayer(uuid, null, System.currentTimeMillis());
            try (PreparedStatement delete = getConnection().prepareStatement("DELETE FROM player_tags WHERE uuid = ?"); PreparedStatement insert = getConnection().prepareStatement("INSERT INTO player_tags(uuid, tag_id, equipped) VALUES (?, ?, ?)")) {
                delete.setString(1, uuid.toString());
                delete.executeUpdate();
                for (String tagId : owned) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, tagId);
                    insert.setInt(3, tagId.equals(equipped) ? 1 : 0);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            getConnection().commit();
        } catch (SQLException exception) {
            rollback();
            throw new IllegalStateException("Could not save tags for " + uuid, exception);
        } finally {
            resetAutoCommit();
        }
    }

    public synchronized void migrateLegacyData(ConfigStore store) {
        try {
            if (!hasMetadata("legacy-yaml-v1")) {
                migrateInventories(store);
                migrateTags(store);
            }
            mergeLegacyState(store);
            store.data().set("separate-inventories", null);
            store.data().set("tags.players", null);
            saveState(store.data());
            setMetadata("legacy-yaml-v1", "1");
            setMetadata("legacy-state-v1", "1");
            store.deleteLegacyData();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not migrate legacy player data", exception);
        }
    }

    private void mergeLegacyState(ConfigStore store) {
        for (String path : store.legacyData().getKeys(true)) {
            if (path.equals("separate-inventories") || path.startsWith("separate-inventories.")
                    || path.equals("tags.players") || path.startsWith("tags.players.")) continue;
            if (!store.legacyData().isConfigurationSection(path) && !store.data().contains(path)) {
                store.data().set(path, store.legacyData().get(path));
            }
        }
    }

    public synchronized FileConfiguration loadState() {
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT state_yaml FROM plugin_state WHERE state_id = 1"); ResultSet result = statement.executeQuery()) {
            if (!result.next()) return null;
            FileConfiguration state = new YamlConfiguration();
            state.loadFromString(result.getString(1));
            return state;
        } catch (SQLException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load player database state", exception);
        }
    }

    public synchronized void saveState(FileConfiguration state) {
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT INTO plugin_state(state_id, state_yaml) VALUES (1, ?) ON CONFLICT(state_id) DO UPDATE SET state_yaml = excluded.state_yaml")) {
            statement.setString(1, state.saveToString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save player database state", exception);
        }
    }

    private void migrateInventories(ConfigStore store) {
        ConfigurationSection root = store.legacyData().getConfigurationSection("separate-inventories");
        if (root == null) return;
        for (String uuidKey : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection player = root.getConfigurationSection(uuidKey);
            if (player == null) continue;
            for (String modeKey : player.getKeys(false)) {
                GameMode mode;
                try {
                    mode = GameMode.valueOf(modeKey.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                ConfigurationSection inventory = player.getConfigurationSection(modeKey);
                if (inventory == null || hasInventory(uuid, mode)) continue;
                int size = inventory.getInt("size", 41);
                if (size < 0 || size > 1000) continue;
                ItemStack[] contents = new ItemStack[size];
                for (int slot = 0; slot < size; slot++) contents[slot] = inventory.getItemStack("slot." + slot);
                saveInventory(uuid, mode, contents);
            }
        }
    }

    private void migrateTags(ConfigStore store) {
        ConfigurationSection root = store.legacyData().getConfigurationSection("tags.players");
        if (root == null) return;
        for (String uuidKey : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                ConfigurationSection player = root.getConfigurationSection(uuidKey);
                if (player == null || hasTagRows(uuid)) continue;
                saveTags(uuid, new LinkedHashSet<>(player.getStringList("owned")), player.getString("equipped"));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private boolean hasTagRows(UUID uuid) {
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT 1 FROM player_tags WHERE uuid = ? LIMIT 1")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not check tags for " + uuid, exception);
        }
    }

    private void ensurePlayer(UUID uuid, String name, long now) throws SQLException {
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT INTO players(uuid, last_name, created_at, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET last_name = COALESCE(excluded.last_name, players.last_name), updated_at = excluded.updated_at")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private byte[] serialize(ItemStack[] contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null) {
                    output.writeInt(-1);
                } else {
                    byte[] serialized = item.serializeAsBytes();
                    output.writeInt(serialized.length);
                    output.write(serialized);
                }
            }
        }
        return bytes.toByteArray();
    }

    private ItemStack[] deserialize(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = input.readInt();
            if (size < 0 || size > 1000) throw new IOException("Invalid inventory size");
            ItemStack[] contents = new ItemStack[size];
            for (int slot = 0; slot < size; slot++) {
                int length = input.readInt();
                if (length >= 0) {
                    if (length > 10_000_000) throw new IOException("Invalid item data length");
                    byte[] item = input.readNBytes(length);
                    if (item.length != length) throw new IOException("Truncated item data");
                    contents[slot] = ItemStack.deserializeBytes(item);
                }
            }
            return contents;
        }
    }

    private void rollback() {
        try { getConnection().rollback(); } catch (SQLException ignored) { }
    }

    private void resetAutoCommit() {
        try { getConnection().setAutoCommit(true); } catch (SQLException ignored) { }
    }

    public static final class TagData {
        private final Set<String> owned;
        private final String equipped;

        private TagData(Set<String> owned, String equipped) {
            this.owned = Collections.unmodifiableSet(new LinkedHashSet<>(owned));
            this.equipped = equipped;
        }

        public Set<String> owned() { return owned; }
        public String equipped() { return equipped; }
    }

    private static final class TagDataBuilder {
        private final Set<String> owned = new LinkedHashSet<>();
        private String equipped;
    }
}