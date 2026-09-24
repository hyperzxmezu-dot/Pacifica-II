package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.taxi.TaxiReason;
import com.districtx.pacifica.util.Items;
import com.districtx.pacifica.service.TeleportManager.TeleportContext;
import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class WarpMenu extends BaseMenu {
    private static final int[] CONTENT = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42, 47, 48, 49, 50, 51};
    private final List<String> names;
    private final boolean taxi;
    public WarpMenu(PacificaPlugin plugin, Player player, int page) {
        this(plugin, player, page, false);
    }
    public WarpMenu(PacificaPlugin plugin, Player player, int page, boolean taxi) {
        super(plugin, player, "&7&lWarp List", page);
        this.taxi = taxi;
        names = plugin.warps().names();
        int start = page * CONTENT.length;
        for (int i = 0; i < CONTENT.length && start + i < names.size(); i++) {
            String name = names.get(start + i);
            ItemStack item = Items.create("ENDER_PEARL", "&e&l" + name, "&7Click to teleport");
            if (plugin.store().data().getInt("warp-uses." + name, 0) >= plugin.store().warp().getInt("popular-warp-uses", 25)) {
                Items.unbreaking(item);
                item.setItemMeta(item.getItemMeta());
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                java.util.List<String> lore = meta.getLore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.getLore());
                lore.add(Items.color("&6&lPOPULAR WARP")); meta.setLore(lore); item.setItemMeta(meta);
            }
            set(CONTENT[i], item);
        }
        set(45, Items.create("REDSTONE", "&c&lBack", "&7Return to the taxi page!"));
        if ((page + 1) * CONTENT.length < names.size()) set(53, Items.create("ARROW", "&e&lNext Page", "&7Page " + (page + 2)));
    }
    @Override public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (event.getSlot() == 45) { main(); return; }
        if (event.getSlot() == 53 && (page + 1) * CONTENT.length < names.size()) { open(new WarpMenu(plugin, player, page + 1, taxi)); return; }
        for (int i = 0; i < CONTENT.length; i++) if (CONTENT[i] == event.getSlot() && page * CONTENT.length + i < names.size()) {
            String name = names.get(page * CONTENT.length + i);
            plugin.store().data().set("warp-uses." + name, plugin.store().data().getInt("warp-uses." + name, 0) + 1);
            plugin.store().save();
            plugin.teleports().start(player, plugin.warps().get(name), false, taxi ? TeleportContext.TAXI : TeleportContext.NAMED_WARP, Collections.singletonMap("%warp%", name), TaxiReason.WARP);
            player.closeInventory();
            return;
        }
    }
}