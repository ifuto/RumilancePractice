package com.rumilance.practice.spectator;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.util.BoundsNudge;
import com.rumilance.practice.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * Hard server-side backstop that keeps spectators inside the arena they are watching.
 * Out-of-bounds flight is nudged ~1 block inward — never snapped to arena centre.
 */
public final class SpectatorBoundsListener implements Listener {

    private final SpectatorService spectatorService;
    private final MatchRegistry matchRegistry;
    private final ArenaService arenaService;
    private final com.rumilance.practice.ffa.FfaService ffaService;

    public SpectatorBoundsListener(SpectatorService spectatorService, MatchRegistry matchRegistry,
                                   ArenaService arenaService) {
        this(spectatorService, matchRegistry, arenaService, null);
    }

    public SpectatorBoundsListener(SpectatorService spectatorService, MatchRegistry matchRegistry,
                                   ArenaService arenaService,
                                   com.rumilance.practice.ffa.FfaService ffaService) {
        this.spectatorService = spectatorService;
        this.matchRegistry = matchRegistry;
        this.arenaService = arenaService;
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Cuboid region = regionFor(event.getPlayer());
        if (region != null && !region.contains(event.getTo())) {
            Location nudged = BoundsNudge.nudgeInward(region, event.getTo());
            nudged.setYaw(event.getTo().getYaw());
            nudged.setPitch(event.getTo().getPitch());
            event.setTo(nudged);
        }
    }

    /** Blocks spectator-menu teleports (clicking a player) from escaping the arena. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return;
        }
        Cuboid region = regionFor(event.getPlayer());
        if (region != null && !region.contains(event.getTo())) {
            event.setCancelled(true);
        }
    }

    private Cuboid regionFor(Player player) {
        UUID matchId = spectatorService.spectatedMatch(player.getUniqueId()).orElse(null);
        if (matchId == null) {
            // Not a duel/team camera: an FFA spectator has no match entry — the per-player
            // border alone is client-side only, so it needs the same server-side backstop.
            if (ffaService == null) {
                return null;
            }
            String ffaArenaId = spectatorService.ffaArenaOf(player.getUniqueId()).orElse(null);
            if (ffaArenaId == null) {
                return null;
            }
            return ffaService.get(ffaArenaId)
                    .map(com.rumilance.practice.ffa.FfaService.FfaArena::region)
                    .orElse(null);
        }
        MatchSession session = matchRegistry.get(matchId).orElse(null);
        if (session == null || session.arenaInstanceId() == null) {
            return null;
        }
        ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            return null;
        }
        return Cuboid.of(instance.template().world(),
                instance.minX(), instance.minY(), instance.minZ(),
                instance.maxX(), instance.maxY(), instance.maxZ());
    }
}
