package com.rumilance.practice.combat;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the correction changes ONLY the spread, for every launch posture the user asked
 * about: standing throws, elytra glide, and airborne after wind-charge knockback.
 */
class ProjectileSpreadMathTest {

    private static final double EPS = 1e-9;
    private static final double PEARL_SPEED = 1.5;

    private static Vector vanillaLaunch(Vector lookUnit, Vector motion, boolean onGround) {
        // vanilla: dir*speed + (Mx, onGround ? 0 : My, Mz)
        Vector v = lookUnit.clone().normalize().multiply(PEARL_SPEED);
        v.add(new Vector(motion.getX(), onGround ? 0.0 : motion.getY(), motion.getZ()));
        return v;
    }

    @Test
    void airborneElytraThrowInheritsFullMotionIncludingVertical() {
        Vector look = new Vector(0.3, -0.1, 1.0).normalize();
        Vector glide = new Vector(0.6, -0.05, -1.2); // elytra: strong horizontal, slight sink
        Vector launch = vanillaLaunch(look, glide, false);
        Vector out = ProjectileSpreadMath.straightenThrow(launch, glide, false, look, 0.0D);
        // straightened throw = look*1.5 + FULL inherited glide motion (vanilla airborne rule)
        Vector expect = look.clone().multiply(PEARL_SPEED).add(glide);
        assertEquals(expect.getX(), out.getX(), EPS);
        assertEquals(expect.getY(), out.getY(), EPS);
        assertEquals(expect.getZ(), out.getZ(), EPS);
    }

    @Test
    void airborneAfterWindChargeKnockbackKeepsInheritanceButNoExtraBoost() {
        Vector look = new Vector(0.0, 0.2, 1.0).normalize();
        Vector burst = new Vector(1.8, 2.4, 0.5); // wind burst launch: mostly upward knockback
        Vector launch = vanillaLaunch(look, burst, false);
        Vector out = ProjectileSpreadMath.straightenThrow(launch, burst, false, look, 0.0D);
        // Thrown part must stay exactly 1.5 long — the correction must neither boost nor slow.
        Vector thrown = out.clone().subtract(burst);
        assertEquals(PEARL_SPEED, thrown.length(), 1e-6);
        Vector expect = look.clone().multiply(PEARL_SPEED).add(burst);
        assertEquals(expect.getX(), out.getX(), 1e-9);
        assertEquals(expect.getY(), out.getY(), 1e-9);
        assertEquals(expect.getZ(), out.getZ(), 1e-9);
    }

    @Test
    void standingThrowMustObeyVanillaGroundRuleAndNotSink() {
        Vector look = new Vector(0.0, -0.05, 1.0).normalize();
        // standing player carries the gravity-sink Y server side; vanilla excludes it
        Vector standingMotion = new Vector(0.0, -0.0784, 0.0);
        Vector launch = vanillaLaunch(look, standingMotion, true);
        Vector out = ProjectileSpreadMath.straightenThrow(launch, standingMotion, true, look, 0.0D);
        Vector expected = look.clone().multiply(PEARL_SPEED); // motion contributes nothing (0,0,0)
        assertEquals(expected.getX(), out.getX(), 1e-9);
        assertEquals(expected.getY(), out.getY(), 1e-9);
        assertEquals(expected.getZ(), out.getZ(), 1e-9);
    }

    @Test
    void groundedHorizontalMotionStillInheritsButNotVertical() {
        Vector look = new Vector(1.0, 0.0, 0.0);
        Vector sprint = new Vector(0.25, -0.0784, 0.0); // sprinting on ground with sink-Y
        Vector launch = vanillaLaunch(look, sprint, true);
        Vector out = ProjectileSpreadMath.straightenThrow(launch, sprint, true, look, 0.0D);
        Vector expect = look.clone().multiply(PEARL_SPEED).add(new Vector(0.25, 0.0, 0.0));
        assertEquals(expect.getX(), out.getX(), EPS);
        assertEquals(expect.getY(), out.getY(), 1e-9);
        assertEquals(0.0D, out.getY(), 1e-9);
        assertEquals(expect.getZ(), out.getZ(), EPS);
    }

    @Test
    void fullVanillaSpreadLeavesVelocityUntouched() {
        Vector look = new Vector(0.4, 0.3, 0.866).normalize();
        Vector motion = new Vector(-0.2, 0.5, 0.1);
        Vector launch = vanillaLaunch(look, motion, false).add(new Vector(0.01, -0.02, 0.015));
        Vector out = ProjectileSpreadMath.straightenThrow(launch, motion, false, look, 1.0D);
        assertEquals(launch.getX(), out.getX(), EPS);
        assertEquals(launch.getY(), out.getY(), EPS);
        assertEquals(launch.getZ(), out.getZ(), EPS);
    }

    @Test
    void partialSpreadDoesNotChangeThrownMagnitude() {
        Vector look = new Vector(0.2, 0.0, 1.0).normalize();
        Vector motion = new Vector(0.1, -0.0784, 0.05);
        // simulate a vanilla noised launch direction slightly off the look vector
        Vector noisyLook = look.clone().add(new Vector(0.02, -0.01, 0.015)).normalize();
        Vector launch = vanillaLaunch(noisyLook, motion, true);
        Vector out = ProjectileSpreadMath.straightenThrow(launch, motion, true, look, 0.5D);
        Vector thrown = out.clone().subtract(new Vector(0.1, 0.0, 0.05));
        assertEquals(PEARL_SPEED, thrown.length(), 1e-6);
        // and the direction must sit strictly between look and noised direction
        assertTrue(thrown.clone().normalize().dot(look) > noisyLook.dot(look),
                "partial spread should pull the direction toward the crosshair");
    }
}
