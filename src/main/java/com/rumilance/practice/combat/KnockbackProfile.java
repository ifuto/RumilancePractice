package com.rumilance.practice.combat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/**
 * Admin knockback preset. Formula matches OldCombatMechanics / vanilla 1.8 melee:
 * residual friction on existing motion, relative base push, then sprint/enchant
 * <em>extra</em> along the attacker's look (Marlow KB Control / knockback displacement).
 *
 * <p>JSON keys:</p>
 * <ul>
 *   <li>{@code attack-knockback}  Escales base H/V only (1.0 = configured base)</li>
 *   <li>{@code horizontal-kb} / {@code vertical-kb}  Egrounded base impulse</li>
 *   <li>{@code air-vertical-kb}  Eadded vertical while airborne (0 = 1.8 air)</li>
 *   <li>{@code sprint-kb}  Eextra horizontal <em>per</em> sprint/enchant bonus level (look-dir)</li>
 *   <li>{@code sprint-vertical-kb}  Eflat extra Y when any bonus level applies</li>
 *   <li>{@code vertical-limit}  Ecap on Y after base, before sprint extra (OCM)</li>
 *   <li>{@code target-velocity}  Eresidual of existing motion (0.5 = vanilla /2)</li>
 *   <li>{@code knockback-direction}  Ebase push: {@code relative} or {@code attacker_look}</li>
 * </ul>
 */
public final class KnockbackProfile {

    public enum Direction {
        RELATIVE,
        ATTACKER_LOOK
    }

    /** OCM / 1.8 textbook. */
    public static final KnockbackProfile VANILLA = new KnockbackProfile(
            1.0d, 0.4d, 0.4d, 0.0d, 0.5d, 0.1d, 0.4d, 0.0d, 0.0d, 0.5d,
            Direction.RELATIVE, 3.5d);

    /**
     * Knockback-Manager modern defaults widely used as "Club / Hypixel-like":
     * H 0.4, V 0.36075, air V 0.24775, sprint extra H 0.5 / V 0.1, Y limit 0.675.
     */
    public static final KnockbackProfile CLUB = new KnockbackProfile(
            1.0d, 0.4d, 0.36075d, 0.24775d, 0.5d, 0.1d, 0.675d, 0.0d, 0.0d, 0.5d,
            Direction.RELATIVE, 4.0d);

    /** Stickier H + Kohi-ish sprint extra; look-dir bonus for KB Control. */
    public static final KnockbackProfile STRAY = new KnockbackProfile(
            1.0d, 0.38d, 0.4d, 0.0d, 0.425d, 0.1d, 0.4d, 0.0d, 0.0d, 0.5d,
            Direction.RELATIVE, 3.5d);

    public static final KnockbackProfile PAPER_ARCHIVE = new KnockbackProfile(
            1.0d, 0.4d, 0.3608d, 0.24775d, 0.5d, 0.1d, 0.675d, 0.0d, 0.0d, 0.5d,
            Direction.RELATIVE, 4.0d);

    public static final KnockbackProfile KOHI_ARCHIVE = new KnockbackProfile(
            1.0d, 0.35d, 0.35d, 0.0d, 0.425d, 0.1d, 0.4d, 0.0d, 0.0d, 0.5d,
            Direction.RELATIVE, 3.5d);

    public static final KnockbackProfile LUNAR_ARCHIVE = new KnockbackProfile(
            1.0d, 0.54d, 0.361735d, 0.0d, 0.38d, 0.1d, 0.675d, 0.0d, 0.0d, 0.6849d,
            Direction.RELATIVE, 4.0d);

    public static final KnockbackProfile VELT_ARCHIVE = new KnockbackProfile(
            1.0d, 0.325d, 0.36d, 0.0d, 0.5d, 0.1d, 0.675d, 0.0d, 0.0d, 0.1d,
            Direction.RELATIVE, 4.0d);

    private final double attackKnockback;
    private final double horizontalKb;
    private final double verticalKb;
    private final double airVerticalKb;
    private final double sprintKb;
    private final double sprintVerticalKb;
    private final double verticalLimit;
    private final double knockbackResistance;
    private final double attackerVelocityInfluence;
    private final double targetVelocity;
    private final Direction knockbackDirection;
    private final double velocityClamp;

    public KnockbackProfile(
            double attackKnockback,
            double horizontalKb,
            double verticalKb,
            double airVerticalKb,
            double sprintKb,
            double sprintVerticalKb,
            double verticalLimit,
            double knockbackResistance,
            double attackerVelocityInfluence,
            double targetVelocity,
            Direction knockbackDirection,
            double velocityClamp
    ) {
        this.attackKnockback = clamp(attackKnockback, 0.0d, 8.0d);
        this.horizontalKb = clamp(horizontalKb, 0.0d, 4.0d);
        this.verticalKb = clamp(verticalKb, 0.0d, 4.0d);
        this.airVerticalKb = clamp(airVerticalKb, 0.0d, 4.0d);
        this.sprintKb = clamp(sprintKb, 0.0d, 4.0d);
        this.sprintVerticalKb = clamp(sprintVerticalKb, 0.0d, 4.0d);
        this.verticalLimit = clamp(verticalLimit, 0.05d, 8.0d);
        this.knockbackResistance = clamp(knockbackResistance, 0.0d, 1.0d);
        this.attackerVelocityInfluence = clamp(attackerVelocityInfluence, 0.0d, 1.0d);
        this.targetVelocity = clamp(targetVelocity, 0.0d, 1.0d);
        this.knockbackDirection = knockbackDirection == null ? Direction.RELATIVE : knockbackDirection;
        this.velocityClamp = clamp(velocityClamp, 0.1d, 16.0d);
    }

    public static KnockbackProfile load(File file) {
        Objects.requireNonNull(file, "file");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        double vertical = yaml.getDouble("vertical-kb", VANILLA.verticalKb);
        return new KnockbackProfile(
                yaml.getDouble("attack-knockback", VANILLA.attackKnockback),
                yaml.getDouble("horizontal-kb", VANILLA.horizontalKb),
                vertical,
                yaml.getDouble("air-vertical-kb", 0.0d),
                yaml.getDouble("sprint-kb", VANILLA.sprintKb),
                yaml.getDouble("sprint-vertical-kb", VANILLA.sprintVerticalKb),
                yaml.getDouble("vertical-limit", Math.max(vertical, VANILLA.verticalLimit)),
                yaml.getDouble("knockback-resistance", VANILLA.knockbackResistance),
                yaml.getDouble("attacker-velocity-influence", VANILLA.attackerVelocityInfluence),
                yaml.getDouble("target-velocity", VANILLA.targetVelocity),
                parseDirection(yaml.getString("knockback-direction", "relative")),
                yaml.getDouble("velocity-clamp", VANILLA.velocityClamp)
        );
    }

    public static Direction parseDirection(String raw) {
        if (raw == null) {
            return Direction.RELATIVE;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (key.equals("attacker_look") || key.equals("look") || key.equals("attackerlook")) {
            return Direction.ATTACKER_LOOK;
        }
        return Direction.RELATIVE;
    }

    /**
     * OCM-faithful rewrite from pre-hit motion. Sprint/enchant extras use look direction
     * so Marlow-style KB Control (knockback displacement) works.
     */
    public Vector apply(
            double existingX, double existingY, double existingZ,
            double dirX, double dirZ,
            double lookX, double lookZ,
            double attackerVelX, double attackerVelZ,
            boolean sprinting,
            boolean onGround,
            int knockbackEnchant
    ) {
        double nx;
        double nz;
        if (knockbackDirection == Direction.ATTACKER_LOOK) {
            nx = lookX;
            nz = lookZ;
        } else {
            nx = dirX;
            nz = dirZ;
        }
        double len = Math.hypot(nx, nz);
        if (len < 1.0e-4d) {
            nx = lookX;
            nz = lookZ;
            len = Math.hypot(nx, nz);
        }
        if (len < 1.0e-4d) {
            nx = 0.0d;
            nz = 1.0d;
            len = 1.0d;
        }
        nx /= len;
        nz /= len;

        double influence = attackerVelocityInfluence;
        if (influence > 0.0d) {
            nx = nx * (1.0d - influence) + attackerVelX * influence;
            nz = nz * (1.0d - influence) + attackerVelZ * influence;
            double mixed = Math.hypot(nx, nz);
            if (mixed > 1.0e-4d) {
                nx /= mixed;
                nz /= mixed;
            }
        }

        double resist = 1.0d - knockbackResistance;
        double friction = targetVelocity;
        double baseH = horizontalKb * attackKnockback * resist;
        double baseV = verticalKb * attackKnockback * resist;
        double airV = airVerticalKb * attackKnockback * resist;

        double vx = existingX * friction + nx * baseH;
        double vz = existingZ * friction + nz * baseH;
        double vy = existingY * friction;
        if (onGround) {
            vy += baseV;
            if (vy > verticalLimit) {
                vy = verticalLimit;
            }
        } else if (airV > 0.0d) {
            vy += airV;
        }

        int bonus = Math.max(0, knockbackEnchant) + (sprinting ? 1 : 0);
        if (bonus > 0) {
            double lx = lookX;
            double lz = lookZ;
            double lookLen = Math.hypot(lx, lz);
            if (lookLen < 1.0e-4d) {
                lx = nx;
                lz = nz;
                lookLen = 1.0d;
            }
            lx /= lookLen;
            lz /= lookLen;
            vx += lx * bonus * sprintKb * resist;
            vz += lz * bonus * sprintKb * resist;
            vy += sprintVerticalKb * resist;
        }

        Vector out = new Vector(vx, vy, vz);
        double mag = out.length();
        if (mag > velocityClamp && mag > 0.0d) {
            out.multiply(velocityClamp / mag);
        }
        return out;
    }

    public boolean sameCoefficients(KnockbackProfile other) {
        if (other == null) {
            return false;
        }
        return nearly(attackKnockback, other.attackKnockback)
                && nearly(horizontalKb, other.horizontalKb)
                && nearly(verticalKb, other.verticalKb)
                && nearly(airVerticalKb, other.airVerticalKb)
                && nearly(sprintKb, other.sprintKb)
                && nearly(sprintVerticalKb, other.sprintVerticalKb)
                && nearly(verticalLimit, other.verticalLimit)
                && nearly(knockbackResistance, other.knockbackResistance)
                && nearly(attackerVelocityInfluence, other.attackerVelocityInfluence)
                && nearly(targetVelocity, other.targetVelocity)
                && knockbackDirection == other.knockbackDirection
                && nearly(velocityClamp, other.velocityClamp);
    }

    public double attackKnockback() {
        return attackKnockback;
    }

    public double horizontalKb() {
        return horizontalKb;
    }

    public double verticalKb() {
        return verticalKb;
    }

    public double airVerticalKb() {
        return airVerticalKb;
    }

    public double sprintKb() {
        return sprintKb;
    }

    public double sprintVerticalKb() {
        return sprintVerticalKb;
    }

    public double verticalLimit() {
        return verticalLimit;
    }

    public double knockbackResistance() {
        return knockbackResistance;
    }

    public double attackerVelocityInfluence() {
        return attackerVelocityInfluence;
    }

    public double targetVelocity() {
        return targetVelocity;
    }

    public Direction knockbackDirection() {
        return knockbackDirection;
    }

    public double velocityClamp() {
        return velocityClamp;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean nearly(double a, double b) {
        return Math.abs(a - b) < 0.0001d;
    }
}
