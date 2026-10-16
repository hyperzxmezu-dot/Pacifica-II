package com.districtx.pacifica.service;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.XParticle;
import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.PacificaAPI;
import com.districtx.pacifica.api.supplydrops.SupplyDropRarity;
import com.districtx.pacifica.api.supplydrops.SupplyDropsAPI;
import com.districtx.pacifica.api.taxi.TaxiAPI;
import com.districtx.pacifica.gui.SupplyDropEditorMenu;
import com.districtx.pacifica.gui.SupplyDropSettingsMenu;
import com.districtx.pacifica.util.Items;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.wesjd.anvilgui.AnvilGUI;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

/** Owns SupplyDrops persistence, reward selection, visuals, interaction, and scheduling. */
public final class SupplyDropsManager implements SupplyDropsAPI, Listener {
    private static final int[] EDITOR_BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 27, 36, 45, 17, 26, 35, 44, 53, 46, 47, 48, 49, 50, 51, 52};
    private static final int[] CANOPY_X = {-1, -1, 0, 0};
    private static final int[] CANOPY_Z = {-1, 0, -1, 0};
    private static final int[] ROPE_X = {-1, -1, 1, 1};
    private static final int[] ROPE_Z = {-1, 1, -1, 1};
    private final PacificaPlugin plugin;
    private final Path directory;
    private final Path settingsPath;
    private final Path rewardsPath;
    private final Path playersPath;
    private final Map<UUID, EnumMap<SupplyDropRarity, Integer>> balances = new HashMap<>();
    private final Map<UUID, RewardEntry> rewardEntries = new HashMap<>();
    private final Map<UUID, ActiveDrop> activeDrops = new HashMap<>();
    private final LinkedHashSet<String> recentAutomaticLocations = new LinkedHashSet<>();
    private FileConfiguration settings;
    private FileConfiguration rewardsFile;
    private FileConfiguration playersFile;
    private BukkitTask automaticTask;
    private BukkitTask movementTask;

    public SupplyDropsManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        directory = plugin.paths().getSupplyDropsDirectory();
        settingsPath = directory.resolve("config.yml");
        rewardsPath = directory.resolve("rewards.yml");
        playersPath = directory.resolve("players.yml");
        loadFiles();
    }

    public void start() {
        if (movementTask == null) movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDrops, 1L, 1L);
        scheduleAutomaticDrops();
    }

    public void stop() {
        if (automaticTask != null) automaticTask.cancel();
        if (movementTask != null) movementTask.cancel();
        automaticTask = null;
        movementTask = null;
        for (ActiveDrop drop : new ArrayList<>(activeDrops.values())) removeDrop(drop, true);
        synchronized (this) { savePlayers(); }
    }

    public void reload() {
        loadFiles();
        for (ActiveDrop drop : activeDrops.values()) {
            if (drop.state != DropState.LANDED) continue;
            for (ArmorStand hologram : drop.holograms) hologram.remove();
            drop.holograms.clear();
            createHologram(drop);
        }
        scheduleAutomaticDrops();
    }

    @Override public boolean isAvailable() { return plugin.isEnabled(); }

    @Override
    public synchronized int getAmount(UUID playerId, SupplyDropRarity rarity) {
        if (playerId == null || rarity == null) return 0;
        return balances.getOrDefault(playerId, new EnumMap<>(SupplyDropRarity.class)).getOrDefault(rarity, 0);
    }

    @Override
    public synchronized boolean grantAmount(UUID playerId, SupplyDropRarity rarity, int amount) {
        if (playerId == null || rarity == null || amount <= 0) return false;
        long updated = (long) getAmount(playerId, rarity) + amount;
        if (updated > Integer.MAX_VALUE) return false;
        setBalance(playerId, rarity, (int) updated);
        return true;
    }

    @Override
    public synchronized boolean removeAmount(UUID playerId, SupplyDropRarity rarity, int amount) {
        if (playerId == null || rarity == null || amount <= 0) return false;
        int current = getAmount(playerId, rarity);
        if (current < amount) return false;
        setBalance(playerId, rarity, current - amount);
        return true;
    }

    public synchronized boolean setAmount(UUID playerId, SupplyDropRarity rarity, int amount) {
        if (playerId == null || rarity == null || amount < 0) return false;
        setBalance(playerId, rarity, amount);
        return true;
    }

    @Override
    public boolean summon(Player player, SupplyDropRarity rarity) {
        if (player == null || rarity == null || !player.isOnline() || !Bukkit.isPrimaryThread()) return false;
        synchronized (this) {
            if (getAmount(player.getUniqueId(), rarity) < 1) return false;
        }
        Location landing = findPlayerLanding(player);
        if (landing == null) return false;
        List<ItemStack> generated = generateRewards(rarity);
        if (generated == null) {
            player.sendMessage(Items.color("&cThis rarity does not have enough valid reward entries configured."));
            return false;
        }
        ActiveDrop drop = spawnDrop(landing, rarity, generated, false,
                settings.getBoolean("player-summon.require-flat-4x4", true), player.getLocation().getY() + 50.0D);
        if (drop == null) return false;
        synchronized (this) {
            int current = getAmount(player.getUniqueId(), rarity);
            if (current < 1) {
                removeDrop(drop, true);
                return false;
            }
            setBalance(player.getUniqueId(), rarity, current - 1);
        }
        return true;
    }

    public String rarityDisplay(SupplyDropRarity rarity) {
        String configured = settings.getString("rarities." + rarity.key() + ".display-name", rarity.displayName());
        String color = settings.getString("rarities." + rarity.key() + ".color", rarity.color());
        return Items.color(color + configured);
    }

    public List<RewardEntry> rewards(SupplyDropRarity rarity) {
        return rewardEntries.values().stream().filter(entry -> entry.rarity == rarity)
                .sorted(java.util.Comparator.comparing(entry -> entry.id.toString())).toList();
    }

    public List<String> taxiWarpNames() {
        TaxiAPI taxi = PacificaAPI.taxi();
        return taxi.isAvailable() ? taxi.getWarpNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList() : List.of();
    }

    public List<String> automaticWarpNames() {
        return List.copyOf(settings.getStringList("automatic.warps"));
    }

    public RewardEntry reward(UUID id) { return rewardEntries.get(id); }

    public void openEditor(Player player, SupplyDropRarity rarity) {
        player.openInventory(new SupplyDropEditorMenu(this, rarity).getInventory());
    }

    private synchronized void setBalance(UUID playerId, SupplyDropRarity rarity, int amount) {
        EnumMap<SupplyDropRarity, Integer> values = balances.computeIfAbsent(playerId,
                ignored -> new EnumMap<>(SupplyDropRarity.class));
        values.put(rarity, Math.max(0, amount));
        playersFile.set("players." + playerId + "." + rarity.key(), Math.max(0, amount));
        savePlayers();
    }

    private void loadFiles() {
        try { Files.createDirectories(directory); }
        catch (IOException exception) { throw new IllegalStateException("Could not create SupplyDrops directory", exception); }
        settings = loadYaml(settingsPath);
        rewardsFile = loadYaml(rewardsPath);
        playersFile = loadYaml(playersPath);
        seedDefaults();
        loadBalances();
        loadRewards();
        saveSettings();
        saveRewards();
        synchronized (this) { savePlayers(); }
    }

    private FileConfiguration loadYaml(Path path) {
        return Files.exists(path) ? YamlConfiguration.loadConfiguration(path.toFile()) : new YamlConfiguration();
    }

    private void seedDefaults() {
        setDefault(settings, "automatic.enabled", true);
        setDefault(settings, "automatic.interval-seconds", 7200);
        setDefault(settings, "automatic.warps", List.of());
        setDefault(settings, "automatic.area-width", 50);
        setDefault(settings, "automatic.max-location-retries", 30);
        setDefault(settings, "automatic.radius-blocks", 50);
        setDefault(settings, "automatic.location-validation.max-attempts",
                settings.getInt("automatic.max-location-retries", 30));
        setDefault(settings, "automatic.allowed-worlds", List.of());
        setDefault(settings, "automatic.rarity-selection.mode", "WEIGHTED");
        for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
            setDefault(settings, rarityWeightPath(rarity), defaultRarityWeight(rarity));
        }
        setDefault(settings, "player-summon.require-flat-4x4", true);
        setDefault(settings, "drop.canopy-material", "CARPET");
        setDefault(settings, "drop.canopy-green-material", "GREEN_CARPET");
        setDefault(settings, "drop.canopy-white-material", "WHITE_CARPET");
        setDefault(settings, "drop.expiration-seconds", 900);
        setDefault(settings, "drop.starting-height", 200);
        setDefault(settings, "drop.gravity", 0.2D);
        setDefault(settings, "drop.descent-speed", 0.12D);
        setDefault(settings, "landing.hologram-offset", 1.5D);
        setDefault(settings, "landing.particles.enabled", true);
        setDefault(settings, "landing.particles.duration-seconds", 600);
        setDefault(settings, "landing.particles.interval-ticks", 10);
        setDefault(settings, "landing.particles.redstone", true);
        setDefault(settings, "landing.particles.flame", true);
        setDefault(settings, "rewards.minimum-selections", 4);
        setDefault(settings, "rewards.maximum-selections", 6);
        setDefault(settings, "hologram.lines", List.of("&2&lSUPPLY AIRDROP", "&7Rarity: %level%"));
        setDefault(settings, "announcements.landing", "&6SupplyDrop &e%rarity% &6landed at &f%world% %x%, %y%, %z%&6.");
        for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
            setDefault(settings, "rarities." + rarity.key() + ".display-name", rarity.displayName());
            setDefault(settings, "rarities." + rarity.key() + ".color", rarity.color());
        }
        validateRarityWeights();
        validateDropSettings();
    }

    private String rarityWeightPath(SupplyDropRarity rarity) {
        return "automatic.rarity-selection.weights." + rarity.key();
    }

    private long defaultRarityWeight(SupplyDropRarity rarity) {
        return switch (rarity) {
            case COMMON -> 75L;
            case UNCOMMON -> 50L;
            case RARE -> 25L;
            case EPIC -> 10L;
            case LEGENDARY -> 1L;
        };
    }

    private void validateRarityWeights() {
        String mode = settings.getString("automatic.rarity-selection.mode", "WEIGHTED");
        if (!"WEIGHTED".equalsIgnoreCase(mode)) {
            plugin.getLogger().warning("Invalid SupplyDrops automatic.rarity-selection.mode '" + mode + "'; using WEIGHTED.");
            settings.set("automatic.rarity-selection.mode", "WEIGHTED");
        }
        for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
            String path = rarityWeightPath(rarity);
            Object raw = settings.get(path);
            if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < 0.0D || number.doubleValue() > Long.MAX_VALUE
                    || number.doubleValue() != Math.rint(number.doubleValue())) {
                plugin.getLogger().warning("Invalid SupplyDrops " + path + "; using default weight " + defaultRarityWeight(rarity) + ".");
                settings.set(path, defaultRarityWeight(rarity));
            }
        }
    }

    private void validateDropSettings() {
        int radius = settings.getInt("automatic.radius-blocks", 50);
        if (radius < 1 || radius > 10_000) {
            plugin.getLogger().warning("Invalid SupplyDrops automatic.radius-blocks; using 50.");
            settings.set("automatic.radius-blocks", 50);
        }
        int locationAttempts = settings.getInt("automatic.location-validation.max-attempts", 30);
        if (locationAttempts < 1 || locationAttempts > 200) {
            plugin.getLogger().warning("Invalid SupplyDrops automatic.location-validation.max-attempts; using 30.");
            settings.set("automatic.location-validation.max-attempts", 30);
        }
        int startingHeight = settings.getInt("drop.starting-height", 200);
        if (startingHeight < 1 || startingHeight > 4096) {
            plugin.getLogger().warning("Invalid SupplyDrops drop.starting-height; using 200.");
            settings.set("drop.starting-height", 200);
        }
        double gravity = settings.getDouble("drop.gravity", 0.2D);
        if (!Double.isFinite(gravity) || gravity <= 0.0D || gravity > 1.0D) {
            plugin.getLogger().warning("Invalid SupplyDrops drop.gravity; using 0.2.");
            settings.set("drop.gravity", 0.2D);
        }
        double descentSpeed = settings.getDouble("drop.descent-speed", 0.12D);
        if (!Double.isFinite(descentSpeed) || descentSpeed < 0.02D || descentSpeed > 0.5D) {
            plugin.getLogger().warning("Invalid SupplyDrops drop.descent-speed; using 0.12.");
            settings.set("drop.descent-speed", 0.12D);
        }
        double hologramOffset = settings.getDouble("landing.hologram-offset", 1.5D);
        if (!Double.isFinite(hologramOffset) || hologramOffset <= 0.0D || hologramOffset > 10.0D) {
            plugin.getLogger().warning("Invalid SupplyDrops landing.hologram-offset; using 1.5.");
            settings.set("landing.hologram-offset", 1.5D);
        }
        int particleDuration = settings.getInt("landing.particles.duration-seconds", 600);
        if (particleDuration < 1 || particleDuration > 86400) {
            plugin.getLogger().warning("Invalid SupplyDrops landing.particles.duration-seconds; using 600.");
            settings.set("landing.particles.duration-seconds", 600);
        }
        int particleInterval = settings.getInt("landing.particles.interval-ticks", 10);
        if (particleInterval < 1 || particleInterval > 1200) {
            plugin.getLogger().warning("Invalid SupplyDrops landing.particles.interval-ticks; using 10.");
            settings.set("landing.particles.interval-ticks", 10);
        }
    }

    private void setDefault(FileConfiguration configuration, String path, Object value) {
        if (!configuration.contains(path)) configuration.set(path, value);
    }

    private void loadBalances() {
        balances.clear();
        ConfigurationSection players = playersFile.getConfigurationSection("players");
        if (players == null) return;
        for (String rawId : players.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(rawId); }
            catch (IllegalArgumentException exception) { continue; }
            ConfigurationSection values = players.getConfigurationSection(rawId);
            if (values == null) continue;
            EnumMap<SupplyDropRarity, Integer> amounts = new EnumMap<>(SupplyDropRarity.class);
            for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
                amounts.put(rarity, Math.max(0, values.getInt(rarity.key(), 0)));
            }
            balances.put(id, amounts);
        }
    }

    private void loadRewards() {
        rewardEntries.clear();
        for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
            ConfigurationSection entries = rewardsFile.getConfigurationSection("rarities." + rarity.key() + ".entries");
            if (entries == null) continue;
            for (String rawId : entries.getKeys(false)) {
                UUID id;
                try { id = UUID.fromString(rawId); }
                catch (IllegalArgumentException exception) { continue; }
                String path = "rarities." + rarity.key() + ".entries." + rawId;
                ItemStack item = rewardsFile.getItemStack(path + ".item");
                Object rawWeight = rewardsFile.get(path + ".weight");
                Object rawChance = rewardsFile.get(path + ".chance");
                Object rawMinimum = rewardsFile.get(path + ".minimum-quantity");
                Object rawMaximum = rewardsFile.get(path + ".maximum-quantity");
                double weightValue = rawWeight instanceof Number number ? number.doubleValue() : rawWeight == null ? 1.0D : Double.NaN;
                double chanceValue = rawChance instanceof Number number ? number.doubleValue() : rawChance == null ? 100.0D : Double.NaN;
                double minimumValue = rawMinimum instanceof Number number ? number.doubleValue() : rawMinimum == null ? 1.0D : Double.NaN;
                double maximumValue = rawMaximum instanceof Number number ? number.doubleValue() : rawMaximum == null ? minimumValue : Double.NaN;
                if (item == null || item.getType().isAir() || !rewardsFile.getBoolean(path + ".enabled", true)
                        || !Double.isFinite(weightValue) || weightValue < 1.0D || weightValue > Long.MAX_VALUE
                        || weightValue != Math.rint(weightValue) || !Double.isFinite(chanceValue)
                        || chanceValue < 0.0D || chanceValue > 100.0D || !Double.isFinite(minimumValue)
                        || minimumValue < 1.0D || minimumValue > Integer.MAX_VALUE || minimumValue != Math.rint(minimumValue)
                        || !Double.isFinite(maximumValue) || maximumValue < minimumValue
                        || maximumValue > Integer.MAX_VALUE || maximumValue != Math.rint(maximumValue)) continue;
                long weight = ((Number) (rawWeight == null ? Integer.valueOf(1) : rawWeight)).longValue();
                double chance = chanceValue;
                int minimum = (int) minimumValue;
                int maximum = (int) maximumValue;
                rewardEntries.put(id, new RewardEntry(id, rarity, item, weight, chance, minimum, maximum));
            }
        }
    }

    private synchronized void savePlayers() { saveYaml(playersFile, playersPath); }
    private void saveSettings() { saveYaml(settings, settingsPath); }
    private void saveRewards() { saveYaml(rewardsFile, rewardsPath); }

    private void saveYaml(FileConfiguration configuration, Path path) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, configuration.saveToString());
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save SupplyDrops data " + path.getFileName() + ": " + exception.getMessage());
        }
    }

    public boolean addReward(SupplyDropRarity rarity, ItemStack item) {
        if (rarity == null || item == null || item.getType().isAir()) return false;
        int capacity = 54 - EDITOR_BORDER.length;
        if (rewards(rarity).size() >= capacity) return false;
        ItemStack stored = item.clone();
        stored.setAmount(1);
        UUID id = UUID.randomUUID();
        RewardEntry entry = new RewardEntry(id, rarity, stored, 1L, 100.0D, 1, 1);
        rewardEntries.put(id, entry);
        persistReward(entry);
        return true;
    }

    public boolean removeReward(UUID id) {
        RewardEntry entry = rewardEntries.remove(id);
        if (entry == null) return false;
        rewardsFile.set("rarities." + entry.rarity.key() + ".entries." + id, null);
        saveRewards();
        return true;
    }

    private void persistReward(RewardEntry entry) {
        String path = "rarities." + entry.rarity.key() + ".entries." + entry.id;
        rewardsFile.set(path + ".rarity", entry.rarity.key());
        rewardsFile.set(path + ".item", entry.item);
        rewardsFile.set(path + ".weight", entry.weight);
        rewardsFile.set(path + ".chance", entry.chance);
        rewardsFile.set(path + ".minimum-quantity", entry.minimumQuantity);
        rewardsFile.set(path + ".maximum-quantity", entry.maximumQuantity);
        saveRewards();
    }

    private List<ItemStack> generateRewards(SupplyDropRarity rarity) {
        List<RewardEntry> eligible = new ArrayList<>();
        for (RewardEntry entry : rewards(rarity)) {
            if (ThreadLocalRandom.current().nextDouble(100.0D) < entry.chance) eligible.add(entry);
        }
        int minimum = Math.max(4, Math.min(6, settings.getInt("rewards.minimum-selections", 4)));
        int maximum = Math.max(minimum, Math.min(6, settings.getInt("rewards.maximum-selections", 6)));
        if (eligible.size() < minimum) return null;
        int count = ThreadLocalRandom.current().nextInt(minimum, Math.min(maximum, eligible.size()) + 1);
        List<ItemStack> generated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RewardEntry selected = weightedPick(eligible);
            if (selected == null) return null;
            eligible.remove(selected);
            int amount = (int) ThreadLocalRandom.current().nextLong(selected.minimumQuantity, (long) selected.maximumQuantity + 1L);
            int stackSize = Math.max(1, selected.item.getMaxStackSize());
            while (amount > 0) {
                if (generated.size() >= 9) return null;
                ItemStack stack = selected.item.clone();
                int stackAmount = Math.min(stackSize, amount);
                stack.setAmount(stackAmount);
                generated.add(stack);
                amount -= stackAmount;
            }
        }
        return generated.size() < 4 || generated.size() > 9 ? null : generated;
    }

    private RewardEntry weightedPick(List<RewardEntry> entries) {
        BigInteger total = BigInteger.ZERO;
        for (RewardEntry entry : entries) if (entry.weight > 0) total = total.add(BigInteger.valueOf(entry.weight));
        if (total.signum() <= 0) return null;
        BigInteger choice;
        do { choice = new BigInteger(total.bitLength(), ThreadLocalRandom.current()); }
        while (choice.compareTo(total) >= 0);
        for (RewardEntry entry : entries) {
            if (entry.weight <= 0) continue;
            BigInteger weight = BigInteger.valueOf(entry.weight);
            if (choice.compareTo(weight) < 0) return entry;
            choice = choice.subtract(weight);
        }
        return null;
    }

    private SupplyDropRarity selectAutomaticRarity() {
        EnumMap<SupplyDropRarity, BigInteger> weights = new EnumMap<>(SupplyDropRarity.class);
        BigInteger total = BigInteger.ZERO;
        for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
            long weight = settings.getLong(rarityWeightPath(rarity), defaultRarityWeight(rarity));
            if (weight <= 0L) continue;
            BigInteger value = BigInteger.valueOf(weight);
            weights.put(rarity, value);
            total = total.add(value);
        }
        if (total.signum() <= 0) return null;
        BigInteger choice;
        do { choice = new BigInteger(total.bitLength(), ThreadLocalRandom.current()); }
        while (choice.compareTo(total) >= 0);
        for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
            BigInteger weight = weights.get(rarity);
            if (weight == null) continue;
            if (choice.compareTo(weight) < 0) return rarity;
            choice = choice.subtract(weight);
        }
        return null;
    }

    private Location findPlayerLanding(Player player) {
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        List<int[]> offsets = new ArrayList<>();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) offsets.add(new int[]{x, z});
        Collections.shuffle(offsets, ThreadLocalRandom.current());
        for (int[] offset : offsets) {
            Location candidate = safeLanding(world, centerX + offset[0], centerZ + offset[1],
                    settings.getBoolean("player-summon.require-flat-4x4", true), null);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private Location safeLanding(World world, int x, int z, boolean requireFlat4x4, Player ignoredPlayer) {
        return safeLanding(world, x, z, requireFlat4x4, ignoredPlayer, null);
    }

    private Location safeLanding(World world, int x, int z, boolean requireFlat4x4, Player ignoredPlayer, ActiveDrop ignoredDrop) {
        if (world == null || !worldAllowed(world)) return null;
        for (ActiveDrop active : activeDrops.values()) {
            if ((ignoredDrop == null || !active.id.equals(ignoredDrop.id))
                    && active.landing.getWorld().equals(world) && active.landing.getBlockX() == x
                    && active.landing.getBlockZ() == z) return null;
        }
        Block support = world.getHighestBlockAt(x, z);
        if (!support.getType().isSolid() || support.isLiquid()) return null;
        int chestY = support.getY() + 1;
        if (chestY < world.getMinHeight() || chestY + 1 >= world.getMaxHeight()) return null;
        Block chestSpace = world.getBlockAt(x, chestY, z);
        if (!chestSpace.getType().isAir() || !world.getBlockAt(x, chestY + 1, z).getType().isAir()) return null;
        Location location = new Location(world, x + 0.5D, chestY, z + 0.5D);
        if (!world.getWorldBorder().isInside(location)) return null;
        if (requireFlat4x4) {
            for (int dx = -2; dx <= 1; dx++) {
                for (int dz = -2; dz <= 1; dz++) {
                    Block ground = world.getBlockAt(x + dx, support.getY(), z + dz);
                    Location footprint = new Location(world, x + dx + 0.5D, chestY, z + dz + 0.5D);
                    if (!ground.getType().isSolid() || ground.isLiquid()
                            || !world.getWorldBorder().isInside(footprint)
                            || !world.getBlockAt(x + dx, chestY, z + dz).getType().isAir()
                            || !world.getBlockAt(x + dx, chestY + 1, z + dz).getType().isAir()) return null;
                }
            }
        }
        for (Entity entity : world.getNearbyEntities(location, 0.65D, 1.1D, 0.65D)) {
            if (ignoredDrop != null && (entity.equals(ignoredDrop.crateDisplay)
                    || ignoredDrop.canopy.contains(entity) || ignoredDrop.holograms.contains(entity))) continue;
            if (ignoredPlayer == null || !entity.getUniqueId().equals(ignoredPlayer.getUniqueId())) return null;
        }
        return location;
    }

    private boolean worldAllowed(World world) {
        List<String> allowed = settings.getStringList("automatic.allowed-worlds");
        if (allowed.isEmpty()) return true;
        return allowed.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private ActiveDrop spawnDrop(Location landing, SupplyDropRarity rarity, List<ItemStack> contents,
                                 boolean automatic, boolean requireFlat4x4) {
        return spawnDrop(landing, rarity, contents, automatic, requireFlat4x4,
                settings.getInt("drop.starting-height", 200));
    }

    private ActiveDrop spawnDrop(Location landing, SupplyDropRarity rarity, List<ItemStack> contents,
                                 boolean automatic, boolean requireFlat4x4, double requestedStartY) {
        if (landing == null || landing.getWorld() == null || contents == null || contents.size() < 4 || contents.size() > 9) return null;
        World world = landing.getWorld();
        Material crateMaterial = material("TRAPPED_CHEST");
        Material greenCarpet = material(settings.getString("drop.canopy-green-material", "GREEN_CARPET"));
        Material whiteCarpet = material(settings.getString("drop.canopy-white-material", "WHITE_CARPET"));
        Material ropeMaterial = material("CHAIN");
        if (crateMaterial == null || greenCarpet == null || whiteCarpet == null || ropeMaterial == null) return null;
        if (!Double.isFinite(requestedStartY)) return null;
        double minimumStartY = world.getMinHeight() + 1.0D;
        double maximumStartY = world.getMaxHeight() - 4.0D;
        if (maximumStartY < minimumStartY) {
            plugin.getLogger().warning("Could not start SupplyDrop in " + world.getName() + ": world has no valid altitude for the drop assembly.");
            return null;
        }
        double startY = Math.max(minimumStartY, Math.min(requestedStartY, maximumStartY));
        if (Double.compare(startY, requestedStartY) != 0) {
            plugin.getLogger().warning("SupplyDrop starting height " + requestedStartY + " is outside " + world.getName()
                    + "'s valid range; using Y=" + startY + ".");
        }
        if (startY <= landing.getBlockY()) {
            plugin.getLogger().warning("Could not start SupplyDrop in " + world.getName()
                    + ": no valid altitude above the selected landing location.");
            return null;
        }
        Location spawnLocation = landing.clone();
        spawnLocation.setY(startY);
        ActiveDrop drop = new ActiveDrop(UUID.randomUUID(), rarity, landing.clone(), contents, automatic, requireFlat4x4);
        try {
            drop.visualCenter = spawnLocation.clone();
            drop.crateDisplay = spawnCrateDisplay(world, spawnLocation, crateMaterial);
            for (int i = 0; i < CANOPY_X.length; i++) {
                Material canopyMaterial = i == 0 || i == 3 ? greenCarpet : whiteCarpet;
                BlockDisplay display = world.spawn(spawnLocation.clone().add(CANOPY_X[i], 2.5D, CANOPY_Z[i]),
                        BlockDisplay.class, entity -> entity.setBlock(canopyMaterial.createBlockData()));
                display.setInvulnerable(true);
                display.setPersistent(false);
                drop.canopy.add(display);
            }
            createRopes(drop, world, spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(), ropeMaterial);
            drop.chunks.add(world.getChunkAt(landing));
            for (BlockDisplay display : drop.canopy) drop.chunks.add(display.getLocation().getChunk());
            for (BlockDisplay rope : drop.ropes) drop.chunks.add(rope.getLocation().getChunk());
            for (org.bukkit.Chunk chunk : drop.chunks) {
                chunk.addPluginChunkTicket(plugin);
                drop.ticketed = true;
            }
            activeDrops.put(drop.id, drop);
            return drop;
        } catch (RuntimeException exception) {
            removeDrop(drop, false);
            plugin.getLogger().warning("Could not start SupplyDrop: " + exception.getMessage());
            return null;
        }
    }

    private ItemDisplay spawnCrateDisplay(World world, Location location, Material crateMaterial) {
        return world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(crateMaterial));
            entity.setInvulnerable(true);
            entity.setPersistent(false);
        });
    }

    private Material material(String name) {
        return XMaterial.matchXMaterial(name).map(XMaterial::parseMaterial).orElse(null);
    }

    private void tickDrop(ActiveDrop drop) {
        if (!activeDrops.containsKey(drop.id) || drop.state != DropState.DESCENDING) return;
        Location current = drop.crateDisplay != null && drop.crateDisplay.isValid()
                ? drop.crateDisplay.getLocation() : drop.visualCenter.clone();
        if (!current.getWorld().isChunkLoaded(current.getBlockX() >> 4, current.getBlockZ() >> 4)) return;
        if (drop.crateDisplay == null || !drop.crateDisplay.isValid()) {
            Material crateMaterial = material("TRAPPED_CHEST");
            if (crateMaterial == null) {
                removeDrop(drop, false);
                return;
            }
            drop.crateDisplay = spawnCrateDisplay(current.getWorld(), current, crateMaterial);
        }
        double gravity = settings.getDouble("drop.gravity", 0.2D);
        double terminalSpeed = settings.getDouble("drop.descent-speed", 0.12D);
        drop.verticalVelocity = Math.min(terminalSpeed, drop.verticalVelocity + gravity * 0.04D);
        double nextY = Math.max(drop.landing.getY(), current.getY() - drop.verticalVelocity);
        if (nextY <= drop.landing.getY() + 0.1D) {
            if (!landingStillSafe(drop)) {
                removeDrop(drop, false);
                return;
            }
            land(drop);
            return;
        }
        for (int i = 0; i < drop.canopy.size(); i++) {
            Entity display = drop.canopy.get(i);
            if (display.isValid()) display.teleport(new Location(current.getWorld(), current.getX() + CANOPY_X[i], nextY + 2.5D, current.getZ() + CANOPY_Z[i]));
        }
        drop.visualCenter = new Location(current.getWorld(), current.getX(), nextY, current.getZ());
        drop.crateDisplay.teleport(drop.visualCenter);
        updateRopes(drop, current.getWorld(), current.getX(), nextY, current.getZ());
    }

    private void createRopes(ActiveDrop drop, World world, double centerX, double crateY, double centerZ, Material ropeMaterial) {
        for (int corner = 0; corner < ROPE_X.length; corner++) {
            BlockDisplay rope = world.spawn(new Location(world, centerX, crateY, centerZ), BlockDisplay.class, entity -> {
                entity.setBlock(ropeMaterial.createBlockData());
                entity.setInvulnerable(true);
                entity.setPersistent(false);
            });
            drop.ropes.add(rope);
        }
        updateRopes(drop, world, centerX, crateY, centerZ);
    }

    private void updateRopes(ActiveDrop drop, World world, double centerX, double crateY, double centerZ) {
        for (int corner = 0; corner < drop.ropes.size(); corner++) {
            BlockDisplay rope = drop.ropes.get(corner);
            if (!rope.isValid()) continue;
            Vector3f start = new Vector3f((float) (centerX + ROPE_X[corner]), (float) (crateY + 2.5D),
                    (float) (centerZ + ROPE_Z[corner]));
            Vector3f end = new Vector3f((float) (centerX + ROPE_X[corner] * 0.38D), (float) (crateY + 1.0D),
                    (float) (centerZ + ROPE_Z[corner] * 0.38D));
            Vector3f direction = new Vector3f(end).sub(start);
            float length = direction.length();
            direction.normalize();
            Vector3f midpoint = new Vector3f(start).add(end).mul(0.5F);
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction);
            Vector3f scale = new Vector3f(0.08F, length, 0.08F);
            Vector3f translation = new Vector3f(0.5F, 0.5F, 0.5F).mul(scale);
            rotation.transform(translation).negate();
            rope.teleport(new Location(world, midpoint.x, midpoint.y, midpoint.z));
            rope.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
        }
    }

    private void tickDrops() {
        for (ActiveDrop drop : new ArrayList<>(activeDrops.values())) {
            tickDrop(drop);
            if (drop.state == DropState.LANDED) tickLandingParticles(drop);
        }
    }

    private void tickLandingParticles(ActiveDrop drop) {
        if (drop.particleTicksRemaining <= 0) return;
        if (drop.particleTicksUntilNext > 0) {
            drop.particleTicksUntilNext--;
        } else {
            spawnLandingParticles(drop);
            drop.particleTicksUntilNext = drop.particleIntervalTicks - 1;
        }
        drop.particleTicksRemaining--;
    }

    private void spawnLandingParticles(ActiveDrop drop) {
        World world = drop.landing.getWorld();
        if (world == null) return;
        Block chest = drop.landing.getBlock();
        Location center = new Location(world, chest.getX() + 0.5D, chest.getY() + 1.15D, chest.getZ() + 0.5D);
        if (drop.redstoneParticles) {
            XParticle.of("DUST").ifPresent(particle -> world.spawnParticle(particle.get(), center, 5,
                    0.18D, 0.08D, 0.18D, 0.0D, new Particle.DustOptions(Color.RED, 1.0F)));
        }
        if (drop.flameParticles) {
            XParticle.of("FLAME").ifPresent(particle -> world.spawnParticle(particle.get(), center, 2,
                    0.16D, 0.08D, 0.16D, 0.005D));
        }
    }

    private boolean landingStillSafe(ActiveDrop drop) {
        World world = drop.landing.getWorld();
        int x = drop.landing.getBlockX();
        int z = drop.landing.getBlockZ();
        Location checked = safeLanding(world, x, z, drop.requireFlat4x4, null, drop);
        return checked != null && checked.getBlockY() == drop.landing.getBlockY();
    }

    private void land(ActiveDrop drop) {
        Block chestBlock = drop.landing.getBlock();
        Material chest = material("CHEST");
        if (chest == null || !chestBlock.getType().isAir()) {
            removeDrop(drop, false);
            return;
        }
        if (drop.crateDisplay != null) drop.crateDisplay.remove();
        drop.crateDisplay = null;
        for (BlockDisplay display : drop.canopy) display.remove();
        drop.canopy.clear();
        for (BlockDisplay rope : drop.ropes) rope.remove();
        drop.ropes.clear();
        chestBlock.setType(chest, false);
        drop.state = DropState.LANDED;
        DropInventoryHolder holder = new DropInventoryHolder(drop.id);
        String title = Items.color("&2&lSupply Airdrop " + rarityDisplay(drop.rarity));
        drop.inventory = Bukkit.createInventory(holder, 9, title);
        holder.inventory = drop.inventory;
        for (int slot = 0; slot < drop.contents.size(); slot++) drop.inventory.setItem(slot, drop.contents.get(slot).clone());
        if (settings.getBoolean("landing.particles.enabled", true)) {
            drop.particleTicksRemaining = settings.getInt("landing.particles.duration-seconds", 600) * 20;
            drop.particleIntervalTicks = settings.getInt("landing.particles.interval-ticks", 10);
            drop.particleTicksUntilNext = 0;
            drop.redstoneParticles = settings.getBoolean("landing.particles.redstone", true);
            drop.flameParticles = settings.getBoolean("landing.particles.flame", true);
        }
        createHologram(drop);
        announceLanding(drop);
    }

    private void createHologram(ActiveDrop drop) {
        for (ArmorStand hologram : drop.holograms) hologram.remove();
        drop.holograms.clear();
        List<String> lines = settings.getStringList("hologram.lines");
        if (lines.isEmpty()) lines = List.of("&2&lSUPPLY AIRDROP", "&7Rarity: %level%");
        Block chest = drop.landing.getBlock();
        double verticalOffset = settings.getDouble("landing.hologram-offset", 1.5D);
        for (int i = 0; i < lines.size(); i++) {
            String text = Items.color(lines.get(i).replace("%level%", rarityDisplay(drop.rarity)));
            Location location = new Location(chest.getWorld(), chest.getX() + 0.5D,
                    chest.getY() + verticalOffset + ((lines.size() - i - 1) * 0.25D), chest.getZ() + 0.5D);
            ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
            stand.setVisible(false);
            stand.setInvulnerable(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(text);
            stand.setPersistent(false);
            drop.holograms.add(stand);
        }
    }

    private void announceLanding(ActiveDrop drop) {
        String message = settings.getString("announcements.landing", "&6SupplyDrop &e%rarity% &6landed at &f%world% %x%, %y%, %z%&6.");
        message = message.replace("%rarity%", rarityDisplay(drop.rarity))
                .replace("%level%", rarityDisplay(drop.rarity))
                .replace("%world%", drop.landing.getWorld().getName())
                .replace("%x%", Integer.toString(drop.landing.getBlockX()))
                .replace("%y%", Integer.toString(drop.landing.getBlockY()))
                .replace("%z%", Integer.toString(drop.landing.getBlockZ()));
        Bukkit.broadcastMessage(Items.color(message));
    }

    private void openDrop(Player player, ActiveDrop drop) {
        if (drop.state != DropState.LANDED || drop.inventory == null) return;
        if (!drop.interacted) {
            drop.interacted = true;
            long seconds = Math.max(1, Math.min(2_592_000L, settings.getLong("drop.expiration-seconds", 900)));
            drop.expirationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> removeDrop(drop, true), seconds * 20L);
        }
        player.openInventory(drop.inventory);
    }

    private void removeDrop(ActiveDrop drop, boolean removeChest) {
        if (drop == null) return;
        if (!activeDrops.containsKey(drop.id)) {
            cleanEntities(drop);
            removeChunkTickets(drop);
            return;
        }
        activeDrops.remove(drop.id);
        if (drop.expirationTask != null) drop.expirationTask.cancel();
        drop.particleTicksRemaining = 0;
        if (drop.inventory != null) {
            for (HumanViewer viewer : viewers(drop.inventory)) {
                if (viewer.player.isOnline()) viewer.player.closeInventory();
            }
        }
        if (removeChest && drop.state == DropState.LANDED && drop.landing.getWorld() != null) {
            Block block = drop.landing.getBlock();
            if (block.getType().name().equals("CHEST")) {
                Material air = material("AIR");
                if (air != null) block.setType(air, false);
            }
        }
        cleanEntities(drop);
        removeChunkTickets(drop);
        drop.state = DropState.REMOVED;
    }

    private void removeChunkTickets(ActiveDrop drop) {
        if (!drop.ticketed) return;
        for (org.bukkit.Chunk chunk : drop.chunks) chunk.removePluginChunkTicket(plugin);
        drop.chunks.clear();
        drop.ticketed = false;
    }

    private List<HumanViewer> viewers(Inventory inventory) {
        List<HumanViewer> players = new ArrayList<>();
        for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player player) players.add(new HumanViewer(player));
        }
        return players;
    }

    private void cleanEntities(ActiveDrop drop) {
        if (drop.crateDisplay != null) drop.crateDisplay.remove();
        for (BlockDisplay display : drop.canopy) display.remove();
        for (BlockDisplay rope : drop.ropes) rope.remove();
        for (ArmorStand hologram : drop.holograms) hologram.remove();
        drop.canopy.clear();
        drop.ropes.clear();
        drop.holograms.clear();
    }

    private void scheduleAutomaticDrops() {
        if (automaticTask != null) automaticTask.cancel();
        automaticTask = null;
        if (!settings.getBoolean("automatic.enabled", true)) return;
        long seconds = Math.max(60, Math.min(31_536_000L, settings.getLong("automatic.interval-seconds", 7200)));
        long ticks = seconds * 20L;
        automaticTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnAutomaticDrop, ticks, ticks);
    }

    private void spawnAutomaticDrop() {
        TaxiAPI taxi = PacificaAPI.taxi();
        if (!taxi.isAvailable()) {
            plugin.getLogger().warning("Skipped automatic SupplyDrop: Taxi warp API is unavailable.");
            return;
        }
        List<String> taxiNames = List.copyOf(taxi.getWarpNames());
        List<String> eligible = new ArrayList<>();
        Map<String, Location> locations = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String configuredName : settings.getStringList("automatic.warps")) {
            String warpName = taxiNames.stream().filter(name -> name.equalsIgnoreCase(configuredName)).findFirst().orElse(null);
            if (warpName == null) {
                plugin.getLogger().warning("SupplyDrops references missing Taxi warp '" + configuredName
                        + "'; remove it with /adminpii supplydrop warp remove " + configuredName + ".");
                continue;
            }
            String normalized = warpName.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) continue;
            Location location = taxi.getWarpLocation(warpName).orElse(null);
            if (location == null || location.getWorld() == null) {
                plugin.getLogger().warning("Skipped unavailable Taxi warp '" + warpName + "' in the automatic SupplyDrop list.");
                continue;
            }
            World world = Bukkit.getWorld(location.getWorld().getUID());
            if (world == null) {
                plugin.getLogger().warning("Skipped Taxi warp '" + warpName + "' because its world is unavailable.");
                continue;
            }
            location.setWorld(world);
            eligible.add(warpName);
            locations.put(warpName, location);
        }
        if (eligible.isEmpty()) {
            plugin.getLogger().warning("Skipped automatic SupplyDrop: no eligible configured Taxi warps are available.");
            return;
        }
        String warpName = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        Location center = locations.get(warpName);
        World world = center.getWorld();
        int radius = Math.max(1, Math.min(10_000, settings.getInt("automatic.radius-blocks", 50)));
        int retries = Math.max(1, Math.min(200, settings.getInt("automatic.location-validation.max-attempts", 30)));
        Location landing = null;
        String candidateKey = null;
        for (int attempt = 0; attempt < retries; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
            double targetX = x + 0.5D;
            double targetZ = z + 0.5D;
            double offsetX = targetX - center.getX();
            double offsetZ = targetZ - center.getZ();
            if (offsetX * offsetX + offsetZ * offsetZ > (double) radius * radius) continue;
            String key = world.getUID() + ":" + x + ":" + z;
            if (recentAutomaticLocations.contains(key) && attempt + 1 < retries) continue;
            Location candidate = safeLanding(world, x, z, false, null);
            if (candidate != null) {
                landing = candidate;
                candidateKey = key;
                break;
            }
        }
        if (landing == null) {
            plugin.getLogger().warning("Skipped automatic SupplyDrop: no safe landing location near warp " + warpName + ".");
            return;
        }
        SupplyDropRarity rarity = selectAutomaticRarity();
        if (rarity == null) {
            plugin.getLogger().warning("Skipped automatic SupplyDrop: all configured rarity weights are disabled.");
            return;
        }
        List<ItemStack> contents = generateRewards(rarity);
        if (contents == null) {
            plugin.getLogger().warning("Skipped automatic SupplyDrop: reward pool is insufficient for " + rarity.key() + ".");
            return;
        }
        ActiveDrop drop = spawnDrop(landing, rarity, contents, true, false);
        if (drop != null) {
            recentAutomaticLocations.add(candidateKey);
            while (recentAutomaticLocations.size() > 100) recentAutomaticLocations.remove(recentAutomaticLocations.iterator().next());
        }
    }

    private boolean setRewardWeight(UUID id, long weight) {
        RewardEntry entry = rewardEntries.get(id);
        if (entry == null || weight <= 0) return false;
        entry.weight = weight;
        persistReward(entry);
        return true;
    }

    private boolean setRewardChance(UUID id, double chance) {
        RewardEntry entry = rewardEntries.get(id);
        if (entry == null || !Double.isFinite(chance) || chance < 0.0D || chance > 100.0D) return false;
        entry.chance = chance;
        persistReward(entry);
        return true;
    }

    private void openSettings(Player player, SupplyDropRarity rarity, UUID rewardId) {
        if (rewardEntries.containsKey(rewardId)) player.openInventory(new SupplyDropSettingsMenu(this, rarity, rewardId).getInventory());
        else openEditor(player, rarity);
    }

    private void openNumericInput(Player player, SupplyDropRarity rarity, UUID rewardId, boolean weight) {
        RewardEntry entry = rewardEntries.get(rewardId);
        if (entry == null) return;
        String title = weight ? "&7Set Reward Weight" : "&7Set Chance (0-100%)";
        String initial = weight ? Long.toString(entry.weight) : Double.toString(entry.chance);
        new AnvilGUI.Builder().plugin(plugin).title(Items.color(title)).text(initial)
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return List.of();
                    String text = snapshot.getText();
                    boolean changed;
                    if (weight) {
                        try { changed = text != null && text.matches("[0-9]+") && setRewardWeight(rewardId, Long.parseLong(text)); }
                        catch (NumberFormatException exception) { changed = false; }
                    } else {
                        try {
                            double chance = Double.parseDouble(text);
                            changed = text != null && text.matches("(?:[0-9]+(?:\\.[0-9]{1,2})?|\\.[0-9]{1,2})")
                                    && setRewardChance(rewardId, chance);
                        } catch (NumberFormatException exception) { changed = false; }
                    }
                    if (!changed) {
                        snapshot.getPlayer().sendMessage(Items.color(weight
                                ? "&cWeight must be a positive whole number." : "&cChance must be between 0 and 100."));
                        return List.of(AnvilGUI.ResponseAction.replaceInputText(""));
                    }
                    return List.of(AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> openSettings(snapshot.getPlayer(), rarity, rewardId)));
                }).open(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        ActiveDrop drop = findDrop(event.getClickedBlock().getLocation());
        if (drop == null || drop.state != DropState.LANDED) return;
        event.setCancelled(true);
        openDrop(event.getPlayer(), drop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof DropInventoryHolder holder)) return;
        ClickType click = event.getClick();
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT || click == ClickType.DOUBLE_CLICK
                    || click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        ActiveDrop drop = activeDrops.get(holder.dropId);
        if (drop == null || click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= drop.inventory.getSize()) return;
        ItemStack offered = drop.inventory.getItem(slot);
        if (offered == null || offered.getType().isAir()) return;
        ItemStack transfer = offered.clone();
        int originalAmount = transfer.getAmount();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(transfer);
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = originalAmount - remaining;
        if (moved <= 0) {
            player.sendMessage(Items.color("&cYour inventory is full."));
            return;
        }
        int stillAvailable = offered.getAmount() - moved;
        if (stillAvailable <= 0) drop.inventory.setItem(slot, null);
        else {
            ItemStack remainder = offered.clone();
            remainder.setAmount(stillAvailable);
            drop.inventory.setItem(slot, remainder);
        }
        if (isEmpty(drop.inventory)) removeDrop(drop, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof DropInventoryHolder) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof SupplyDropEditorMenu
                || event.getView().getTopInventory().getHolder() instanceof SupplyDropSettingsMenu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SupplyDropSettingsMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            if (event.getRawSlot() == 18) openEditor(player, menu.rarity());
            else if (event.getRawSlot() == 12) openNumericInput(player, menu.rarity(), menu.rewardId(), true);
            else if (event.getRawSlot() == 14) openNumericInput(player, menu.rarity(), menu.rewardId(), false);
            return;
        }
        if (!(top.getHolder() instanceof SupplyDropEditorMenu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            if (!event.isShiftClick() || !event.isLeftClick() || event.getCurrentItem() == null
                    || event.getCurrentItem().getType().isAir()) return;
            ItemStack clicked = event.getCurrentItem();
            if (!addReward(menu.rarity(), clicked)) {
                player.sendMessage(Items.color("&cNo reward slots are available for this rarity."));
                return;
            }
            if (clicked.getAmount() <= 1) event.getClickedInventory().setItem(event.getSlot(), null);
            else {
                ItemStack remaining = clicked.clone();
                remaining.setAmount(clicked.getAmount() - 1);
                event.getClickedInventory().setItem(event.getSlot(), remaining);
            }
            openEditor(player, menu.rarity());
            return;
        }
        int slot = event.getRawSlot();
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            UUID id = rewardIdAt(menu.rarity(), slot);
            if (id != null) {
                removeReward(id);
                openEditor(player, menu.rarity());
            }
            return;
        }
        UUID id = rewardIdAt(menu.rarity(), slot);
        if (id != null) openSettings(player, menu.rarity(), id);
    }

    private UUID rewardIdAt(SupplyDropRarity rarity, int slot) {
        int[] usable = java.util.stream.IntStream.range(0, 54)
                .filter(value -> Arrays.stream(EDITOR_BORDER).noneMatch(border -> border == value)).toArray();
        int index = -1;
        for (int i = 0; i < usable.length; i++) if (usable[i] == slot) index = i;
        List<RewardEntry> entries = rewards(rarity);
        return index >= 0 && index < entries.size() ? entries.get(index).id : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestBreak(BlockBreakEvent event) {
        ActiveDrop drop = findDrop(event.getBlock().getLocation());
        if (drop != null && drop.state == DropState.LANDED) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplosion(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(block -> findDrop(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> findDrop(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVisualDamage(EntityDamageEvent event) {
        for (ActiveDrop drop : activeDrops.values()) {
            if (event.getEntity().equals(drop.crateDisplay) || drop.canopy.contains(event.getEntity()) || drop.holograms.contains(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private ActiveDrop findDrop(Location location) {
        for (ActiveDrop drop : activeDrops.values()) {
            if (drop.state == DropState.LANDED && drop.landing.getWorld().equals(location.getWorld())
                    && drop.landing.getBlockX() == location.getBlockX() && drop.landing.getBlockY() == location.getBlockY()
                    && drop.landing.getBlockZ() == location.getBlockZ()) return drop;
        }
        return null;
    }

    private boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) if (item != null && !item.getType().isAir()) return false;
        return true;
    }

    public boolean handleAdminCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length == 0) return adminUsage(sender);
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("edit") && args.length == 2) {
            SupplyDropRarity rarity = SupplyDropRarity.parse(args[1]).orElse(null);
            if (rarity == null) return adminUsage(sender);
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Items.color("&cThe reward editor can only be opened in-game."));
                return true;
            }
            openEditor(player, rarity);
            return true;
        }
        if (action.equals("reload") && args.length == 1) {
            reload();
            sender.sendMessage(Items.color("&aSupplyDrops configuration reloaded."));
            return true;
        }
        if (action.equals("warp")) return handleWarpAdminCommand(sender, args);
        if (action.equals("chance") && args.length == 1) {
            BigInteger totalWeight = BigInteger.ZERO;
            for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
                totalWeight = totalWeight.add(BigInteger.valueOf(Math.max(0L,
                        settings.getLong(rarityWeightPath(rarity), defaultRarityWeight(rarity)))));
            }
            sender.sendMessage(Items.color("&eAutomatic SupplyDrop rarity weights and selection chances:"));
            for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
                long weight = Math.max(0L, settings.getLong(rarityWeightPath(rarity), defaultRarityWeight(rarity)));
                String chance = totalWeight.signum() == 0 ? "0.00"
                        : String.format(Locale.ROOT, "%.2f", BigInteger.valueOf(weight).doubleValue() * 100.0D / totalWeight.doubleValue());
                sender.sendMessage(Items.color("&7" + rarity.key() + ": &fweight " + weight + " &7(&f" + chance + "%&7)"));
            }
            return true;
        }
        if (action.equals("active") && args.length == 1) {
            sender.sendMessage(Items.color("&eActive SupplyDrops: &f" + activeDrops.size()));
            for (ActiveDrop drop : activeDrops.values()) {
                sender.sendMessage(Items.color("&7" + drop.id + " &f" + rarityDisplay(drop.rarity) + " &7"
                        + drop.state.name().toLowerCase(Locale.ROOT) + " at " + drop.landing.getWorld().getName() + " "
                        + drop.landing.getBlockX() + ", " + drop.landing.getBlockY() + ", " + drop.landing.getBlockZ()));
            }
            return true;
        }
        if (action.equals("active") && args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            try {
                ActiveDrop drop = activeDrops.get(UUID.fromString(args[2]));
                if (drop == null) sender.sendMessage(Items.color("&cNo active SupplyDrop has that ID."));
                else {
                    removeDrop(drop, true);
                    sender.sendMessage(Items.color("&aSupplyDrop removed."));
                }
            } catch (IllegalArgumentException exception) { sender.sendMessage(Items.color("&cInvalid SupplyDrop ID.")); }
            return true;
        }
        if (List.of("grant", "remove", "set", "inspect").contains(action) && args.length >= 2) {
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(Items.color("&cCould not resolve that player's UUID."));
                return true;
            }
            if (action.equals("inspect") && args.length == 2) {
                for (SupplyDropRarity rarity : SupplyDropRarity.values()) {
                    sender.sendMessage(Items.color("&7" + (target.getName() == null ? target.getUniqueId() : target.getName())
                            + " &f" + rarityDisplay(rarity) + "&7: &a" + getAmount(target.getUniqueId(), rarity)));
                }
                return true;
            }
            if (args.length != 4) return adminUsage(sender);
            SupplyDropRarity rarity = SupplyDropRarity.parse(args[2]).orElse(null);
            int amount;
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException exception) { amount = -1; }
            if (rarity == null || amount < 0 || action.equals("grant") && amount == 0 || action.equals("remove") && amount == 0) {
                sender.sendMessage(Items.color("&cProvide a rarity and a valid non-negative amount."));
                return true;
            }
            boolean changed = switch (action) {
                case "grant" -> grantAmount(target.getUniqueId(), rarity, amount);
                case "remove" -> removeAmount(target.getUniqueId(), rarity, amount);
                case "set" -> setAmount(target.getUniqueId(), rarity, amount);
                default -> false;
            };
            sender.sendMessage(Items.color(changed ? "&aSupplyDrop balance updated." : "&cCould not update that balance."));
            return true;
        }
        return adminUsage(sender);
    }

    private boolean handleWarpAdminCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            List<String> eligible = automaticWarpNames();
            if (eligible.isEmpty()) {
                sender.sendMessage(Items.color("&eNo Taxi warps are enabled for automatic SupplyDrops."));
                return true;
            }
            sender.sendMessage(Items.color("&eAutomatic SupplyDrop warps: &f" + String.join(", ", eligible)));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
            TaxiAPI taxi = PacificaAPI.taxi();
            if (!taxi.isAvailable()) {
                sender.sendMessage(Items.color("&cTaxi warp API is unavailable."));
                return true;
            }
            String warpName = taxi.getWarpNames().stream()
                    .filter(name -> name.equalsIgnoreCase(args[2])).findFirst().orElse(null);
            if (warpName == null) {
                sender.sendMessage(Items.color("&cUnknown Taxi warp: &f" + args[2]));
                return true;
            }
            Location location = taxi.getWarpLocation(warpName).orElse(null);
            if (location == null || location.getWorld() == null
                    || Bukkit.getWorld(location.getWorld().getUID()) == null) {
                sender.sendMessage(Items.color("&cTaxi warp has no available world: &f" + warpName));
                return true;
            }
            List<String> eligible = new ArrayList<>(settings.getStringList("automatic.warps"));
            if (eligible.stream().anyMatch(name -> name.equalsIgnoreCase(warpName))) {
                sender.sendMessage(Items.color("&cThat Taxi warp is already enabled for automatic SupplyDrops."));
                return true;
            }
            eligible.add(warpName);
            settings.set("automatic.warps", eligible);
            saveSettings();
            sender.sendMessage(Items.color("&aAdded Taxi warp &f" + warpName + " &ato automatic SupplyDrops."));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            List<String> eligible = new ArrayList<>(settings.getStringList("automatic.warps"));
            boolean removed = eligible.removeIf(name -> name.equalsIgnoreCase(args[2]));
            if (!removed) {
                sender.sendMessage(Items.color("&cThat warp is not enabled for automatic SupplyDrops."));
                return true;
            }
            settings.set("automatic.warps", eligible);
            saveSettings();
            sender.sendMessage(Items.color("&aRemoved &f" + args[2] + " &afrom automatic SupplyDrops. The Taxi warp was not changed."));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("info")) {
            boolean eligible = settings.getStringList("automatic.warps").stream()
                    .anyMatch(name -> name.equalsIgnoreCase(args[2]));
            TaxiAPI taxi = PacificaAPI.taxi();
            String warpName = taxi.isAvailable() ? taxi.getWarpNames().stream()
                    .filter(name -> name.equalsIgnoreCase(args[2])).findFirst().orElse(null) : null;
            if (warpName == null) {
                sender.sendMessage(Items.color(eligible
                        ? "&eWarp &f" + args[2] + " &eis an eligible but stale Taxi warp reference."
                        : "&cUnknown Taxi warp: &f" + args[2]));
                return true;
            }
            Location location = taxi.getWarpLocation(warpName).orElse(null);
            if (location == null || location.getWorld() == null) {
                sender.sendMessage(Items.color("&eWarp &f" + warpName + " &7eligible: &f" + eligible + "&7; location unavailable."));
                return true;
            }
            sender.sendMessage(Items.color("&eWarp &f" + warpName + " &7eligible: &f" + eligible + " &7location: &f"
                    + location.getWorld().getName() + " " + location.getX() + ", " + location.getY() + ", " + location.getZ()));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
            if (settings.getStringList("automatic.warps").isEmpty()) {
                sender.sendMessage(Items.color("&eThe automatic SupplyDrop warp list is already empty."));
                return true;
            }
            settings.set("automatic.warps", List.of());
            saveSettings();
            sender.sendMessage(Items.color("&aCleared the automatic SupplyDrop warp list. Taxi warps were not changed."));
            return true;
        }
        sender.sendMessage(Items.color("&e/adminpii supplydrop warp <list|add <warp>|remove <warp>|info <warp>|clear>"));
        return true;
    }

    private boolean adminUsage(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(Items.color("&e/adminpii supplydrop <edit <rarity>|warp <list|add|remove|info|clear>|chance|grant <player> <rarity> <amount>|remove <player> <rarity> <amount>|set <player> <rarity> <amount>|inspect <player>|reload|active [remove <id>]>&r"));
        return true;
    }

    public List<UUID> activeDropIds() { return List.copyOf(activeDrops.keySet()); }

    public static final class RewardEntry {
        private final UUID id;
        private final SupplyDropRarity rarity;
        private final ItemStack item;
        private long weight;
        private double chance;
        private final int minimumQuantity;
        private final int maximumQuantity;

        private RewardEntry(UUID id, SupplyDropRarity rarity, ItemStack item, long weight, double chance, int minimumQuantity, int maximumQuantity) {
            this.id = id;
            this.rarity = rarity;
            this.item = item.clone();
            this.weight = weight;
            this.chance = chance;
            this.minimumQuantity = minimumQuantity;
            this.maximumQuantity = maximumQuantity;
        }

        public UUID id() { return id; }
        public SupplyDropRarity rarity() { return rarity; }
        public ItemStack item() { return item.clone(); }
        public long weight() { return weight; }
        public double chance() { return chance; }
        public int minimumQuantity() { return minimumQuantity; }
        public int maximumQuantity() { return maximumQuantity; }
        public String displayName() {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
            String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            StringBuilder readable = new StringBuilder();
            for (String word : name.split(" ")) {
                if (readable.length() > 0) readable.append(' ');
                readable.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
            return readable.toString();
        }
    }

    private enum DropState { DESCENDING, LANDED, REMOVED }

    private static final class ActiveDrop {
        private final UUID id;
        private final SupplyDropRarity rarity;
        private final Location landing;
        private final List<ItemStack> contents;
        private final boolean automatic;
        private final boolean requireFlat4x4;
        private final List<BlockDisplay> canopy = new ArrayList<>();
        private final List<BlockDisplay> ropes = new ArrayList<>();
        private final List<ArmorStand> holograms = new ArrayList<>();
        private DropState state = DropState.DESCENDING;
        private ItemDisplay crateDisplay;
        private Location visualCenter;
        private Inventory inventory;
        private final Set<org.bukkit.Chunk> chunks = new HashSet<>();
        private boolean ticketed;
        private boolean interacted;
        private double verticalVelocity;
        private int particleTicksRemaining;
        private int particleIntervalTicks;
        private int particleTicksUntilNext;
        private boolean redstoneParticles;
        private boolean flameParticles;
        private BukkitTask expirationTask;

        private ActiveDrop(UUID id, SupplyDropRarity rarity, Location landing, List<ItemStack> contents,
                           boolean automatic, boolean requireFlat4x4) {
            this.id = id;
            this.rarity = rarity;
            this.landing = landing;
            this.contents = contents.stream().map(ItemStack::clone).toList();
            this.automatic = automatic;
            this.requireFlat4x4 = requireFlat4x4;
        }
    }

    private static final class DropInventoryHolder implements InventoryHolder {
        private final UUID dropId;
        private Inventory inventory;
        private DropInventoryHolder(UUID dropId) { this.dropId = dropId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class HumanViewer {
        private final Player player;
        private HumanViewer(Player player) { this.player = player; }
    }
}