package com.rumilance.practice.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.util.Vector;

/**
 * Applies the operator-tuned knockback coefficients ({@link KnockbackTuning}) on top of Paper.
 *
 * <p><b>The knockback calculation itself is never rewritten.</b> Paper already computed the
 * final vector from the vanilla rules — attacker strength (sprint/Knockback enchant), the
 * victim's {@code KNOCKBACK_RESISTANCE} attribute (netherite), explosion knockback resistance,
 * current velocity and the grounded hop — this listener only multiplies that FINAL vector via
 * {@code EntityKnockbackEvent#getFinalKnockback()} → {@code setFinalKnockback(...)}. All
 * knockback-reducing effects therefore stay perfectly in play.</p>
 *
 * <p>Profiles (KBM-style): the effective factor comes from the victim's kit when a kit profile
 * is configured, then the knockback {@code Cause}, then the global value — being knocked back
 * by an explosion bed can be tuned differently from a melee swing, and a boxing duel from a
 * nodebuff one, without any math being replaced. Priority HIGH so cancellers and other
 * shaping plugins run first; fully-neutral configs short-circuit at zero cost.</p>
 */
public final class KnockbackTuningListener implements Listener {

    private final KnockbackTuning tuning;
    /** Resolves the victim's current kit id (duel kit, FFA arena kit), or {@code null}. */
    private final java.util.function.Function<java.util.UUID, String> kitResolver;

    public KnockbackTuningListener(KnockbackTuning tuning,
                                   java.util.function.Function<java.util.UUID, String> kitResolver) {
        this.tuning = tuning;
        this.kitResolver = kitResolver;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (tuning.isNeutral()) {
            return; // no profile configured anywhere → vanilla pass-through, zero cost
        }
        if (!(event.getEntity() instanceof Player player)) {
            return; // 練習用ボットなど非プレイヤーは既存挙動のまま
        }
        String cause = event.getCause() != null ? event.getCause().name() : "UNKNOWN";
        String kit = kitResolver != null ? kitResolver.apply(player.getUniqueId()) : null;
        if (tuning.isNeutralFor(cause, kit)) {
            return;
        }
        Vector finalKnockback = event.getFinalKnockback();
        double[] scaled = tuning.scale(cause, kit,
                finalKnockback.getX(), finalKnockback.getY(), finalKnockback.getZ());
        event.setFinalKnockback(new Vector(scaled[0], scaled[1], scaled[2]));
    }
}
