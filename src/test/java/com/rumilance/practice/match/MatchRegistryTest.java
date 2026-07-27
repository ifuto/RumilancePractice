package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.MatchMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRegistryTest {

    @Test
    void refusesDoublePlayerRegistration() {
        MatchRegistry registry = new MatchRegistry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        MatchSession first = new MatchSession(UUID.randomUUID(), MatchMode.RANKED, "nodebuff",
                List.of(a, b), UUID.randomUUID(), ArenaTerrain.FLAT, 1);
        MatchSession second = new MatchSession(UUID.randomUUID(), MatchMode.UNRANKED, "nodebuff",
                List.of(a, c), UUID.randomUUID(), ArenaTerrain.FLAT, 1);

        assertTrue(registry.register(first));
        assertFalse(registry.register(second));
    }

    @Test
    void refusesDoubleArenaReservation() {
        MatchRegistry registry = new MatchRegistry();
        UUID arena = UUID.randomUUID();
        UUID matchA = UUID.randomUUID();
        UUID matchB = UUID.randomUUID();

        assertTrue(registry.tryReserveArena(arena, matchA));
        assertFalse(registry.tryReserveArena(arena, matchB));
        assertTrue(registry.tryReserveArena(arena, matchA));
    }
}
