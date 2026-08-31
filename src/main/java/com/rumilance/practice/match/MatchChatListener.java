package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchState;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Scopes chat while a player is in an active duel / team match.
 *
 * <ul>
 *   <li><b>Team match</b> — a fighter's message is delivered only to people inside that match:
 *       their own team (combat allies) AND the enemy team (opponents), plus anyone spectating it.</li>
 *   <li><b>1v1 duel</b> — the message goes to the two fighters and its spectators only.</li>
 * </ul>
 *
 * <p>Everyone else (lobby players, FFA players, spectators of other matches) is removed from the
 * recipients, so match chat never leaks out and outside chat never matters to fighters (their own
 * lines are simply not broadcast to the world). System messages that must reach a fighter (duel
 * requests, countdown, end screens, party chat UI) are sent directly by their owning code and are
 * not affected — this listener only rewrites the recipient <em>viewers</em> of a chat message.</p>
 */
public final class MatchChatListener implements Listener {

    private final MatchRegistry registry;
    private final SpectatorService spectatorService;

    public MatchChatListener(MatchRegistry registry, SpectatorService spectatorService) {
        this.registry = registry;
        this.spectatorService = spectatorService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player speaker = event.getPlayer();
        MatchSession session = registry.byPlayer(speaker.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        MatchState state = session.state();
        if (state != MatchState.ACTIVE && state != MatchState.COUNTDOWN
                && state != MatchState.WAITING_FOR_PLAYERS && state != MatchState.ENDING) {
            return;
        }
        Set<UUID> allowed = new HashSet<>(session.participants());
        if (spectatorService != null) {
            allowed.addAll(spectatorService.spectatorsWatching(session.id()));
        }
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) {
                // Keep non-player audiences (console) aware of match chat for moderation.
                return false;
            }
            return !allowed.contains(viewer.getUniqueId());
        });
    }
}
