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
 * knockback-reducing effects therefore stay perfectly in play, exactly as if the server were
 * running an alternative "kb multiplier" profile.</p>
 *
 * <p>Runs at {@link EventPriority#HIGH} so cancellers (LOW/NORMAL, cancelled events skipped)
 * and other shaping plugins run first; with neutral factors (the default) it is a pure no-op.</p>
 */
public final class KnockbackTuningListener implements Listener {

    private final KnockbackTuning tuning;

    public KnockbackTuningListener(KnockbackTuning tuning) {
        this.tuning = tuning;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (tuning.isNeutral()) {
            return; // 1.0 / 1.0 — vanilla pass-through, zero cost
        }
        if (!(event.getEntity() instanceof Player)) {
            return; // 練習用ボットなど非プレイヤーの扱いは既存挙動のまま
        }
        Vector finalKnockback = event.getFinalKnockback();
        double[] scaled = tuning.scale(finalKnockback.getX(), finalKnockback.getY(), finalKnockback.getZ());
        event.setFinalKnockback(new Vector(scaled[0], scaled[1], scaled[2]));
    }
}
