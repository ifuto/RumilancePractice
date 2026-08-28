package com.rumilance.practice.match;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-match combat statistics for the post-fight report card. Tracks damage, hits, critical
 * hits, projectiles and the current/best combo for every participant of a match.
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
        bumpMeleeCombo(attackerStats, victim);

        CombatStats victimStats = forParticipant(matchId, victim);
        victimStats.damageTaken.addAndGet((int) Math.round(finalDamage));
        victimStats.breakCombo();
        victimStats.lastHitAt = System.currentTimeMillis();
    }

    /**
     * Records a projectile (bow/other) hit. Counts as a hit and projectile hit but does
     * <strong>not</strong> extend the melee combo chain.
     */
    public void recordProjectileHit(UUID matchId, UUID attacker, UUID victim, double finalDamage) {
        CombatStats attackerStats = forParticipant(matchId, attacker);
        attackerStats.damageDealt.addAndGet((int) Math.round(finalDamage));
        attackerStats.hits.incrementAndGet();
        attackerStats.projectileHits.incrementAndGet();
        // Projectile hits do not extend or break the melee combo window timing for the attacker;
        // they also do not increment currentCombo.
        attackerStats.lastHitAt = System.currentTimeMillis();

        CombatStats victimStats = forParticipant(matchId, victim);
        victimStats.damageTaken.addAndGet((int) Math.round(finalDamage));
        victimStats.breakCombo();
        victimStats.lastHitAt = System.currentTimeMillis();
    }

    private void bumpMeleeCombo(CombatStats stats, UUID victim) {
        long now = System.currentTimeMillis();
        UUID previousTarget = stats.comboTarget.get();
        boolean sameTarget = previousTarget != null && previousTarget.equals(victim);
        boolean inWindow = now - stats.lastHitAt <= COMBO_WINDOW_MS;
        if (sameTarget && inWindow) {
            int current = stats.currentCombo.incrementAndGet();
            stats.bestCombo.accumulateAndGet(current, Math::max);
        } else {
            stats.currentCombo.set(1);
            stats.bestCombo.accumulateAndGet(1, Math::max);
        }
        stats.comboTarget.set(victim);
        stats.lastHitAt = now;
    }

    /** Mutable per-player counters held inside the tracker; read via accessors. */
    public static final class CombatStats {
        final AtomicInteger damageDealt = new AtomicInteger();
        final AtomicInteger damageTaken = new AtomicInteger();
        final AtomicInteger hits = new AtomicInteger();
        final AtomicInteger crits = new AtomicInteger();
        final AtomicInteger projectileHits = new AtomicInteger();
        final AtomicInteger currentCombo = new AtomicInteger();
        final AtomicInteger bestCombo = new AtomicInteger();
        final AtomicReference<UUID> comboTarget = new AtomicReference<>();
        volatile long lastHitAt;

        void breakCombo() {
            currentCombo.set(0);
            comboTarget.set(null);
        }

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

        public int currentCombo() {
            return currentCombo.get();
        }

        public int bestCombo() {
            return bestCombo.get();
        }
    }
}
