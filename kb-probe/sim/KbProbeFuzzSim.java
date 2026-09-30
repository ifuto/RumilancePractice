package com.rumilance.kbprobe;

import java.util.Locale;
import java.util.Random;

/**
 * Mass-value fuzz verification of the KB Probe expectation formulas ("超大量の様々な値").
 *
 * <p>Two independently-written implementations face off here:</p>
 * <ul>
 *   <li><b>Vanilla pipeline replica</b> — re-implements vanilla 1.21.1's knockback pipeline
 *       literally (per-stage {@code newV = oldV/2 + strength*(1-r)} horizontal composition,
 *       grounded-only {@code min(0.4, vy/2 + strength*(1-r))} vertical, then the attack-stage
 *       {@code takeKnockback(k*0.5)} while still grounded) and serialises the result the way
 *       the server does: {@code (int)(v*8000)} per component.</li>
 *   <li><b>The mod's formula</b> — {@link KbProbeMath#expectHorizontal} /
 *       {@link KbProbeMath#expectVertical}, fed through the mod's real guard chain (idle →
 *       direction → outlier → resistance → vertical plausibility) on the unscaled values.</li>
 * </ul>
 *
 * <p>For a stationary grounded victim the measured factor must equal 1 within wire-truncation
 * noise; the harness asserts that bound per case and prints mean/p99/max deviation. Slow-moving
 * victims stay inside a computable bound, moving/airborne/guarded cases must be REJECTED by
 * the guard chain. Seeded, so failures reproduce.</p>
 *
 * <p>Run (repo root, repo-local ECJ + JRE):</p>
 * <pre>
 *   JAVA="$(python3 -c 'import jdk4py, os; print(os.path.join(jdk4py.JAVA_HOME, "bin\\", "java\\"))')"
 *   mkdir -p /tmp/kbprobe-fuzz/classes
 *   "$JAVA" -jar tools/localtest/ecj.jar -17 -d /tmp/kbprobe-fuzz/classes \
 *       kb-probe/src/main/java/com/rumilance/kbprobe/KbProbeMath.java \
 *       kb-probe/src/main/java/com/rumilance/kbprobe/ServerStats.java \
 *       kb-probe/sim/KbProbeFuzzSim.java
 *   "$JAVA" -cp /tmp/kbprobe-fuzz/classes com.rumilance.kbprobe.KbProbeFuzzSim
 * </pre>
 */
public final class KbProbeFuzzSim {

    private static final long SEED = 20260930L;
    private static final int ITERATIONS = 200_000;
    /** Wire truncation: each component loses < 1/8000, hRaw error < 2/8000 worst case. */
    static final double WIRE_ERR_H = 2.0 / KbProbeMath.VELOCITY_PACKET_SCALE;

    private final Random rnd = new Random(SEED);
    private int failures;
    private int rejectedByGuard;
    private int hRecorded;
    private int vRecorded;
    private double maxDevH;
    private double maxDevV;
    private double sumDevH;
    private double sumDevV;
    private final double[] devsH = new double[ITERATIONS + 16];
    private final double[] devsV = new double[ITERATIONS + 16];
    private int devHCount;
    private int devVCount;
    /** Stationary-only deviations — the pure formula check (wire noise floor expected). */
    private double stillH;
    private double stillV;
    private double maxStillH;
    private double maxStillV;
    private int stillHCount;
    private int stillVCount;

    public static void main(String[] args) {
        KbProbeFuzzSim fuzz = new KbProbeFuzzSim();
        System.out.println("=== KB Probe 式ファズ検証 (seed=" + SEED + ", iterations=" + ITERATIONS + ") ===\n");
        fuzz.run();
        fuzz.report();
        if (fuzz.failures > 0) {
            System.out.println("\nVERDICT: FAILED (" + fuzz.failures + " failures)");
            System.exit(1);
        }
        System.out.println("\nVERDICT: ALL OK");
    }

    // ------------------------------------------------------------------ vanilla replica

    /**
     * Vanilla 1.21.1 takeKnockback pipeline (deobf sources). Two stages while the victim
     * still counts as grounded (same tick): stage 1 = damage() strength 0.4, stage 2 =
     * attack() strength k*0.5 when k>0. Returns {vx, vy, vz} AFTER both stages.
     * dirX/dirZ = unit vector attacker→victim (the away-push direction), r = knockback
     * resistance, onGround = victim grounded at damage-application tick.
     */
    static double[] vanillaKnockback(double vx, double vy, double vz,
                                     double dirX, double dirZ, double r,
                                     double k, boolean onGround) {
        // stage closures: strength>0 required (vanilla: strength == 0 → untouched)
        double s1 = (KbProbeMath.BASE) * (1.0 - r);
        if (s1 > 0.0) {
            vx = vx / 2.0 + s1 * dirX;
            vz = vz / 2.0 + s1 * dirZ;
            if (onGround) {
                vy = Math.min(KbProbeMath.BASE, vy / 2.0 + s1);
            }
        }
        if (k > 0.0) {
            double s2 = k * 0.5 * (1.0 - r);
            if (s2 > 0.0) {
                vx = vx / 2.0 + s2 * dirX;
                vz = vz / 2.0 + s2 * dirZ;
                if (onGround) {
                    vy = Math.min(KbProbeMath.BASE, vy / 2.0 + s2);
                }
            }
        }
        return new double[] {vx, vy, vz};
    }

    /** Vanilla sender side: EntityVelocityUpdateS2CPacket carries int values. */
    static int wire(double v) {
        return (int) (v * KbProbeMath.VELOCITY_PACKET_SCALE);
    }

    // ------------------------------------------------------------------ the fuzz

    private void run() {
        for (int i = 0; i < ITERATIONS; i++) {
            runCase(i);
        }
        // Deterministic edge grid: the exact vanilla boundary values, one by one.
        edge(0.0, 0.0, true);
        edge(1.0, 0.0, true);
        edge(2.0, 0.4, true);   // KB II + full netherite
        edge(3.0, 0.9, true);
        edge(0.0, 0.99, true);  // tiny magnitudes still must resolve
        edge(1.0, 0.99, true);
        edge(0.0, -0.5, true);  // negative resistance (custom servers)
        edge(0.0, 0.0, false);  // airborne: no vertical
        edge(2.0, 0.4, false);
    }

    private void runCase(int index) {
        double angle = rnd.nextDouble() * 2.0 * Math.PI;
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double k = pickK();
        double r = pickR();
        boolean onGround = rnd.nextInt(4) != 0; // 75% grounded
        // 0 = stationary, 1 = slow (< idle thresholds), 2 = clearly moving
        int motion = rnd.nextInt(10) < 6 ? 0 : (rnd.nextInt(2) + 1);
        double mvx = 0, mvy = 0, mvz = 0;
        if (motion == 1) {
            double a = rnd.nextDouble() * 2.0 * Math.PI;
            double sp = rnd.nextDouble() * KbProbeMath.IDLE_HORIZONTAL;
            mvx = Math.cos(a) * sp;
            mvz = Math.sin(a) * sp;
            mvy = (rnd.nextDouble() - 0.5) * 1.5 * KbProbeMath.IDLE_VERTICAL;
        } else if (motion == 2) {
            double a = rnd.nextDouble() * 2.0 * Math.PI;
            double sp = KbProbeMath.IDLE_HORIZONTAL * (1.2 + rnd.nextDouble() * 3.0);
            mvx = Math.cos(a) * sp;
            mvz = Math.sin(a) * sp;
        }
        drive(index, dirX, dirZ, k, r, onGround, mvx, mvy, mvz, motion == 2, false);
    }

    /** k = attack_knockback(0..2 vanilla items, custom up to 3) + sprint(+1): fuzz [0,3]. */
    private double pickK() {
        switch (rnd.nextInt(8)) {
            case 0: case 1: return 0.0;             // bare punch
            case 2: case 3: return 1.0;             // sprint hit
            case 4: return 2.0;                     // sprint + KB I
            case 5: return 3.0;                     // sprint + KB II
            default: return rnd.nextDouble() * 3.0; // arbitrary attribute totals
        }
    }

    private double pickR() {
        switch (rnd.nextInt(10)) {
            case 0: case 1: case 2: return 0.0;
            case 3: return 0.4;                     // full netherite
            case 4: return 0.99;                    // near-immune
            case 5: return rnd.nextDouble() * 0.06; // leather-iron era ~ small
            default: return rnd.nextDouble() * 0.95;
        }
    }

    private void edge(double k, double r, boolean onGround) {
        drive(-1, 1.0, 0.0, k, r, onGround, 0.0, 0.0, 0.0, false, false);
        double d = Math.sqrt(0.5);
        drive(-1, d, d, k, r, onGround, 0.0, 0.0, 0.0, false, false);
    }

    /** jump-into-hit dedicated fuzz block (called from main-run and report). */
    void runJumpMidWindowCases() {
        for (int i = 0; i < 10_000; i++) {
            double angle = rnd.nextDouble() * 2.0 * Math.PI;
            drive(i, Math.cos(angle), Math.sin(angle), pickK(), pickR(),
                    true /* clicked grounded */, 0.0, 0.0, 0.0, false,
                    true /* but airborne at damage tick */);
        }
    }

    /**
     * Drives ONE sample through: vanilla pipeline → wire serialisation → mod guard chain →
     * expectation comparison. {@code airborneAtDamageTick} models the victim leaving the
     * ground between the click and the server applying the damage.
     */
    private void drive(int index, double dirX, double dirZ, double k, double r,
                       boolean onGroundAtClick, double mvx, double mvy, double mvz,
                       boolean clearlyMoving, boolean airborneAtDamageTick) {
        boolean onGroundAtDamage = onGroundAtClick && !airborneAtDamageTick;
        double[] after = vanillaKnockback(mvx, mvy, mvz, dirX, dirZ, r, k, onGroundAtDamage);
        int rawX = wire(after[0]);
        int rawY = wire(after[1]);
        int rawZ = wire(after[2]);
        // Mod side (VelocityCaptureMixin + KbProbe.recordSample, same order):
        double pvx = KbProbeMath.unscaleVelocity(rawX);
        double pvy = KbProbeMath.unscaleVelocity(rawY);
        double pvz = KbProbeMath.unscaleVelocity(rawZ);
        double dx = pvx - mvx;
        double dy = pvy - mvy;
        double dz = pvz - mvz;

        // Guard 1: idle baseline — clearly-moving victims must be refused.
        if (!KbProbeMath.isIdle(mvx, mvy, mvz)) {
            if (!clearlyMoving) {
                fail(index, "idle guard rejected a victim the fuzz classified as stationary");
            }
            rejectedByGuard++;
            return;
        }
        if (clearlyMoving) {
            fail(index, "idle guard ACCEPTED a clearly-moving victim (v=" + mvx + "," + mvz + ")");
        }
        double hRaw = Math.hypot(dx, dz);
        if (!KbProbeMath.directionOk(dx, dz, dirX, dirZ, hRaw)) {
            // The direction guard is only allowed to fire on the no-op magnitude floor
            // (r≈1 makes the packet ≈ baseline) or under slow-movement contamination:
            // never on a clean, meaningful stationary push.
            double expectH = KbProbeMath.expectHorizontal(k, r);
            boolean cleanStationaryPush = Math.hypot(mvx, mvz) == 0.0 && mvy == 0.0
                    && expectH > 0.01 && onGroundAtDamage;
            if (cleanStationaryPush) {
                fail(index, String.format(Locale.ROOT,
                        "direction guard tripped on a legit push (k=%.2f r=%.4f h=%.5f)",
                        k, r, hRaw));
            }
            rejectedByGuard++;
            return;
        }
        if (KbProbeMath.outlier(hRaw, dy)) {
            fail(index, String.format(Locale.ROOT,
                    "outlier guard tripped on vanilla value (k=%.2f r=%.3f h=%.4f dy=%.4f)",
                    k, r, hRaw, dy));
        }
        if (r >= 1.0) {
            rejectedByGuard++;
            return;
        }

        double expectH = KbProbeMath.expectHorizontal(k, r);
        double fH = hRaw / expectH;
        // EXACT error envelope: run the vanilla replica twice — with and without the baseline
        // motion — so the clamp (min(0.4)) interplay with a non-zero baseline is captured
        // case-by-case instead of by a hand-waved constant. The stationary difference is
        // identically zero and reduces the bound to pure wire-truncation noise.
        double[] ideal0 = vanillaKnockback(0.0, 0.0, 0.0, dirX, dirZ, r, k, onGroundAtDamage);
        double[] idealM = vanillaKnockback(mvx, mvy, mvz, dirX, dirZ, r, k, onGroundAtDamage);
        double baseMixH = Math.abs(
                Math.hypot(idealM[0] - mvx, idealM[2] - mvz) - Math.hypot(ideal0[0], ideal0[2]));
        double tolH = (baseMixH + WIRE_ERR_H) / expectH + 1.0e-9;
        double devH = Math.abs(fH - 1.0);
        if (devH > tolH) {
            fail(index, String.format(Locale.ROOT,
                    "H factor off (k=%.3f r=%.4f ground=%b mv=%.4f): fH=%.6f tol=%.6f",
                    k, r, onGroundAtDamage, Math.hypot(mvx, mvz), fH, tolH));
        }
        hRecorded++;
        devsH[devHCount++] = devH;
        sumDevH += devH;
        maxDevH = Math.max(maxDevH, devH);
        if (Math.hypot(mvx, mvz) == 0.0 && mvy == 0.0) {
            stillH += devH;
            stillHCount++;
            maxStillH = Math.max(maxStillH, devH);
        }

        if (onGroundAtClick) {
            double expectV = KbProbeMath.expectVertical(k, r);
            if (expectV > 1.0e-4) {
                double fV = dy / expectV;
                if (airborneAtDamageTick || !onGroundAtDamage) {
                    // Jump mid-window: vanilla kept Y → dy≈0 → fV≈0 → plausible band MUST refuse.
                    if (KbProbeMath.verticalFactorPlausible(fV)) {
                        fail(index, "jump-mid-window fake vertical sample passed plausible band");
                    }
                    rejectedByGuard++;
                } else {
                    boolean cleanStationary = Math.hypot(mvx, mvz) == 0.0 && mvy == 0.0;
                    if (cleanStationary && !KbProbeMath.verticalFactorPlausible(fV)) {
                        fail(index, String.format(Locale.ROOT,
                                "plausible band rejected a legit STATIONARY vertical"
                                        + " (k=%.2f r=%.4f fV=%.5f)", k, r, fV));
                    }
                    // Plausible-band acceptance for slow movers is a guard decision, not a
                    // formula issue — assert bound only when the band accepted the sample.
                    if (!KbProbeMath.verticalFactorPlausible(fV)) {
                        rejectedByGuard++;
                    } else {
                    // Vertical mixing envelope from the ideal double-run (clamp-aware).
                    double baseMixV = Math.abs((idealM[1] - mvy) - ideal0[1]);
                    double tolV = (baseMixV + WIRE_ERR_H) / expectV + 1.0e-9;
                    double devV = Math.abs(fV - 1.0);
                    if (devV > tolV) {
                        fail(index, String.format(Locale.ROOT,
                                "V factor off (k=%.3f r=%.4f): fV=%.6f tol=%.6f", k, r, fV, tolV));
                    }
                    vRecorded++;
                    devsV[devVCount++] = devV;
                    sumDevV += devV;
                    maxDevV = Math.max(maxDevV, devV);
                    if (cleanStationary) {
                        stillV += devV;
                        stillVCount++;
                        maxStillV = Math.max(maxStillV, devV);
                    }
                    }
                }
            }
        }
    }

    private void report() {
        runJumpMidWindowCases();
        System.out.printf(Locale.ROOT,
                "採用 H=%d 件, V=%d 件, ガード棄却=%d 件 (moving/airborne/r≥1/noise/jump)%n",
                hRecorded, vRecorded, rejectedByGuard);
        System.out.printf(Locale.ROOT,
                "H 係数ずれ: mean=%.2e  p99=%.2e  max=%.2e%n",
                devHCount == 0 ? 0 : sumDevH / devHCount, p99(devsH, devHCount), maxDevH);
        System.out.printf(Locale.ROOT,
                "V 係数ずれ: mean=%.2e  p99=%.2e  max=%.2e%n",
                devVCount == 0 ? 0 : sumDevV / devVCount, p99(devsV, devVCount), maxDevV);
        System.out.printf(Locale.ROOT,
                "  うち完全静止サンプル (式検証の本体): H mean=%.2e max=%.2e (n=%d) / V mean=%.2e max=%.2e (n=%d)%n",
                stillHCount == 0 ? 0 : stillH / stillHCount, maxStillH, stillHCount,
                stillVCount == 0 ? 0 : stillV / stillVCount, maxStillV, stillVCount);
        System.out.println("  ※静止の max が wire 切捨てノイズ (≲1/8000/expect) 級であることを確認 — それ以上のずれは");
        System.out.println("    低速移動サンプル (旧速度の半減混合による理論誤差, mod 設計上の許容域) のみ");
        System.out.println("判定: 全ケースで |f-1| ≤ wire切捨て誤差+bound なら式は vanilla と一致");
    }

    private static double p99(double[] values, int count) {
        if (count == 0) {
            return 0.0;
        }
        double[] copy = java.util.Arrays.copyOf(values, count);
        java.util.Arrays.sort(copy);
        return copy[Math.min(count - 1, (int) Math.floor(count * 0.99))];
    }

    private void fail(int index, String message) {
        failures++;
        if (failures <= 20) {
            System.out.println("FAIL[" + (index < 0 ? "edge" : index) + "] " + message);
        }
    }

    private KbProbeFuzzSim() {
    }
}
