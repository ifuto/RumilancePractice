package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Syncs RED/BLUE scoreboard team colours onto a viewer's current scoreboard so ProtocolLib
 * LOS glow outlines are coloured (not white). Prefers the same fight teams as
 * {@link MatchTeamVisuals} so nametag colours stay intact (one entry = one team).
 */
public final class TeamGlowColors {

    static final String GLOW_RED = "glow_red";
    static final String GLOW_BLUE = "glow_blue";

    private TeamGlowColors() {
    }

    /**
     * Ensures {@code target} is on a coloured RED/BLUE team on {@code viewer}'s scoreboard.
     * Uses {@link MatchTeamVisuals} fight teams when present; otherwise dedicated glow teams.
     */
    public static void apply(Player viewer, Player target, MatchSession session) {
        if (viewer == null || target == null || session == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(board);
        }
        // Prefer full MatchTeamVisuals sync so nametag + glow share one coloured team.
        if (board.getTeam("0_fight_red") != null || board.getTeam("0_fight_blue") != null
                || MatchTeamVisuals.fightTeamOf(board, target) != null
                || board.getObjective(org.bukkit.scoreboard.DisplaySlot.SIDEBAR) != null) {
            List<Player> online = new ArrayList<>();
            for (UUID id : session.participants()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    online.add(p);
                }
            }
            MatchTeamVisuals.apply(board, viewer, session, online);
            return;
        }
        // No sidebar / fight teams yet — attach dedicated glow colour teams only.
        var color = session.teamColor(target.getUniqueId());
        if (color == null) {
            return;
        }
        Team red = ensureGlowTeam(board, GLOW_RED, NamedTextColor.RED);
        Team blue = ensureGlowTeam(board, GLOW_BLUE, NamedTextColor.BLUE);
        String entry = target.getName();
        removeFromGlowTeams(board, entry);
        if (color == com.rumilance.practice.state.TeamColor.RED) {
            red.addEntry(entry);
        } else if (color == com.rumilance.practice.state.TeamColor.BLUE) {
            blue.addEntry(entry);
        }
    }

    /** Removes glow-only team membership for one target (does not touch fight teams). */
    public static void clearTarget(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        if (board == null) {
            return;
        }
        removeFromGlowTeams(board, target.getName());
    }

    /** Clears dedicated glow_* teams only — fight/nametag teams stay for ScoreboardService. */
    public static void clear(Player viewer) {
        if (viewer == null) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        if (board == null) {
            return;
        }
        unregister(board, GLOW_RED);
        unregister(board, GLOW_BLUE);
    }

    private static Team ensureGlowTeam(Scoreboard board, String name, NamedTextColor color) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.color(color);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setAllowFriendlyFire(true);
        return team;
    }

    private static void removeFromGlowTeams(Scoreboard board, String entry) {
        for (String name : new String[]{GLOW_RED, GLOW_BLUE}) {
            Team team = board.getTeam(name);
            if (team != null && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private static void unregister(Scoreboard board, String name) {
        Team team = board.getTeam(name);
        if (team == null) {
            return;
        }
        for (String entry : java.util.Set.copyOf(team.getEntries())) {
            team.removeEntry(entry);
        }
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
            // already gone
        }
    }
}
