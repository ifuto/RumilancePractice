package com.rumilance.practice.scoreboard;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.util.RealPlayers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Renders the fight TAB grid — the column layout of the operator's TAB sample:
 *
 * <ul>
 *   <li><b>1v1</b> — one merged <i>In-Game Players</i> column (the "your mcid / opponent mcid"
 *       pair, coloured by team) followed by the <i>Spectators</i> column.</li>
 *   <li><b>Party fight</b> — one column per team headed by "● Red Team" / "● Blue Team" in the
 *       team colour, the roster as "Name ●", fallen players kept in their team column as the
 *       faded "Name ● - Death" row, then the <i>Spectators</i> column.</li>
 * </ul>
 *
 * <p><b>The grid model (TAB's layout model).</b> The client sorts the player list by the
 * server-provided list order (highest first) and starts a new column every 20 entries, so a
 * half-empty team only forms its own column when the remaining rows are filled with disposable
 * entries. Every column here is therefore exactly 20 rows — header, blank spacer, roster, then
 * blank fillers — each row is given the absolute order
 * {@code Integer.MAX_VALUE - matchBand - slot} (slot 1 = first row of the first column, exactly
 * like TAB's {@code COLUMNS} direction), and the header/filler rows are sent to each viewer as
 * fake player-info entries ({@link TabEntryPackets}). Putting the grid at the very top of the
 * list keeps it clear of the lobby ordering of {@code tab-layout.csv} (which tops out around
 * 5.9M): the two used to share a band and the lobby rows pushed the fight grid into the wrong
 * columns.</p>
 *
 * <p><b>Real players vs filler.</b> Participants keep their real entries (chat completion,
 * {@code /msg}, other plugins) and are positioned by {@link Player#setPlayerListOrder(int)};
 * only the rows they do not occupy are sent as filler per viewer, and the filler is removed
 * again when the viewer leaves the match. While the grid is shown to a viewer, everyone the
 * grid does not place — lobby players, players of another match, their spectators — is hidden
 * from that viewer's list with a {@code listed = false} entry: an outside entry carries its own
 * match's order, which would otherwise sit above the grid and shift every column break by a
 * row. Leaving the layout re-lists them. Bots are never touched — they are not part of
 * {@code RealPlayers.online()} and stay stripped from the list by {@code PacketBot}.</p>
 *
 * <p><b>Safety switches.</b> Servers running NBT-injector style packet patchers (NBTAPI /
 * Triton) can crash inside their patched player-info writer on the list-order field, which
 * disconnects receivers when a match starts. Such plugins are probed once and the layout
 * auto-disables for them; operators can override either way with
 * {@code match.tab-columns-enabled}.</p>
 */
public final class TabFightListService {

    /** Client slots reserved per running match: the grid of one match never touches the next. */
    static final int SLOTS_PER_MATCH = TabFightLayout.SLOTS_PER_COLUMN * TabFightLayout.MAX_COLUMNS;
    /** Highest list order handout — the fight grid sits above every lobby order. */
    static final int ORDER_TOP = Integer.MAX_VALUE;
    /** Namespace of the deterministic filler UUIDs (0x4E4152454E41 = "NARENA"). */
    private static final long FILLER_NAMESPACE = 0x4E4152454E410000L;

    private static final Component HEADER_COMBAT =
            Component.text("In-Game Players", NamedTextColor.AQUA);
    private static final Component HEADER_SPECTATING =
            Component.text("Spectators", NamedTextColor.GREEN);
    private static final Component BLANK = Component.empty();
    private static final TextColor SPECTATOR_COLOR = NamedTextColor.GRAY;
    private static final TextColor DEATH_COLOR = NamedTextColor.DARK_GRAY;

    /** Plugins known to patch the player-info packet writer and crash on the 1.21.2 action. */
    private static final String[] PACKET_PATCHERS = {"NBTAPI", "Item-NBT-API", "TritonSpigot", "Triton"};

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
    /** Per viewer: filler id -> entry state currently sent to that client. */
    private final Map<UUID, Map<UUID, PadState>> sentPads = new ConcurrentHashMap<>();
    /** Per viewer: real players whose list entry this grid hid (re-listed when they leave). */
    private final Map<UUID, Set<UUID>> unlisted = new ConcurrentHashMap<>();
    private volatile Boolean patcherCache;
    /** Set once a filler packet fails; the grid then stays off for this boot. */
    private volatile boolean padsBroken;
    /** One INFO line per boot so operators can confirm the grid reaches clients. */
    private volatile boolean layoutLogged;

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
                log(Level.WARNING, "TAB fight columns disabled: packet-patcher plugin '"
                        + name + "' crashes on player-info list-order packets. "
                        + "Set match.tab-columns-enabled: true to force them anyway.", null);
                return true;
            }
        }
        patcherCache = false;
        return false;
    }

    /** Headers and blank filler rows need the player-info packets to reach the client. */
    private boolean padsUsable() {
        if (padsBroken || !columnsEnabled()) {
            return false;
        }
        boolean usable;
        try {
            usable = TabEntryPackets.available();
        } catch (Throwable t) {
            usable = false;
        }
        if (!usable) {
            if (padsBroken) {
                return false;
            }
            padsBroken = true;
            log(Level.WARNING, "TAB fight layout: the server's player-info packet classes are"
                    + " unavailable — the fight TAB keeps the team order but loses the column"
                    + " headers and blank rows.", null);
            return false;
        }
        return true;
    }

    /** Reserves a stable column band for a match (lowest free slot). */
    int slotFor(UUID matchId) {
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

    /**
     * Client list order of one grid slot. Higher orders are listed first, so every row of the
     * grid sorts above the lobby ranks ({@code tab-layout.csv} maxes out at 5.9M) and the rows
     * of a match keep their reading order inside the match's own band.
     */
    static int orderOf(long matchSlot, int slot) {
        long order = (long) ORDER_TOP - matchSlot * (long) SLOTS_PER_MATCH - slot;
        return order < 1 ? 1 : (int) order;
    }

    /** Deterministic filler UUID — one per (match band, slot), never a real player's. */
    static UUID padId(long matchSlot, int slot) {
        return new UUID(FILLER_NAMESPACE, matchSlot * (long) SLOTS_PER_MATCH + slot);
    }

    /** Applies the fight grid (real players' order + names) for one match. */
    public void apply(MatchSession session, Collection<? extends Player> online) {
        if (!running(session)) {
            return;
        }
        layoutApplied.removeIf(id -> Bukkit.getPlayer(id) == null);
        boolean ordering = columnsEnabled();
        Grid grid = buildGrid(session, online);
        long matchSlot = slotFor(session.id());
        int slot = 0;
        for (GridRow row : grid.rows()) {
            slot++;
            if (row.player() == null || slot > SLOTS_PER_MATCH) {
                continue;
            }
            applyListEntry(row.player(), ordering, orderOf(matchSlot, slot), row.display());
        }
    }

    /**
     * Syncs the header / spacer / blank filler rows of one viewer with the grid of the match
     * they are in (or watching). Only the rows without a real entry are sent, so the client
     * sees every column exactly 20 rows tall.
     */
    public void applyViewerPads(Player viewer, MatchSession session) {
        sentPads.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        unlisted.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        Map<UUID, PadState> sent =
                sentPads.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>());
        if (!running(session) || !padsUsable()) {
            removeAllPads(viewer, sent);
            restoreListing(viewer);
            return;
        }
        Grid grid = buildGrid(session, RealPlayers.online());
        if (grid.rows().isEmpty()) {
            removeAllPads(viewer, sent);
            restoreListing(viewer);
            return;
        }
        // The grid is the whole list for a viewer inside a match (TAB hides the real entries of
        // everyone outside its layout for the same reason): an entry from another match carries
        // its own match's order and would otherwise sit above this grid and shift every column
        // break by a row.
        syncListing(viewer, grid);
        long matchSlot = slotFor(session.id());
        Map<UUID, PadState> needed = new LinkedHashMap<>();
        int slot = 0;
        for (GridRow row : grid.rows()) {
            slot++;
            if (slot > SLOTS_PER_MATCH) {
                break;
            }
            if (row.player() != null) {
                continue;
            }
            needed.put(padId(matchSlot, slot), new PadState(orderOf(matchSlot, slot), row.display()));
        }

        List<UUID> stale = sent.keySet().stream().filter(id -> !needed.containsKey(id)).toList();
        if (!stale.isEmpty()) {
            try {
                TabEntryPackets.remove(viewer, stale);
            } catch (Throwable t) {
                fail(t);
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
            try {
                if (current == null) {
                    TabEntryPackets.add(viewer, padId, desired.display(), desired.order());
                } else {
                    TabEntryPackets.update(viewer, padId, desired.display(), desired.order());
                }
                sent.put(padId, desired);
            } catch (Throwable t) {
                fail(t);
                removeAllPads(viewer, sent);
                return;
            }
        }
        logLayoutOnce(grid, needed.size());
    }

    /**
     * Keeps {@code viewer}'s list down to the grid: every real player that the grid does not
     * place is hidden from this viewer's player list, and re-listed as soon as they belong to
     * it (or when the viewer leaves the layout). Bots are never touched — they are not part of
     * {@link RealPlayers#online()} and stay stripped from the list by {@code PacketBot}.
     */
    private void syncListing(Player viewer, Grid grid) {
        Set<UUID> gridPlayers = new HashSet<>();
        for (GridRow row : grid.rows()) {
            if (row.player() != null) {
                gridPlayers.add(row.player().getUniqueId());
            }
        }
        Set<UUID> hidden = unlisted.computeIfAbsent(
                viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        Set<UUID> wanted = new HashSet<>();
        for (Player other : RealPlayers.online()) {
            // Never hide the viewer's own row, even if the grid did not place it.
            if (!other.getUniqueId().equals(viewer.getUniqueId())
                    && !gridPlayers.contains(other.getUniqueId())) {
                wanted.add(other.getUniqueId());
            }
        }
        for (UUID id : List.copyOf(hidden)) {
            if (!wanted.contains(id)) {
                hidden.remove(id);
                sendListed(viewer, id, true);
            }
        }
        for (UUID id : wanted) {
            if (hidden.add(id)) {
                sendListed(viewer, id, false);
            }
        }
    }

    /** Re-lists every entry this layout hid from {@code viewer}. */
    private void restoreListing(Player viewer) {
        Set<UUID> hidden = unlisted.remove(viewer.getUniqueId());
        if (hidden == null || hidden.isEmpty()) {
            return;
        }
        for (UUID id : Set.copyOf(hidden)) {
            sendListed(viewer, id, true);
        }
    }

    private void sendListed(Player viewer, UUID id, boolean listed) {
        try {
            TabEntryPackets.setListed(viewer, id, listed);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void removeAllPads(Player viewer, Map<UUID, PadState> sent) {
        if (sent.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(sent.keySet());
        sent.clear();
        if (!TabEntryPackets.available()) {
            return;
        }
        try {
            TabEntryPackets.remove(viewer, ids);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void fail(Throwable error) {
        padsBroken = true;
        log(Level.WARNING, "TAB fight layout: the filler/header packets were rejected — column"
                + " headers and blank rows are off for this boot (team order still applies).", error);
    }

    private void logLayoutOnce(Grid grid, int fillers) {
        if (layoutLogged || plugin == null) {
            return;
        }
        layoutLogged = true;
        int columns = grid.rows().size() / TabFightLayout.SLOTS_PER_COLUMN;
        plugin.getLogger().info("TAB fight layout active: " + columns + " columns x "
                + TabFightLayout.SLOTS_PER_COLUMN + " rows, " + fillers
                + " header/blank rows sent as player-info entries.");
    }

    private void log(Level level, String message, Throwable error) {
        if (plugin == null) {
            return;
        }
        if (error == null) {
            plugin.getLogger().log(level, message);
        } else {
            plugin.getLogger().log(level, message, error);
        }
    }

    /** True while a match is being fought or finished (the layout only exists then). */
    private static boolean running(MatchSession session) {
        return session != null
                && (session.state() == MatchState.ACTIVE || session.state() == MatchState.ENDING);
    }

    /**
     * Builds the whole grid of one match as a flat row list in client order (row {@code i}
     * occupies slot {@code i + 1}). Header, spacer and blank rows carry no player and are sent
     * to each viewer as filler entries; everything else is a real participant or spectator.
     */
    Grid buildGrid(MatchSession session, Collection<? extends Player> online) {
        List<Member> members = new ArrayList<>();
        for (Player player : online) {
            UUID id = player.getUniqueId();
            if (session.isParticipant(id)) {
                TeamColor color = session.teamColor(id);
                boolean fallen = session.isEliminated(id) || player.getGameMode() == GameMode.SPECTATOR;
                members.add(fallen
                        ? Member.ofFallen(id, player.getName(), color)
                        : Member.ofFighter(id, player.getName(), color));
            } else if (player.getGameMode() == GameMode.SPECTATOR) {
                members.add(Member.ofSpectator(id, player.getName()));
            }
        }
        boolean teamMatch = session.isTeamMatch();
        TabFightLayout.Scheme scheme = TabFightLayout.schemeFor(teamMatch, aliveSizes(members));
        List<GridRow> rows = new ArrayList<>();
        for (ColumnPlan column : planGrid(teamMatch, members)) {
            rows.add(new GridRow(null, column.header()));
            rows.add(new GridRow(null, BLANK));
            for (Member member : column.rows()) {
                rows.add(new GridRow(Bukkit.getPlayer(member.id()), rowDisplay(member, scheme)));
            }
            int fillers = TabFightLayout.padCount(column.rows().size());
            for (int i = 0; i < fillers; i++) {
                rows.add(new GridRow(null, BLANK));
            }
        }
        return new Grid(rows);
    }

    /**
     * Pure grid plan: which columns exist in which order and which real players sit in them.
     * A duel merges both fighters into one column, a party fight renders one column per team in
     * canonical battle order, fallen players stay in their team column, and the spectator column
     * comes last.
     */
    static List<ColumnPlan> planGrid(boolean teamMatch, List<Member> members) {
        List<Member> alive = new ArrayList<>();
        List<Member> fallen = new ArrayList<>();
        List<Member> spectators = new ArrayList<>();
        for (Member member : members) {
            if (member.spectator()) {
                spectators.add(member);
            } else if (member.fallen()) {
                fallen.add(member);
            } else {
                alive.add(member);
            }
        }
        sortByName(alive);
        sortByName(fallen);
        sortByName(spectators);
        List<ColumnPlan> columns = new ArrayList<>();
        if (TabFightLayout.schemeFor(teamMatch, aliveSizes(members)) == TabFightLayout.Scheme.DUEL) {
            List<Member> rows = new ArrayList<>();
            appendTeams(rows, alive);
            appendTeams(rows, fallen);
            if (!rows.isEmpty()) {
                columns.add(new ColumnPlan(HEADER_COMBAT, rows));
            }
        } else {
            for (TeamColor color : TeamColor.values()) {
                List<Member> rows = new ArrayList<>();
                appendTeam(rows, alive, color);
                appendTeam(rows, fallen, color);
                if (!rows.isEmpty()) {
                    columns.add(new ColumnPlan(teamHeader(color), rows));
                }
            }
        }
        if (!spectators.isEmpty()) {
            columns.add(new ColumnPlan(HEADER_SPECTATING, spectators));
        }
        return columns;
    }

    /** Display text of one row: team-coloured in a duel, "Name ●" in a party fight. */
    static Component rowDisplay(Member member, TabFightLayout.Scheme scheme) {
        if (member.spectator()) {
            return Component.text(member.name(), SPECTATOR_COLOR);
        }
        TeamColor color = member.color();
        if (member.fallen()) {
            return Component.text(member.name(), DEATH_COLOR)
                    .append(Component.text(" "))
                    .append(Component.text("●", fadedTeamColor(color)))
                    .append(Component.text(" - Death", DEATH_COLOR));
        }
        if (scheme == TabFightLayout.Scheme.DUEL) {
            return Component.text(member.name(), teamColor(color));
        }
        return Component.text(member.name(), NamedTextColor.WHITE)
                .append(Component.text(" "))
                .append(Component.text("●", teamColor(color)));
    }

    /** Column header of the sample: "● Red Team" in the team's own colour. */
    static Component teamHeader(TeamColor color) {
        return Component.text("● " + teamLabel(color), teamColor(color));
    }

    /** "Red Team" / "Blue Team" — the sample never shows the bare enum name. */
    static String teamLabel(TeamColor color) {
        String lower = color.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1) + " Team";
    }

    private static TextColor teamColor(TeamColor color) {
        return color == null ? NamedTextColor.WHITE : TextColor.color(color.leatherColor().asRGB());
    }

    /** Faded team colour of the death row's dot (the sample greys the whole row). */
    private static TextColor fadedTeamColor(TeamColor color) {
        if (color == null) {
            return DEATH_COLOR;
        }
        int rgb = color.leatherColor().asRGB();
        int red = fade((rgb >> 16) & 0xFF);
        int green = fade((rgb >> 8) & 0xFF);
        int blue = fade(rgb & 0xFF);
        return TextColor.color((red << 16) | (green << 8) | blue);
    }

    private static int fade(int channel) {
        return channel + (255 - channel) * 55 / 100;
    }

    private static void appendTeams(List<Member> target, List<Member> source) {
        for (TeamColor color : TeamColor.values()) {
            appendTeam(target, source, color);
        }
    }

    private static void appendTeam(List<Member> target, List<Member> source, TeamColor color) {
        for (Member member : source) {
            if (member.color() == color) {
                target.add(member);
            }
        }
    }

    private static List<Integer> aliveSizes(List<Member> members) {
        List<Integer> sizes = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            int count = 0;
            for (Member member : members) {
                if (!member.spectator() && !member.fallen() && member.color() == color) {
                    count++;
                }
            }
            sizes.add(count);
        }
        return sizes;
    }

    private static void sortByName(List<Member> members) {
        members.sort(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER));
    }

    /**
     * Applies one tablist entry. Ordering is only written when it changed (this runs on the
     * periodic scoreboard refresh and must not re-broadcast identical player-info updates
     * every cycle). The list name is taken over once on entering the layout, and re-synced
     * when the styled content changes (e.g. a fighter falling to the "- Death" row).
     */
    private void applyListEntry(Player player, boolean ordering, int order, Component display) {
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
            player.playerListName(display);
            layoutNames.put(id, display);
        } else if (!Objects.equals(layoutNames.get(id), display)) {
            player.playerListName(display);
            layoutNames.put(id, display);
        }
    }

    /** Restores vanilla ordering, the styled list name and removes fillers for one player. */
    public void clear(Player player) {
        restoreListing(player);
        Map<UUID, PadState> sent = sentPads.remove(player.getUniqueId());
        if (sent != null && !sent.isEmpty() && TabEntryPackets.available()) {
            try {
                TabEntryPackets.remove(player, List.copyOf(sent.keySet()));
            } catch (Throwable t) {
                fail(t);
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

    /** One online player the grid knows about. */
    record Member(UUID id, String name, TeamColor color, boolean fallen, boolean spectator) {

        static Member ofFighter(UUID id, String name, TeamColor color) {
            return new Member(id, name, color, false, false);
        }

        static Member ofFallen(UUID id, String name, TeamColor color) {
            return new Member(id, name, color, true, false);
        }

        static Member ofSpectator(UUID id, String name) {
            return new Member(id, name, null, false, true);
        }
    }

    /** One planned column: header text and the real players below the header/spacer rows. */
    record ColumnPlan(Component header, List<Member> rows) {
    }

    /** One flattened grid row: {@code player == null} means a filler entry. */
    record GridRow(Player player, Component display) {
    }

    /** The whole grid of one match, in client order. */
    record Grid(List<GridRow> rows) {
    }

    /** What a filler entry currently shows: list order + display text. */
    private record PadState(int order, Component display) {
    }
}
