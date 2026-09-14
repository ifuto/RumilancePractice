package com.rumilance.practice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Crystal FFA declaration: off by default, survives {@link KitDefinition#toBuilder()}
 * round-trips, and stays independent from the Main/Sub category.
 */
class KitCrystalFfaTest {

    @Test
    void defaultsToOff() {
        KitDefinition kit = KitDefinition.builder("nodebuff").build();
        assertFalse(kit.crystalFfa());
    }

    @Test
    void roundTripsThroughToBuilder() {
        KitDefinition kit = KitDefinition.builder("crystal")
                .category(KitCategory.SUB)
                .crystalFfa(true)
                .build();
        assertTrue(kit.crystalFfa());
        assertTrue(kit.toBuilder().build().crystalFfa());
        assertSame(KitCategory.SUB, kit.category());
    }

    @Test
    void canBeClearedAgain() {
        KitDefinition kit = KitDefinition.builder("crystal").crystalFfa(true).build();
        assertFalse(kit.toBuilder().crystalFfa(false).build().crystalFfa());
    }
}
