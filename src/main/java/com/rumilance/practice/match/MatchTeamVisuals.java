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
 *
 * <p>Fight teams also carry a name <strong>prefix</strong> resolved by the injected
 * {@code prefixResolver}: during team fights it renders the RED/BLUE team marker image and,
 * for staff / donors, the rank badge image — custom-font glyphs from the server resource
 * pack instead of text tags.</p>
 */
public final class MatchTeamVisuals {

    /** Legacy shared fight/spec teams from older builds. */
    private static final String[] LEGACY = {
            "0_fight_red", "0_fight_blue", "1_spec",
            "rp_red", "rp_blue", "rp_spec", "glow_red", "glow_blue"
    };

    /** Prefix (team marker / rank badge images) for a fighter's nametag + TAB entry. */
    private static volatile java.util.function.BiFunction<Player, MatchSession,
            net.kyori.adventure.text.Component> prefixResolver;

    private MatchTeamVisuals() {
    }

    /** Wires the icon prefix resolver (rank badge + team marker images). */
    public static void setPrefixResolver(java.util.function.BiFunction<Player, MatchSession,
            net.kyori.adventure.text.Component> resolver) {
        prefixResolver = resolver;
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
                spec.prefix(net.kyori.adventure.text.Component.empty());
                spec.suffix(net.kyori.adventure.text.Component.empty());
                continue;
            }
            if (!participant) {
                continue;
            }
            TeamColor color = session.teamColor(onlinePlayer.getUniqueId());
            // Classic two-side fights keep their exact red/blue shades; extra team colors
            // use their own team colour.
            NamedTextColor named = color == TeamColor.RED ? NamedTextColor.RED
                    : color == TeamColor.BLUE ? NamedTextColor.BLUE
                    : color.textColor();
            Team fight = team(board, fightName(color, onlinePlayer.getUniqueId()), named, ff);
            fight.addEntry(entry);
            fight.prefix(resolvePrefix(onlinePlayer, session));
        }
    }

    private static net.kyori.adventure.text.Component resolvePrefix(Player player, MatchSession session) {
        java.util.function.BiFunction<Player, MatchSession, net.kyori.adventure.text.Component> resolver =
                prefixResolver;
        if (resolver == null) {
            return net.kyori.adventure.text.Component.empty();
        }
        try {
            net.kyori.adventure.text.Component prefix = resolver.apply(player, session);
            return prefix == null ? net.kyori.adventure.text.Component.empty() : prefix;
        } catch (RuntimeException ignored) {
            return net.kyori.adventure.text.Component.empty();
        }
    }

    public static boolean isFightTeam(String name) {
        // All fight teams are "0<colorKey><uuid>"; legacy builds also used 0_fight_*.
        return name != null && (name.startsWith("0") || name.startsWith("0_fight"));
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
            if (name.startsWith("0") || name.startsWith("1s") || name.startsWith("rp_hp_")) {
                unregister(board, name);
            }
        }
        for (String legacy : LEGACY) {
            unregister(board, legacy);
        }
    }

    private static String fightName(TeamColor color, UUID id) {
        String hex = id.toString().replace("-", "");
        return "0" + color.sortKey() + hex.substring(0, Math.min(8, hex.length()));
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
