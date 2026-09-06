package com.rumilance.practice.combat;

/**
 * Vanilla Java Edition explosion math (1.21.x), extracted so it can be unit tested.
 *
 * <p>Mojang's {@code Explosion#finalizeExplosion} does, for every entity inside the
 * {@code 2 * power} damage radius:</p>
 * <pre>
 *   explosionSize = 2 * power                       // "damage radius"
 *   impact        = (1 - distance / explosionSize) * exposure
 *   damage        = (int) ((impact * impact + impact) / 2 * 7 * explosionSize + 1)
 *   delta         = normalise(vec3(eyeX - x, eyeY - y, eyeZ - z))
 *   knockback     = delta * (impact - dot(currentVelocity, delta)) * (1 - explosionKnockbackResistance)
 *   velocity     += knockback
 * </pre>
 *
 * <ul>
 *   <li>{@code distance} is measured from the explosion centre to the entity's <em>feet</em>
 *       position (its {@code X/Y(min)/Z}), not to its eye or centre.</li>
 *   <li>{@code exposure} is the fraction of unobstructed sample rays, sampled over the entity's
 *       bounding box <em>inflated by 0.6</em> on every axis — for a standing player (0.6 x 1.8)
 *       that is a 2 x 3 x 2 grid = 12 sample points, not a handful of made-up offsets.</li>
 *   <li>{@code damage} is the RAW amount: the server afterwards applies difficulty scaling,
 *       armor / toughness, protection enchantments, resistance and absorption on its own, so a
 *       plugin re-applying a skipped blast must hand over exactly this raw value
 *       ({@code EntityDamageEvent} then reproduces the whole vanilla pipeline).</li>
 *   <li>The knockback is a <em>delta</em>: vanilla subtracts the velocity the entity already has
 *       in the blast direction, so a player running INTO their own crystal is not launched as far
 *       as a standing one. Adding {@code impact * delta} on top (what a naive port does) roughly
 *       doubles the knockback for a moving player.</li>
 *   <li>{@code explosionKnockbackResistance} is a real attribute since 1.21.2 — Blast Protection
 *       grants 0.15 per level through it, so no separate enchantment math is needed.</li>
 * </ul>
 *
 * <p>Reference values (point blank, full exposure, before armor): end crystal / charged creeper
 * (power 6) = 85, bed / respawn anchor (power 5) = 71, TNT (power 4) = 57, creeper (power 3) = 43.</p>
 */
public final class ExplosionPhysics {

    /** End crystal blast power (vanilla {@code EndCrystal#destroy}). */
    public static final float CRYSTAL_POWER = 6.0f;
    /** Bed (Nether/End/custom dimensions) and respawn anchor blast power. */
    public static final float BED_POWER = 5.0f;
    public static final float ANCHOR_POWER = 5.0f;
    /** Primed TNT blast power. */
    public static final float TNT_POWER = 4.0f;
    /** Creeper blast power (charged: 6). */
    public static final float CREEPER_POWER = 3.0f;
    /** Bounding-box inflation used by the exposure sampling ({@code Mth.ceil} grid). */
    public static final double EXPOSURE_MARGIN = 0.6d;

    private ExplosionPhysics() {
    }

    /** Vanilla {@code explosionSize}: the damage radius is twice the power. */
    public static double damageRadius(double power) {
        return power * 2.0d;
    }

    /**
     * Grid step count on one axis: {@code Mth.ceil((2 * (margin + halfSize)) / 3)}, i.e. how many
     * slices the inflated bounding box is cut into. Never below 1.
     */
    public static int sampleSteps(double sizeOnAxis) {
        int steps = (int) Math.ceil((2.0d * (EXPOSURE_MARGIN + sizeOnAxis / 2.0d)) / 3.0d);
        return Math.max(1, steps);
    }

    /** Sample coordinate on one axis: {@code minX + (0.5 + i) / steps * size}, i in [0, steps). */
    public static double sampleCoordinate(double min, double size, int steps, int index) {
        return min + (0.5d + index) / steps * size;
    }

    /** {@code (1 - distance / (2 * power)) * exposure}, clamped to [0, 1]. */
    public static double impact(double distance, double power, double exposure) {
        double radius = damageRadius(power);
        if (radius <= 0.0d) {
            return 0.0d;
        }
        double raw = (1.0d - distance / radius) * exposure;
        return Math.max(0.0d, Math.min(1.0d, raw));
    }

    /** True when the entity is inside the damage radius at all. */
    public static boolean inRadius(double distance, double power) {
        return distance < damageRadius(power);
    }

    /**
     * Raw explosion damage: {@code (int) ((impact^2 + impact) / 2 * 7 * (2 * power) + 1)}.
     * Returns 0 outside the radius (vanilla never even looks at those entities).
     */
    public static double rawDamage(double impact, double power) {
        if (impact <= 0.0d) {
            return 0.0d;
        }
        return (int) ((impact * impact + impact) / 2.0d * 7.0d * damageRadius(power) + 1.0d);
    }

    /** Convenience: distance + exposure straight to raw damage. */
    public static double rawDamageAt(double distance, double power, double exposure) {
        if (!inRadius(distance, power)) {
            return 0.0d;
        }
        return rawDamage(impact(distance, power, exposure), power);
    }

    /**
     * Vanilla knockback delta on one axis:
     * {@code dir * (impact - dot(velocity, dir)) * (1 - explosionKnockbackResistance)}.
     *
     * @param dirComponent   the normalised direction component (eye - centre) on this axis
     * @param impact         blast impact for this entity
     * @param velocityDotDir dot product of the entity's CURRENT velocity with the direction
     * @param knockbackResistance the entity's {@code explosion_knockback_resistance} attribute
     */
    public static double knockbackDelta(double dirComponent, double impact, double velocityDotDir,
                                        double knockbackResistance) {
        if (impact <= 0.0d || dirComponent == 0.0d) {
            return 0.0d;
        }
        double scale = 1.0d - Math.max(0.0d, Math.min(1.0d, knockbackResistance));
        return dirComponent * (impact - velocityDotDir) * scale;
    }
}
