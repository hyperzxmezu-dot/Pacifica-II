package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import com.districtx.pacifica.database.ItemPrice;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class TrashMenu extends BaseMenu {
    private static final int[] DEFAULT_DEPOSIT = {0, 1, 2, 3, 4, 5, 6, 7, 8, 18, 19, 20, 21, 22, 23, 24, 25, 26, 36, 37, 38, 39, 40, 41, 42};
    private static final int[] DEFAULT_REWARD = {9, 10, 11, 12, 13, 14, 15, 16, 17, 27, 28, 29, 30, 31, 32, 33, 34, 35, 45, 46, 47, 48, 49, 50, 51};
    private final int[] deposit;
    private final int[] reward;
    private final ConfigurationSection settings;
    private boolean settled;
    public TrashMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, string(plugin, "title", "&7&lTrash Can"), 0);
        settings = plugin.store().trashcan().getConfigurationSection("trashcan-gui");
        deposit = slots(settings, "deposit-slots", DEFAULT_DEPOSIT);
        reward = slots(settings, "reward-slots", DEFAULT_REWARD);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        for (int slot : reward) set(slot, Items.create(string("reward-pane.material", "BLACK_STAINED_GLASS_PANE"), string("reward-pane.name", "&7")));
        set(buttonSlot("cancel"), Items.create(string("buttons.cancel.material", "REDSTONE"), string("buttons.cancel.name", "&c&lCancel"), string("buttons.cancel.lore", "&7Return all items!")));
        set(buttonSlot("sell-entire-inventory"), Items.create(string("buttons.sell-entire-inventory.material", "DIAMOND"), string("buttons.sell-entire-inventory.name", "&6&lSell Entire Inventory"), totalLore("buttons.sell-entire-inventory.lore", "&7Total Value: &a&l$0", 0)));
        set(buttonSlot("confirm"), Items.create(string("buttons.confirm.material", "PAPER"), string("buttons.confirm.name", "&a&lConfirm"), totalLore("buttons.confirm.lore", "&7Total Reward: &a&l$0", 0)));
        refresh();
    }

    @Override public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == inventory) {
            int slot = event.getSlot();
            if (slot == buttonSlot("cancel")) {
                event.setCancelled(true);
                returnDeposits();
                settled = true;
                player.closeInventory();
                return;
            }
            if (slot == buttonSlot("sell-entire-inventory")) {
                event.setCancelled(true);
                sellPlayerInventory();
                return;
            }
            if (slot == buttonSlot("confirm")) {
                event.setCancelled(true);
                confirm();
                return;
            }
            if (!isDeposit(slot)) {
                event.setCancelled(true);
                return;
            }
            refreshLater();
        } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        } else if (event.isShiftClick()) {
            refreshLater();
        }
    }

    @Override public void handleDrag(InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize() && !isDeposit(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        refreshLater();
    }

    private boolean isDeposit(int slot) { for (int value : deposit) if (value == slot) return true; return false; }
    private void refreshLater() { plugin.getServer().getScheduler().runTask(plugin, this::refresh); }
    private void refresh() {
        double total = 0;
        for (int i = 0; i < deposit.length; i++) {
            ItemStack item = inventory.getItem(deposit[i]);
            double value = sellable(item) ? price(item) * item.getAmount() : 0;
            total += value;
            if (value <= 0) set(reward[i], Items.create(string("reward-pane.material", "BLACK_STAINED_GLASS_PANE"), string("reward-pane.name", "&7")));
            else set(reward[i], Items.create(string("reward-pane.sellable-material", "GREEN_STAINED_GLASS_PANE"), string("reward-pane.reward-name", "&a&lReward: $%value%").replace("%value%", money(value))));
        }
        updateButton(buttonSlot("sell-entire-inventory"), string("buttons.sell-entire-inventory.material", "DIAMOND"), string("buttons.sell-entire-inventory.name", "&6&lSell Entire Inventory"), totalLore("buttons.sell-entire-inventory.lore", "&7Total Value: &a&l$%value%", inventoryTotal()));
        updateButton(buttonSlot("confirm"), string("buttons.confirm.material", "PAPER"), string("buttons.confirm.name", "&a&lConfirm"), totalLore("buttons.confirm.lore", "&7Total Reward: &a&l$%value%", total));
    }
    private void updateButton(int slot, String material, String name, String lore) { set(slot, Items.create(material, name, lore)); }
    private double inventoryTotal() { double total = 0; for (ItemStack item : player.getInventory().getStorageContents()) if (sellable(item)) total += price(item) * item.getAmount(); return total; }
    private void sellPlayerInventory() {
        double total = inventoryTotal();
        if (total <= 0 || !plugin.economy().deposit(player, total)) { player.sendMessage(plugin.store().message(player, "trash.no-sellable-items")); return; }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) if (sellable(contents[i])) contents[i] = null;
        player.getInventory().setStorageContents(contents);
        player.sendMessage(plugin.store().message(player, "trash.inventory-sold", "%amount%", money(total)));
        refresh();
    }
    private void confirm() {
        double total = 0;
        for (int i : deposit) { ItemStack item = inventory.getItem(i); if (sellable(item)) total += price(item) * item.getAmount(); }
        if (total <= 0) { player.sendMessage(plugin.store().message(player, "trash.no-eligible-items")); return; }
        if (!plugin.economy().deposit(player, total)) { player.sendMessage(plugin.store().message(player, "trash.transaction-failed")); returnDeposits(); settled = true; player.closeInventory(); return; }
        for (int slot : deposit) if (sellable(inventory.getItem(slot))) inventory.setItem(slot, null);
        returnDeposits();
        settled = true;
        player.sendMessage(plugin.store().message(player, "trash.items-sold", "%amount%", money(total)));
        player.closeInventory();
    }
    private boolean sellable(ItemStack item) { if (item == null || item.getType().isAir()) return false; String key = com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name(); return price(item) > 0 && !plugin.store().protection().getStringList("protection.protected-items").stream().map(String::toUpperCase).anyMatch(key::equals); }
    private double price(ItemStack item) {
        if (item == null) return 0;
        ItemPrice stored = plugin.itemPriceDatabase().getPrice(item);
        return stored == null ? 0 : durabilityPrice(item, stored.getBasePrice().doubleValue());
    }
    private boolean sameIdentity(ItemStack item, ItemStack identity) {
        return identityItem(item).isSimilar(identityItem(identity));
    }
    private ItemStack identityItem(ItemStack item) {
        ItemStack identity = item.clone();
        identity.setAmount(1);
        ItemMeta meta = identity.getItemMeta();
        if (meta != null) {
            meta.setLore(null);
            if (meta instanceof Damageable damageable) damageable.setDamage(0);
            identity.setItemMeta(meta);
        }
        return identity;
    }
    private double durabilityPrice(ItemStack item, double basePrice) {
        if (basePrice <= 0) return 0;
        ItemMeta meta = item.getItemMeta();
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0 || !(meta instanceof Damageable damageable) || meta.isUnbreakable()) return basePrice;
        double remaining = Math.max(0, maxDurability - damageable.getDamage());
        double multiplier = Math.floor((remaining / maxDurability) * 10.0) / 10.0;
        return basePrice * Math.max(0, Math.min(1, multiplier));
    }
    private String money(double value) { return String.format(Locale.US, "%.2f", value).replaceFirst("\\.00$", ""); }
    private void returnDeposits() { for (int slot : deposit) { ItemStack item = inventory.getItem(slot); if (item != null) { java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item); leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)); inventory.setItem(slot, null); } } }
    @Override public void handleClose(InventoryCloseEvent event) { if (!settled) returnDeposits(); }

    private static String string(PacificaPlugin plugin, String path, String fallback) {
        ConfigurationSection section = plugin.store().trashcan().getConfigurationSection("trashcan-gui");
        return section == null ? fallback : section.getString(path, fallback);
    }
    private String string(String path, String fallback) { return settings == null ? fallback : settings.getString(path, fallback); }
    private int buttonSlot(String button) { return settings == null ? (button.equals("cancel") ? 44 : button.equals("confirm") ? 53 : 52) : settings.getInt("buttons." + button + ".slot", button.equals("cancel") ? 44 : button.equals("confirm") ? 53 : 52); }
    private String totalLore(String path, String fallback, double value) { return string(path, fallback).replace("%value%", money(value)); }
    private static int[] slots(ConfigurationSection section, String path, int[] fallback) {
        if (section == null) return fallback.clone();
        List<Integer> values = section.getIntegerList(path);
        if (values.size() != fallback.length) return fallback.clone();
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}