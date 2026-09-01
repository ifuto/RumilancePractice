package com.rumilance.practice.combat;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Nether and End portals must never materialise during a match, regardless of kit.
 *
 * <p>A player in a duel/team match or an FFA arena lives in a controlled arena world; a spawned
 * portal frame (nether portal from flint-and-steel on obsidian, or an End portal) has no use in a
 * practice fight and only risks teleporting fighters out or corrupting the arena. This listener
 * cancels {@link PortalCreateEvent} in any world that currently hosts an active combatant. It
 * covers every {@link PortalCreateEvent.CreationReason} (fire, etc.).</p>
 */
public final class PortalBlockListener implements Listener {

    private final MatchRegistry matchRegistry;
    private final FfaService ffaService;

    public PortalBlockListener(MatchRegistry matchRegistry, FfaService ffaService) {
        this.matchRegistry = matchRegistry;
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        World world = event.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != world) {
                continue;
            }
            if (isCombatant(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isCombatant(UUID playerId) {
        if (ffaService != null && ffaService.isInFfa(playerId)) {
            return true;
        }
        if (matchRegistry != null) {
            MatchSession session = matchRegistry.byPlayer(playerId).orElse(null);
            if (session != null && (session.state() == MatchState.ACTIVE
                    || session.state() == MatchState.COUNTDOWN)) {
                return true;
            }
        }
        return false;
    }

    /** Predicate form reused by tests / other guards. */
    public Predicate<UUID> combatantPredicate() {
        return this::isCombatant;
    }
}
