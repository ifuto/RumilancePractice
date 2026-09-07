package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groups the TAB (player list) into labeled columns for an active fight:
 * <ul>
 *   <li><b>1v1 duel</b> — one merged <i>Players in Combat</i> column holding both fighters,
 *       styled with each player's own colour (name-colour cosmetic, falling back to their
 *       rank colour); a <i>Players in Spectating</i> column follows when anyone watches.</li>
 *   <li><b>Team fight</b> — one column per team, headed by the team label in the team
 *       colour, then the <i>Players in Spectating</i> column.</li>
 * </ul>
 * Every column is a header row, one blank spacer row, the alphabetical roster and blank
 * padding to a multiple of 20 rows so the next column starts at the top.
 *
 * <p><b>Mechanism — the 1.21.2+ list-order index.</b> Since 1.21.2 the vanilla client no
 * longer sorts the tab list by scoreboard team name; the server controls the order through
 * a non-negative priority per player (snapshot 24w33a) and the client sorts it <i>highest
 * to lowest</i>. Paper exposes it for real players as {@link Player#setPlayerListOrder(int)}.
 * Roster columns are built by assigning each team its own priority band: the first team the
 * highest values, later teams lower bands, descending values inside a band yielding an
 * alphabetical top-to-bottom roster.</p>
 *
 * <p><b>Blank padding &amp; headers.</b> The client wraps the list into a new column every 20
 * entries, so a 3-person roster alone would never form its own column. Every column is
 * therefore padded up to a multiple of 20 rows with invisible fake player-info entries sent
 * per viewer via ProtocolLib — the same technique layout plugins use. Header rows and spacer
 * rows are pads with a styled display name instead of a blank one. Each running match
 * reserves a stable priority band ({@link #slotFor(UUID)}) so simultaneous matches never
 * interleave; lobby players keep priority 0 and sort last.</p>
 *
 * <p><b>Safety switches.</b> Servers running NBT-injector style packet patchers (NBTAPI /
 * Triton) can crash inside their patched {@code ClientboundPlayerInfoUpdatePacket} writer
 * on the 1.21.2 list-order action, which disconnects receivers when a match starts. Such
 * plugins are probed once and ordering auto-disables for them; operators can override
 * either way with {@code match.tab-columns-enabled}. Without ProtocolLib the layout still
 * groups real players via priorities — only the padding and headers are skipped. Every
 * packet write is wrapped so a failure can never take down the scoreboard refresh.</p>
 *
 * <p><b>Display-name side.</b> In team fights the custom list name is cleared so the client
 * renders team prefix + team-coloured names from the scoreboard visuals. In duels the
 * player's personal styled name (name colour, else rank colour) is applied instead. Both are
 * restored by {@link #clear(Player)} when the layout ends.</p>
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
    /** Shared pool of pad entries (headers, spacers and blank pads). */
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

    /** Column header texts, legacy-formatted for the fake-entry display names. */
    private static final String HEADER_COMBAT = "\u00A7bPlayers in Combat";
    private static final String HEADER_SPECTATING = "\u00A77Players in Spectating";
    private static final String BLANK = " ";

    /** Plugins known to patch the player-info packet writer and crash on the 1.21.2 action. */
    private static final String[] PACKET_PATCHERS = {"NBTAPI", "Item-NBT-API", "TritonSpigot", "Triton"};

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final org.bukkit.plugin.Plugin plugin;
    private com.rumilance.practice.rank.RankService rankService;
    private com.rumilance.practice.cosmetic.namecolor.NameColorService nameColorService;
    private volatile com.rumilance.practice.config.ConfigService configService;
    /** Players whose custom tab-list display name was taken over for the fight layout. */
    private final Set<UUID> layoutApplied = new HashSet<>();
    /** Per player: the list-name content the layout last wrote (null = cleared for teams). */
    private final Map<UUID, Component> layoutNames = new HashMap<>();
    /** Stable column-band slot per running match id. */
    private final Map<UUID, Integer> matchSlots = new HashMap<>();
    /** Per viewer: pad id -> entry state currently sent to that client. */
    private final Map<UUID, Map<UUID, PadState>> sentPads = new ConcurrentHashMap<>();
    private volatile Boolean patcherCache;
    /** Set once a pad packet fails; padding then stays off for this boot. */
    private volatile boolean padsBroken;

    public TabFightListService(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    public void setRankService(com.rumilance.practice.rank.RankService rankService) {
        this.rankService = rankService;
    }

    public void setNameColorService(
            com.rumilance.practice.cosmetic.namecolor.NameColorService nameColorService) {
        this.nameColorService = nameColorService;
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

    /** Blank padding & headers need the ordering packets plus ProtocolLib for the fake entries. */
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
        Comparator<Player> byName = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        Plan plan = planColumns(session, online, byName);
        int matchTop = ORDER_TOP - slotFor(session.id()) * MATCH_SPAN;
        int columnIndex = 0;
        for (Column column : plan.columns()) {
            int order = matchTop - columnIndex * SLOT_WIDTH - TabFightLayout.HEADER_ROWS;
            for (Player p : column.roster()) {
                applyListEntry(p, ordering, order--, column.personalStyle() ? personalListName(p) : null);
            }
            columnIndex++;
        }
    }

    /**
     * Syncs the header / spacer / blank padding entries of one viewer with the layout of the
     * match they are in (or watching). Pads fill each column up to a multiple of 20 rows so
     * every column starts at the top of its own client-side column.
     */
    public void applyViewerPads(Player viewer, MatchSession session) {
        sentPads.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        Map<UUID, PadState> sent =
                sentPads.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>());
        boolean want = padsUsable() && session != null
                && (session.state() == MatchState.ACTIVE || session.state() == MatchState.ENDING);
        if (!want) {
            removeAllPads(viewer, sent);
            return;
        }
        Comparator<Player> byName = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        Plan plan = planColumns(session, Bukkit.getOnlinePlayers(), byName);
        int matchTop = ORDER_TOP - slotFor(session.id()) * MATCH_SPAN;
        Map<UUID, PadState> needed = new HashMap<>();
        int columnIndex = 0;
        int padIndex = 0;
        outer:
        for (Column column : plan.columns()) {
            int base = matchTop - columnIndex * SLOT_WIDTH;
            // Header row + spacer row.
            if (!reserve(needed, padIndex, base, column.header())) {
                break;
            }
            padIndex++;
            if (!reserve(needed, padIndex, base - 1, BLANK)) {
                break;
            }
            padIndex++;
            int rosterSize = column.roster().size();
            int padCount = TabFightLayout.padCount(rosterSize);
            for (int j = 0; j < padCount; j++) {
                int priority = base - TabFightLayout.HEADER_ROWS - rosterSize - j;
                if (!reserve(needed, padIndex, priority, BLANK)) {
                    break outer;
                }
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
        for (Map.Entry<UUID, PadState> entry : needed.entrySet()) {
            UUID padId = entry.getKey();
            PadState desired = entry.getValue();
            PadState current = sent.get(padId);
            if (desired.equals(current)) {
                continue;
            }
            String padName = PAD_NAME_BY_ID.get(padId);
            try {
                if (current == null) {
                    TabPadPackets.addPad(viewer, padId, padName, desired.priority(), desired.display());
                } else {
                    if (!Objects.equals(current.display(), desired.display())) {
                        TabPadPackets.updateDisplayName(viewer, padId, padName, desired.display());
                    }
                    if (current.priority() != desired.priority()) {
                        TabPadPackets.updatePriority(viewer, padId, padName, desired.priority());
                    }
                }
                sent.put(padId, desired);
            } catch (Throwable t) {
                padsBroken = true;
                removeAllPads(viewer, sent);
                return;
            }
        }
    }

    private boolean reserve(Map<UUID, PadState> needed, int padIndex, int priority, String display) {
        if (padIndex >= PAD_COUNT) {
            return false;
        }
        needed.put(PAD_IDS[padIndex], new PadState(priority, display));
        return true;
    }

    private void removeAllPads(Player viewer, Map<UUID, PadState> sent) {
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

    /**
     * Builds the column plan for one match — the single source of truth shared by the real
     * priority assignment and the per-viewer padding, so both can never drift apart.
     */
    private Plan planColumns(MatchSession session, Collection<? extends Player> online,
                             Comparator<Player> byName) {
        Groups groups = collectGroups(session, online);
        boolean duel = groups.rosters().values().stream().allMatch(r -> r.size() <= 1);
        List<Column> columns = new ArrayList<>();
        if (duel && !groups.rosters().isEmpty()) {
            List<Player> combat = new ArrayList<>();
            for (TeamColor color : TeamColor.values()) {
                List<Player> roster = groups.rosters().get(color);
                if (roster != null) {
                    combat.addAll(roster);
                }
            }
            combat.sort(byName);
            if (!combat.isEmpty()) {
                columns.add(new Column(HEADER_COMBAT, combat, true));
            }
        } else if (!duel) {
            for (TeamColor color : TeamColor.values()) { // canonical battle order RED -> GOLD
                List<Player> roster = groups.rosters().get(color);
                if (roster == null || roster.isEmpty()) {
                    continue;
                }
                roster.sort(byName);
                columns.add(new Column(teamHeader(color), roster, false));
            }
        }
        if (!groups.spectators().isEmpty()) {
            groups.spectators().sort(byName);
            columns.add(new Column(HEADER_SPECTATING, new ArrayList<>(groups.spectators()), false));
        }
        return new Plan(columns);
    }

    /** Team column header: the team label in its own colour, legacy-formatted. */
    private static String teamHeader(TeamColor color) {
        return LEGACY.serialize(Component.text(color.label(), color.textColor()));
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
     * every cycle). The list name is taken over once on entering the layout, and re-synced
     * if the styled content changes (e.g. a colour switch or a duel/team scheme flip during
     * eliminations). {@code personalName == null} means "clear the custom name" (team
     * fights), which reveals the scoreboard team's prefix and colour.
     */
    private void applyListEntry(Player player, boolean ordering, int order, Component personalName) {
        if (ordering) {
            try {
                if (player.getPlayerListOrder() != order) {
                    player.setPlayerListOrder(order);
                }
            } catch (Throwable ignored) {
                // Never let a tab-layout write break the scoreboard refresh.
            }
        }
        UUID id = player.getUniqueId();
        boolean entering = layoutApplied.add(id);
        if (entering) {
            player.playerListName(personalName);
            layoutNames.put(id, personalName);
        } else if (!Objects.equals(layoutNames.get(id), personalName)) {
            player.playerListName(personalName);
            layoutNames.put(id, personalName);
        }
    }

    /**
     * The player's own coloured name for the duel combat column: name-colour cosmetic when
     * active, otherwise the rank-styled name, otherwise null (falls back to team visuals).
     */
    private Component personalListName(Player player) {
        com.rumilance.practice.cosmetic.namecolor.NameColorService ncs = nameColorService;
        if (ncs != null && ncs.selection(player.getUniqueId()).active()) {
            return ncs.styledName(player);
        }
        com.rumilance.practice.rank.RankService ranks = rankService;
        if (ranks != null) {
            return ranks.styledComponentName(player);
        }
        return null;
    }

    /** Restores vanilla ordering, the styled list name and removes pads for one player. */
    public void clear(Player player) {
        Map<UUID, PadState> sent = sentPads.remove(player.getUniqueId());
        if (sent != null && !sent.isEmpty() && TabPadPackets.available()) {
            try {
                TabPadPackets.removePads(player, List.copyOf(sent.keySet()));
            } catch (Throwable t) {
                padsBroken = true;
            }
        }
        layoutNames.remove(player.getUniqueId());
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
        com.rumilance.practice.cosmetic.namecolor.NameColorService ncs = nameColorService;
        if (ncs != null && ncs.selection(player.getUniqueId()).active()) {
            player.playerListName(ncs.styledName(player));
        }
    }

    private record Groups(Map<TeamColor, List<Player>> rosters, List<Player> spectators) {
    }

    /** One planned TAB column: header text, roster and whether duel names are style-taken-over. */
    private record Column(String header, List<Player> roster, boolean personalStyle) {
    }

    private record Plan(List<Column> columns) {
    }

    /** What a pad entry currently shows: priority + display text. */
    private record PadState(int priority, String display) {
    }
}
