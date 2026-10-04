package com.rumilance.practice.combat;

import com.rumilance.practice.kb.StagedKnockback;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the operator-tuned knockback coefficients ({@link KnockbackTuning}) on top of Paper.
 *
 * <p><b>Two layers:</b></p>
 * <ol>
 *   <li><b>Staged profiles (kb-probe 0.7.0)</b> — when the victim's live match KB profile is a
 *   staged export, the melee knockback is REBUILT with the full fitted physics model
 *   ({@link StagedKnockback}) instead of being scaled: stage 1 = base hit, stage 2 =
 *   sprint/Knockback-enchant (vanilla fires one knockback event per stage, synchronously, so a
 *   second melee event for the same victim within the i-frame window is the extra stage; the
 *   vanilla {@code k = sprintBonus + level} is recovered from the event's own delta and split
 *   using the attacker's held {@code knockback} enchant level from the damage context).
 *   Only {@code ENTITY_ATTACK}/{@code SWEEP_ATTACK} are reshaped (what the probe measures);
 *   every other cause — explosions, pushes, projectiles — passes untouched. Sweeps are treated
 *   as a single base stage (vanilla sweeps never add the sprint/enchant stage).</li>
 *   <li><b>Multiplier profiles + tuning</b> — the classic path: the effective factor comes from
 *   the victim's live match KB profile first, then the victim's kit, then the knockback
 *   {@code Cause}, then the global value. The knockback FORMULA itself is never rewritten —
 *   the listener rebuilds the would-be final velocity ({@code current + delta}) and multiplies
 *   THAT by the factors, so a sub-1.0 factor also tames the victim's own motion at the hit
 *   moment (the feel this server shipped with).</li>
 * </ol>
 *
 * <p><b>Which event:</b> Paper's modern {@code io.papermc.paper.event.entity.EntityKnockbackEvent}
 * — the 1.21.11 NMS funnels EVERY knockback (melee {@code ENTITY_ATTACK}, sweep,
 * {@code SHIELD_BLOCK}, {@code EXPLOSION}, {@code DAMAGE}, {@code PUSH}) through
 * {@code CraftEventFactory.callEntityKnockbackEvent}. Priority HIGH so cancellers and other
 * shaping plugins run first; fully-neutral configs short-circuit at zero cost.</p>
 */
public final class KnockbackTuningListener implements Listener {

    /** The two vanilla melee knockback stages fire in the same tick; i-frames keep any real
     *  follow-up hit ≥ 500ms away, so a generous-but-bounded window is collision-free. */
    private static final long STAGE_WINDOW_NANOS = 250_000_000L;

    private final KnockbackTuning tuning;
    /** Resolves the victim's current kit id (duel kit, FFA arena kit), or {@code null}. */
    private final java.util.function.Function<java.util.UUID, String> kitResolver;
    /**
     * Live per-match KB profile (Duel Request の KB 選択/既定プロファイル) —
     * a {@code double[]{horizontal, vertical}} multiplicative factor, or {@code null}
     * for "no profile layer" (classic kit > cause > global precedence).
     */
    private volatile java.util.function.Function<java.util.UUID, double[]> liveProfileResolver;
    /**
     * Live per-match STAGED profile (kb-probe 0.7.0 export) — non-null reshapes the melee
     * knockback entirely ({@link StagedKnockback}); consulted before the multiplier layer.
     */
    private volatile java.util.function.Function<java.util.UUID, StagedKnockback> stagedProfileResolver;
    /** victim → time of the last stage-1 melee reshaping (detects the same-tick stage 2). */
    private final Map<UUID, Long> stageOneAt = new ConcurrentHashMap<>();

    public KnockbackTuningListener(KnockbackTuning tuning,
                                   java.util.function.Function<java.util.UUID, String> kitResolver) {
        this.tuning = tuning;
        this.kitResolver = kitResolver;
    }

    public void setLiveProfileResolver(
            java.util.function.Function<java.util.UUID, double[]> liveProfileResolver) {
        this.liveProfileResolver = liveProfileResolver;
    }

    public void setStagedProfileResolver(
            java.util.function.Function<java.util.UUID, StagedKnockback> stagedProfileResolver) {
        this.stagedProfileResolver = stagedProfileResolver;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (tuning.isNeutral() && liveProfileResolver == null && stagedProfileResolver == null) {
            return; // nothing configured anywhere → vanilla pass-through, zero cost
        }
        if (!(event.getEntity() instanceof Player player)) {
            return; // 練習用ボットなど非プレイヤーは既存挙動のまま
        }
        StagedKnockback staged = stagedProfileResolver == null
                ? null : stagedProfileResolver.apply(player.getUniqueId());
        if (staged != null) {
            applyStaged(event, player, staged);
            return;
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

    /** Rebuilds one melee knockback stage with the staged model (kb-probe 0.7.0 semantics). */
    private void applyStaged(EntityKnockbackEvent event, Player player, StagedKnockback staged) {
        String cause = event.getCause() != null ? event.getCause().name() : "UNKNOWN";
        boolean sweep = "SWEEP_ATTACK".equals(cause);
        if (!"ENTITY_ATTACK".equals(cause) && !sweep) {
            return; // staged profiles reproduce MELEE knockback; everything else stays vanilla
        }
        Vector current = player.getVelocity();
        Vector delta = event.getKnockback();
        double[] stage = StagedKnockback.decomposeStage(
                current.getX(), current.getZ(), delta.getX(), delta.getZ());
        long now = System.nanoTime();
        if (stage == null) {
            stageOneAt.remove(player.getUniqueId());
            return; // ~zero impulse — nothing to reshape
        }
        double dirX = stage[0];
        double dirZ = stage[1];
        double vanillaStrength = stage[2];
        double resistScale = knockbackResistanceScale(player);
        boolean grounded = player.isOnGround();
        UUID id = player.getUniqueId();
        Long stageOne = stageOneAt.remove(id);
        boolean extraStage = !sweep && stageOne != null && now - stageOne <= STAGE_WINDOW_NANOS;

        double[] out;
        if (extraStage) {
            // Recover k = sprintBonus + Knockback level from the vanilla delta, then split it
            // with the attacker's held enchant level (the damage context IS the melee attack).
            int level = 0;
            boolean knownAttacker = false;
            boolean sprint = false;
            try {
                if (player.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ede
                        && ede.getDamager() instanceof Player attacker) {
                    knownAttacker = true;
                    level = knockbackEnchantLevel(attacker);
                    double k = StagedKnockback.vanillaKFromStrength(vanillaStrength, resistScale);
                    sprint = k - level >= 0.5d; // vanilla sprint bonus is exactly +1.0
                }
            } catch (RuntimeException ignored) {
                // fall through with the safe defaults below
            }
            if (!knownAttacker) {
                return; // cannot split k — leave the vanilla stage 2 untouched
            }
            double extraHBase = (sprint ? staged.extraHorizontal() : 0.0d)
                    + level * staged.knockbackEnchant();
            double extraVBase = (sprint ? staged.extraVertical() : 0.0d)
                    + level * staged.knockbackEnchant();
            out = staged.stageExtra(current.getX(), current.getY(), current.getZ(),
                    dirX, dirZ, resistScale, grounded, extraHBase, extraVBase);
        } else {
            stageOneAt.put(id, now);
            out = staged.stageBase(current.getX(), current.getY(), current.getZ(),
                    dirX, dirZ, resistScale, grounded);
        }
        event.setKnockback(new Vector(
                out[0] - current.getX(),
                out[1] - current.getY(),
                out[2] - current.getZ()));
    }

    /** Vanilla: strength scales with {@code 1 - clamp01(KNOCKBACK_RESISTANCE)}. */
    private static double knockbackResistanceScale(Player victim) {
        try {
            org.bukkit.attribute.AttributeInstance attr =
                    victim.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
            double resist = attr == null ? 0.0d : attr.getValue();
            return 1.0d - Math.max(0.0d, Math.min(1.0d, resist));
        } catch (RuntimeException e) {
            return 1.0d;
        }
    }

    private static int knockbackEnchantLevel(Player attacker) {
        try {
            org.bukkit.enchantments.Enchantment kb = org.bukkit.Registry.ENCHANTMENT
                    .get(org.bukkit.NamespacedKey.minecraft("knockback"));
            if (kb == null) {
                return 0;
            }
            return Math.max(0, attacker.getInventory().getItemInMainHand().getEnchantmentLevel(kb));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
