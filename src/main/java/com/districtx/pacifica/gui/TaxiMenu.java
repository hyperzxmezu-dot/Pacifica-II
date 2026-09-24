package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.taxi.TaxiAPI;
import com.districtx.pacifica.api.taxi.TaxiGUIProvider;
import com.districtx.pacifica.api.taxi.TaxiReason;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.districtx.pacifica.service.TeleportManager.TeleportContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TaxiMenu extends BaseMenu {
    private final Map<Integer, TaxiGUIProvider> providerSlots = new HashMap<>();

    public TaxiMenu(PacificaPlugin plugin, Player player) {
        super(plugin, player, string(plugin, "title", "&7&lTaxi"), 0);
        ConfigurationSection section = plugin.store().taxi().getConfigurationSection("taxi-gui");
        mainButton(integer(section, "buttons.spawn.slot", 13), string(section, "buttons.spawn.material", "BED"), string(section, "buttons.spawn.name", "&a&lSpawn"), string(section, "buttons.spawn.lore", "&7Teleport to spawn!"));
        mainButton(integer(section, "buttons.quick-play.slot", 11), string(section, "buttons.quick-play.material", "EMERALD"), string(section, "buttons.quick-play.name", "&a&lQuick Play"), string(section, "buttons.quick-play.lore", "&7Teleport to a random location!"));
        mainButton(integer(section, "buttons.warps.slot", 31), string(section, "buttons.warps.material", "ENDER_PEARL"), string(section, "buttons.warps.name", "&e&lWarps"), string(section, "buttons.warps.lore", "&7Browse the warp list!"));
        mainButton(integer(section, "buttons.player.slot", 29), string(section, "buttons.player.material", "PLAYER_HEAD"), string(section, "buttons.player.name", "&e&lPlayer"), string(section, "buttons.player.lore", "&7Send a teleport request"));
        mainButton(integer(section, "buttons.house.slot", 15), string(section, "buttons.house.material", "IRON_DOOR"), string(section, "buttons.house.name", "&1House"), string(section, "buttons.house.lore", "&7Travel to one of your houses!"));
        addProviders();
    }

    @Override public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory || event.getCurrentItem() == null) return;
        int slot = event.getSlot();
        TaxiGUIProvider provider = providerSlots.get(slot);
        if (provider != null) {
            try {
                provider.execute(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Taxi GUI provider " + provider.getClass().getName() + " failed: " + exception.getMessage());
            }
            return;
        }
        if (slot == buttonSlot("spawn", 13)) {
                plugin.teleports().start(player, plugin.store().spawn() == null ? player.getWorld().getSpawnLocation() : plugin.store().spawn(), true, TeleportContext.TAXI, Collections.emptyMap(), TaxiReason.SPAWN);
                player.closeInventory();
        } else if (slot == buttonSlot("quick-play", 11)) {
                String name = plugin.warps().randomName();
                if (name == null) { player.sendMessage(plugin.store().message(player, "warp.no-random")); return; }
                plugin.teleports().start(player, plugin.warps().get(name), false, TeleportContext.TAXI, Collections.singletonMap("%warp%", name), TaxiReason.QUICK_PLAY);
                player.closeInventory();
        } else if (slot == buttonSlot("warps", 31)) {
            open(new WarpMenu(plugin, player, 0, true));
        } else if (slot == buttonSlot("player", 29)) {
            open(new PlayerMenu(plugin, player, 0));
        } else if (slot == buttonSlot("house", 15)) {
            if (plugin.wasted().isWasted(player)) player.sendMessage(plugin.store().message(player, "wasted.command-blocked"));
            else if (plugin.combatTags().isTagged(player)) player.sendMessage(plugin.store().message(player, "combattag.command-blocked"));
            else {
                plugin.teleports().requestExternalProtection(player);
                player.performCommand("myhouses");
            }
        }
    }

    private void addProviders() {
        TaxiAPI api = Bukkit.getServicesManager().load(TaxiAPI.class);
        if (api == null || !api.isAvailable()) return;
        int[] candidates = {20, 21, 22, 23, 24, 30, 32, 33, 38, 39, 40, 41, 42};
        Set<Integer> reserved = new HashSet<>();
        reserved.add(buttonSlot("spawn", 13));
        reserved.add(buttonSlot("quick-play", 11));
        reserved.add(buttonSlot("warps", 31));
        reserved.add(buttonSlot("player", 29));
        reserved.add(buttonSlot("house", 15));
        int index = 0;
        for (TaxiGUIProvider provider : api.getGUIProviders()) {
            while (index < candidates.length && reserved.contains(candidates[index])) index++;
            if (index >= candidates.length) break;
            try {
                ItemStack icon = provider.getIcon(player);
                if (icon == null) continue;
                icon = icon.clone();
                ItemMeta meta = icon.getItemMeta();
                String name = provider.getDisplayName();
                if (meta != null && name != null && !name.isBlank()) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                    icon.setItemMeta(meta);
                }
                int slot = candidates[index++];
                reserved.add(slot);
                inventory.setItem(slot, icon);
                providerSlots.put(slot, provider);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Unable to render Taxi GUI provider " + provider.getClass().getName() + ": " + exception.getMessage());
            }
        }
    }

    private static String string(PacificaPlugin plugin, String path, String fallback) {
        ConfigurationSection section = plugin.store().taxi().getConfigurationSection("taxi-gui");
        return string(section, path, fallback);
    }
    private static String string(ConfigurationSection section, String path, String fallback) { return section == null ? fallback : section.getString(path, fallback); }
    private static int integer(ConfigurationSection section, String path, int fallback) { return section == null ? fallback : section.getInt(path, fallback); }
    private int buttonSlot(String button, int fallback) {
        ConfigurationSection section = plugin.store().taxi().getConfigurationSection("taxi-gui");
        return integer(section, "buttons." + button + ".slot", fallback);
    }
}