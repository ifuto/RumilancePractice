package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Controls the TAB (player list) layout for an active fight, keeping the roster readable at
 * a glance. Purely a sort-order service on top of vanilla packets — no fake players, so it
 * stays fully compatible with any tablist plugin the server may add later.
 *
 * <p>Layout rules (max 20 rows per column, columns are 20-order wide):</p>
 * <ul>
 *   <li><b>Team fights</b> — column 1 is the RED roster, column 2 the BLUE roster, column 3
 *   spectators. RED members are rendered with red names, BLUE with blue names and spectators
 *   in gray, so the two sides are obvious without header text (the vanilla player list has no
 *   way to show non-player rows without fake players).</li>
 *   <li><b>Non-team fights (duels)</b> — column 1 lists the fighters with their RED/BLUE team
 *   colour, column 2 lists spectators.</li>
 * </ul>
 *
 * <p>When a team roster exceeds 20 members the vanilla client auto-wraps into the next column
 * in roster order, so overflow stays readable without any custom scrolling.</p>
 */
public final class TabFightListService {

    /** Start order of the second column (20 rows per column). */
    private static final int COL_2_BASE = 20;
    /** Start order of the third column (spectators in team fights). */
    private static final int COL_3_BASE = 40;

    private final org.bukkit.plugin.Plugin plugin;
    private com.rumilance.practice.rank.RankService rankService;

    public TabFightListService(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    public void setRankService(com.rumilance.practice.rank.RankService rankService) {
        this.rankService = rankService;
    }

    /** Applies the fight layout to the tablist of every online player. */
    public void apply(MatchSession session, Collection<? extends Player> online) {
        List<Player> fighters = new ArrayList<>();
        List<Player> red = new ArrayList<>();
        List<Player> blue = new ArrayList<>();
        List<Player> spectators = new ArrayList<>();

        for (Player p : online) {
            boolean fighting = session.isParticipant(p.getUniqueId())
                    && !session.isEliminated(p.getUniqueId())
                    && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
            if (fighting) {
                fighters.add(p);
                TeamColor color = session.teamColor(p.getUniqueId());
                if (color == TeamColor.RED) {
                    red.add(p);
                } else if (color == TeamColor.BLUE) {
                    blue.add(p);
                }
            } else if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                spectators.add(p);
            }
        }
        Comparator<Player> byName = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        red.sort(byName);
        blue.sort(byName);
        fighters.sort(byName);
        spectators.sort(byName);

        if (session.state() != MatchState.ACTIVE && session.state() != MatchState.ENDING) {
            return;
        }

        if (session.isTeamMatch()) {
            // Column 1 = RED, column 2 = BLUE, column 3 = spectators.
            int order = 0;
            for (Player p : red) {
                p.setPlayerListOrder(order++);
                p.playerListName(Component.text(p.getName(), NamedTextColor.RED));
            }
            order = COL_2_BASE;
            for (Player p : blue) {
                p.setPlayerListOrder(order++);
                p.playerListName(Component.text(p.getName(), NamedTextColor.BLUE));
            }
            order = COL_3_BASE;
            for (Player p : spectators) {
                p.setPlayerListOrder(order++);
                p.playerListName(Component.text(p.getName(), NamedTextColor.DARK_GRAY));
            }
        } else {
            // Column 1 = fighters (their RED/BLUE team colour), column 2 = spectators.
            int order = 0;
            for (Player p : fighters) {
                p.setPlayerListOrder(order++);
                TeamColor color = session.teamColor(p.getUniqueId());
                if (color != null) {
                    p.playerListName(Component.text(p.getName(),
                            color == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.BLUE));
                }
            }
            order = COL_2_BASE;
            for (Player p : spectators) {
                p.setPlayerListOrder(order++);
                p.playerListName(Component.text(p.getName(), NamedTextColor.DARK_GRAY));
            }
        }
    }

    /** Restores vanilla ordering and the rank-styled list name for one player. */
    public void clear(Player player) {
        player.setPlayerListOrder(0);
        if (rankService != null) {
            rankService.applyNametag(player);
        }
    }
}
