package com.districtx.pacifica;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final File folder;
    private FileConfiguration config;
    private FileConfiguration prices;
    private FileConfiguration messages;
    private FileConfiguration data;
    private FileConfiguration taxiConfiguration;
    private FileConfiguration combattagConfiguration;
    private final Map<String, FileConfiguration> featureConfigs = new HashMap<>();
    private final File dataFile;
    private final File pricesFile;
    private final Map<String, File> featureFiles = new HashMap<>();
    private final HashMap<UUID, String> lastChat = new HashMap<>();
    private final PlayerDatabase playerDatabase;
    private final FileConfiguration legacyData;

    public ConfigStore(PacificaPlugin plugin) {
        this(plugin, null);
    }

    public ConfigStore(PacificaPlugin plugin, PlayerDatabase playerDatabase) {
        this.plugin = plugin;
        this.playerDatabase = playerDatabase;
        File parent = plugin.getDataFolder().getParentFile();
        folder = new File(parent == null ? plugin.getDataFolder() : parent, "Pacifica-II");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Could not create Pacifica folder");
        copyDefault("config.yml");
        copyDefault("messages.yml");
        config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        pricesFile = new File(folder, "item_prices.yml");
        prices = pricesFile.exists() ? YamlConfiguration.loadConfiguration(pricesFile) : new YamlConfiguration();
        messages = YamlConfiguration.loadConfiguration(new File(folder, "messages.yml"));
        dataFile = new File(folder, "data.yml");
        legacyData = YamlConfiguration.loadConfiguration(dataFile);
        data = playerDatabase == null ? YamlConfiguration.loadConfiguration(dataFile) : playerDatabase.loadState();
        if (data == null) data = legacyData;
        loadConsolidatedConfigurations();
        migrateLegacyConfiguration();
        loadAllFeatures();
    }

    private void copyDefault(String name) {
        File file = new File(folder, name);
        if (file.exists()) return;
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) Files.copy(in, file.toPath());
            else if (!file.createNewFile()) throw new IOException("Could not create " + name);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create " + name, exception);
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
    public FileConfiguration separateInventory() { return config; }
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
        String value = consolidatedMessage(path);
        if (value == null) value = messages.getString(path, path);
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
        config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        prices = pricesFile.exists() ? YamlConfiguration.loadConfiguration(pricesFile) : new YamlConfiguration();
        messages = YamlConfiguration.loadConfiguration(new File(folder, "messages.yml"));
        data = playerDatabase == null ? YamlConfiguration.loadConfiguration(dataFile) : playerDatabase.loadState();
        if (data == null) data = new YamlConfiguration();
        loadConsolidatedConfigurations();
        migrateLegacyConfiguration();
        loadAllFeatures();
    }

    public void load(String feature) {
        if (isConsolidatedFeature(feature)) {
            reload();
            return;
        }
        loadFeature(feature, legacyPaths(feature));
    }

    public void reload(String feature) {
        load(feature);
    }

    public void save(String feature) {
        FileConfiguration configuration = featureConfigs.get(feature);
        File file = featureFiles.get(feature);
        if (configuration == null || file == null) return;
        try { configuration.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("Could not save " + feature + ".yml: " + exception.getMessage()); }
    }

    private void loadAllFeatures() {
        featureConfigs.clear();
        featureConfigs.put("taxi", taxiConfiguration);
        featureConfigs.put("spawn", taxiConfiguration);
        featureConfigs.put("tpa", taxiConfiguration);
        featureConfigs.put("teleport", taxiConfiguration);
        featureConfigs.put("warp", taxiConfiguration);
        featureConfigs.put("combattag", combattagConfiguration);
        featureConfigs.put("wasted", combattagConfiguration);
        featureConfigs.put("protection", config);
        featureConfigs.put("separate-inventory", config);
        featureConfigs.put("motd", config);
        featureConfigs.put("tab", config);
        featureConfigs.put("moderation", config);
        loadFeature("trashcan", legacyPaths("trashcan"));
        loadFeature("tag", legacyPaths("tag"));
        loadFeature("backpack", legacyPaths("backpack"));
        loadFeature("essentials", legacyPaths("essentials"));
        loadFeature("chat", legacyPaths("chat"));
    }

    private void loadConsolidatedConfigurations() {
        copyDefault("taxi.yml");
        copyDefault("combattag.yml");
        File taxiFile = new File(folder, "taxi.yml");
        File combattagFile = new File(folder, "combattag.yml");
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

    private void loadFeature(String name, String... ignoredLegacyPaths) {
        File file = new File(folder, name + ".yml");
        featureFiles.put(name, file);
        copyDefault(name + ".yml");
        featureConfigs.put(name, YamlConfiguration.loadConfiguration(file));
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
            case "separate-inventory": return new String[]{"separate-inventory"};
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
        boolean changedMessages = false;
        for (String source : new String[]{"tpa", "teleport", "warp", "spawn"}) {
            FileConfiguration legacy = legacyFile(source);
            changedTaxi |= copyConfiguration(taxiConfiguration, legacy);
        }
        for (String source : new String[]{"wasted"}) {
            FileConfiguration legacy = legacyFile(source);
            changedCombattag |= copyConfiguration(combattagConfiguration, legacy);
        }
        for (String legacyMessageFile : new String[]{"wasted_message", "wasted_Messages"}) {
            FileConfiguration wastedLegacyMessages = legacyMessagesFile(legacyMessageFile);
            changedCombattag |= copyConfiguration(combattagConfiguration, "messages", wastedLegacyMessages);
        }
        for (String path : new String[]{"teleport", "tpa", "warp", "spawn-protection"}) {
            ConfigurationSection section = messages.getConfigurationSection(path);
            if (section != null) {
                changedTaxi |= copyMissingSection(taxiConfiguration, "messages." + path, section);
                messages.set(path, null);
                changedMessages = true;
            }
        }
        for (String path : new String[]{"combattag", "wasted"}) {
            ConfigurationSection section = messages.getConfigurationSection(path);
            if (section != null) {
                changedCombattag |= copyMissingSection(combattagConfiguration, "messages." + path, section);
                messages.set(path, null);
                changedMessages = true;
            }
        }
        for (String path : new String[]{"anti-chat"}) {
            ConfigurationSection section = messages.getConfigurationSection(path);
            if (section != null) {
                changedConfig |= copyMissingSection(config, "messages." + path, section);
                messages.set(path, null);
                changedMessages = true;
            }
        }

        String[] movedConfigPaths = {"taxi-gui", "tpa", "teleport", "popular-warp-uses", "spawn", "spawn-protection", "combattag", "wasted", "pvp", "starvation", "fall"};
        boolean hasMovedConfigPaths = false;
        for (String path : movedConfigPaths) hasMovedConfigPaths |= config.contains(path);
        if (hasMovedConfigPaths) backupLegacyConfiguration();

        changedTaxi |= migrateLegacyPaths(config, taxiConfiguration, new String[]{"taxi-gui", "tpa", "teleport", "popular-warp-uses", "spawn", "spawn-protection"});
        changedCombattag |= migrateLegacyPaths(config, combattagConfiguration, new String[]{"combattag", "wasted"});
        for (String path : new String[]{"pvp", "starvation", "fall"}) {
            if (!config.contains(path)) continue;
            ConfigurationSection section = config.getConfigurationSection(path);
            changedCombattag |= section == null
                    ? copyMissingValue(combattagConfiguration, "messages." + path, config.get(path))
                    : copyMissingSection(combattagConfiguration, "messages." + path, section);
            config.set(path, null);
            changedConfig = true;
        }
        for (String path : movedConfigPaths) {
            if (!config.contains(path)) continue;
            config.set(path, null);
            changedConfig = true;
        }

        changedConfig |= migrateRootConfiguration(config, "protection", "protected-items", "weapons");
        changedConfig |= migrateRootConfiguration(config, "motd", "server-list", "Server-MOTD");
        changedConfig |= migrateRootConfiguration(config, "moderation", "anti-chat", "help");
        for (String source : new String[]{"protection", "separate-inventory", "motd", "tab", "moderation"}) {
            changedConfig |= copyFeatureConfiguration(config, source, legacyFile(source), true);
            if (source.equals("separate-inventory")) {
                changedConfig |= copyFeatureConfiguration(config, source, legacyFile("seperate-inventory"), true);
            }
        }
        if (config.getInt("config-version", 0) < 4) {
            config.set("config-version", 4);
            changedConfig = true;
        }
        if (taxiConfiguration.getInt("teleport.quick-play-countdown-seconds", 5) != 5) {
            taxiConfiguration.set("teleport.quick-play-countdown-seconds", 5);
            changedTaxi = true;
        }

        boolean taxiSaved = !changedTaxi || saveConfiguration("taxi", taxiConfiguration, new File(folder, "taxi.yml"));
        boolean combattagSaved = !changedCombattag || saveConfiguration("combattag", combattagConfiguration, new File(folder, "combattag.yml"));
        boolean messagesSaved = true;
        if (changedMessages) {
            try { messages.save(new File(folder, "messages.yml")); }
            catch (IOException exception) {
                messagesSaved = false;
                plugin.getLogger().warning("Could not save consolidated messages.yml: " + exception.getMessage());
            }
        }
        boolean configSaved = true;
        if (changedConfig) {
            try { config.save(new File(folder, "config.yml")); }
            catch (IOException exception) {
                configSaved = false;
                plugin.getLogger().warning("Could not save migrated config.yml: " + exception.getMessage());
            }
        }
        boolean taxiVerified = taxiSaved && verifyTaxiMigration();
        boolean combattagVerified = combattagSaved && verifyCombattagMigration();
        boolean configVerified = configSaved && verifyConfigMigration();
        if (taxiVerified && combattagVerified && configVerified && messagesSaved) {
            for (String name : new String[]{"tpa", "teleport", "warp", "spawn", "wasted", "wasted_message", "wasted_Messages", "protection", "separate-inventory", "seperate-inventory", "motd", "tab", "moderation"}) {
                removeLegacyFile(name);
            }
        }
    }

    private boolean isConsolidatedFeature(String feature) {
        return feature.equals("taxi") || feature.equals("spawn") || feature.equals("tpa")
                || feature.equals("teleport") || feature.equals("warp") || feature.equals("combattag")
                || feature.equals("wasted") || feature.equals("protection") || feature.equals("separate-inventory")
                || feature.equals("motd") || feature.equals("tab") || feature.equals("moderation");
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
        for (String source : new String[]{"protection", "separate-inventory", "motd", "tab", "moderation"}) {
            if (!verifyFeatureConfiguration(config, source, legacyFile(source))) return false;
        }
        return verifyFeatureConfiguration(config, "separate-inventory", legacyFile("seperate-inventory"));
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

    private FileConfiguration legacyFile(String name) {
        File file = new File(folder, name + ".yml");
        if (name.equals("separate-inventory") && !file.exists()) {
            file = new File(folder, "seperate-inventory.yml");
        }
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
            changed |= section == null
                    ? copyMissingValue(configuration, targetSection + "." + path, configuration.get(path))
                    : copyMissingSection(configuration, targetSection + "." + path, section);
            configuration.set(path, null);
            changed = true;
        }
        return changed;
    }

    private String consolidatedMessage(String path) {
        if (taxiConfiguration != null && taxiConfiguration.contains("messages." + path)) {
            return taxiConfiguration.getString("messages." + path);
        }
        if (combattagConfiguration != null && combattagConfiguration.contains("messages." + path)) {
            return combattagConfiguration.getString("messages." + path);
        }
        if (config != null && config.contains("messages." + path)) {
            return config.getString("messages." + path);
        }
        return null;
    }

    private boolean migrateLegacyPaths(FileConfiguration source, FileConfiguration target, String[] paths) {
        boolean changed = false;
        for (String path : paths) {
            if (!source.contains(path) || source == target) continue;
            ConfigurationSection section = source.getConfigurationSection(path);
            changed |= section == null
                    ? copyMissingValue(target, path, source.get(path))
                    : copyMissingSection(target, path, section);
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