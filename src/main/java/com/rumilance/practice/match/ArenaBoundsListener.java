package com.rumilance.practice.match;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Pushes duel participants back inside their arena bounds (participants only).
 */
public final class ArenaBoundsListener implements Listener {

    private final MatchService matchService;
    private final ArenaService arenaService;

    public ArenaBoundsListener(MatchService matchService, ArenaService arenaService) {
        this.matchService = matchService;
        this.arenaService = arenaService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null
                || (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != MatchState.ACTIVE && session.state() != MatchState.COUNTDOWN) {
            return;
        }
        if (session.arenaInstanceId() == null) {
            return;
        }
        ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        // Use the instance's own bounds: for disposable copies these are origin-shifted.
        Cuboid region = Cuboid.of(instance.template().world(),
                instance.minX(), instance.minY(), instance.minZ(),
                instance.maxX(), instance.maxY(), instance.maxZ());
        if (!region.contains(event.getTo())) {
            // RED returns to spawn A, BLUE to spawn B (correct for 1v1 AND team matches;
            // previously only participant #0 got spawn A, so most team members were sent
            // to the enemy spawn when pushed back).
            Location spawn = session.teamColor(player.getUniqueId()) == com.rumilance.practice.state.TeamColor.RED
                    ? arenaService.spawnA(instance)
                    : arenaService.spawnB(instance);
            event.setTo(LocationUtil.safeTeleportLocation(spawn, player));
        }
    }
}
