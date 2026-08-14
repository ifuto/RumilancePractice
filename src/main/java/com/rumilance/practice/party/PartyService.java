package com.rumilance.practice.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight party manager: create/disband, invite/join/leave/kick, party chat, and a shared
 * warp that teleports all members to the party leader. Persistence is intentionally out of
 * scope — parties do not survive a server restart.
 */
public final class PartyService {

    /** Maps a player (leader or member) to their party. */
    private final Map<UUID, Party> byPlayer = new ConcurrentHashMap<>();
    /** Pending invites: invitee -> (inviter, expiresAt). */
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    private static final Duration INVITE_TTL = Duration.ofSeconds(60);

    private record Invite(UUID partyLeader, Instant expiresAt) {
        boolean expired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    public enum Result {
        OK, ALREADY_IN_PARTY, NOT_LEADER, TARGET_OFFLINE, TARGET_IN_PARTY,
        NO_PARTY, NOT_INVITED, INVITE_EXPIRED, CANNOT_KICK_LEADER, CANNOT_REMOVE_SELF, TARGET_SELF
    }

    /** Creates a party for the player if they are not already in one. */
    public Result create(Player leader) {
        if (byPlayer.containsKey(leader.getUniqueId())) {
            return Result.ALREADY_IN_PARTY;
        }
        byPlayer.put(leader.getUniqueId(), new Party(leader.getUniqueId()));
        leader.sendMessage(Component.text("Party created. Invite players with /party invite <name>.",
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        return Result.OK;
    }

    public Result disband(Player player) {
        Party party = byPlayer.get(player.getUniqueId());
        if (party == null) {
            return Result.NO_PARTY;
        }
        if (!party.isLeader(player.getUniqueId())) {
            return Result.NOT_LEADER;
        }
        broadcast(party, Component.text("The party has been disbanded.", NamedTextColor.RED));
        for (UUID member : party.members()) {
            byPlayer.remove(member);
        }
        return Result.OK;
    }

    public Result invite(Player inviter, String targetName) {
        Party party = byPlayer.get(inviter.getUniqueId());
        if (party == null) {
            party = new Party(inviter.getUniqueId());
            byPlayer.put(inviter.getUniqueId(), party);
        }
        if (!party.isLeader(inviter.getUniqueId())) {
            inviter.sendMessage(Component.text("Only the party leader can invite players.", NamedTextColor.RED));
            return Result.NOT_LEADER;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            inviter.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return Result.TARGET_OFFLINE;
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            return Result.TARGET_SELF;
        }
        if (byPlayer.containsKey(target.getUniqueId())) {
            inviter.sendMessage(Component.text(target.getName() + " is already in a party.", NamedTextColor.RED));
            return Result.TARGET_IN_PARTY;
        }
        invites.put(target.getUniqueId(), new Invite(inviter.getUniqueId(), Instant.now().plus(INVITE_TTL)));
        inviter.sendMessage(Component.text("Invited " + target.getName() + " to your party.", NamedTextColor.AQUA));
        target.sendMessage(Component.text(inviter.getName() + " invited you to a party. ", NamedTextColor.AQUA)
                .append(Component.text("[Accept]", NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/party accept " + inviter.getName()))));
        return Result.OK;
    }

    public Result accept(Player player, String inviterName) {
        Invite invite = invites.get(player.getUniqueId());
        if (invite == null || invite.expired(Instant.now())) {
            invites.remove(player.getUniqueId());
            player.sendMessage(Component.text("You have no pending party invite.", NamedTextColor.RED));
            return Result.NOT_INVITED;
        }
        Player inviter = Bukkit.getPlayer(invite.partyLeader());
        if (inviter == null) {
            invites.remove(player.getUniqueId());
            player.sendMessage(Component.text("The inviter is no longer online.", NamedTextColor.RED));
            return Result.TARGET_OFFLINE;
        }
        if (inviterName != null && !inviter.getName().equalsIgnoreCase(inviterName)) {
            player.sendMessage(Component.text("That invite does not match the pending invite.", NamedTextColor.RED));
            return Result.NOT_INVITED;
        }
        Party party = byPlayer.get(inviter.getUniqueId());
        if (party == null) {
            invites.remove(player.getUniqueId());
            return Result.NO_PARTY;
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in a party.", NamedTextColor.RED));
            return Result.ALREADY_IN_PARTY;
        }
        party.add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), party);
        invites.remove(player.getUniqueId());
        broadcast(party, Component.text(player.getName() + " joined the party.", NamedTextColor.AQUA));
        return Result.OK;
    }

    public Result leave(Player player) {
        Party party = byPlayer.get(player.getUniqueId());
        if (party == null) {
            return Result.NO_PARTY;
        }
        if (party.isLeader(player.getUniqueId())) {
            return disband(player);
        }
        party.remove(player.getUniqueId());
        byPlayer.remove(player.getUniqueId());
        player.sendMessage(Component.text("You left the party.", NamedTextColor.YELLOW));
        broadcast(party, Component.text(player.getName() + " left the party.", NamedTextColor.YELLOW));
        return Result.OK;
    }

    public Result kick(Player leader, String targetName) {
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            return Result.NO_PARTY;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            return Result.NOT_LEADER;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            return Result.TARGET_OFFLINE;
        }
        if (target.getUniqueId().equals(leader.getUniqueId())) {
            return Result.CANNOT_KICK_LEADER;
        }
        if (!party.contains(target.getUniqueId())) {
            return Result.NOT_INVITED;
        }
        party.remove(target.getUniqueId());
        byPlayer.remove(target.getUniqueId());
        target.sendMessage(Component.text("You were removed from the party.", NamedTextColor.RED));
        broadcast(party, Component.text(target.getName() + " was kicked from the party.", NamedTextColor.RED));
        return Result.OK;
    }

    public Result warp(Player leader) {
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            return Result.NO_PARTY;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            return Result.NOT_LEADER;
        }
        broadcast(party, Component.text("Teleporting to " + leader.getName() + "...", NamedTextColor.AQUA));
        for (UUID member : party.members()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && !p.getUniqueId().equals(leader.getUniqueId())) {
                p.teleportAsync(leader.getLocation());
            }
        }
        return Result.OK;
    }

    public boolean isInParty(UUID player) {
        return byPlayer.containsKey(player);
    }

    public Optional<Party> partyOf(UUID player) {
        return Optional.ofNullable(byPlayer.get(player));
    }

    public List<String> membersOnlineNames(UUID player) {
        Party party = byPlayer.get(player);
        if (party == null) {
            return List.of();
        }
        return party.members().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .map(Player::getName)
                .toList();
    }

    public void sendPartyChat(Player sender, String message) {
        Party party = byPlayer.get(sender.getUniqueId());
        if (party == null) {
            sender.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }
        Component prefix = Component.text("[Party] ", NamedTextColor.AQUA)
                .append(Component.text(sender.getName() + ": ", NamedTextColor.WHITE))
                .append(Component.text(message, NamedTextColor.LIGHT_PURPLE))
                .decoration(TextDecoration.ITALIC, false);
        for (UUID member : party.members()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(prefix);
            }
        }
    }

    private void broadcast(Party party, Component message) {
        for (UUID member : party.members()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(message.decoration(TextDecoration.ITALIC, false));
            }
        }
    }

    /** Removes a player from their party on quit (without spamming offline members). */
    public void handleQuit(UUID player) {
        Party party = byPlayer.get(player);
        if (party == null) {
            invites.remove(player);
            return;
        }
        if (party.isLeader(player)) {
            for (UUID member : party.members()) {
                byPlayer.remove(member);
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
                    p.sendMessage(Component.text("Party disbanded (leader left).", NamedTextColor.RED));
                }
            }
        } else {
            party.remove(player);
            byPlayer.remove(player);
            for (UUID member : party.members()) {
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
                    p.sendMessage(Component.text("A player left the party.", NamedTextColor.YELLOW));
                }
            }
        }
        invites.remove(player);
    }
}
