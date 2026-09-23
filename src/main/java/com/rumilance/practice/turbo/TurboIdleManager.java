package com.rumilance.practice.turbo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * Presence-based auto idle/wake for the Windows turbo optimization.
 *
 * <p>The aim of the feature is "when NOBODY is online, don't burn CPU at max clocks / with idle
 * states disabled; when someone comes back, be at full speed within seconds — without ever running
 * a cold start". The server process and its listener stay fully alive the whole time, so joining
 * keeps working normally and recovery is a matter of seconds (power plan + process QoS).</p>
 *
 * <ul>
 *   <li><b>Wake</b> (first player joins or {@code _players()} goes 1→0→1): clear the EcoQoS
 *       power throttling and re-apply the turbo power plan.</li>
 *   <li><b>Idle</b> (last player leaves, after the configured delay): revert the power plan to the
 *       operator's normal one and engage EcoQoS throttling on the server process.</li>
 * </ul>
 *
 * <p>Elevation for the plan switch is handled by the resident helper inside
 * {@link WindowsOptimizationService}, so these automatic transitions do not pop a UAC dialog every
 * time: the first ever privileged call (usually {@code /turbo on} by an admin) raised it once for
 * the lifetime of the server, and the helper keeps answering afterwards.</p>
 *
 * <p>Degradation is graceful: a non-Windows host, an empty recipe, a never-granted UAC, or any
 * failure just logs and continues — the server keeps working normally, only the power savings do
 * not apply.</p>
 */
public final class TurboIdleManager implements Listener {

    private final JavaPlugin plugin;
    private final WindowsOptimizationService turbo;
    private final Logger logger;

    private boolean autoEnabled;
    private int idleDelaySeconds;
    private boolean lastWasEmpty;
    private boolean idleApplied;
    private BukkitRunnable pendingIdle;

    public TurboIdleManager(JavaPlugin plugin, WindowsOptimizationService turbo) {
        this.plugin = plugin;
        this.turbo = turbo;
        this.logger = plugin.getLogger();
        this.autoEnabled = turbo.autoEnabled();
        this.idleDelaySeconds = turbo.autoIdleDelaySeconds();
        this.lastWasEmpty = plugin.getServer().getOnlinePlayers().isEmpty();
    }

    /** Re-reads the {@code turbo.auto.*} switches from config (called by /rumireload). */
    public void reload() {
        autoEnabled = turbo.autoEnabled();
        idleDelaySeconds = turbo.autoIdleDelaySeconds();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        lastWasEmpty = false;
        cancelIdle();
        if (!idleApplied) {
            return;
        }
        logger.info("turbo: プレイヤー復帰を検知 — 省電力状態を解除しフルパワーへ復帰します。");
        transition(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getServer().getOnlinePlayers().size() > 1) {
            return;
        }
        lastWasEmpty = true;
        if (!autoEnabled) {
            return;
        }
        scheduleIdle();
    }

    private void scheduleIdle() {
        cancelIdle();
        if (idleDelaySeconds <= 0) {
            logger.info("turbo: 最後のプレイヤーが退出しました — 省電力状態へ移行します。");
            transition(true);
            return;
        }
        pendingIdle = new BukkitRunnable() {
            @Override
            public void run() {
                if (lastWasEmpty && !idleApplied) {
                    logger.info("turbo: 無人のまま " + idleDelaySeconds
                            + " 秒経過 — 省電力状態へ移行します。");
                    transition(true);
                }
            }
        };
        pendingIdle.runTaskLater(plugin, idleDelaySeconds * 20L);
    }

    private void cancelIdle() {
        if (pendingIdle != null) {
            pendingIdle.cancel();
            pendingIdle = null;
        }
    }

    /**
     * Performs the actual plan + EcoQoS transition and owns the {@code idleApplied} flag. EcoQoS
     * is unelevated and always safe; the privileged power-plan part is skipped when the configured
     * recipe is empty or no helper was ever granted elevation. Runs its OS work off the main
     * thread through the service's async executor.
     */
    private void transition(boolean intoIdle) {
        pendingIdle = null;
        idleApplied = intoIdle;
        if (!WindowsOptimizationService.isWindows() || !autoEnabled) {
            return;
        }
        // EcoQoS is unelevated and always safe; the privileged plan part is skipped when the
        // configured recipe is empty or no helper was ever granted elevation.
        boolean hasPlanThatMatters = turbo.recipeTouchesSettings();
        turbo.setEcoQosAsync(intoIdle).whenComplete((unused, error) -> {
            if (error != null) {
                logger.warning("turbo: EcoQoS 切替に失敗: " + error.getMessage());
            }
        });
        if (intoIdle) {
            if (hasPlanThatMatters) {
                turbo.revertQuietAsync().whenComplete((result, error) -> {
                    if (error != null) {
                        logger.warning("turbo: 省電力のプラン復元に失敗: " + error.getMessage());
                    } else if (!result.ok()) {
                        logger.info("turbo: 省電力のプラン復元をスキップしました (UAC 未承認など)。");
                    }
                });
            }
        } else {
            if (hasPlanThatMatters) {
                turbo.applyQuietAsync().whenComplete((result, error) -> {
                    if (error != null) {
                        logger.warning("turbo: フルパワー復帰に失敗: " + error.getMessage());
                    } else if (!result.success()) {
                        logger.info("turbo: フルパワー復帰をスキップしました (UAC 未承認など)。");
                    }
                });
            }
        }
    }

    /** Cancels any pending timers on plugin disable. */
    public void shutdown() {
        cancelIdle();
    }
}
