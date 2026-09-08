package com.rumilance.practice.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Predicate;
import java.util.UUID;

/**
 * Locator-bar (waypoint HUD, 1.21.6+) visibility control:
 * <ul>
 *   <li><b>Lobby players</b> — {@code waypoint_receive_range}/{@code waypoint_transmit_range}
 *       base set to {@code 0}: no bar shown, and lobby players do not appear on anyone's bar.</li>
 *   <li><b>Combatants</b> (duel, party match, practice, FFA) — both ranges restored to the
 *       vanilla player base ({@code 60,000,000}) so teammates are visible on the bar.</li>
 * </ul>
 *
 * <p>The attributes only exist on 1.21.6+ runtimes; the whole service silently disables itself
 * when the registry does not offer them (older server forks).</p>
 *
 * <p>Application is event-driven (join, world change, respawn) plus a low-frequency periodic
 * sweep (every 40 ticks) so temporary desyncs (command teleports, attribute resets after
 * death, renames) always converge back to the correct base value.</p>
 */
public final class LocatorBarService implements Listener {

    /** Vanilla player base for both waypoint ranges. 0 disables the bar per player. */
    public static final double FULL_RANGE = 60_000_000.0d;
    private static final double DISABLED = 0.0d;
    private static final long SWEEP_PERIOD_TICKS = 40L;

    private final Plugin plugin;
    private final Predicate<UUID> combatant;
    private final Attribute transmit;
    private final Attribute receive;
    private BukkitTask sweep;

    /**
     * @param combatant true when the player is in a duel/practice/FFA match (bar ON), false in
     *                  lobby (bar OFF)
     */
    public LocatorBarService(Plugin plugin, Predicate<UUID> combatant) {
        this.plugin = plugin;
        this.combatant = combatant == null ? id -> false : combatant;
        this.transmit = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("waypoint_transmit_range"));
        this.receive = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("waypoint_receive_range"));
    }

    /** Begins the periodic convergence sweep. Safe when attributes are unsupported. */
    public void start() {
        if (!supported() || sweep != null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
        sweep = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                apply(player);
            }
        }, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    public void stop() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
    }

    /** Reapplies the target ranges for a single player (called on events / state changes). */
    public void apply(Player player) {
        if (player == null || !supported()) {
            return;
        }
        double target = combatant.test(player.getUniqueId()) ? FULL_RANGE : DISABLED;
        setBase(player.getAttribute(transmit), target);
        setBase(player.getAttribute(receive), target);
    }

    private boolean supported() {
        return transmit != null && receive != null;
    }

    private static void setBase(AttributeInstance instance, double target) {
        if (instance == null) {
            return;
        }
        try {
            if (Double.compare(instance.getBaseValue(), target) != 0) {
                instance.setBaseValue(target);
            }
        } catch (Throwable ignored) {
            // Antique runtime forks may reject the vanilla-only attributes; do not flood logs.
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // One tick later so post-join kit/spawn flows have settled the player's state.
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        apply(event.getPlayer());
    }
}
