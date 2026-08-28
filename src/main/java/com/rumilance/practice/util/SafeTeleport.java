package com.rumilance.practice.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Single entry for every player teleport. The destination chunk is loaded, a collision-free
 * stand is resolved, and only then is the player moved. A no-op is allowed only when they are
 * already standing clear at that pose.
 */
public final class SafeTeleport {

    private static final double ALREADY_THERE_SQ = 0.04d;
    private static final ConcurrentMap<java.util.UUID, Location> LAST_SAFE = new ConcurrentHashMap<>();

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
        Location stand = SpawnFooting.standClear(desired);
        if (stand == null) {
            plugin().getLogger().warning(() -> "Refusing teleport for " + player.getName()
                    + ": no collision-free stand at " + format(desired));
            return false;
        }
        if (alreadyStandingClear(player, stand)) {
            player.setFallDistance(0f);
            player.setVelocity(new Vector());
            rememberSafe(player);
            return true;
        }
        Location fallback = safelyStanding(player) ? player.getLocation().clone() : LAST_SAFE.get(player.getUniqueId());
        player.setVelocity(new Vector());
        player.setFallDistance(0f);
        if (!player.teleport(stand)) {
            return false;
        }
        verifyFooting(player, stand, fallback, 0);
        return true;
    }

    static boolean alreadyStandingClear(Player player, Location stand) {
        Location current = player.getLocation();
        if (current.getWorld() == null || stand.getWorld() == null || !current.getWorld().equals(stand.getWorld())) {
            return false;
        }
        if (SpawnFooting.isBuried(player)) {
            return false;
        }
        return current.distanceSquared(stand) <= ALREADY_THERE_SQ;
    }

    private static void verifyFooting(Player player, Location intended, Location fallback, int attempt) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (safelyStanding(player)) {
            player.setFallDistance(0f);
            rememberSafe(player);
            // The first verification catches an unloaded-paste race; the later ones catch
            // a delayed block update without continuously polling players.
            if (attempt < 2) {
                long delay = attempt == 0 ? 2L : 8L;
                Bukkit.getScheduler().runTaskLater(plugin(),
                        () -> verifyFooting(player, intended, fallback, attempt + 1), delay);
            }
            return;
        }
        Location repaired = SpawnFooting.standClear(intended);
        if (repaired == null && fallback != null) {
            repaired = SpawnFooting.standClear(fallback);
        }
        if (repaired == null) {
            // Never leave a player at an unsupported location. The world spawn is only an
            // emergency fallback when both the arena/lobby target and the previous footing
            // disappeared while a schematic was being pasted.
            repaired = SpawnFooting.standClear(player.getWorld().getSpawnLocation());
        }
        if (repaired == null) {
            plugin().getLogger().warning(() -> "Unable to restore safe footing for " + player.getName());
            return;
        }
        player.setVelocity(new Vector());
        player.setFallDistance(0f);
        player.teleport(repaired);
        if (attempt < 2) {
            Location verifiedTarget = repaired;
            Bukkit.getScheduler().runTaskLater(plugin(),
                    () -> verifyFooting(player, verifiedTarget, fallback, attempt + 1), 2L);
        }
    }

    private static boolean safelyStanding(Player player) {
        Location location = player.getLocation();
        return !SpawnFooting.isBuried(player)
                && SpawnFooting.supported(player.getWorld(), location.getX(), location.getY(), location.getZ());
    }

    private static void rememberSafe(Player player) {
        LAST_SAFE.put(player.getUniqueId(), player.getLocation().clone());
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
            Plugin named = Bukkit.getPluginManager().getPlugin("RumilancePractice");
            if (named != null) {
                return named;
            }
            throw e;
        }
    }

    private static String format(Location location) {
        World world = location.getWorld();
        return (world == null ? "?" : world.getName())
                + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
