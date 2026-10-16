package com.districtx.pacifica;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Centralizes Pacifica-II storage locations and creates the feature-owned directories.
 * Taxi, combat, tag, essentials, and jobs keep their YAML and runtime files in their own
 * directories. ConfigStore loads these files at startup and on reload. The player database
 * remains global because multiple systems share it; only trash-can item prices are module-owned.
 * Legacy root-level messages are migrated into feature-owned message files without replacing
 * existing values.
 */
public final class PacificaPaths {
    private final Path root;

    public PacificaPaths(PacificaPlugin plugin) {
        root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            for (String feature : new String[]{"taxi", "combat", "tag", "essentials", "jobs", "SupplyDrops"}) {
                Files.createDirectories(getFeatureDirectory(feature));
                Files.createDirectories(getFeatureDirectory(feature).resolve("data"));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create Pacifica-II storage directories", exception);
        }
    }

    public Path getRoot() { return root; }
    public Path getMainConfig() { return root.resolve("config.yml"); }
    public Path getGlobalMessages() { return root.resolve("messages.yml"); }
    public Path getPlayerDatabase() { return root.resolve("player.db"); }
    public Path getItemPriceDatabase() { return getEssentialsDataDirectory().resolve("itemprices.db"); }

    public Path getTaxiDirectory() { return root.resolve("taxi"); }
    public Path getCombatDirectory() { return root.resolve("combat"); }
    public Path getTagDirectory() { return root.resolve("tag"); }
    public Path getEssentialsDirectory() { return root.resolve("essentials"); }
    public Path getJobsDirectory() { return root.resolve("jobs"); }
    public Path getSupplyDropsDirectory() { return root.resolve("SupplyDrops"); }

    public Path getTaxiConfig() { return getTaxiDirectory().resolve("config.yml"); }
    public Path getCombatConfig() { return getCombatDirectory().resolve("config.yml"); }
    public Path getTagConfig() { return getTagDirectory().resolve("config.yml"); }
    public Path getEssentialsConfig() { return getEssentialsDirectory().resolve("config.yml"); }
    public Path getJobsConfig() { return getJobsDirectory().resolve("config.yml"); }

    public Path getTaxiMessages() { return getTaxiDirectory().resolve("messages.yml"); }
    public Path getCombatMessages() { return getCombatDirectory().resolve("messages.yml"); }
    public Path getTagMessages() { return getTagDirectory().resolve("messages.yml"); }
    public Path getEssentialsMessages() { return getEssentialsDirectory().resolve("messages.yml"); }
    public Path getJobsMessages() { return getJobsDirectory().resolve("messages.yml"); }

    public Path getTaxiDataDirectory() { return getTaxiDirectory().resolve("data"); }
    public Path getCombatDataDirectory() { return getCombatDirectory().resolve("data"); }
    public Path getTagDataDirectory() { return getTagDirectory().resolve("data"); }
    public Path getEssentialsDataDirectory() { return getEssentialsDirectory().resolve("data"); }
    public Path getJobsDataDirectory() { return getJobsDirectory().resolve("data"); }

    /** Returns the directory owned by the named feature. */
    public Path getFeatureDirectory(String feature) {
        switch (feature) {
            case "taxi": return getTaxiDirectory();
            case "combat": return getCombatDirectory();
            case "tag": return getTagDirectory();
            case "essentials": return getEssentialsDirectory();
            case "jobs": return getJobsDirectory();
            case "SupplyDrops": return getSupplyDropsDirectory();
            default: throw new IllegalArgumentException("Unknown Pacifica-II feature: " + feature);
        }
    }

    /**
     * Copies the legacy root database into Essentials storage without replacing a newer target.
     * The legacy file is removed only after the copied bytes are verified.
     */
    public void migrateLegacyItemPriceDatabase(Logger logger) {
        migrateLegacyDatabaseFile(root.resolve("itemprices.db"), getItemPriceDatabase(), logger);
        migrateLegacyDatabaseFile(root.resolve("itemprices.db.backup"), getItemPriceDatabase().resolveSibling("itemprices.db.backup"), logger);
    }

    private void migrateLegacyDatabaseFile(Path legacy, Path target, Logger logger) {
        if (!Files.exists(legacy)) return;
        boolean copied = false;
        boolean verified = false;
        try {
            if (Files.exists(target)) {
                if (Files.mismatch(legacy, target) == -1) {
                    Files.delete(legacy);
                    logger.info("Migrated legacy item price storage into essentials/data/.");
                } else {
                    logger.warning("Keeping legacy item price storage because a destination file already exists.");
                }
                return;
            }
            copied = true;
            Files.copy(legacy, target);
            if (Files.mismatch(legacy, target) != -1) {
                throw new IOException("The copied database did not match the legacy database");
            }
            verified = true;
            Files.delete(legacy);
            logger.info("Migrated legacy item price storage into essentials/data/.");
        } catch (IOException exception) {
            if (copied && !verified) {
                try { Files.deleteIfExists(target); }
                catch (IOException ignored) { }
            }
            logger.warning("Could not migrate legacy item price storage: " + exception.getMessage());
        }
    }
}