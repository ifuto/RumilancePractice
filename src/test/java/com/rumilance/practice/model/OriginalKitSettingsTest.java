package com.rumilance.practice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serialisation round-trips and vanilla-default invariants for {@link OriginalKitSettings}.
 */
class OriginalKitSettingsTest {

    @Test
    void defaultsAreVanillaHandsoffAndSerializeRoundTripsIdentically() {
        OriginalKitSettings settings = OriginalKitSettings.defaults();
        assertEquals(settings, OriginalKitSettings.parse(settings.serialize()));
    }

    @Test
    void everySwitchFlipsAndRoundTrips() {
        OriginalKitSettings settings = OriginalKitSettings.defaults()
                .with("fallDamage", false)
                .with("totem", false)
                .with("pearl", false)
                .with("naturalRegen", false)
                .with("autoFood", true)
                .with("swordShieldBreak", true)
                .with("blockPlace", true)
                .with("blockBreak", true)
                .with("bedExplosion", true)
                .with("forceAdventure", true)
                .withMaxHealth(32.0d)
                .withTimeoutSeconds(300)
                .withBodyScale(1.6d);
        assertFalse(settings.fallDamage());
        assertFalse(settings.totem());
        assertFalse(settings.pearl());
        assertFalse(settings.naturalRegen());
        assertTrue(settings.autoFood());
        assertTrue(settings.swordShieldBreak());
        assertTrue(settings.blockPlace());
        assertTrue(settings.blockBreak());
        assertTrue(settings.bedExplosion());
        assertTrue(settings.forceAdventure());
        assertEquals(32.0d, settings.maxHealth());
        assertEquals(300, settings.timeoutSeconds());
        assertEquals(1.6d, settings.bodyScale());

        OriginalKitSettings restored = OriginalKitSettings.parse(settings.serialize());
        assertEquals(settings, restored);
    }

    @Test
    void blankOrGarbageFallsBackToDefaults() {
        assertEquals(OriginalKitSettings.defaults(), OriginalKitSettings.parse(null));
        assertEquals(OriginalKitSettings.defaults(), OriginalKitSettings.parse(""));
        assertEquals(OriginalKitSettings.defaults(), OriginalKitSettings.parse("   "));
        // Unknown keys ignored, missing keys default.
        OriginalKitSettings partial = OriginalKitSettings.parse("fallDamage=0;unknown=1");
        assertFalse(partial.fallDamage());
        assertTrue(partial.totem());
    }

    @Test
    void maxHealthIsClampedToSaneBounds() {
        assertEquals(OriginalKitSettings.MIN_MAX_HEALTH,
                OriginalKitSettings.defaults().withMaxHealth(0.5d).maxHealth());
        assertEquals(OriginalKitSettings.MAX_MAX_HEALTH,
                OriginalKitSettings.defaults().withMaxHealth(12345d).maxHealth());
        assertEquals(OriginalKitSettings.DEFAULT_MAX_HEALTH,
                OriginalKitSettings.defaults().withMaxHealth(Double.NaN).maxHealth());
    }

    @Test
    void bodyScaleIsClampedToSaneBounds() {
        assertEquals(OriginalKitSettings.MIN_BODY_SCALE,
                OriginalKitSettings.defaults().withBodyScale(0.01d).bodyScale());
        assertEquals(OriginalKitSettings.MAX_BODY_SCALE,
                OriginalKitSettings.defaults().withBodyScale(99.0d).bodyScale());
        assertEquals(OriginalKitSettings.DEFAULT_BODY_SCALE,
                OriginalKitSettings.defaults().withBodyScale(Double.NaN).bodyScale());
        assertEquals(OriginalKitSettings.DEFAULT_BODY_SCALE,
                OriginalKitSettings.defaults().withBodyScale(Double.POSITIVE_INFINITY).bodyScale());
    }
}
