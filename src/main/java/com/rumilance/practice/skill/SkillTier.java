package com.rumilance.practice.skill;

/**
 * Ranked skill bands from highest to lowest: HT1 … LT5.
 * Placement uses the conservative ordinal {@code mu - 3σ}, not raw wins.
 */
public enum SkillTier {

    HT1(2100, "HT1"),
    LT1(1850, "LT1"),
    HT2(1650, "HT2"),
    LT2(1450, "LT2"),
    HT3(1300, "HT3"),
    LT3(1150, "LT3"),
    HT4(1000, "HT4"),
    LT4(850, "LT4"),
    HT5(700, "HT5"),
    LT5(0, "LT5");

    private final int minOrdinal;
    private final String label;

    SkillTier(int minOrdinal, String label) {
        this.minOrdinal = minOrdinal;
        this.label = label;
    }

    public String label() {
        return label;
    }

    public int minOrdinal() {
        return minOrdinal;
    }

    /** Highest tier whose floor the ordinal meets. */
    public static SkillTier ofOrdinal(int ordinal) {
        int value = Math.max(0, ordinal);
        for (SkillTier tier : values()) {
            if (value >= tier.minOrdinal) {
                return tier;
            }
        }
        return LT5;
    }

    public static SkillTier of(SkillRating rating) {
        return ofOrdinal(SkillCalculator.ordinalPoints(rating));
    }

    public static SkillTier of(double mu, double sigma) {
        return ofOrdinal(SkillCalculator.ordinalPoints(mu, sigma));
    }
}
