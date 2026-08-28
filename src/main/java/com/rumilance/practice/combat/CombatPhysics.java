package com.rumilance.practice.combat;

/**
 * Pure combat / netcode math used to make knockback and crits feel the same across ping
 * without opening rewind or hitbox-expansion exploits.
 *
 * <p>Defaults match the values used by competitive practice servers (stray.gg and the
 * KnockbackSync 1.3.2 baseline): {@code ping offset = 25ms}, {@code spike threshold = 20ms}.
 * Compensation is always clamped so a 400ms lag-switch cannot buy a 400ms rewind (cap 320ms).</p>
 */
public final class CombatPhysics {

    /** Subtracted from measured ping so low-ping players keep vanilla knockback. */
    public static final int PING_OFFSET_MS = 25;
    /** If ping jumped by more than this vs the previous sample, treat it as a spike. */
    public static final int SPIKE_THRESHOLD_MS = 20;
    /** Hard cap on how far we will compensate. Prevents lag-switch rewind abuse. */
    public static final int MAX_COMPENSATION_MS = 320;
    /** Vanilla living-entity gravity per tick. */
    public static final double GRAVITY = 0.08d;
    /** Vanilla vertical drag applied after gravity. */
    public static final double DRAG = 0.98d;
    /** Vanilla on-ground knockback vertical (strength 0.4). */
    public static final double ON_GROUND_VERTICAL = 0.4d;
    /** KnockbackSync PlayerData gate: distanceToGround &lt;= 1.3. */
    public static final double GROUND_DISTANCE_MAX = 1.3d;
    /** Safety cap on gravity simulation so a bad sample cannot loop forever. */
    public static final int MAX_SIM_TICKS = 30;
    /** Attack cooldown that vanilla requires before a critical hit is possible. */
    public static final float CRIT_ATTACK_COOLDOWN = 0.9f;
    /** Vanilla airborne horizontal drag applied each tick after knockback. */
    public static final double HORIZONTAL_AIR_DRAG = 0.91d;

    private CombatPhysics() {
    }

    /**
     * Spike-aware compensated ping. On a spike we keep the previous sample (do not reward
     * lag-switches with extra compensation). Offset is then subtracted and the result is
     * clamped to {@code [1, MAX_COMPENSATION_MS]}.
     */
    public static double compensatedPingMs(double pingMs, double previousPingMs,
                                           int pingOffsetMs, int spikeThresholdMs) {
        double spikeSafe = (pingMs - previousPingMs > spikeThresholdMs) ? previousPingMs : pingMs;
        double compensated = spikeSafe - pingOffsetMs;
        if (compensated < 1.0d) {
            return 1.0d;
        }
        return Math.min(MAX_COMPENSATION_MS, compensated);
    }

    public static double compensatedPingMs(double pingMs, double previousPingMs) {
        return compensatedPingMs(pingMs, previousPingMs, PING_OFFSET_MS, SPIKE_THRESHOLD_MS);
    }

    /** @return true when the latest sample jumped by more than the spike threshold. */
    public static boolean isSpike(double pingMs, double previousPingMs, int spikeThresholdMs) {
        return pingMs - previousPingMs > spikeThresholdMs;
    }

    public static boolean isSpike(double pingMs, double previousPingMs) {
        return isSpike(pingMs, previousPingMs, SPIKE_THRESHOLD_MS);
    }

    public static int ticksFromMs(double milliseconds) {
        if (milliseconds <= 0.0d) {
            return 0;
        }
        int ticks = (int) Math.ceil(milliseconds / 50.0d);
        return Math.min(MAX_SIM_TICKS, Math.max(0, ticks));
    }

    /** Applies vanilla gravity+drag for {@code ticks} to a vertical velocity. */
    public static double applyGravity(double verticalVelocity, int ticks, double gravity) {
        double velocity = verticalVelocity;
        int steps = Math.min(MAX_SIM_TICKS, Math.max(0, ticks));
        for (int i = 0; i < steps; i++) {
            velocity = (velocity - gravity) * DRAG;
        }
        return velocity;
    }

    public static double applyGravity(double verticalVelocity, int ticks) {
        return applyGravity(verticalVelocity, ticks, GRAVITY);
    }

    /** Vertical displacement after {@code ticks} of vanilla gravity. */
    public static double distanceTraveled(double verticalVelocity, int ticks, double gravity) {
        double distance = 0.0d;
        double velocity = verticalVelocity;
        int steps = Math.min(MAX_SIM_TICKS, Math.max(0, ticks));
        for (int i = 0; i < steps; i++) {
            distance += velocity;
            velocity = (velocity - gravity) * DRAG;
        }
        return distance;
    }

    /**
     * Ticks until upward velocity becomes non-positive. {@code -1} means the simulation
     * hit {@link #MAX_SIM_TICKS} and the prediction is not safe to use.
     */
    public static int timeToApex(double verticalVelocity, double gravity) {
        if (verticalVelocity <= 0.0d) {
            return 0;
        }
        int ticks = 0;
        double velocity = verticalVelocity;
        while (velocity > 0.0d && ticks < MAX_SIM_TICKS) {
            velocity = (velocity - gravity) * DRAG;
            ticks++;
        }
        return ticks >= MAX_SIM_TICKS ? -1 : ticks;
    }

    /**
     * Ticks needed to cover {@code distance} blocks downward (or finish rising then fall).
     * {@code -1} if the simulation cannot land within {@link #MAX_SIM_TICKS}.
     */
    public static int fallTime(double verticalVelocity, double distance, double gravity) {
        if (distance < 0.0d) {
            return -1;
        }
        double traveled = 0.0d;
        double velocity = verticalVelocity;
        for (int ticks = 0; ticks < MAX_SIM_TICKS; ticks++) {
            if (traveled >= distance && velocity <= 0.0d) {
                return ticks;
            }
            traveled += -Math.min(0.0d, velocity) + Math.max(0.0d, velocity);
            // Track absolute vertical displacement from the start; landing is when we have
            // returned to (or below) the start plus the remaining ground distance.
            velocity = (velocity - gravity) * DRAG;
        }
        // Fallback: integrate net Y change until we have dropped `distance`.
        return fallTimeNet(verticalVelocity, distance, gravity);
    }

    private static int fallTimeNet(double verticalVelocity, double distance, double gravity) {
        double y = 0.0d;
        double velocity = verticalVelocity;
        for (int ticks = 1; ticks <= MAX_SIM_TICKS; ticks++) {
            y += velocity;
            velocity = (velocity - gravity) * DRAG;
            if (y <= -distance) {
                return ticks;
            }
        }
        return -1;
    }

    /**
     * Predicts whether the <em>client</em> currently considers the player on ground.
     * Used for victim knockback: the client is behind the server by ping, so a player
     * the server sees in the air may already have landed on the client (or vice versa).
     *
     * <p>Returns true only when the landing would already have happened within the
     * compensated ping window and the ground is close enough that the prediction is
     * trustworthy. Unsafe / too-long simulations return {@code false} (no compensation).</p>
     */
    public static boolean clientOnGround(double verticalVelocity, double distanceToGround,
                                         int compensatedTicks, double gravity) {
        if (distanceToGround > GROUND_DISTANCE_MAX || compensatedTicks <= 0) {
            return false;
        }
        int apex = timeToApex(verticalVelocity, gravity);
        if (apex < 0) {
            return false;
        }
        double peak = verticalVelocity > 0.0d
                ? distanceTraveled(verticalVelocity, apex, gravity)
                : 0.0d;
        int falling = fallTimeNet(verticalVelocity, peak + distanceToGround, gravity);
        if (falling < 0) {
            return false;
        }
        return (apex + falling) - compensatedTicks <= 0;
    }

    /**
     * Extra ticks of air-friction the high-ping client already simulated before the
     * velocity packet arrives. Applying them server-side stops the rubberband fling.
     */
    public static double compensatedHorizontal(double component, int compensatedTicks) {
        double value = component;
        int steps = Math.min(MAX_SIM_TICKS, Math.max(0, compensatedTicks));
        for (int i = 0; i < steps; i++) {
            value *= HORIZONTAL_AIR_DRAG;
        }
        return value;
    }

    /**
     * Vertical velocity the victim's client should receive so that, after ping delay and
     * gravity, it matches what a 0-ping player would already have.
     */
    public static double compensatedOffGroundVertical(double currentVertical, int compensatedTicks) {
        return applyGravity(currentVertical, compensatedTicks, GRAVITY);
    }

    /**
     * If the server applied on-ground knockback (Y ≁E0.4) but the client is airborne,
     * strip the extra vertical boost. Off-ground vanilla knockback leaves Y unchanged
     * (only X/Z are halved).
     */
    public static double toOffGroundVertical(double knockedVertical, double preKnockbackVertical) {
        if (knockedVertical >= ON_GROUND_VERTICAL - 0.02d) {
            return preKnockbackVertical;
        }
        return knockedVertical;
    }

    /**
     * Exponential moving average for ping smoothing. Alpha 0.35 reacts quickly enough
     * for real ping changes without treating single keepalive jitter as a spike.
     */
    public static double ema(double previous, double sample, double alpha) {
        return previous + alpha * (sample - previous);
    }

    /**
     * Idle gap that indicates the client froze and is about to dump a packet burst.
     * Gap is ping + spike threshold + one tick of slack.
     *
     * <p>When the <em>server</em> tick itself was long, elapsed time is not a client freeze  E
     * clamping that catch-up is what makes PvP stutter on low TPS.</p>
     */
    public static boolean isIdleGap(long elapsedNanos, double pingMs, int spikeThresholdMs) {
        return isIdleGap(elapsedNanos, pingMs, spikeThresholdMs, 50_000_000L);
    }

    public static boolean isIdleGap(long elapsedNanos, double pingMs, int spikeThresholdMs,
                                    long lastServerTickNanos) {
        if (lastServerTickNanos >= 55_000_000L) {
            return false;
        }
        long thresholdNanos = (long) ((pingMs + spikeThresholdMs + 50.0d) * 1_000_000.0d);
        return elapsedNanos > thresholdNanos;
    }

    /**
     * Maximum horizontal displacement we will accept in a single server tick. Burst
     * dumps after a freeze are clamped to this so the player is not rubber-banded into
     * the void, without allowing timer/speed cheats to fly across the arena.
     */
    public static double maxHorizontalDisplacement(double pingMs, boolean burst) {
        double sprintPerTick = 0.2873d; // vanilla sprint
        double knockbackSlack = 2.4d;
        double queuedTicks = Math.min(10.0d, Math.max(1.0d, pingMs / 50.0d));
        if (burst) {
            return sprintPerTick * queuedTicks + knockbackSlack;
        }
        return sprintPerTick * 3.0d + knockbackSlack;
    }

    /**
     * Rewind / forward sample index. {@code deltaTicks} positive = rewind (attacker view),
     * negative unused. Result is clamped to {@code [0, historySize-1]}.
     */
    public static int historyIndex(int writeIndex, int deltaTicks, int historySize) {
        if (historySize <= 0) {
            return 0;
        }
        int back = Math.min(historySize - 1, Math.max(0, deltaTicks));
        int idx = writeIndex - 1 - back;
        idx %= historySize;
        if (idx < 0) {
            idx += historySize;
        }
        return idx;
    }

    /**
     * Vanilla-style critical-hit predicate using a ping-rewound snapshot of the attacker.
     * Does not invent crits from ping alone: the attacker still has to be falling, not
     * sprinting, and past the cooldown gate.
     */
    public static boolean isClientCritical(
            boolean onGround,
            float fallDistance,
            boolean sprinting,
            boolean inWater,
            boolean climbing,
            boolean passenger,
            boolean blindness,
            float attackCooldown
    ) {
        return attackCooldown >= CRIT_ATTACK_COOLDOWN
                && fallDistance > 0.0f
                && !onGround
                && !sprinting
                && !inWater
                && !climbing
                && !passenger
                && !blindness;
    }
}
