package com.rumilance.practice.listener;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.sight.SightSettings;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.PearlLanding;
import org.bukkit.Location;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Server-wide practice rules: no pearl endermites, and pearl landings stay inside the arena wall
 * without climbing onto glass border ceilings.
 */
public final class PracticePearlListener implements Listener {

    private final MatchService matchService;
    private final FfaService ffaService;
    private final ArenaService arenaService;
    private final SightSettings sightSettings;

    public PracticePearlListener(MatchService matchService, FfaService ffaService, ArenaService arenaService,
                                 SightSettings sightSettings) {
        this.matchService = matchService;
        this.ffaService = ffaService;
        this.arenaService = arenaService;
        this.sightSettings = sightSettings == null ? SightSettings.defaults() : sightSettings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermite(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Endermite)) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.ENDER_PEARL
                || reason.name().contains("PEARL")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (!isCombatant(player) || event.getTo() == null) {
            return;
        }
        Cuboid bounds = playBounds(player);
        Location to = PearlLanding.safePearlLanding(
                event.getFrom(), event.getTo(), bounds, sightSettings.pearlMaxLiftBlocks());
        if (to == null || (bounds != null && !bounds.containsHorizontal(to))) {
            event.setCancelled(true);
            return;
        }
        event.setTo(to);
    }

    private Cuboid playBounds(Player player) {
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.arenaInstanceId() != null) {
            ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
            return instance == null ? null : instance.bounds();
        }
        if (ffaService.isInFfa(player.getUniqueId())) {
            return ffaService.arenaOf(player.getUniqueId())
                    .flatMap(ffaService::get)
                    .map(com.rumilance.practice.ffa.FfaService.FfaArena::region)
                    .orElse(null);
        }
        return null;
    }

    private boolean isCombatant(Player player) {
        if (ffaService.isInFfa(player.getUniqueId())) {
            return true;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        return session != null && (session.state() == MatchState.ACTIVE
                || session.state() == MatchState.COUNTDOWN
                || session.state() == MatchState.WAITING_FOR_PLAYERS);
    }
}
