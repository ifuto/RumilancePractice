package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla explosion math (minecraft.wiki "Explosion" / Mojang {@code Explosion#finalizeExplosion}).
 * The reference numbers are the wiki's maximum entity damages: crystal & charged creeper 85,
 * bed & respawn anchor 71, TNT 57, creeper 43, fireball 15.
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
        // impact = (1 - 0/2p) * 1 = 1 -> damage = (1 + 1)/2 * 7 * 2p + 1 = 14p + 1
        assertEquals(85.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
        assertEquals(71.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.BED_POWER, 1.0d), 1e-9);
        assertEquals(71.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.ANCHOR_POWER, 1.0d), 1e-9);
        assertEquals(57.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.TNT_POWER, 1.0d), 1e-9);
        assertEquals(43.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.CREEPER_POWER, 1.0d), 1e-9);
    }

    @Test
    void damageFallsOffWithDistanceAndExposure() {
        // Crystal (2p = 12): 6 blocks away -> impact 0.5 -> (0.25+0.5)/2*84 + 1 = 32.5 -> (int) 32
        assertEquals(32.0d, ExplosionPhysics.rawDamageAt(6.0d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
        // Half covered at point blank: impact 0.5 -> (0.25+0.5)/2*84 + 1 = 32.5 -> 32
        assertEquals(32.0d, ExplosionPhysics.rawDamageAt(0.0d, ExplosionPhysics.CRYSTAL_POWER, 0.5d), 1e-9);
        // Bed (2p = 10), 5 blocks: impact 0.5 -> (0.25+0.5)/2*70 + 1 = 27.25 -> 27
        assertEquals(27.0d, ExplosionPhysics.rawDamageAt(5.0d, ExplosionPhysics.BED_POWER, 1.0d), 1e-9);
    }

    @Test
    void outsideTheRadiusNothingHappens() {
        assertFalse(ExplosionPhysics.inRadius(12.0d, ExplosionPhysics.CRYSTAL_POWER));
        assertTrue(ExplosionPhysics.inRadius(11.99d, ExplosionPhysics.CRYSTAL_POWER));
        assertEquals(0.0d, ExplosionPhysics.rawDamageAt(12.5d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
        // Edge of the radius still deals the guaranteed 1 point (impact ~ 0 -> +1).
        assertEquals(1.0d, ExplosionPhysics.rawDamageAt(11.999d, ExplosionPhysics.CRYSTAL_POWER, 1.0d), 1e-9);
    }

    @Test
    void damageIsTruncatedNotRounded() {
        // (impact^2 + impact)/2 * 7 * 2p + 1 is cast to int in vanilla: 32.5 -> 32, not 33.
        double impact = ExplosionPhysics.impact(6.0d, ExplosionPhysics.CRYSTAL_POWER, 1.0d);
        assertEquals(0.5d, impact, 1e-9);
        assertEquals(32.0d, ExplosionPhysics.rawDamage(impact, ExplosionPhysics.CRYSTAL_POWER), 1e-9);
    }

    @Test
    void exposureGridIsTheVanillaTwelvePointPlayerGrid() {
        // Standing player 0.6 x 1.8 inflated by 0.6 -> 1.8 x 3.0 x 1.8
        // steps = ceil((2*(0.6+0.3))/3) = 2 on X/Z, ceil((2*(0.6+0.9))/3) = 3 on Y -> 12 rays.
        assertEquals(2, ExplosionPhysics.sampleSteps(0.6d));
        assertEquals(3, ExplosionPhysics.sampleSteps(1.8d));
        assertEquals(2 * 3 * 2, 12);
        // Sample coordinates span the inflated box: min + (0.5 + i)/steps * size.
        double min = 10.0d - 0.3d - ExplosionPhysics.EXPOSURE_MARGIN; // feet X - half width - margin
        double size = 0.6d + 2 * ExplosionPhysics.EXPOSURE_MARGIN;
        assertEquals(min + 0.25d * size, ExplosionPhysics.sampleCoordinate(min, size, 2, 0), 1e-9);
        assertEquals(min + 0.75d * size, ExplosionPhysics.sampleCoordinate(min, size, 2, 1), 1e-9);
    }

    @Test
    void knockbackIsTheVanillaDeltaNotAStraightImpulse() {
        // Standing still (velocity dot dir = 0): full impact along the direction.
        assertEquals(0.6d, ExplosionPhysics.knockbackDelta(0.8d, 0.75d, 0.0d, 0.0d), 1e-9);
        // Already flying away at the same speed as the blast: no extra knockback at all.
        assertEquals(0.0d, ExplosionPhysics.knockbackDelta(0.8d, 0.75d, 0.75d, 0.0d), 1e-9);
        // Running INTO the blast (negative dot) is punished harder than standing still.
        assertTrue(ExplosionPhysics.knockbackDelta(0.8d, 0.75d, -0.4d, 0.0d)
                > ExplosionPhysics.knockbackDelta(0.8d, 0.75d, 0.0d, 0.0d));
        // Blast Protection IV = 0.6 explosion knockback resistance -> 40% of the impulse.
        assertEquals(0.6d * 0.4d, ExplosionPhysics.knockbackDelta(0.8d, 0.75d, 0.0d, 0.6d), 1e-9);
        // Resistance is clamped: 1.0 kills the knockback, above 1.0 never reverses it.
        assertEquals(0.0d, ExplosionPhysics.knockbackDelta(0.8d, 0.75d, 0.0d, 1.0d), 1e-9);
        assertEquals(0.0d, ExplosionPhysics.knockbackDelta(0.8d, 0.75d, 0.0d, 3.0d), 1e-9);
    }

    @Test
    void impactIsClampedAndZeroWithoutExposure() {
        assertEquals(0.0d, ExplosionPhysics.impact(1.0d, 6.0d, 0.0d), 1e-9);
        assertEquals(1.0d, ExplosionPhysics.impact(0.0d, 6.0d, 1.0d), 1e-9);
        // A negative distance (numerically impossible, but) must not produce impact > 1.
        assertEquals(1.0d, ExplosionPhysics.impact(-5.0d, 6.0d, 1.0d), 1e-9);
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
