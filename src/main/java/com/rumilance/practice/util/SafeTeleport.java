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
 *
 * <p><strong>Prevention first.</strong> The destination is chosen so the player can never end
 * up inside a block: own column first, then the nearest free column, and the pearl path resolves
 * its landing the same way ({@link PearlLanding}). The only case no pre-check can see is terrain
 * that does not exist yet — a disposable arena pasted around the fight start can finish after the
 * teleport already landed — so the landing is additionally watched for a short window after every
 * teleport (and for teleports made by other plugins, via the {@code PlayerTeleportEvent}
 * monitor): as soon as the player is genuinely inside blocks, they are lifted to the surface of
 * their own column. That watch is a net, not the mechanism.</p>
 */
public final class SafeTeleport {

    /** How far a pinned spawn may be moved sideways when its own column is unusable. */
    private static final int NEARBY_COLUMN_RADIUS = 3;

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
            if (clear != null) {
                target = clear;
            } else {
                // The spawn's own column has no standable spot at all (spawn points inside a
                // wall, an arena floor that moved): move to the nearest free column instead of
                // teleporting into blocks. Prevention first — the landing watch below is only
                // the net for blocks that appear after the move (a paste still running).
                Location nearby = SpawnFooting.standNearby(dest, NEARBY_COLUMN_RADIUS, 0.0d, 0.0d, true);
                if (nearby != null) {
                    target = nearby;
                }
            }
        }
        // Reset any stale per-player WorldBorder (lobby / previous arena) BEFORE the
        // teleport. Paper enforces the border during teleport and would otherwise reject
        // or clamp the player back onto the OLD border's corner — the source of the
        // "rematch snaps to a random arena corner" and "border in a weird place" bugs.
        // Callers re-apply the destination area's border AFTER the teleport succeeds.
        resetPersonalBorder(player);
        // Paper #13473: teleporting a spectator who is locked onto a camera target leaves
        // the client spectating the old entity while the server moves the camera, causing a
        // nasty unloaded-chunk desync. Detach from the target (vanilla behaviour) first.
        releaseSpectatorTarget(player);
        releaseSeat(player);
        player.setVelocity(new Vector());
        player.setFallDistance(0f);
        boolean ok = player.teleport(target);
        if (ok) {
            unBury(player);
            watchLanding(player);
        }
        return ok;
    }

    /**
     * Watches a fresh landing for a short window and lifts the player out of blocks that
     * appear afterwards (schematic paste still running, chunk arriving late). Only a genuine
     * burial — solid material inside both halves of the hitbox — triggers a lift, so ordinary
     * movement against a wall is never disturbed. Stops early once the landing is stable.
     */
    public static void watchLanding(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // One watch per player: SafeTeleport arms it directly and the teleport monitor arms it
        // again for the same move.
        if (!WATCHED.add(player.getUniqueId())) {
            return;
        }
        try {
            new LandingWatch(player.getUniqueId()).start();
        } catch (Throwable ignored) {
            WATCHED.remove(player.getUniqueId());
            // A guard that cannot be scheduled must never break the teleport itself.
        }
    }

    /** Players with an armed landing watch (see {@link #watchLanding(Player)}). */
    private static final java.util.Set<java.util.UUID> WATCHED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Ticks the landing watch stays armed; long enough for a paste that lands after the TP. */
    private static final int WATCH_TICKS = 60;
    /** How often the landing is checked while the watch is armed. */
    private static final int WATCH_INTERVAL = 2;
    /** Consecutive clean checks before the watch stands down early. */
    private static final int WATCH_CLEAN_CHECKS = 12;

    private static final class LandingWatch implements Runnable {

        private final java.util.UUID playerId;
        private org.bukkit.scheduler.BukkitTask task;
        private int ticks;
        private int clean;

        LandingWatch(java.util.UUID playerId) {
            this.playerId = playerId;
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin(), this, WATCH_INTERVAL, WATCH_INTERVAL);
        }

        @Override
        public void run() {
            ticks += WATCH_INTERVAL;
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || ticks > WATCH_TICKS) {
                cancel();
                return;
            }
            if (!SpawnFooting.isDeeplyBuried(player)) {
                if (++clean >= WATCH_CLEAN_CHECKS) {
                    cancel();
                }
                return;
            }
            clean = 0;
            Location at = player.getLocation();
            Location lifted = SpawnFooting.standClearPearl(at, SpawnFooting.maxLiftForUnbury());
            if (lifted == null) {
                lifted = SpawnFooting.forceLift(at);
            }
            if (lifted == null || SpawnFooting.isBuried(lifted)) {
                return;
            }
            player.setFallDistance(0f);
            player.setVelocity(new Vector());
            player.teleport(lifted);
        }

        private void cancel() {
            WATCHED.remove(playerId);
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private static void resetPersonalBorder(Player player) {
        try {
            // null restores the world's default border (see ViewControlService#clear).
            player.setWorldBorder(null);
        } catch (RuntimeException ignored) {
            // Older/forked servers without per-player borders: nothing to reset.
        }
    }

    /**
     * Paper #13473: detaches a spectator from the entity they are locked onto before the
     * teleport, matching vanilla (which stops spectating on teleport). Without this the client
     * keeps the old camera while the server moves the player, leaving the view in unloaded
     * chunks. A no-op when the player isn't spectating an entity.
     */
    private static void releaseSpectatorTarget(Player player) {
        try {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    && player.getSpectatorTarget() != null) {
                player.setSpectatorTarget(null);
            }
        } catch (RuntimeException ignored) {
            // Best-effort: a teleport must never fail because of camera detachment.
        }
    }

    /**
     * Sits are owned by the external GSit plugin: a seated player rides an invisible seat
     * armour stand. Teleporting a mounted player drags the seat along (or strands it in the
     * destination), so every teleport stands the player up first. Also covers any other ride
     * (boat, minecart, another plugin's seat) a lobby player might still be on when a fight,
     * a spectate or a rescue moves them.
     */
    private static void releaseSeat(Player player) {
        try {
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
        } catch (RuntimeException | NoSuchMethodError ignored) {
            // Best-effort: a teleport must never fail because the player was sitting.
        }
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
