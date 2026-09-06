package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Groups the TAB (player list) into team columns for an active fight.
 *
 * <p><b>Mechanism — the 1.21.2+ list-order index.</b> Since 1.21.2 the vanilla client no
 * longer sorts the tab list by scoreboard team name; the server controls the order through a
 * non-negative ordering index per player (added in snapshot 24w33a) and the client sorts it
 * <i>highest to lowest</i>. Paper exposes it as {@link Player#setPlayerListOrder(int)}.
 * Roster columns are built by assigning each team its own index band: the first team gets
 * the highest values, later teams lower bands, and descending values inside a band yield an
 * alphabetical top-to-bottom roster. The client wraps entries into the next column every
 * 20 rows, so each team naturally becomes its own column. Spectators get the band below the
 * last team; lobby players keep the default index 0 and therefore always sort last.</p>
 *
 * <p>Each running match reserves a stable band of indexes ({@link #slotFor(UUID)}), so
 * several simultaneous matches never interleave their columns.</p>
 *
 * <p><b>Safety switch.</b> Servers running NBT-injector style packet patchers (NBTAPI /
 * Triton) can crash inside their patched {@code ClientboundPlayerInfoUpdatePacket} writer
 * on the 1.21.2 UPDATE_LIST_ORDER action, which disconnects receivers when a match starts.
 * Such plugins are probed once and ordering auto-disables for them; operators can override
 * either way with {@code match.tab-columns-enabled}. Every order write is additionally
 * wrapped so a failure can never take down the scoreboard refresh.</p>
 *
 * <p>The display-name side stays as before: the client renders a set display name verbatim
 * and only falls back to "team prefix + team-coloured name" when it is unset, so on
 * entering the layout every roster player's custom list name is cleared once — revealing
 * the MatchTeamVisuals rank badge, team marker and team colour in the TAB. The styled name
 * is restored by {@link #clear(Player)}.</p>
 */
public final class TabFightListService {

    /** One team column: enough headroom for any realistic roster. */
    private static final int SLOT_WIDTH = 1000;
    /** Index space reserved per match: 7 team columns + the spectator column. */
    private static final int MATCH_SPAN = 8 * SLOT_WIDTH;
    /** Highest index handed out; the client lists higher indexes first. */
    private static final int ORDER_TOP = 1_000_000;
    /** Plugins known to patch the player-info packet writer and crash on UPDATE_LIST_ORDER. */
    private static final String[] PACKET_PATCHERS = {"NBTAPI", "Item-NBT-API", "TritonSpigot", "Triton"};

    private final org.bukkit.plugin.Plugin plugin;
    private com.rumilance.practice.rank.RankService rankService;
    private volatile com.rumilance.practice.config.ConfigService configService;
    /** Players whose custom tab-list display name was cleared for the fight layout. */
    private final Set<UUID> layoutApplied = new HashSet<>();
    /** Stable column-band slot per running match id. */
    private final Map<UUID, Integer> matchSlots = new HashMap<>();
    private volatile Boolean patcherCache;

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
     * Whether list-order packets may be sent. An explicitly configured
     * {@code match.tab-columns-enabled} always wins; otherwise the layout is on unless a
     * known packet-patcher plugin is present (those crash on the 1.21.2 action).
     */
    private boolean columnsEnabled() {
        com.rumilance.practice.config.ConfigService service = configService;
        if (service != null && service.config().isSet("match.tab-columns-enabled")) {
            return service.config().getBoolean("match.tab-columns-enabled", true);
        }
        return !packetPatcherDetected();
    }

    private boolean packetPatcherDetected() {
        Boolean cached = patcherCache;
        if (cached != null) {
            return cached;
        }
        for (String name : PACKET_PATCHERS) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                patcherCache = true;
                plugin.getLogger().warning("TAB fight columns disabled: packet-patcher plugin '"
                        + name + "' crashes on 1.21.2 list-order packets. "
                        + "Set match.tab-columns-enabled: true to force them anyway.");
                return true;
            }
        }
        patcherCache = false;
        return false;
    }

    /** Reserves a stable column band for a match (lowest free slot). */
    private int slotFor(UUID matchId) {
        return matchSlots.computeIfAbsent(matchId, id -> {
            Set<Integer> used = new HashSet<>(matchSlots.values());
            int slot = 0;
            while (used.contains(slot)) {
                slot++;
            }
            return slot;
        });
    }

    /** Drops column-band reservations of matches that were not applied this refresh cycle. */
    public void prune(Set<UUID> activeMatches) {
        matchSlots.keySet().retainAll(activeMatches);
    }

    /** Applies the fight layout to the tablist of every online player. */
    public void apply(MatchSession session, Collection<? extends Player> online) {
        if (session == null) {
            return;
        }
        if (session.state() != MatchState.ACTIVE && session.state() != MatchState.ENDING) {
            return;
        }
        layoutApplied.removeIf(id -> Bukkit.getPlayer(id) == null);
        boolean ordering = columnsEnabled();

        Map<TeamColor, List<Player>> rosters = new EnumMap<>(TeamColor.class);
        List<Player> spectators = new ArrayList<>();
        for (Player p : online) {
            boolean fighting = session.isParticipant(p.getUniqueId())
                    && !session.isEliminated(p.getUniqueId())
                    && p.getGameMode() != GameMode.SPECTATOR;
            if (fighting) {
                rosters.computeIfAbsent(session.teamColor(p.getUniqueId()), k -> new ArrayList<>()).add(p);
            } else if (p.getGameMode() == GameMode.SPECTATOR) {
                spectators.add(p);
            }
        }

        Comparator<Player> byName = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        int matchTop = ORDER_TOP - slotFor(session.id()) * MATCH_SPAN;
        int columnIndex = 0;
        for (TeamColor color : TeamColor.values()) { // canonical battle order RED -> GOLD
            List<Player> roster = rosters.get(color);
            if (roster == null || roster.isEmpty()) {
                continue;
            }
            roster.sort(byName);
            int order = matchTop - columnIndex * SLOT_WIDTH;
            for (Player p : roster) {
                applyListEntry(p, ordering, order--);
            }
            columnIndex++;
        }
        if (!spectators.isEmpty()) {
            spectators.sort(byName);
            int order = matchTop - columnIndex * SLOT_WIDTH;
            for (Player p : spectators) {
                applyListEntry(p, ordering, order--);
            }
        }
    }

    /**
     * Applies one tablist entry. Ordering is only written when it changed (this runs on the
     * periodic scoreboard refresh and must not re-broadcast identical player-info updates
     * every cycle). The custom list name is cleared once on entering the layout.
     */
    private void applyListEntry(Player player, boolean ordering, int order) {
        if (ordering) {
            try {
                if (player.getPlayerListOrder() != order) {
                    player.setPlayerListOrder(order);
                }
            } catch (Throwable ignored) {
                // Never let a tab-layout write break the scoreboard refresh.
            }
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
        try {
            if (player.getPlayerListOrder() != 0) {
                player.setPlayerListOrder(0);
            }
        } catch (Throwable ignored) {
            // see applyListEntry
        }
        if (rankService != null) {
            rankService.applyNametag(player);
        }
    }
}
