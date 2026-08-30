package com.rumilance.practice.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Single entry for every player teleport. Loads the destination chunk, adjusts the target
 * onto a standable surface in the <strong>same block column</strong> ({@link SpawnFooting})
 * so a configured spawn inside a schematic / under a pasted floor never buries the player,
 * then teleports on the main thread. A post-teleport burial check lifts the player out
 * of any block the client/server still reports overlapping.
 */
public final class SafeTeleport {

    private SafeTeleport() {
    }

    public static CompletableFuture<Boolean> teleport(Player player, Location desired) {
        return teleport(player, desired, true);
    }

    /**
     * @param footingAdjust when {@code true} (pinned spawns: lobby, duel spawn A/B, FFA
     *                      fixed spawns) the destination is clamped onto a standable surface
     *                      in the same column. Pass {@code false} for landings that must keep
     *                      their exact coordinates (e.g. pearl landings, which run their own
     *                      footing logic via {@link PearlLanding}).
     */
    public static CompletableFuture<Boolean> teleport(Player player, Location desired, boolean footingAdjust) {
        if (player == null || !player.isOnline() || desired == null || desired.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }
        World world = desired.getWorld();
        int cx = desired.getBlockX() >> 4;
        int cz = desired.getBlockZ() >> 4;
        CompletableFuture<?> chunkReady = world.isChunkLoaded(cx, cz)
                ? CompletableFuture.completedFuture(Boolean.TRUE)
                : world.getChunkAtAsync(cx, cz, true);
        return chunkReady.thenCompose(ignored -> runOnMain(() -> apply(player, desired, footingAdjust)));
    }

    private static boolean apply(Player player, Location desired, boolean footingAdjust) {
        if (player == null || !player.isOnline() || desired == null || desired.getWorld() == null) {
            return false;
        }
        Location dest = desired.clone();
        // Pinned spawns: keep the configured X/Z column, only un-bury the Y onto the
        // collision surface of that column. If no standable point exists (incomplete
        // schematic / void arena), fail the teleport so callers can retry another arena
        // instead of dropping the player into blocks or the void.
        Location target = dest;
        if (footingAdjust) {
            Location clear = SpawnFooting.standOneAbove(dest);
            if (clear == null) {
                return false;
            }
            target = clear;
        }
        player.setVelocity(new Vector());
        player.setFallDistance(0f);
        boolean ok = player.teleport(target);
        if (ok) {
            unBury(player);
        }
        return ok;
    }

    /**
     * Last-mile anti-bury: if the player still overlaps solid blocks right after the
     * teleport (client sent an overlapping pose, paste lag, etc.), pop them up to the
     * nearest clear spot in the same column. Never silently leaves them embedded.
     */
    private static void unBury(Player player) {
        try {
            if (!SpawnFooting.isBuried(player)) {
                return;
            }
            Location at = player.getLocation();
            Location lifted = SpawnFooting.standClearPearl(at, SpawnFooting.maxLiftForUnbury());
            if (lifted != null && !SpawnFooting.isBuried(lifted)) {
                player.setVelocity(new Vector());
                player.setFallDistance(0f);
                player.teleport(lifted);
            }
        } catch (RuntimeException ignored) {
            // Un-bury is best-effort; the primary teleport already succeeded.
        }
    }

    private static <T> CompletableFuture<T> runOnMain(Supplier<T> action) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(action.get());
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<T> done = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin(), () -> {
            try {
                done.complete(action.get());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

    private static Plugin plugin() {
        try {
            return JavaPlugin.getProvidingPlugin(SafeTeleport.class);
        } catch (IllegalArgumentException e) {
            Plugin named = com.rumilance.practice.PluginIdentity.plugin();
            if (named != null) {
                return named;
            }
            throw e;
        }
    }
}
