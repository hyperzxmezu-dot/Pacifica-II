package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class TrashSellConfirmationMenu extends BaseMenu {
    private static final int[] LIGHT_GRAY_SLOTS = {0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53};
    private static final int[] WHITE_SLOTS = {1, 10, 19, 28, 37, 46, 7, 16, 25, 34, 43, 52};
    private static final int[] BLACK_SLOTS = {2, 3, 4, 5, 6};
    private static final int[] GRAY_SLOTS = {13, 22, 31, 40, 49};
    private static final int[] SELL_SLOTS = {11, 12, 20, 21, 29, 30, 38, 39, 47, 48};

    private final UUID playerId;
    private final TrashMenu previousMenu;
    private ItemStack[] inventorySnapshot;
    private BigDecimal displayedTotal;
    private TransactionState state = TransactionState.OPEN;

    public TrashSellConfirmationMenu(PacificaPlugin plugin, Player player, TrashMenu previousMenu) {
        super(plugin, player, "&a&lConfirm Inventory Sell", 0);
        this.playerId = player.getUniqueId();
        this.previousMenu = previousMenu;
        inventorySnapshot = cloneContents(player.getInventory().getStorageContents());
        for (int slot = 0; slot < inventory.getSize(); slot++) set(slot, Items.create("RED_STAINED_GLASS_PANE", "&cCancel the transaction"));
        placePanes(LIGHT_GRAY_SLOTS, "LIGHT_GRAY_STAINED_GLASS_PANE");
        placePanes(WHITE_SLOTS, "WHITE_STAINED_GLASS_PANE");
        placePanes(BLACK_SLOTS, "BLACK_STAINED_GLASS_PANE");
        placePanes(GRAY_SLOTS, "GRAY_STAINED_GLASS_PANE");
        refreshSellButtons();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!playerId.equals(event.getWhoClicked().getUniqueId())) return;
        if (event.getClickedInventory() != inventory || state != TransactionState.OPEN) return;
        int slot = event.getSlot();
        if (contains(SELL_SLOTS, slot)) {
            confirmSale();
        } else if (!contains(LIGHT_GRAY_SLOTS, slot) && !contains(WHITE_SLOTS, slot)
                && !contains(BLACK_SLOTS, slot) && !contains(GRAY_SLOTS, slot)) {
            state = TransactionState.CANCELLED;
            returnToPrevious();
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (state == TransactionState.OPEN) {
            state = TransactionState.CANCELLED;
            returnToPrevious();
        }
    }

    private void confirmSale() {
        if (state != TransactionState.OPEN || !player.isOnline() || !playerId.equals(player.getUniqueId())) return;
        state = TransactionState.PROCESSING;
        ItemStack[] current = cloneContents(player.getInventory().getStorageContents());
        if (!sameContents(inventorySnapshot, current)) {
            inventorySnapshot = current;
            refreshSellButtons();
            state = TransactionState.OPEN;
            return;
        }
        BigDecimal total = previousMenu.calculateInventorySellPrice();
        if (!total.equals(displayedTotal)) {
            refreshSellButtons();
            state = TransactionState.OPEN;
            return;
        }
        if (total.signum() <= 0) {
            state = TransactionState.FAILED;
            player.sendMessage(plugin.store().message(player, "trash.no-sellable-items"));
            returnToPrevious();
            return;
        }
        if (!plugin.economy().isAvailable() || !plugin.economy().deposit(player, total)) {
            state = TransactionState.FAILED;
            player.sendMessage(plugin.store().message(player, "trash.transaction-failed"));
            returnToPrevious();
            return;
        }
        for (int slot = 0; slot < current.length; slot++) {
            if (previousMenu.sellable(current[slot])) current[slot] = null;
        }
        player.getInventory().setStorageContents(current);
        state = TransactionState.COMPLETED;
        player.sendMessage(plugin.store().message(player, "trash.inventory-sold", "%amount%", money(total)));
        returnToPrevious();
    }

    private void refreshSellButtons() {
        displayedTotal = previousMenu.calculateInventorySellPrice();
        String name = "&2Sell inventory for $" + money(displayedTotal);
        for (int slot : SELL_SLOTS) set(slot, Items.create("GREEN_STAINED_GLASS_PANE", name));
    }

    private void placePanes(int[] slots, String material) {
        for (int slot : slots) set(slot, Items.create(material, "&7"));
    }

    private void returnToPrevious() {
        plugin.guis().reopen(previousMenu);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) copy[slot] = contents[slot] == null ? null : contents[slot].clone();
        return copy;
    }

    private boolean sameContents(ItemStack[] first, ItemStack[] second) {
        if (first.length != second.length) return false;
        for (int slot = 0; slot < first.length; slot++) {
            ItemStack left = first[slot];
            ItemStack right = second[slot];
            if (left == null || right == null) {
                if (left != right) return false;
            } else if (left.getAmount() != right.getAmount() || !left.isSimilar(right)) {
                return false;
            }
        }
        return true;
    }

    private boolean contains(int[] slots, int target) {
        for (int slot : slots) if (slot == target) return true;
        return false;
    }

    private String money(BigDecimal value) {
        return previousMenu.money(value);
    }

    private enum TransactionState {
        OPEN,
        PROCESSING,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}