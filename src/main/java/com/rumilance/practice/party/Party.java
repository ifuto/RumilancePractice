package com.rumilance.practice.party;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory representation of a party. The leader is the player who created it; members include
 * the leader. Parties are intentionally simple here (no 2v2 matchmaking integration yet) and
 * provide the social layer: invite/join/leave/kick, party-only chat, and a shared warp.
 */
public final class Party {

    private final UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leader) {
        this.leader = Objects.requireNonNull(leader, "leader");
        this.members.add(leader);
    }

    public UUID leader() {
        return leader;
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    void add(UUID player) {
        members.add(player);
    }

    boolean remove(UUID player) {
        return members.remove(player);
    }
}
