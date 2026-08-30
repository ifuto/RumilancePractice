package com.rumilance.practice.combat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores the classic 1.20.4-and-earlier shield-"stun" feel that Paper 1.20.6+ changed
 * (PaperMC issue #10742 / SPIGOT-7732) and the related vanilla knockback gap (MC-268147).
 *
 * <p>Since 1.20.6, a hit that lands on a raised shield triggers vanilla invulnerability
 * frames, so the follow-up swing right after an axe/sword shield-break is swallowed for a
 * few ticks ("can't hit immediately after breaking the shield"). Paper devs confirmed a
 * plugin can restore the old behaviour by clearing the victim's invulnerability.</p>
 */
public final class ShieldBreakStunFix {

    /** Ticks to keep the post-break target damageable (covers the vanilla stun window). */
    private static final int WINDOW_TICKS = 8;
    /** Victims whose shield was just broken -> tick timestamp of the break. */
    private static final Map<UUID, Integer> RECENT_BREAKS = new ConcurrentHashMap<>();

    private ShieldBreakStunFix() {
    }

    /**
     * Called when our sword/axe shield-break rule fires. Zeroes the victim's i-frames and
     * remembers the break so the next connecting hit can be treated as a post-stun hit.
     */
    public static void allowFollowUpHit(Plugin plugin, Player victim) {
        if (plugin == null || victim == null || !victim.isOnline()) {
            return;
        }
        RECENT_BREAKS.put(victim.getUniqueId(), Bukkit.getCurrentTick());
        // Immediately drop the i-frames the shield-blocked hit armed.
        victim.setNoDamageTicks(0);
        // Re-clear each tick for the window: vanilla still refreshes noDamageTicks on the
        // shield-disable tick, so a single reset can be overwritten before the next swing.
        for (int delay = 1; delay <= WINDOW_TICKS; delay++) {
            final UUID id = victim.getUniqueId();
            final boolean last = delay == WINDOW_TICKS;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(id);
                if (online != null && online.isOnline()) {
                    online.setNoDamageTicks(0);
                }
                if (last) {
                    RECENT_BREAKS.remove(id);
                }
            }, delay);
        }
    }

    /** @return true if the victim's shield was broken by {@code attacker} very recently. */
    public static boolean recentShieldBreak(Player victim) {
        if (victim == null) {
            return false;
        }
        Integer tick = RECENT_BREAKS.get(victim.getUniqueId());
        return tick != null && (Bukkit.getCurrentTick() - tick) <= (WINDOW_TICKS + 2);
    }

    /** @return true if the attacker is holding a sword/axe/mace (tools that can disable a shield). */
    public static boolean holdsShieldBreaker(Player attacker) {
        if (attacker == null) {
            return false;
        }
        Material hand = attacker.getInventory().getItemInMainHand().getType();
        return hand.name().endsWith("_SWORD")
                || hand.name().endsWith("_AXE")
                || hand == Material.MACE
                || hand == Material.TRIDENT;
    }
}
