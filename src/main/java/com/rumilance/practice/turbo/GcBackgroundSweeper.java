package com.rumilance.practice.turbo;

import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * Repeating scheduler that moves {@code System.gc()} off the busy moment and into the background:
 * it sweeps the heap ONLY while the server is empty (no players) and the main thread has tick
 * headroom. Paying the collector's old-generation work during idle means the next match starts
 * with a cleaned heap instead of discovering garbage mid-fight.
 *
 * <p>The danger of a naive repeating {@code System.gc()} is running a stop-the-world pause exactly
 * when it hurts. All pauses are absorbed by gates which are checked on the main thread first:</p>
 *
 * <ul>
 *   <li>{@code turbo.gc-background.enabled} master switch (config);</li>
 *   <li>no players online;</li>
 *   <li>{@code turbo.tickHealthPercent()} &gt;= clearance (default 90) — an idle server still has a
 *       live main loop, and we refuse to sweep while the tick is struggling;</li>
 *   <li>min-interval guard so two sweeps never stack in the same window.</li>
 * </ul>
 *
 * <p>The actual {@code System.gc()} call runs on the plugin's worker pool ({@code AsyncExecutor}),
 * not the server thread, so the pause cost is never added to a tick's own budget. Skipped rounds
 * log nothing (steady-state is silent); only actual collections log their freed bytes.</p>
 */
public final class GcBackgroundSweeper {

    private final JavaPlugin plugin;
    private final WindowsOptimizationService turbo;
    private final JvmGcService jvmGc;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;

    private BukkitRunnable task;
    private int intervalSeconds = 60;
    private int minIntervalSeconds = 30;
    private int clearancePercent = 90;

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
        intervalSeconds = Math.max(5,
                plugin.getConfig().getInt("turbo.gc-background.interval-seconds", 60));
        minIntervalSeconds = Math.max(1,
                plugin.getConfig().getInt("turbo.gc-background.min-interval-seconds", 30));
        clearancePercent = Math.max(0, Math.min(100,
                plugin.getConfig().getInt("turbo.gc-background.clearance-percent", 90)));
    }

    /** Starts (or restarts) the repeating sweep task. */
    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("turbo.gc-background.enabled", true)) {
            logger.info("gc-background: 無効 (turbo.gc-background.enabled: false)");
            return;
        }
        reloadConfig();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                maybeSweep();
            }
        };
        task.runTaskTimer(plugin, 20L * intervalSeconds, 20L * intervalSeconds);
        logger.info("gc-background: 起動しました (interval=" + intervalSeconds
                + "s, clearance=" + clearancePercent + "%)");
    }

    private void maybeSweep() {
        if (turbo.playersOnline()) {
            return;
        }
        int health = turbo.tickHealthPercent();
        if (health < clearancePercent) {
            return;
        }
        asyncExecutor.runAsync(() -> {
            JvmGcService.GcSweep sweep = jvmGc.round(false, health, minIntervalSeconds * 1000L);
            if (sweep.bytesFreed() > 0) {
                logger.info("gc-background: " + sweep.detail());
            }
        });
    }

    /** Cancels the repeating task on plugin disable. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
