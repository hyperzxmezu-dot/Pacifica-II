package com.districtx.pacifica.listener;

import com.districtx.pacifica.PacificaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public final class BackpackListener implements Listener {
    private final PacificaPlugin plugin;
    public BackpackListener(PacificaPlugin plugin) { this.plugin = plugin; }

    @EventHandler public void join(PlayerJoinEvent event) { plugin.backpacks().ensure(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(plugin.backpacks()::isItem);
        event.getEntity().getInventory().setItem(17, null);
    }
    @EventHandler public void respawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        plugin.backpacks().ensure(event.getPlayer());
    }
    @EventHandler public void drop(PlayerDropItemEvent event) { if (plugin.backpacks().isItem(event.getItemDrop().getItemStack())) event.setCancelled(true); }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == player.getInventory() && event.getSlot() == 17) {
            event.setCancelled(true);
            if (plugin.backpacks().isItem(event.getCurrentItem())) plugin.backpacks().open(player);
        } else if (plugin.backpacks().isItem(event.getCurrentItem()) || plugin.backpacks().isItem(event.getCursor())
                || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                    && plugin.backpacks().isItem(player.getInventory().getItem(event.getHotbarButton()))) event.setCancelled(true);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (plugin.backpacks().isItem(event.getOldCursor()) || event.getRawSlots().stream().anyMatch(slot -> slot >= event.getView().getTopInventory().getSize() && slot - event.getView().getTopInventory().getSize() == 17)) event.setCancelled(true);
    }
}