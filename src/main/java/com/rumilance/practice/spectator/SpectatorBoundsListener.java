package com.rumilance.practice.spectator;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.session.MatchSession;
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
 * Hard server-side backstop that keeps spectators inside the arena they are watching. The
 * per-player world border already walls them in client-side, but spectator-mode flight and
 * teleports (spectate-target hopping) can pierce a client border, so out-of-bounds moves are
 * clamped back to the arena centre here.
 */
public final class SpectatorBoundsListener implements Listener {

    private final SpectatorService spectatorService;
    private final MatchRegistry matchRegistry;
    private final ArenaService arenaService;

    public SpectatorBoundsListener(SpectatorService spectatorService, MatchRegistry matchRegistry,
                                   ArenaService arenaService) {
        this.spectatorService = spectatorService;
        this.matchRegistry = matchRegistry;
        this.arenaService = arenaService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Cuboid region = regionFor(event.getPlayer());
        if (region != null && !region.contains(event.getTo())) {
            Location center = region.center();
            center.setWorld(event.getTo().getWorld());
            event.setTo(center);
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
            return null;
        }
        MatchSession session = matchRegistry.get(matchId).orElse(null);
        if (session == null || session.arenaInstanceId() == null) {
            return null;
        }
        ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            return null;
        }
        ArenaTemplate template = instance.template();
        return Cuboid.of(template.world(),
                template.minX(), template.minY(), template.minZ(),
                template.maxX(), template.maxY(), template.maxZ());
    }
}
