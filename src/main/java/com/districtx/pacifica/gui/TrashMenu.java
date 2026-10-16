package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.util.Items;
import com.districtx.pacifica.database.ItemPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
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
    private boolean transitioning;
    private boolean processingDeposit;
    public TrashMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, string(plugin, "title", "&7&lTrash Can"), 0);
        settings = plugin.store().trashcan().getConfigurationSection("trashcan-gui");
        deposit = slots(settings, "deposit-slots", DEFAULT_DEPOSIT);
        reward = slots(settings, "reward-slots", DEFAULT_REWARD);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        for (int slot : reward) set(slot, Items.create(string("reward-pane.material", "BLACK_STAINED_GLASS_PANE"), string("reward-pane.name", "&7")));
        set(buttonSlot("cancel"), Items.create(string("buttons.cancel.material", "REDSTONE"), string("buttons.cancel.name", "&c&lCancel"), string("buttons.cancel.lore", "&7Return all items!")));
        set(buttonSlot("sell-entire-inventory"), Items.create(string("buttons.sell-entire-inventory.material", "DIAMOND"), string("buttons.sell-entire-inventory.name", "&a&lSell Entire Inventory"), totalLore("buttons.sell-entire-inventory.lore", "&7Total Value: &a&l$0", BigDecimal.ZERO)));
        set(buttonSlot("confirm"), Items.create(string("buttons.confirm.material", "PAPER"), string("buttons.confirm.name", "&a&lConfirm"), totalLore("buttons.confirm.lore", "&7Total Reward: &a&l$0", BigDecimal.ZERO)));
        refresh();
    }

    @Override public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
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
                openSellConfirmation();
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
        if (!event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
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
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < deposit.length; i++) {
            ItemStack item = inventory.getItem(deposit[i]);
            BigDecimal value = sellable(item) ? price(item).multiply(BigDecimal.valueOf(item.getAmount())) : BigDecimal.ZERO;
            total = total.add(value);
            if (value.signum() <= 0) set(reward[i], Items.create(string("reward-pane.material", "BLACK_STAINED_GLASS_PANE"), string("reward-pane.name", "&7")));
            else set(reward[i], Items.create(string("reward-pane.sellable-material", "GREEN_STAINED_GLASS_PANE"), string("reward-pane.reward-name", "&a&lReward: $%value%").replace("%value%", money(value))));
        }
        updateButton(buttonSlot("sell-entire-inventory"), string("buttons.sell-entire-inventory.material", "DIAMOND"), string("buttons.sell-entire-inventory.name", "&a&lSell Entire Inventory"), totalLore("buttons.sell-entire-inventory.lore", "&7Total Value: &a&l$%value%", calculateInventorySellPrice()));
        updateButton(buttonSlot("confirm"), string("buttons.confirm.material", "PAPER"), string("buttons.confirm.name", "&a&lConfirm"), totalLore("buttons.confirm.lore", "&7Total Reward: &a&l$%value%", normalizeMoney(total)));
    }
    private void updateButton(int slot, String material, String name, String lore) { set(slot, Items.create(material, name, lore)); }
    public BigDecimal calculateInventorySellPrice() {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (sellable(item)) total = total.add(price(item).multiply(BigDecimal.valueOf(item.getAmount())));
        }
        return normalizeMoney(total);
    }
    private BigDecimal depositTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (int slot : deposit) {
            ItemStack item = inventory.getItem(slot);
            if (sellable(item)) total = total.add(price(item).multiply(BigDecimal.valueOf(item.getAmount())));
        }
        return normalizeMoney(total);
    }
    private void openSellConfirmation() {
        if (calculateInventorySellPrice().signum() <= 0) {
            player.sendMessage(plugin.store().message(player, "trash.no-sellable-items"));
            return;
        }
        transitioning = true;
        open(new TrashSellConfirmationMenu(plugin, player, this));
    }
    private void confirm() {
        if (processingDeposit) return;
        BigDecimal total = depositTotal();
        if (total.signum() <= 0) { player.sendMessage(plugin.store().message(player, "trash.no-eligible-items")); return; }
        processingDeposit = true;
        if (!plugin.economy().isAvailable() || !plugin.economy().deposit(player, total)) {
            processingDeposit = false;
            player.sendMessage(plugin.store().message(player, "trash.transaction-failed"));
            return;
        }
        for (int slot : deposit) if (sellable(inventory.getItem(slot))) inventory.setItem(slot, null);
        returnDeposits();
        settled = true;
        player.sendMessage(plugin.store().message(player, "trash.items-sold", "%amount%", money(total)));
        player.closeInventory();
    }
    boolean sellable(ItemStack item) { if (item == null || item.getType().isAir()) return false; String key = com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name(); return price(item).signum() > 0 && !plugin.store().protection().getStringList("protection.protected-items").stream().map(String::toUpperCase).anyMatch(key::equals); }
    BigDecimal price(ItemStack item) {
        if (item == null) return BigDecimal.ZERO;
        ItemPrice stored = plugin.itemPriceDatabase().getPrice(item);
        return stored == null ? BigDecimal.ZERO : durabilityPrice(item, stored.getBasePrice());
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
    private BigDecimal durabilityPrice(ItemStack item, BigDecimal basePrice) {
        if (basePrice.signum() <= 0) return BigDecimal.ZERO;
        ItemMeta meta = item.getItemMeta();
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0 || !(meta instanceof Damageable damageable) || meta.isUnbreakable()) return basePrice;
        long remaining = Math.max(0, maxDurability - damageable.getDamage());
        BigDecimal multiplier = BigDecimal.valueOf(remaining).divide(BigDecimal.valueOf(maxDurability), 1, RoundingMode.DOWN);
        return basePrice.multiply(multiplier.max(BigDecimal.ZERO).min(BigDecimal.ONE));
    }
    private BigDecimal normalizeMoney(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(normalizeMoney(value));
    }
    private void returnDeposits() { for (int slot : deposit) { ItemStack item = inventory.getItem(slot); if (item != null) { java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item); leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)); inventory.setItem(slot, null); } } }
    private boolean hasDepositContents() { for (int slot : deposit) { ItemStack item = inventory.getItem(slot); if (item != null && !item.getType().isAir()) return true; } return false; }
    private void retainForPlayer() { settled = false; plugin.guis().reopen(this); }
    @Override public void handleClose(InventoryCloseEvent event) {
        if (transitioning) { transitioning = false; return; }
        if (settled) return;
        settled = true;
        if (!hasDepositContents()) return;
        BigDecimal total = depositTotal();
        if (total.signum() <= 0 || !plugin.economy().isAvailable() || !plugin.economy().deposit(player, total)) {
            if (player.isOnline()) player.sendMessage(plugin.store().message(player, total.signum() <= 0 ? "trash.no-eligible-items" : "trash.transaction-failed"));
            retainForPlayer();
            return;
        }
        for (int slot : deposit) if (sellable(inventory.getItem(slot))) inventory.setItem(slot, null);
        if (player.isOnline()) player.sendMessage(plugin.store().message(player, "trash.items-sold", "%amount%", money(total)));
        if (hasDepositContents()) {
            if (player.isOnline()) player.sendMessage(plugin.store().message(player, "trash.no-eligible-items"));
            retainForPlayer();
        }
    }

    private static String string(PacificaPlugin plugin, String path, String fallback) {
        ConfigurationSection section = plugin.store().trashcan().getConfigurationSection("trashcan-gui");
        return section == null ? fallback : section.getString(path, fallback);
    }
    private String string(String path, String fallback) { return settings == null ? fallback : settings.getString(path, fallback); }
    private int buttonSlot(String button) { return settings == null ? (button.equals("cancel") ? 44 : button.equals("confirm") ? 53 : 52) : settings.getInt("buttons." + button + ".slot", button.equals("cancel") ? 44 : button.equals("confirm") ? 53 : 52); }
    private String totalLore(String path, String fallback, BigDecimal value) { return string(path, fallback).replace("%value%", money(value)); }
    private static int[] slots(ConfigurationSection section, String path, int[] fallback) {
        if (section == null) return fallback.clone();
        List<Integer> values = section.getIntegerList(path);
        if (values.size() != fallback.length) return fallback.clone();
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}