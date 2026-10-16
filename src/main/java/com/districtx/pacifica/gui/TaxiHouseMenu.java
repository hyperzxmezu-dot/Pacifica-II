package com.districtx.pacifica.gui;

import com.districtx.pacifica.PacificaPlugin;
import com.districtx.pacifica.api.taxi.TaxiReason;
import com.districtx.pacifica.api.taxi.TaxiResult;
import com.districtx.pacifica.util.Items;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class TaxiHouseMenu extends BaseMenu {
    private static final int BACK_SLOT = 45;
    private static final int[] WHITE_SLOTS = {0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53};
    private static final int[] BLACK_SLOTS = {1, 2, 3, 4, 5, 6, 7, 10, 16, 19, 25, 28, 34, 37, 43, 46, 52};

    private final TaxiMenu previousMenu;
    private final Object housing;
    private final NamespacedKey houseIdKey;
    private final Map<Integer, String> houseIdsBySlot = new HashMap<>();

    public TaxiHouseMenu(PacificaPlugin plugin, Player player, TaxiMenu previousMenu) {
        super(plugin, player, title(plugin), 0);
        this.previousMenu = previousMenu;
        this.housing = loadHousingApi();
        this.houseIdKey = new NamespacedKey(plugin, "taxi-house-id");
        populateHouses();
        set(BACK_SLOT, Items.create("REDSTONE", "&c&lBack"));
    }

    boolean isHousingAvailable() {
        return housing != null;
    }

    @Override
    protected void frame() {
        ItemStack light = Items.create("LIGHT_GRAY_STAINED_GLASS_PANE", "&f");
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, light.clone());
        ItemStack white = Items.create("WHITE_STAINED_GLASS_PANE", "&f");
        for (int slot : WHITE_SLOTS) inventory.setItem(slot, white.clone());
        ItemStack black = Items.create("BLACK_STAINED_GLASS_PANE", "&f");
        for (int slot : BLACK_SLOTS) inventory.setItem(slot, black.clone());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory || event.getCurrentItem() == null
                || !event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;

        int slot = event.getSlot();
        if (slot == BACK_SLOT) {
            player.closeInventory();
            plugin.guis().open(previousMenu);
            return;
        }

        String houseId = houseIdsBySlot.get(slot);
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (houseId == null || meta == null
                || !houseId.equals(meta.getPersistentDataContainer().get(houseIdKey, PersistentDataType.STRING))) return;
        startHouseTeleport(houseId);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    private void populateHouses() {
        if (housing == null || !housingEnabled()) return;
        try {
            Object houseService = invoke(housing, "houses", new Class<?>[0]);
            Object ownedHouses = invoke(houseService, "getHousesByOwner", new Class<?>[]{UUID.class}, player.getUniqueId());
            if (!(ownedHouses instanceof Iterable<?> houses)) return;
            List<Integer> slots = houseDisplaySlots();
            int index = 0;
            for (Object house : houses) {
                if (index >= slots.size()) break;
                String houseId = (String) invoke(house, "getId", new Class<?>[0]);
                if (!isOwnedBy(house, player.getUniqueId()) || houseId == null || houseId.isBlank()) continue;

                ItemStack item = houseItem(house, houseId);
                if (item == null) continue;
                int slot = slots.get(index++);
                inventory.setItem(slot, item);
                houseIdsBySlot.put(slot, houseId);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Unable to load Pacifica-Housing houses: " + exception.getMessage());
        }
    }

    private ItemStack houseItem(Object house, String houseId) throws ReflectiveOperationException {
        String type = String.valueOf(invoke(house, "getType", new Class<?>[0]));
        String material;
        String typeName;
        switch (type) {
            case "REGULAR" -> { material = "OAK_DOOR"; typeName = "Regular"; }
            case "PREMIUM" -> { material = "IRON_DOOR"; typeName = "Premium"; }
            case "LUXURY" -> { material = "CRIMSON_DOOR"; typeName = "Luxury"; }
            default -> { return null; }
        }
        String name = String.valueOf(invoke(house, "getName", new Class<?>[0]));
        double price = ((Number) invoke(house, "getPrice", new Class<?>[0])).doubleValue();
        Object vaultService = invoke(housing, "vaults", new Class<?>[0]);
        int vaultCount = ((Number) invoke(vaultService, "getVaultCount", new Class<?>[]{String.class}, houseId)).intValue();
        ItemStack item = Items.create(material, "&9&lHouse " + name,
                "&7Cost: &a$" + BigDecimal.valueOf(price).stripTrailingZeros().toPlainString(),
                "&7Chests: &6" + vaultCount,
                "&7House Type: &6" + typeName,
                "&7",
                "&a&lClick to teleport");
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(houseIdKey, PersistentDataType.STRING, houseId);
        item.setItemMeta(meta);
        return item;
    }

    private void startHouseTeleport(String houseId) {
        if (housing == null || !housingEnabled() || !player.isOnline()) return;
        if (plugin.wasted().isWasted(player)) {
            player.sendMessage(plugin.store().message(player, "wasted.command-blocked"));
            return;
        }

        try {
            Object houseService = invoke(housing, "houses", new Class<?>[0]);
            Optional<?> found = (Optional<?>) invoke(houseService, "getHouse", new Class<?>[]{String.class}, houseId);
            Object house = found.orElse(null);
            UUID playerId = player.getUniqueId();
            boolean ownsHouse = (Boolean) invoke(houseService, "ownsHouse", new Class<?>[]{UUID.class, String.class}, playerId, houseId);
            if (!isOwnedBy(house, playerId) || !ownsHouse) {
                destinationUnavailable();
                return;
            }
            Location destination = destination(house);
            if (!hasLoadedWorld(destination)) {
                destinationUnavailable();
                return;
            }
            if (plugin.teleports().isTeleporting(player)) return;

            if (plugin.teleports().startApi(player, destination, TaxiReason.HOUSE) == TaxiResult.SUCCESS) {
                player.closeInventory();
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Unable to validate Pacifica-Housing house: " + exception.getMessage());
            destinationUnavailable();
        }
    }

    private Location destination(Object house) throws ReflectiveOperationException {
        if (house == null) return null;
        Object teleportLocation = invoke(house, "getTeleportLocation", new Class<?>[0]);
        if (teleportLocation instanceof Location location && hasLoadedWorld(location)) return location;
        Object doorsObject = invoke(house, "getDoors", new Class<?>[0]);
        if (!(doorsObject instanceof List<?> doors) || doors.isEmpty()) return null;
        Object inside = invoke(doors.get(0), "getInsideLocation", new Class<?>[0]);
        return inside instanceof Location location ? location : null;
    }

    private boolean isOwnedBy(Object house, UUID playerId) throws ReflectiveOperationException {
        return house != null
                && (Boolean) invoke(house, "isOwned", new Class<?>[0])
                && playerId.equals(invoke(house, "getOwnerUUID", new Class<?>[0]));
    }

    private boolean hasLoadedWorld(Location location) {
        return location != null && location.getWorld() != null
                && Bukkit.getWorld(location.getWorld().getUID()) != null;
    }

    private void destinationUnavailable() {
        player.sendMessage(plugin.store().message(player, "teleport.destination-unavailable"));
    }

    private List<Integer> houseDisplaySlots() {
        Set<Integer> unavailable = new HashSet<>();
        Arrays.stream(WHITE_SLOTS).forEach(unavailable::add);
        Arrays.stream(BLACK_SLOTS).forEach(unavailable::add);
        unavailable.add(BACK_SLOT);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 54; slot++) {
            if (!unavailable.contains(slot)) slots.add(slot);
        }
        return slots;
    }

    private boolean housingEnabled() {
        Plugin housingPlugin = Bukkit.getPluginManager().getPlugin("Pacifica-Housing");
        return housingPlugin != null && housingPlugin.isEnabled();
    }

    private Object loadHousingApi() {
        Plugin housingPlugin = Bukkit.getPluginManager().getPlugin("Pacifica-Housing");
        if (housingPlugin == null || !housingPlugin.isEnabled()) return null;
        try {
            Class<?> apiClass = housingPlugin.getClass().getClassLoader()
                    .loadClass("com.districtx.housing.api.PacificaHousingAPI");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            return registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException | IllegalStateException exception) {
            plugin.getLogger().warning("Pacifica-Housing API is unavailable: " + exception.getMessage());
            return null;
        }
    }

    private Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        if (target == null) throw new IllegalStateException("Pacifica-Housing API returned no service for " + name);
        Method method = target.getClass().getMethod(name, parameterTypes);
        if (!method.canAccess(target)) method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static String title(PacificaPlugin plugin) {
        return plugin.store().taxi().getString("taxi-gui.house-selection.title", "&2&lGrab Service: &9&lHouses");
    }
}