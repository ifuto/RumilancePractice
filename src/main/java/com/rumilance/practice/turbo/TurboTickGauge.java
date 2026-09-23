package com.rumilance.practice.turbo;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Per-tick tail hook for {@link WindowsOptimizationService#gaugeTickHealth()}, the root-level load
 * shedding tag.
 *
 * <p>Must remain allocation-free in steady state. {@link #onTickEnd} reads one cached volatile
 * under the server tick (no {@code System.nanoTime}, no collection) and hands off; the actual
 * store is blocked by the service behind a one-integer bucket comparison, so nothing is written
 * to disk or re-tagged unless the sampled-tick health bucket actually changed.</p>
 *
 * <p>{@code CombatSyncListener} already owns the {@link com.rumilance.practice.util.TickHealth}
 * recording hook; the gauge deliberately defers to that snapshot so it never opens a second
 * nano-time source or competes with it on the same tick.</p>
 */
public final class TurboTickGauge implements Listener {

    private final WindowsOptimizationService turbo;

    public TurboTickGauge(WindowsOptimizationService turbo) {
        this.turbo = turbo;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTickEnd(ServerTickEndEvent event) {
        turbo.gaugeTickHealth();
    }
}
