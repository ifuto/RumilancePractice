package com.rumilance.practice.match;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-match combat statistics for the post-fight report card. Tracks damage, hits, critical
 * hits, projectiles and the current/best combo for every participant of a match. The tracker
 * is keyed by match id so multiple concurrent matches never mix up their numbers, and is
 * cleared when the match is cleaned up.
 *
 * <p>All counters are mutated only from the main thread (damage events fire there), but the
 * maps are concurrent so the report GUI can read them safely from any thread.</p>
 */
public final class MatchCombatTracker {

    /** Rolling combo window: hits within this many milliseconds of the previous one extend the combo. */
    private static final long COMBO_WINDOW_MS = 2500L;

    /** matchId -> (playerId -> stats). */
    private final Map<UUID, Map<UUID, CombatStats>> byMatch = new ConcurrentHashMap<>();

    /** Clears every statistic for a finished match. Called by {@link MatchService#cleanupSession}. */
    public void clear(UUID matchId) {
        byMatch.remove(matchId);
    }

    /** @return an unmodifiable snapshot of per-player stats for the match, or empty if unknown. */
    public java.util.Optional<Map<UUID, CombatStats>> matchStats(UUID matchId) {
        Map<UUID, CombatStats> stats = byMatch.get(matchId);
        return stats == null ? java.util.Optional.empty()
                : java.util.Optional.of(java.util.Collections.unmodifiableMap(stats));
    }

    public CombatStats forParticipant(UUID matchId, UUID playerId) {
        return byMatch
                .computeIfAbsent(matchId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new CombatStats());
    }

    /**
     * Records a melee hit dealt by {@code attacker} to {@code victim}. The {@code finalDamage} is
     * the damage actually applied (after armour/enchants); {@code crit} flags a vanilla critical hit.
     */
    public void recordHit(UUID matchId, UUID attacker, UUID victim, double finalDamage, boolean crit) {
        CombatStats attackerStats = forParticipant(matchId, attacker);
        attackerStats.damageDealt.addAndGet((int) Math.round(finalDamage));
        attackerStats.hits.incrementAndGet();
        if (crit) {
            attackerStats.crits.incrementAndGet();
        }
        bumpCombo(attackerStats);

        CombatStats victimStats = forParticipant(matchId, victim);
        victimStats.damageTaken.addAndGet((int) Math.round(finalDamage));
        victimStats.lastHitAt = System.currentTimeMillis();
    }

    /** Records a projectile (bow/other) hit. Counts both as a hit and as a projectile hit. */
    public void recordProjectileHit(UUID matchId, UUID attacker, UUID victim, double finalDamage) {
        CombatStats attackerStats = forParticipant(matchId, attacker);
        attackerStats.damageDealt.addAndGet((int) Math.round(finalDamage));
        attackerStats.hits.incrementAndGet();
        attackerStats.projectileHits.incrementAndGet();
        bumpCombo(attackerStats);

        CombatStats victimStats = forParticipant(matchId, victim);
        victimStats.damageTaken.addAndGet((int) Math.round(finalDamage));
        victimStats.lastHitAt = System.currentTimeMillis();
    }

    private void bumpCombo(CombatStats stats) {
        long now = System.currentTimeMillis();
        if (now - stats.lastHitAt <= COMBO_WINDOW_MS) {
            int current = stats.currentCombo.incrementAndGet();
            stats.bestCombo.accumulateAndGet(current, Math::max);
        } else {
            stats.currentCombo.set(1);
            stats.bestCombo.accumulateAndGet(1, Math::max);
        }
        stats.lastHitAt = now;
    }

    /** Mutable per-player counters held inside the tracker; read via {@link #snapshot()}. */
    public static final class CombatStats {
        final AtomicInteger damageDealt = new AtomicInteger();
        final AtomicInteger damageTaken = new AtomicInteger();
        final AtomicInteger hits = new AtomicInteger();
        final AtomicInteger crits = new AtomicInteger();
        final AtomicInteger projectileHits = new AtomicInteger();
        final AtomicInteger currentCombo = new AtomicInteger();
        final AtomicInteger bestCombo = new AtomicInteger();
        volatile long lastHitAt;

        public int damageDealt() {
            return damageDealt.get();
        }

        public int damageTaken() {
            return damageTaken.get();
        }

        public int hits() {
            return hits.get();
        }

        public int crits() {
            return crits.get();
        }

        public int projectileHits() {
            return projectileHits.get();
        }

        public int bestCombo() {
            return bestCombo.get();
        }
    }
}
