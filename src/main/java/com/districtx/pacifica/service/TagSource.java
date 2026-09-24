package com.districtx.pacifica.service;

import java.util.Locale;

public enum TagSource {
    EVENT,
    COSMETICS;

    public static TagSource parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}