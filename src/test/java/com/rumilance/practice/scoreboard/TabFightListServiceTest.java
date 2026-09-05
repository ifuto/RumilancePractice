package com.rumilance.practice.scoreboard;

import com.rumilance.practice.state.TeamColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TAB list for a fight must NOT spawn any dummy/blank-pad players and must not rely on
 * player-info ordering packets (which break servers running NBT-injector packet patchers).
 * Grouping comes from the scoreboard team names the client sorts by: fighters use
 * {@code 0<sortKey><name>} with the digit sort key in canonical battle order, spectators
 * share {@code 9_spec} and therefore sort last.
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
        // The client sorts tab entries by team name (ascending), so the one-char team sort
        // keys must order the groups exactly like the canonical battle order:
        // RED, BLUE, GREEN, YELLOW, AQUA, PURPLE, GOLD.
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
        // Fight team names start with "0", the shared spectator team is "9_spec".
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
}
