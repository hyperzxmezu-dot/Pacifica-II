package com.districtx.pacifica.api.tag;

import java.util.Collection;
import java.util.Optional;
import java.util.Collection;
import org.bukkit.entity.Player;

/** Public access to Pacifica-II tag definitions and player tag state. */
public interface TagAPI {
    /** @return {@code true} when the Tag service is enabled */
    boolean isAvailable();

    /**
     * Finds a tag by its case-insensitive identifier.
     * Must be called on the server's main thread.
     *
     * @param id tag identifier
     * @return matching tag, if present
     */
    Optional<Tag> getTag(String id);

    /**
     * Returns an immutable collection of current tag definitions.
     * Must be called on the server's main thread.
     *
     * @return immutable tag snapshots
     */
    Collection<Tag> getTags();

    /**
     * Returns an immutable collection of tags owned by the player.
     * Must be called on the server's main thread.
     *
     * @param player player to inspect
     * @return immutable owned tag snapshots
     */
    Collection<Tag> getOwnedTags(Player player);

    /**
     * Returns the player's equipped tag, if any.
     * Must be called on the server's main thread.
     *
     * @param player player to inspect
     * @return equipped tag, if present
     */
    Optional<Tag> getEquippedTag(Player player);

    /**
     * Checks whether the player owns the identified tag.
     * Must be called on the server's main thread.
     *
     * @param player player to inspect
     * @param tagId tag identifier
     * @return {@code true} if the tag is owned
     */
    boolean hasTag(Player player, String tagId);

    /**
     * Equips a tag if it exists and the player owns it.
     * The operation can be cancelled by {@code TagEquipEvent}.
     * Must be called on the server's main thread.
     *
     * @param player player to update
     * @param tagId tag identifier
     * @return {@code true} when equipped or already equipped
     */
    boolean equipTag(Player player, String tagId);

    /**
     * Unequips the player's current tag when present.
     * The operation can be cancelled by {@code TagUnequipEvent}.
     * Must be called on the server's main thread.
     *
     * @param player player to update
     * @return {@code true} if a tag was unequipped
     */
    boolean unequipTag(Player player);

    /**
     * Checks whether the player currently has the Tag GUI open.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return {@code true} if a Pacifica-II Tag GUI is open
     */
    boolean isTagGUI(Player player);

    /**
     * Returns a safe handle for the player's open Tag GUI.
     * Must be called on the server's main thread.
     *
     * @param player player to check
     * @return GUI handle, if the Tag GUI is open
     */
    Optional<TagGUI> getOpenGUI(Player player);

    /**
     * Registers an additional entry in the paginated Tag menu.
     * Provider IDs must be unique. Must be called on the server's main thread.
     *
     * @param provider menu entry provider
     * @return {@code true} if registered
     */
    boolean registerGUIProvider(TagGUIProvider provider);

    /**
     * Unregisters a Tag menu provider by its case-insensitive ID.
     * Must be called on the server's main thread.
     *
     * @param id provider ID
     * @return {@code true} if a provider was removed
     */
    boolean unregisterGUIProvider(String id);

    /**
     * Returns an immutable snapshot of registered Tag menu providers.
     * Must be called on the server's main thread.
     *
     * @return registered providers in registration order
     */
    Collection<TagGUIProvider> getGUIProviders();
}