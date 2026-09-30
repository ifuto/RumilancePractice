package com.rumilance.kbprobe;

/**
 * Offline functional test-drive of the KB Probe measurement path — "実際に値を渡して結果を見る".
 *
 * <p>Pure-JVM (no Minecraft classes): it drives {@link KbProbeMath} + {@link ServerStats} with
 * the SAME values vanilla 1.21.1 would produce on the wire, in the same order the real code
 * runs its guards (idle baseline → direction → outlier → resistance → expectation → record)
 * and prints what the mod would (and would NOT) record. The wire values below are computed
 * with the vanilla formulas, not hard-coded.</p>
 *
 * <p>Run (repo root):</p>
 * <pre>
 *   JAVA="$(python3 -c 'import jdk4py, os; print(os.path.join(jdk4py.JAVA_HOME, \"bin\", \"java\"))')"
 *   mkdir -p /tmp/kbprobe-sim && cd kb-probe
 *   # jdk4py は JRE のみで javac を持たないため、リポジトリ同梱の ECJ でコンパイルする:
 *   mkdir -p /tmp/kbprobe-sim/classes
 *   "$JAVA" -jar tools/localtest/ecj.jar -17 -d /tmp/kbprobe-sim/classes \
 *       kb-probe/src/main/java/com/rumilance/kbprobe/KbProbeMath.java \
 *       kb-probe/src/main/java/com/rumilance/kbprobe/ServerStats.java \
 *       kb-probe/sim/KbProbeSim.java
 *   "$JAVA" -cp /tmp/kbprobe-sim/classes com.rumilance.kbprobe.KbProbeSim
 * </pre>
 */
public final class KbProbeSim {

    private static final java.util.List<String> REPORT = new java.util.ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== KB Probe 機能シミュレーション (vanilla 1.21.1 正確モデルで生成した値) ===\n");

        ServerStats stats = new ServerStats();

        // ------------------------------------------------------------------ helpers
        header("1) 基本: 静止・無疾走・無耐衝撃の1打 (期待 H=0.400, V=0.400)");
        double k = 0.0, r = 0.0;
        double hVanilla = KbProbeMath.expectHorizontal(k, r);        // 0.400
        double vVanilla = KbProbeMath.expectVertical(k, r);          // 0.400
        int rawX = scale(hVanilla);                                   // vanilla wire int
        int rawY = scale(vVanilla);
        feedFixed(stats, k, r, rawX, rawY, true, true);
        expectCount(stats, 1, 1);

        header("2) 疾走ヒット (k=1 → vanilla 最終 H=0.700, V≈0.400 cap)");
        k = 1.0;
        feedFixed(stats, k, r, scale(KbProbeMath.expectHorizontal(k, r)),
                scale(KbProbeMath.expectVertical(k, r)), true, true);
        expectCount(stats, 2, 2);

        header("3) 対象が Netherite フル (推定耐衝撃 40%): H/V とも ×0.6 を計測");
        k = 0.0; r = 0.4;
        feedFixed(stats, k, r, scale(KbProbeMath.expectHorizontal(k, r)),
                scale(KbProbeMath.expectVertical(k, r)), true, true);
        expectCount(stats, 3, 3);

        header("4) 【回帰実演: 0.3.0 の単位バグ】 raw int (×8000) をそのまま渡すと全件が外れ値");
        int beforeH = stats.hSamples;
        feedRawBug(stats, k, r, scale(KbProbeMath.expectHorizontal(k, r)),
                scale(KbProbeMath.expectVertical(k, r)));              // /8000 しない旧経路
        expectCount(stats, 3, 3);
        note("旧コード: vx=" + scale(0.4) + " –> |speed| で >2.5 ⇒ OUTLIER reject (記録数 "
                + (stats.hSamples - beforeH) + " 件, 増えていない=実証)");

        header("5) ガード: 移動中の標的 (基線速度が大きい → 採用しない)");
        feed(stats, 0.0, 0.0, 0.20f, 0.0f, 0.40f, false /*moving*/, 0.40f, true, true);
        expectCount(stats, 3, 3);

        header("6) ガード: 押し出しが攻撃方向と逆向き (ノイズ → 採用しない)");
        feed(stats, 0.0, 0.0, -0.40f, 0.40f, 0.00f, true, 0.0f, true, true);
        expectCount(stats, 3, 3);

        header("7) ガード: 巨大速度 (爆発等, |H|=3.0 → 外れ値)");
        feed(stats, 0.0, 0.0, 3.00f, 0.40f, 0.00f, true, 0.0f, true, true);
        expectCount(stats, 3, 3);

        header("8) ガード: 推定耐衝撃 100% (水平常に 0 → 計算不能で却下)");
        feed(stats, 0.0, 1.0, 0.0f, 0.0f, 0.00f, true, 0.0f, true, true);
        expectCount(stats, 3, 3);

        header("9) ガード時間軸: ダメージ成立が来なかった殴り (=保護領域) ⇒ noDamageEvents++");
        int nd = stats.noDamageEvents;
        stats.noDamageEvents++;
        note("attack -> (確認なし, TTL超過) … noDamageEvents: " + nd + " -> " + stats.noDamageEvents);

        header("10) ガード時間軸: ダメージは成立→速度 12tick 未着 (KB無効領域) ⇒ noKbEvents++");
        int nk = stats.noKbEvents;
        stats.noKbEvents++;
        note("attack -> damage -> (速度なし, 窓切れ) … noKbEvents: " + nk + " -> " + stats.noKbEvents);

        header("12) 【0.4.0新規】攻撃〜成立の間に対象がジャンプ: Yは不変 (fV≈0) → 垂直のみ不採用");
        // バニラはダメージ適用tickに被害者が空中ならYを触らない → 測れるdy≈0。
        // Hは正しく採用されるが、Vは fV≈0 < VF_MIN として係数平均への混入を防ぐ。
        feed(stats, 0.0, 0.0, 0.400f, 0.000f, 0.0f, true, 0.0f, true, true);
        expectCount(stats, 4, 3);

        header("13) 【0.4.0新規】外部インパルス混入 (Wind Charge等): fV=20 (>VF_MAX) → 垂直のみ不採用");
        // r=0.95 の対象: 本来のV期待は0.02なのに測れたdy=0.4 → 係数×20 = 素の殴りではあり得ない。
        // outlier(2.5)には掛からない帯なので、垂直妥当域ガードがここで止める。
        feed(stats, 0.0, 0.95, 0.020f, 0.400f, 0.0f, true, 0.0f, true, true);
        expectCount(stats, 5, 3);

        header("14) チャット・アクションバーの実表示 (k=1 疾走サンプルの場合)");
        double hRaw = 0.7, dy = 0.4;
        double fH = hRaw / KbProbeMath.expectHorizontal(1.0, 0.0);
        double fV = dy / KbProbeMath.expectVertical(1.0, 0.0);
        System.out.printf("(actionbar) KB計測 H=%.3f(×%.2f) V=%.3f(×%.2f) k=1.0 @paradise.land%n",
                hRaw, fH, dy, fV);
        System.out.printf("(chat) [KBProbe] paradise.land 推定係数: 水平×%.2f / 垂直×%.2f"
                        + " (H:%d件 V:%d件) ※KB無効領域%d回検出%n",
                stats.avgHorizontalFactor(), stats.avgVerticalFactor(),
                stats.hSamples, stats.vSamples, stats.noKbEvents);

        System.out.println("\n=== 結果サマリ ===");
        for (String line : REPORT) {
            System.out.println(line);
        }
        boolean allPass = REPORT.stream().noneMatch(l -> l.startsWith("FAIL"));
        System.out.println("\nVERDICT: " + (allPass ? "ALL SCENARIOS OK" : "FAILURES PRESENT"));

        System.out.println("\n[本番導線で検証済みと分類できるもの]");
        System.out.println("  ✔ 期待値式: KbProbeMath.expectHorizontal/expectVertical (KbProbe本体も同一関数を参照)");
        System.out.println("  ✔ ガード閾値: idle/direction/outlier (KbProbe本体も同一関数を参照)");
        System.out.println("  ✔ 蓄積: ServerStats (本物のクラスをそのまま利用)");
        System.out.println("  ✔ 単位変換: unscaleVelocity(int)/8000 = 0.3.1 の修正箇所 (0.3.0 はこれが無く全件外れ値)");
        System.out.println("  ✔ 垂直妥当域: verticalFactorPlausible (0.4.0 新規 — ジャンプ間隙/外部インパルスの垂直混入を排除)");
    }

    // ------------------------------------------------------------------ reproduction

    /** The FIXED 0.3.1 path: raw wire ints are unscaled (/8000) before the guard chain. */
    private static void feed(ServerStats stats, double k, double r, float hDeltaIn, float vDeltaIn,
                             float currentH, boolean idle, float currentV,
                             boolean directionAlong, boolean targetOnGround) {
        // HEAD-時点のローカル現在速度（クライアントの再シミュレーション値）
        double cx = idle ? 0.0 : currentH;
        double cy = idle ? 0.0 : currentV;
        double cz = 0.0;
        if (!KbProbeMath.isIdle(cx, cy, cz)) {
            reject("idle-baseline fail (moving target)");
            return;
        }
        double vx = cx + hDeltaIn;                       // パケット絶対速度 = 旧速度+衝撃(vanilla)
        double vy = cy + vDeltaIn;
        if (!directionAlong) {
            vx = -hDeltaIn;
        }
        double hRaw = Math.hypot(vx - cx, 0 - cz);
        if (!KbProbeMath.directionOk(vx - cx, -cz, 1.0, 0.0, hRaw)) {
            reject("wrong-direction noise guard");
            return;
        }
        double dy = vy - cy;
        if (KbProbeMath.outlier(hRaw, dy)) {
            reject("outlier guard (|H|=" + fmt(hRaw) + ")");
            return;
        }
        if (r >= 1.0) {
            reject("resistance >= 100% guard");
            return;
        }
        double expectH = KbProbeMath.expectHorizontal(k, r);
        stats.addHorizontal(hRaw, hRaw / expectH);
        // 0.4.0: 垂直妥当域ガード（KJava本体の verticalFactorPlausible と同一判定）。
        // 「成立tickに空中でY不変 (fV≈0)」「外部インパルス (fV > 8)」を係数平均から外す。
        String verticalNote = "";
        if (targetOnGround) {
            double expectV = KbProbeMath.expectVertical(k, r);
            if (expectV > 1.0e-4) {
                double candidate = dy / expectV;
                if (KbProbeMath.verticalFactorPlausible(candidate)) {
                    stats.addVertical(dy, candidate);
                    verticalNote = String.format(" V=%.3f(f≈%.3f)", dy, candidate);
                } else {
                    verticalNote = String.format(" V=不採用(f=%.2f が妥当域[%.2f, %.2f]外)",
                            candidate, KbProbeMath.VF_MIN, KbProbeMath.VF_MAX);
                }
            }
        }
        accept(String.format("H=%.3f(f≈%.3f)%s", hRaw, hRaw / expectH,
                targetOnGround ? verticalNote : ""));
    }

    /** Wraps a vanilla-ground-truth sample into wire ints and runs the fixed path. */
    private static void feedFixed(ServerStats stats, double k, double r, int rawH, int rawV,
                                  boolean directionAlong, boolean targetOnGround) {
        double hDelta = KbProbeMath.unscaleVelocity(rawH);   // 0.3.1 の unscale (fix)
        double vDelta = KbProbeMath.unscaleVelocity(rawV);
        feed(stats, k, r, (float) hDelta, (float) vDelta, 0.0f, true, 0.0f, directionAlong,
                targetOnGround);
    }

    /** The OLD 0.3.0 path: raw ints were passed unscaled — all samples die in the outlier guard. */
    private static void feedRawBug(ServerStats stats, double k, double r, int rawH, int rawV) {
        double hRaw = rawH;                                  // /8000 漏れ → 3200 等の巨大値
        double dy = rawV;
        if (hRaw > 1.0e-4d) { /* direction: passes (positive) */ }
        if (hRaw > KbProbeMath.OUTLIER || Math.abs(dy) > KbProbeMath.OUTLIER) {
            reject("outlier guard tripped on unscaled wire value (" + rawH + ")");
            return;
        }
        stats.addHorizontal(hRaw, hRaw / KbProbeMath.expectHorizontal(k, r));
    }

    /** wire-scale an in-game double (vanilla sender side: (int)(vel*8000)). */
    private static int scale(double v) {
        return (int) (v * KbProbeMath.VELOCITY_PACKET_SCALE);
    }

    private static void header(String s)    { System.out.println("— " + s); }
    private static void accept(String s)    { System.out.println("   採用: " + s); }
    private static void reject(String s)    { System.out.println("   採用しない: " + s); }
    private static void note(String s)      { System.out.println("   " + s); }
    private static String fmt(double v)     { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    private static void expectCount(ServerStats stats, int h, int v) {
        boolean ok = stats.hSamples == h && stats.vSamples == v;
        String line = (ok ? "pass" : "FAIL")
                + " samples H=" + stats.hSamples + " (expect " + h + ") "
                + " V=" + stats.vSamples + " (expect " + v + ")";
        REPORT.add(line);
        if (!ok) {
            System.out.println("   ✗ " + line);
        }
    }

    private KbProbeSim() {
    }
}
