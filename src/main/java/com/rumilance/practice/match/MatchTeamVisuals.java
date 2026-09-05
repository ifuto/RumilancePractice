package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;

/**
 * Applies red/blue nametag + TAB list colours for everyone watching a match scoreboard.
 * One team per fighter so HP suffixes and rank-badge prefixes do not collide.
 *
 * <p><b>TAB grouping without any player-info packets:</b> the client sorts tab entries with
 * equal list order by team name (case-sensitive, ascending — vanilla behaviour, verified
 * against the protocol wiki), so the team names encode the layout: {@code 0<sortKey><name>}
 * for fighters, where the digit sort key follows canonical battle order (RED, BLUE, GREEN,
 * YELLOW, AQUA, PURPLE, GOLD) and the lowercased player name yields alphabetical rosters
 * inside each column. Spectators share one {@code 9_spec} team and therefore always sort
 * last. This works on every client and is unaffected by packet-patching (NBT-injector)
 * plugins that choke on the 1.21.2 list-order action.</p>
 *
 * <p>Fight teams also carry a name <strong>prefix</strong> resolved by the injected
 * {@code prefixResolver}: during team fights it renders the RED/BLUE team marker and, for
 * staff / donors, the rank badge — custom-font glyphs from the server resource pack for
 * viewers who applied the pack, or the plain-text badges (N / N+ / OWNER) for viewers who
 * declined or failed it. The resolver therefore receives the viewing player as well.</p>
 */
public final class MatchTeamVisuals {

    /** Legacy shared fight/spec teams from older builds. */
    private static final String[] LEGACY = {
            "0_fight_red", "0_fight_blue", "1_spec",
            "rp_red", "rp_blue", "rp_spec", "glow_red", "glow_blue"
    };

    /** Shared team holding every match spectator; the {@code 9} prefix sorts them last. */
    private static final String SPEC_TEAM = "9_spec";

    /** Prefix (team marker / rank badge images) for a fighter's nametag + TAB entry. */
    private static volatile PrefixResolver prefixResolver;

    private MatchTeamVisuals() {
    }

    /**
     * Resolves the nametag / TAB prefix of {@code target} as seen by {@code viewer}.
     * Viewer-dependent because the rank badge is a resource-pack glyph: viewers without the
     * pack get the plain-text badge (N / N+ / OWNER) instead.
     */
    @FunctionalInterface
    public interface PrefixResolver {
        net.kyori.adventure.text.Component resolve(Player viewer, Player target, MatchSession session);
    }

    /** Wires the icon prefix resolver (rank badge + team marker images). */
    public static void setPrefixResolver(PrefixResolver resolver) {
        prefixResolver = resolver;
    }

    public static void apply(Scoreboard board, Player viewer, MatchSession session,
                             Collection<? extends Player> online) {
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
                Team spec = team(board, SPEC_TEAM, NamedTextColor.GRAY, false);
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
            Team fight = team(board, fightName(color, onlinePlayer.getName()), named, ff);
            fight.addEntry(entry);
            fight.prefix(resolvePrefix(viewer, onlinePlayer, session));
        }
    }

    private static net.kyori.adventure.text.Component resolvePrefix(Player viewer, Player player,
                                                                    MatchSession session) {
        PrefixResolver resolver = prefixResolver;
        if (resolver == null) {
            return net.kyori.adventure.text.Component.empty();
        }
        try {
            net.kyori.adventure.text.Component prefix = resolver.resolve(viewer, player, session);
            return prefix == null ? net.kyori.adventure.text.Component.empty() : prefix;
        } catch (RuntimeException ignored) {
            return net.kyori.adventure.text.Component.empty();
        }
    }

    public static boolean isFightTeam(String name) {
        // All fight teams are "0<sortKey><name>"; legacy builds also used 0_fight_*.
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
            if (name.startsWith("0") || name.startsWith("1s") || name.equals(SPEC_TEAM)
                    || name.startsWith("rp_hp_")) {
                unregister(board, name);
            }
        }
        for (String legacy : LEGACY) {
            unregister(board, legacy);
        }
    }

    /**
     * Fight team name encoding the TAB layout: {@code 0} marks fighters (sorted before the
     * {@code 9} spectator team), the digit sort key orders the groups in canonical battle
     * order, and the lowercased player name sorts each roster alphabetically. Team names are
     * limited to 16 characters by the protocol, so the name part is capped at 14 chars.
     */
    private static String fightName(TeamColor color, String playerName) {
        String key = playerName.toLowerCase(java.util.Locale.ROOT);
        if (key.length() > 14) {
            key = key.substring(0, 14);
        }
        return "0" + color.sortKey() + key;
    }

    private static void removeFromManaged(Scoreboard board, String entry) {
        Team current = board.getEntryTeam(entry);
        if (current != null) {
            String name = current.getName();
            if (isFightTeam(name) || name.startsWith("1s") || name.equals(SPEC_TEAM)
                    || name.startsWith("rp_hp_") || name.startsWith("0_fight")
                    || name.equals("1_spec")) {
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
