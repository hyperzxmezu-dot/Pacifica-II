package com.districtx.pacifica.gui;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiManager {
    private final Map<UUID, BaseMenu> reopenOnJoin = new HashMap<>();

    public void open(BaseMenu menu) {
        menu.player.openInventory(menu.getInventory());
    }
    public void reopen(BaseMenu menu) {
        UUID playerId = menu.player.getUniqueId();
        reopenOnJoin.put(playerId, menu);
        Bukkit.getScheduler().runTask(menu.plugin, () -> {
            if (reopenOnJoin.get(playerId) != menu || !menu.player.isOnline()) return;
            reopenOnJoin.remove(playerId);
            open(menu);
        });
    }
    public void joined(Player player) {
        BaseMenu menu = reopenOnJoin.remove(player.getUniqueId());
        if (menu != null) open(menu);
    }
    public void click(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu menu) menu.handleClick(event);
    }
    public void close(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BaseMenu menu) menu.handleClose(event);
    }
    public void drag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu menu) menu.handleDrag(event);
    }
}