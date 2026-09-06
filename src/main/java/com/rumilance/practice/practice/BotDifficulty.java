package com.rumilance.practice.practice;

import java.util.Locale;

/**
 * ITEM 41 bot-fight difficulty.
 *
 * <p>The ladder is the Quantum's PvP Practice map's ladder one-for-one — the same seven rungs,
 * the same names, and each rung's aggression taken from the map's own
 * {@code quantum:difficulty/<n>} functions so a bot here fights like the map's bot:</p>
 *
 * <pre>
 *   map rung            map hitcd   map reach   map aim   map combo cds   map totem_cd
 *   0 NPC               (no attack) (n/a)       (n/a)     0               (n/a)
 *   1 Easy              23t (1.15s) 1.3 blk     5°        5-6s            40t
 *   2 Intermediate      15t (0.75s) 1.6 blk     4°        4-6s            31t
 *   3 Hard              10t (0.50s) 2.0 blk     3°        3-6s            21t
 *   4 CRAZY              5t (0.25s) 2.3 blk     2°        2-3s            10t
 *   5 MASTER             0t (every) 2.9 blk     2°        2s              0t
 *   6 SURVIVAL MASTER    0t (every) 3.0 blk     -         2-3s            1t
 * </pre>
 *
 * <p>HP / damage / regen are our own scaling on top of that (the map's bots are fake players with
 * the player's own gear, our bots are mannequins whose toughness comes from these numbers):
 * <strong>INTERMEDIATE is the default</strong> — the rung for a beginner-leaning intermediate
 * player — with NPC/EASY below it and HARD/CRAZY/MASTER/SURVIVAL_MASTER above it. Every field is
 * still individually tunable, which flips the preset to {@link Preset#CUSTOM}.</p>
 */
public final class BotDifficulty {

    /** Legacy names are mapped on load: NORMAL → INTERMEDIATE, EXPERT → CRAZY. */
    public enum Preset {
        NPC, EASY, INTERMEDIATE, HARD, CRAZY, MASTER, SURVIVAL_MASTER, CUSTOM
    }

    /** How long the bot must be untouched before out-of-combat regen starts (ticks per second). */
    public static final long REGEN_DELAY_MS = 5_000L;

    private Preset preset = Preset.INTERMEDIATE;
    private double botMaxHp = 50.0d;
    private double attackDamage = 4.0d;
    private long attackIntervalMs = 750L;
    private double moveSpeed = 0.22d;
    private double regenPerSecond = 3.0d;
    private long comboCooldownMs = 2400L; // crystal / cart / potion attacks
    private boolean shieldStun = true;
    private double shieldReduction = 0.5d;
    private int totemGoal = 3; // crystal bot pops needed to win
    private double reachBlocks = 3.0d;     // map "reach" (10 = 1 block)
    private double aimSpreadDegrees = 4.0d; // map "aim": per-swing direction error

    public static BotDifficulty of(Preset preset) {
        BotDifficulty d = new BotDifficulty();
        d.applyPreset(preset);
        return d;
    }

    /** Maps a saved (possibly legacy) preset name onto the current ladder. */
    public static Preset parsePreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return Preset.INTERMEDIATE;
        }
        String name = raw.trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "NORMAL" -> Preset.INTERMEDIATE;  // legacy rung name
            case "EXPERT" -> Preset.CRAZY;         // legacy rung name
            default -> {
                try {
                    yield Preset.valueOf(name);
                } catch (IllegalArgumentException e) {
                    yield Preset.INTERMEDIATE;
                }
            }
        };
    }

    /**
     * Parses {@code PRESET:hp:dmg:interval:speed:regen:combo:stun:reduction:goal[:reach[:aim]]}.
     * Saved presets always re-apply the current ladder values, so a rebalance reaches every
     * player; only {@link Preset#CUSTOM} keeps the stored numbers.
     */
    public static BotDifficulty deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return of(Preset.INTERMEDIATE);
        }
        String[] p = raw.split(":");
        Preset preset = parsePreset(p[0]);
        if (preset == Preset.CUSTOM) {
            BotDifficulty d = new BotDifficulty();
            d.preset = Preset.CUSTOM;
            try {
                if (p.length >= 10) {
                    d.botMaxHp = Double.parseDouble(p[1]);
                    d.attackDamage = Double.parseDouble(p[2]);
                    d.attackIntervalMs = Long.parseLong(p[3]);
                    d.moveSpeed = Double.parseDouble(p[4]);
                    d.regenPerSecond = Double.parseDouble(p[5]);
                    d.comboCooldownMs = Long.parseLong(p[6]);
                    d.shieldStun = Boolean.parseBoolean(p[7]);
                    d.shieldReduction = Double.parseDouble(p[8]);
                    d.totemGoal = Integer.parseInt(p[9]);
                }
                if (p.length >= 11) {
                    d.reachBlocks = Double.parseDouble(p[10]);
                }
                if (p.length >= 12) {
                    d.aimSpreadDegrees = Double.parseDouble(p[11]);
                }
            } catch (RuntimeException e) {
                return of(Preset.INTERMEDIATE);
            }
            return d;
        }
        return of(preset);
    }

    public String serialize() {
        return preset.name() + ":" + botMaxHp + ":" + attackDamage + ":" + attackIntervalMs
                + ":" + moveSpeed + ":" + regenPerSecond + ":" + comboCooldownMs
                + ":" + shieldStun + ":" + shieldReduction + ":" + totemGoal
                + ":" + reachBlocks + ":" + aimSpreadDegrees;
    }

    public void applyPreset(Preset next) {
        this.preset = next;
        switch (next) {
            case NPC -> {
                // Map rung 0: the bot does not fight back, it just exists (and walks slowly).
                botMaxHp = 20; attackDamage = 0; attackIntervalMs = 2000; moveSpeed = 0.10;
                regenPerSecond = 0; comboCooldownMs = 6000; shieldStun = false;
                shieldReduction = 0.25; totemGoal = 1; reachBlocks = 2.5; aimSpreadDegrees = 12;
            }
            case EASY -> {
                // Map rung 1: hitcd 23t, reach 1.3, aim 5, combos 5-6s, totem_cd 40t.
                botMaxHp = 30; attackDamage = 3; attackIntervalMs = 1150; moveSpeed = 0.16;
                regenPerSecond = 1.5; comboCooldownMs = 4000; shieldStun = false;
                shieldReduction = 0.4; totemGoal = 2; reachBlocks = 2.8; aimSpreadDegrees = 8;
            }
            case INTERMEDIATE -> {
                // Map rung 2 ("Intermediate"): hitcd 15t, reach 1.6, aim 4, combos 4-6s.
                botMaxHp = 50; attackDamage = 4; attackIntervalMs = 750; moveSpeed = 0.22;
                regenPerSecond = 3; comboCooldownMs = 2400; shieldStun = true;
                shieldReduction = 0.5; totemGoal = 3; reachBlocks = 3.0; aimSpreadDegrees = 5;
            }
            case HARD -> {
                // Map rung 3: hitcd 10t, reach 2.0, aim 3, combos 3-6s.
                botMaxHp = 70; attackDamage = 5; attackIntervalMs = 500; moveSpeed = 0.26;
                regenPerSecond = 4; comboCooldownMs = 1800; shieldStun = true;
                shieldReduction = 0.6; totemGoal = 4; reachBlocks = 3.2; aimSpreadDegrees = 3;
            }
            case CRAZY -> {
                // Map rung 4 ("CRAZY"): hitcd 5t, reach 2.3, aim 2, combos 2-3s.
                botMaxHp = 90; attackDamage = 6; attackIntervalMs = 300; moveSpeed = 0.30;
                regenPerSecond = 5; comboCooldownMs = 1300; shieldStun = true;
                shieldReduction = 0.7; totemGoal = 5; reachBlocks = 3.4; aimSpreadDegrees = 2;
            }
            case MASTER -> {
                // Map rung 5 ("MASTER"): hitcd 0 (every tick it can), reach 2.9, totem_cd 0.
                botMaxHp = 110; attackDamage = 7; attackIntervalMs = 250; moveSpeed = 0.34;
                regenPerSecond = 6; comboCooldownMs = 1000; shieldStun = true;
                shieldReduction = 0.75; totemGoal = 6; reachBlocks = 3.6; aimSpreadDegrees = 1;
            }
            case SURVIVAL_MASTER -> {
                // Map rung 6: the map's final boss — fastest combos, longest reach, no mercy.
                botMaxHp = 140; attackDamage = 8; attackIntervalMs = 200; moveSpeed = 0.38;
                regenPerSecond = 8; comboCooldownMs = 800; shieldStun = true;
                shieldReduction = 0.8; totemGoal = 8; reachBlocks = 3.8; aimSpreadDegrees = 0;
            }
            default -> {
            }
        }
    }

    /** Any manual parameter edit drops the preset to CUSTOM. */
    private void touch() {
        preset = Preset.CUSTOM;
    }

    public Preset preset() { return preset; }
    public double botMaxHp() { return botMaxHp; }
    public double attackDamage() { return attackDamage; }
    public long attackIntervalMs() { return attackIntervalMs; }
    public double moveSpeed() { return moveSpeed; }
    public double regenPerSecond() { return regenPerSecond; }
    public long comboCooldownMs() { return comboCooldownMs; }
    public boolean shieldStun() { return shieldStun; }
    public double shieldReduction() { return shieldReduction; }
    public int totemGoal() { return totemGoal; }
    public double reachBlocks() { return reachBlocks; }
    public double aimSpreadDegrees() { return aimSpreadDegrees; }

    public void setBotMaxHp(double v) { this.botMaxHp = clamp(v, 20, 200); touch(); }
    public void setAttackDamage(double v) { this.attackDamage = clamp(v, 0, 12); touch(); }
    public void setAttackIntervalMs(long v) { this.attackIntervalMs = (long) clamp(v, 150, 2500); touch(); }
    public void setMoveSpeed(double v) { this.moveSpeed = clamp(v, 0.05, 0.40); touch(); }
    public void setRegenPerSecond(double v) { this.regenPerSecond = clamp(v, 0, 25); touch(); }
    public void setComboCooldownMs(long v) { this.comboCooldownMs = (long) clamp(v, 500, 6000); touch(); }
    public void setShieldStun(boolean v) { this.shieldStun = v; touch(); }
    public void setShieldReduction(double v) { this.shieldReduction = clamp(v, 0, 0.9); touch(); }
    public void setTotemGoal(int v) { this.totemGoal = (int) clamp(v, 1, 10); touch(); }
    public void setReachBlocks(double v) { this.reachBlocks = clamp(v, 2.0, 4.5); touch(); }
    public void setAimSpreadDegrees(double v) { this.aimSpreadDegrees = clamp(v, 0, 20); touch(); }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
