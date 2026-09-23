package com.rumilance.practice.turbo;

import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * Fast repeating monitor with two independent GC paths, both of which move the pause off the
 * server tick (the actual {@code System.gc()} runs on the worker pool, never the main thread).
 *
 * <ul>
 *   <li><b>Pressure (safety net, always armed):</b> whenever heap usage reaches
 *       {@code pressure-percent} (default 85), sweep immediately — regardless of players online or
 *       tick health. A short reclaim at 85-90% is far cheaper than the multi-second full GC (or
 *       OutOfMemoryError) the JVM would otherwise hit at the worst moment. This is the answer to a
 *       server that never empties: it can no longer "GC-less とは死ぬ".</li>
 *   <li><b>Idle (opportunistic pre-pay):</b> when NOBODY is online and the tick is healthy, sweep
 *       so the next match starts with a clean heap instead of discovering garbage mid-fight.</li>
 * </ul>
 *
 * <p>The monitor polls every {@code interval-seconds} (default 5); each poll is a few cached
 * volatile reads on the main thread with no allocation, no score writes, no GC. The expensive
 * pause only ever happens off-thread, so the monitor itself cannot cause the very lag it detects.</p>
 */
public final class GcBackgroundSweeper {

    private final JavaPlugin plugin;
    private final WindowsOptimizationService turbo;
    private final JvmGcService jvmGc;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;

    private BukkitRunnable task;
    private int intervalSeconds = 5;
    private int minIntervalSeconds = 15;
    private int clearancePercent = 90;
    private int pressurePercent = 85;

    public GcBackgroundSweeper(JavaPlugin plugin, WindowsOptimizationService turbo,
                               JvmGcService jvmGc, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.turbo = turbo;
        this.jvmGc = jvmGc;
        this.asyncExecutor = asyncExecutor;
        this.logger = plugin.getLogger();
    }

    /** Re-reads {@code turbo.gc-background.*} from config (called by /rumireload). */
    public void reloadConfig() {
        intervalSeconds = Math.max(1,
                plugin.getConfig().getInt("turbo.gc-background.interval-seconds", 5));
        minIntervalSeconds = Math.max(1,
                plugin.getConfig().getInt("turbo.gc-background.min-interval-seconds", 15));
        clearancePercent = Math.max(0, Math.min(100,
                plugin.getConfig().getInt("turbo.gc-background.clearance-percent", 90)));
        pressurePercent = Math.max(1, Math.min(100,
                plugin.getConfig().getInt("turbo.gc-background.pressure-percent", 85)));
    }

    /** Starts (or restarts) the repeating monitor. */
    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("turbo.gc-background.enabled", true)) {
            logger.info("gc(background): 無効 (turbo.gc-background.enabled: false)");
            return;
        }
        reloadConfig();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                poll();
            }
        };
        task.runTaskTimer(plugin, 20L * intervalSeconds, 20L * intervalSeconds);
        logger.info("gc(background): 監視開始 (interval=" + intervalSeconds + "s, "
                + "idle-clearance=" + clearancePercent + "%, pressure=" + pressurePercent + "%)");
    }

    private void poll() {
        int health = turbo.tickHealthPercent();

        // 1) Pressure gate — the safety net. Always evaluated, no idle requirement.
        if (jvmGc.pressureState(pressurePercent)) {
            asyncExecutor.runAsync(() -> {
                JvmGcService.GcSweep sweep =
                        jvmGc.pressureRound(minIntervalSeconds * 1000L, pressurePercent);
                if (sweep.bytesFreed() > 0) {
                    logger.warning("gc(pressure): " + sweep.detail());
                }
            });
            return;
        }

        // 2) Idle gate — opportunistic pre-pay. Only when empty AND tick is healthy.
        if (jvmGc.idleClearanceOk(turbo.playersOnline(), health, clearancePercent)) {
            asyncExecutor.runAsync(() -> {
                JvmGcService.GcSweep sweep = jvmGc.round(minIntervalSeconds * 1000L);
                if (sweep.bytesFreed() > 0) {
                    logger.info("gc(idle): " + sweep.detail());
                }
            });
        }
    }

    /** Cancels the repeating task on plugin disable. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
