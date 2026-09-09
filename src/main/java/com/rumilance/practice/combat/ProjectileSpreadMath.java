package com.rumilance.practice.combat;

import org.bukkit.util.Vector;

/**
 * Pure math for straightening pearl / wind-charge spread — deliberately mirrors vanilla's
 * {@code ProjectileEntity#setVelocity(Entity,float,float,float,float,float)} so the final
 * trajectory differs ONLY by the configured spread, never by anything else:
 *
 * <pre>
 *   vanilla:  v = normalize( lookDir + noise(divergence=1.0) ) * 1.5
 *                    + ( Mx, isOnGround ? 0 : My, Mz )    // thrower motion inheritance
 * </pre>
 *
 * The inheritance rule is the whole point: airborne throws (elytra glide, wind-charge
 * knockback) inherit the FULL motion including vertical — vanilla behaviour that must not
 * be lost — while grounded throws deliberately ignore vertical motion (a standing player
 * carries a tiny gravity-sink Y velocity that vanilla does NOT hand to the pearl; copying it
 * would drop every standing throw ~2 blocks short).
 *
 * <p>Magnitude invariant: the thrown part always keeps its exact length (≈1.5), so the
 * straightened pearl never flies further or slower than vanilla — only the direction noise
 * changes.</p>
 */
public final class ProjectileSpreadMath {

    private ProjectileSpreadMath() {
    }

    /**
     * Rebuilds the launch velocity with a scaled random spread.
     *
     * @param launchVelocity the projectile's velocity as launched (already includes vanilla
     *                       motion inheritance), never mutated
     * @param shooterMotion  the thrower's entity velocity at launch, never mutated
     * @param shooterOnGround thrower's on-ground state at launch (vanilla Y-inheritance rule)
     * @param lookDirection  the thrower's eye look direction (unit or not — renormalized)
     * @param spreadFraction 0 = perfectly straight at the crosshair, 1 = untouched vanilla
     * @return the corrected final velocity (or {@code launchVelocity} clone when nothing
     *         should change)
     */
    public static Vector straightenThrow(Vector launchVelocity, Vector shooterMotion,
                                         boolean shooterOnGround, Vector lookDirection,
                                         double spreadFraction) {
        double spread = Math.max(0.0D, Math.min(1.0D, spreadFraction));
        if (spread >= 0.999D) {
            return launchVelocity.clone(); // full vanilla spread: leave untouched
        }
        // Vanilla-conditional inherited motion — the isOnGround Y-zeroing is load-bearing.
        Vector motion = shooterMotion.clone();
        if (shooterOnGround) {
            motion.setY(0.0D);
        }
        Vector thrown = launchVelocity.clone().subtract(motion);
        double speed = thrown.length();
        if (speed <= 0.0001D) {
            return launchVelocity.clone(); // degenerate: nothing sensible to straighten
        }
        Vector look = lookDirection.clone().normalize();
        Vector currentDir = thrown.clone().normalize();
        // Blend orientation only; renormalise so |thrown| stays exactly `speed` (no boost,
        // no slow — "distance" is untouched by the spread correction).
        Vector blended = look.multiply(1.0D - spread).add(currentDir.multiply(spread)).normalize();
        return blended.multiply(speed).add(motion);
    }
}
