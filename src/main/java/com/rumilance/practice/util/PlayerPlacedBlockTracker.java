package com.rumilance.practice.util;

import org.bukkit.block.Block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks blocks placed by players during a match or FFA arena session so kits can allow breaking
 * only player-placed blocks while keeping arena geometry intact.
 */
public final class PlayerPlacedBlockTracker {

    private final Map<Long, String> scopes = new ConcurrentHashMap<>();

    public void mark(Block block, String scope) {
        if (block == null || scope == null || scope.isBlank()) {
            return;
        }
        scopes.put(key(block), scope);
    }

    public boolean isPlacedInScope(Block block, String scope) {
        if (block == null || scope == null || scope.isBlank()) {
            return false;
        }
        return scope.equals(scopes.get(key(block)));
    }

    public void unmark(Block block) {
        if (block == null) {
            return;
        }
        scopes.remove(key(block));
    }

    public void clearScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return;
        }
        scopes.entrySet().removeIf(entry -> scope.equals(entry.getValue()));
    }

    static long key(Block block) {
        UUID worldId = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        long w = worldId.getMostSignificantBits() ^ worldId.getLeastSignificantBits();
        return w ^ ((long) x << 38) ^ ((long) y << 26) ^ (z & 0x3FFFFFFFL);
    }
}
