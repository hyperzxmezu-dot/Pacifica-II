package com.districtx.pacifica.service;

import com.districtx.pacifica.api.jobs.WantedDisplayAPI;
import java.math.BigDecimal;
import java.util.Map;

public final class JobsWantedLogicTest {
    private JobsWantedLogicTest() { }

    public static void run() {
        wantedStarsFollowConfiguredKillThresholds();
        wantedStarDisplayAlwaysHasFiveClampedPositions();
        copKillsEscalateByTwoWithoutExceedingTheMaximum();
        bountyAndBribeBoundariesUseDecimalAmounts();
    }

    private static void wantedStarsFollowConfiguredKillThresholds() {
        Map<Integer, Integer> thresholds = Map.of(1, 1, 2, 2, 4, 3, 5, 4, 6, 5);
        require(JobsWantedRules.starsForKills(0, thresholds, 5) == 0, "zero kills must have zero stars");
        require(JobsWantedRules.starsForKills(1, thresholds, 5) == 1, "first qualifying kill must give one star");
        require(JobsWantedRules.starsForKills(2, thresholds, 5) == 2, "second qualifying kill must give two stars");
        require(JobsWantedRules.starsForKills(3, thresholds, 5) == 2, "third qualifying kill must not skip a threshold");
        require(JobsWantedRules.starsForKills(4, thresholds, 5) == 3, "four qualifying kills must give three stars");
        require(JobsWantedRules.starsForKills(5, thresholds, 5) == 4, "five qualifying kills must give four stars");
        require(JobsWantedRules.starsForKills(6, thresholds, 5) == 5, "six qualifying kills must give five stars");
        require(JobsWantedRules.starsForKills(100, thresholds, 5) == 5, "wanted stars must stay capped");
    }

    private static void copKillsEscalateByTwoWithoutExceedingTheMaximum() {
        require(JobsWantedRules.copKillLevel(0, 2, 5) == 2, "killing a Cop from zero must add two stars");
        require(JobsWantedRules.copKillLevel(1, 2, 5) == 3, "Cop-kill escalation must add two stars");
        require(JobsWantedRules.copKillLevel(4, 2, 5) == 5, "Cop-kill escalation must cap at maximum stars");
        require(JobsWantedRules.copKillLevel(5, 2, 5) == 5, "five stars must not increase further");
    }

    private static void wantedStarDisplayAlwaysHasFiveClampedPositions() {
        String[] expected = {
                "&6☆☆☆☆☆",
                "&6★☆☆☆☆",
                "&6★★☆☆☆",
                "&6★★★☆☆",
                "&6★★★★☆",
                "&6★★★★★"
        };
        for (int level = 0; level <= 5; level++) {
            require(expected[level].equals(WantedDisplayAPI.format(level)), "wanted display must format level " + level);
        }
        require(expected[0].equals(WantedDisplayAPI.format(-1)), "negative wanted levels must clamp to zero");
        require(expected[5].equals(WantedDisplayAPI.format(6)), "wanted levels above five must clamp to five");
    }

    private static void bountyAndBribeBoundariesUseDecimalAmounts() {
        BigDecimal million = new BigDecimal("1000000");
        require(JobsWantedRules.isValidBounty(million, million), "maximum bounty must be accepted");
        require(!JobsWantedRules.isValidBounty(new BigDecimal("1000000.01"), million), "bounty above maximum must be rejected");
        BigDecimal minimum = new BigDecimal("5000");
        require(JobsWantedRules.meetsBribeMinimum(minimum, minimum), "minimum bribe must be accepted");
        require(!JobsWantedRules.meetsBribeMinimum(new BigDecimal("4999.99"), minimum), "bribe below minimum must be rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}