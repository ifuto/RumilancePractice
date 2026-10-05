package com.rumilance.practice.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * TPS の20分平均と、24時間以内に閾値を割った時間帯の記録。
 *
 * <p>{@code /tps} のための集計。スレッドセーフではない — サンプリングも読み出しもメインスレッド
 * から行う。時刻はすべて引数で受け取るので、窓や履歴の判定はサーバーなしで検証できる。</p>
 */
public final class TpsTracker {

    /** 20 TPS が 100%。 */
    public static final double MAX_TPS = 20.0d;
    /** 「重い」とみなす境目。仕様どおり 17.0。 */
    public static final double DIP_THRESHOLD = 17.0d;
    public static final long HISTORY_MS = 24L * 60L * 60L * 1000L;
    /** 20秒間隔のサンプルを60件 = 20分。 */
    public static final int WINDOW_SAMPLES = 60;
    public static final long SAMPLE_PERIOD_TICKS = 20L * 20L;

    /** TPS が閾値以下だった区間。 {@link #ongoing()} のものはまだ続いている。 */
    public record Dip(long startMillis, long endMillis) {

        public boolean ongoing() {
            return endMillis <= startMillis;
        }
    }

    private final Deque<Double> window = new ArrayDeque<>();
    private final List<Dip> dips = new ArrayList<>();

    private Dip open;
    private double latest = MAX_TPS;

    /** MSPT から TPS を求める。{@code TickHealth} と同じ式。 */
    public static double tpsFromMspt(double mspt) {
        if (mspt <= 0.0d || Double.isNaN(mspt) || Double.isInfinite(mspt)) {
            return MAX_TPS;
        }
        return Math.min(MAX_TPS, 1000.0d / mspt);
    }

    /** サンプルを1件追加する。 */
    public void sample(double tps, long nowMillis) {
        if (Double.isNaN(tps) || Double.isInfinite(tps)) {
            return;
        }
        latest = Math.max(0.0d, Math.min(MAX_TPS, tps));
        window.addLast(latest);
        while (window.size() > WINDOW_SAMPLES) {
            window.removeFirst();
        }
        if (latest <= DIP_THRESHOLD) {
            if (open == null) {
                open = new Dip(nowMillis, nowMillis);
            }
        } else if (open != null) {
            dips.add(new Dip(open.startMillis(), nowMillis));
            open = null;
        }
        long cutoff = nowMillis - HISTORY_MS;
        dips.removeIf(dip -> dip.endMillis() < cutoff);
        if (open != null && open.startMillis() < cutoff) {
            // 24時間を超えて続いている低下は、始まりを窓の縁まで切り詰めて表示する。
            open = new Dip(cutoff, cutoff);
        }
    }

    /** 直近のサンプル値。 */
    public double latest() {
        return latest;
    }

    /** 20分窓の平均 TPS。サンプルがまだ無ければ最新値。 */
    public double averageTps() {
        if (window.isEmpty()) {
            return latest;
        }
        double sum = 0.0d;
        for (double value : window) {
            sum += value;
        }
        return sum / window.size();
    }

    /** 20分平均 TPS の、20 TPS に対する百分率。 */
    public double averagePercent() {
        return averageTps() / MAX_TPS * 100.0d;
    }

    /** 24時間以内に閾値以下になった区間（古い順）。続いているものは末尾に ongoing で入る。 */
    public List<Dip> recentDips(long nowMillis) {
        long cutoff = nowMillis - HISTORY_MS;
        List<Dip> out = new ArrayList<>();
        for (Dip dip : dips) {
            if (dip.endMillis() >= cutoff) {
                out.add(dip);
            }
        }
        if (open != null) {
            out.add(open);
        }
        return List.copyOf(out);
    }
}
