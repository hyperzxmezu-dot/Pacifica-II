package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public final class IdentityDisplayResolver {
    private static final String LEVEL_CHANGE_EVENT = "com.districtx.pacificacore.api.event.PlayerLevelChangeEvent";
    private static final String LUCKPERMS_RECALCULATE_EVENT = "net.luckperms.api.event.user.UserDataRecalculateEvent";
    private final PacificaPlugin plugin;
    private Listener levelListener;
    private Listener coreEnableListener;
    private Object luckPermsSubscription;
    private Class<?> luckPermsEventClass;
    private boolean apiWarningLogged;

    /** @param plugin Pacifica-II instance that owns this resolver */
    public IdentityDisplayResolver(PacificaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves the exclusive Tag-or-level display for a player.
     * An equipped Tag takes priority when Tag display is enabled; otherwise the current Pacifica-Core level is used.
     * Must be called on the server thread when Tag display is enabled. Returns an empty string for a null player,
     * unavailable Pacifica-Core, or an unavailable level service. Does not mutate player or persistent state.
     *
     * @param player player whose display is resolved; may be null
     * @param allowTag whether an equipped Tag may take priority
     * @return formatted Tag or XP level display, or an empty string when no safe fallback is available
     */
    public String display(Player player, boolean allowTag) {
        if (player == null) return "";
        UUID playerId = player.getUniqueId();
        if (allowTag && plugin.tags() != null && plugin.tags().enabled()
                && plugin.tags().getEquippedTag(playerId) != null) {
            return plugin.tags().display(playerId);
        }
        String level = formattedLevel(player);
        return level.isEmpty() ? "" : "&8[ " + level + "&8 ]&8&l";
    }

    private String formattedLevel(Player player) {
        if (plugin.placeholders() != null && plugin.placeholders().isAvailable()) {
            String formatted = plugin.placeholders().parseExternal(player, "%pacifica_level%");
            if (!formatted.isBlank() && !formatted.equalsIgnoreCase("%pacifica_level%")) return formatted;
        }
        return xpLevel(player);
    }

    /**
     * Reads the player's current level from Pacifica-Core's public PlayerLevelService.
     * The lookup uses Pacifica-Core's actual API and is safe when that soft dependency is absent.
     *
     * @param player player whose level is requested; may be null
     * @return the numeric level, or an empty string when unavailable
     */
    public String xpLevel(Player player) {
        if (player == null) return "";
        Integer level = lookupLevel(player.getUniqueId());
        return level == null ? "" : String.valueOf(level);
    }

    /** Reads the authoritative Pacifica-Core level for a UUID, or zero if Core is unavailable. */
    public int pacificaLevel(UUID playerId) {
        Integer level = lookupLevel(playerId);
        return level == null ? 0 : level;
    }

    private Integer lookupLevel(UUID playerId) {
        if (playerId == null) return null;
        Plugin core = Bukkit.getPluginManager().getPlugin("Pacifica-Core");
        if (core == null || !core.isEnabled()) return null;
        try {
            ClassLoader loader = core.getClass().getClassLoader();
            Class<?> coreClass = loader.loadClass("com.districtx.pacificacore.PacificaCore");
            Object api = coreClass.getMethod("getAPI").invoke(null);
            if (api == null) return unavailableLevel("PacificaCore.getAPI() returned no API instance");
            Class<?> apiType = loader.loadClass("com.districtx.pacificacore.api.PacificaCoreAPI");
            Object levelService = apiType.getMethod("getPlayerLevelService").invoke(api);
            if (levelService == null) return unavailableLevel("PacificaCoreAPI returned no PlayerLevelService");
            Class<?> serviceType = loader.loadClass("com.districtx.pacificacore.api.PlayerLevelService");
            Object level = serviceType.getMethod("getLevel", UUID.class)
                    .invoke(levelService, playerId);
            if (!(level instanceof Number number)) {
                return unavailableLevel("PlayerLevelService.getLevel(UUID) returned an incompatible value");
            }
            apiWarningLogged = false;
            return number.intValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return unavailableLevel(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private Integer unavailableLevel(String reason) {
        if (!apiWarningLogged) {
            plugin.getLogger().warning("Pacifica-Core is enabled, but its public player-level API is unavailable ("
                    + reason + "). Level displays will be omitted and level-gated jobs will fail closed.");
            apiWarningLogged = true;
        }
        return null;
    }

    /**
     * Registers a reflective listener for Pacifica-Core level-up events when Core is available.
     * Safe to call repeatedly; registration has no persistent data side effects.
     */
    public void start() {
        if (levelListener == null) registerLevelListener();
        if (coreEnableListener == null) registerCoreEnableListener();
        if (luckPermsSubscription == null) registerLuckPermsListener();
    }

    /** Unregisters the optional level-up listener without changing player data. */
    public void stop() {
        if (levelListener != null) HandlerList.unregisterAll(levelListener);
        levelListener = null;
        if (coreEnableListener != null) HandlerList.unregisterAll(coreEnableListener);
        coreEnableListener = null;
        if (luckPermsSubscription instanceof AutoCloseable subscription) {
            try { subscription.close(); }
            catch (Exception ignored) { }
        }
        luckPermsSubscription = null;
        luckPermsEventClass = null;
    }

    private void registerLevelListener() {
        Plugin core = Bukkit.getPluginManager().getPlugin("Pacifica-Core");
        if (core == null || !core.isEnabled()) return;
        try {
            Class<?> eventClass = core.getClass().getClassLoader().loadClass(LEVEL_CHANGE_EVENT);
            Class<? extends Event> eventType = eventClass.asSubclass(Event.class);
            Listener listener = new Listener() { };
            Bukkit.getPluginManager().registerEvent(eventType, listener, EventPriority.MONITOR,
                    (registeredListener, event) -> refreshLevel(eventClass, event), plugin, true);
            levelListener = listener;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            levelListener = null;
        }
    }

    private void registerCoreEnableListener() {
        Listener listener = new Listener() { };
        Bukkit.getPluginManager().registerEvent(PluginEnableEvent.class, listener, EventPriority.MONITOR,
                (registeredListener, event) -> {
                    if (!((PluginEnableEvent) event).getPlugin().getName().equalsIgnoreCase("Pacifica-Core")) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (levelListener == null) registerLevelListener();
                        if (plugin.tags() != null) plugin.tags().refreshAll();
                    });
                }, plugin, true);
        coreEnableListener = listener;
    }

    private void registerLuckPermsListener() {
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) return;
        try {
            ClassLoader loader = luckPerms.getClass().getClassLoader();
            Class<?> providerClass = loader.loadClass("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object eventBus = loader.loadClass("net.luckperms.api.LuckPerms").getMethod("getEventBus").invoke(api);
            Class<?> eventBusClass = loader.loadClass("net.luckperms.api.event.EventBus");
            Class<?> eventClass = loader.loadClass(LUCKPERMS_RECALCULATE_EVENT);
            luckPermsSubscription = eventBusClass.getMethod("subscribe", Object.class, Class.class, Consumer.class)
                    .invoke(eventBus, plugin, eventClass, (Consumer<Object>) this::refreshLuckPermsUser);
            luckPermsEventClass = eventClass;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            luckPermsSubscription = null;
            luckPermsEventClass = null;
        }
    }

    private void refreshLevel(Class<?> eventClass, Event event) {
        try {
            Object player = eventClass.getMethod("getPlayer").invoke(event);
            if (player instanceof Player onlinePlayer) scheduleRefresh(onlinePlayer.getUniqueId());
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private void refreshLuckPermsUser(Object event) {
        try {
            Object user = luckPermsEventClass.getMethod("getUser").invoke(event);
            Object uuid = luckPermsEventClass.getMethod("getUser").getReturnType()
                    .getMethod("getUniqueId").invoke(user);
            if (uuid instanceof UUID playerId) scheduleRefresh(playerId);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private void scheduleRefresh(UUID playerId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            if (plugin.tags() != null) plugin.tags().refreshNametag(player);
            if (plugin.tabs() != null) plugin.tabs().update(player);
        });
    }
}