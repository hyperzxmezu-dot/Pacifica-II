package com.districtx.pacifica.service;

import java.math.BigDecimal;
import java.util.Map;

final class JobsWantedRules {
    private JobsWantedRules() { }

    static int starsForKills(int qualifyingKills, Map<Integer, Integer> thresholds, int maxStars) {
        if (qualifyingKills <= 0 || maxStars <= 0) return 0;
        int stars = 0;
        for (Map.Entry<Integer, Integer> entry : thresholds.entrySet()) {
            if (entry.getKey() > 0 && qualifyingKills >= entry.getKey()) stars = Math.max(stars, entry.getValue());
        }
        return Math.max(0, Math.min(maxStars, stars));
    }

    static int copKillLevel(int currentStars, int addedStars, int maxStars) {
        if (maxStars <= 0) return 0;
        long total = (long) Math.max(0, currentStars) + Math.max(0, addedStars);
        return (int) Math.min(maxStars, total);
    }

    static boolean isValidBounty(BigDecimal amount, BigDecimal maximum) {
        return amount != null && maximum != null && amount.signum() > 0
                && maximum.signum() > 0 && amount.compareTo(maximum) <= 0;
    }

    static boolean meetsBribeMinimum(BigDecimal amount, BigDecimal minimum) {
        return amount != null && minimum != null && amount.signum() > 0 && minimum.signum() >= 0
                && amount.compareTo(minimum) >= 0;
    }
}