package com.rumilance.practice.match.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ring-buffer behaviour and disk round-trip of {@link MatchHistoryStore}. */
class MatchHistoryStoreTest {

    private static UUID matchId(String suffix) {
        return UUID.nameUUIDFromBytes(suffix.getBytes());
    }

    private static MatchHistoryStore.Entry entry(String suffix, long endedAt, UUID viewer) {
        return new MatchHistoryStore.Entry(
                matchId(suffix),
                "RANKED", "nodebuff", endedAt, 42_000L, false,
                List.of(new MatchHistoryStore.Participant(viewer, "Viewer", "RED", 3, true),
                        new MatchHistoryStore.Participant(UUID.randomUUID(), "Enemy", "BLUE", 1, false)));
    }

    @Test
    void recentMatchesComeBackNewestFirst(@TempDir Path tmp) {
        MatchHistoryStore store = new MatchHistoryStore(null, tmp.resolve("h.bin"));
        UUID viewer = UUID.randomUUID();
        long now = System.currentTimeMillis();
        store.record(entry("a", now - 3000, viewer));
        store.record(entry("b", now - 2000, viewer));
        store.record(entry("c", now - 1000, viewer));

        List<MatchHistoryStore.Entry> recent = store.recent(viewer);
        assertEquals(3, recent.size());
        assertEquals(matchId("c"), recent.get(0).matchId(), "newest first");
        assertEquals(matchId("a"), recent.get(2).matchId(), "oldest last");
    }

    @Test
    void perPlayerCapKeepsTheNewestSixty(@TempDir Path tmp) {
        MatchHistoryStore store = new MatchHistoryStore(null, tmp.resolve("h.bin"));
        UUID viewer = UUID.randomUUID();
        long now = System.currentTimeMillis();
        for (int i = 0; i < MatchHistoryStore.MAX_PER_PLAYER + 10; i++) {
            store.record(entry("m" + i, now + i, viewer));
        }
        List<MatchHistoryStore.Entry> recent = store.recent(viewer);
        assertEquals(MatchHistoryStore.MAX_PER_PLAYER, recent.size());
        assertEquals(matchId("m69"), recent.get(0).matchId());
        assertEquals(matchId("m10"), recent.get(recent.size() - 1).matchId());
    }

    @Test
    void entriesSurviveRestartViaDisk(@TempDir Path tmp) {
        Path file = tmp.resolve("persist.bin");
        UUID viewer = UUID.randomUUID();
        MatchHistoryStore first = new MatchHistoryStore(null, file);
        first.record(entry("keep", System.currentTimeMillis(), viewer));

        MatchHistoryStore second = new MatchHistoryStore(null, file);
        List<MatchHistoryStore.Entry> recent = second.recent(viewer);
        assertEquals(1, recent.size());
        MatchHistoryStore.Entry loaded = recent.get(0);
        assertEquals("nodebuff", loaded.kit());
        assertEquals("RANKED", loaded.mode());
        assertNotNull(loaded.participant(viewer));
        assertTrue(loaded.participant(viewer).winner());
        assertEquals("RED", loaded.participant(viewer).teamColor());
        assertEquals(2, loaded.participants().size());
        assertEquals(42_000L, loaded.durationMs());
    }

    @Test
    void expiredEntriesAreDropped(@TempDir Path tmp) {
        MatchHistoryStore store = new MatchHistoryStore(null, tmp.resolve("h.bin"));
        UUID viewer = UUID.randomUUID();
        store.record(entry("old", System.currentTimeMillis() - MatchHistoryStore.TTL_MS - 1000, viewer));
        assertTrue(store.recent(viewer).isEmpty());
    }

    @Test
    void getReturnsOnlyOwnMatches(@TempDir Path tmp) {
        MatchHistoryStore store = new MatchHistoryStore(null, tmp.resolve("h.bin"));
        UUID viewer = UUID.randomUUID();
        MatchHistoryStore.Entry e = entry("mine", System.currentTimeMillis(), viewer);
        store.record(e);
        assertNotNull(store.get(viewer, e.matchId()));
        assertNull(store.get(UUID.randomUUID(), e.matchId()));
    }

    // ------------------------------------------------------------------ recentAll (web battle log)

    @Test
    void recentAllDedupesMatchesSharedBySeveralPlayers(@TempDir Path tmp) {
        MatchHistoryStore store = new MatchHistoryStore(null, tmp.resolve("h.bin"));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        MatchHistoryStore.Entry shared = new MatchHistoryStore.Entry(
                matchId("shared"), "TEAM", "gapple", System.currentTimeMillis(), 90_000L, false,
                List.of(new MatchHistoryStore.Participant(a, "A", "RED", 5, true),
                        new MatchHistoryStore.Participant(b, "B", "BLUE", 2, false)));
        store.record(shared);

        List<MatchHistoryStore.Entry> all = store.recentAll(50);
        assertEquals(1, all.size(), "one match recorded via 2 players → one battle entry");
        assertEquals(matchId("shared"), all.get(0).matchId());
    }

    @Test
    void recentAllIsNewestFirstAndHonoursTheLimit(@TempDir Path tmp) {
        MatchHistoryStore store = new MatchHistoryStore(null, tmp.resolve("h.bin"));
        UUID viewer = UUID.randomUUID();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            // separate opponents so every match lands in its own player deque
            store.record(new MatchHistoryStore.Entry(
                    matchId("m" + i), "RANKED", "nodebuff", now - (5 - i) * 1000L, 30_000L, false,
                    List.of(new MatchHistoryStore.Participant(viewer, "V", "RED", 1, true),
                            new MatchHistoryStore.Participant(matchId("opp" + i), "O" + i,
                                    "BLUE", 0, false))));
        }

        List<MatchHistoryStore.Entry> all = store.recentAll(50);
        assertEquals(5, all.size());
        assertEquals(matchId("m4"), all.get(0).matchId(), "newest first");
        assertEquals(matchId("m0"), all.get(4).matchId(), "oldest last");

        List<MatchHistoryStore.Entry> limited = store.recentAll(3);
        assertEquals(3, limited.size(), "limit caps the web battle log");
        assertEquals(matchId("m4"), limited.get(0).matchId());

        assertTrue(store.recentAll(0).isEmpty(), "non-positive limit → empty");
    }
}
