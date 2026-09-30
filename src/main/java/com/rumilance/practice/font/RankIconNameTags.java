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

    /** Applies the rank-icon prefix for every online ranked player on {@code board}. */
    public static void apply(Scoreboard board, IconFontService icons, RankService ranks,
                             Collection<? extends Player> online) {
        apply(board, icons, ranks, online, null);
    }

    /**
     * Applies an optional plugin-owned CSV prefix after the rank icon. The badge itself is
     * always the resource-pack glyph — there is no per-viewer text fallback anymore.
     */
    public static void apply(Scoreboard board, IconFontService icons, RankService ranks,
                             Collection<? extends Player> online,
                             Function<Player, Component> customPrefix) {
        apply(board, icons, ranks, online, customPrefix, null);
    }

    /**
     * Full form: rank-icon prefix + optional status-marker suffix (TAB name 右側の状態マーカー:
     * ロビー視点で 試合中 = ⚔️ / 観戦中 = 👁️)。Markers ride in the SAME team as the badge —
     * an entry may only render one team's prefix+suffix, so a second marker team would fight
     * the badge team over the entry, and {@link #clear(Scoreboard)} (match entry) removes both.
     * Glyph-less (no resource pack) viewers still get the markers: they are plain unicode.
     */
    public static void apply(Scoreboard board, IconFontService icons, RankService ranks,
                             Collection<? extends Player> online,
                             Function<Player, Component> customPrefix,
                             Function<Player, Component> markerSuffix) {
        if (board == null || ranks == null) {
            return;
        }
        boolean glyphs = icons != null && icons.enabled();
        for (Player other : online) {
            Component icon = Component.empty();
            if (glyphs) {
                PlayerRank effective = effectiveRank(ranks, other);
                icon = icons.rankIcon(effective);
                if (customPrefix != null) {
                    Component suffix = customPrefix.apply(other);
                    if (suffix != null && !suffix.equals(Component.empty())) {
                        icon = icon.append(suffix);
                    }
                }
            }
            Component marker = Component.empty();
            if (markerSuffix != null) {
                Component resolved = markerSuffix.apply(other);
                if (resolved != null) {
                    marker = resolved;
                }
            }
            String entry = other.getName();
            String name = teamName(other.getUniqueId());
            if (icon.equals(Component.empty()) && marker.equals(Component.empty())) {
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
            team.suffix(marker);
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
     * server and must not make every player display the OWNER badge: the stored rank (set with
     * {@code /rank}) is the only source.
     */
    public static PlayerRank effectiveRank(RankService ranks, Player player) {
        return ranks.get(player);
    }
}
