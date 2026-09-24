package com.districtx.pacifica;

import com.districtx.pacifica.api.taxi.TaxiAPI;
import com.districtx.pacifica.api.taxi.TaxiGUI;
import com.districtx.pacifica.api.taxi.TaxiGUIProvider;
import com.districtx.pacifica.api.taxi.TaxiReason;
import com.districtx.pacifica.api.taxi.TaxiRequest;
import com.districtx.pacifica.api.taxi.TaxiResult;
import com.districtx.pacifica.api.taxi.TeleportRequest;
import com.districtx.pacifica.gui.TaxiMenu;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Internal service adapter registered for Pacifica-II API consumers. */
final class PacificaTaxiService implements TaxiAPI {
    private final PacificaPlugin plugin;
    private final Map<String, TaxiGUIProvider> guiProviders = new LinkedHashMap<>();

    /** @param plugin owning Pacifica-II plugin instance */
    PacificaTaxiService(PacificaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean isAvailable() { return plugin.isEnabled(); }

    @Override public boolean isTeleporting(Player player) {
        return isAvailable() && plugin.teleports().isTeleporting(player);
    }

    @Override public Optional<TeleportRequest> getTeleportRequest(Player player) {
        if (!isAvailable()) return Optional.empty();
        return plugin.teleports().getTeleportRequest(player);
    }

    @Override public Optional<TaxiRequest> getTaxiRequest(Player player) {
        if (!isAvailable()) return Optional.empty();
        return plugin.teleports().getTaxiRequest(player);
    }

    @Override public void cancelTeleport(Player player) {
        if (isAvailable() && player != null) plugin.teleports().cancel(player, null);
    }

    @Override public void cancelTaxi(Player player) {
        if (isAvailable() && player != null && getTaxiRequest(player).isPresent()) plugin.teleports().cancel(player, null);
    }

    @Override public TaxiResult requestTeleport(Player player, Location destination) {
        return request(player, destination, TaxiReason.OTHER);
    }

    @Override public TaxiResult requestTeleport(Player player, Location destination, TaxiReason reason) {
        return request(player, destination, reason == null ? TaxiReason.OTHER : reason);
    }

    @Override public TaxiResult requestTaxi(Player player, Location destination) {
        return request(player, destination, TaxiReason.TAXI);
    }

    private TaxiResult request(Player player, Location destination, TaxiReason reason) {
        if (!isAvailable()) return TaxiResult.DISABLED;
        if (player == null || !player.isOnline()) return TaxiResult.PLAYER_NOT_FOUND;
        if (destination == null || destination.getWorld() == null) return TaxiResult.INVALID_DESTINATION;
        if (plugin.teleports().isTeleporting(player)) return TaxiResult.ALREADY_TELEPORTING;
        return plugin.teleports().startApi(player, destination, reason);
    }

    @Override public boolean isTaxiGUI(Player player) { return findOpenGui(player) != null; }

    @Override public Optional<TaxiGUI> getOpenTaxiGUI(Player player) {
        TaxiMenu menu = findOpenGui(player);
        return menu == null ? Optional.empty() : Optional.of(new TaxiGUIHandle(player, menu));
    }

    @Override public boolean registerGUIProvider(TaxiGUIProvider provider) {
        if (!isAvailable() || provider == null) return false;
        try {
            String id = provider.getId();
            if (id == null) return false;
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9._-]{1,64}") || guiProviders.containsKey(normalized)) return false;
            guiProviders.put(normalized, provider);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override public boolean unregisterGUIProvider(String id) {
        return isAvailable() && id != null && guiProviders.remove(id.trim().toLowerCase(Locale.ROOT)) != null;
    }

    @Override public Collection<TaxiGUIProvider> getGUIProviders() { return List.copyOf(guiProviders.values()); }

    private TaxiMenu findOpenGui(Player player) {
        if (!isAvailable() || player == null || !player.isOnline()) return null;
        return player.getOpenInventory().getTopInventory().getHolder() instanceof TaxiMenu menu ? menu : null;
    }

    private final class TaxiGUIHandle implements TaxiGUI {
        private final Player player;
        private final TaxiMenu menu;

        private TaxiGUIHandle(Player player, TaxiMenu menu) {
            this.player = player;
            this.menu = menu;
        }

        @Override public Player getPlayer() { return player; }
        @Override public String getId() { return "taxi"; }
        @Override public void open() { if (isAvailable()) plugin.guis().open(menu); }
        @Override public void close() { if (isOpen()) player.closeInventory(); }
        @Override public boolean isOpen() {
            return isAvailable() && player.getOpenInventory().getTopInventory().getHolder() == menu;
        }
    }
}