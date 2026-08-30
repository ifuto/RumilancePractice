package com.rumilance.practice.combat;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KnockbackProfileTest {

    @Test
    void vanillaWalkHitIsExactlyConfiguredBase() {
        Vector out = KnockbackProfile.VANILLA.apply(
                0.0d, 0.0d, 0.0d,
                1.0d, 0.0d,
                0.0d, 1.0d,
                0.0d, 0.0d,
                false, true, 0);
        assertEquals(0.4d, out.getX(), 1.0e-9d);
        assertEquals(0.4d, out.getY(), 1.0e-9d);
        assertEquals(0.0d, out.getZ(), 1.0e-9d);
    }

    @Test
    void attackKnockbackOneDoesNotInflateBase() {
        KnockbackProfile profile = KnockbackProfile.CLUB;
        assertEquals(1.0d, profile.attackKnockback(), 1.0e-9d);
        Vector out = profile.apply(
                0, 0, 0,
                1, 0,
                1, 0,
                0, 0,
                false, true, 0);
        assertEquals(0.4d, out.getX(), 1.0e-9d);
        assertEquals(0.36075d, out.getY(), 1.0e-9d);
        assertFalse(out.getX() > 0.41d, "attack-knockback 1.0 must not become ~1.3 horizontal");
    }

    @Test
    void clubSprintExtraUsesLookDirectionForKbControl() {
        Vector out = KnockbackProfile.CLUB.apply(
                0, 0, 0,
                1, 0,
                0, 1,
                0, 0,
                true, true, 0);
        assertEquals(0.4d, out.getX(), 1.0e-9d);
        assertEquals(0.5d, out.getZ(), 1.0e-9d);
        assertEquals(0.36075d + 0.1d, out.getY(), 1.0e-9d);
    }

    @Test
    void clubAlignedSprintIsBasePlusLookExtraNotMultiplied() {
        Vector out = KnockbackProfile.CLUB.apply(
                0, 0, 0,
                1, 0,
                1, 0,
                0, 0,
                true, true, 0);
        assertEquals(0.9d, out.getX(), 1.0e-9d);
        assertEquals(0.46075d, out.getY(), 1.0e-9d);
    }

    @Test
    void enchantAddsBonusLevelsNotBaseMultiplier() {
        Vector walkKb1 = KnockbackProfile.VANILLA.apply(
                0, 0, 0, 1, 0, 0, 1, 0, 0, false, true, 1);
        assertEquals(0.4d, walkKb1.getX(), 1.0e-9d);
        assertEquals(0.5d, walkKb1.getZ(), 1.0e-9d);
        assertEquals(0.5d, walkKb1.getY(), 1.0e-9d);
    }

    @Test
    void verticalLimitCapsBeforeSprintExtra() {
        KnockbackProfile high = new KnockbackProfile(
                1.0d, 0.4d, 0.8d, 0.0d, 0.5d, 0.1d, 0.4d, 0.0d, 0.0d, 0.5d,
                KnockbackProfile.Direction.RELATIVE, 8.0d);
        Vector walk = high.apply(0, 0, 0, 1, 0, 1, 0, 0, 0, false, true, 0);
        assertEquals(0.4d, walk.getY(), 1.0e-9d);
        Vector sprint = high.apply(0, 0, 0, 1, 0, 1, 0, 0, 0, true, true, 0);
        assertEquals(0.5d, sprint.getY(), 1.0e-9d);
    }

    @Test
    void frictionKeepsHalfExistingMotion() {
        KnockbackProfile frictionOnly = new KnockbackProfile(
                1.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.4d, 0.0d, 0.0d, 0.5d,
                KnockbackProfile.Direction.RELATIVE, 8.0d);
        Vector out = frictionOnly.apply(
                0.8d, 0.2d, -0.4d,
                1, 0,
                1, 0,
                0, 0,
                false, false, 0);
        assertEquals(0.4d, out.getX(), 1.0e-9d);
        assertEquals(0.1d, out.getY(), 1.0e-9d);
        assertEquals(-0.2d, out.getZ(), 1.0e-9d);
    }

    @Test
    void clubAirAddsConfiguredAirVertical() {
        Vector out = KnockbackProfile.CLUB.apply(
                0, 0.1d, 0,
                1, 0,
                1, 0,
                0, 0,
                false, false, 0);
        assertEquals(0.4d, out.getX(), 1.0e-9d);
        assertEquals(0.1d * 0.5d + 0.24775d, out.getY(), 1.0e-9d);
    }

    @Test
    void bundledPresetsAreDistinct() {
        assertFalse(KnockbackProfile.VANILLA.sameCoefficients(KnockbackProfile.CLUB));
        assertFalse(KnockbackProfile.CLUB.sameCoefficients(KnockbackProfile.STRAY));
        assertEquals(0.36075d, KnockbackProfile.CLUB.verticalKb(), 1.0e-9d);
        assertEquals(0.24775d, KnockbackProfile.CLUB.airVerticalKb(), 1.0e-9d);
        assertEquals(0.675d, KnockbackProfile.CLUB.verticalLimit(), 1.0e-9d);
        assertEquals(0.4d, KnockbackProfile.VANILLA.verticalLimit(), 1.0e-9d);
    }

    @Test
    void parseDirectionAcceptsAliases() {
        assertEquals(KnockbackProfile.Direction.ATTACKER_LOOK,
                KnockbackProfile.parseDirection("attacker_look"));
        assertEquals(KnockbackProfile.Direction.RELATIVE,
                KnockbackProfile.parseDirection("relative"));
    }
}
