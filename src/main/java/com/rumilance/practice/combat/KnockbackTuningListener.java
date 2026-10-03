package com.rumilance.practice.combat;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

/**
 * Applies the operator-tuned knockback coefficients ({@link KnockbackTuning}) on top of Paper.
 *
 * <p><b>The knockback calculation itself is never rewritten.</b> Paper already computed the
 * final vector from the vanilla rules — attacker strength (sprint/Knockback enchant), the
 * victim's {@code KNOCKBACK_RESISTANCE} attribute (netherite), explosion knockback resistance,
 * current velocity and the grounded hop — this listener only multiplies that result. All
 * knockback-reducing effects therefore stay perfectly in play.</p>
 *
 * <p><b>Which event:</b> Paper's modern {@code io.papermc.paper.event.entity.EntityKnockbackEvent}
 * — the 1.21.11 NMS (LivingEntity#knockback and ServerExplosion) funnels EVERY knockback
 * (melee {@code ENTITY_ATTACK}, sweep, {@code SHIELD_BLOCK}, {@code EXPLOSION} incl. beds/
 * crystals/TNT, {@code DAMAGE}, {@code PUSH}) through {@code CraftEventFactory
 * .callEntityKnockbackEvent}, which fires the deprecated {@code org.bukkit.event.entity}
 * pair first and then this event. We listen here because the old Bukkit classes are
 * {@code @Deprecated(forRemoval = true)} in paper-api 1.21.11 itself and are scheduled for
 * removal; the modern class is their replacement and covers the identical event set (the
 * by-entity subclass shares the same handler list).</p>
 *
 * <p><b>Semantics (deliberate, preserved from the previous implementation):</b> the modern
 * event's {@code getKnockback()} is the DELTA that NMS will {@code add} to the current motion,
 * so the listener rebuilds the would-be final velocity ({@code current + delta} — the exact
 * value the legacy event called "final knockback") and multiplies THAT by the factors. Scaling
 * the final velocity instead of the bare impulse means a sub-1.0 factor also tames the victim's
 * own motion at the hit moment (a comboed player is pushed less), which is the feel this server
 * shipped with. Callers wanting pure impulse scaling would scale {@code getKnockback()}
 * directly instead.</p>
 *
 * <p>Profiles (KBM-style): the effective factor comes from the victim's live match KB profile
 * (Duel Request の KB 選択 / 既定) first, then the victim's kit, then the knockback
 * {@code Cause}, then the global value — a boxing duel can be tuned differently from a
 * nodebuff one without any math being replaced. Priority HIGH so cancellers and other shaping
 * plugins run first; fully-neutral configs short-circuit at zero cost.</p>
 */
public final class KnockbackTuningListener implements Listener {

    private final KnockbackTuning tuning;
    /** Resolves the victim's current kit id (duel kit, FFA arena kit), or {@code null}. */
    private final java.util.function.Function<java.util.UUID, String> kitResolver;
    /**
     * Live per-match KB profile (Duel Request の KB 選択/既定プロファイル) —
     * a {@code double[]{horizontal, vertical}} multiplicative factor, or {@code null}
     * for "no profile layer" (classic kit > cause > global precedence).
     */
    private volatile java.util.function.Function<java.util.UUID, double[]> liveProfileResolver;

    public KnockbackTuningListener(KnockbackTuning tuning,
                                   java.util.function.Function<java.util.UUID, String> kitResolver) {
        this.tuning = tuning;
        this.kitResolver = kitResolver;
    }

    public void setLiveProfileResolver(
            java.util.function.Function<java.util.UUID, double[]> liveProfileResolver) {
        this.liveProfileResolver = liveProfileResolver;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (tuning.isNeutral() && liveProfileResolver == null) {
            return; // nothing configured anywhere → vanilla pass-through, zero cost
        }
        if (!(event.getEntity() instanceof Player player)) {
            return; // 練習用ボットなど非プレイヤーは既存挙動のまま
        }
        // Factor precedence: live match profile > kit > cause > global (1.0/1.0 = vanilla).
        double horizontal;
        double vertical;
        java.util.function.Function<java.util.UUID, double[]> resolverFn = liveProfileResolver;
        double[] live = resolverFn == null ? null : resolverFn.apply(player.getUniqueId());
        if (live != null) {
            horizontal = live[0];
            vertical = live[1];
        } else {
            String cause = event.getCause() != null ? event.getCause().name() : "UNKNOWN";
            String kit = kitResolver != null ? kitResolver.apply(player.getUniqueId()) : null;
            KnockbackTuning.Factor factor = tuning.effective(cause, kit);
            horizontal = factor.horizontal();
            vertical = factor.vertical();
        }
        if (horizontal == KnockbackTuning.NEUTRAL && vertical == KnockbackTuning.NEUTRAL) {
            return;
        }
        // Rebuild the legacy "final knockback" (current motion + impulse) and scale that;
        // hand the scaled DELTA back — this is exactly what setFinalKnockback did upstream.
        Vector current = player.getVelocity();
        Vector delta = event.getKnockback();
        event.setKnockback(new Vector(
                (current.getX() + delta.getX()) * horizontal - current.getX(),
                (current.getY() + delta.getY()) * vertical - current.getY(),
                (current.getZ() + delta.getZ()) * horizontal - current.getZ()));
    }
}
