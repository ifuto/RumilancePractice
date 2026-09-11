package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The practice room must never be able to produce the "buried but unhittable" state: the
 * finishing tick of a suffocation death has to be able to land, and a player being crushed or
 * drowned must keep taking their damage — room protection is for stray drill damage, not for
 * making anyone invincible.
 */
class PracticeRoomDamagePolicyTest {

    @Test
    void suffocationAlwaysTicksEvenWhenSurvivable() {
        assertTrue(PracticeRoomDamagePolicy.isEnvironmentalDot("SUFFOCATION"));
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("SUFFOCATION", 10.0d),
                "buried players keep ticking — no invincible walls");
    }

    @Test
    void drowningAlwaysTicksEvenWhenSurvivable() {
        assertTrue(PracticeRoomDamagePolicy.isEnvironmentalDot("DROWNING"));
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("DROWNING", 10.0d));
    }

    @Test
    void lethalSuffocationResolvesToARealDeath() {
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("SUFFOCATION", 0.0d));
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("SUFFOCATION", -2.0d),
                "overkill cases must also reach the death pipeline");
    }

    @Test
    void anyLethalFrameResolvesRegardlessOfCause() {
        // A silent cancel of ANY lethal frame is exactly the 0-HP zombie the fix removes.
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("FALL", 0.0));
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("STARVATION", -0.5));
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("VOID", -1000.0));
        assertFalse(PracticeRoomDamagePolicy.mayCancelRoomDamage("FIRE_TICK", 0.0));
    }

    @Test
    void ordinaryDrillDamageStaysCancelledWhenSurvivable() {
        // Classic room protection: drills cancel stray non-combat damage below the lethal line.
        assertTrue(PracticeRoomDamagePolicy.mayCancelRoomDamage("FALL", 12.0d));
        assertTrue(PracticeRoomDamagePolicy.mayCancelRoomDamage("STARVATION", 19.0d));
        assertTrue(PracticeRoomDamagePolicy.mayCancelRoomDamage("VOID", 15.25d));
    }

    @Test
    void nonDotCausesAreNotClassifiedAsEnvironmental() {
        assertFalse(PracticeRoomDamagePolicy.isEnvironmentalDot("FALL"));
        assertFalse(PracticeRoomDamagePolicy.isEnvironmentalDot("LAVA"));
        assertFalse(PracticeRoomDamagePolicy.isEnvironmentalDot("ENTITY_ATTACK"));
    }

    @Test
    void nullAndUnknownCauseNamesStayCancellableBelowLethal() {
        assertTrue(PracticeRoomDamagePolicy.mayCancelRoomDamage("", 8.0d));
        assertFalse(PracticeRoomDamagePolicy.isEnvironmentalDot(null),
                "null must be safe (defends the listener's null-guarded cause)");
    }
}
