package com.rumilance.practice.tier;

import com.rumilance.practice.model.RankedKitStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierServiceComputeTest {

    private static RankedKitStats row(UUID player, String kit, int elo, int wins, int losses) {
        return new RankedKitStats(UUID.randomUUID(), player, kit, elo, wins, losses, 0, elo);
    }

    @Test
    void rarityBandsMatchSpec() {
        // HT1 is "1 in 1000": only the 0.1% ceiling and below.
        assertEquals(TierService.Tier.HT1, TierService.bandOf(0.0010d));
        assertEquals(TierService.Tier.HT1, TierService.bandOf(0.0009d));
        assertEquals(TierService.Tier.HT2, TierService.bandOf(0.0020d));
        assertEquals(TierService.Tier.HT3, TierService.bandOf(0.0050d));
        assertEquals(TierService.Tier.HT4, TierService.bandOf(0.0200d));
        // HT5 = top-10% shell: the "beginner who already plays some PvP" rung.
        assertEquals(TierService.Tier.HT5, TierService.bandOf(0.0600d));
        assertEquals(TierService.Tier.HT5, TierService.bandOf(0.1000d));
        assertEquals(TierService.Tier.LT1, TierService.bandOf(0.1500d));
        assertEquals(TierService.Tier.LT3, TierService.bandOf(0.4500d));
        assertEquals(TierService.Tier.LT4, TierService.bandOf(0.6000d));
        assertEquals(TierService.Tier.LT5, TierService.bandOf(0.9000d));
    }

    @Test
    void belowMatchFloorIsNotPlaced() {
        UUID a = UUID.randomUUID();
        List<RankedKitStats> rows = new ArrayList<>();
        rows.add(row(a, "sword", 3000, 10, 9)); // 19 matches — one short of the floor
        var result = TierService.compute(rows);
        assertEquals(0, result.eligibleCount());
        assertTrue(result.standings().isEmpty());
    }

    @Test
    void bestKitOnlyAndRankingOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<RankedKitStats> rows = new ArrayList<>();
        // a: one monster kit + one weak kit — score must be the strong kit only
        rows.add(row(a, "sword", 2400, 15, 5));
        rows.add(row(a, "crystal", 900, 5, 5));
        // b: mid elo on one kit
        rows.add(row(b, "sword", 1500, 12, 8));
        var result = TierService.compute(rows);
        assertEquals(2, result.eligibleCount());
        var sa = result.standings().get(a);
        var sb = result.standings().get(b);
        assertEquals(1, sa.rank());
        assertEquals(2, sb.rank());
        assertEquals(2400, sa.bestElo());
        assertEquals("sword", sa.topKit());
        assertEquals(30, sa.matches());
        // 2-player population: rank1 = 50% share (LT3), rank2 = 100% (LT5)
        assertEquals(TierService.Tier.LT3, sa.tier());
        assertEquals(TierService.Tier.LT5, sb.tier());
    }

    @Test
    void ht1OnlyExistsInLargePopulations() {
        List<RankedKitStats> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(row(UUID.randomUUID(), "sword", 4000 - i, 11, 9));
        }
        var result = TierService.compute(rows);
        assertEquals(1000, result.eligibleCount());
        // rank 1 of 1000 = 0.1% → HT1; rank 2 = 0.2% → HT2
        for (var entry : result.standings().entrySet()) {
            if (entry.getValue().bestElo() == 4000) {
                assertEquals(TierService.Tier.HT1, entry.getValue().tier());
            }
            if (entry.getValue().bestElo() == 3999) {
                assertEquals(TierService.Tier.HT2, entry.getValue().tier());
            }
        }
        assertFalse(result.standings().isEmpty());
    }
}
