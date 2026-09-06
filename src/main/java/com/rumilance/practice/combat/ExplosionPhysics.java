package com.rumilance.practice.combat;

/**
 * Vanilla Java Edition explosion math (1.21.x), extracted so it can be unit tested.
 *
 * <p>Mojang's {@code Explosion#finalizeExplosion} does, for every entity inside the
 * {@code 2 * power} damage radius:</p>
 * <pre>
 *   explosionSize = 2 * power                       // "damage radius"
 *   impact        = (1 - distance / explosionSize) * exposure
 *   damage        = (impact * impact + impact) / 2 * 7 * explosionSize + 1     // NOT truncated
 *   direction     = normalise(vec3(eyeX - x, eyeY - y, eyeZ - z))
 *   magnitude     = impact * knockbackMultiplier * (1 - explosionKnockbackResistance)
 *   velocity     += direction * magnitude
 * </pre>
 *
 * <ul>
 *   <li>{@code distance} is measured from the explosion centre to the entity's <em>feet</em>
 *       position (its {@code X/Y(min)/Z}), not to its eye or centre.</li>
 *   <li>{@code exposure} is the fraction of unobstructed sample rays. The samples form a
 *       {@code ceil(2*width+1) x ceil(2*height+1) x ceil(2*depth+1)} grid over the entity's
 *       bounding box (minecraft.wiki "Explosion / Exposure"): for a standing player (0.6 x 1.8)
 *       that is 3 x 5 x 3 = 45 rays, for TNT (0.98 cube) 3 x 3 x 3 = 27 - not a handful of
 *       made-up offsets, and NOT an inflated box.</li>
 *   <li>{@code damage} is the RAW amount: the server afterwards applies difficulty scaling,
 *       armor / toughness, protection enchantments, resistance and absorption on its own, so a
 *       plugin re-applying a skipped blast must hand over exactly this raw value
 *       ({@code EntityDamageEvent} then reproduces the whole vanilla pipeline). It is a float, not
 *       an int: the {@code + 1} means everything inside the radius takes AT LEAST 1 damage, even
 *       when the blast is fully blocked (exposure 0 → impact 0 → damage 1).</li>
 *   <li>The knockback is a velocity <em>added</em> to the entity's current velocity: the vector
 *       from the blast centre to the entity's EYES, scaled to
 *       {@code impact * knockbackMultiplier * (1 - explosionKnockbackResistance)}. A stationary
 *       player next to a crystal therefore leaves at ~0.9 blocks/tick, which is the familiar
 *       crystal launch (minecraft.wiki calculator: velocity magnitude == impact for multiplier 1
 *       and resistance 0).</li>
 *   <li>{@code explosionKnockbackResistance} is a real attribute since 1.21.2 — Blast Protection
 *       grants 0.15 per level through it, so no separate enchantment math is needed.</li>
 * </ul>
 *
 * <p>Reference values (point blank, full exposure, before armor): end crystal / charged creeper
 * (power 6) = 85, bed / respawn anchor (power 5) = 71, TNT (power 4) = 57, creeper (power 3) = 43.
 * The wiki's own calculator agrees on a non-trivial case: TNT (power 4) one block away with full
 * exposure → impact 0.875, damage 46.9375, velocity 0.875 blocks/tick.</p>
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
    /** Knockback multiplier of a normal explosion (crystal, bed, anchor, TNT, creeper). */
    public static final double KNOCKBACK_MULTIPLIER = 1.0d;
    /** Player-thrown wind charge (minecraft.wiki: 1.22f). */
    public static final double KNOCKBACK_MULTIPLIER_WIND_CHARGE = 1.22d;
    /** Breeze wind charge (minecraft.wiki: 0.6f). */
    public static final double KNOCKBACK_MULTIPLIER_BREEZE = 0.6d;

    private ExplosionPhysics() {
    }

    /** Vanilla {@code explosionSize}: the damage radius is twice the power. */
    public static double damageRadius(double power) {
        return power * 2.0d;
    }

    /**
     * Sample spacing on one axis as a fraction of the box: {@code 1 / (2 * size + 1)}.
     */
    public static double sampleStep(double sizeOnAxis) {
        return 1.0d / (2.0d * sizeOnAxis + 1.0d);
    }

    /**
     * Number of sample rays on one axis. Vanilla loops {@code for (f = 0; f <= 1; f += step)} with
     * {@code step = 1 / (2 * size + 1)}, i.e. {@code floor(2 * size + 1) + 1} samples — the wiki
     * writes {@code ceil(2 * size + 1)}, which differs only for exact integer sizes (where the
     * accumulated {@code f == 1.0} endpoint still runs). Standing player: X/Z 3, Y 5.
     */
    public static int sampleSteps(double sizeOnAxis) {
        double step = sampleStep(sizeOnAxis);
        if (step <= 0.0d || Double.isNaN(step) || Double.isInfinite(step)) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(1.0d / step) + 1);
    }

    /**
     * Sample coordinate on one axis: {@code lerp(index * step, min, min + size)}, i.e. the grid
     * starts at the box minimum and walks {@code size / (2 * size + 1)} blocks per sample. Vanilla
     * computes the centring offset for this grid and then never applies it, which is the known
     * directional bias of explosion exposure (MC-232355) — reproduced here on purpose.
     */
    public static double sampleCoordinate(double min, double sizeOnAxis, int index) {
        return min + index * sampleStep(sizeOnAxis) * sizeOnAxis;
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
     * Raw explosion damage: {@code (impact^2 + impact) / 2 * 7 * (2 * power) + 1}. Never below 1
     * inside the radius - the {@code + 1} is why a fully blocked blast still hurts for 1.
     * Callers outside the radius get 0 from {@link #rawDamageAt} (vanilla ignores those entities).
     */
    public static double rawDamage(double impact, double power) {
        double clamped = Math.max(0.0d, impact);
        return (clamped * clamped + clamped) / 2.0d * 7.0d * damageRadius(power) + 1.0d;
    }

    /** Convenience: distance + exposure straight to raw damage (0 outside the radius). */
    public static double rawDamageAt(double distance, double power, double exposure) {
        if (!inRadius(distance, power)) {
            return 0.0d;
        }
        return rawDamage(impact(distance, power, exposure), power);
    }

    /**
     * Vanilla explosion knockback magnitude (minecraft.wiki "Explosion / Velocity"):
     * {@code impact * knockbackMultiplier * (1 - explosion_knockback_resistance)}, clamped so a
     * resistance of 1.0 or more kills the push and a negative one never amplifies it. The result
     * is the LENGTH of the vector added to the entity's current velocity, pointing from the blast
     * centre to the entity's eyes.
     *
     * @param impact              blast impact for this entity (0..1)
     * @param knockbackMultiplier 1.0 for explosions, 1.22 / 0.6 for wind charges
     * @param knockbackResistance the {@code explosion_knockback_resistance} attribute value
     */
    public static double knockbackMagnitude(double impact, double knockbackMultiplier,
                                            double knockbackResistance) {
        if (impact <= 0.0d || knockbackMultiplier <= 0.0d) {
            return 0.0d;
        }
        double resistance = Math.max(0.0d, Math.min(1.0d, knockbackResistance));
        return impact * knockbackMultiplier * (1.0d - resistance);
    }
}
