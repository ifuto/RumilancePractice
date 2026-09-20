package com.rumilance.practice.font;

import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/**
 * Lobby / FFA nametag + TAB prefixes: the resource-pack rank badge (admin / VIP+ / VIP) is
 * rendered in front of the player name via the custom icon font. Viewers who declined or
 * failed the resource pack see the plain-text badges (N / N+ / OWNER) instead of the font
 * glyphs, which would otherwise render as missing-glyph boxes on their client. Match
 * contexts use {@code MatchTeamVisuals} fight teams instead (one entry may only belong to
 * one team, so the two layers clear each other's teams when switching contexts).
 */
public final class RankIconNameTags {

    private RankIconNameTags() {
    }

    /**
     * Applies the rank-icon prefix for every online ranked player on {@code board}.
     * {@code viewerHasPack} decides whether the owner of this scoreboard sees the
     * resource-pack glyphs or the text fallback badges.
     */
    public static void apply(Scoreboard board, IconFontService icons, RankService ranks,
                             Collection<? extends Player> online, boolean viewerHasPack) {
        apply(board, icons, ranks, online, viewerHasPack, null);
    }

    /** Applies an optional plugin-owned CSV prefix after the rank icon. */
    public static void apply(Scoreboard board, IconFontService icons, RankService ranks,
                             Collection<? extends Player> online, boolean viewerHasPack,
                             Function<Player, Component> customPrefix) {
        if (board == null || icons == null || ranks == null || !icons.enabled()) {
            return;
        }
        for (Player other : online) {
            PlayerRank effective = effectiveRank(ranks, other);
            Component icon = icons.rankIcon(effective, viewerHasPack);
            if (customPrefix != null) {
                Component suffix = customPrefix.apply(other);
                if (suffix != null && !suffix.equals(Component.empty())) {
                    icon = icon.append(suffix);
                }
            }
            String entry = other.getName();
            String name = teamName(other.getUniqueId());
            if (icon.equals(Component.empty())) {
                remove(board, entry, name);
                continue;
            }
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.prefix(icon);
            team.suffix(Component.empty());
        }
    }

    /** Removes every rank-icon team from {@code board} (entering a match / icons disabled). */
    public static void clear(Scoreboard board) {
        if (board == null) {
            return;
        }
        for (Team team : Set.copyOf(board.getTeams())) {
            if (team.getName().startsWith("2r")) {
                unregister(board, team);
            }
        }
    }

    private static void remove(Scoreboard board, String entry, String name) {
        Team team = board.getTeam(name);
        if (team == null) {
            return;
        }
        unregister(board, team);
    }

    private static void unregister(Scoreboard board, Team team) {
        for (String entry : Set.copyOf(team.getEntries())) {
            team.removeEntry(entry);
        }
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
            // already gone
        }
    }

    private static String teamName(java.util.UUID id) {
        String hex = id.toString().replace("-", "");
        return "2r" + hex.substring(0, Math.min(12, hex.length()));
    }

    /**
     * Rank badges are UUID-backed social state, not a side effect of the permission graph.
     * In particular, {@code rumilance.admin} is normally inherited by every OP on a test
     * server and must not make every player display the OWNER badge.
     */
    public static PlayerRank effectiveRank(RankService ranks, Player player) {
        return ranks.get(player);
    }
}
