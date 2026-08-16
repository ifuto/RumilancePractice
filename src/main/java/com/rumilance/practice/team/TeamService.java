package com.rumilance.practice.team;

import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.state.ArenaTerrain;
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

    private final Plugin plugin;
    private final MatchService matchService;

    private final Map<UUID, Team> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Team> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> recentLeaver = new ConcurrentHashMap<>();

    private record Invite(UUID teamId, Instant expiresAt) {
        boolean expired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public enum Result {
        OK, ALREADY_IN_TEAM, NOT_OWNER, NOT_IN_TEAM, TEAM_FULL, TARGET_OFFLINE,
        TARGET_IN_TEAM, NO_INVITE, INVITE_EXPIRED, INVALID_NAME, TOO_SMALL,
        UNBALANCED, OWNER_CANNOT_LEAVE, INVALID_SIDE, KIT_NOT_FOUND, NO_ARENA
    }

    public TeamService(Plugin plugin, MatchService matchService) {
        this.plugin = plugin;
        this.matchService = matchService;
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
        team.invite(target.getUniqueId());
        invites.put(target.getUniqueId(), new Invite(team.id(), Instant.now().plus(INVITE_TTL)));
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

    // ---- start team battle (no queue) ----

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

        // Delegate to MatchService which handles arena reservation, teleport, countdown.
        Bukkit.getScheduler().runTask(plugin, () ->
                matchService.startTeamMatch(red, blue, kitId, MatchMode.TEAM, ArenaTerrain.ANY, 1));
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
        if (team.isOwner(player)) {
            for (UUID member : team.members()) {
                byMember.remove(member);
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
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
}
