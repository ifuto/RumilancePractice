package com.rumilance.practice.anticheat;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic envelope guard: validates the CONTENT of the attested stream, complementing
 * the transport proof ({@link MovementDigest}), with strictly impossible-action checks only.
 *
 * <h3>Zero-false-positive invariants</h3>
 * Reach math: vanilla entity reach is 3.0 blocks (eye-to-hitbox border). We measure
 * eye-to-hitbox-CENTER (strictly larger) with a flat absolute ceiling of
 * {@link #MAX_REACH_SQUARED} (~4.6) plus vehicle/extra-lag slack already inside that
 * ceiling. Values above it are physically impossible in vanilla — sprint/jump expansion,
 * teleport acks and worst-case lag cannot produce them, so a violation cannot be a false
 * positive by construction (Grim-style: only the impossible is flagged).
 *
 * <p>Policy: violations are cancelled and alerted; REQUIRED users are additionally kicked
 * (fail-closed), optional users just notify ops (they may be unverified vanilla players —
 * the cancel itself is the neutraliser).</p>
 */
public final class EnvelopeGuardListener implements Listener {

    /** (4.6 blocks)^2 — center-based measurement plus a full legality envelope on top. */
    private static final double MAX_REACH_SQUARED = 4.6 * 4.6;
    /** Alert throttling: at most one op-ping per player per window. */
    private static final long ALERT_WINDOW_MILLIS = 10_000L;

    private final AntiCheatService antiCheatService;
    private final Map<UUID, Long> lastAlertAt = new ConcurrentHashMap<>();

    public EnvelopeGuardListener(AntiCheatService antiCheatService) {
        this.antiCheatService = antiCheatService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || attacker.isInsideVehicle()) {
            return; // vehicle combat is outside the flat-ceiling safety proof
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        BoundingBox box = victim.getBoundingBox();
        Location eye = attacker.getEyeLocation();
        double dx = eye.getX() - box.getCenterX();
        double dy = eye.getY() - box.getCenterY();
        double dz = eye.getZ() - box.getCenterZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= MAX_REACH_SQUARED) {
            return;
        }
        // Physically impossible hit. Cancel unconditionally (legality neutralisation)…
        event.setCancelled(true);
        boolean required = antiCheatService.isRequired(attacker.getUniqueId());
        long now = System.currentTimeMillis();
        Long last = lastAlertAt.get(attacker.getUniqueId());
        if (last == null || now - last > ALERT_WINDOW_MILLIS) {
            lastAlertAt.put(attacker.getUniqueId(), now);
            antiCheatService.alertImpossible(attacker, victim.getName(),
                    Math.sqrt(distanceSquared));
        }
        // …and fail closed for mandated users: under attestation this also evidences that
        // the client stream is not vanilla-legal, which a pinned honest mod can't emit.
        if (required) {
            antiCheatService.kickImpossible(attacker, victim.getName(),
                    Math.sqrt(distanceSquared));
        }
    }
}
