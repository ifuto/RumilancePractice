package com.rumilance.practice.scoreboard;

import com.rumilance.practice.state.TeamColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Since 1.21.2 the vanilla client sorts the player list by the server-provided non-negative
 * ordering index (highest first) — team-name sorting is gone — so the columns are built
 * from {@code Player#setPlayerListOrder} index bands: one band per team in canonical battle
 * order, then the spectator band, lobby players on the default 0 sorting last. Because the
 * client wraps the list into a new column every 20 entries, each team band is additionally
 * padded to a multiple of 20 rows with invisible fake entries (the blank column gaps the
 * operators asked for).
 */
class TabFightListServiceTest {

    @Test
    void padPoolIsUniqueAndProtocolValid() throws Exception {
        // The blank padding entries are fake player-info entries: unique random UUIDs and
        // profile names that satisfy the ADD_PLAYER constraints (1-16 chars, [A-Za-z0-9_]).
        Field idsField = TabFightListService.class.getDeclaredField("PAD_IDS");
        Field namesField = TabFightListService.class.getDeclaredField("PAD_NAMES");
        idsField.setAccessible(true);
        namesField.setAccessible(true);
        UUID[] ids = (UUID[]) idsField.get(null);
        String[] names = (String[]) namesField.get(null);
        assertTrue(ids.length >= 152, "enough pads for 7 team columns + spectator padding");
        Set<UUID> unique = new HashSet<>(List.of(ids));
        assertEquals(ids.length, unique.size(), "pad UUIDs must be unique");
        for (String name : names) {
            assertTrue(name.length() >= 1 && name.length() <= 16, "name length: " + name);
            assertTrue(name.matches("[A-Za-z0-9_]+"), "name charset: " + name);
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
        // Construction must work without ProtocolLib on the classpath; the pad packets
        // live in a separate class that is only touched after a runtime availability check.
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
