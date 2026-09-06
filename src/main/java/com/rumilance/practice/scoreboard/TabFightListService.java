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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groups the TAB (player list) into one column per team for an active fight, with blank
 * padding rows so the columns stay visually separated even for tiny rosters.
 *
 * <p><b>Mechanism — the 1.21.2+ list-order index.</b> Since 1.21.2 the vanilla client no
 * longer sorts the tab list by scoreboard team name; the server controls the order through
 * a non-negative priority per player (snapshot 24w33a) and the client sorts it <i>highest
 * to lowest</i>. Paper exposes it for real players as {@link Player#setPlayerListOrder(int)}.
 * Roster columns are built by assigning each team its own priority band: the first team the
 * highest values, later teams lower bands, descending values inside a band yielding an
 * alphabetical top-to-bottom roster.</p>
 *
 * <p><b>Blank padding.</b> The client wraps the list into a new column every 20 entries,
 * so a 3-person roster alone would never form its own column. Every team band is therefore
 * padded up to a multiple of 20 rows with invisible fake player-info entries (blank display
 * name, no ping icon) sent per viewer via ProtocolLib — the same technique layout plugins
 * use. Each running match reserves a stable priority band ({@link #slotFor(UUID)}) so
 * simultaneous matches never interleave; lobby players keep priority 0 and sort last.</p>
 *
 * <p><b>Safety switches.</b> Servers running NBT-injector style packet patchers (NBTAPI /
 * Triton) can crash inside their patched {@code ClientboundPlayerInfoUpdatePacket} writer
 * on the 1.21.2 list-order action, which disconnects receivers when a match starts. Such
 * plugins are probed once and ordering auto-disables for them; operators can override
 * either way with {@code match.tab-columns-enabled}. Without ProtocolLib the layout still
 * groups real players via priorities — only the blank padding is skipped. Every packet
 * write is wrapped so a failure can never take down the scoreboard refresh.</p>
 *
 * <p>The display-name side stays as before: the client renders a set display name verbatim
 * and only falls back to "team prefix + team-coloured name" when it is unset, so on
 * entering the layout every roster player's custom list name is cleared once — revealing
 * the MatchTeamVisuals rank badge, team marker and team colour in the TAB. The styled name
 * is restored by {@link #clear(Player)}.</p>
 */
public final class TabFightListService {

    /** One team column: enough headroom for any realistic roster + padding. */
    private static final int SLOT_WIDTH = 1000;
    /** Priority space reserved per match: 7 team columns + the spectator column. */
    private static final int MATCH_SPAN = 8 * SLOT_WIDTH;
    /** Highest priority handed out; the client lists higher values first. */
    private static final int ORDER_TOP = 1_000_000;
    /** Vanilla wraps the tab list into a new column every 20 entries. */
    private static final int ROWS_PER_COLUMN = 20;
    /** Shared pool of blank pad entries (7 teams x 19 pads + spectator padding). */
    private static final int PAD_COUNT = 160;
    private static final UUID[] PAD_IDS = new UUID[PAD_COUNT];
    private static final String[] PAD_NAMES = new String[PAD_COUNT];
    private static final Map<UUID, String> PAD_NAME_BY_ID = new HashMap<>();

    static {
        for (int i = 0; i < PAD_COUNT; i++) {
            PAD_IDS[i] = UUID.randomUUID();
            PAD_NAMES[i] = String.format(java.util.Locale.ROOT, "NPad%03d", i);
            PAD_NAME_BY_ID.put(PAD_IDS[i], PAD_NAMES[i]);
        }
    }

    /** Plugins known to patch the player-info packet writer and crash on the 1.21.2 action. */
    private static final String[] PACKET_PATCHERS = {"NBTAPI", "Item-NBT-API", "TritonSpigot", "Triton"};

    private final org.bukkit.plugin.Plugin plugin;
    private com.rumilance.practice.rank.RankService rankService;
    private volatile com.rumilance.practice.config.ConfigService configService;
    /** Players whose custom tab-list display name was cleared for the fight layout. */
    private final Set<UUID> layoutApplied = new HashSet<>();
    /** Stable column-band slot per running match id. */
    private final Map<UUID, Integer> matchSlots = new HashMap<>();
    /** Per viewer: pad id -> priority currently sent to that client. */
    private final Map<UUID, Map<UUID, Integer>> sentPads = new ConcurrentHashMap<>();
    private volatile Boolean patcherCache;
    /** Set once a pad packet fails; padding then stays off for this boot. */
    private volatile boolean padsBroken;

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

    /** Blank padding needs the ordering packets plus ProtocolLib for the fake entries. */
    private boolean padsUsable() {
        if (padsBroken || !columnsEnabled()) {
            return false;
        }
        // Check the plain Bukkit side first so TabPadPackets (ProtocolLib types) is never
        // even class-loaded on servers without the soft-depend.
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return false;
        }
        try {
            return TabPadPackets.available();
        } catch (Throwable t) {
            padsBroken = true;
            return false;
        }
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

    /** Applies the fight layout (real players' priorities) for one match. */
    public void apply(MatchSession session, Collection<? extends Player> online) {
        if (session == null) {
            return;
        }
        if (session.state() != MatchState.ACTIVE && session.state() != MatchState.ENDING) {
            return;
        }
        layoutApplied.removeIf(id -> Bukkit.getPlayer(id) == null);
        boolean ordering = columnsEnabled();
        Groups groups = collectGroups(session, online);

        Comparator<Player> byName = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        int matchTop = ORDER_TOP - slotFor(session.id()) * MATCH_SPAN;
        int columnIndex = 0;
        for (TeamColor color : TeamColor.values()) { // canonical battle order RED -> GOLD
            List<Player> roster = groups.rosters().get(color);
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
        if (!groups.spectators().isEmpty()) {
            groups.spectators().sort(byName);
            int order = matchTop - columnIndex * SLOT_WIDTH;
            for (Player p : groups.spectators()) {
                applyListEntry(p, ordering, order--);
            }
        }
    }

    /**
     * Syncs the blank padding entries of one viewer with the layout of the match they are
     * in (or watching). Pads fill each team column up to a multiple of 20 rows so every
     * team starts at the top of its own column.
     */
    public void applyViewerPads(Player viewer, MatchSession session) {
        sentPads.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        Map<UUID, Integer> sent = sentPads.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>());
        boolean want = padsUsable() && session != null
                && (session.state() == MatchState.ACTIVE || session.state() == MatchState.ENDING);
        if (!want) {
            removeAllPads(viewer, sent);
            return;
        }
        Groups groups = collectGroups(session, Bukkit.getOnlinePlayers());
        int matchTop = ORDER_TOP - slotFor(session.id()) * MATCH_SPAN;
        Map<UUID, Integer> needed = new HashMap<>();
        int columnIndex = 0;
        int padIndex = 0;
        for (TeamColor color : TeamColor.values()) {
            List<Player> roster = groups.rosters().get(color);
            if (roster == null || roster.isEmpty()) {
                continue;
            }
            int base = matchTop - columnIndex * SLOT_WIDTH;
            int size = roster.size();
            int padCount = (ROWS_PER_COLUMN - size % ROWS_PER_COLUMN) % ROWS_PER_COLUMN;
            for (int j = 0; j < padCount && padIndex < PAD_COUNT; j++) {
                needed.put(PAD_IDS[padIndex], base - size - 1 - j);
                padIndex++;
            }
            columnIndex++;
        }

        List<UUID> stale = sent.keySet().stream().filter(id -> !needed.containsKey(id)).toList();
        if (!stale.isEmpty()) {
            try {
                TabPadPackets.removePads(viewer, stale);
            } catch (Throwable t) {
                padsBroken = true;
                return;
            }
            stale.forEach(sent::remove);
        }
        for (Map.Entry<UUID, Integer> entry : needed.entrySet()) {
            UUID padId = entry.getKey();
            int priority = entry.getValue();
            Integer current = sent.get(padId);
            if (current != null && current == priority) {
                continue;
            }
            try {
                if (current == null) {
                    TabPadPackets.addPad(viewer, padId, PAD_NAME_BY_ID.get(padId), priority);
                } else {
                    TabPadPackets.updatePriority(viewer, padId, PAD_NAME_BY_ID.get(padId), priority);
                }
                sent.put(padId, priority);
            } catch (Throwable t) {
                padsBroken = true;
                removeAllPads(viewer, sent);
                return;
            }
        }
    }

    private void removeAllPads(Player viewer, Map<UUID, Integer> sent) {
        if (sent.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(sent.keySet());
        sent.clear();
        if (!TabPadPackets.available()) {
            return;
        }
        try {
            TabPadPackets.removePads(viewer, ids);
        } catch (Throwable t) {
            padsBroken = true;
        }
    }

    /** Live fighters grouped by team colour plus every spectator, for one match. */
    private Groups collectGroups(MatchSession session, Collection<? extends Player> online) {
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
        return new Groups(rosters, spectators);
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

    /** Restores vanilla ordering, the styled list name and removes pads for one player. */
    public void clear(Player player) {
        Map<UUID, Integer> sent = sentPads.remove(player.getUniqueId());
        if (sent != null && !sent.isEmpty() && TabPadPackets.available()) {
            try {
                TabPadPackets.removePads(player, List.copyOf(sent.keySet()));
            } catch (Throwable t) {
                padsBroken = true;
            }
        }
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

    private record Groups(Map<TeamColor, List<Player>> rosters, List<Player> spectators) {
    }
}
