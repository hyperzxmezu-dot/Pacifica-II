package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.tag.TagAPI;
import com.districtx.pacifica.api.tag.TagGUIProvider;
import com.districtx.pacifica.service.Tag;
import com.districtx.pacifica.service.TagDefinition;
import com.districtx.pacifica.service.TagSource;
import com.districtx.pacifica.util.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;

public final class TagMenu extends BaseMenu {
    private static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final int totalPages;
    private final Map<Integer, TagGUIProvider> providerSlots = new HashMap<>();

    public TagMenu(PacificaPlugin plugin, org.bukkit.entity.Player player, int page) {
        super(plugin, player, plugin.store().tag().getString("tag-system.gui.title", "&b&lTags"), page);
        List<Tag> tags = new ArrayList<>(plugin.tags().getTags().values());
        List<ProviderEntry> providers = providerEntries();
        totalPages = Math.max(1, (tags.size() + providers.size() + CONTENT.length - 1) / CONTENT.length);
        if (this.page > totalPages) return;
        for (int index = 0; index < CONTENT.length; index++) {
            int tagIndex = this.page * CONTENT.length + index;
            if (tagIndex >= tags.size() + providers.size()) break;
            if (tagIndex < tags.size()) set(CONTENT[index], item((TagDefinition) tags.get(tagIndex)));
            else {
                ProviderEntry provider = providers.get(tagIndex - tags.size());
                set(CONTENT[index], provider.icon());
                providerSlots.put(CONTENT[index], provider.provider());
            }
        }
        set(49, Items.create("BOOK", "&b&lPage " + (this.page + 1), "&7", "&7Use the arrows to navigate between pages"));
        if (this.page > 0) set(48, Items.create("ARROW", "&b&l< Previous Page", "&8Page " + this.page + " / " + totalPages));
        if (this.page + 1 < totalPages) set(50, Items.create("ARROW", "&b&lNext Page >", "&8Page " + (this.page + 2) + " / " + totalPages));
    }

    @Override
    protected void frame() {
        ItemStack background = Items.create("LIGHT_BLUE_STAINED_GLASS_PANE", "&7");
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, background);
        ItemStack border = Items.create("CYAN_STAINED_GLASS_PANE", "&7");
        for (int slot : new int[]{0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53}) inventory.setItem(slot, border);
        for (int slot : new int[]{1, 2, 3, 4, 5, 6, 7, 46, 47, 48, 50}) inventory.setItem(slot, background);
    }

    private ItemStack item(TagDefinition tag) {
        boolean owned = plugin.tags().hasTag(player.getUniqueId(), tag.getId());
        boolean equipped = plugin.tags().getEquippedTag(player.getUniqueId()) != null
                && plugin.tags().getEquippedTag(player.getUniqueId()).getId().equals(tag.getId());
        String rarity = Items.color(plugin.store().tag().getString("tag-system.rarity." + tag.getRarity().name().toLowerCase(java.util.Locale.ROOT), tag.getRarity().name()));
        String source = Items.color(plugin.store().tag().getString("tag-system.source." + tag.getSource().name().toLowerCase(java.util.Locale.ROOT), tag.getSource().name()));
        List<String> lore = new ArrayList<>();
        lore.add("&7");
        lore.add("&7Rarity: " + rarity);
        lore.add("&7Source: " + source);
        lore.add("&7");
        if (equipped) {
            lore.add("&a✔ Equipped");
            lore.add("&e&lRight-Click to unequip.");
        }
        else if (owned) {
            lore.add("&aOwned");
            lore.add("&e&lRight-Click to equip.");
            if (tag.getSource() == TagSource.COSMETICS) lore.add("&c&lShift-Right-Click to take out.");
        } else {
            lore.add("&c✘ Not Owned");
            lore.add("&7Obtain from crates or trade up.");
        }
        return Items.create(plugin.store().tag().getString("tag-system.gui.tag-material", "NAME_TAG"), tag.displayText(), lore.toArray(new String[0]));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() == 48 && page > 0) {
            open(new TagMenu(plugin, player, page - 1));
            return;
        }
        if (event.getRawSlot() == 50 && page + 1 < totalPages) {
            open(new TagMenu(plugin, player, page + 1));
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= inventory.getSize()) return;
        TagGUIProvider provider = providerSlots.get(event.getRawSlot());
        if (provider != null) {
            try {
                provider.onClick(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Tag GUI provider failed: " + exception.getMessage());
            }
            return;
        }
        int index = -1;
        for (int i = 0; i < CONTENT.length; i++) if (CONTENT[i] == event.getRawSlot()) index = i;
        if (index < 0) return;
        List<Tag> tags = new ArrayList<>(plugin.tags().getTags().values());
        int tagIndex = page * CONTENT.length + index;
        if (tagIndex >= tags.size()) return;
        TagDefinition tag = (TagDefinition) tags.get(tagIndex);
        if (!plugin.tags().hasTag(player.getUniqueId(), tag.getId())) return;
        boolean eventRemovable = plugin.store().tag().getBoolean("tag-system.event-removable", false);
        if (event.isRightClick() && event.isShiftClick()
                && (tag.getSource() == TagSource.COSMETICS || (tag.getSource() == TagSource.EVENT && eventRemovable))) {
            ItemStack item = plugin.tagItems().create(tag);
            if (item != null && canFit(item) && plugin.tags().removeTag(player.getUniqueId(), tag.getId())) {
                player.getInventory().addItem(item);
                open(new TagMenu(plugin, player, page));
            }
            return;
        }
        if (event.isRightClick()) {
            Tag equipped = plugin.tags().getEquippedTag(player.getUniqueId());
            if (equipped != null && equipped.getId().equals(tag.getId())) {
                plugin.tags().unequipTag(player.getUniqueId());
                if (plugin.tags().getEquippedTag(player.getUniqueId()) == null) {
                    player.sendMessage(plugin.store().message(player, "tag.unequipped"));
                }
            } else {
                if (plugin.tags().equipTag(player.getUniqueId(), tag.getId())) {
                    player.sendMessage(plugin.store().message(player, "tag.equipped", "%tag%", tag.getDisplayName()));
                }
            }
            open(new TagMenu(plugin, player, page));
        }
    }

    private boolean canFit(ItemStack item) {
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) return true;
        }
        return false;
    }

    private List<ProviderEntry> providerEntries() {
        TagAPI api = Bukkit.getServicesManager().load(TagAPI.class);
        if (api == null || !api.isAvailable()) return List.of();
        List<ProviderEntry> result = new ArrayList<>();
        for (TagGUIProvider provider : api.getGUIProviders()) {
            try {
                ItemStack icon = provider.createIcon(player);
                if (icon != null) result.add(new ProviderEntry(provider, icon.clone()));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Unable to render Tag GUI provider: " + exception.getMessage());
            }
        }
        return result;
    }

    private record ProviderEntry(TagGUIProvider provider, ItemStack icon) { }

    @Override
    public void handleDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        event.setCancelled(true);
    }
}