package com.districtx.pacifica.api;

import com.districtx.pacifica.api.tag.TagRarity;
import com.districtx.pacifica.api.tag.TagSnapshot;
import com.districtx.pacifica.api.tag.TagSource;
import com.districtx.pacifica.api.taxi.TaxiReason;
import com.districtx.pacifica.api.taxi.TaxiRequest;
import com.districtx.pacifica.api.taxi.TaxiResult;
import com.districtx.pacifica.api.taxi.TeleportRequest;
import java.util.UUID;
import org.bukkit.Location;

public final class PublicApiModelTest {
    public static void main(String[] args) {
        teleportSnapshotsDefensivelyCopyDestination();
        taxiRequestRetainsTaxiReasonAndApiResultIsTyped();
        tagSnapshotExposesOnlyItsImmutableDefinitionValues();
        unavailableServicesFailSafelyWithoutPacificasServerRegistration();
    }

    private static void teleportSnapshotsDefensivelyCopyDestination() {
        Location original = new Location(null, 10.0, 64.0, -5.0);
        TeleportRequest request = new TeleportRequest(UUID.randomUUID(), original, 4, TaxiReason.TAXI);

        original.setX(20.0);
        Location returned = request.getDestination();
        returned.setZ(99.0);

        require(request.getDestination().getX() == 10.0, "teleport destination must be copied");
        require(request.getDestination().getZ() == -5.0, "returned destination must be copied");
        require(request.getRemainingSeconds() == 4, "remaining seconds must be retained");
    }

    private static void taxiRequestRetainsTaxiReasonAndApiResultIsTyped() {
        TaxiRequest request = new TaxiRequest(UUID.randomUUID(), new Location(null, 1.0, 2.0, 3.0), 8, TaxiReason.TAXI);

        require(request.getReason() == TaxiReason.TAXI, "Taxi request must retain its reason");
        require(TaxiResult.SUCCESS == TaxiResult.valueOf("SUCCESS"), "Taxi outcomes must be typed");
    }

    private static void tagSnapshotExposesOnlyItsImmutableDefinitionValues() {
        TagSnapshot tag = new TagSnapshot("champion", "&6Champion", TagRarity.LEGENDARY, TagSource.EVENT, true);

        require("champion".equals(tag.getId()), "tag ID must be retained");
        require("&6Champion".equals(tag.getDisplayName()), "tag display name must be retained");
        require(tag.getRarity() == TagRarity.LEGENDARY, "tag rarity must be retained");
        require(tag.getSource() == TagSource.EVENT, "tag source must be retained");
        require(tag.isEnabled(), "enabled state must be retained");
        require(!new TagSnapshot("disabled", "Disabled", TagRarity.COMMON, TagSource.COSMETICS, false).isEnabled(),
                "disabled state must be retained");
    }

    private static void unavailableServicesFailSafelyWithoutPacificasServerRegistration() {
        require(!PacificaAPI.isAvailable(), "API must be unavailable without registered services");
        require(!PacificaAPI.taxi().isAvailable(), "Taxi fallback must be unavailable");
        require(PacificaAPI.taxi().requestTaxi(null, null) == TaxiResult.DISABLED, "Taxi fallback must fail safely");
        require(PacificaAPI.taxi().getTeleportRequest(null).isEmpty(), "Taxi fallback must return an empty request");
        require(!PacificaAPI.combatTag().isInCombat(null), "Combat fallback must fail safely");
        require(PacificaAPI.tags().getTags().isEmpty(), "Tag fallback must return no tags");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}