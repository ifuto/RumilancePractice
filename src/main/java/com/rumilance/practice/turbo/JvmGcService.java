package com.rumilance.practice.turbo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JVM/GC diagnostics, a Windows start-script generator, and an on-demand GC engine with two
 * independent modes:
 *
 * <ul>
 *   <li><b>idle sweep</b> — {@code System.gc()} only while no players are online AND the sampled
 *       tick has headroom (see {@link #round}). Pays the old-generation pause now so the next
 *       match starts with a clean heap instead of discovering garbage mid-fight.</li>
 *   <li><b>pressure sweep</b> — {@code System.gc()} as soon as heap usage passes a cutoff (see
 *       {@link #pressureRound}), regardless of players or tick health. This is the answer to a
 *       server that never empties: a short reclaim at 90% heap is far cheaper than the
 *       multi-second full GC (or OutOfMemoryError) the JVM would otherwise reach at the worst
 *       moment. Idle sweep is opportunistic; pressure sweep is the safety net.</li>
 * </ul>
 *
 * <p>GC pauses can never be eliminated completely (a collector must have a safe point somewhere),
 * but moving the pause to a moment of our choosing — instead of the collector's — is what makes
 * them invisible to players.</p>
 */
public final class JvmGcService {

    /** Diagnostic snapshot of the running JVM's GC/heap state. */
    public record Report(
            String vmName,
            String vmVersion,
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            int processors,
            List<String> collectorNames,
            List<String> jvmArgs,
            boolean zgc,
            boolean shenandoah,
            boolean heapFixed,
            List<String> recommendations
    ) {
    }

    private final MemoryMXBean memoryBean;
    private final RuntimeMXBean runtimeBean;
    private final OperatingSystemMXBean osBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    public JvmGcService() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    }

    /** Collects the current JVM/GC state and derives actionable recommendations. */
    public Report report() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();

        List<String> collectorNames = new ArrayList<>();
        StringBuilder collectorSummary = new StringBuilder();
        for (GarbageCollectorMXBean bean : gcBeans) {
            collectorNames.add(bean.getName());
            collectorSummary.append(bean.getName())
                    .append("(count=").append(bean.getCollectionCount())
                    .append(", timeMs=").append(bean.getCollectionTime())
                    .append(") ");
        }

        String args = String.join(" ", runtimeBean.getInputArguments());
        boolean zgc = collectorSummary.toString().contains("ZGC")
                || args.contains("-XX:+UseZGC") || args.contains("-XX:+UseZLoadBarriers");
        boolean shenandoah = collectorSummary.toString().contains("Shenandoah")
                || args.contains("-XX:+UseShenandoahGC");
        boolean hasMinFlag = args.contains("-Xms");
        boolean hasMaxFlag = args.contains("-Xmx");
        boolean explicitlyFixed = hasMinFlag && hasMaxFlag;

        List<String> recommendations = buildRecommendations(
                zgc, shenandoah, explicitlyFixed, heap, collectorSummary.toString());

        return new Report(
                runtimeBean.getVmName(),
                runtimeBean.getVmVersion(),
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                osBean.getAvailableProcessors(),
                List.copyOf(collectorNames),
                List.copyOf(runtimeBean.getInputArguments()),
                zgc,
                shenandoah,
                explicitlyFixed,
                List.copyOf(recommendations));
    }

    private static List<String> buildRecommendations(boolean zgc, boolean shenandoah,
                                                     boolean explicitlyFixed,
                                                     MemoryUsage heap,
                                                     String collectorSummary) {
        List<String> out = new ArrayList<>();
        if (!zgc && !shenandoah) {
            out.add("低停止GCが未使用です (現在: " + collectorSummary.trim() + ")。"
                    + "ZGC に切り替えると GC によるカクつき(フリーズ)がほぼ見えなくなります。"
                    + "プラグインは実行中のGCを変更できないため /turbo make-start で生成した"
                    + " start.bat から再起動してください。");
        } else if (shenandoah) {
            out.add("Shenandoah が有効です。Windows では ZGC の方が実績が多いため、"
                    + "/turbo make-start の ZGC 版も検討できます。");
        } else {
            out.add("ZGC が有効です。フルGC起因の大フリーズはほぼ抑えられています。");
        }
        if (!explicitlyFixed) {
            out.add("ヒープ上下限 (-Xms / -Xmx) が固定されていません。起動直後のヒープ拡張が"
                    + "ティックを揺らすので、生成 start.bat のように両方を同じ値に固定してください。");
        }
        if (heap.getMax() > 0 && heap.getMax() < (2L << 30)) {
            out.add("最大ヒープが 2GB 未満です。PvP サーバーとしては小さめなので、メモリに余裕が"
                    + "あるなら 4GB 以上を推奨します。");
        }
        return out;
    }

    /**
     * Generates a Windows {@code start.bat} that restarts the server with a low-pause GC and a
     * fixed heap sized to the CURRENT {@code -Xmx} (falling back to 4G when unknown). The user
     * drops it next to {@code paper.jar} (adjust the jar name if it differs).
     */
    /**
     * Generates a Windows {@code start.bat} for a low-pause restart. The heap size is a single
     * variable at the top of the file — the operator edits {@code Xms}/{@code Xmx} there and
     * leaves the rest of the benchmarked flags alone.
     *
     * <p>Defaults to ZGC+ZGenerational (sub-millisecond p99 pauses even on a large working set,
     * and full-GC freezes are concurrent by design), a fixed heap, pre-touching so the backing
     * pages are committed before players arrive, and {@code -XX:SoftRefLRUPolicyMSPerMB=0} so
     * soft caches (class metadata, code cache) are reclaimed at the next minor cycle instead of
     * carrying into a full GC.</p>
     */
    public String startScriptText() {
        long maxBytes = memoryBean.getHeapMemoryUsage().getMax();
        String gb;
        if (maxBytes > 0) {
            gb = String.format(Locale.ROOT, "%.1f", maxBytes / 1073741824.0).replace(".0", "") + "G";
        } else {
            gb = "4G";
        }
        int processors = osBean.getAvailableProcessors();
        int concGc = Math.max(1, processors / 4);
        int parallelGc = Math.max(2, processors / 2);
        String jar = "paper.jar";
        return "@echo off\r\n"
                + "rem ==== N Arena optimized start script (generated by /turbo make-start) ====\r\n"
                + "rem  1) MEMORY: edit the value below — keep -Xms and -Xmx EQUAL\r\n"
                + "rem     (a fixed heap stops the JVM from resizing it mid-fight).\r\n"
                + "set HEAP=" + gb + "\r\n"
                + "rem  2) JAR: point this at your actual server jar if it is not paper.jar\r\n"
                + "set JAR=" + jar + "\r\n"
                + "rem  3) Leave the flags below alone — they are the low-pause / no-stutter tune.\r\n"
                + "chcp 65001 >nul\r\n"
                + "java -Xms%HEAP% -Xmx%HEAP% -XX:+UseZGC -XX:+ZGenerational"
                + " -XX:ConcGCThreads=" + concGc
                + " -XX:ParallelGCThreads=" + parallelGc
                + " -XX:+AlwaysPreTouch -XX:+PerfDisableSharedMem"
                + " -XX:SoftRefLRUPolicyMSPerMB=0 -Dfile.encoding=UTF-8 -jar %JAR% nogui\r\n"
                + "pause\r\n";
    }

    // ---------------------------------------------------------------------------------------------
    // background GC sweeper ("put GC on the back end")
    // ---------------------------------------------------------------------------------------------

    /**
     * The last {@code System.gc()} sweep's uptime (millis since JVM start). Shared by BOTH the
     * idle and the pressure mode, so a pressure sweep cannot be immediately followed by another
     * sweep (and vice versa). Spacing sweeps floors the total pause budget per unit time.
     */
    private volatile long lastSweepUptime = Long.MIN_VALUE;

    /**
     * Idle-mode check: whether we still have a chance to "pre-pay" the pause. {@code true} only
     * while no players are online and the sampled tick is healthy, i.e. the cheapest possible
     * moment. {@link GcBackgroundSweeper} calls this frequently; it is a pure read, no GC.
     */
    public boolean idleClearanceOk(boolean playersOnline, int tickHealthPercent, int clearancePercent) {
        return !playersOnline && tickHealthPercent >= clearancePercent;
    }

    /**
     * One idle-time GC sweep. Only call after {@link #idleClearanceOk} passed. Runs on the worker
     * pool, so the pause cost never lands on the server tick's own budget.
     */
    public GcSweep round(long minIntervalMs) {
        long now = System.currentTimeMillis();
        if (now - lastSweepUptime < minIntervalMs) {
            return new GcSweep(0L, "skipped: cooldown");
        }
        long before = heapUsedBytes();
        sweepOnce();
        long after = heapUsedBytes();
        lastSweepUptime = now;
        long freed = Math.max(0L, before - after);
        return new GcSweep(freed, "idle sweep: freed "
                + String.format(Locale.ROOT, "%.2fMB", freed / 1048576.0)
                + " (used " + before + " -> " + after + " bytes)");
    }

    /** Heap usage as a percentage of the configured maximum (100 when the max is unknown). */
    public int heapUsagePercent() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long max = heap.getMax();
        if (max <= 0) {
            return 100;
        }
        return (int) Math.min(100L, Math.max(0L, heap.getUsed() * 100L / max));
    }

    /**
     * Pressure-mode check: {@code true} when heap usage is at/above the cutoff — the moment to
     * clean house REGARDLESS of players or tick health, because waiting any longer makes the
     * eventual full GC (or OOM) strictly worse. This is the answer to a server that never empties.
     */
    public boolean pressureState(int pressurePercent) {
        return memoryBean.getHeapMemoryUsage().getMax() > 0 && heapUsagePercent() >= pressurePercent;
    }

    /**
     * One pressure sweep. Prefer {@link #sweepOnce} style reclaim under G1: {@code System.gc()}
     * with {@code -XX:ExplicitGCInvokesConcurrent} runs a <em>concurrent</em> mixed/old cycle, so
     * the stop-the-world portion stays tiny even while players are online. The cooldown guard
     * stops a permanently-stressed heap from turning into a GC-every-second storm.
     */
    public GcSweep pressureRound(long minIntervalMs, int pressurePercent) {
        long now = System.currentTimeMillis();
        if (now - lastSweepUptime < minIntervalMs) {
            return new GcSweep(0L, "skipped: cooldown");
        }
        long before = heapUsedBytes();
        sweepOnce();
        long after = heapUsedBytes();
        lastSweepUptime = now;
        long freed = Math.max(0L, before - after);
        return new GcSweep(freed, "pressure sweep (heap " + pressurePercent + "%+): freed "
                + String.format(Locale.ROOT, "%.2fMB", freed / 1048576.0)
                + " (used " + before + " -> " + after + " bytes)");
    }

    /** Current heap usage, for before/after measuring and status display. */
    public long heapUsedBytes() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    /**
     * Prompts the collector to do its pause-worthy work NOW. Under G1 (the Paper default)
     * {@code System.gc()} runs an explicit old-generation collection; we combine it with
     * {@code System.runFinalization()} so pending finalizers do not hitch a later tick.
     *
     * <p>Deliberately skipped on ZGC: there the full collection is concurrent anyway and an
     * explicit request would only burn CPU for no pause win.</p>
     */
    private static void sweepOnce() {
        if (isZgc()) {
            return;
        }
        System.gc();
        System.runFinalization();
        // Small settle window so one sweep does not stack onto the head of the next.
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isZgc() {
        String args = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        return args.contains("-XX:+UseZGC");
    }

    /** Result of one background sweep: bytes freed (>=0) plus a short reason line. */
    public record GcSweep(long bytesFreed, String detail) {
    }
}

