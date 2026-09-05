package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Groups the TAB (player list) for an active fight WITHOUT fake players and without any
 * player-info ordering packets. The columns come from the scoreboard team names applied by
 * {@link com.rumilance.practice.match.MatchTeamVisuals}: the client sorts tab entries with
 * equal list order by team name (ascending), so fighters ({@code 0<sortKey><name>}) are
 * grouped by team in canonical battle order with alphabetical rosters, and spectators
 * ({@code 9_spec}) sort last. That is the classic pre-1.21.2 grouping mechanism and works
 * on every client — including servers running NBT-injector style packet patchers that
 * crash on the 1.21.2 list-order action.
 *
 * <p>This service's own job is the display-name side: the client renders a set display name
 * verbatim in the tab list and only falls back to "team prefix + team-coloured name" when it
 * is unset, so on entering the fight layout every roster player's custom list name is
 * cleared once — revealing the MatchTeamVisuals rank badge, team marker and team colour in
 * the tab. The styled name is restored by {@link #clear(Player)}.</p>
 */
public final class TabFightListService {

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

    /** Master switch for the fight TAB layout (match.tab-columns-enabled, default on). */
    private boolean columnsEnabled() {
        com.rumilance.practice.config.ConfigService service = configService;
        return service == null || service.config().getBoolean("match.tab-columns-enabled", true);
    }

    /** Applies the fight layout to the tablist of every online player. */
    public void apply(MatchSession session, Collection<? extends Player> online) {
        if (!columnsEnabled() || session == null) {
            return;
        }
        if (session.state() != MatchState.ACTIVE && session.state() != MatchState.ENDING) {
            return;
        }
        layoutApplied.removeIf(id -> org.bukkit.Bukkit.getPlayer(id) == null);
        for (Player player : online) {
            boolean inLayout = session.isParticipant(player.getUniqueId())
                    || player.getGameMode() == GameMode.SPECTATOR;
            if (inLayout && layoutApplied.add(player.getUniqueId())) {
                player.playerListName(null);
            }
        }
    }

    /** Restores the rank-styled list name for one player leaving the fight layout. */
    public void clear(Player player) {
        if (!layoutApplied.remove(player.getUniqueId())) {
            return;
        }
        if (rankService != null) {
            rankService.applyNametag(player);
        }
    }
}
