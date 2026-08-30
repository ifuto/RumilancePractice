package com.rumilance.practice.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Restores the classic 1.20.4-and-earlier shield-"stun" feel that Paper 1.20.6+ changed
 * (PaperMC issue #10742 / SPIGOT-7732).
 *
 * <p>Since 1.20.6, a hit that lands on a raised shield triggers vanilla invulnerability
 * frames, so the follow-up swing right after an axe/sword shield-break is swallowed for a
 * few ticks ("can't hit immediately after breaking the shield"). Paper devs confirmed a
 * plugin can restore the old behaviour by clearing the victim's invulnerability. This helper
 * zeroes {@link Player#getNoDamageTicks()} the instant the shield breaks and keeps it zeroed
 * for a handful of ticks so the very next swing connects.</p>
 */
public final class ShieldBreakStunFix {

    /** Ticks to keep the post-break target damageable (covers the vanilla stun window). */
    private static final int WINDOW_TICKS = 8;

    private ShieldBreakStunFix() {
    }

    public static void allowFollowUpHit(Plugin plugin, Player victim) {
        if (plugin == null || victim == null || !victim.isOnline()) {
            return;
        }
        // Immediately drop the i-frames the shield-blocked hit armed.
        victim.setNoDamageTicks(0);
        // Re-clear each tick for the window: vanilla still refreshes noDamageTicks on the
        // shield-disable tick, so a single reset can be overwritten before the next swing.
        for (int delay = 1; delay <= WINDOW_TICKS; delay++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    victim.setNoDamageTicks(0);
                }
            }, delay);
        }
    }
}
