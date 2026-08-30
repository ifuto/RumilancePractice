package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.UUID;

/**
 * Applies red/blue nametag + TAB list colours for everyone watching a match scoreboard.
 * One team per player so HP suffixes do not collide. Names are prefixed so TAB sorts
 * fighters left ({@code 0*}) and spectators right ({@code 1*}).
 */
public final class MatchTeamVisuals {

    /** Legacy shared fight/spec teams from older builds. */
    private static final String[] LEGACY = {
            "0_fight_red", "0_fight_blue", "1_spec",
            "rp_red", "rp_blue", "rp_spec", "glow_red", "glow_blue"
    };

    private MatchTeamVisuals() {
    }

    public static void apply(Scoreboard board, MatchSession session, Collection<? extends Player> online) {
        if (board == null || session == null) {
            return;
        }
        boolean ff = session.friendlyFire();
        for (Player onlinePlayer : online) {
            String entry = onlinePlayer.getName();
            removeFromManaged(board, entry);
            boolean spectator = onlinePlayer.getGameMode() == GameMode.SPECTATOR;
            boolean participant = session.participants().contains(onlinePlayer.getUniqueId());
            if (spectator) {
                Team spec = team(board, specName(onlinePlayer.getUniqueId()), NamedTextColor.GRAY, false);
                spec.addEntry(entry);
                spec.suffix(net.kyori.adventure.text.Component.empty());
                continue;
            }
            if (!participant) {
                continue;
            }
            TeamColor color = session.teamColor(onlinePlayer.getUniqueId());
            NamedTextColor named = color == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.BLUE;
            Team fight = team(board, fightName(color, onlinePlayer.getUniqueId()), named, ff);
            fight.addEntry(entry);
        }
    }

    public static boolean isFightTeam(String name) {
        return name != null && (name.startsWith("0r") || name.startsWith("0b") || name.startsWith("0_fight"));
    }

    public static Team fightTeamOf(Scoreboard board, Player player) {
        if (board == null || player == null) {
            return null;
        }
        Team team = board.getEntryTeam(player.getName());
        if (team != null && isFightTeam(team.getName())) {
            return team;
        }
        return null;
    }

    /** Removes practice fight/spec teams from {@code board} (lobby / FFA / leave match). */
    public static void clear(Scoreboard board) {
        if (board == null) {
            return;
        }
        for (Team team : java.util.Set.copyOf(board.getTeams())) {
            String name = team.getName();
            if (name.startsWith("0r") || name.startsWith("0b") || name.startsWith("1s")
                    || name.startsWith("rp_hp_")) {
                unregister(board, name);
            }
        }
        for (String legacy : LEGACY) {
            unregister(board, legacy);
        }
    }

    private static String fightName(TeamColor color, UUID id) {
        String hex = id.toString().replace("-", "");
        return (color == TeamColor.RED ? "0r" : "0b") + hex.substring(0, Math.min(8, hex.length()));
    }

    private static String specName(UUID id) {
        String hex = id.toString().replace("-", "");
        return "1s" + hex.substring(0, Math.min(8, hex.length()));
    }

    private static void removeFromManaged(Scoreboard board, String entry) {
        Team current = board.getEntryTeam(entry);
        if (current != null) {
            String name = current.getName();
            if (isFightTeam(name) || name.startsWith("1s") || name.startsWith("rp_hp_")
                    || name.startsWith("0_fight") || name.equals("1_spec")) {
                current.removeEntry(entry);
            }
        }
    }

    private static void unregister(Scoreboard board, String name) {
        Team team = board.getTeam(name);
        if (team != null) {
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

    private static Team team(Scoreboard board, String name, NamedTextColor color, boolean fightTeam) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.color(color);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setAllowFriendlyFire(fightTeam);
        return team;
    }
}
