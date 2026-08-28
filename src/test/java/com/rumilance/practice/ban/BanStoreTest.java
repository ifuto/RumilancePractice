package com.rumilance.practice.ban;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanStoreTest {

    @TempDir
    Path temp;

    @Test
    void durationTokensParse() {
        assertEquals(Duration.ofDays(7), BanDuration.parse("7d").orElseThrow());
        assertEquals(Duration.ofDays(14), BanDuration.parse("2w").orElseThrow());
        assertEquals(Duration.ofDays(30), BanDuration.parse("1mo").orElseThrow());
        assertTrue(BanDuration.parse("forever").isEmpty());
        assertEquals("Permanent", BanDuration.label(null));
        assertEquals("7 days", BanDuration.labelFromToken("7d", Duration.ofDays(7)));
        assertEquals("1 week", BanDuration.labelFromToken("1w", Duration.ofDays(7)));
    }

    @Test
    void gzipRoundTripKeepsNewestActiveBan() throws Exception {
        BanStore store = new BanStore(temp.resolve("bans.rpb"));
        UUID player = UUID.randomUUID();
        long now = 1_700_000_000_000L;
        store.add(new BanRecord(UUID.randomUUID(), player, "Alice", "first", "1 day",
                now - 10_000L, now - 1L, true, "staff"));
        store.add(new BanRecord(UUID.randomUUID(), player, "Alice", "second", "Permanent",
                now, 0L, true, "staff"));
        store.save();

        BanStore loaded = new BanStore(temp.resolve("bans.rpb"));
        loaded.load();
        assertEquals(2, loaded.all().size());
        BanRecord active = loaded.activeOf(player, now);
        assertEquals("second", active.reason());
        assertTrue(active.permanent());
        assertEquals(1, loaded.activeNewestFirst(now).size());
        loaded.deactivate(player, now);
        assertEquals(null, loaded.activeOf(player, now));
    }

    @Test
    void remainingLabel() {
        assertEquals("Permanent", BanDuration.remaining(0L, 50L));
        assertEquals("Expired", BanDuration.remaining(10L, 50L));
        assertEquals("1d 0h", BanDuration.remaining(86_400_000L, 0L));
    }
}
