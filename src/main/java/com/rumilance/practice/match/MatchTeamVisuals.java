package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Applies red/blue nametag + TAB list colours for everyone watching a match scoreboard.
 * Team names are prefixed so TAB sorts fighters left ({@code 0_*}) and spectators right ({@code 1_*}).
 * {@link Team#color(NamedTextColor)} also drives ProtocolLib / vanilla glow outline colour.
 */
public final class MatchTeamVisuals {

    private static final String RED = "0_fight_red";
    private static final String BLUE = "0_fight_blue";
    private static final String SPEC = "1_spec";
    /** Legacy names from older builds — cleared on {@link #clear(Scoreboard)}. */
    private static final String[] LEGACY = {"rp_red", "rp_blue", "rp_spec", "glow_red", "glow_blue"};

    private MatchTeamVisuals() {
    }

    public static void apply(Scoreboard board, MatchSession session, java.util.Collection<? extends Player> online) {
        if (board == null || session == null) {
            return;
        }
        boolean ff = session.friendlyFire();
        Team red = team(board, RED, NamedTextColor.RED, ff);
        Team blue = team(board, BLUE, NamedTextColor.BLUE, ff);
        Team spec = team(board, SPEC, NamedTextColor.GRAY, false);
        clearEntries(red);
        clearEntries(blue);
        clearEntries(spec);

        for (Player onlinePlayer : online) {
            String entry = onlinePlayer.getName();
            if (!session.participants().contains(onlinePlayer.getUniqueId())) {
                if (onlinePlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    spec.addEntry(entry);
                }
                continue;
            }
            TeamColor color = session.teamColor(onlinePlayer.getUniqueId());
            if (color == TeamColor.RED) {
                red.addEntry(entry);
            } else if (color == TeamColor.BLUE) {
                blue.addEntry(entry);
            }
        }
    }

    /** Removes practice fight/spec teams from {@code board} (lobby / FFA / leave match). */
    public static void clear(Scoreboard board) {
        if (board == null) {
            return;
        }
        unregister(board, RED);
        unregister(board, BLUE);
        unregister(board, SPEC);
        for (String legacy : LEGACY) {
            unregister(board, legacy);
        }
    }

    private static void unregister(Scoreboard board, String name) {
        Team team = board.getTeam(name);
        if (team != null) {
            clearEntries(team);
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
                // already gone
            }
        }
    }

    private static Team team(Scoreboard board, String name, NamedTextColor color, boolean fightTeam) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        // Paper: Team.color sets glow outline colour (ChatColor setColor is deprecated).
        team.color(color);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        // Fight teams: allow FF at the scoreboard layer; friendly-fire is blocked in match listeners.
        team.setAllowFriendlyFire(fightTeam);
        return team;
    }

    private static void clearEntries(Team team) {
        for (String entry : java.util.Set.copyOf(team.getEntries())) {
            team.removeEntry(entry);
        }
    }
}
