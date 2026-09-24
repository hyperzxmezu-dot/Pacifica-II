package com.districtx.pacifica;

import com.districtx.pacifica.api.tag.Tag;
import com.districtx.pacifica.api.tag.TagAPI;
import com.districtx.pacifica.api.tag.TagGUI;
import com.districtx.pacifica.api.tag.TagGUIProvider;
import com.districtx.pacifica.api.tag.TagSnapshot;
import com.districtx.pacifica.gui.TagMenu;
import com.districtx.pacifica.service.TagDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Internal service adapter registered for Pacifica-II API consumers. */
final class PacificaTagService implements TagAPI {
    private final PacificaPlugin plugin;
    private final Map<String, TagGUIProvider> guiProviders = new LinkedHashMap<>();

    /** @param plugin owning Pacifica-II plugin instance */
    PacificaTagService(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean isAvailable() { return plugin.isEnabled() && plugin.tags().enabled(); }

    @Override public Optional<Tag> getTag(String id) {
        if (!isAvailable()) return Optional.empty();
        Object definition = plugin.tags().getTag(id);
        return definition instanceof TagDefinition tag ? Optional.of(snapshot(tag)) : Optional.empty();
    }

    @Override public Collection<Tag> getTags() {
        if (!isAvailable()) return List.of();
        List<Tag> result = new ArrayList<>();
        for (TagDefinition tag : plugin.tags().getTags().values()) result.add(snapshot(tag));
        return List.copyOf(result);
    }

    @Override public Collection<Tag> getOwnedTags(Player player) {
        if (!isAvailable() || player == null) return List.of();
        List<Tag> result = new ArrayList<>();
        for (com.districtx.pacifica.service.Tag owned : plugin.tags().getOwnedTags(player.getUniqueId())) {
            Optional<Tag> tag = getTag(owned.getId());
            tag.ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    @Override public Optional<Tag> getEquippedTag(Player player) {
        if (!isAvailable() || player == null) return Optional.empty();
        com.districtx.pacifica.service.Tag equipped = plugin.tags().getEquippedTag(player.getUniqueId());
        return equipped instanceof TagDefinition tag ? Optional.of(snapshot(tag)) : Optional.empty();
    }

    @Override public boolean hasTag(Player player, String tagId) {
        return isAvailable() && player != null && plugin.tags().hasTag(player.getUniqueId(), tagId);
    }

    @Override public boolean equipTag(Player player, String tagId) {
        return isAvailable() && player != null && player.isOnline()
                && plugin.tags().equipTag(player.getUniqueId(), tagId);
    }

    @Override public boolean unequipTag(Player player) {
        if (!isAvailable() || player == null || !player.isOnline()) return false;
        if (plugin.tags().getEquippedTag(player.getUniqueId()) == null) return false;
        plugin.tags().unequipTag(player.getUniqueId());
        return plugin.tags().getEquippedTag(player.getUniqueId()) == null;
    }

    private TagSnapshot snapshot(TagDefinition definition) {
        return new TagSnapshot(definition.getId(), definition.getDisplayName(),
                com.districtx.pacifica.api.tag.TagRarity.valueOf(definition.getRarity().name()),
                com.districtx.pacifica.api.tag.TagSource.valueOf(definition.getSource().name()), plugin.tags().enabled());
    }

    @Override public boolean isTagGUI(Player player) { return findOpenGui(player) != null; }

    @Override public Optional<TagGUI> getOpenGUI(Player player) {
        TagMenu menu = findOpenGui(player);
        return menu == null ? Optional.empty() : Optional.of(new TagGUIHandle(player, menu));
    }

    @Override public boolean registerGUIProvider(TagGUIProvider provider) {
        if (!isAvailable() || provider == null) return false;
        try {
            String id = provider.getId();
            if (id == null) return false;
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9._-]{1,64}") || guiProviders.containsKey(normalized)) return false;
            guiProviders.put(normalized, provider);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override public boolean unregisterGUIProvider(String id) {
        return isAvailable() && id != null && guiProviders.remove(id.trim().toLowerCase(Locale.ROOT)) != null;
    }

    @Override public Collection<TagGUIProvider> getGUIProviders() { return List.copyOf(guiProviders.values()); }

    private TagMenu findOpenGui(Player player) {
        if (!isAvailable() || player == null || !player.isOnline()) return null;
        return player.getOpenInventory().getTopInventory().getHolder() instanceof TagMenu menu ? menu : null;
    }

    private final class TagGUIHandle implements TagGUI {
        private final Player player;
        private final TagMenu menu;

        private TagGUIHandle(Player player, TagMenu menu) {
            this.player = player;
            this.menu = menu;
        }

        @Override public Player getPlayer() { return player; }
        @Override public String getId() { return "tags"; }
        @Override public void open() { if (isAvailable()) plugin.guis().open(menu); }
        @Override public void close() { if (isOpen()) player.closeInventory(); }
        @Override public boolean isOpen() {
            return isAvailable() && player.getOpenInventory().getTopInventory().getHolder() == menu;
        }
    }
}