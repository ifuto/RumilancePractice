package com.rumilance.practice.tournament;

import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamConfig;
import com.rumilance.practice.team.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Party color-team tournament — a single-elimination bracket over the party's assigned
 * sides, run on top of the existing team-match machinery. Every card is a normal 2-roster
 * {@link MatchService#startTeamMatch} call; the bracket lives entirely here.
 *
 * <p>Deliberately kept small:</p>
 * <ul>
 *   <li>Entrants are the party's non-empty color sides (snapshot at start). Members without a
 *       side assignment spectate only.</li>
 *   <li>Kit and arena come from the party's existing selection; per-team configs (health /
 *       size / kit overrides) ride along per card, re-mapped to a single card's RED/BLUE
 *       roster slots.</li>
 *   <li>Any entrant count works: cards are adjacent seeds from a shuffled order plus a
 *       trailing bye, so odd counts and non-powers-of-two need no special bracket.</li>
 *   <li>{@code parallel} issues a whole round at once; {@code sequential} runs one card at a
 *       time and auto-spectates every waiting member onto the live card — both selected at
 *       start.</li>
 *   <li>A card is only advanced once it has <em>settled</em>: the match ended and its players
 *       were sent back to the lobby — the only moment it is safe to teleport a waiters/entrant
 *       into the next card without racing the one-way SPECTATING→LOBBY state gate.</li>
 * </ul>
 */
public final class TournamentService implements PartyTournamentHook {

    private final Plugin plugin;
    private final TeamService teamService;
    private final MatchService matchService;
    private final SpectatorService spectatorService;
    private final PlayerStateManager stateManager;
    private final LobbyService lobbyService;
    private final SoundService soundService;

    private final Map<String, Running> byTag = new ConcurrentHashMap<>();

    public TournamentService(Plugin plugin, TeamService teamService, MatchService matchService,
                             SpectatorService spectatorService, PlayerStateManager stateManager,
                             LobbyService lobbyService, SoundService soundService) {
        this.plugin = plugin;
        this.teamService = teamService;
        this.matchService = matchService;
        this.spectatorService = spectatorService;
        this.stateManager = stateManager;
        this.lobbyService = lobbyService;
        this.soundService = soundService;
    }

    // ---- command surface ---------------------------------------------------

    /** True when the owner's party is already running a tournament. */
    public boolean isRunning(Player owner) {
        Team team = teamService.teamOf(owner.getUniqueId()).orElse(null);
        return team != null && byTag.containsKey(baseTag(team.id()));
    }

    /** Number of color teams that would enter a tournament for {@code owner}'s party right now. */
    public int entrantCount(Player owner) {
        Team team = teamService.teamOf(owner.getUniqueId()).orElse(null);
        if (team == null || team.kind() != com.rumilance.practice.team.GroupKind.PARTY) {
            return 0;
        }
        return nonEmptySides(team).size();
    }

    /**
     * @return a short human-readable reason a tournament cannot start for {@code owner}, or
     *         {@code null} when it can. Mirrors {@link #start} without side effects, so the
     *         tournament GUI can show the exact blocker before the owner clicks start.
     */
    public String readinessError(Player owner) {
        Team team = teamService.teamOf(owner.getUniqueId()).orElse(null);
        if (team == null) {
            return "You are not in a team.";
        }
        if (!team.isOwner(owner.getUniqueId())) {
            return "Only the team owner can start a tournament.";
        }
        if (team.kind() != com.rumilance.practice.team.GroupKind.PARTY) {
            return "Tournaments run over parties, not internal teams.";
        }
        if (nonEmptySides(team).size() < 2) {
            return "Assign members to at least two color sides first.";
        }
        for (UUID memberId : team.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !freeInLobby(memberId)) {
                return "Every member must be online and in the lobby.";
            }
        }
        return null;
    }

    /** Starts a tournament for the owner's party. Kit name validated by the caller. */
    public boolean start(Player owner, String kitId, String modeArg) {
        Team team = teamService.teamOf(owner.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(owner.getUniqueId())) {
            owner.sendMessage(msg("Only the team owner can start a tournament.", NamedTextColor.RED));
            return false;
        }
        if (team.kind() != com.rumilance.practice.team.GroupKind.PARTY) {
            owner.sendMessage(msg("Tournaments run over parties, not internal teams.", NamedTextColor.RED));
            return false;
        }
        if (byTag.containsKey(baseTag(team.id()))) {
            owner.sendMessage(msg("A tournament is already running for this team.", NamedTextColor.RED));
            return false;
        }
        List<TeamColor> colors = nonEmptySides(team);
        if (colors.size() < 2) {
            owner.sendMessage(msg("Assign members to at least two color sides first.",
                    NamedTextColor.RED));
            return false;
        }
        boolean sequential;
        if (modeArg == null || modeArg.isBlank()) {
            sequential = false;
        } else {
            switch (modeArg.toLowerCase(Locale.ROOT)) {
                case "parallel", "simultaneous" -> sequential = false;
                case "sequential", "series", "one-by-one" -> sequential = true;
                default -> {
                    owner.sendMessage(msg("Usage: /tournament start <kit> [parallel|sequential]",
                            NamedTextColor.RED));
                    return false;
                }
            }
        }
        for (UUID memberId : team.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !freeInLobby(memberId)) {
                owner.sendMessage(msg("Every member must be online and in the lobby.",
                        NamedTextColor.RED));
                return false;
            }
        }
        Running running = new Running(team, kitId, sequential);
        byTag.put(running.tag, running);
        running.kickOff();
        return true;
    }

    /** Prints the bracket state if the team has a tournament running. */
    public boolean info(Player player, Team team) {
        Running running = byTag.get(baseTag(team.id()));
        if (running == null) {
            player.sendMessage(msg("No tournament is running for this team.", NamedTextColor.GRAY));
            return false;
        }
        player.sendMessage(Component.text("Tournament — Round " + running.activeRound
                        + "/" + (running.rounds.size())
                        + (running.sequential ? " (sequential)" : " (parallel)"),
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        if (!running.rounds.isEmpty() && running.activeRound >= 1
                && running.activeRound <= running.rounds.size()) {
            for (Card card : running.rounds.get(running.activeRound - 1)) {
                player.sendMessage(cardLine(card));
            }
        }
        player.sendMessage(msg("Champion: " + (running.champion == null ? "—" : running.champion.label()),
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        return true;
    }

    /** Cancels the owner's tournament and sends every member home. */
    public boolean cancel(Player owner) {
        Team team = teamService.teamOf(owner.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(owner.getUniqueId())) {
            owner.sendMessage(msg("Only the team owner can cancel a tournament.", NamedTextColor.RED));
            return false;
        }
        Running running = byTag.remove(baseTag(team.id()));
        if (running == null) {
            owner.sendMessage(msg("No tournament is running for this team.", NamedTextColor.GRAY));
            return false;
        }
        running.shutdown("Tournament cancelled.");
        return true;
    }

    // ---- hook (MatchService) ----------------------------------------------

    @Override
    public void completeMatch(String tag, UUID matchId, TeamColor winner, boolean draw) {
        Running running = byTag.get(baseOf(tag));
        if (running == null) {
            return;
        }
        Card card = running.cardsByTag.get(tag);
        if (card == null || card.decided) {
            return;
        }
        if (draw || winner == null) {
            card.winner = null; // mutual elimination
        } else {
            // Inside a card the rosters are RED/BLUE; map back to the party's real colors.
            card.winner = (winner == TeamColor.RED) ? card.a.color : card.b.color;
        }
        card.decided = true;
    }

    @Override
    public void settled(String tag, UUID matchId) {
        Running running = byTag.get(baseOf(tag));
        if (running == null) {
            return;
        }
        running.settle(tag);
    }

    @Override
    public void matchFailed(String tag, Set<UUID> players) {
        Running running = byTag.get(baseOf(tag));
        if (running == null) {
            return;
        }
        Card card = running.cardsByTag.get(tag);
        if (card == null || card.decided) {
            return;
        }
        card.decided = true;
        card.issued = true;
        // Defer one tick: a failMatch runs synchronously inside startTeamMatch, so any waiting
        // spectators are still SPECTATING (clearMatch has not run yet). Advancing here would
        // teleport them into the next card through an illegal state transition and re-fail it.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (running.over) {
                return;
            }
            card.winner = null;
            running.broadcast(msg("A match failed to start — " + colorText(card.a.color)
                    + " vs " + colorText(card.b.color) + " is void.", NamedTextColor.RED));
            running.applyCard(card);
        });
    }

    // ---- bracket internals -------------------------------------------------

    private static final class Side {
        final TeamColor color;
        final List<UUID> roster;
        Side(TeamColor color, List<UUID> roster) {
            this.color = color;
            this.roster = List.copyOf(roster);
        }
    }

    private static final class Card {
        final String tag;
        final Side a;         // roster slot 0 → RED inside the match
        final Side b;         // roster slot 1 → BLUE; null = acknowledged bye
        TeamColor winner;     // the party color that advances (null = nobody / draw)
        boolean decided;
        boolean issued;
        boolean advanced;
        Card(String tag, Side a, Side b) {
            this.tag = tag;
            this.a = a;
            this.b = b;
        }
    }

    private final class Running {
        final String tag;
        final String kitId;
        final String arenaName;
        final boolean friendlyFire;
        final boolean sequential;
        final Map<TeamColor, TeamConfig> configs;
        final List<List<Card>> rounds = new ArrayList<>();
        final Map<String, Card> cardsByTag = new HashMap<>();
        final List<Side> participants;
        final Set<UUID> everyone = new HashSet<>();
        final List<Side> alive = new ArrayList<>();
        final List<Side> nextAlive = new ArrayList<>();
        int activeRound = 0;
        int remaining = 0;
        TeamColor champion = null;
        boolean over = false;

        Running(Team team, String kitId, boolean sequential) {
            this.tag = baseTag(team.id());
            this.kitId = kitId;
            this.arenaName = selectedArena(team);
            this.friendlyFire = team.friendlyFire();
            this.sequential = sequential;
            this.configs = normalizeConfigs(team);
            List<Side> sides = new ArrayList<>();
            for (TeamColor color : nonEmptySides(team)) {
                Side side = new Side(color, new ArrayList<>(team.side(color)));
                sides.add(side);
                everyone.addAll(side.roster);
            }
            for (UUID memberId : team.members()) {
                if (!everyone.contains(memberId)) {
                    everyone.add(memberId); // spectates only
                }
            }
            this.participants = List.copyOf(sides);
        }

        void kickOff() {
            ArrayList<Side> seeded = new ArrayList<>(participants);
            Collections.shuffle(seeded);
            alive.clear();
            alive.addAll(seeded);
            broadcast(msg("Tournament started! " + (sequential ? "Sequential" : "Parallel")
                    + " bracket, kit: " + kitId, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            startRound();
        }

        void startRound() {
            if (over) {
                return;
            }
            activeRound++;
            List<Card> round = new ArrayList<>();
            nextAlive.clear();
            for (int i = 0; i + 1 < alive.size(); i += 2) {
                Card card = new Card(tag + "/" + activeRound + "/" + (i / 2),
                        alive.get(i), alive.get(i + 1));
                round.add(card);
                cardsByTag.put(card.tag, card);
            }
            if (alive.size() % 2 == 1) {
                nextAlive.add(alive.get(alive.size() - 1)); // trailing bye
            }
            rounds.add(round);
            if (round.isEmpty()) {
                champion = nextAlive.isEmpty() ? null : nextAlive.get(0).color;
                finish();
                return;
            }
            remaining = round.size();
            announceRound(round);
            if (sequential) {
                issueCard(round.get(0));
            } else {
                for (Card card : round) {
                    issueCard(card);
                }
            }
        }

        void settle(String tag) {
            if (over) {
                return;
            }
            Card card = cardsByTag.get(tag);
            if (card == null) {
                return;
            }
            applyCard(card);
        }

        void applyCard(Card card) {
            if (card.advanced || over) {
                return;
            }
            card.advanced = true;
            if (card.winner != null) {
                nextAlive.add(sideOf(card.winner));
                broadcast(msg(colorText(card.winner) + " defeats "
                        + colorText(opponentOf(card, card.winner)) + " — advances!",
                        NamedTextColor.GREEN));
            } else {
                broadcast(msg("Both " + colorText(card.a.color) + " and "
                        + colorText(card.b.color) + " are out (draw).", NamedTextColor.RED));
            }
            remaining--;
            if (remaining > 0) {
                if (sequential) {
                    issueNextInRound();
                }
                return;
            }
            finishRound();
        }

        TeamColor opponentOf(Card card, TeamColor color) {
            return color == card.a.color ? card.b.color : card.a.color;
        }

        void issueNextInRound() {
            for (Card card : rounds.get(activeRound - 1)) {
                if (!card.issued) {
                    issueCard(card);
                    return;
                }
            }
        }

        void finishRound() {
            alive.clear();
            alive.addAll(nextAlive);
            if (alive.size() == 1) {
                champion = alive.get(0).color;
                finish();
                return;
            }
            if (alive.isEmpty()) {
                finish();
                return;
            }
            startRound();
        }

        void issueCard(Card card) {
            if (card.issued || card.advanced || over) {
                return;
            }
            if (card.b == null) {
                // Lone bye (only reachable if the bracket degenerates): that side advances.
                card.winner = card.a.color;
                card.decided = true;
                card.issued = true;
                applyCard(card);
                return;
            }
            List<UUID> a = new ArrayList<>(card.a.roster);
            List<UUID> b = new ArrayList<>(card.b.roster);
            boolean onlineA = allOnline(a);
            boolean onlineB = allOnline(b);
            if (!onlineA || !onlineB) {
                // Offline side auto-forfeits so the bracket keeps moving. Mark issued so the
                // sequential issuer skips past it to the next live card.
                card.winner = onlineA ? card.a.color : (onlineB ? card.b.color : null);
                card.decided = true;
                card.issued = true;
                if (card.winner != null) {
                    broadcast(msg(colorText(card.winner) + " advances — "
                            + colorText(opponentOf(card, card.winner)) + " is missing.",
                            NamedTextColor.GRAY));
                }
                applyCard(card);
                return;
            }
            Map<TeamColor, String> teamKits = new HashMap<>();
            Map<TeamColor, TeamConfig> cardConfigs = new HashMap<>();
            TeamConfig ca = configs.getOrDefault(card.a.color, TeamConfig.defaults());
            if (ca.customKitId() != null) {
                teamKits.put(TeamColor.RED, ca.customKitId());
            }
            if (!ca.isDefault()) {
                cardConfigs.put(TeamColor.RED, ca);
            }
            TeamConfig cb = configs.getOrDefault(card.b.color, TeamConfig.defaults());
            if (cb.customKitId() != null) {
                teamKits.put(TeamColor.BLUE, cb.customKitId());
            }
            if (!cb.isDefault()) {
                cardConfigs.put(TeamColor.BLUE, cb);
            }
            card.issued = true;
            matchService.startTeamMatch(List.of(a, b), kitId, MatchMode.TEAM, 1, arenaName,
                    friendlyFire, Map.of(), null, teamKits, cardConfigs, null, card.tag);
            for (UUID id : a) {
                bubble(id, "You face " + colorText(card.b.color) + "!", NamedTextColor.AQUA);
            }
            for (UUID id : b) {
                bubble(id, "You face " + colorText(card.a.color) + "!", NamedTextColor.AQUA);
            }
            if (sequential) {
                moveWaitersToCard(card);
            }
        }

        void moveWaitersToCard(Card card) {
            Player target = null;
            for (UUID id : card.a.roster) {
                target = Bukkit.getPlayer(id);
                if (target != null) {
                    break;
                }
            }
            if (target == null) {
                for (UUID id : card.b.roster) {
                    target = Bukkit.getPlayer(id);
                    if (target != null) {
                        break;
                    }
                }
            }
            if (target == null) {
                return;
            }
            Set<UUID> playing = new HashSet<>();
            playing.addAll(card.a.roster);
            playing.addAll(card.b.roster);
            for (UUID id : everyone) {
                if (playing.contains(id)) {
                    continue;
                }
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    continue;
                }
                PlayerState st = stateManager.getState(id);
                if (st != PlayerState.LOBBY && st != PlayerState.OPENING_GUI) {
                    continue;
                }
                spectatorService.trySpectate(p, target);
            }
        }

        void announceRound(List<Card> round) {
            List<String> labels = new ArrayList<>();
            for (Card card : round) {
                labels.add(colorText(card.a.color) + " vs " + colorText(card.b.color));
            }
            for (Side side : nextAlive) {
                labels.add(colorText(side.color) + " (bye)");
            }
            broadcast(msg("Round " + activeRound + ": " + String.join("  |  ", labels),
                    NamedTextColor.YELLOW));
        }

        void finish() {
            if (over) {
                return;
            }
            over = true;
            if (champion != null) {
                Side ch = sideOf(champion);
                if (ch != null) {
                    for (UUID id : ch.roster) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            p.showTitle(Title.title(
                                    Component.text("CHAMPION", NamedTextColor.GOLD)
                                            .decorate(TextDecoration.BOLD),
                                    Component.text(colorText(champion) + " wins the tournament!",
                                            NamedTextColor.WHITE),
                                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2),
                                            Duration.ofMillis(300))));
                            soundService.play(p, "match-end-levelup");
                        }
                    }
                }
                broadcast(msg(colorText(champion) + " wins the tournament!", NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD));
            } else {
                broadcast(msg("Tournament ended with no winner.", NamedTextColor.RED));
            }
            shutdown(null);
        }

        void shutdown(String notice) {
            over = true;
            byTag.remove(tag);
            if (notice != null) {
                broadcast(msg(notice, NamedTextColor.GRAY));
            }
            for (UUID id : everyone) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    stateManager.resetToLobby(id);
                    lobbyService.sendToLobby(p);
                }
            }
        }

        void broadcast(Component component) {
            for (UUID id : everyone) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage(component);
                }
            }
        }

        Side sideOf(TeamColor color) {
            for (Side side : participants) {
                if (side.color == color) {
                    return side;
                }
            }
            return null;
        }
    }

    // ---- helpers -----------------------------------------------------------

    private void bubble(UUID id, String text, NamedTextColor color) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(msg(text, color).decoration(TextDecoration.ITALIC, false));
        }
    }

    private Component cardLine(Card card) {
        String a = colorText(card.a.color);
        String b = card.b == null ? "BYE" : colorText(card.b.color);
        Component base = Component.text(a + " vs " + b, NamedTextColor.WHITE);
        if (card.decided) {
            base = base.append(Component.text(card.winner == null
                            ? "  ->  (out)" : "  ->  " + colorText(card.winner),
                    NamedTextColor.GREEN));
        }
        return base.decoration(TextDecoration.ITALIC, false);
    }

    private static String colorText(TeamColor color) {
        return color == null ? "BYE" : color.label();
    }

    private static String selectedArena(Team team) {
        String arena = team.selectedArena();
        return arena == null || arena.isBlank() ? null : arena;
    }

    private static List<TeamColor> nonEmptySides(Team team) {
        List<TeamColor> colors = new ArrayList<>();
        for (TeamColor color : team.activeColors()) {
            if (!team.side(color).isEmpty()) {
                colors.add(color);
            }
        }
        return colors;
    }

    private static Map<TeamColor, TeamConfig> normalizeConfigs(Team team) {
        Map<TeamColor, TeamConfig> out = new HashMap<>();
        for (Map.Entry<TeamColor, TeamConfig> entry : team.customConfigs().entrySet()) {
            if (!entry.getValue().isDefault()) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static boolean allOnline(List<UUID> ids) {
        for (UUID id : ids) {
            if (Bukkit.getPlayer(id) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean freeInLobby(UUID id) {
        PlayerState state = stateManager.getState(id);
        return state == PlayerState.LOBBY || state == PlayerState.IDLE
                || state == PlayerState.OPENING_GUI;
    }

    private static String baseTag(UUID partyId) {
        return "pto:" + partyId.toString().replace("-", "");
    }

    /** Card tags are "{@code baseTag}/{round}/{index}"; recover the base for the registry. */
    private static String baseOf(String tag) {
        if (tag == null) {
            return "";
        }
        int slash = tag.indexOf('/');
        return slash < 0 ? tag : tag.substring(0, slash);
    }

    private static Component msg(String text, NamedTextColor color) {
        return Component.text(text, color);
    }
}
