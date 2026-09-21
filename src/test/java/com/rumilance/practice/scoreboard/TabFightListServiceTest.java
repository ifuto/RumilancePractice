package com.rumilance.practice.scoreboard;

import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fight TAB grid follows the TAB plugin's layout model: the client sorts the player list by
 * the server-provided order (highest first) and wraps it into a new column every 20 entries, so
 * every column is padded to exactly 20 rows — header, blank spacer, roster, blank fillers — and
 * each row is placed with an absolute order {@code Integer.MAX_VALUE - band - slot}.
 *
 * <p>The grid must sit above the lobby ordering of {@code tab-layout.csv} (which reaches
 * 5,900,000). Both used to share the 1M band, so a rank-holding lobby player (owner/VIP) was
 * listed above the whole fight grid and pushed every column break one row off.</p>
 */
class TabFightListServiceTest {

    /** The highest list order the lobby CSV can hand out: 900_000 + priority(500) * 10_000. */
    private static final int MAX_LOBBY_ORDER = 900_000 + 500 * 10_000;

    @Test
    void fightRowsSortAboveEveryLobbyOrder() {
        assertTrue(TabFightListService.orderOf(0, 1) > MAX_LOBBY_ORDER);
        assertTrue(TabFightListService.orderOf(0, TabFightListService.SLOTS_PER_MATCH)
                > MAX_LOBBY_ORDER);
        assertEquals(Integer.MAX_VALUE - 1, TabFightListService.orderOf(0, 1));
    }

    @Test
    void gridKeepsReadingOrderAndBandsDoNotOverlap() {
        assertTrue(TabFightListService.orderOf(0, 1) > TabFightListService.orderOf(0, 2));
        assertTrue(TabFightListService.orderOf(0, 20) > TabFightListService.orderOf(0, 21),
                "the row after a full column is the top of the next one");
        assertTrue(TabFightListService.orderOf(0, TabFightListService.SLOTS_PER_MATCH)
                > TabFightListService.orderOf(1, 1), "a second match starts below the first band");
    }

    @Test
    void fillerIdsAreDeterministicAndUnique() {
        assertEquals(TabFightListService.padId(0, 1), TabFightListService.padId(0, 1));
        Set<UUID> ids = new HashSet<>();
        for (int slot = 1; slot <= TabFightListService.SLOTS_PER_MATCH; slot++) {
            ids.add(TabFightListService.padId(0, slot));
            ids.add(TabFightListService.padId(1, slot));
        }
        assertEquals(TabFightListService.SLOTS_PER_MATCH * 2, ids.size());
    }

    @Test
    void duelGridMergesBothFightersIntoOneColumn() {
        List<TabFightListService.Member> members = List.of(
                TabFightListService.Member.ofFighter(UUID.randomUUID(), "Alpha", TeamColor.RED),
                TabFightListService.Member.ofFighter(UUID.randomUUID(), "Bravo", TeamColor.BLUE),
                TabFightListService.Member.ofSpectator(UUID.randomUUID(), "Watcher"));
        List<TabFightListService.ColumnPlan> columns = TabFightListService.planGrid(false, members);
        assertEquals(2, columns.size(), "In-Game Players + Spectators");
        assertEquals("In-Game Players", plain(columns.get(0).header()));
        assertEquals(List.of("Alpha", "Bravo"), names(columns.get(0)));
        assertEquals("Spectators", plain(columns.get(1).header()));
        assertEquals(List.of("Watcher"), names(columns.get(1)));
        // The duel rows are the two mcid names on their own, coloured by team.
        assertEquals("Alpha", plain(TabFightListService.rowDisplay(
                members.get(0), TabFightLayout.Scheme.DUEL)));
    }

    @Test
    void partyGridSplitsTeamsAndKeepsFallenPlayers() {
        List<TabFightListService.Member> members = List.of(
                TabFightListService.Member.ofFighter(UUID.randomUUID(), "PlayerA", TeamColor.RED),
                TabFightListService.Member.ofFallen(UUID.randomUUID(), "PlayerH", TeamColor.RED),
                TabFightListService.Member.ofFighter(UUID.randomUUID(), "PlayerD", TeamColor.BLUE));
        List<TabFightListService.ColumnPlan> columns = TabFightListService.planGrid(true, members);
        assertEquals(2, columns.size());
        assertEquals("● Red Team", plain(columns.get(0).header()));
        assertEquals("● Blue Team", plain(columns.get(1).header()));
        assertEquals(List.of("PlayerA", "PlayerH"), names(columns.get(0)));
        assertEquals("PlayerA ●", plain(TabFightListService.rowDisplay(
                columns.get(0).rows().get(0), TabFightLayout.Scheme.TEAMS)));
        assertEquals("PlayerH ● - Death", plain(TabFightListService.rowDisplay(
                columns.get(0).rows().get(1), TabFightLayout.Scheme.TEAMS)));
    }

    @Test
    void serviceHasNoPacketPluginDependency() {
        // Construction and the no-session path must not require ProtocolLib or any packet
        // plugin: the filler rows are built from the server's own classes.
        TabFightListService service = new TabFightListService(null);
        assertNotNull(service);
        service.apply(null, List.of());
    }

    @Test
    void matchSlotsAreStableAndFreedByPrune() {
        TabFightListService service = new TabFightListService(null);
        UUID matchA = UUID.randomUUID();
        UUID matchB = UUID.randomUUID();
        assertEquals(0, service.slotFor(matchA), "first match gets slot 0");
        assertEquals(1, service.slotFor(matchB), "second match gets slot 1");
        assertEquals(0, service.slotFor(matchA), "slot assignment is stable");
        service.prune(Set.of(matchB));
        assertEquals(0, service.slotFor(matchA), "pruned match frees its band");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> names(TabFightListService.ColumnPlan column) {
        return column.rows().stream().map(TabFightListService.Member::name).toList();
    }
}
