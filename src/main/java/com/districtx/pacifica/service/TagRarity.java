package com.districtx.pacifica.service;

import java.util.Locale;

public enum TagRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY;

    public static TagRarity parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}