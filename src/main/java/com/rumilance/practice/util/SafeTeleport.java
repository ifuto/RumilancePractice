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
 * Single entry for every player teleport. Loads the destination chunk, then teleports exactly
 * like {@code /tp} to the requested coordinates (+1 Y), without scanning for footing blocks.
 */
public final class SafeTeleport {

    private static final double Y_OFFSET = 1.0d;

    private SafeTeleport() {
    }

    public static CompletableFuture<Boolean> teleport(Player player, Location desired) {
        if (player == null || !player.isOnline() || desired == null || desired.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }
        World world = desired.getWorld();
        int cx = desired.getBlockX() >> 4;
        int cz = desired.getBlockZ() >> 4;
        CompletableFuture<?> chunkReady = world.isChunkLoaded(cx, cz)
                ? CompletableFuture.completedFuture(Boolean.TRUE)
                : world.getChunkAtAsync(cx, cz, true);
        return chunkReady.thenCompose(ignored -> runOnMain(() -> apply(player, desired)));
    }

    private static boolean apply(Player player, Location desired) {
        if (player == null || !player.isOnline() || desired == null || desired.getWorld() == null) {
            return false;
        }
        Location dest = desired.clone();
        dest.setY(dest.getY() + Y_OFFSET);
        player.setVelocity(new Vector());
        player.setFallDistance(0f);
        return player.teleport(dest);
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
