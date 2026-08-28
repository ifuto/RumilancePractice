package com.rumilance.practice.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickHealthTest {

    @BeforeEach
    @AfterEach
    void reset() {
        TickHealth.resetForTest();
    }

    @Test
    void healthyTickIsNotLagging() {
        TickHealth.record(48.0d);
        assertFalse(TickHealth.lagging());
    }

    @Test
    void slowTickIsLagging() {
        TickHealth.record(80.0d);
        assertTrue(TickHealth.lagging());
    }
}
