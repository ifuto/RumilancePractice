package com.rumilance.practice.scoreboard;

import com.rumilance.practice.state.TeamColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TAB list for a fight must NOT spawn any dummy/blank-pad players. Since 1.21.2 the
 * vanilla client sorts the player list by the server-provided non-negative ordering index
 * (highest first) — team-name sorting is gone — so the columns are built from
 * {@code Player#setPlayerListOrder} index bands: one band per team in canonical battle
 * order, then the spectator band, lobby players on the default 0 sorting last.
 */
class TabFightListServiceTest {

    @Test
    void noDummyPadStateExists() {
        // The old ProtocolLib pad implementation kept these members; they must be gone.
        for (Field field : TabFightListService.class.getDeclaredFields()) {
            String name = field.getName().toUpperCase();
            assertFalse(name.contains("BLANK_PAD"), "dummy pad profile removed: " + field.getName());
            assertFalse(name.contains("PAD"), "pad tracking removed: " + field.getName());
        }
    }

    @Test
    void sortKeysFollowCanonicalBattleOrder() {
        // Team columns are assigned in TeamColor declaration order, so the one-char sort
        // keys must keep matching canonical battle order: RED, BLUE, GREEN, YELLOW, AQUA,
        // PURPLE, GOLD.
        List<TeamColor> canonical = List.of(TeamColor.values());
        for (int i = 0; i + 1 < canonical.size(); i++) {
            String before = canonical.get(i).sortKey();
            String after = canonical.get(i + 1).sortKey();
            assertTrue(before.compareTo(after) < 0,
                    canonical.get(i) + " (" + before + ") must sort before "
                            + canonical.get(i + 1) + " (" + after + ")");
        }
    }

    @Test
    void fightersSortBeforeSpectators() {
        // Fight team names start with "0", the shared spectator team is "9_spec" (nametag
        // colours still come through scoreboard teams).
        String spectatorTeam = "9_spec";
        for (TeamColor color : TeamColor.values()) {
            String fightTeam = "0" + color.sortKey() + "player";
            assertTrue(fightTeam.compareTo(spectatorTeam) < 0,
                    color + " fighters must be listed before spectators");
        }
    }

    @Test
    void serviceHasNoProtocolLibDependency() {
        // Construction must work without ProtocolLib on the classpath (ordering only).
        TabFightListService service = new TabFightListService(null);
        assertTrue(service != null);
    }

    @Test
    void applyWithNullSessionIsNoOp() {
        // Must not throw with a null session (defensive guard used by the scoreboard loop).
        new TabFightListService(null).apply(null, java.util.List.of());
    }

    @Test
    void matchSlotsAreStableAndFreedByPrune() throws Exception {
        TabFightListService service = new TabFightListService(null);
        Method slotFor = TabFightListService.class.getDeclaredMethod("slotFor", UUID.class);
        slotFor.setAccessible(true);
        UUID matchA = UUID.randomUUID();
        UUID matchB = UUID.randomUUID();
        assertEquals(0, slotFor.invoke(service, matchA), "first match gets slot 0");
        assertEquals(1, slotFor.invoke(service, matchB), "second match gets slot 1");
        assertEquals(0, slotFor.invoke(service, matchA), "slot assignment is stable");
        service.prune(Set.of(matchB));
        assertEquals(0, slotFor.invoke(service, matchA), "pruned match frees its band");
    }
}
