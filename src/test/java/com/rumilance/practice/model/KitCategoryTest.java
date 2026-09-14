package com.rumilance.practice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Main Kits / Sub Kits split: the category defaults to MAIN, parses safely from YAML, and
 * survives {@link KitDefinition#toBuilder()} round-trips.
 */
class KitCategoryTest {

    @Test
    void defaultsToMain() {
        KitDefinition kit = KitDefinition.builder("nodebuff").build();
        assertSame(KitCategory.MAIN, kit.category());
    }

    @Test
    void subKitsParseAndRoundTrip() {
        KitDefinition sub = KitDefinition.builder("bedpvp")
                .category(KitCategory.parse("sub"))
                .build();
        assertSame(KitCategory.SUB, sub.category());
        assertSame(KitCategory.SUB, sub.toBuilder().build().category());
    }

    @Test
    void unknownValuesFallBackToMain() {
        assertSame(KitCategory.MAIN, KitCategory.parse("bogus"));
        assertSame(KitCategory.MAIN, KitCategory.parse(null));
        assertSame(KitCategory.MAIN, KitCategory.parse(""));
        assertSame(KitCategory.SUB, KitCategory.parse(" Sub "));
    }
}
