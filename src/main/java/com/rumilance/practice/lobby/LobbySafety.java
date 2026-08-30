package com.rumilance.practice.lobby;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Pure lobby movement rules. Combat netcode / world-border must never use these to
 * cancel look packets or freeze a player standing on spawn.
 */
public final class LobbySafety {

    private LobbySafety() {
    }

    public static boolean ignoresBounds(Player player) {
        if (player == null) {
            return true;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE
                || mode == GameMode.SPECTATOR
                || player.isFlying()
                || player.getAllowFlight()
                || player.hasPermission("rumilance.admin")
                || player.hasPermission("rumilance.lobby.bypass");
    }

    /**
     * Y below which a lobby player is sent back. Spawn itself must never be below this
     * line — a mis-set {@code fall-return-y} at floor/spawn height froze everyone, including
     * creative flight downward.
     */
    public static double voidReturnY(double fallReturnY, Double spawnY, Integer worldMinHeight) {
        double floor = fallReturnY;
        if (spawnY != null && floor >= spawnY - 1.0d) {
            return worldMinHeight == null ? spawnY - 16.0d : worldMinHeight + 1.0d;
        }
        return floor;
    }
}
