package com.rumilance.practice.team;

import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Owner-invite style team manager. Teams can be public (join without an invite) or private
 * (invite only); the owner triggers a match split into RED and BLUE, either by manually
 * assigning members or letting the service balance-shuffle them automatically. No queue.
 */
public final class TeamService {

    private static final int MAX_TEAM_SIZE = 30;
    private static final int MIN_TEAM_SIZE = 2;
    /** Hard cap per battle side (matches {@link com.rumilance.practice.session.MatchSession#MAX_SIDE_SIZE}). */
    private static final int MAX_SIDE_SIZE = 15;
    private static final Duration INVITE_TTL = Duration.ofSeconds(60);
    private static final Duration INVITE_COOLDOWN = Duration.ofSeconds(30);

    private final Plugin plugin;
    private final MatchService matchService;
    private final com.rumilance.practice.locale.MessageService messageService;
    private volatile PartyHotbar partyHotbar;
    private volatile java.util.function.BooleanSupplier hasPartyMaps = () -> false;

    private final Map<UUID, Team> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Team> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Instant>> lastInviteAt = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> recentLeaver = new ConcurrentHashMap<>();

    private record Invite(UUID teamId, Instant expiresAt) {
        boolean expired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public enum Result {
        OK, ALREADY_IN_TEAM, NOT_OWNER, NOT_IN_TEAM, TEAM_FULL, TARGET_OFFLINE,
        TARGET_IN_TEAM, NO_INVITE, INVITE_EXPIRED, INVALID_NAME, TOO_SMALL,
        UNBALANCED, OWNER_CANNOT_LEAVE, INVALID_SIDE, KIT_NOT_FOUND, NO_ARENA, COOLDOWN,
        MEMBER_BUSY
    }

    private volatile com.rumilance.practice.session.PlayerStateManager stateManager;
    /** Details of the member that last failed the availability check, for error messages. */
    private volatile String lastBusyName;
    private volatile String lastBusyStateKey;
    private volatile boolean lastBusyOffline;

    public void setStateManager(com.rumilance.practice.session.PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    /** Localized / player-facing explanation for a non-{@link Result#OK} result. */
    public String errorMessage(Player player, Result result) {
        return errorMessage(player, result, null);
    }

    public String errorMessage(Player player, Result result, UUID cooldownTarget) {
        if (result == null || result == Result.OK) {
            return "";
        }
        if (messageService != null) {
            if (result == Result.COOLDOWN) {
                int secs = remainingInviteCooldownSeconds(player.getUniqueId(), cooldownTarget);
                return messageService.raw(player, "party.err-cooldown")
                        .replace("<secs>", String.valueOf(Math.max(1, secs)));
            }
            if (result == Result.MEMBER_BUSY) {
                if (lastBusyOffline) {
                    return messageService.raw(player, "party.err-member-offline")
                            .replace("<player>", String.valueOf(lastBusyName));
                }
                String state = messageService.raw(player,
                        lastBusyStateKey == null ? "menu.state-fighting" : lastBusyStateKey);
                return messageService.raw(player, "party.err-member-busy")
                        .replace("<player>", String.valueOf(lastBusyName))
                        .replace("<state>", state);
            }
            String key = switch (result) {
                case ALREADY_IN_TEAM -> "party.err-already";
                case NOT_OWNER -> "party.err-not-owner";
                case NOT_IN_TEAM -> "party.err-not-in";
                case TEAM_FULL -> "party.err-full";
                case TARGET_OFFLINE -> "party.err-offline";
                case TARGET_IN_TEAM -> "party.err-target-in";
                case NO_INVITE -> "party.err-no-invite";
                case INVITE_EXPIRED -> "party.err-invite-expired";
                case INVALID_NAME -> "party.err-invalid-name";
                case TOO_SMALL -> "party.err-too-small";
                case UNBALANCED -> "party.err-unbalanced";
                case OWNER_CANNOT_LEAVE -> "party.err-owner-kick";
                case INVALID_SIDE -> "party.err-invalid-side";
                case KIT_NOT_FOUND -> "party.err-kit";
                case NO_ARENA -> "party.err-arena";
                case COOLDOWN, OK, MEMBER_BUSY -> "";
            };
            if (key.isEmpty()) {
                return "";
            }
            return messageService.raw(player, key);
        }
        return switch (result) {
            case ALREADY_IN_TEAM -> "Already in a team.";
            case NOT_OWNER -> "Only the team owner can do that.";
            case NOT_IN_TEAM -> "You are not in a team.";
            case TEAM_FULL -> "That team is full.";
            case TARGET_OFFLINE -> "That player is offline.";
            case TARGET_IN_TEAM -> "That player is already in a team.";
            case NO_INVITE -> "No pending invite.";
            case INVITE_EXPIRED -> "That invite expired.";
            case INVALID_NAME -> "Invalid team name.";
            case TOO_SMALL -> "Not enough players.";
            case UNBALANCED -> "Teams are unbalanced.";
            case OWNER_CANNOT_LEAVE -> "The owner cannot leave; disband instead.";
            case INVALID_SIDE -> "Invalid side.";
            case KIT_NOT_FOUND -> "Kit not found.";
            case NO_ARENA -> "No arena available.";
            case COOLDOWN -> {
                int secs = remainingInviteCooldownSeconds(player.getUniqueId(), cooldownTarget);
                yield "Wait " + Math.max(1, secs) + "s before inviting that player again.";
            }
            case MEMBER_BUSY -> lastBusyOffline
                    ? lastBusyName + " is offline — everyone must be online to start."
                    : lastBusyName + " is busy right now — everyone must be free in the lobby.";
            case OK -> "";
        };
    }

    public TeamService(Plugin plugin, MatchService matchService) {
        this(plugin, matchService, null);
    }

    public TeamService(Plugin plugin, MatchService matchService,
                       com.rumilance.practice.locale.MessageService messageService) {
        this.plugin = plugin;
        this.matchService = matchService;
        this.messageService = messageService;
    }

    public void setPartyHotbar(PartyHotbar partyHotbar) {
        this.partyHotbar = partyHotbar;
    }

    public void setHasPartyMaps(java.util.function.BooleanSupplier hasPartyMaps) {
        this.hasPartyMaps = hasPartyMaps == null ? () -> false : hasPartyMaps;
    }

    private void applyHotbar(Player player, Team team) {
        PartyHotbar bar = partyHotbar;
        if (bar == null || player == null || team == null) {
            return;
        }
        bar.give(player, team.isOwner(player.getUniqueId()), hasPartyMaps.getAsBoolean(),
                team.friendlyFire());
    }

    private void restoreLobby(Player player) {
        PartyHotbar bar = partyHotbar;
        if (bar != null && player != null) {
            bar.restoreLobby(player);
        }
    }

    private void refreshAllHotbars(Team team) {
        if (team == null || partyHotbar == null) {
            return;
        }
        for (UUID id : team.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                applyHotbar(p, team);
            }
        }
    }

    // ---- create / disband ----

    public Result create(Player owner, String name, boolean isPublic) {
        if (byMember.containsKey(owner.getUniqueId())) {
            return Result.ALREADY_IN_TEAM;
        }
        if (name != null && name.length() > 24) {
            return Result.INVALID_NAME;
        }
        Team team = new Team(UUID.randomUUID(), owner.getUniqueId(), name, isPublic);
        byId.put(team.id(), team);
        byMember.put(owner.getUniqueId(), team);
        applyHotbar(owner, team);
        owner.sendMessage(Component.text("Team '" + team.name() + "' created ("
                + (isPublic ? "public" : "private") + ").", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        return Result.OK;
    }

    public Result disband(Player player) {
        Team team = byMember.get(player.getUniqueId());
        if (team == null) {
            return Result.NOT_IN_TEAM;
        }
        if (!team.isOwner(player.getUniqueId())) {
            return Result.NOT_OWNER;
        }
        broadcast(team, Component.text("Team '" + team.name() + "' disbanded.", NamedTextColor.RED));
        for (UUID member : team.members()) {
            byMember.remove(member);
            invites.entrySet().removeIf(e -> e.getValue().teamId().equals(team.id()));
            Player m = Bukkit.getPlayer(member);
            if (m != null) {
                restoreLobby(m);
            }
        }
        byId.remove(team.id());
        return Result.OK;
    }

    // ---- membership ----

    public Result invite(Player owner, String targetName) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        if (team.size() >= MAX_TEAM_SIZE) return Result.TEAM_FULL;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return Result.TARGET_OFFLINE;
        if (byMember.containsKey(target.getUniqueId())) return Result.TARGET_IN_TEAM;
        if (remainingInviteCooldownSeconds(owner.getUniqueId(), target.getUniqueId()) > 0) {
            return Result.COOLDOWN;
        }
        team.invite(target.getUniqueId());
        invites.put(target.getUniqueId(), new Invite(team.id(), Instant.now().plus(INVITE_TTL)));
        lastInviteAt.computeIfAbsent(owner.getUniqueId(), id -> new ConcurrentHashMap<>())
                .put(target.getUniqueId(), Instant.now());
        owner.sendMessage(Component.text("Invited " + target.getName() + " to '" + team.name() + "'.", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        target.sendMessage(Component.text(owner.getName() + " invited you to team '" + team.name() + "'  ",
                        NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .append(com.rumilance.practice.chat.ChatButtons.row(
                        com.rumilance.practice.chat.ChatButtons.accept("Accept",
                                "/team join " + team.name(), "Join " + team.name()),
                        com.rumilance.practice.chat.ChatButtons.decline("Decline",
                                "/team decline", "Decline this invite"))));
        return Result.OK;
    }

    private int remainingInviteCooldownSeconds(UUID owner, UUID target) {
        if (owner == null || target == null) {
            return 0;
        }
        Map<UUID, Instant> byTarget = lastInviteAt.get(owner);
        if (byTarget == null) {
            return 0;
        }
        Instant last = byTarget.get(target);
        if (last == null) {
            return 0;
        }
        long leftMs = Duration.between(Instant.now(), last.plus(INVITE_COOLDOWN)).toMillis();
        if (leftMs <= 0) {
            return 0;
        }
        return (int) ((leftMs + 999L) / 1000L);
    }

    public Result join(Player player, String teamName) {
        if (byMember.containsKey(player.getUniqueId())) {
            return Result.ALREADY_IN_TEAM;
        }
        Team team = findByName(teamName).orElse(null);
        if (team == null) {
            player.sendMessage(Component.text("No public team named '" + teamName + "'.", NamedTextColor.RED));
            return Result.NO_INVITE;
        }
        if (!team.isPublic()) {
            Invite invite = invites.get(player.getUniqueId());
            if (invite == null || !invite.teamId().equals(team.id()) || invite.expired(Instant.now())) {
                return Result.NO_INVITE;
            }
        }
        if (team.size() >= MAX_TEAM_SIZE) return Result.TEAM_FULL;
        team.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), team);
        invites.remove(player.getUniqueId());
        applyHotbar(player, team);
        broadcast(team, Component.text(player.getName() + " joined the team.", NamedTextColor.AQUA));
        return Result.OK;
    }

    /** Declines (clears) the player's pending invite, if any. */
    public Result decline(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null) {
            return Result.NO_INVITE;
        }
        Team team = byId.get(invite.teamId());
        if (team != null) {
            team.revokeInvite(player.getUniqueId());
            Player owner = Bukkit.getPlayer(team.owner());
            if (owner != null) {
                owner.sendMessage(Component.text(player.getName() + " declined the invite.", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        player.sendMessage(Component.text("Invite declined.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return Result.OK;
    }

    public Result leave(Player player) {
        Team team = byMember.get(player.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (team.isOwner(player.getUniqueId())) {
            return disband(player);
        }
        team.remove(player.getUniqueId());
        byMember.remove(player.getUniqueId());
        recentLeaver.put(player.getUniqueId(), Instant.now());
        restoreLobby(player);
        broadcast(team, Component.text(player.getName() + " left the team.", NamedTextColor.YELLOW));
        return Result.OK;
    }

    public Result kick(Player owner, String targetName) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return Result.TARGET_OFFLINE;
        if (!team.contains(target.getUniqueId())) return Result.NOT_IN_TEAM;
        if (team.isOwner(target.getUniqueId())) return Result.OWNER_CANNOT_LEAVE;
        team.remove(target.getUniqueId());
        byMember.remove(target.getUniqueId());
        restoreLobby(target);
        target.sendMessage(Component.text("You were kicked from '" + team.name() + "'.", NamedTextColor.RED));
        broadcast(team, Component.text(target.getName() + " was kicked from the team.", NamedTextColor.RED));
        return Result.OK;
    }

    public Result togglePublic(Player owner) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        team.setPublic(!team.isPublic());
        broadcast(team, Component.text("Team is now " + (team.isPublic() ? "public" : "private") + ".", NamedTextColor.AQUA));
        refreshAllHotbars(team);
        return Result.OK;
    }

    public Result setSelectedArena(Player owner, String arenaName) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        team.setSelectedArena(arenaName);
        broadcast(team, Component.text(
                arenaName == null ? "Party map: Random" : "Party map: " + arenaName,
                NamedTextColor.AQUA));
        return Result.OK;
    }

    // ---- side assignment ----

    public Result assignSide(Player owner, String targetName, String side) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return Result.TARGET_OFFLINE;
        if (!team.contains(target.getUniqueId())) return Result.NOT_IN_TEAM;
        TeamColor color;
        try {
            color = TeamColor.valueOf(side.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.INVALID_SIDE;
        }
        // Enforce the 15-per-side cap (moving within the same side is always fine).
        if (team.sideOf(target.getUniqueId()) != color && team.side(color).size() >= MAX_SIDE_SIZE) {
            return Result.TEAM_FULL;
        }
        team.assignSide(target.getUniqueId(), color);
        broadcast(team, Component.text(owner.getName() + " set " + target.getName() + " to " + color + ".", NamedTextColor.AQUA));
        return Result.OK;
    }

    /** Evenly shuffles every member into RED/BLUE using a stable-ish random split. */
    public Result autoAssign(Player owner) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        if (team.size() < MIN_TEAM_SIZE) return Result.TOO_SMALL;
        List<UUID> shuffled = new ArrayList<>(team.members());
        Collections.shuffle(shuffled);
        team.clearSides();
        for (int i = 0; i < shuffled.size(); i++) {
            team.assignSide(shuffled.get(i), i % 2 == 0 ? TeamColor.RED : TeamColor.BLUE);
        }
        return Result.OK;
    }

    public Result clearSides(Player owner) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        team.clearSides();
        broadcast(team, Component.text("Side assignments cleared.", NamedTextColor.YELLOW));
        return Result.OK;
    }

    public Result toggleFriendlyFire(Player owner) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) {
            return Result.NOT_IN_TEAM;
        }
        if (!team.isOwner(owner.getUniqueId())) {
            return Result.NOT_OWNER;
        }
        team.setFriendlyFire(!team.friendlyFire());
        broadcast(team, Component.text(
                team.friendlyFire() ? "Friendly fire: ON" : "Friendly fire: OFF",
                team.friendlyFire() ? NamedTextColor.RED : NamedTextColor.GREEN));
        refreshAllHotbars(team);
        return Result.OK;
    }

    // ---- start team battle (no queue) ----

    /**
     * Validates that {@code owner} could start a party battle right now (team, ownership,
     * size, side balance and every member free in the lobby), without consuming the chosen
     * kit or map. Used to gate the kit → map selection flow before the player picks an arena.
     */
    public Result preflightStart(Player owner) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        if (team.size() < MIN_TEAM_SIZE) return Result.TOO_SMALL;
        if (!team.isSplitReady()) return Result.UNBALANCED;

        List<UUID> red = new ArrayList<>(team.side(TeamColor.RED));
        List<UUID> blue = new ArrayList<>(team.side(TeamColor.BLUE));
        if (red.isEmpty() || blue.isEmpty()) return Result.UNBALANCED;
        if (red.size() > MAX_SIDE_SIZE || blue.size() > MAX_SIDE_SIZE) return Result.UNBALANCED;
        Result availability = checkMembersAvailable(team);
        if (availability != Result.OK) {
            return availability;
        }
        return Result.OK;
    }

    /**
     * Every member must be online and free in the lobby. Members sitting in a queue, an FFA
     * arena, a match or the spectate mode would collide with the team teleport and leave
     * inconsistent state — starting on top of them is refused with a named reason.
     */
    private Result checkMembersAvailable(Team team) {
        for (UUID memberId : team.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                lastBusyName = "?";
                lastBusyStateKey = null;
                lastBusyOffline = true;
                return Result.MEMBER_BUSY;
            }
            com.rumilance.practice.state.PlayerState state = stateManager == null
                    ? com.rumilance.practice.state.PlayerState.LOBBY
                    : stateManager.getState(memberId);
            boolean free = state == com.rumilance.practice.state.PlayerState.LOBBY
                    || state == com.rumilance.practice.state.PlayerState.OPENING_GUI
                    || state == com.rumilance.practice.state.PlayerState.IDLE;
            if (!free) {
                lastBusyName = member.getName();
                lastBusyStateKey = stateKey(state);
                lastBusyOffline = false;
                return Result.MEMBER_BUSY;
            }
        }
        return Result.OK;
    }

    private static String stateKey(com.rumilance.practice.state.PlayerState state) {
        return switch (state) {
            case QUEUED_RANKED -> "menu.state-ranked-queue";
            case QUEUED_UNRANKED -> "menu.state-unranked-queue";
            case FIGHTING, PREPARING_MATCH, COUNTDOWN, ENDING -> "menu.state-fighting";
            case SPECTATING -> "menu.state-spectating";
            case FFA -> "menu.state-ffa";
            case EDITING_KIT -> "menu.state-editing";
            case REQUESTING_DUEL -> "menu.state-dueling";
            case PRACTICE_WAIT, PRACTICE_ACTIVE -> "menu.state-fighting";
            default -> "menu.state-fighting";
        };
    }

    public Result start(Player owner, String kitId) {
        Team team = byMember.get(owner.getUniqueId());
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.isOwner(owner.getUniqueId())) return Result.NOT_OWNER;
        if (team.size() < MIN_TEAM_SIZE) return Result.TOO_SMALL;
        if (!team.isSplitReady()) return Result.UNBALANCED;

        List<UUID> red = new ArrayList<>(team.side(TeamColor.RED));
        List<UUID> blue = new ArrayList<>(team.side(TeamColor.BLUE));
        if (red.isEmpty() || blue.isEmpty()) return Result.UNBALANCED;
        if (red.size() > MAX_SIDE_SIZE || blue.size() > MAX_SIDE_SIZE) return Result.UNBALANCED;

        // Never start a party fight while ANY member is committed elsewhere: a solo duel, queue
        // battle, FFA, spectating, or an active/eliminated match would conflict with the team
        // teleport and leave state inconsistent (a personal match could have a party fight
        // start on top of it). Require everyone to be free in the lobby.
        Result availability = checkMembersAvailable(team);
        if (availability != Result.OK) {
            return availability;
        }
        for (UUID memberId : team.members()) {
            // Belt-and-braces: the state machine above should already cover matches, but a
            // session in a transient phase must never get a party fight stacked on top.
            if (matchService.isBusyForSoloDuel(memberId)
                    || matchService.registry().byPlayer(memberId).isPresent()) {
                owner.sendMessage(Component.text(
                        "A party member is currently in a fight or match. Wait until everyone is in the lobby.",
                        NamedTextColor.RED));
                return Result.UNBALANCED;
            }
        }

        String arenaName = team.selectedArena();
        boolean ff = team.friendlyFire();
        Bukkit.getScheduler().runTask(plugin, () ->
                matchService.startTeamMatch(red, blue, kitId, MatchMode.TEAM, 1, arenaName, ff));
        broadcast(team, Component.text("Team battle starting!", NamedTextColor.GOLD));
        return Result.OK;
    }

    // ---- queries ----

    public Optional<Team> teamOf(UUID player) {
        return Optional.ofNullable(byMember.get(player));
    }

    public List<Team> publicTeams() {
        return byId.values().stream().filter(Team::isPublic).collect(Collectors.toList());
    }

    public Optional<Team> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Team> findByName(String name) {
        if (name == null) return Optional.empty();
        String lower = name.toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .filter(t -> t.name().toLowerCase(Locale.ROOT).equals(lower))
                .findFirst();
    }

    public String info(Team team) {
        StringBuilder sb = new StringBuilder();
        sb.append(team.isPublic() ? "Public" : "Private").append(" team '").append(team.name()).append("' (")
                .append(team.size()).append("/").append(MAX_TEAM_SIZE).append(")");
        if (!team.sideAssignment().isEmpty()) {
            sb.append("  RED=").append(team.side(TeamColor.RED).size())
                    .append(" BLUE=").append(team.side(TeamColor.BLUE).size());
        }
        return sb.toString();
    }

    private void broadcast(Team team, Component message) {
        for (UUID member : team.members()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(message.decoration(TextDecoration.ITALIC, false));
            }
        }
    }

    /** Called by {@link com.rumilance.practice.team.TeamListener} on quit. */
    public void handleQuit(UUID player) {
        Team team = byMember.get(player);
        if (team == null) {
            invites.remove(player);
            return;
        }
        if (team.isOwner(player) && isInActiveTeamMatch(player)) {
            team.remove(player);
            byMember.remove(player);
            return;
        }
        if (team.isOwner(player)) {
            for (UUID member : team.members()) {
                byMember.remove(member);
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
                    restoreLobby(p);
                    p.sendMessage(Component.text("Team disbanded (owner left).", NamedTextColor.RED));
                }
            }
            byId.remove(team.id());
        } else {
            team.remove(player);
            byMember.remove(player);
            for (UUID member : team.members()) {
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
                    p.sendMessage(Component.text("A player left the team.", NamedTextColor.YELLOW));
                }
            }
        }
        invites.remove(player);
    }

    private boolean isInActiveTeamMatch(UUID player) {
        return matchService.registry().byPlayer(player)
                .filter(com.rumilance.practice.session.MatchSession::isTeamMatch)
                .filter(s -> {
                    com.rumilance.practice.state.MatchState st = s.state();
                    return st != com.rumilance.practice.state.MatchState.CLOSED
                            && st != com.rumilance.practice.state.MatchState.FAILED;
                })
                .isPresent();
    }
}
