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
    /** Optional party fight arena name (exact case); null = random / kit default. */
    private volatile String selectedArena;
    /** When true, teammates can damage each other in party battles. */
    private volatile boolean friendlyFire;
    /**
     * Number of active team slots (2 = classic RED/BLUE). Upper bound depends on the host's
     * rank (3 norm / 5 VIP / 7 VIP+); enforced by TeamService, clamped here defensively.
     */
    private volatile int teamCount = 2;
    /** Per-team battle config edits (health / size / effects / own kit). */
    private final java.util.Map<TeamColor, TeamConfig> teamConfigs = new java.util.HashMap<>();
    /** Owner's original-kit slot to fight with (null = normal kit loadouts). */
    private volatile Integer originalKitSlot;

    /** Original-kit slot the owner picked for the next battle, or null for regular kits. */
    public Integer originalKitSlot() {
        return originalKitSlot;
    }

    public void setOriginalKitSlot(Integer slot) {
        this.originalKitSlot = slot;
    }

    public Team(UUID id, UUID owner, String name, boolean isPublic) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = name == null || name.isBlank() ? "Team" : name;
        this.isPublic = isPublic;
        this.createdAt = Instant.now();
        this.members.add(owner);
    }

    // ---- multi-team slots + per-team battle config ----

    /** Active team colors in canonical order (first {@link #teamCount} colors). */
    public java.util.List<TeamColor> activeColors() {
        return TeamColor.canonical(teamCount);
    }

    public int teamCount() {
        return teamCount;
    }

    /** Clamps into 2..{@code maxAllowed}; members assigned to removed slots lose their side. */
    public void setTeamCount(int count, int maxAllowed) {
        int clamped = Math.max(2, Math.min(TeamColor.MAX_TEAMS, Math.min(count, maxAllowed)));
        if (clamped == teamCount) {
            return;
        }
        java.util.Set<TeamColor> kept = new java.util.HashSet<>(TeamColor.canonical(clamped));
        sideAssignment.values().removeIf(color -> !kept.contains(color));
        teamConfigs.keySet().removeIf(color -> !kept.contains(color));
        this.teamCount = clamped;
    }

    /** Battle config of a team slot (never null — defaults when unedited). */
    public TeamConfig configOf(TeamColor color) {
        TeamConfig config = teamConfigs.get(color);
        return config == null ? TeamConfig.defaults() : config;
    }

    /** Stores a battle config for a team slot (null/default removes the override). */
    public void setConfig(TeamColor color, TeamConfig config) {
        if (color == null) {
            return;
        }
        if (config == null || config.isDefault()) {
            teamConfigs.remove(color);
        } else {
            teamConfigs.put(color, config);
        }
    }

    /** Snapshot of every non-default team config (for starting a battle). */
    public java.util.Map<TeamColor, TeamConfig> customConfigs() {
        return java.util.Map.copyOf(teamConfigs);
    }

    /**
     * Swaps the rosters (and battle configs) of two team slots — the wool "color toggle"
     * in the team settings GUI. Members keep their slot; only the colors exchange places.
     */
    public void swapColors(TeamColor a, TeamColor b) {
        if (a == null || b == null || a == b) {
            return;
        }
        sideAssignment.replaceAll((u, color) -> {
            if (color == a) return b;
            if (color == b) return a;
            return color;
        });
        TeamConfig configA = teamConfigs.remove(a);
        TeamConfig configB = teamConfigs.remove(b);
        if (configA != null) teamConfigs.put(b, configA);
        if (configB != null) teamConfigs.put(a, configB);
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
        if (color == null) {
            sideAssignment.remove(player);
        } else {
            sideAssignment.put(player, color);
        }
    }

    /** Removes the member's side assignment (back to unassigned). */
    public void unassignSide(UUID player) {
        sideAssignment.remove(player);
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

    /** @return true when every member has a side and at least two sides are non-empty. */
    public boolean isSplitReady() {
        java.util.Set<TeamColor> populated = new java.util.HashSet<>();
        for (UUID member : members) {
            TeamColor color = sideAssignment.get(member);
            if (color == null) {
                return false;
            }
            populated.add(color);
        }
        return populated.size() >= 2;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String selectedArena() {
        return selectedArena;
    }

    public void setSelectedArena(String arenaName) {
        this.selectedArena = arenaName == null || arenaName.isBlank() ? null : arenaName;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }
}
