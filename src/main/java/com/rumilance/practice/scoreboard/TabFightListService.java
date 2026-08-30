package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Orders the vanilla TAB list for an ongoing match <strong>without</strong> spawning any
 * dummy players:
 *
 * <ul>
 *   <li>Fighters are packed at the <b>top</b> of the list (ascending {@code setPlayerListOrder}),
 *       so entries are always flush with the top and never leave phantom rows behind.</li>
 *   <li>Spectators are pushed to the <b>right column</b> by giving them order indices that begin
 *       after the fighter block (TAB wraps order rows into the second column client-side), so the
 *       specs read on the right.</li>
 * </ul>
 *
 * <p>No ProtocolLib fake-player rows are sent: blank space, when needed, is produced only by
 * real player ordering. This removes the old "ghost player" pads that lingered in TAB.</p>
 */
public final class TabFightListService {

    /** Orders after this mark begin the right (spectator) column. */
    private static final int RIGHT_COLUMN_BASE = 80;

    public TabFightListService(org.bukkit.plugin.Plugin plugin) {
        // No ProtocolLib dependency: ordering only.
    }

    public void apply(MatchSession session, Collection<? extends Player> online) {
        if (session == null) {
            return;
        }
        List<Player> fighters = new ArrayList<>();
        List<Player> specs = new ArrayList<>();
        for (Player player : online) {
            boolean participant = session.participants().contains(player.getUniqueId());
            if (player.getGameMode() == GameMode.SPECTATOR) {
                specs.add(player);
            } else if (participant) {
                fighters.add(player);
            }
        }
        // Left column, packed top-down: fighters occupy order 0..n-1 (no gaps, no blanks).
        int fightIndex = 0;
        for (Player fighter : fighters) {
            fighter.setPlayerListOrder(fightIndex++);
        }
        // Right column: spectators start at a high index so the client places them after
        // the left block, i.e. in the second (right) column.
        int specIndex = 0;
        for (Player spec : specs) {
            spec.setPlayerListOrder(RIGHT_COLUMN_BASE + specIndex++);
        }
    }

    public void clear(Player player) {
        if (player != null) {
            player.setPlayerListOrder(0);
        }
    }
}
