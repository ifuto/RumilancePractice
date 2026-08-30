package com.rumilance.practice.combat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import com.rumilance.practice.util.TickHealth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player combat netcode state: ping EMA, spike/burst flags, sprint intent, last safe
 * grounded location, knockback-multiplier pending from kit rules, and a short movement
 * history used to rewind attacker crits.
 *
 * <p>All public mutators are intended for the main thread. Maps are concurrent so a
 * report/GUI thread can read ping without blocking combat.</p>
 */
public final class CombatNetTracker {

    private static final int HISTORY = 20;
    private static final double PING_EMA_ALPHA = 0.35d;
    private static final long BURST_WINDOW_NANOS = 400_000_000L;
    private static final long TELEPORT_GRACE_NANOS = 1_200_000_000L;
    private static final long KNOCKBACK_GRACE_NANOS = 350_000_000L;

    public record Snapshot(
            boolean onGround,
            float fallDistance,
            double verticalVelocity,
            boolean sprinting,
            boolean inWater,
            boolean climbing,
            boolean passenger,
            boolean blindness
    ) {
    }

    public static final class PlayerNet {
        private double ping = 50.0d;
        private double previousPing = 50.0d;
        private double emaPing = 50.0d;
        private volatile boolean wantsSprint;
        private volatile boolean burst;
        private volatile long lastMoveNanos = System.nanoTime();
        private volatile long burstUntilNanos;
        private volatile long teleportGraceUntilNanos;
        private volatile long lastKnockbackNanos;
        private volatile double lastVerticalVelocity;
        private volatile double pendingKbMultiplier = 1.0d;
        private volatile boolean pendingMeleeKnockback;
        private volatile UUID pendingMeleeAttacker;
        private volatile Vector pendingPreHitVelocity;
        private volatile boolean pendingAttackerSprinting;
        private volatile Location lastSafe;
        private final Snapshot[] history = new Snapshot[HISTORY];
        private int writeIndex;
        private int historyCount;
    }

    private final Map<UUID, PlayerNet> players = new ConcurrentHashMap<>();
    private final int pingOffsetMs;
    private final int spikeThresholdMs;

    public CombatNetTracker(int pingOffsetMs, int spikeThresholdMs) {
        this.pingOffsetMs = Math.max(0, pingOffsetMs);
        this.spikeThresholdMs = Math.max(1, spikeThresholdMs);
    }

    public CombatNetTracker() {
        this(CombatPhysics.PING_OFFSET_MS, CombatPhysics.SPIKE_THRESHOLD_MS);
    }

    public PlayerNet of(UUID playerId) {
        return players.computeIfAbsent(playerId, id -> new PlayerNet());
    }

    public void remove(UUID playerId) {
        players.remove(playerId);
    }

    /**
     * Samples {@link Player#getPing()}, updates EMA / spike state, records a movement
     * snapshot and refreshes last-safe when the player is grounded inside a loaded chunk.
     */
    public void sample(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerNet net = of(player.getUniqueId());
        double sample = Math.max(0, player.getPing());
        net.previousPing = net.ping;
        net.ping = sample;
        net.emaPing = CombatPhysics.ema(net.emaPing, sample, PING_EMA_ALPHA);

        long now = System.nanoTime();
        long elapsed = now - net.lastMoveNanos;
        if (!TickHealth.lagging()
                && CombatPhysics.isIdleGap(elapsed, net.emaPing, spikeThresholdMs, TickHealth.lastTickNanos())) {
            net.burst = true;
            net.burstUntilNanos = now + BURST_WINDOW_NANOS;
        } else if (now >= net.burstUntilNanos) {
            net.burst = false;
        }

        Snapshot snap = capture(player);
        net.history[net.writeIndex] = snap;
        net.writeIndex = (net.writeIndex + 1) % HISTORY;
        if (net.historyCount < HISTORY) {
            net.historyCount++;
        }
        net.lastVerticalVelocity = player.getVelocity().getY();

        if (player.isOnGround() && isLoadedSafe(player.getLocation())) {
            rememberSafeInPlace(net, player.getLocation());
        }
    }

    public void markMoved(Player player) {
        of(player.getUniqueId()).lastMoveNanos = System.nanoTime();
    }

    public void markTeleport(Player player) {
        PlayerNet net = of(player.getUniqueId());
        net.teleportGraceUntilNanos = System.nanoTime() + TELEPORT_GRACE_NANOS;
        net.burst = false;
        Location loc = player.getLocation();
        if (isLoadedSafe(loc)) {
            rememberSafeInPlace(net, loc);
        }
    }

    public void setWantsSprint(UUID playerId, boolean wants) {
        of(playerId).wantsSprint = wants;
    }

    public boolean wantsSprint(UUID playerId) {
        PlayerNet net = players.get(playerId);
        return net != null && net.wantsSprint;
    }

    public void setPendingKbMultiplier(UUID playerId, double multiplier) {
        of(playerId).pendingKbMultiplier = multiplier <= 0.0d ? 1.0d : multiplier;
    }

    public double takePendingKbMultiplier(UUID playerId) {
        PlayerNet net = of(playerId);
        double value = net.pendingKbMultiplier;
        net.pendingKbMultiplier = 1.0d;
        return value;
    }

    public void markPendingMeleeKnockback(UUID playerId) {
        markPendingMeleeKnockback(playerId, null, null, false);
    }

    public void markPendingMeleeKnockback(UUID victimId, UUID attackerId) {
        markPendingMeleeKnockback(victimId, attackerId, null, false);
    }

    /**
     * {@code preHitVelocity} must be the victim's motion <em>before</em> vanilla knockback
     * is applied (capture it on {@code EntityDamageByEntityEvent}). Using the post-hit
     * velocity stacks the preset on top of Paper's knockback.
     */
    public void markPendingMeleeKnockback(
            UUID victimId, UUID attackerId, Vector preHitVelocity, boolean attackerSprinting
    ) {
        PlayerNet net = of(victimId);
        net.pendingMeleeKnockback = true;
        net.pendingMeleeAttacker = attackerId;
        net.pendingPreHitVelocity = preHitVelocity == null ? null : preHitVelocity.clone();
        net.pendingAttackerSprinting = attackerSprinting;
    }

    public UUID takePendingMeleeAttacker(UUID playerId) {
        PlayerNet net = of(playerId);
        UUID attacker = net.pendingMeleeAttacker;
        net.pendingMeleeAttacker = null;
        return attacker;
    }

    public Vector takePendingPreHitVelocity(UUID playerId) {
        PlayerNet net = of(playerId);
        Vector velocity = net.pendingPreHitVelocity;
        net.pendingPreHitVelocity = null;
        return velocity;
    }

    public boolean takePendingAttackerSprinting(UUID playerId) {
        PlayerNet net = of(playerId);
        boolean sprinting = net.pendingAttackerSprinting;
        net.pendingAttackerSprinting = false;
        return sprinting;
    }

    public boolean takePendingMeleeKnockback(UUID playerId) {
        PlayerNet net = of(playerId);
        boolean pending = net.pendingMeleeKnockback;
        net.pendingMeleeKnockback = false;
        return pending;
    }

    public void markKnockback(UUID playerId, double vertical) {
        PlayerNet net = of(playerId);
        net.lastKnockbackNanos = System.nanoTime();
        net.lastVerticalVelocity = vertical;
    }

    public double lastVerticalVelocity(UUID playerId) {
        PlayerNet net = players.get(playerId);
        return net == null ? 0.0d : net.lastVerticalVelocity;
    }

    public boolean inTeleportGrace(UUID playerId) {
        PlayerNet net = players.get(playerId);
        return net != null && System.nanoTime() < net.teleportGraceUntilNanos;
    }

    public boolean inKnockbackGrace(UUID playerId) {
        PlayerNet net = players.get(playerId);
        return net != null && System.nanoTime() - net.lastKnockbackNanos < KNOCKBACK_GRACE_NANOS;
    }

    public boolean inBurst(UUID playerId) {
        PlayerNet net = players.get(playerId);
        return net != null && net.burst && System.nanoTime() < net.burstUntilNanos;
    }

    public boolean isSpike(UUID playerId) {
        PlayerNet net = players.get(playerId);
        if (net == null) {
            return false;
        }
        return CombatPhysics.isSpike(net.ping, net.previousPing, spikeThresholdMs);
    }

    public double compensatedPingMs(UUID playerId) {
        PlayerNet net = of(playerId);
        return CombatPhysics.compensatedPingMs(net.ping, net.previousPing, pingOffsetMs, spikeThresholdMs);
    }

    public int compensatedTicks(UUID playerId) {
        return CombatPhysics.ticksFromMs(compensatedPingMs(playerId));
    }

    public double emaPing(UUID playerId) {
        return of(playerId).emaPing;
    }

    public Location lastSafe(UUID playerId) {
        PlayerNet net = players.get(playerId);
        return net == null || net.lastSafe == null ? null : net.lastSafe.clone();
    }

    public void rememberSafe(Player player) {
        if (player != null && isLoadedSafe(player.getLocation())) {
            rememberSafeInPlace(of(player.getUniqueId()), player.getLocation());
        }
    }

    private static void rememberSafeInPlace(PlayerNet net, Location loc) {
        Location prev = net.lastSafe;
        if (prev == null || prev.getWorld() != loc.getWorld()) {
            net.lastSafe = loc.clone();
            return;
        }
        prev.setX(loc.getX());
        prev.setY(loc.getY());
        prev.setZ(loc.getZ());
        prev.setYaw(loc.getYaw());
        prev.setPitch(loc.getPitch());
    }

    /**
     * Attacker's state as their client saw it when the click was sent (rewind by compensated
     * ticks). Falls back to the live state when history is too short or a spike/burst makes
     * rewind untrustworthy  Ein those cases we <em>do not</em> invent a crit.
     */
    public Snapshot rewindAttacker(Player attacker) {
        UUID id = attacker.getUniqueId();
        if (isSpike(id) || inBurst(id)) {
            return capture(attacker);
        }
        PlayerNet net = of(id);
        int ticks = compensatedTicks(id);
        if (net.historyCount == 0 || ticks <= 0) {
            return capture(attacker);
        }
        int idx = CombatPhysics.historyIndex(net.writeIndex, ticks, HISTORY);
        Snapshot snap = net.history[idx];
        return snap == null ? capture(attacker) : snap;
    }

    private static final Vector DOWN = new Vector(0, -1, 0);

    public double distanceToGround(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return 5.0d;
        }
        double min = 5.0d;
        double half = 0.3d;
        double y = loc.getY();
        double x = loc.getX();
        double z = loc.getZ();
        Location from = loc.clone();
        from.setY(y);
        double[] xs = {x - half, x - half, x + half, x + half};
        double[] zs = {z - half, z + half, z - half, z + half};
        for (int i = 0; i < 4; i++) {
            from.setX(xs[i]);
            from.setZ(zs[i]);
            RayTraceResult hit = world.rayTraceBlocks(from, DOWN, 5.0d, FluidCollisionMode.NEVER, true);
            if (hit == null || hit.getHitPosition() == null) {
                continue;
            }
            min = Math.min(min, y - hit.getHitPosition().getY());
        }
        return Math.max(0.0d, min);
    }

    public static boolean isVoidLike(Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        World world = location.getWorld();
        if (location.getY() < world.getMinHeight() + 1) {
            return true;
        }
        Block feet = location.getBlock();
        return feet.getType() == Material.VOID_AIR;
    }

    public static boolean isLoadedSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (isVoidLike(location)) {
            return false;
        }
        World world = location.getWorld();
        return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static Snapshot capture(Player player) {
        return new Snapshot(
                player.isOnGround(),
                player.getFallDistance(),
                player.getVelocity().getY(),
                player.isSprinting(),
                player.isInWater(),
                player.isClimbing(),
                player.isInsideVehicle(),
                player.hasPotionEffect(PotionEffectType.BLINDNESS)
        );
    }
}
