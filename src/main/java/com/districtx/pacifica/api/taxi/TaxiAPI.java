package com.districtx.pacifica.api.taxi;

import java.util.Optional;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Public access to Pacifica-II's player teleport and Taxi operations. */
public interface TaxiAPI {
    /**
     * Returns whether this implementation is currently available.
     *
     * @return {@code true} if Pacifica-II is enabled
     */
    boolean isAvailable();

    /**
     * Checks whether the player has an active Pacifica-II teleport countdown.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return {@code true} if a countdown is active
     */
    boolean isTeleporting(Player player);

    /**
     * Returns a read-only snapshot of the player's pending teleport.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return pending request, if one exists
     */
    Optional<TeleportRequest> getTeleportRequest(Player player);

    /**
     * Returns a read-only snapshot when the player's pending teleport was
     * started through a Taxi action. Must be called on the server's main thread.
     *
     * @param player player to check
     * @return pending Taxi request, if one exists
     */
    Optional<TaxiRequest> getTaxiRequest(Player player);

    /**
     * Cancels the player's active Pacifica-II teleport countdown, if present.
     * Must be called on the server's main thread.
     *
     * @param player player whose countdown should be cancelled
     */
    void cancelTeleport(Player player);

    /**
     * Cancels the player's active Taxi countdown, if present. Other teleport
     * countdowns are left untouched. Must be called on the server's main thread.
     *
     * @param player player whose Taxi countdown should be cancelled
     */
    void cancelTaxi(Player player);

    /**
     * Starts a Pacifica-II teleport countdown to the destination.
     * Must be called on the server's main thread.
     *
     * @param player player to teleport
     * @param destination destination location; it is copied by the API
     * @return typed outcome of the request
     */
    TaxiResult requestTeleport(Player player, Location destination);

    /**
     * Starts a teleport countdown with an explicit source reason.
     * Must be called on the server's main thread.
     *
     * @param player player to teleport
     * @param destination destination location; it is copied by the API
     * @param reason source reported by Taxi lifecycle events
     * @return typed outcome of the request
     */
    TaxiResult requestTeleport(Player player, Location destination, TaxiReason reason);

    /**
     * Starts a Taxi teleport countdown to the destination.
     * Must be called on the server's main thread.
     *
     * @param player player to teleport
     * @param destination destination location; it is copied by the API
     * @return typed outcome of the request
     */
    TaxiResult requestTaxi(Player player, Location destination);

    /**
     * Checks whether the player currently has the Taxi GUI open.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return {@code true} if a Pacifica-II Taxi GUI is open
     */
    boolean isTaxiGUI(Player player);

    /**
     * Returns a safe handle for the player's open Taxi GUI.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return GUI handle, if the Taxi GUI is open
     */
    Optional<TaxiGUI> getOpenTaxiGUI(Player player);

    /**
     * Registers an additional Taxi menu action. Provider IDs must be unique.
     * Must be called on the server's main thread.
     *
     * @param provider menu action provider
     * @return {@code true} if registered
     */
    boolean registerGUIProvider(TaxiGUIProvider provider);

    /**
     * Unregisters a Taxi menu provider by its case-insensitive ID.
     * Must be called on the server's main thread.
     *
     * @param id provider ID
     * @return {@code true} if a provider was removed
     */
    boolean unregisterGUIProvider(String id);

    /**
     * Returns a snapshot of the configured Taxi warp identifiers.
     * Must be called on the server's main thread.
     *
     * @return configured warp identifiers
     */
    default Collection<String> getWarpNames() { return java.util.List.of(); }

    /**
     * Returns a copy of a configured Taxi warp's location.
     * Must be called on the server's main thread.
     *
     * @param name warp identifier
     * @return configured warp location, if it exists and its world is available
     */
    default Optional<Location> getWarpLocation(String name) { return Optional.empty(); }

    /**
     * Returns an immutable snapshot of registered Taxi menu providers.
     * Must be called on the server's main thread.
     *
     * @return registered providers in registration order
     */
    Collection<TaxiGUIProvider> getGUIProviders();
}