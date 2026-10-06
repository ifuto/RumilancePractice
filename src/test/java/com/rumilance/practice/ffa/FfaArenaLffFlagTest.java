package com.rumilance.practice.ffa;

import com.rumilance.practice.ffa.FfaService.FfaArena;
import com.rumilance.practice.util.Cuboid;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirror of {@link FfaArenaBotFlagTest} for the LFF flag ({@code arenas.<id>.settings.lff},
 * default OFF — the opt-in switch an admin flips in {@code /ffa settings <arena>}).
 * {@link FfaArena} is a wide record with a copy method per field, and every copy has to carry
 * the flag along: one that dropped it would silently stop forcing the toggle item into the
 * ninth kit slot.
 *
 * <p>These tests need no server: the record's compact constructor only clamps and null-checks,
 * and {@code Cuboid.of(String, ...)} does not touch Bukkit.</p>
 */
final class FfaArenaLffFlagTest {

    private static FfaArena arena(boolean lffEnabled) {
        return new FfaArena(
                "crystal",
                "crystal-kit",
                "ffa",
                Cuboid.of("ffa", 0, 60, 0, 40, 90, 40),
                null,
                true,
                30,
                "IRON_SWORD",
                true,
                true,
                true,
                true,
                true,
                true,
                List.of("STONE"),
                false,
                false,
                lffEnabled, null); // child kit
    }

    /** Every copy method except withLff itself, so the loop cannot miss one. */
    private static Map<String, UnaryOperator<FfaArena>> copies() {
        Map<String, UnaryOperator<FfaArena>> all = new LinkedHashMap<>();
        all.put("withResetInterval", a -> a.withResetInterval(99));
        all.put("withEnabled", a -> a.withEnabled(false));
        all.put("withKit", a -> a.withKit("other-kit"));
        all.put("withRegion", a -> a.withRegion(Cuboid.of("ffa", 1, 2, 3, 4, 5, 6)));
        all.put("withSpawn", a -> a.withSpawn(null));
        all.put("withId", a -> a.withId("renamed"));
        all.put("withTpa", a -> a.withTpa(false));
        all.put("withRtp", a -> a.withRtp(false));
        all.put("withRtpQueue", a -> a.withRtpQueue(false));
        all.put("withBlockPlace", a -> a.withBlockPlace(false));
        all.put("withBlockBreak", a -> a.withBlockBreak(false));
        all.put("withBreakPlayerPlacedOnly", a -> a.withBreakPlayerPlacedOnly(false));
        all.put("withCanBreak", a -> a.withCanBreak(List.of()));
        all.put("withIconMaterial", a -> a.withIconMaterial("GOLD_SWORD"));
        all.put("withBot", a -> a.withBot(true));
        all.put("withFreehitGuard", a -> a.withFreehitGuard(true));
        all.put("withInnerKit", a -> a.withInnerKit("preset-a"));
        return all;
    }

    @Test
    void arenaCarriesTheFlagItWasBuiltWith() {
        assertFalse(arena(false).lffEnabled(), "OFF stays OFF");
        assertTrue(arena(true).lffEnabled(), "ON stays ON");
    }

    @Test
    void withLffFlipsOnlyThatFlag() {
        FfaArena before = arena(false);
        FfaArena after = before.withLff(true);

        assertTrue(after.lffEnabled());
        assertEquals(before.id(), after.id());
        assertEquals(before.kitId(), after.kitId());
        assertEquals(before.world(), after.world());
        assertEquals(before.region(), after.region());
        assertEquals(before.spawn(), after.spawn());
        assertEquals(before.enabled(), after.enabled());
        assertEquals(before.resetIntervalSeconds(), after.resetIntervalSeconds());
        assertEquals(before.iconMaterial(), after.iconMaterial());
        assertEquals(before.tpaEnabled(), after.tpaEnabled());
        assertEquals(before.rtpEnabled(), after.rtpEnabled());
        assertEquals(before.rtpQueueEnabled(), after.rtpQueueEnabled());
        assertEquals(before.blockPlace(), after.blockPlace());
        assertEquals(before.blockBreak(), after.blockBreak());
        assertEquals(before.breakPlayerPlacedOnly(), after.breakPlayerPlacedOnly());
        assertEquals(before.canBreak(), after.canBreak());
        assertEquals(before.botEnabled(), after.botEnabled());
        assertEquals(before.freehitGuard(), after.freehitGuard());

        assertFalse(before.withLff(false).lffEnabled(), "switching off works too");
    }

    @Test
    void everyCopyKeepsTheFlag() {
        for (Map.Entry<String, UnaryOperator<FfaArena>> copy : copies().entrySet()) {
            assertTrue(copy.getValue().apply(arena(true)).lffEnabled(),
                    copy.getKey() + " dropped an enabled LFF flag");
            assertFalse(copy.getValue().apply(arena(false)).lffEnabled(),
                    copy.getKey() + " switched LFF on by itself");
        }
    }
}
