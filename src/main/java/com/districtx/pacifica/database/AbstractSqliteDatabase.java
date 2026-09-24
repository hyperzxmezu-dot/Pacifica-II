package com.districtx.pacifica.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

abstract class AbstractSqliteDatabase implements DatabaseManager {
    private final File file;
    private Connection connection;

    protected AbstractSqliteDatabase(File file) {
        this.file = file;
    }

    @Override
    public synchronized void initialize() {
        if (isConnected()) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create database directory");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = DELETE");
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS database_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            }
            createTables();
            setMetadataIfMissing("schema-version", "1");
        } catch (SQLException | IOException exception) {
            close();
            throw new IllegalStateException("Could not initialize " + file.getName(), exception);
        }
    }

    @Override
    public synchronized Connection getConnection() {
        if (!isConnected()) throw new IllegalStateException("Database is not connected: " + file.getName());
        return connection;
    }

    @Override
    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }

    @Override
    public synchronized void backup() {
        if (!file.exists()) return;
        File backup = new File(file.getParentFile(), file.getName() + ".backup");
        if (backup.exists()) return;
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ignored) {
        }
    }

    protected synchronized boolean hasMetadata(String key) throws SQLException {
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT 1 FROM database_metadata WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    protected synchronized void setMetadataIfMissing(String key, String value) throws SQLException {
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT OR IGNORE INTO database_metadata(key, value) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    protected synchronized void setMetadata(String key, String value) throws SQLException {
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT INTO database_metadata(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    protected File file() {
        return file;
    }
}