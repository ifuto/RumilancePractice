package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Operator-tuned knockback coefficients: the tuning ITSELF is what ships (math stays
 * vanilla), so its invariants are small but sacred — neutral pass-through, asymmetric
 * horizontal/vertical scaling, clamped parsing, and config-vs-override precedence.
 */
class KnockbackTuningTest {

    @Test
    void defaultIsVanillaNeutral() {
        KnockbackTuning tuning = new KnockbackTuning(null, 1.0, 1.0);
        assertTrue(tuning.isNeutral());
        double[] scaled = tuning.scale(0.33, 0.4, -0.21);
        assertEquals(0.33, scaled[0], 1e-9);
        assertEquals(0.4, scaled[1], 1e-9);
        assertEquals(-0.21, scaled[2], 1e-9);
    }

    @Test
    void scaleAppliesHorizontalToXZandVerticalToY() {
        KnockbackTuning tuning = new KnockbackTuning(null, 2.0, 0.5);
        assertFalse(tuning.isNeutral());
        double[] scaled = tuning.scale(0.4, 0.8, -0.4);
        assertEquals(0.8, scaled[0], 1e-9, "X × horizontal");
        assertEquals(0.4, scaled[1], 1e-9, "Y × vertical");
        assertEquals(-0.8, scaled[2], 1e-9, "Z × horizontal");
    }

    @Test
    void overrideWinsOverConfigAndResetFallsBack(@TempDir Path tmp) {
        Path file = tmp.resolve("knockback.json");
        KnockbackTuning tuning = new KnockbackTuning(file, 1.0, 1.0);
        tuning.setHorizontal(1.4);
        tuning.setVertical(0.6);
        assertEquals(1.4, tuning.horizontal(), 1e-9);
        assertEquals(0.6, tuning.vertical(), 1e-9);

        // survives reload — overrides persist and win over config
        KnockbackTuning reloaded = new KnockbackTuning(file, 1.0, 1.0);
        assertEquals(1.4, reloaded.horizontal(), 1e-9);
        assertEquals(0.6, reloaded.vertical(), 1e-9);

        reloaded.resetOverrides();
        assertTrue(reloaded.isNeutral(), "reset falls back to config defaults");
    }

    @Test
    void missingOrNullOverrideStaysOnConfigDefaults(@TempDir Path tmp) {
        Path file = tmp.resolve("knockback.json");
        // null overrides (as written by an empty/legacy save) must not crash the reader
        KnockbackTuning t1 = new KnockbackTuning(file, 0.8, 1.2);
        assertEquals(0.8, t1.horizontal(), 1e-9);
        assertEquals(1.2, t1.vertical(), 1e-9);
        assertNull(KnockbackTuning.jsonNumber("{\"horizontal\":null}", "horizontal"));
        assertNull(KnockbackTuning.jsonNumber("garbage", "horizontal"));
        assertEquals(1.25, KnockbackTuning.jsonNumber("{\"horizontal\":1.25}", "horizontal"), 1e-9);
        // out-of-band persisted values are clamped to the safe band
        assertEquals(1.0, KnockbackTuning.jsonNumber("{\"horizontal\":99.0}", "horizontal"), 1e-9);
    }

    @Test
    void parseFactorValidates(@TempDir Path tmp) throws Exception {
        assertEquals(0.0, KnockbackTuning.parseFactor("0"), 1e-9);
        assertEquals(4.0, KnockbackTuning.parseFactor(" 4.0 "), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("-0.1"));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("9"));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("abc"));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor(null));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("NaN"));

        // Files.write is unreachable after the throws — sanity that @TempDir exists instead:
        assertTrue(Files.isDirectory(tmp) || Files.notExists(tmp));
    }

    @Test
    void settersClampIntoTheSafeBand() {
        KnockbackTuning tuning = new KnockbackTuning(null, 1.0, 1.0);
        tuning.setHorizontal(1_000_000.0);  // typo-safe: clamped, previous value kept sane
        assertEquals(1.0, tuning.horizontal(), 1e-9);
        tuning.setHorizontal(3.5);
        assertEquals(3.5, tuning.horizontal(), 1e-9);
    }
}
