package com.districtx.pacifica;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.districtx.pacifica.database.PlayerDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigStore {
    private final PacificaPlugin plugin;
    private final PacificaPaths paths;
    private final File folder;
    private final File configFile;
    private final File messagesFile;
    private FileConfiguration config;
    private FileConfiguration prices;
    private FileConfiguration messages;
    private FileConfiguration data;
    private FileConfiguration taxiConfiguration;
    private FileConfiguration combattagConfiguration;
    private boolean taxiConfigurationExisted;
    private boolean combattagConfigurationExisted;
    private final Map<String, FileConfiguration> featureConfigs = new HashMap<>();
    private final File dataFile;
    private final File pricesFile;
    private final Map<String, File> featureFiles = new HashMap<>();
    private final Map<String, FileConfiguration> featureMessages = new HashMap<>();
    private FileConfiguration globalMessages;
    private FileConfiguration essentialsConfiguration;
    private FileConfiguration jobsConfiguration;
    private final HashMap<UUID, String> lastChat = new HashMap<>();
    private final Set<String> warnedMessagePaths = new HashSet<>();
    private final PlayerDatabase playerDatabase;
    private final FileConfiguration legacyData;

    public ConfigStore(PacificaPlugin plugin) {
        this(plugin, null);
    }

    public ConfigStore(PacificaPlugin plugin, PlayerDatabase playerDatabase) {
        this.plugin = plugin;
        this.paths = plugin.paths();
        this.playerDatabase = playerDatabase;
        folder = paths.getRoot().toFile();
        configFile = paths.getMainConfig().toFile();
        messagesFile = paths.getGlobalMessages().toFile();
        dataFile = new File(folder, "data.yml");
        pricesFile = new File(folder, "item_prices.yml");
        migrateLegacyFeatureFiles();
        copyDefault("config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        prices = pricesFile.exists() ? YamlConfiguration.loadConfiguration(pricesFile) : new YamlConfiguration();
        globalMessages = messagesFile.exists() ? YamlConfiguration.loadConfiguration(messagesFile) : new YamlConfiguration();
        messages = globalMessages;
        legacyData = YamlConfiguration.loadConfiguration(dataFile);
        data = playerDatabase == null ? YamlConfiguration.loadConfiguration(dataFile) : playerDatabase.loadState();
        if (data == null) data = legacyData;
        loadConsolidatedConfigurations();
        loadAllFeatures();
        loadFeatureMessages();
        mergeLegacyFeatureFiles();
        migrateLegacyConfiguration();
        migrateOwnedConfigurationSections();
        migrateFeatureMessages();
        seedDefaultFeatureMessages();
        mergeCompatibilityConfiguration();
        refreshMessages();
    }

    private void copyDefault(String name) {
        File file = new File(folder, name);
        copyDefault(file, name);
    }

    private void copyDefault(File file, String resourceName) {
        if (file.exists()) return;
        try (InputStream in = resourceName == null ? null : plugin.getResource(resourceName)) {
            if (in != null) Files.copy(in, file.toPath());
            else if (!file.createNewFile()) throw new IOException("Could not create " + (resourceName == null ? file.getName() : resourceName));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create " + (resourceName == null ? file.getName() : resourceName), exception);
        }
    }

    public FileConfiguration config() { return config; }
    public FileConfiguration prices() { return prices; }
    public FileConfiguration messages() { return messages; }
    public FileConfiguration wastedMessages() { return combattagConfiguration; }
    public FileConfiguration data() { return data; }
    public FileConfiguration legacyData() { return legacyData; }
    public void deleteLegacyData() {
        try { Files.deleteIfExists(dataFile.toPath()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not remove migrated data.yml: " + exception.getMessage()); }
    }

    public void deleteLegacyPrices() {
        try { Files.deleteIfExists(pricesFile.toPath()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not remove migrated item_prices.yml: " + exception.getMessage()); }
    }
    public FileConfiguration feature(String name) { return featureConfigs.get(name); }
    public FileConfiguration getConfig(String feature) { return feature(feature); }
    public boolean exists(String feature) {
        File file = featureFiles.get(feature);
        return file != null && file.exists();
    }
    public FileConfiguration spawnConfig() { return taxiConfiguration; }
    public FileConfiguration taxi() { return taxiConfiguration; }
    public FileConfiguration trashcan() { return feature("trashcan"); }
    public FileConfiguration tag() { return feature("tag"); }
    public FileConfiguration tpa() { return taxiConfiguration; }
    public FileConfiguration tab() { return config; }
    public FileConfiguration motd() { return config; }
    public FileConfiguration teleport() { return taxiConfiguration; }
    public FileConfiguration combattag() { return combattagConfiguration; }
    public FileConfiguration moderation() { return config; }
    public FileConfiguration backpack() { return feature("backpack"); }
    public FileConfiguration protection() { return config; }
    public FileConfiguration wasted() { return combattagConfiguration; }
    public FileConfiguration essentials() { return feature("essentials"); }
    public FileConfiguration chat() { return feature("chat"); }
    public FileConfiguration warp() { return feature("warp"); }
    public String lastChat(org.bukkit.entity.Player player) { return lastChat.getOrDefault(player.getUniqueId(), ""); }
    public void setLastChat(org.bukkit.entity.Player player, String message) { lastChat.put(player.getUniqueId(), message); }
    public String message(String path, String... replacements) {
        String value = messages.getString(path);
        if (value == null) {
            value = path;
            if (warnedMessagePaths.add(path)) plugin.getLogger().warning("Missing or invalid message '" + path + "'; displaying its key as a fallback.");
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i + 1]);
        return com.districtx.pacifica.util.Items.color(value);
    }
    public String message(org.bukkit.entity.Player player, String path, String... replacements) {
        String value = message(path, replacements);
        return plugin.placeholders() == null ? value : com.districtx.pacifica.util.Items.color(plugin.placeholders().parse(player, value));
    }
    public void save() {
        if (playerDatabase != null) playerDatabase.saveState(data);
    }

    public void reload() {
        warnedMessagePaths.clear();
        migrateLegacyFeatureFiles();
        config = YamlConfiguration.loadConfiguration(configFile);
        prices = pricesFile.exists() ? YamlConfiguration.loadConfiguration(pricesFile) : new YamlConfiguration();
        globalMessages = messagesFile.exists() ? YamlConfiguration.loadConfiguration(messagesFile) : new YamlConfiguration();
        messages = globalMessages;
        data = playerDatabase == null ? YamlConfiguration.loadConfiguration(dataFile) : playerDatabase.loadState();
        if (data == null) data = new YamlConfiguration();
        loadConsolidatedConfigurations();
        loadAllFeatures();
        loadFeatureMessages();
        mergeLegacyFeatureFiles();
        migrateLegacyConfiguration();
        migrateOwnedConfigurationSections();
        migrateFeatureMessages();
        seedDefaultFeatureMessages();
        mergeCompatibilityConfiguration();
        refreshMessages();
        if (plugin.safeSpawn() != null) plugin.safeSpawn().refresh();
    }

    public void load(String feature) {
        if (isConsolidatedFeature(feature)) {
            reload();
            return;
        }
        loadFeature(feature, featureResource(feature));
        if (feature.equals("tag")) overlayMessageSection(feature("tag"), "tag-system", featureMessages.get("tag"), "tag-system");
    }

    public void reload(String feature) {
        load(feature);
    }

    public void save(String feature) {
        FileConfiguration configuration = featureConfigs.get(feature);
        File file = featureFiles.get(feature);
        if (configuration == null || file == null) return;
        FileConfiguration saved = new YamlConfiguration();
        copyConfiguration(saved, "", configuration);
        stripCompatibilityMessages(feature, saved);
        try { saved.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("Could not save " + feature + ".yml: " + exception.getMessage()); }
    }

    private void stripCompatibilityMessages(String feature, FileConfiguration configuration) {
        if (feature.equals("taxi") || feature.equals("spawn") || feature.equals("tpa") || feature.equals("teleport") || feature.equals("warp")) {
            for (String path : new String[]{"teleport", "tpa", "warp", "spawn-protection"}) configuration.set("messages." + path, null);
        } else if (feature.equals("combattag") || feature.equals("wasted")) {
            for (String path : new String[]{"combattag", "wasted", "pvp", "starvation", "fall"}) configuration.set("messages." + path, null);
            configuration.set("wanted.warden.messages", null);
        } else if (feature.equals("tag")) {
            configuration.set("tag-system.messages", null);
        } else if (feature.equals("jobs")) {
            configuration.set("jobs.messages", null);
            configuration.set("medic.messages", null);
        }
    }

    private void loadAllFeatures() {
        featureConfigs.clear();
        featureFiles.clear();
        featureConfigs.put("taxi", taxiConfiguration);
        featureConfigs.put("spawn", taxiConfiguration);
        featureConfigs.put("tpa", taxiConfiguration);
        featureConfigs.put("teleport", taxiConfiguration);
        featureConfigs.put("warp", taxiConfiguration);
        featureConfigs.put("combattag", combattagConfiguration);
        featureConfigs.put("wasted", combattagConfiguration);
        featureConfigs.put("protection", config);
        featureConfigs.put("moderation", config);
        featureFiles.put("protection", configFile);
        featureFiles.put("moderation", configFile);
        featureFiles.put("taxi", paths.getTaxiConfig().toFile());
        featureFiles.put("spawn", paths.getTaxiConfig().toFile());
        featureFiles.put("tpa", paths.getTaxiConfig().toFile());
        featureFiles.put("teleport", paths.getTaxiConfig().toFile());
        featureFiles.put("warp", paths.getTaxiConfig().toFile());
        featureFiles.put("combattag", paths.getCombatConfig().toFile());
        featureFiles.put("wasted", paths.getCombatConfig().toFile());
        essentialsConfiguration = loadConfiguration(paths.getEssentialsConfig().toFile(), null);
        jobsConfiguration = loadConfiguration(paths.getJobsConfig().toFile(), null);
        featureConfigs.put("motd", essentialsConfiguration);
        featureConfigs.put("tab", essentialsConfiguration);
        featureConfigs.put("jobs", jobsConfiguration);
        featureFiles.put("motd", paths.getEssentialsConfig().toFile());
        featureFiles.put("tab", paths.getEssentialsConfig().toFile());
        featureFiles.put("jobs", paths.getJobsConfig().toFile());
        loadFeature("trashcan", "trashcan.yml");
        loadFeature("tag", "tag.yml");
        loadFeature("backpack", "backpack.yml");
        loadFeature("essentials", "essentials.yml");
        loadFeature("chat", "chat.yml");
    }

    private void loadConsolidatedConfigurations() {
        File taxiFile = paths.getTaxiConfig().toFile();
        File combattagFile = paths.getCombatConfig().toFile();
        taxiConfigurationExisted = taxiFile.exists();
        combattagConfigurationExisted = combattagFile.exists();
        copyDefault(taxiFile, "taxi.yml");
        copyDefault(combattagFile, "combattag.yml");
        featureFiles.put("taxi", taxiFile);
        featureFiles.put("spawn", taxiFile);
        featureFiles.put("tpa", taxiFile);
        featureFiles.put("teleport", taxiFile);
        featureFiles.put("warp", taxiFile);
        featureFiles.put("combattag", combattagFile);
        featureFiles.put("wasted", combattagFile);
        taxiConfiguration = YamlConfiguration.loadConfiguration(taxiFile);
        combattagConfiguration = YamlConfiguration.loadConfiguration(combattagFile);
    }

    private void loadFeature(String name, String resource) {
        File file = featureConfigFile(name);
        featureFiles.put(name, file);
        copyDefault(file, resource);
        featureConfigs.put(name, YamlConfiguration.loadConfiguration(file));
    }

    private File featureConfigFile(String name) {
        switch (name) {
            case "taxi": return paths.getTaxiConfig().toFile();
            case "combattag": return paths.getCombatConfig().toFile();
            case "tag": return paths.getTagConfig().toFile();
            case "trashcan": return paths.getEssentialsDirectory().resolve("trashcan.yml").toFile();
            case "backpack": return paths.getEssentialsDirectory().resolve("backpack.yml").toFile();
            case "essentials": return paths.getEssentialsDirectory().resolve("essentials.yml").toFile();
            case "chat": return paths.getEssentialsDirectory().resolve("chat.yml").toFile();
            default: throw new IllegalArgumentException("Unknown feature configuration: " + name);
        }
    }

    private String featureResource(String name) {
        switch (name) {
            case "trashcan": return "trashcan.yml";
            case "tag": return "tag.yml";
            case "backpack": return "backpack.yml";
            case "essentials": return "essentials.yml";
            case "chat": return "chat.yml";
            default: return null;
        }
    }

    private FileConfiguration loadConfiguration(File file, String resource) {
        copyDefault(file, resource);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void loadFeatureMessages() {
        featureMessages.clear();
        featureMessages.put("taxi", loadConfiguration(paths.getTaxiMessages().toFile(), null));
        featureMessages.put("combat", loadConfiguration(paths.getCombatMessages().toFile(), null));
        featureMessages.put("tag", loadConfiguration(paths.getTagMessages().toFile(), null));
        featureMessages.put("essentials", loadConfiguration(paths.getEssentialsMessages().toFile(), null));
        featureMessages.put("jobs", loadConfiguration(paths.getJobsMessages().toFile(), null));
    }

    private void seedDefaultFeatureMessages() {
        FileConfiguration defaults = resourceConfiguration("messages.yml");
        Map<String, Boolean> changed = new HashMap<>();
        for (String path : defaults.getKeys(false)) {
            String owner = messageOwner(path);
            if (owner == null) owner = "essentials";
            FileConfiguration target = featureMessages.get(owner);
            if (defaults.isConfigurationSection(path)) {
                changed.merge(owner, copyMissingSection(target, path, defaults.getConfigurationSection(path)), Boolean::logicalOr);
            } else {
                changed.merge(owner, copyMissingValue(target, path, defaults.get(path)), Boolean::logicalOr);
            }
        }
        for (Map.Entry<String, Boolean> entry : changed.entrySet()) {
            if (entry.getValue()) {
                saveConfiguration(entry.getKey() + " messages", featureMessages.get(entry.getKey()), featureMessageFile(entry.getKey()));
            }
        }
    }

    private void migrateLegacyFeatureFiles() {
        migrateLegacyFile("taxi.yml", paths.getTaxiConfig());
        migrateLegacyFile("combattag.yml", paths.getCombatConfig());
        migrateLegacyFile("combat.yml", paths.getCombatConfig());
        migrateLegacyFile("tag.yml", paths.getTagConfig());
        migrateLegacyFile("essentials.yml", paths.getEssentialsDirectory().resolve("essentials.yml"));
        migrateLegacyFile("backpack.yml", paths.getEssentialsDirectory().resolve("backpack.yml"));
        migrateLegacyFile("trashcan.yml", paths.getEssentialsDirectory().resolve("trashcan.yml"));
        migrateLegacyFile("chat.yml", paths.getEssentialsDirectory().resolve("chat.yml"));
        archiveLegacyFile(new File(folder, "wanted.yml"), paths.getCombatDataDirectory().resolve("legacy").resolve("wanted.yml"));
        archiveLegacyFile(new File(folder, "jail.yml"), paths.getCombatDataDirectory().resolve("legacy").resolve("jail.yml"));
        archiveLegacyFile(new File(folder, "jobs.yml"), paths.getJobsDataDirectory().resolve("legacy").resolve("jobs.yml"));
        archiveLegacyFile(new File(folder, "bounty.yml"), paths.getJobsDataDirectory().resolve("legacy").resolve("bounty.yml"));
        archiveLegacyFile(new File(folder, "tags.yml"), paths.getTagDataDirectory().resolve("legacy").resolve("tags.yml"));
    }

    private void migrateLegacyFile(String name, java.nio.file.Path target) {
        File legacy = new File(folder, name);
        if (!legacy.exists()) return;
        boolean copied = false;
        boolean verified = false;
        try {
            if (Files.exists(target)) {
                if (Files.mismatch(legacy.toPath(), target) == -1) {
                    Files.delete(legacy.toPath());
                    plugin.getLogger().info("Migrated " + name + " into feature storage.");
                }
                return;
            }
            copied = true;
            Files.copy(legacy.toPath(), target);
            if (Files.mismatch(legacy.toPath(), target) != -1) {
                throw new IOException("Copied file did not match its source");
            }
            verified = true;
            Files.delete(legacy.toPath());
            plugin.getLogger().info("Migrated " + name + " into feature storage.");
        } catch (IOException exception) {
            if (copied && !verified) {
                try { Files.deleteIfExists(target); }
                catch (IOException ignored) { }
            }
            plugin.getLogger().warning("Could not migrate " + name + ": " + exception.getMessage());
        }
    }

    private void mergeLegacyFeatureFiles() {
        mergeLegacyFeatureFile("taxi.yml", taxiConfiguration, paths.getTaxiConfig().toFile());
        mergeLegacyFeatureFile("combattag.yml", combattagConfiguration, paths.getCombatConfig().toFile());
        mergeLegacyFeatureFile("combat.yml", combattagConfiguration, paths.getCombatConfig().toFile());
        mergeLegacyFeatureFile("tag.yml", feature("tag"), paths.getTagConfig().toFile());
        mergeLegacyFeatureFile("essentials.yml", feature("essentials"), featureConfigFile("essentials"));
        mergeLegacyFeatureFile("backpack.yml", feature("backpack"), featureConfigFile("backpack"));
        mergeLegacyFeatureFile("trashcan.yml", feature("trashcan"), featureConfigFile("trashcan"));
        mergeLegacyFeatureFile("chat.yml", feature("chat"), featureConfigFile("chat"));
    }

    private void mergeLegacyFeatureFile(String name, FileConfiguration target, File targetFile) {
        File legacyFile = new File(folder, name);
        if (!legacyFile.exists() || target == null) return;
        FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        boolean changed = copyMissingConfiguration(target, legacy);
        boolean saved = !changed || saveConfiguration(name, target, targetFile);
        if (saved && verifyConfiguration(target, legacy)) {
            removeLegacyFile(name.substring(0, name.length() - 4));
        } else if (saved) {
            if (!archiveLegacyFile(legacyFile, targetFile.toPath().getParent().resolve("data").resolve("legacy").resolve(name))) {
                plugin.getLogger().warning("Leaving legacy " + name + " in the plugin root because it could not be archived safely.");
            }
        }
    }

    private void migrateOwnedConfigurationSections() {
        boolean changed = false;
        changed |= transferRootSection("tab", essentialsConfiguration, "tab", "essentials", paths.getEssentialsConfig().toFile());
        changed |= transferRootSection("motd", essentialsConfiguration, "motd", "essentials", paths.getEssentialsConfig().toFile());
        changed |= transferRootSection("wanted", combattagConfiguration, "wanted", "combat", paths.getCombatConfig().toFile());
        changed |= transferRootSection("jail", combattagConfiguration, "jail", "combat", paths.getCombatConfig().toFile());
        changed |= transferRootSection("jobs", jobsConfiguration, "jobs", "jobs", paths.getJobsConfig().toFile());
        changed |= transferRootSection("cop", jobsConfiguration, "cop", "jobs", paths.getJobsConfig().toFile());
        changed |= transferRootSection("bounty", jobsConfiguration, "bounty", "jobs", paths.getJobsConfig().toFile());
        changed |= transferRootSection("medic", jobsConfiguration, "medic", "jobs", paths.getJobsConfig().toFile());
        if (changed) {
            if (!saveConfiguration("config", config, configFile)) {
                plugin.getLogger().warning("Feature configuration was migrated, but the main config could not be saved.");
            } else {
                plugin.getLogger().info("Migrated feature-owned configuration sections into their module folders.");
            }
        }
    }

    private boolean transferRootSection(String sourcePath, FileConfiguration target, String targetPath, String feature, File targetFile) {
        ConfigurationSection source = config.getConfigurationSection(sourcePath);
        if (source == null || target == null) return false;
        boolean copied = copyMissingSection(target, targetPath, source);
        if (copied && !saveConfiguration(feature, target, targetFile)) return false;
        if (!verifySection(target, targetPath, source)) return false;
        config.set(sourcePath, null);
        return true;
    }

    private void mergeCompatibilityConfiguration() {
        FileConfiguration combatMessages = featureMessages.get("combat");
        overlayMessageSection(combattagConfiguration, "messages.combattag", combatMessages, "combattag");
        overlayMessageSection(combattagConfiguration, "messages.wasted", combatMessages, "wasted");
        overlayMessageSection(combattagConfiguration, "wanted", combatMessages, "wanted");
        if (combatMessages != null) {
            for (String path : new String[]{"pvp", "starvation", "fall"}) {
                if (combatMessages.contains(path)) combattagConfiguration.set("messages." + path, combatMessages.get(path));
            }
        }

        FileConfiguration jobsMessages = featureMessages.get("jobs");
        overlayMessageSection(jobsConfiguration, "jobs", jobsMessages, "jobs");
        overlayMessageSection(jobsConfiguration, "medic", jobsMessages, "medic");
        overlayMessageSection(feature("tag"), "tag-system", featureMessages.get("tag"), "tag-system");

        mergeCompatibilitySection("tab", essentialsConfiguration);
        mergeCompatibilitySection("motd", essentialsConfiguration);
        mergeCompatibilitySection("wanted", combattagConfiguration);
        mergeCompatibilitySection("jail", combattagConfiguration);
        mergeCompatibilitySection("jobs", jobsConfiguration);
        mergeCompatibilitySection("cop", jobsConfiguration);
        mergeCompatibilitySection("bounty", jobsConfiguration);
        mergeCompatibilitySection("medic", jobsConfiguration);
    }

    private void overlayMessageSection(FileConfiguration target, String targetPath, FileConfiguration source, String sourcePath) {
        if (target == null || source == null) return;
        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        if (section != null) copySection(target, targetPath, section);
    }

    private void mergeCompatibilitySection(String path, FileConfiguration source) {
        if (source == null) return;
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section != null) copyConfiguration(config, path, section);
    }

    private void migrateFeatureMessages() {
        boolean taxiChanged = false;
        for (String path : new String[]{"teleport", "tpa", "warp", "spawn-protection"}) {
            taxiChanged |= transferMessageSection(taxiConfiguration, "messages." + path, "taxi", path);
        }
        boolean combatChanged = false;
        for (String path : new String[]{"combattag", "wasted"}) {
            combatChanged |= transferMessageSection(combattagConfiguration, "messages." + path, "combat", path);
        }
        combatChanged |= transferMessageSection(combattagConfiguration, "wanted.warden.messages", "combat", "wanted.warden.messages");
        for (String path : new String[]{"pvp", "starvation", "fall"}) {
            combatChanged |= transferMessageValue(combattagConfiguration, "messages." + path, "combat", path);
        }
        boolean tagChanged = transferMessageSection(feature("tag"), "tag-system.messages", "tag", "tag-system.messages");
        boolean jobsChanged = transferMessageSection(jobsConfiguration, "jobs.messages", "jobs", "jobs.messages");
        jobsChanged |= transferMessageSection(jobsConfiguration, "medic.messages", "jobs", "medic.messages");
        if (taxiChanged) saveConfiguration("taxi", taxiConfiguration, paths.getTaxiConfig().toFile());
        if (combatChanged) saveConfiguration("combat", combattagConfiguration, paths.getCombatConfig().toFile());
        if (tagChanged) saveConfiguration("tag", feature("tag"), paths.getTagConfig().toFile());
        if (jobsChanged) saveConfiguration("jobs", jobsConfiguration, paths.getJobsConfig().toFile());

        boolean globalChanged = migrateRemainingGlobalMessages();
        boolean migrated = globalChanged || taxiChanged || combatChanged || tagChanged || jobsChanged;
        if (migrated) plugin.getLogger().info("Migrated feature messages into their owner folders.");
    }

    private boolean migrateRemainingGlobalMessages() {
        if (!messagesFile.exists() && globalMessages.getKeys(false).isEmpty()) return false;
        boolean migrated = false;
        for (String path : new ArrayList<>(globalMessages.getKeys(false))) {
            String owner = messageOwner(path);
            if (owner == null) owner = "essentials";
            FileConfiguration target = featureMessages.get(owner);
            boolean changed;
            boolean verified;
            if (globalMessages.isConfigurationSection(path)) {
                ConfigurationSection source = globalMessages.getConfigurationSection(path);
                if (source == null) continue;
                changed = copyMissingSection(target, path, source);
                if (changed && !saveConfiguration(owner + " messages", target, featureMessageFile(owner))) return false;
                verified = verifySection(target, path, source);
            } else {
                Object value = globalMessages.get(path);
                changed = copyMissingValue(target, path, value);
                if (changed && !saveConfiguration(owner + " messages", target, featureMessageFile(owner))) return false;
                verified = target.contains(path) && Objects.equals(target.get(path), value);
            }
            if (verified) {
                globalMessages.set(path, null);
                migrated = true;
            }
        }
        if (globalMessages.getKeys(false).isEmpty()) {
            try {
                migrated |= Files.deleteIfExists(messagesFile.toPath());
                return migrated;
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not remove migrated messages.yml: " + exception.getMessage());
                return false;
            }
        }
        return archiveLegacyFile(messagesFile, paths.getEssentialsDataDirectory().resolve("legacy").resolve("messages.yml"));
    }

    private boolean transferMessageSection(FileConfiguration source, String sourcePath, String owner, String targetPath) {
        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        FileConfiguration target = featureMessages.get(owner);
        if (section == null || target == null) return false;
        boolean copied = copyMissingSection(target, targetPath, section);
        File targetFile = featureMessageFile(owner);
        if (copied && !saveConfiguration(owner + " messages", target, targetFile)) return false;
        if (!verifySection(target, targetPath, section)) return false;
        source.set(sourcePath, null);
        return true;
    }

    private boolean transferMessageValue(FileConfiguration source, String sourcePath, String owner, String targetPath) {
        FileConfiguration target = featureMessages.get(owner);
        if (source == null || target == null || !source.contains(sourcePath)) return false;
        Object value = source.get(sourcePath);
        if (!target.contains(targetPath)) target.set(targetPath, value);
        if (!Objects.equals(target.get(targetPath), value)) return false;
        if (!saveConfiguration(owner + " messages", target, featureMessageFile(owner))) return false;
        source.set(sourcePath, null);
        return true;
    }

    private File featureMessageFile(String owner) {
        switch (owner) {
            case "taxi": return paths.getTaxiMessages().toFile();
            case "combat": return paths.getCombatMessages().toFile();
            case "tag": return paths.getTagMessages().toFile();
            case "essentials": return paths.getEssentialsMessages().toFile();
            case "jobs": return paths.getJobsMessages().toFile();
            default: throw new IllegalArgumentException("Unknown message owner: " + owner);
        }
    }

    private String messageOwner(String path) {
        String group = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
        switch (group) {
            case "teleport":
            case "tpa":
            case "warp":
            case "spawn-protection": return "taxi";
            case "combattag":
            case "wasted":
            case "wanted":
            case "pvp":
            case "starvation":
            case "fall": return "combat";
            case "tag":
            case "tag-system": return "tag";
            case "trash":
            case "essentials":
            case "admin":
            case "general":
            case "plugin-list":
            case "anti-chat": return "essentials";
            case "bounty":
            case "jobs":
            case "medic": return "jobs";
            default: return null;
        }
    }

    private void refreshMessages() {
        FileConfiguration combined = new YamlConfiguration();
        copyConfiguration(combined, "", globalMessages);
        copyMessageSection(combined, taxiConfiguration);
        copyMessageSection(combined, combattagConfiguration);
        copyMessageSection(combined, config);
        for (String owner : new String[]{"taxi", "combat", "tag", "essentials", "jobs"}) {
            copyConfiguration(combined, "", featureMessages.get(owner));
        }
        messages = combined;
    }

    private void copyMessageSection(FileConfiguration target, FileConfiguration source) {
        if (source == null) return;
        ConfigurationSection section = source.getConfigurationSection("messages");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) copyConfiguration(target, key, section.getConfigurationSection(key));
            else target.set(key, section.get(key));
        }
    }

    private String[] legacyPaths(String name) {
        switch (name) {
            case "spawn": return new String[]{"spawn", "spawn-protection"};
            case "taxi": return new String[]{"taxi-gui"};
            case "trashcan": return new String[]{"trashcan", "trashcan-gui"};
            case "tag": return new String[]{"tag-system"};
            case "tpa": return new String[]{"tpa"};
            case "tab": return new String[]{"tab"};
            case "motd": return new String[]{"Server-MOTD", "server-list"};
            case "teleport": return new String[]{"teleport"};
            case "combattag": return new String[]{"combattag"};
            case "moderation": return new String[]{"anti-chat", "help"};
            case "backpack": return new String[]{"backpack"};
            case "protection": return new String[]{"protected-items", "weapons"};
            case "wasted": return new String[]{"wasted"};
            case "essentials": return new String[]{"essentials"};
            case "chat": return new String[]{"chat"};
            case "warp": return new String[]{"popular-warp-uses"};
            default: return new String[0];
        }
    }

    private void migrateLegacyConfiguration() {
        boolean changedConfig = false;
        boolean changedTaxi = false;
        boolean changedCombattag = false;
        for (String source : new String[]{"tpa", "teleport", "warp", "spawn"}) {
            FileConfiguration legacy = legacyFile(source);
            changedTaxi |= copyLegacyConfiguration(taxiConfiguration, legacy, taxiConfigurationExisted);
        }
        for (String source : new String[]{"wasted"}) {
            FileConfiguration legacy = legacyFile(source);
            changedCombattag |= copyLegacyConfiguration(combattagConfiguration, legacy, combattagConfigurationExisted);
        }
        for (String legacyMessageFile : new String[]{"wasted_message", "wasted_Messages"}) {
            FileConfiguration wastedLegacyMessages = legacyMessagesFile(legacyMessageFile);
            changedCombattag |= copyLegacyConfiguration(combattagConfiguration, "messages", wastedLegacyMessages, combattagConfigurationExisted);
        }
        String[] movedConfigPaths = {"taxi-gui", "tpa", "teleport", "popular-warp-uses", "spawn", "spawn-protection", "combattag", "wasted", "pvp", "starvation", "fall"};
        boolean hasMovedConfigPaths = false;
        for (String path : movedConfigPaths) hasMovedConfigPaths |= config.contains(path);
        if (hasMovedConfigPaths) backupLegacyConfiguration();

        changedTaxi |= migrateLegacyPaths(config, taxiConfiguration, new String[]{"taxi-gui", "tpa", "teleport", "popular-warp-uses", "spawn", "spawn-protection"}, !taxiConfigurationExisted);
        changedCombattag |= migrateLegacyPaths(config, combattagConfiguration, new String[]{"combattag", "wasted"}, !combattagConfigurationExisted);
        for (String path : new String[]{"pvp", "starvation", "fall"}) {
            if (!config.contains(path)) continue;
            ConfigurationSection section = config.getConfigurationSection(path);
            changedCombattag |= section == null
                    ? combattagConfigurationExisted
                        ? copyMissingValue(combattagConfiguration, "messages." + path, config.get(path))
                        : copyValueIfChanged(combattagConfiguration, "messages." + path, config.get(path))
                    : combattagConfigurationExisted
                        ? copyMissingSection(combattagConfiguration, "messages." + path, section)
                        : copySection(combattagConfiguration, "messages." + path, section);
        }
        for (String path : movedConfigPaths) {
            if (!config.contains(path)) continue;
            FileConfiguration target = path.equals("combattag") || path.equals("wasted") || path.equals("pvp") || path.equals("starvation") || path.equals("fall")
                    ? combattagConfiguration : taxiConfiguration;
            String targetPath = path.equals("pvp") || path.equals("starvation") || path.equals("fall") ? "messages." + path : path;
            if (verifyMigratedValue(config, path, target, targetPath)) {
                config.set(path, null);
                changedConfig = true;
            } else {
                plugin.getLogger().warning("Keeping legacy configuration section " + path + " because feature values differ.");
            }
        }

        changedConfig |= migrateRootConfiguration(config, "protection", "protected-items", "weapons");
        changedConfig |= migrateRootConfiguration(config, "motd", "server-list", "Server-MOTD");
        changedConfig |= migrateRootConfiguration(config, "moderation", "anti-chat", "help");
        for (String source : new String[]{"protection", "motd", "tab", "moderation"}) {
            changedConfig |= copyFeatureConfiguration(config, source, legacyFile(source));
        }
        boolean taxiSaved = !changedTaxi || saveConfiguration("taxi", taxiConfiguration, paths.getTaxiConfig().toFile());
        boolean combattagSaved = !changedCombattag || saveConfiguration("combattag", combattagConfiguration, paths.getCombatConfig().toFile());
        boolean configSaved = true;
        if (changedConfig) {
            try { config.save(configFile); }
            catch (IOException exception) {
                configSaved = false;
                plugin.getLogger().warning("Could not save migrated config.yml: " + exception.getMessage());
            }
        }
        boolean taxiVerified = taxiSaved && verifyTaxiMigration();
        boolean combattagVerified = combattagSaved && verifyCombattagMigration();
        boolean configVerified = configSaved && verifyConfigMigration();
        if (taxiVerified) {
            for (String name : new String[]{"tpa", "teleport", "warp", "spawn"}) removeLegacyFile(name);
        } else if (taxiSaved) {
            archiveLegacyFiles(new String[]{"tpa", "teleport", "warp", "spawn"}, paths.getTaxiDataDirectory());
        }
        if (combattagVerified) {
            for (String name : new String[]{"wasted", "wasted_message", "wasted_Messages"}) removeLegacyFile(name);
        } else if (combattagSaved) {
            archiveLegacyFiles(new String[]{"wasted", "wasted_message", "wasted_Messages"}, paths.getCombatDataDirectory());
        }
        if (configVerified) {
            for (String name : new String[]{"protection", "motd", "tab", "moderation"}) removeLegacyFile(name);
        } else if (configSaved) {
            archiveLegacyFiles(new String[]{"protection", "motd", "tab", "moderation"}, paths.getEssentialsDataDirectory());
        }
        archiveLegacyFiles(new String[]{"separate-inventory", "seperate-inventory"}, paths.getEssentialsDataDirectory());
    }

    private boolean isConsolidatedFeature(String feature) {
        return feature.equals("taxi") || feature.equals("spawn") || feature.equals("tpa")
                || feature.equals("teleport") || feature.equals("warp") || feature.equals("combattag")
                || feature.equals("wasted") || feature.equals("protection")
                || feature.equals("motd") || feature.equals("tab") || feature.equals("moderation")
                || feature.equals("jobs");
    }

    private boolean verifyTaxiMigration() {
        for (String source : new String[]{"tpa", "teleport", "warp", "spawn"}) {
            if (!verifyConfiguration(taxiConfiguration, legacyFile(source))) return false;
        }
        return true;
    }

    private boolean verifyCombattagMigration() {
        if (!verifyConfiguration(combattagConfiguration, legacyFile("wasted"))) return false;
        for (String source : new String[]{"wasted_message", "wasted_Messages"}) {
            if (!verifyConfiguration(combattagConfiguration, "messages", legacyMessagesFile(source))) return false;
        }
        return true;
    }

    private boolean verifyConfigMigration() {
        for (String source : new String[]{"protection", "motd", "tab", "moderation"}) {
            if (!verifyFeatureConfiguration(config, source, legacyFile(source))) return false;
        }
        return true;
    }

    private boolean verifyConfiguration(FileConfiguration target, FileConfiguration source) {
        return verifyConfiguration(target, "", source);
    }

    private boolean verifyConfiguration(FileConfiguration target, String targetPath, FileConfiguration source) {
        for (String key : source.getKeys(false)) {
            String path = targetPath.isEmpty() ? key : targetPath + "." + key;
            if (source.isConfigurationSection(key)) {
                ConfigurationSection section = source.getConfigurationSection(key);
                if (section == null || !verifySection(target, path, section)) return false;
            } else if (!target.contains(path) || !Objects.equals(target.get(path), source.get(key))) {
                return false;
            }
        }
        return true;
    }

    private boolean verifyFeatureConfiguration(FileConfiguration target, String feature, FileConfiguration source) {
        ConfigurationSection nested = source.getConfigurationSection(feature);
        if (nested != null && !verifySection(target, feature, nested)) return false;
        for (String key : source.getKeys(false)) {
            if (key.equals(feature) && source.isConfigurationSection(key)) continue;
            String path = feature + "." + key;
            if (source.isConfigurationSection(key)) {
                ConfigurationSection section = source.getConfigurationSection(key);
                if (section == null || !verifySection(target, path, section)) return false;
            } else if (!target.contains(path) || !Objects.equals(target.get(path), source.get(key))) {
                return false;
            }
        }
        return true;
    }

    private boolean verifySection(FileConfiguration target, String path, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            String targetPath = path + "." + key;
            if (source.isConfigurationSection(key)) {
                ConfigurationSection section = source.getConfigurationSection(key);
                if (section == null || !verifySection(target, targetPath, section)) return false;
            } else if (!target.contains(targetPath) || !Objects.equals(target.get(targetPath), source.get(key))) {
                return false;
            }
        }
        return true;
    }

    private boolean verifyMigratedValue(FileConfiguration source, String sourcePath, FileConfiguration target, String targetPath) {
        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        if (section != null) return verifySection(target, targetPath, section);
        return target.contains(targetPath) && Objects.equals(target.get(targetPath), source.get(sourcePath));
    }

    private FileConfiguration legacyFile(String name) {
        File file = new File(folder, name + ".yml");
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private FileConfiguration legacyMessagesFile(String name) {
        File file = new File(folder, name + ".yml");
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private FileConfiguration resourceConfiguration(String name) {
        InputStream input = plugin.getResource(name);
        if (input == null) return new YamlConfiguration();
        try (InputStream in = input) {
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read migration defaults from " + name + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private boolean copyMissingConfiguration(FileConfiguration target, FileConfiguration source) {
        return copyMissingConfiguration(target, "", source);
    }

    private boolean copyConfiguration(FileConfiguration target, FileConfiguration source) {
        return copyConfiguration(target, "", source);
    }

    private boolean copyConfiguration(FileConfiguration target, String targetPath, FileConfiguration source) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = targetPath.isEmpty() ? key : targetPath + "." + key;
            if (source.isConfigurationSection(key)) {
                changed |= copyConfiguration(target, path, source.getConfigurationSection(key));
            } else if (!Objects.equals(target.get(path), source.get(key))) {
                target.set(path, source.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private boolean copyConfiguration(FileConfiguration target, String targetPath, ConfigurationSection source) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = targetPath + "." + key;
            if (source.isConfigurationSection(key)) {
                changed |= copyConfiguration(target, path, source.getConfigurationSection(key));
            } else if (!Objects.equals(target.get(path), source.get(key))) {
                target.set(path, source.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private boolean copyMissingConfiguration(FileConfiguration target, String targetPath, FileConfiguration source) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = targetPath.isEmpty() ? key : targetPath + "." + key;
            if (source.isConfigurationSection(key)) {
                changed |= copyMissingSection(target, path, source.getConfigurationSection(key));
            } else if (!target.contains(path)) {
                target.set(path, source.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private boolean copyLegacyConfiguration(FileConfiguration target, FileConfiguration source, boolean targetExisted) {
        return targetExisted ? copyMissingConfiguration(target, source) : copyConfiguration(target, source);
    }

    private boolean copyLegacyConfiguration(FileConfiguration target, String targetPath, FileConfiguration source, boolean targetExisted) {
        return targetExisted ? copyMissingConfiguration(target, targetPath, source) : copyConfiguration(target, targetPath, source);
    }

    private boolean copyFeatureConfiguration(FileConfiguration target, String feature, FileConfiguration source) {
        return copyFeatureConfiguration(target, feature, source, false);
    }

    private boolean copyFeatureConfiguration(FileConfiguration target, String feature, FileConfiguration source, boolean overwrite) {
        boolean changed = false;
        ConfigurationSection nested = source.getConfigurationSection(feature);
        if (nested != null) changed |= overwrite ? copySection(target, feature, nested) : copyMissingSection(target, feature, nested);
        for (String key : source.getKeys(false)) {
            if (key.equals(feature) && source.isConfigurationSection(key)) continue;
            String path = feature + "." + key;
            if (source.isConfigurationSection(key)) {
                changed |= overwrite ? copySection(target, path, source.getConfigurationSection(key)) : copyMissingSection(target, path, source.getConfigurationSection(key));
            } else if (overwrite) {
                changed |= copyValueIfChanged(target, path, source.get(key));
            } else changed |= copyMissingValue(target, path, source.get(key));
        }
        return changed;
    }

    private boolean copyValueIfChanged(FileConfiguration target, String path, Object value) {
        if (Objects.equals(target.get(path), value)) return false;
        target.set(path, value);
        return true;
    }

    private boolean migrateRootConfiguration(FileConfiguration configuration, String targetSection, String... paths) {
        boolean changed = false;
        for (String path : paths) {
            if (!configuration.contains(path)) continue;
            ConfigurationSection section = configuration.getConfigurationSection(path);
            String targetPath = targetSection + "." + path;
            boolean copied = section == null
                    ? copyMissingValue(configuration, targetPath, configuration.get(path))
                    : copyMissingSection(configuration, targetPath, section);
            changed |= copied;
            boolean verified = section == null
                    ? configuration.contains(targetPath) && Objects.equals(configuration.get(targetPath), configuration.get(path))
                    : verifySection(configuration, targetPath, section);
            if (verified) {
                configuration.set(path, null);
                changed = true;
            }
        }
        return changed;
    }

    private boolean migrateLegacyPaths(FileConfiguration source, FileConfiguration target, String[] paths, boolean overwrite) {
        boolean changed = false;
        for (String path : paths) {
            if (!source.contains(path) || source == target) continue;
            ConfigurationSection section = source.getConfigurationSection(path);
            changed |= section == null
                    ? overwrite ? copyValueIfChanged(target, path, source.get(path)) : copyMissingValue(target, path, source.get(path))
                    : overwrite ? copySection(target, path, section) : copyMissingSection(target, path, section);
        }
        return changed;
    }

    private void backupLegacyConfiguration() {
        File source = new File(folder, "config.yml");
        File backup = new File(folder, "config.yml.backup");
        if (backup.exists()) return;
        try { Files.copy(source.toPath(), backup.toPath()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not back up config.yml: " + exception.getMessage()); }
    }

    private void migrateFeature(String name, String... legacyPaths) {
        File file = new File(folder, name + ".yml");
        boolean existed = file.exists();
        featureFiles.put(name, file);
        copyDefault(name + ".yml");
        FileConfiguration feature = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (String legacyPath : legacyPaths) if (config.contains(legacyPath)) {
            ConfigurationSection legacy = config.getConfigurationSection(legacyPath);
            changed |= !existed
                ? legacy == null ? copyValue(feature, legacyPath, config.get(legacyPath)) : copySection(feature, legacyPath, legacy)
                : legacy == null ? copyMissingValue(feature, legacyPath, config.get(legacyPath)) : copyMissingSection(feature, legacyPath, legacy);
            config.set(legacyPath, null);
        }
        if (changed) saveConfiguration(name, feature, file);
        featureConfigs.put(name, feature);
    }

    private boolean copyMissingSection(FileConfiguration target, String path, ConfigurationSection source) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String targetPath = path + "." + key;
            if (source.isConfigurationSection(key)) changed |= copyMissingSection(target, targetPath, source.getConfigurationSection(key));
            else if (!target.contains(targetPath)) { target.set(targetPath, source.get(key)); changed = true; }
        }
        return changed;
    }

    private boolean copySection(FileConfiguration target, String path, ConfigurationSection source) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String targetPath = path + "." + key;
            if (source.isConfigurationSection(key)) changed |= copySection(target, targetPath, source.getConfigurationSection(key));
            else { target.set(targetPath, source.get(key)); changed = true; }
        }
        return changed;
    }

    private boolean copyValue(FileConfiguration target, String path, Object value) {
        target.set(path, value);
        return true;
    }

    private boolean copyMissingValue(FileConfiguration target, String path, Object value) {
        if (target.contains(path)) return false;
        target.set(path, value);
        return true;
    }

    private boolean saveConfiguration(String name, FileConfiguration configuration, File file) {
        try {
            configuration.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not migrate " + name + ".yml: " + exception.getMessage());
            return false;
        }
    }

    private void removeLegacyFile(String name) {
        File source = new File(folder, name + ".yml");
        if (!source.exists()) return;
        try { Files.delete(source.toPath()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not remove migrated " + name + ".yml: " + exception.getMessage()); }
    }

    private void archiveLegacyFiles(String[] names, java.nio.file.Path featureDataDirectory) {
        for (String name : names) {
            archiveLegacyFile(new File(folder, name + ".yml"), featureDataDirectory.resolve("legacy").resolve(name + ".yml"));
        }
    }

    private boolean archiveLegacyFile(File source, java.nio.file.Path archive) {
        if (!source.exists()) return true;
        java.nio.file.Path target = archive;
        boolean copied = false;
        boolean verified = false;
        try {
            Files.createDirectories(archive.getParent());
            int suffix = 2;
            while (Files.exists(target) && Files.mismatch(source.toPath(), target) != -1) {
                String filename = archive.getFileName().toString();
                int extensionIndex = filename.lastIndexOf('.');
                String stem = extensionIndex < 0 ? filename : filename.substring(0, extensionIndex);
                String extension = extensionIndex < 0 ? "" : filename.substring(extensionIndex);
                target = archive.resolveSibling(stem + "-" + suffix++ + extension);
            }
            if (!Files.exists(target)) {
                copied = true;
                Files.copy(source.toPath(), target);
            }
            if (Files.mismatch(source.toPath(), target) != -1) throw new IOException("Archived file did not match its source");
            verified = true;
            Files.delete(source.toPath());
            plugin.getLogger().info("Archived legacy " + source.getName() + " under " + paths.getRoot().relativize(target) + ".");
            return true;
        } catch (IOException exception) {
            if (copied && !verified) {
                try { Files.deleteIfExists(target); }
                catch (IOException ignored) { }
            }
            plugin.getLogger().warning("Could not archive legacy " + source.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    public Location spawn() {
        return location(data, "spawn");
    }

    public void setSpawn(Location location) {
        setLocation(data, "spawn", location);
        save();
    }

    public Location safeSpawnPos1() {
        return location(data, "safe-spawn.pos1");
    }

    public Location safeSpawnPos2() {
        return location(data, "safe-spawn.pos2");
    }

    public void setSafeSpawnPos(int position, Location location) {
        if (position != 1 && position != 2) throw new IllegalArgumentException("Safe Spawn position must be 1 or 2");
        String path = position == 1 ? "safe-spawn.pos1" : "safe-spawn.pos2";
        setBlockLocation(data, path, location);
        save();
        if (plugin.safeSpawn() != null) plugin.safeSpawn().refresh();
    }

    public List<String> warpNames() {
        ConfigurationSection section = data.getConfigurationSection("warps");
        return section == null ? Collections.emptyList() : new ArrayList<>(section.getKeys(false));
    }

    public Location warp(String name) { return location(data, "warps." + name); }

    public void setWarp(String name, Location location) {
        setLocation(data, "warps." + name, location);
        save();
    }

    public UUID taxiNpc() {
        String value = data.getString("taxi-npc");
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public void setTaxiNpc(UUID uuid) { data.set("taxi-npc", uuid.toString()); save(); }

    public boolean isTrashCan(Location location) {
        for (String key : data.getStringList("trash-cans")) {
            if (key.equals(locationKey(location))) return true;
        }
        return false;
    }

    public void addTrashCan(Location location) {
        List<String> locations = new ArrayList<>(data.getStringList("trash-cans"));
        String key = locationKey(location);
        if (!locations.contains(key)) locations.add(key);
        data.set("trash-cans", locations);
        save();
    }

    public void removeTrashCan(Location location) {
        List<String> locations = new ArrayList<>(data.getStringList("trash-cans"));
        if (locations.remove(locationKey(location))) {
            data.set("trash-cans", locations);
            data.set("trash-holograms-enabled." + hologramKey(location), null);
            save();
        }
    }

    public boolean isTrashHologramEnabled(Location location) {
        return data.getBoolean("trash-holograms-enabled." + hologramKey(location), true);
    }

    public void setTrashHologramEnabled(Location location, boolean enabled) {
        data.set("trash-holograms-enabled." + hologramKey(location), enabled);
        save();
    }

    public List<Location> trashCanLocations() {
        List<Location> locations = new ArrayList<>();
        for (String key : data.getStringList("trash-cans")) {
            String[] parts = key.split(":", 4);
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            try { locations.add(new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))); }
            catch (NumberFormatException ignored) { }
        }
        return locations;
    }

    public List<String> holograms() { return data.getStringList("holograms"); }
    public void setHolograms(List<String> ids) { data.set("holograms", ids); save(); }
    public List<String> trashHolograms(Location location) { return data.getStringList("trash-holograms." + hologramKey(location)); }
    public void setTrashHolograms(Location location, List<String> ids) { data.set("trash-holograms." + hologramKey(location), ids); save(); }
    public void removeTrashHolograms(Location location) { data.set("trash-holograms." + hologramKey(location), null); save(); }

    private static String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static String hologramKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static Location location(FileConfiguration source, String path) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        return world == null ? null : new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private static void setLocation(FileConfiguration target, String path, Location location) {
        target.set(path + ".world", location.getWorld().getName());
        target.set(path + ".x", location.getX());
        target.set(path + ".y", location.getY());
        target.set(path + ".z", location.getZ());
        target.set(path + ".yaw", location.getYaw());
        target.set(path + ".pitch", location.getPitch());
    }

    private static void setBlockLocation(FileConfiguration target, String path, Location location) {
        target.set(path + ".world", location.getWorld().getName());
        target.set(path + ".x", location.getBlockX());
        target.set(path + ".y", location.getBlockY());
        target.set(path + ".z", location.getBlockZ());
    }
}