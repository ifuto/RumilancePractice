package com.rumilance.practice.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbySafetyTest {

    @Test
    void defaultFallReturnBelowSpawnStaysAsConfigured() {
        assertEquals(0.0d, LobbySafety.voidReturnY(0.0d, 65.0d, -64), 0.001d);
    }

    @Test
    void fallReturnAtOrAboveSpawnFallsBackToWorldMin() {
        assertEquals(-63.0d, LobbySafety.voidReturnY(65.0d, 65.0d, -64), 0.001d);
        assertEquals(-63.0d, LobbySafety.voidReturnY(100.0d, 65.0d, -64), 0.001d);
        assertEquals(-63.0d, LobbySafety.voidReturnY(64.5d, 65.0d, -64), 0.001d);
    }

    @Test
    void fallReturnWellBelowSpawnIsKept() {
        assertEquals(40.0d, LobbySafety.voidReturnY(40.0d, 65.0d, -64), 0.001d);
    }

    @Test
    void missingSpawnUsesConfiguredValue() {
        assertEquals(12.0d, LobbySafety.voidReturnY(12.0d, null, -64), 0.001d);
        assertTrue(LobbySafety.voidReturnY(80.0d, 80.0d, null) < 80.0d);
    }
}
