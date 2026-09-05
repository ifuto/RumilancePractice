package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controls the TAB (player list) layout for an active fight, keeping the roster readable at
 * a glance. Purely a sort-order service on top of vanilla packets — no fake players, so it
 * stays fully compatible with any tablist plugin the server may add later.
 *
 * <p>Ordering uses the vanilla list-order index added in 1.21.2 (snapshot 24w33a:
 * "a non-negative ordering index that is sorted highest to lowest", exposed by Paper as
 * {@link Player#setPlayerListOrder(int)}). Higher values are therefore listed FIRST: the
 * first sort group gets the highest priorities, and the client wraps entries into the next
 * column every 20 rows automatically. Within a group players are listed alphabetically by
 * assigning descending priorities in name order.</p>
 *
 * <p>Layout rules:</p>
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

    /** Priority bases — higher groups are listed first (vanilla sorts highest to lowest). */
    private static final int FIRST_COLUMN_BASE = 3000;
    private static final int SECOND_COLUMN_BASE = 2000;
    private static final int THIRD_COLUMN_BASE = 1000;

    private final org.bukkit.plugin.Plugin plugin;
    private com.rumilance.practice.rank.RankService rankService;
    private volatile com.rumilance.practice.config.ConfigService configService;
    /** Players whose custom tab-list display name was cleared for the fight layout. */
    private final Set<UUID> layoutApplied = new HashSet<>();

    public TabFightListService(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    public void setRankService(com.rumilance.practice.rank.RankService rankService) {
        this.rankService = rankService;
    }

    public void setConfigService(com.rumilance.practice.config.ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Whether the column layout may be applied via player-list-order packets. Some servers run
     * NBT-injector style plugins (NBTAPI/Triton and similar) that rewrite
     * {@code ClientboundPlayerInfoUpdatePacket} with a writer predating the 1.21.2
     * UPDATE_LIST_ORDER action; sending it there throws inside their patched packet class and
     * disconnects the receiving players the moment a match starts. On such servers this stays
     * off — the tab keeps vanilla ordering while name colours still work.
     */
    private boolean columnsEnabled() {
        com.rumilance.practice.config.ConfigService service = configService;
        return service != null && service.config().getBoolean("match.tab-columns-enabled", false);
    }

    /** Applies the fight layout to the tablist of every online player. */
    public void apply(MatchSession session, Collection<? extends Player> online) {
        if (session == null) {
            return;
        }
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

        layoutApplied.removeIf(id -> org.bukkit.Bukkit.getPlayer(id) == null);
        boolean ordering = columnsEnabled();
        if (session.isTeamMatch()) {
            // Column 1 = RED, column 2 = BLUE, column 3 = spectators. Vanilla sorts the list
            // order index highest to lowest, so the first column gets the highest values and
            // descending values within a group yield alphabetical top-to-bottom rosters.
            int order = FIRST_COLUMN_BASE;
            for (Player p : red) {
                applyListEntry(p, ordering, order--);
            }
            order = SECOND_COLUMN_BASE;
            for (Player p : blue) {
                applyListEntry(p, ordering, order--);
            }
            order = THIRD_COLUMN_BASE;
            for (Player p : spectators) {
                applyListEntry(p, ordering, order--);
            }
        } else {
            // Column 1 = fighters, column 2 = spectators.
            int order = FIRST_COLUMN_BASE;
            for (Player p : fighters) {
                applyListEntry(p, ordering, order--);
            }
            order = SECOND_COLUMN_BASE;
            for (Player p : spectators) {
                applyListEntry(p, ordering, order--);
            }
        }
    }

    /**
     * Applies one tablist entry. Ordering packets are only sent when enabled and changed
     * (this runs on the periodic scoreboard refresh and must not re-broadcast identical
     * player-info updates every cycle). The custom list name is cleared once on entering
     * the layout: the client renders a set display name verbatim and only falls back to
     * "scoreboard team prefix + team-coloured name" when no display name is set, so a custom
     * name would hide the MatchTeamVisuals badge/marker/colour in the tab list.
     */
    private void applyListEntry(Player player, boolean ordering, int order) {
        if (ordering && player.getPlayerListOrder() != order) {
            player.setPlayerListOrder(order);
        }
        if (layoutApplied.add(player.getUniqueId())) {
            player.playerListName(null);
        }
    }

    /** Restores vanilla ordering and the rank-styled list name for one player. */
    public void clear(Player player) {
        if (!layoutApplied.remove(player.getUniqueId())) {
            return;
        }
        if (player.getPlayerListOrder() != 0) {
            player.setPlayerListOrder(0);
        }
        if (rankService != null) {
            rankService.applyNametag(player);
        }
    }
}
