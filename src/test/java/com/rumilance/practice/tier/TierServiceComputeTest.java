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
    void tierlistOrderIsHighLowPerLevel() {
        // HT1 > LT1 > HT2 > LT2 > ... > HT5 > LT5, band ceilings in that order.
        assertEquals(TierService.Tier.HT1, TierService.bandOf(0.0010d));
        assertEquals(TierService.Tier.HT1, TierService.bandOf(0.0009d));
        assertEquals(TierService.Tier.LT1, TierService.bandOf(0.0020d));
        assertEquals(TierService.Tier.HT2, TierService.bandOf(0.0050d));
        assertEquals(TierService.Tier.LT2, TierService.bandOf(0.0200d));
        assertEquals(TierService.Tier.HT3, TierService.bandOf(0.0500d));
        assertEquals(TierService.Tier.LT3, TierService.bandOf(0.1500d));
        assertEquals(TierService.Tier.HT4, TierService.bandOf(0.2500d));
        assertEquals(TierService.Tier.LT4, TierService.bandOf(0.4000d));
        assertEquals(TierService.Tier.HT5, TierService.bandOf(0.6000d));
        assertEquals(TierService.Tier.LT5, TierService.bandOf(0.9000d));
    }

    @Test
    void placementFloorIsPerKit() {
        UUID a = UUID.randomUUID();
        List<RankedKitStats> rows = new ArrayList<>();
        rows.add(row(a, "sword", 3000, 10, 9));   // 19 matches — one short: no placement
        rows.add(row(a, "crystal", 1500, 11, 9)); // 20 matches: placed on crystal only
        var result = TierService.compute(rows);
        var p = result.standings().get(a);
        assertFalse(p.containsKey("sword"));
        assertTrue(p.containsKey("crystal"));
        assertEquals(0, result.kitPopulations().get("sword"));
        assertEquals(1, result.kitPopulations().get("crystal"));
    }

    @Test
    void kitLaddersAreIndependent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<RankedKitStats> rows = new ArrayList<>();
        // sword: a outranks b — crystal: b outranks a (independent placement)
        rows.add(row(a, "sword", 2400, 15, 5));
        rows.add(row(b, "sword", 1500, 12, 8));
        rows.add(row(a, "crystal", 1200, 10, 10));
        rows.add(row(b, "crystal", 2600, 18, 2));
        var result = TierService.compute(rows);
        assertEquals(1, result.standings().get(a).get("sword").rank());
        assertEquals(2, result.standings().get(b).get("sword").rank());
        assertEquals(2, result.standings().get(a).get("crystal").rank());
        assertEquals(1, result.standings().get(b).get("crystal").rank());
        assertEquals(2, result.kitPopulations().get("sword"));
        assertEquals(2, result.kitPopulations().get("crystal"));
    }

    @Test
    void ht1OnlyExistsInLargeKitPopulations() {
        List<RankedKitStats> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(row(UUID.randomUUID(), "sword", 4000 - i, 11, 9));
        }
        var result = TierService.compute(rows);
        assertEquals(1000, result.kitPopulations().get("sword"));
        // rank 1 of 1000 = 0.1% → HT1; rank 2 = 0.2% → LT1
        for (var entry : result.standings().entrySet()) {
            var s = entry.getValue().get("sword");
            if (s.elo() == 4000) {
                assertEquals(TierService.Tier.HT1, s.tier());
            }
            if (s.elo() == 3999) {
                assertEquals(TierService.Tier.LT1, s.tier());
            }
        }
        assertFalse(result.standings().isEmpty());
    }
}
