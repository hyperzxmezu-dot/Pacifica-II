package com.districtx.pacifica.api;

import com.districtx.pacifica.api.combat.CombatSession;
import com.districtx.pacifica.api.combat.CombatTagAPI;
import com.districtx.pacifica.api.tag.TagAPI;
import com.districtx.pacifica.api.tag.Tag;
import com.districtx.pacifica.api.tag.TagGUI;
import com.districtx.pacifica.api.tag.TagGUIProvider;
import com.districtx.pacifica.api.taxi.TaxiAPI;
import com.districtx.pacifica.api.taxi.TaxiGUI;
import com.districtx.pacifica.api.taxi.TaxiGUIProvider;
import com.districtx.pacifica.api.jobs.JobMenuAPI;
import com.districtx.pacifica.api.supplydrops.SupplyDropsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.Collection;

/**
 * Public entry point for integrations with Pacifica-II.
 *
 * <p>Call {@link #isAvailable()} before accessing Pacifica-II-specific
 * functionality when Pacifica-II is an optional dependency.</p>
 */
public final class PacificaAPI {
    /** The semantic version of the public Pacifica-II API. */
    public static final String API_VERSION = "1.1.0";

    private static final CombatTagAPI UNAVAILABLE_COMBAT = new CombatTagAPI() {
        @Override public boolean isAvailable() { return false; }
        @Override public boolean isInCombat(org.bukkit.entity.Player player) { return false; }
        @Override public java.util.Optional<CombatSession> getCombatSession(org.bukkit.entity.Player player) { return java.util.Optional.empty(); }
        @Override public int getRemainingSeconds(org.bukkit.entity.Player player) { return 0; }
        @Override public void tag(org.bukkit.entity.Player player) { }
        @Override public void tag(org.bukkit.entity.Player player, org.bukkit.entity.Player opponent) { }
        @Override public void untag(org.bukkit.entity.Player player) { }
    };

    private static final TagAPI UNAVAILABLE_TAGS = new TagAPI() {
        @Override public boolean isAvailable() { return false; }
        @Override public java.util.Optional<Tag> getTag(String id) { return java.util.Optional.empty(); }
        @Override public Collection<Tag> getTags() { return java.util.List.of(); }
        @Override public Collection<Tag> getOwnedTags(org.bukkit.entity.Player player) { return java.util.List.of(); }
        @Override public java.util.Optional<Tag> getEquippedTag(org.bukkit.entity.Player player) { return java.util.Optional.empty(); }
        @Override public boolean hasTag(org.bukkit.entity.Player player, String tagId) { return false; }
        @Override public boolean equipTag(org.bukkit.entity.Player player, String tagId) { return false; }
        @Override public boolean unequipTag(org.bukkit.entity.Player player) { return false; }
        @Override public boolean isTagGUI(org.bukkit.entity.Player player) { return false; }
        @Override public java.util.Optional<TagGUI> getOpenGUI(org.bukkit.entity.Player player) { return java.util.Optional.empty(); }
        @Override public boolean registerGUIProvider(TagGUIProvider provider) { return false; }
        @Override public boolean unregisterGUIProvider(String id) { return false; }
        @Override public Collection<TagGUIProvider> getGUIProviders() { return java.util.List.of(); }
    };

    private static final TaxiAPI UNAVAILABLE_TAXI = new TaxiAPI() {
        @Override public boolean isAvailable() { return false; }
        @Override public boolean isTeleporting(org.bukkit.entity.Player player) { return false; }
        @Override public java.util.Optional<com.districtx.pacifica.api.taxi.TeleportRequest> getTeleportRequest(org.bukkit.entity.Player player) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.districtx.pacifica.api.taxi.TaxiRequest> getTaxiRequest(org.bukkit.entity.Player player) { return java.util.Optional.empty(); }
        @Override public void cancelTeleport(org.bukkit.entity.Player player) { }
        @Override public void cancelTaxi(org.bukkit.entity.Player player) { }
        @Override public com.districtx.pacifica.api.taxi.TaxiResult requestTeleport(org.bukkit.entity.Player player, org.bukkit.Location destination) { return com.districtx.pacifica.api.taxi.TaxiResult.DISABLED; }
        @Override public com.districtx.pacifica.api.taxi.TaxiResult requestTeleport(org.bukkit.entity.Player player, org.bukkit.Location destination, com.districtx.pacifica.api.taxi.TaxiReason reason) { return com.districtx.pacifica.api.taxi.TaxiResult.DISABLED; }
        @Override public com.districtx.pacifica.api.taxi.TaxiResult requestTaxi(org.bukkit.entity.Player player, org.bukkit.Location destination) { return com.districtx.pacifica.api.taxi.TaxiResult.DISABLED; }
        @Override public boolean isTaxiGUI(org.bukkit.entity.Player player) { return false; }
        @Override public java.util.Optional<TaxiGUI> getOpenTaxiGUI(org.bukkit.entity.Player player) { return java.util.Optional.empty(); }
        @Override public boolean registerGUIProvider(TaxiGUIProvider provider) { return false; }
        @Override public boolean unregisterGUIProvider(String id) { return false; }
        @Override public Collection<TaxiGUIProvider> getGUIProviders() { return java.util.List.of(); }
    };

    private static final JobMenuAPI UNAVAILABLE_JOB_MENUS = new JobMenuAPI() {
        @Override public boolean isAvailable() { return false; }
        @Override public void openJobMenu(org.bukkit.entity.Player player) { }
        @Override public void openPoliceMenu(org.bukkit.entity.Player player) { }
        @Override public void openMedicMenu(org.bukkit.entity.Player player) { }
        @Override public void openWardenMenu(org.bukkit.entity.Player player) { }
        @Override public void openHitmanMenu(org.bukkit.entity.Player player) { }
        @Override public void openCriminalMenu(org.bukkit.entity.Player player) { }
        @Override public void openPlaceBountiesMenu(org.bukkit.entity.Player player) { }
        @Override public void openChoosePlayerMenu(org.bukkit.entity.Player player) { }
    };

    private static final SupplyDropsAPI UNAVAILABLE_SUPPLY_DROPS = new SupplyDropsAPI() {
        @Override public boolean isAvailable() { return false; }
        @Override public int getAmount(java.util.UUID playerId, com.districtx.pacifica.api.supplydrops.SupplyDropRarity rarity) { return 0; }
        @Override public boolean grantAmount(java.util.UUID playerId, com.districtx.pacifica.api.supplydrops.SupplyDropRarity rarity, int amount) { return false; }
        @Override public boolean removeAmount(java.util.UUID playerId, com.districtx.pacifica.api.supplydrops.SupplyDropRarity rarity, int amount) { return false; }
        @Override public boolean summon(org.bukkit.entity.Player player, com.districtx.pacifica.api.supplydrops.SupplyDropRarity rarity) { return false; }
    };

    private PacificaAPI() { }

    /**
     * Returns whether Pacifica-II is enabled and has published its API services.
     *
     * @return {@code true} when Pacifica-II's public services are registered
     */
    public static boolean isAvailable() {
        return taxi() != UNAVAILABLE_TAXI || combatTag() != UNAVAILABLE_COMBAT || tags() != UNAVAILABLE_TAGS
                || jobMenus() != UNAVAILABLE_JOB_MENUS || supplyDrops() != UNAVAILABLE_SUPPLY_DROPS;
    }

    /**
     * Returns the Taxi API, or a safe unavailable implementation if the plugin
     * is absent or has not registered its service.
     *
     * @return the Taxi API; never {@code null}
     */
    public static TaxiAPI taxi() {
        TaxiAPI service = loadService(TaxiAPI.class);
        return service == null ? UNAVAILABLE_TAXI : service;
    }

    /**
     * Returns the Combat Tag API, or a safe unavailable implementation if the
     * plugin is absent or has not registered its service.
     *
     * @return the Combat Tag API; never {@code null}
     */
    public static CombatTagAPI combatTag() {
        CombatTagAPI service = loadService(CombatTagAPI.class);
        return service == null ? UNAVAILABLE_COMBAT : service;
    }

    /**
     * Returns the Tag API, or a safe unavailable implementation if the plugin
     * is absent or has not registered its service.
     *
     * @return the Tag API; never {@code null}
     */
    public static TagAPI tags() {
        TagAPI service = loadService(TagAPI.class);
        return service == null ? UNAVAILABLE_TAGS : service;
    }

    /**
     * Returns the job-menu API, or a safe unavailable implementation if
     * Pacifica-II is absent or has not registered its service.
     *
     * @return the job-menu API; never {@code null}
     */
    public static JobMenuAPI jobMenus() {
        JobMenuAPI service = loadService(JobMenuAPI.class);
        return service == null ? UNAVAILABLE_JOB_MENUS : service;
    }

    /** Returns the SupplyDrops API, or a safe unavailable implementation. */
    public static SupplyDropsAPI supplyDrops() {
        SupplyDropsAPI service = loadService(SupplyDropsAPI.class);
        return service == null ? UNAVAILABLE_SUPPLY_DROPS : service;
    }

    private static <T> T loadService(Class<T> serviceType) {
        if (Bukkit.getServer() == null) return null;
        RegisteredServiceProvider<T> registration = Bukkit.getServicesManager().getRegistration(serviceType);
        if (registration == null) return null;
        Plugin provider = registration.getPlugin();
        if (provider == null || !provider.isEnabled() || !provider.getName().equals("Pacifica-II")) return null;
        return registration.getProvider();
    }
}