package com.districtx.pacifica.api.supplydrops;

import java.util.Locale;
import java.util.Optional;

public enum SupplyDropRarity {
    COMMON("Common", "&f"),
    UNCOMMON("Uncommon", "&a"),
    RARE("Rare", "&9"),
    EPIC("Epic", "&5"),
    LEGENDARY("Legendary", "&6");

    private final String displayName;
    private final String color;

    SupplyDropRarity(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return displayName; }
    public String color() { return color; }

    public static Optional<SupplyDropRarity> parse(String value) {
        if (value == null) return Optional.empty();
        try { return Optional.of(valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException exception) { return Optional.empty(); }
    }
}