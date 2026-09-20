package com.rumilance.practice.practice.afk;

import org.bukkit.World;

import java.util.UUID;

/**
 * Read-only view of "who is inside a private AFK room, and where is that room". Implemented by
 * {@link AfkCrystalManager}; consumed by {@link AfkRoomIsolationPackets} on the netty threads,
 * so the returned {@link Room} must be immutable and the lookup thread-safe.
 */
public interface AfkRoomIsolationSource {

    /**
     * The caller's own room while they are isolated, or {@code null} when they are not in a
     * private AFK room (and therefore see the world normally).
     */
    Room roomOf(UUID playerId);

    /**
     * One private room's footprint: world plus floor centre and floor radius. The build-height
     * cap is deliberately absent — it is a placement rule, never a visibility or movement rule.
     */
    record Room(World world, double centerX, double centerZ, int floorRadius) {
    }
}
