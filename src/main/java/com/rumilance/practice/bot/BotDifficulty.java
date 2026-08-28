package com.rumilance.practice.bot;

/**
 * Sword-PvP bot difficulty. Timing and technique fidelity scale up to {@link #MARLOWWW}
 * (inspired by high-level modern sword PvP: W-tap sprint resets, S-tap spacing, crit finishes).
 */
public enum BotDifficulty {
    NOOB(0.55, 14, 0.05, 0.0, 0.35, false, false),
    EASY(0.70, 10, 0.15, 0.10, 0.25, true, false),
    NORMAL(0.85, 7, 0.35, 0.25, 0.15, true, true),
    PRO(0.95, 4, 0.55, 0.45, 0.08, true, true),
    MARLOWWW(1.0, 2, 0.75, 0.70, 0.03, true, true);

    private final double attackCharge;
    private final int reactionTicks;
    private final double critChance;
    private final double wtapChance;
    private final double aimError;
    private final boolean strafe;
    private final boolean staple;

    BotDifficulty(double attackCharge, int reactionTicks, double critChance, double wtapChance,
                  double aimError, boolean strafe, boolean staple) {
        this.attackCharge = attackCharge;
        this.reactionTicks = reactionTicks;
        this.critChance = critChance;
        this.wtapChance = wtapChance;
        this.aimError = aimError;
        this.strafe = strafe;
        this.staple = staple;
    }

    public double attackCharge() {
        return attackCharge;
    }

    public int reactionTicks() {
        return reactionTicks;
    }

    public double critChance() {
        return critChance;
    }

    public double wtapChance() {
        return wtapChance;
    }

    public double aimError() {
        return aimError;
    }

    public boolean strafe() {
        return strafe;
    }

    public boolean staple() {
        return staple;
    }

    public static BotDifficulty fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        if ("EAZY".equals(key)) {
            key = "EASY";
        }
        try {
            return BotDifficulty.valueOf(key);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
