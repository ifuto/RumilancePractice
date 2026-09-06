package com.rumilance.practice.practice;

import java.util.Locale;

/**
 * ITEM 41 bot-fight difficulty. Coarse presets mirror the Quantum map's difficulty ladder
 * (NPC … SURVIVAL MASTER); every field is also individually tunable, which flips the
 * preset to {@link Preset#CUSTOM}.
 */
public final class BotDifficulty {

    public enum Preset {
        NPC, EASY, NORMAL, HARD, EXPERT, SURVIVAL_MASTER, CUSTOM
    }

    private Preset preset = Preset.NORMAL;
    private double botMaxHp = 100.0d;
    private double attackDamage = 5.0d;
    private long attackIntervalMs = 900L;
    private double moveSpeed = 0.24d;
    private double regenPerSecond = 10.0d;
    private long comboCooldownMs = 2600L; // crystal / cart / potion attacks
    private boolean shieldStun = true;
    private double shieldReduction = 0.5d;
    private int totemGoal = 3; // crystal bot pops needed to win

    public static BotDifficulty of(Preset preset) {
        BotDifficulty d = new BotDifficulty();
        d.applyPreset(preset);
        return d;
    }

    /** Parses {@code PRESET:hp:dmg:interval:speed:regen:combo:stun:reduction:goal}. */
    public static BotDifficulty deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return of(Preset.NORMAL);
        }
        String[] p = raw.split(":");
        try {
            BotDifficulty d = new BotDifficulty();
            d.preset = Preset.valueOf(p[0].toUpperCase(Locale.ROOT));
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
            return d;
        } catch (Exception e) {
            return of(Preset.NORMAL);
        }
    }

    public String serialize() {
        return preset.name() + ":" + botMaxHp + ":" + attackDamage + ":" + attackIntervalMs
                + ":" + moveSpeed + ":" + regenPerSecond + ":" + comboCooldownMs
                + ":" + shieldStun + ":" + shieldReduction + ":" + totemGoal;
    }

    public void applyPreset(Preset next) {
        this.preset = next;
        switch (next) {
            case NPC -> {
                botMaxHp = 60; attackDamage = 2; attackIntervalMs = 1500; moveSpeed = 0.10;
                regenPerSecond = 5; comboCooldownMs = 4500; shieldStun = false;
                shieldReduction = 0.25; totemGoal = 1;
            }
            case EASY -> {
                botMaxHp = 80; attackDamage = 3; attackIntervalMs = 1200; moveSpeed = 0.16;
                regenPerSecond = 8; comboCooldownMs = 3500; shieldStun = false;
                shieldReduction = 0.4; totemGoal = 2;
            }
            case NORMAL -> {
                botMaxHp = 100; attackDamage = 5; attackIntervalMs = 900; moveSpeed = 0.24;
                regenPerSecond = 10; comboCooldownMs = 2600; shieldStun = true;
                shieldReduction = 0.5; totemGoal = 3;
            }
            case HARD -> {
                botMaxHp = 120; attackDamage = 6; attackIntervalMs = 750; moveSpeed = 0.28;
                regenPerSecond = 12; comboCooldownMs = 2000; shieldStun = true;
                shieldReduction = 0.6; totemGoal = 4;
            }
            case EXPERT -> {
                botMaxHp = 140; attackDamage = 8; attackIntervalMs = 600; moveSpeed = 0.32;
                regenPerSecond = 15; comboCooldownMs = 1500; shieldStun = true;
                shieldReduction = 0.7; totemGoal = 5;
            }
            case SURVIVAL_MASTER -> {
                botMaxHp = 160; attackDamage = 10; attackIntervalMs = 450; moveSpeed = 0.36;
                regenPerSecond = 20; comboCooldownMs = 1100; shieldStun = true;
                shieldReduction = 0.8; totemGoal = 6;
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

    public void setBotMaxHp(double v) { this.botMaxHp = clamp(v, 20, 200); touch(); }
    public void setAttackDamage(double v) { this.attackDamage = clamp(v, 1, 12); touch(); }
    public void setAttackIntervalMs(long v) { this.attackIntervalMs = (long) clamp(v, 300, 2000); touch(); }
    public void setMoveSpeed(double v) { this.moveSpeed = clamp(v, 0.05, 0.40); touch(); }
    public void setRegenPerSecond(double v) { this.regenPerSecond = clamp(v, 0, 25); touch(); }
    public void setComboCooldownMs(long v) { this.comboCooldownMs = (long) clamp(v, 600, 6000); touch(); }
    public void setShieldStun(boolean v) { this.shieldStun = v; touch(); }
    public void setShieldReduction(double v) { this.shieldReduction = clamp(v, 0, 0.9); touch(); }
    public void setTotemGoal(int v) { this.totemGoal = (int) clamp(v, 1, 10); touch(); }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
