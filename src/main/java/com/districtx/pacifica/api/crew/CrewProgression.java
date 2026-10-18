package com.districtx.pacifica.api.crew;

public final class CrewProgression {
    public static final int DEFAULT_MAX_LEVEL = 50;
    public static final long DEFAULT_TOTAL_XP = 413_250L;

    private final int maximumLevel;
    private final long totalXp;
    private final long xpPerStep;
    private final long remainder;

    public CrewProgression(int maximumLevel, long totalXp) {
        this.maximumLevel = Math.max(2, maximumLevel);
        long minimumTotal = this.maximumLevel - 1L;
        this.totalXp = Math.max(minimumTotal, totalXp);
        long steps = this.maximumLevel - 1L;
        xpPerStep = this.totalXp / steps;
        remainder = this.totalXp % steps;
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    public long totalXp() {
        return totalXp;
    }

    public long xpRequiredForNextLevel(int level) {
        if (level < 1 || level >= maximumLevel) return 0;
        return xpPerStep + (level <= remainder ? 1 : 0);
    }

    public long totalXpRequiredForLevel(int level) {
        int boundedLevel = Math.max(1, Math.min(maximumLevel, level));
        long steps = boundedLevel - 1L;
        return xpPerStep * steps + Math.min(steps, remainder);
    }

    public int levelForExperience(long totalExperience) {
        if (totalExperience <= 0) return 1;
        int low = 1;
        int high = maximumLevel;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (totalXpRequiredForLevel(middle) <= totalExperience) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    public long experienceIntoLevel(long totalExperience) {
        int level = levelForExperience(totalExperience);
        return Math.max(0, totalExperience - totalXpRequiredForLevel(level));
    }

    public long experienceToNextLevel(long totalExperience) {
        int level = levelForExperience(totalExperience);
        long requirement = xpRequiredForNextLevel(level);
        return requirement == 0 ? 0 : Math.max(0, requirement - experienceIntoLevel(totalExperience));
    }
}