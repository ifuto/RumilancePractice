package com.rumilance.practice.kb;

/**
 * One kb-probe 0.7.0 "staged-knockback" profile — the full fitted parameter set the
 * measurement mod exports per target server. When such a profile is live for a fight,
 * the melee knockback events are REBUILT with these constants instead of only scaling
 * vanilla's result (the legacy {@code horizontal}/{@code vertical} multiplier files).
 *
 * <p>The model (decompiled from kb-probe 0.7.0 {@code KbModel#knockback}, verified against
 * vanilla {@code LivingEntity#takeKnockback}): every melee hit runs up to two
 * {@code knockback(strength)} stages. With {@code r} = the victim's knockback resistance
 * (0..1), {@code resist = 1 - r}, {@code airH} = grounded ? 1 : {@code airHorizontalMultiplier}:</p>
 *
 * <pre>
 * stage 1 (base, vanilla strength 0.4·resist):
 *   h = current·frictionHorizontal + dir · horizontal · resist · airH
 *   y = grounded ? min(verticalLimit, currentY·frictionVertical + vertical·resist)
 *                : currentY + vertical·resist·airVerticalMultiplier
 * stage 2 (sprint / Knockback enchant, vanilla strength k·0.5·resist) — exists iff the
 * attacker sprinted (charged) or their weapon has Knockback:
 *   extraH = (sprint ? extraHorizontal : 0 + level·knockbackEnchant) · resist · airH
 *   extraV = (sprint ? extraVertical  : 0 + level·knockbackEnchant) · resist
 *   extraReappliesFriction=true:  h = h·frictionHorizontal + dir·extraH
 *                                 y = grounded ? min(verticalLimit, y·frictionVertical + extraV)
 *                                              : y + extraV·airVerticalMultiplier
 *   extraReappliesFriction=false: h = h + dir·extraH;  y = grounded ? y + extraV : y + extraV·airVerticalMultiplier
 * </pre>
 *
 * <p>Vanilla 1.21.x is reproduced exactly by
 * {@code (0.4, 0.4, 0.4, 0.5, 0.5, 0.5, 0.5, 1.0, 0.0, 0.5, reapply=true)} — the probe's own
 * regression test fits those values back out of vanilla samples. {@code attackerSlowdown},
 * {@code hitDelay} and {@code gravity} in the export are measurement-side corrections and are
 * deliberately ignored here.</p>
 *
 * <p>Pure JDK (local-test runnable); no Bukkit types — velocities travel as {@code double[x,y,z]}.</p>
 */
public record StagedKnockback(
        double horizontal, double vertical, double verticalLimit,
        double extraHorizontal, double extraVertical,
        double frictionHorizontal, double frictionVertical,
        double airHorizontalMultiplier, double airVerticalMultiplier,
        double knockbackEnchant, boolean extraReappliesFriction) {

    /** The probe's fitted bounds: magnitudes 0..2.5, frictions 0..1 (KbModel$Params LOW/HIGH). */
    public static final double MAG_MAX = 2.5d;
    public static final double FRICTION_MAX = 1.0d;

    /** Vanilla 1.21.x knockback exactly — the fit must return these for a vanilla server. */
    public static final StagedKnockback VANILLA = new StagedKnockback(
            0.4d, 0.4d, 0.4d, 0.5d, 0.5d, 0.5d, 0.5d, 1.0d, 0.0d, 0.5d, true);

    public StagedKnockback {
        horizontal = clamp(horizontal, 0.0d, MAG_MAX);
        vertical = clamp(vertical, 0.0d, MAG_MAX);
        verticalLimit = clamp(verticalLimit, 0.0d, MAG_MAX);
        extraHorizontal = clamp(extraHorizontal, 0.0d, MAG_MAX);
        extraVertical = clamp(extraVertical, 0.0d, MAG_MAX);
        frictionHorizontal = clamp(frictionHorizontal, 0.0d, FRICTION_MAX);
        frictionVertical = clamp(frictionVertical, 0.0d, FRICTION_MAX);
        airHorizontalMultiplier = clamp(airHorizontalMultiplier, 0.0d, MAG_MAX);
        airVerticalMultiplier = clamp(airVerticalMultiplier, 0.0d, MAG_MAX);
        knockbackEnchant = clamp(knockbackEnchant, 0.0d, MAG_MAX);
    }

    private static double clamp(double v, double low, double high) {
        if (Double.isNaN(v)) {
            return low;
        }
        return Math.max(low, Math.min(high, v));
    }

    /**
     * Recovers the vanilla push vector {@code dir · strength} of one knockback stage from the
     * event's (current, delta): vanilla always halves the horizontal current
     * ({@code new = current/2 - dir·s}), so {@code dir·s = -delta - current/2}.
     *
     * @return {@code {dirX, dirZ, vanillaStrength}} or {@code null} for a ~zero impulse
     *         (nothing to reshape).
     */
    public static double[] decomposeStage(double currentX, double currentZ,
                                          double deltaX, double deltaZ) {
        double qx = -deltaX - 0.5d * currentX;
        double qz = -deltaZ - 0.5d * currentZ;
        double m = Math.hypot(qx, qz);
        if (m < 1.0E-7d) {
            return null;
        }
        return new double[]{qx / m, qz / m, m};
    }

    /** Recovers the vanilla stage-2 {@code k} (= sprintBonus + Knockback level) from the
     *  decomposed strength: vanilla stage 2 strength = k · 0.5 · resist. */
    public static double vanillaKFromStrength(double strength, double resistScale) {
        if (resistScale <= 1.0E-9d) {
            return 0.0d;
        }
        return strength / (0.5d * resistScale);
    }

    /** Stage 1 (base): rebuilds the full post-hit velocity from the victim's current one. */
    public double[] stageBase(double cx, double cy, double cz, double dirX, double dirZ,
                              double resistScale, boolean grounded) {
        double airH = grounded ? 1.0d : airHorizontalMultiplier;
        double nx = cx * frictionHorizontal + dirX * horizontal * resistScale * airH;
        double nz = cz * frictionHorizontal + dirZ * horizontal * resistScale * airH;
        double ny;
        if (grounded) {
            ny = Math.min(verticalLimit, cy * frictionVertical + vertical * resistScale);
        } else {
            ny = cy + vertical * resistScale * airVerticalMultiplier;
        }
        return new double[]{nx, ny, nz};
    }

    /**
     * Stage 2 (sprint / Knockback enchant): rebuilds on top of the stage-1 velocity.
     * {@code extraHBase}/{@code extraVBase} = {@code (sprint ? extra* : 0) + level · knockbackEnchant}.
     */
    public double[] stageExtra(double cx, double cy, double cz, double dirX, double dirZ,
                               double resistScale, boolean grounded,
                               double extraHBase, double extraVBase) {
        double airH = grounded ? 1.0d : airHorizontalMultiplier;
        double eh = extraHBase * resistScale * airH;
        double ev = extraVBase * resistScale;
        double nx;
        double ny;
        double nz;
        if (extraReappliesFriction) {
            nx = cx * frictionHorizontal + dirX * eh;
            nz = cz * frictionHorizontal + dirZ * eh;
            ny = grounded
                    ? Math.min(verticalLimit, cy * frictionVertical + ev)
                    : cy + ev * airVerticalMultiplier;
        } else {
            nx = cx + dirX * eh;
            nz = cz + dirZ * eh;
            ny = grounded ? cy + ev : cy + ev * airVerticalMultiplier;
        }
        return new double[]{nx, ny, nz};
    }
}
