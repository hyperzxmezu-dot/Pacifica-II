package com.districtx.pacifica.service;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.gui.BackpackMenu;
import com.districtx.pacifica.util.Items;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import com.cryptomorin.xseries.XMaterial;

public final class BackpackManager {
    private final PacificaPlugin plugin;

    public BackpackManager(PacificaPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) { plugin.guis().open(new BackpackMenu(plugin, player, 0)); }

    public int capacity(Player player) {
        int rows = 0;
        for (String permission : plugin.store().backpack().getStringList("backpack.size-permissions")) {
            String[] values = permission.split(":", 2);
            if (values.length == 2 && player.hasPermission(values[0])) rows = Math.max(rows, Integer.parseInt(values[1]));
        }
        return Math.min(63, Math.max(27, rows * 9));
    }

    public ItemStack[] contents(Player player) {
        int size = capacity(player);
        ItemStack[] result = new ItemStack[size];
        List<?> values = plugin.store().data().getList("backpacks." + player.getUniqueId(), new ArrayList<>());
        if (values != null) for (int i = 0; i < Math.min(size, values.size()); i++) if (values.get(i) instanceof ItemStack item) result[i] = item;
        return result;
    }

    public void save(Player player, Inventory inventory, int offset, int capacity) {
        ItemStack[] contents = contents(player);
        for (int i = 0; i < 54 && offset + i < capacity; i++) contents[offset + i] = inventory.getItem(i);
        List<ItemStack> values = new ArrayList<>();
        for (ItemStack item : contents) values.add(item);
        plugin.store().data().set("backpacks." + player.getUniqueId(), values);
        plugin.store().save();
    }

    public ItemStack item() { return Items.create("CHEST", "&6&lBackpack", "&aClick to open backpack!"); }

    public boolean isItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return false;
        return XMaterial.matchXMaterial(item.getType()).name().equals("CHEST") && item.getItemMeta().getDisplayName().equals(Items.color("&6&lBackpack"));
    }

    public void ensure(Player player) { player.getInventory().setItem(17, item()); }
}