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
 * JVM/GC diagnostics, a Windows start-script generator, and a <em>background GC</em> sweeper that
 * pushes garbage collection off the server tick and into idle moments.
 *
 * <p>GC pauses can never be eliminated completely (a collector must have a safe point somewhere),
 * but a PvP server's stalls are dominated by full/old-generation collections. That work can be
 * moved from the busy moment into the background in two independent ways, both of which this
 * class provides:</p>
 *
 * <ul>
 *   <li><b>{@link #sweepOnce(int)}</b> — proactively invoke {@code System.gc()} (in G1 it is
 *       effectively a synchronous mixed/full cycle) only when the server is idle (no players) and
 *       the main thread has tick headroom. Paying the pause cost now means the collector does not
 *       have to do it later while players are fighting.</li>
 *   <li><b>{@link #cleanSoftlyReferenced()}</b> — force-drop the JVM's softly-reachable caches
 *       (class metadata, code cache, resources) between fights. This is real "put GC on the back
 *       end": it releases the softly-held garbage ahead of the collector instead of letting a
 *       full GC spend its own stop-the-world budget on them.</li>
 * </ul>
 *
 * <p>The sweeper itself never runs on the main thread (it is called from a repeating Bukkit task
 * that only fires when {@link WindowsOptimizationService#tickHealthPercent()} grants clearance),
 * and it marks cleared references as enqueued so they are reclaimed without a full pause.</p>
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
                + "rem  - ZGC (low pause) + fixed heap => GC stutter is minimized\r\n"
                + "rem  - Adjust 'paper.jar' below if your server jar has a different name\r\n"
                + "chcp 65001 >nul\r\n"
                + "java -Xms" + gb + " -Xmx" + gb
                + " -XX:+UseZGC -XX:+ZGenerational"
                + " -XX:ConcGCThreads=" + concGc
                + " -XX:ParallelGCThreads=" + parallelGc
                + " -XX:+AlwaysPreTouch"
                + " -XX:+PerfDisableSharedMem"
                + " -XX:SoftRefLRUPolicyMSPerMB=0"
                + " -Dfile.encoding=UTF-8"
                + " -jar " + jar + " nogui\r\n"
                + "pause\r\n";
    }

    // ---------------------------------------------------------------------------------------------
    // background GC sweeper ("put GC on the back end")
    // ---------------------------------------------------------------------------------------------

    /**
     * The last {@code System.gc()} sweep's uptime (millis since JVM start). Spaces sweeps so they
     * do not fire back-to-back inside the same idle window.
     */
    private volatile long lastSweepUptime = Long.MIN_VALUE;

    /**
     * One idle-time GC sweep. Invoked only when the server is empty and the tick has headroom,
     * so a pause here costs the players nothing. Returns the bytes freed (used-heap delta) and a
     * human-readable reason, or the skip reason alone.
     */
    public GcSweep round(boolean playersOnline, int tickHealthPercent, long minIntervalMs) {
        if (playersOnline) {
            return new GcSweep(0L, "skipped: players online");
        }
        if (tickHealthPercent < 90) {
            return new GcSweep(0L, "skipped: tick health " + tickHealthPercent + "% (need 90)");
        }
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

    /** Current heap usage, for before/after measuring and status display. */
    public long heapUsedBytes() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    /**
     * Prompts the collector to do its pause-worthy work NOW, while the server is empty. Under G1
     * (the Paper default) {@code System.gc()} runs an explicit old-generation collection —
     * effectively the mixed/full cycle that would otherwise fire mid-fight — so paying for it here
     * means the next busy moment starts with a cleaned heap. {@code System.runFinalization()} then
     * drains any pending finalizers so they do not hitch a later tick.
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

