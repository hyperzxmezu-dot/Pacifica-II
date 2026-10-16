package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.supplydrops.SupplyDropRarity;
import com.districtx.pacifica.util.Items;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SupplyMenu extends BaseMenu {
    private static final int[] SUPPLY_SLOTS = {20, 21, 22, 23, 24};
    private final NamespacedKey rarityKey;
    private final Map<Integer, SupplyDropRarity> entries = new HashMap<>();

    public SupplyMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, "&2&lSupply Drops", 0);
        rarityKey = new NamespacedKey(plugin, "supply_gui_rarity");
        render();
    }

    private void render() {
        entries.clear();
        SupplyDropRarity[] rarities = SupplyDropRarity.values();
        for (int index = 0; index < rarities.length; index++) {
            SupplyDropRarity rarity = rarities[index];
            int amount = plugin.supplyDrops().getAmount(player.getUniqueId(), rarity);
            String display = plugin.supplyDrops().rarityDisplay(rarity);
            ItemStack item = Items.create("CHEST", "&a&lSupply " + display,
                    "&7Available: &e" + amount,
                    "&7Level: &f" + display,
                    amount == 0 ? "&cNo supplies remaining." : "&aClick to summon this supply drop");
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity.key());
            item.setItemMeta(meta);
            int slot = SUPPLY_SLOTS[index];
            entries.put(slot, rarity);
            set(slot, item);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        int slot = event.getRawSlot();
        SupplyDropRarity rarity = entries.get(slot);
        ItemStack item = event.getCurrentItem();
        if (rarity == null || item == null || !item.hasItemMeta()) return;
        String itemRarity = item.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        if (!rarity.key().equals(itemRarity)) return;
        if (plugin.supplyDrops().getAmount(player.getUniqueId(), rarity) < 1) {
            player.sendMessage(Items.color("&cYou have no " + plugin.supplyDrops().rarityDisplay(rarity) + " &csupplies remaining."));
            render();
            return;
        }
        if (plugin.supplyDrops().summon(player, rarity)) {
            player.sendMessage(Items.color("&aYour " + plugin.supplyDrops().rarityDisplay(rarity) + " &aSupplyDrop is on its way."));
        } else {
            player.sendMessage(Items.color("&cYou cannot summon that SupplyDrop here."));
        }
        render();
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }
}