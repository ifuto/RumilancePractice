package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla explosion math as documented on minecraft.wiki/w/Explosion (Java Edition).
 *
 * <p>Two independent checks anchor every number here:</p>
 * <ul>
 *   <li>the wiki's maximum-damage table (point blank, full exposure): crystal / charged creeper 85,
 *       bed / respawn anchor 71, TNT 57, creeper 43;</li>
 *   <li>the wiki's own exposure/knockback calculator for TNT: power 4, one block away, full
 *       exposure (27/27 rays) \u2192 impact 0.875, damage <strong>46.9375</strong>, velocity
 *       0.875 blocks/tick. That value is fractional, which is what proves the damage formula is
 *       NOT truncated to an int.</li>
 * </ul>
 */
class ExplosionPhysicsTest {

    @Test
    void damageRadiusIsTwiceThePower() {
        assertEquals(12.0d, ExplosionPhysics.damageRadius(6.0d), 1e-9);
        assertEquals(10.0d, ExplosionPhysics.damageRadius(ExplosionPhysics.BED_POWER), 1e-9);
        assertEquals(8.0d, ExplosionPhysics.damageRadius(ExplosionPhysics.TNT_POWER), 1e-9);
    }

    @Test
    void pointBlankFullExposureMatchesTheWikiReferenceTable() {
        // impact = 1 -> damage = 7 * (2 * power) + 1 = 14 * power + 1
        assertEquals(85.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
        assertEquals(71.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.BED_POWER, 1.0d), 1e-9);
        assertEquals(71.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.ANCHOR_POWER, 1.0d), 1e-9);
        assertEquals(57.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.TNT_POWER, 1.0d), 1e-9);
        assertEquals(43.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.CREEPER_POWER, 1.0d), 1e-9);
    }

    @Test
    void matchesTheWikiCalculatorForTntOneBlockAway() {
        // TNT (power 4), distance 1, exposure 27/27 = 1.0:
        //   impact  = 1 - 1/(2*4) = 0.875
        //   damage  = (0.875^2 + 0.875)/2 * 7 * 8 + 1 = 46.9375   (wiki calculator value)
        //   velocity magnitude = impact * 1.0 * (1 - 0) = 0.875 blocks/tick
        double impact = ExplosionPhysics.impact(1.0d, ExplosionPhysics.TNT_POWER, 1.0d);
        assertEquals(0.875d, impact, 1e-9);
        assertEquals(46.9375d, ExplosionPhysics.rawDamage(impact, ExplosionPhysics.TNT_POWER), 1e-9);
        assertEquals(46.9375d, ExplosionPhysics.rawDamageAt(1.0d, ExplosionPhysics.TNT_POWER, 1.0d), 1e-9);
        assertEquals(0.875d, ExplosionPhysics.knockbackMagnitude(impact,
                ExplosionPhysics.KNOCKBACK_MULTIPLIER, 0.0d), 1e-9);
    }

    @Test
    void damageIsAFloatNotATruncatedInt() {
        // Crystal (2p = 12) 6 blocks away: impact 0.5 -> 0.375 * 84 + 1 = 32.5, NOT 32.
        assertEquals(32.5d, ExplosionPhysics.rawDamageAt(6.0d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
        // Half covered at point blank gives the same impact and therefore the same damage.
        assertEquals(32.5d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.CRYSTAL_POWER, 0.5d), 1e-9);
        // Bed (2p = 10), 5 blocks: impact 0.5 -> 0.375 * 70 + 1 = 27.25.
        assertEquals(27.25d, ExplosionPhysics.rawDamageAt(5.0d, ExplosionPhysics.BED_POWER, 1.0d), 1e-9);
    }

    @Test
    void everythingInsideTheRadiusTakesAtLeastOneDamage() {
        // The formula's "+ 1" is why a fully blocked blast still hurts (exposure 0 -> impact 0).
        assertEquals(1.0d, ExplosionPhysics.rawDamageAt(1.0d, ExplosionPhysics.CRYSTAL_POWER, 0.0d), 1e-9);
        assertEquals(1.0d, ExplosionPhysics.rawDamage(0.0d, ExplosionPhysics.TNT_POWER), 1e-9);
        // ... and right at the edge of the radius it is still ~1.
        assertEquals(1.0d, ExplosionPhysics.rawDamageAt(11.999d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-3);
        // A fully blocked blast pushes nobody.
        assertEquals(0.0d, ExplosionPhysics.knockbackMagnitude(0.0d,
                ExplosionPhysics.KNOCKBACK_MULTIPLIER, 0.0d), 1e-9);
    }

    @Test
    void outsideTheRadiusNothingHappens() {
        assertFalse(ExplosionPhysics.inRadius(12.0d, ExplosionPhysics.CRYSTAL_POWER));
        assertTrue(ExplosionPhysics.inRadius(11.99d, ExplosionPhysics.CRYSTAL_POWER));
        assertEquals(0.0d, ExplosionPhysics.rawDamageAt(12.5d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
        assertEquals(0.0d, ExplosionPhysics.rawDamageAt(9.0d, ExplosionPhysics.TNT_POWER, 1.0d), 1e-9);
    }

    @Test
    void impactIsClampedToOne() {
        assertEquals(0.0d, ExplosionPhysics.impact(1.0d, 6.0d, 0.0d), 1e-9);
        assertEquals(1.0d, ExplosionPhysics.impact(0.0d, 6.0d, 1.0d), 1e-9);
        // A negative distance (numerically impossible) must not produce an impact above 1.
        assertEquals(1.0d, ExplosionPhysics.impact(-5.0d, 6.0d, 1.0d), 1e-9);
        // A zero/negative power has no radius and therefore no impact.
        assertEquals(0.0d, ExplosionPhysics.impact(1.0d, 0.0d, 1.0d), 1e-9);
    }

    /**
     * The exposure grid is the wiki's {@code ceil(2*size+1)} per axis over the entity's actual
     * bounding box (no inflation): a standing player is 3 x 5 x 3 = 45 rays, TNT (0.98 cube) is
     * 3 x 3 x 3 = 27 rays - the 27 the wiki calculator divides by.
     */
    @Test
    void exposureGridMatchesTheWikiSampleCounts() {
        assertEquals(3, ExplosionPhysics.sampleSteps(0.6d));   // player width
        assertEquals(5, ExplosionPhysics.sampleSteps(1.8d));   // player height
        assertEquals(3 * 5 * 3, 45);
        assertEquals(3, ExplosionPhysics.sampleSteps(0.98d));  // TNT / creeper cube
        assertEquals(3 * 3 * 3, 27);
        assertEquals(1.0d / 2.2d, ExplosionPhysics.sampleStep(0.6d), 1e-12);
        assertEquals(1.0d / 4.6d, ExplosionPhysics.sampleStep(1.8d), 1e-12);
        // Degenerate sizes must terminate: a zero size still yields the f = 0 and f = 1 samples,
        // a negative one (impossible box) yields none at all instead of looping forever.
        assertEquals(2, ExplosionPhysics.sampleSteps(0.0d));
        assertEquals(0, ExplosionPhysics.sampleSteps(-1.0d));
    }

    @Test
    void exposureSamplesStartAtTheBoxMinimumAndWalkTheBox() {
        // Player standing at x = 10 (feet y = 64), width 0.6: minX = 9.7, spacing 0.6/2.2.
        double minX = 10.0d - 0.6d / 2.0d;
        double spacing = 0.6d / 2.2d;
        assertEquals(minX, ExplosionPhysics.sampleCoordinate(minX, 0.6d, 0), 1e-12);
        assertEquals(minX + spacing, ExplosionPhysics.sampleCoordinate(minX, 0.6d, 1), 1e-12);
        assertEquals(minX + 2 * spacing, ExplosionPhysics.sampleCoordinate(minX, 0.6d, 2), 1e-12);
        // The last sample stays inside the box (0.6 * 2/2.2 = 0.545 < 0.6).
        assertTrue(ExplosionPhysics.sampleCoordinate(minX, 0.6d, 2) < minX + 0.6d);
        // Vertically: 5 samples from the feet to just under the head.
        assertEquals(64.0d, ExplosionPhysics.sampleCoordinate(64.0d, 1.8d, 0), 1e-12);
        assertTrue(ExplosionPhysics.sampleCoordinate(64.0d, 1.8d, 4) < 64.0d + 1.8d);
    }

    /**
     * Knockback is a velocity ADDED to the current one, with magnitude
     * {@code impact * multiplier * (1 - explosion_knockback_resistance)} - it does not subtract
     * the velocity the player already has (that would let a player running away shrink their own
     * blast push, which vanilla does not do).
     */
    @Test
    void knockbackMagnitudeScalesWithImpactMultiplierAndResistance() {
        assertEquals(0.8d, ExplosionPhysics.knockbackMagnitude(0.8d, 1.0d, 0.0d), 1e-9);
        // Blast Protection IV = 0.6 explosion knockback resistance through the attribute.
        assertEquals(0.8d * 0.4d, ExplosionPhysics.knockbackMagnitude(0.8d, 1.0d, 0.6d), 1e-9);
        // Resistance is clamped: 1.0 kills the push, above 1.0 never reverses it, below 0 never
        // amplifies it.
        assertEquals(0.0d, ExplosionPhysics.knockbackMagnitude(0.8d, 1.0d, 1.0d), 1e-9);
        assertEquals(0.0d, ExplosionPhysics.knockbackMagnitude(0.8d, 1.0d, 3.0d), 1e-9);
        assertEquals(0.8d, ExplosionPhysics.knockbackMagnitude(0.8d, 1.0d, -2.0d), 1e-9);
        // Wind charges use their own multiplier (wiki: 1.22 player-thrown, 0.6 breeze).
        assertEquals(0.8d * 1.22d, ExplosionPhysics.knockbackMagnitude(0.8d,
                ExplosionPhysics.KNOCKBACK_MULTIPLIER_WIND_CHARGE, 0.0d), 1e-9);
        assertEquals(0.8d * 0.6d, ExplosionPhysics.knockbackMagnitude(0.8d,
                ExplosionPhysics.KNOCKBACK_MULTIPLIER_BREEZE, 0.0d), 1e-9);
        assertEquals(1.0d, ExplosionPhysics.KNOCKBACK_MULTIPLIER, 1e-9);
    }

    @Test
    void vanillaPowersAreTheOnesTheWikiLists() {
        assertEquals(6.0f, ExplosionPhysics.CRYSTAL_POWER);
        assertEquals(5.0f, ExplosionPhysics.BED_POWER);
        assertEquals(5.0f, ExplosionPhysics.ANCHOR_POWER);
        assertEquals(4.0f, ExplosionPhysics.TNT_POWER);
        assertEquals(3.0f, ExplosionPhysics.CREEPER_POWER);
    }
}
