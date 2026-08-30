package com.rumilance.practice.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitDefinitionArenasTest {

    @Test
    void emptyArenasMeansAnyFreeDuelArena() {
        KitDefinition kit = KitDefinition.builder("nodebuff").build();
        assertTrue(kit.usesAnyArena());
        assertFalse(kit.hasFixedArena());
        assertEquals("", kit.arenaName());
    }

    @Test
    void arenaNameSetterCreatesSingleEntryPool() {
        KitDefinition kit = KitDefinition.builder("nodebuff").arenaName("castle").build();
        assertEquals(List.of("castle"), kit.arenas());
        assertEquals("castle", kit.arenaName());
    }

    @Test
    void arenasDedupesAndLowercases() {
        KitDefinition kit = KitDefinition.builder("nodebuff")
                .arenas(List.of("Castle", "castle", "  river  ", ""))
                .build();
        assertEquals(List.of("castle", "river"), kit.arenas());
    }
}
