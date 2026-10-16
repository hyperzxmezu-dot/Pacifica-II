package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class ChoosePlayerMenu extends JobDetailMenu {
    private static final int[] PLAYER_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private static final int[] PAGED_PLAYER_SLOTS = {12, 13, 14, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42};

    private final Map<Integer, UUID> playerIdsBySlot = new HashMap<>();
    private final int page;

    public ChoosePlayerMenu(PacificaPlugin plugin, Player player, int requestedPage) {
        super(plugin, player, "&5&lChoose Player");
        standardFrame("GRAY_STAINED_GLASS_PANE");
        List<OfflinePlayer> players = plugin.jobMenus().historicalPlayers();
        boolean paged = players.size() > PLAYER_SLOTS.length;
        int[] slots = paged ? PAGED_PLAYER_SLOTS : PLAYER_SLOTS;
        int pageSize = slots.length;
        int pageCount = Math.max(1, (players.size() + pageSize - 1) / pageSize);
        page = Math.min(requestedPage, pageCount - 1);
        int start = page * pageSize;
        int end = Math.min(players.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            OfflinePlayer offlinePlayer = players.get(index);
            String name = offlinePlayer.getName();
            if (name == null) continue;
            String status = offlinePlayer.isOnline() ? "&a&lOnline" : "&c&lOffline";
            ItemStack head = Items.create("PLAYER_HEAD", "&e&l" + name, "&7Click to target!", status);
            if (head != null && head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(offlinePlayer);
                head.setItemMeta(meta);
            }
            int slot = slots[index - start];
            set(slot, head);
            playerIdsBySlot.put(slot, offlinePlayer.getUniqueId());
        }

        mainButton(47, "REDSTONE", "&c&lBack", "&7Return to the bounty menu!");
        if (paged && page > 0) set(11, Items.create("ARROW", "&a&lPrevious Page"));
        if (paged && page < pageCount - 1) set(15, Items.create("ARROW", "&a&lNext Page"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory || event.getCurrentItem() == null
                || !event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;
        int slot = event.getSlot();
        if (slot == 47 && isMaterial(event, "REDSTONE")) {
            plugin.jobMenus().returnToPlaceBountiesMenu(player);
            return;
        }
        if (slot == 11 && isMaterial(event, "ARROW")) {
            plugin.jobMenus().openChoosePlayerMenu(player, page - 1);
            return;
        }
        if (slot == 15 && isMaterial(event, "ARROW")) {
            plugin.jobMenus().openChoosePlayerMenu(player, page + 1);
            return;
        }
        UUID targetId = playerIdsBySlot.get(slot);
        if (targetId != null && isMaterial(event, "PLAYER_HEAD")) plugin.jobMenus().selectBountyTarget(player, targetId);
    }

    private boolean isMaterial(InventoryClickEvent event, String material) {
        ItemStack expected = Items.create(material, "");
        return expected != null && event.getCurrentItem().getType() == expected.getType();
    }
}