package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Operator-tuned knockback coefficients (KBM-style profiles). The tuning ITSELF is what
 * ships (the math stays vanilla), so its invariants are small but sacred: neutral
 * pass-through, asymmetric horizontal/vertical scaling, precedence kit > cause > global,
 * fixable botching via clamping, and JSON round-trip persistence with v1 legacy support.
 */
class KnockbackTuningTest {

    private static KnockbackTuning neutral() {
        return new KnockbackTuning(null, 1.0, 1.0, Map.of(), Map.of());
    }

    @Test
    void defaultIsVanillaNeutral() {
        KnockbackTuning tuning = neutral();
        assertTrue(tuning.isNeutral());
        assertTrue(tuning.isNeutralFor("ENTITY_ATTACK", "nodebuff"));
        double[] scaled = tuning.scale("ENTITY_ATTACK", "nodebuff", 0.33, 0.4, -0.21);
        assertEquals(0.33, scaled[0], 1e-9);
        assertEquals(0.4, scaled[1], 1e-9);
        assertEquals(-0.21, scaled[2], 1e-9);
    }

    @Test
    void scaleAppliesHorizontalToXZandVerticalToY() {
        KnockbackTuning tuning = new KnockbackTuning(null, 2.0, 0.5, Map.of(), Map.of());
        double[] scaled = tuning.scale("DAMAGE", "boxing", 0.4, 0.8, -0.4);
        assertEquals(0.8, scaled[0], 1e-9, "X × horizontal");
        assertEquals(0.4, scaled[1], 1e-9, "Y × vertical");
        assertEquals(-0.8, scaled[2], 1e-9, "Z × horizontal");
    }

    @Test
    void precedenceIsKitOverCauseOverGlobal() {
        KnockbackTuning tuning = new KnockbackTuning(null,
                1.0, 1.0,
                Map.of("ENTITY_ATTACK", new double[]{1.5, 1.5},
                        "EXPLOSION", new double[]{0.5, 0.5}),
                Map.of("boxing", new double[]{0.8, 0.6}));
        assertFalse(tuning.isNeutral(), "configured profiles break neutrality");

        var f = tuning.effective("ENTITY_ATTACK", "BOXING");
        assertEquals(0.8, f.horizontal(), 1e-9, "kit profile wins (case-insensitive)");
        assertEquals(0.6, f.vertical(), 1e-9);

        f = tuning.effective("ENTITY_ATTACK", "nodebuff");
        assertEquals(1.5, f.horizontal(), 1e-9, "no kit profile → cause override");
        f = tuning.effective("EXPLOSION", null);
        assertEquals(0.5, f.vertical(), 1e-9, "cause override without kit");
        f = tuning.effective("DAMAGE", null);
        assertEquals(1.0, f.horizontal(), 1e-9, "unconfigured cause → global");
        assertTrue(f.isNeutral());
    }

    @Test
    void runtimeOverrideWinsOverConfigAndPersists(@TempDir Path tmp) throws Exception {
        java.nio.file.Path file = tmp.resolve("knockback.json");
        KnockbackTuning tuning = new KnockbackTuning(file, 1.0, 1.0,
                Map.of("ENTITY_ATTACK", new double[]{1.5, 1.5}), Map.of());
        tuning.setGlobal(1.4, 0.6);
        tuning.setCause("explosion", 0.7, 0.3);
        tuning.setKit("NodeBuff", 1.1, 0.9);

        KnockbackTuning reloaded = new KnockbackTuning(file, 1.0, 1.0,
                Map.of("ENTITY_ATTACK", new double[]{1.5, 1.5}), Map.of());
        assertEquals(1.4, reloaded.global().horizontal(), 1e-9, "global override persists");
        assertEquals(0.6, reloaded.global().vertical(), 1e-9);
        var f = reloaded.effective("ENTITY_ATTACK", "nodebuff");
        assertEquals(1.1, f.horizontal(), 1e-9, "kit override (saved lowercase) wins");
        f = reloaded.effective("EXPLOSION", "");
        assertEquals(0.7, f.horizontal(), 1e-9, "cause override persists (uppercased key)");
        f = reloaded.effective("ENTITY_ATTACK", "");
        assertEquals(1.5, f.horizontal(), 1e-9, "runtime overrides fall back to config");

        reloaded.resetOverrides();
        assertEquals(1.0, reloaded.global().horizontal(), 1e-9);
        assertEquals(1.5, reloaded.effective("ENTITY_ATTACK", "").horizontal(), 1e-9,
                "config cause overrides survive a runtime reset");
    }

    @Test
    void legacyV1ShapeStillLoads(@TempDir Path tmp) throws Exception {
        java.nio.file.Path file = tmp.resolve("knockback.json");
        Files.writeString(file,
                "{\"horizontal\":1.3,\"vertical\":0.7}\n", java.nio.charset.StandardCharsets.UTF_8);
        KnockbackTuning tuning = new KnockbackTuning(file, 1.0, 1.0, Map.of(), Map.of());
        assertEquals(1.3, tuning.horizontal(), 1e-9);
        assertEquals(0.7, tuning.vertical(), 1e-9);
    }

    @Test
    void parseFactorValidates() {
        assertEquals(0.0, KnockbackTuning.parseFactor("0"), 1e-9);
        assertEquals(4.0, KnockbackTuning.parseFactor(" 4.0 "), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("-0.1"));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("9"));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("abc"));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor(null));
        assertThrows(IllegalArgumentException.class, () -> KnockbackTuning.parseFactor("NaN"));
    }

    @Test
    void settersClampIntoTheSafeBand() {
        KnockbackTuning tuning = neutral();
        tuning.setGlobal(1_000_000.0, 1.0);  // typo-safe: clamped, previous value kept sane
        assertEquals(1.0, tuning.horizontal(), 1e-9);
        tuning.setGlobal(3.5, tuning.vertical());
        assertEquals(3.5, tuning.horizontal(), 1e-9);
        tuning.setCause("ATTACK", 99, 1.0);
        assertEquals(KnockbackTuning.NEUTRAL, tuning.effective("ATTACK", "").horizontal(), 1e-9,
                "out-of-band stored values fall back to neutral via clamp");
    }
}
