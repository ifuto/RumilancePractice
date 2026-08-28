package com.rumilance.practice.match;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.PlayAreaWall;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Soft wall for duel participants: slide along the border (never teleport to spawn/center).
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
        if (session == null || !PracticeGuards.arenaBoundsActive(session.state())) {
            return;
        }
        if (session.arenaInstanceId() == null) {
            return;
        }
        ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        Cuboid region = Cuboid.of(instance.template().world(),
                instance.minX(), instance.minY(), instance.minZ(),
                instance.maxX(), instance.maxY(), instance.maxZ());
        PlayAreaWall.constrain(event, region, player);
    }
}
