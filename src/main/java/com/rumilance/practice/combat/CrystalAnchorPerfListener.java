package com.rumilance.practice.combat;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;

/**
 * Soft cap on stacked end crystals near a combatant to reduce chain-reaction TPS spikes.
 * Block breaking / blast radius stay vanilla  Eno {@code blockList} edits here.
 */
public final class CrystalAnchorPerfListener implements Listener {

    /** Soft cap of crystals within {@link #CAP_RADIUS} of the placer. */
    private static final int MAX_CRYSTALS_NEAR = 12;
    private static final double CAP_RADIUS = 48.0d;

    private final MatchService matchService;
    private final FfaService ffaService;

    public CrystalAnchorPerfListener(MatchService matchService, FfaService ffaService) {
        this.matchService = matchService;
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !isCombatant(player)) {
            return;
        }
        Location at = event.getEntity().getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        int nearby = 0;
        double r2 = CAP_RADIUS * CAP_RADIUS;
        for (Entity entity : world.getEntitiesByClass(EnderCrystal.class)) {
            if (entity.getLocation().distanceSquared(at) <= r2) {
                nearby++;
                if (nearby >= MAX_CRYSTALS_NEAR) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean isCombatant(Player player) {
        if (ffaService.isInFfa(player.getUniqueId())) {
            return true;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        return session != null && (session.state() == MatchState.ACTIVE
                || session.state() == MatchState.COUNTDOWN);
    }
}
