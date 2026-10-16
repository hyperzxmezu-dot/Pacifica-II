package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.tag.TagSnapshot;
import com.districtx.pacifica.api.tag.event.TagCreatedEvent;
import com.districtx.pacifica.api.tag.event.TagEquipEvent;
import com.districtx.pacifica.api.tag.event.TagRemovedEvent;
import com.districtx.pacifica.api.tag.event.TagUnequipEvent;
import com.districtx.pacifica.database.PlayerDatabase;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class TagManager {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private final PacificaPlugin plugin;
    private final Map<String, TagDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, PlayerTags> players = new LinkedHashMap<>();
    private final TagNametagProvider nametagProvider;
    private Object placeholderExpansion;
    private Object identityExpansion;

    public TagManager(PacificaPlugin plugin) {
        this.plugin = plugin;
        this.nametagProvider = new TagNametagProvider(plugin);
        load();
    }

    public void start() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (enabled() && plugin.store().tag().getBoolean("tag-system.placeholder.enabled", true)) {
                try {
                    Class<?> expansionClass = Class.forName("com.districtx.pacifica.service.TagPlaceholderExpansion");
                    placeholderExpansion = expansionClass.getConstructor(PacificaPlugin.class).newInstance(plugin);
                    expansionClass.getMethod("register").invoke(placeholderExpansion);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    placeholderExpansion = null;
                }
            }
            try {
                Class<?> expansionClass = Class.forName("com.districtx.pacifica.service.PacificaIdentityPlaceholderExpansion");
                Object expansion = expansionClass.getConstructor(PacificaPlugin.class).newInstance(plugin);
                if (Boolean.TRUE.equals(expansionClass.getMethod("register").invoke(expansion))) {
                    identityExpansion = expansion;
                } else {
                    plugin.getLogger().warning("Could not register Pacifica-II placeholders with identifier 'pacificaii'.");
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                identityExpansion = null;
                plugin.getLogger().warning("Could not register Pacifica-II PlaceholderAPI expansion: " + exception.getMessage());
            }
        }
        if (!enabled()) return;
        refreshAll();
    }

    public void stop() {
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.getClass().getMethod("unregister").invoke(placeholderExpansion);
            } catch (ReflectiveOperationException ignored) { }
            placeholderExpansion = null;
        }
        if (identityExpansion != null) {
            try {
                identityExpansion.getClass().getMethod("unregister").invoke(identityExpansion);
            } catch (ReflectiveOperationException ignored) { }
            identityExpansion = null;
        }
    }

    public boolean enabled() {
        return plugin.store().tag().getBoolean("tag-system.enabled", true);
    }

    public Tag getTag(String id) {
        return definitions.get(normalize(id));
    }

    public boolean tagExists(String id) {
        return getTag(id) != null;
    }

    public Map<String, TagDefinition> getTags() {
        return Collections.unmodifiableMap(definitions);
    }

    public Set<Tag> getOwnedTags(UUID uuid) {
        PlayerTags state = players.get(uuid);
        if (state == null) return Collections.emptySet();
        Set<Tag> result = new LinkedHashSet<>();
        for (String id : state.owned) {
            TagDefinition tag = definitions.get(id);
            if (tag != null) result.add(tag);
        }
        return Collections.unmodifiableSet(result);
    }

    public Tag getEquippedTag(UUID uuid) {
        PlayerTags state = players.get(uuid);
        return state == null || state.equipped == null ? null : definitions.get(state.equipped);
    }

    public boolean hasTag(UUID uuid, String id) {
        PlayerTags state = players.get(uuid);
        return state != null && state.owned.contains(normalize(id));
    }

    public boolean equipTag(UUID uuid, String id) {
        String normalized = normalize(id);
        PlayerTags state = players.get(uuid);
        if (!definitions.containsKey(normalized) || state == null || !state.owned.contains(normalized)) return false;
        if (normalized.equals(state.equipped)) return true;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            TagEquipEvent event = new TagEquipEvent(player, snapshot(definitions.get(normalized)));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
        }
        state.equipped = normalized;
        save();
        refresh(uuid);
        return true;
    }

    public void unequipTag(UUID uuid) {
        PlayerTags state = players.get(uuid);
        if (state == null || state.equipped == null) return;
        TagDefinition equipped = definitions.get(state.equipped);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && equipped != null) {
            TagUnequipEvent event = new TagUnequipEvent(player, snapshot(equipped));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
        }
        state.equipped = null;
        save();
        refresh(uuid);
    }

    public boolean giveTag(UUID uuid, String id) {
        String normalized = normalize(id);
        if (!definitions.containsKey(normalized)) return false;
        PlayerTags state = players.computeIfAbsent(uuid, ignored -> new PlayerTags());
        if (!state.owned.add(normalized)) return false;
        save();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && plugin.tagGui() != null) plugin.tagGui().refresh(player);
        return true;
    }

    public boolean removeTag(UUID uuid, String id) {
        PlayerTags state = players.get(uuid);
        String normalized = normalize(id);
        if (state == null || !state.owned.contains(normalized)) return false;
        if (normalized.equals(state.equipped)) {
            TagDefinition equipped = definitions.get(normalized);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && equipped != null) {
                TagUnequipEvent event = new TagUnequipEvent(player, snapshot(equipped));
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return false;
            }
        }
        if (!state.owned.remove(normalized)) return false;
        if (normalized.equals(state.equipped)) state.equipped = null;
        save();
        refresh(uuid);
        return true;
    }

    public boolean createTag(String id, String displayName) {
        return createTag(id, displayName, TagRarity.COMMON, TagSource.EVENT);
    }

    public boolean createTag(String id, String displayName, TagRarity rarity, TagSource source) {
        String normalized = normalize(id);
        if (!VALID_ID.matcher(normalized).matches() || displayName == null || displayName.isBlank()
                || rarity == null || source == null || definitions.containsKey(normalized)) return false;
        definitions.put(normalized, new TagDefinition(normalized, displayName, rarity, source));
        save();
        Bukkit.getPluginManager().callEvent(new TagCreatedEvent(snapshot(definitions.get(normalized))));
        if (plugin.tagGui() != null) plugin.tagGui().refreshAll();
        return true;
    }

    public boolean editTag(String id, String displayName) {
        TagDefinition tag = (TagDefinition) getTag(id);
        if (tag == null || displayName == null || displayName.isBlank()) return false;
        tag.setDisplayName(displayName);
        save();
        refreshAll();
        if (plugin.tagGui() != null) plugin.tagGui().refreshAll();
        return true;
    }

    public boolean editRarity(String id, TagRarity rarity) {
        TagDefinition tag = (TagDefinition) getTag(id);
        if (tag == null || rarity == null) return false;
        tag.setRarity(rarity);
        save();
        if (plugin.tagGui() != null) plugin.tagGui().refreshAll();
        return true;
    }

    public boolean editSource(String id, TagSource source) {
        TagDefinition tag = (TagDefinition) getTag(id);
        if (tag == null || source == null) return false;
        tag.setSource(source);
        save();
        if (plugin.tagGui() != null) plugin.tagGui().refreshAll();
        return true;
    }

    public boolean deleteTag(String id) {
        String normalized = normalize(id);
        TagDefinition removed = definitions.remove(normalized);
        if (removed == null) return false;
        plugin.store().data().set("tags.definitions." + normalized, null);
        for (PlayerTags state : players.values()) {
            state.owned.remove(normalized);
            if (normalized.equals(state.equipped)) state.equipped = null;
        }
        save();
        Bukkit.getPluginManager().callEvent(new TagRemovedEvent(snapshot(removed)));
        refreshAll();
        if (plugin.tagGui() != null) plugin.tagGui().refreshAll();
        return true;
    }

    public String display(UUID uuid) {
        Tag tag = getEquippedTag(uuid);
        return tag == null ? "" : ((TagDefinition) tag).displayText();
    }

    /** Refreshes the player's above-head nametag using their current Tag or level identity. */
    public void refreshNametag(Player player) {
        nametagProvider.update(player);
    }

    /** Removes the player's above-head nametag entry when they leave the server. */
    public void removeNametag(Player player) {
        nametagProvider.remove(player);
    }

    public void refreshAll() {
        if (!enabled()) return;
        nametagProvider.updateAll();
        if (plugin.tabs() != null) plugin.tabs().updateAll();
    }

    private void refresh(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) nametagProvider.update(player);
        if (plugin.tabs() != null) plugin.tabs().updateAll();
    }

    private void load() {
        ConfigurationSection tagSection = plugin.store().data().getConfigurationSection("tags.definitions");
        if (tagSection != null) {
            for (String id : tagSection.getKeys(false)) {
                String normalized = normalize(id);
                String display = tagSection.getString(id + ".display", "");
                TagRarity rarity = TagRarity.parse(tagSection.getString(id + ".rarity", "COMMON"));
                TagSource source = TagSource.parse(tagSection.getString(id + ".source", "EVENT"));
                if (VALID_ID.matcher(normalized).matches() && !display.isBlank()) {
                    definitions.put(normalized, new TagDefinition(normalized, display,
                            rarity == null ? TagRarity.COMMON : rarity,
                            source == null ? TagSource.EVENT : source));
                }
            }
        }
        Map<UUID, PlayerDatabase.TagData> databaseTags = plugin.playerDatabase().loadTags();
        for (Map.Entry<UUID, PlayerDatabase.TagData> entry : databaseTags.entrySet()) {
            PlayerTags state = new PlayerTags();
            for (String id : entry.getValue().owned()) if (definitions.containsKey(id)) state.owned.add(id);
            String equipped = entry.getValue().equipped();
            if (equipped != null && state.owned.contains(equipped)) state.equipped = equipped;
            players.put(entry.getKey(), state);
        }
    }

    private void save() {
        for (Map.Entry<String, TagDefinition> entry : definitions.entrySet()) {
            String path = "tags.definitions." + entry.getKey();
            plugin.store().data().set(path + ".display", entry.getValue().getDisplayName());
            plugin.store().data().set(path + ".rarity", entry.getValue().getRarity().name());
            plugin.store().data().set(path + ".source", entry.getValue().getSource().name());
        }
        for (Map.Entry<UUID, PlayerTags> entry : players.entrySet()) {
            plugin.playerDatabase().saveTags(entry.getKey(), entry.getValue().owned, entry.getValue().equipped);
        }
        plugin.store().save();
    }

    private String normalize(String id) {
        return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
    }

    private TagSnapshot snapshot(TagDefinition definition) {
        return new TagSnapshot(definition.getId(), definition.getDisplayName(),
                com.districtx.pacifica.api.tag.TagRarity.valueOf(definition.getRarity().name()),
                com.districtx.pacifica.api.tag.TagSource.valueOf(definition.getSource().name()), enabled());
    }

    private static final class PlayerTags {
        private final Set<String> owned = new LinkedHashSet<>();
        private String equipped;
    }
}