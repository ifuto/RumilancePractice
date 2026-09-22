package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchState;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Scopes a fighter's own chat line while a duel / team match is running.
 *
 * <ul>
 *   <li><b>What a fighter says</b> is tagged {@code [Duel]} in aqua (a party battle uses
 *       {@code [Match]}) and is delivered to that match only: the fighters and whoever is
 *       spectating it. It never reaches the lobby, another match, or FFA.</li>
 *   <li><b>What a fighter reads</b> is untouched — lobby chat, announcements, private messages
 *       and other players' lines all arrive normally (the fight isolation hides players from the
 *       TAB list and the world, never from chat). Only the speaker's own recipients and tag are
 *       rewritten.</li>
 * </ul>
 *
 * <p>System messages that must reach a fighter (duel requests, countdown, end screens, party
 * chat UI) are sent directly by their owning code and are not affected — this listener only
 * rewrites the recipient <em>viewers</em> and the renderer of a chat message.</p>
 */
public final class MatchChatListener implements Listener {

    /** Aqua tag in front of a fighter's own line (the requested duel chat marker). */
    private static final String DUEL_TAG = "[Duel]";
    /** The same marker for a party battle. */
    private static final String MATCH_TAG = "[Match]";

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
        // Tag the speaker's own line so the scoped channel is obvious: [Duel] aqua in a 1v1,
        // [Match] in a party battle. Wrapped around the existing renderer so the line keeps its
        // rank / team styling.
        Component tag = Component.text(
                session.isTeamMatch() ? MATCH_TAG : DUEL_TAG, NamedTextColor.AQUA);
        ChatRenderer original = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> tag
                .append(Component.space())
                .append(original.render(source, sourceDisplayName, message, viewer)));
    }
}
