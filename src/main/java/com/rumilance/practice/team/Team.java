package com.rumilance.practice.team;

import com.rumilance.practice.state.TeamColor;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A player-created team used for team battles. The owner creates the team, picks whether it is
 * {@link #isPublic() public} (anyone can join without an invite) or private (invite-only), and
 * triggers a match split into RED and BLUE. Assignment is either automatic (balanced shuffle)
 * or manual (the owner assigns players to sides).
 */
public final class Team {

    private final UUID id;
    private final UUID owner;
    private String name;
    private boolean isPublic;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> invites = new LinkedHashSet<>();
    /** Player -> chosen side (only set while a split is active). */
    private final java.util.Map<UUID, TeamColor> sideAssignment = new java.util.HashMap<>();
    private volatile Instant createdAt;

    public Team(UUID id, UUID owner, String name, boolean isPublic) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = name == null || name.isBlank() ? "Team" : name;
        this.isPublic = isPublic;
        this.createdAt = Instant.now();
        this.members.add(owner);
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? this.name : name;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    void add(UUID player) {
        members.add(player);
    }

    boolean remove(UUID player) {
        return members.remove(player);
    }

    Set<UUID> invites() {
        return invites;
    }

    void invite(UUID player) {
        invites.add(player);
    }

    boolean revokeInvite(UUID player) {
        return invites.remove(player);
    }

    boolean isInvited(UUID player) {
        return invites.contains(player);
    }

    // ---- side assignment for team battles ----

    public java.util.Map<UUID, TeamColor> sideAssignment() {
        return Collections.unmodifiableMap(sideAssignment);
    }

    public TeamColor sideOf(UUID player) {
        return sideAssignment.get(player);
    }

    public void assignSide(UUID player, TeamColor color) {
        if (!members.contains(player)) {
            return;
        }
        sideAssignment.put(player, color);
    }

    public void clearSides() {
        sideAssignment.clear();
    }

    public Set<UUID> side(TeamColor color) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID member : members) {
            if (sideAssignment.get(member) == color) {
                out.add(member);
            }
        }
        return out;
    }

    /** @return true when every member has a side and both sides are non-empty. */
    public boolean isSplitReady() {
        boolean red = false, blue = false;
        for (UUID member : members) {
            TeamColor color = sideAssignment.get(member);
            if (color == null) {
                return false;
            }
            if (color == TeamColor.RED) {
                red = true;
            } else {
                blue = true;
            }
        }
        return red && blue;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
