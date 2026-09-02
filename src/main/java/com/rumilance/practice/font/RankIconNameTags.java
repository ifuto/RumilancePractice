package com.rumilance.practice.font;

import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.Set;

/**
 * Lobby / FFA nametag + TAB prefixes: the resource-pack rank badge (admin / VIP+ / VIP) is
 * rendered in front of the player name via the custom icon font. Match contexts use
 * {@code MatchTeamVisuals} fight teams instead (one entry may only belong to one team, so the
 * two layers clear each other's teams when switching contexts).
 */
public final class RankIconNameTags {

    private RankIconNameTags() {
    }

    /** Applies the rank-icon prefix for every online ranked player on {@code board}. */
    public static void apply(Scoreboard board, IconFontService icons, RankService ranks,
                             Collection<? extends Player> online) {
        if (board == null || icons == null || ranks == null || !icons.enabled()) {
            return;
        }
        for (Player other : online) {
            PlayerRank effective = effectiveRank(ranks, other);
            Component icon = icons.rankIcon(effective);
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

    private static PlayerRank effectiveRank(RankService ranks, Player player) {
        if (ranks.isAdmin(player)) {
            return PlayerRank.ADMIN;
        }
        if (ranks.isVipPlusOrAbove(player)) {
            return PlayerRank.VIP_PLUS;
        }
        if (ranks.isVipOrAbove(player)) {
            return PlayerRank.VIP;
        }
        return PlayerRank.NORM;
    }
}
