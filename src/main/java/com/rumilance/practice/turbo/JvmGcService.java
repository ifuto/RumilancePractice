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
 * Read-only JVM/GC diagnostics and a Windows start-script generator for the "massive
 * lightweighting" goal.
 *
 * <p>The single biggest lever against GC stutter is the garbage collector itself: a
 * low-pause collector (ZGC) makes full-GC hitches effectively disappear on a game server,
 * where the working set is large but the live set is small. That choice is made at JVM
 * startup — a running plugin cannot swap its own collector — so this class both <em>measures</em>
 * the current JVM and <em>emits a ready-to-run start script</em> using the current heap size.</p>
 *
 * <p>Everything here is pure JDK MBean reads and string building; it never touches the OS and
 * never mutates the running JVM.</p>
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
                + " -Dfile.encoding=UTF-8"
                + " -jar " + jar + " nogui\r\n"
                + "pause\r\n";
    }
}
