package com.rumilance.practice.spectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.plugin.Plugin;

/**
 * While spectating, other players in spectator gamemode must not be rendered on screen at
 * all — the semi-transparent bodies other spectators float around with (vanilla renders a
 * spectator to another spectator) and the parked fighters of a party fight included. They
 * stay listed in TAB: only the entity packets are blocked, never the player-info ones
 * (user spec 2026-10-04, the "hidePlayer は TAB からも消えるので禁止" rule).
 *
 * <p>Why the packet layer: {@code Player#hideEntity} is already applied on every spectate
 * entry ({@code SpectatorService#hideInWorld}), yet the client keeps rendering
 * spectator-gamemode players for spectator viewers, so the heads kept showing. Cancelling
 * the outbound entity packets at ProtocolLib is unconditional and covers every path (late
 * joins, spectate entries, eliminated fighters that never went through a spectate entry at
 * all).</p>
 *
 * <p>Because cancelled packets leave the server's tracker believing the client already has
 * the entity, a client that stops being blocked would never receive a fresh spawn. Two
 * transitions therefore force a retrack (hide + show pair, the only reliable re-send):
 * a spectator leaving spectate mode, and any player leaving spectator gamemode while
 * spectators are watching. Needs ProtocolLib (soft-depend); without it the previous
 * Bukkit-level hiding stays as the only layer.</p>
 */
public final class SpectatorViewIsolation {

    private SpectatorViewIsolation() {
    }

    /** Registers the packet filter + retrack hooks when ProtocolLib is present. */
    public static void register(Plugin plugin, SpectatorService service) {
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().info(
                    "[Spectator] ProtocolLib missing - spectator-vs-spectator entity blocking is off.");
            return;
        }
        try {
            SpectatorViewIsolationPackets.register(plugin, service);
        } catch (Throwable t) {
            // A ProtocolLib version without one of the packet types must not break the boot.
            plugin.getLogger().warning(
                    "[Spectator] entity blocking unavailable: " + t.getClass().getSimpleName());
        }
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onGameModeChange(PlayerGameModeChangeEvent event) {
                Player changed = event.getPlayer();
                if (event.getNewGameMode() == GameMode.SPECTATOR) {
                    // A fighter just died/parked into spectator mode while spectators may
                    // already track them: untrack (despawn) for every spectating client, or
                    // the cancelled updates would leave a frozen body at the death spot.
                    for (Player spectator : Bukkit.getOnlinePlayers()) {
                        if (spectator.equals(changed)
                                || !service.isSpectating(spectator.getUniqueId())) {
                            continue;
                        }
                        try {
                            spectator.hideEntity(plugin, changed);
                        } catch (Throwable ignored) {
                            // bookkeeping must never surface into gameplay
                        }
                    }
                    return;
                }
                for (Player spectator : Bukkit.getOnlinePlayers()) {
                    if (spectator.equals(changed)
                            || !service.isSpectating(spectator.getUniqueId())) {
                        continue;
                    }
                    forceRetrack(plugin, spectator, changed);
                }
            }
        }, plugin);
    }

    /**
     * Hides and immediately re-shows {@code target} to {@code viewer}: the hide marks the
     * tracker untracked (a despawn the client can safely ignore) and the show triggers a
     * fresh spawn chain, this time past the packet filter.
     */
    static void forceRetrack(Plugin plugin, Player viewer, Player target) {
        try {
            viewer.hideEntity(plugin, target);
            viewer.showEntity(plugin, target);
        } catch (Throwable ignored) {
            // Never let retrack bookkeeping surface into gameplay.
        }
    }

    /**
     * Called when {@code viewer} stops spectating: every still-spectator-gamemode player is
     * re-tracked for them, because their spawns were packet-cancelled while the viewer was
     * spectating.
     */
    static void revealSpectatorsTo(Plugin plugin, Player viewer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(viewer) || online.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            forceRetrack(plugin, viewer, online);
        }
    }
}
