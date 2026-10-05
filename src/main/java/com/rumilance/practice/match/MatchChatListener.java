package com.rumilance.practice.match;

import com.rumilance.practice.headfont.HeadFontService;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchState;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The single place that decides who reads a chat line.
 *
 * <p>Two channels exist while a duel is running:</p>
 * <ul>
 *   <li><b>Duel Chat</b> — the default for a fighter. The line is tagged {@code [Duel]} in aqua
 *       (a party battle uses {@code [Match]}) and reaches only that match's fighters and
 *       spectators, and only those of them who left Duel Chat reception ON. Format:
 *       {@code <aqua>[Duel]</aqua> <head> <white>Name</white> : message}.</li>
 *   <li><b>Global chat</b> — what everyone else sends, and what a fighter sends after switching
 *       the send-toggle to Global. Public lines honour each viewer's Global Chat reception
 *       toggle; a viewer's chat whitelist overrides it.</li>
 * </ul>
 *
 * <p>All three toggles live in {@link PlayerSettings} (Chat Settings screen in {@code /setting}),
 * so they survive a restart. System messages sent directly by their owning code (duel requests,
 * countdown, end screens, party chat) are unaffected — only chat lines are routed here.</p>
 */
public final class MatchChatListener implements Listener {

    /** Aqua tag in front of a fighter's own line (the requested duel chat marker). */
    private static final String DUEL_TAG = "[Duel]";
    /** The same marker for a party battle. */
    private static final String MATCH_TAG = "[Match]";

    private final MatchRegistry registry;
    private final SpectatorService spectatorService;
    private final SettingsService settingsService;

    public MatchChatListener(MatchRegistry registry, SpectatorService spectatorService,
                             SettingsService settingsService) {
        this.registry = registry;
        this.spectatorService = spectatorService;
        this.settingsService = settingsService;
    }

    /**
     * Whether {@code speaker}'s next line belongs to Duel Chat rather than the public channel:
     * true while they are inside a live match and have not switched their send-toggle to Global.
     */
    public boolean routesToDuel(Player speaker) {
        if (settingsService != null) {
            PlayerSettings settings = settingsService.get(speaker);
            if (settings != null && settings.duelChatGlobal()) {
                return false;
            }
        }
        return liveMatch(speaker.getUniqueId()) != null;
    }

    /** The match this player is currently fighting in, or {@code null} when they are not. */
    private MatchSession liveMatch(UUID playerId) {
        MatchSession session = registry.byPlayer(playerId).orElse(null);
        if (session == null) {
            return null;
        }
        MatchState state = session.state();
        if (state != MatchState.ACTIVE && state != MatchState.COUNTDOWN
                && state != MatchState.WAITING_FOR_PLAYERS && state != MatchState.ENDING) {
            return null;
        }
        return session;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player speaker = event.getPlayer();
        MatchSession session = routesToDuel(speaker) ? liveMatch(speaker.getUniqueId()) : null;

        if (session != null) {
            routeDuelChat(event, speaker, session);
        } else {
            routeGlobalChat(event, speaker);
        }
    }

    /** Scopes the line to the match and renders it in the Duel Chat format. */
    private void routeDuelChat(AsyncChatEvent event, Player speaker, MatchSession session) {
        Set<UUID> allowed = new HashSet<>(session.participants());
        if (spectatorService != null) {
            allowed.addAll(spectatorService.spectatorsWatching(session.id()));
        }
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) {
                // Keep non-player audiences (console) aware of duel chat for moderation.
                return false;
            }
            if (viewer.getUniqueId().equals(speaker.getUniqueId())) {
                // A speaker always reads back what they typed.
                return false;
            }
            if (!allowed.contains(viewer.getUniqueId())) {
                return true;
            }
            return !receivesDuelChat(viewer);
        });
        Component tag = Component.text(
                session.isTeamMatch() ? MATCH_TAG : DUEL_TAG, NamedTextColor.AQUA);
        Component head = HeadFontService.of(speaker.getUniqueId()).color(NamedTextColor.WHITE);
        event.renderer((source, sourceDisplayName, message, viewer) -> tag
                .append(Component.space())
                .append(head)
                .append(Component.text(source.getName(), NamedTextColor.WHITE))
                .append(Component.text(" : ", NamedTextColor.WHITE))
                .append(message));
    }

    /** Drops the viewers who turned Global Chat reception off (whitelist still wins). */
    private void routeGlobalChat(AsyncChatEvent event, Player speaker) {
        if (settingsService == null) {
            return;
        }
        String speakerName = speaker.getName().toLowerCase(Locale.ROOT);
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player viewer)) {
                return false;
            }
            if (viewer.getUniqueId().equals(speaker.getUniqueId())) {
                return false;
            }
            PlayerSettings settings = settingsService.get(viewer);
            if (settings == null || settings.receiveGlobalChat()) {
                return false;
            }
            return !settings.chatWhitelist().contains(speakerName);
        });
    }

    private boolean receivesDuelChat(Player viewer) {
        if (settingsService == null) {
            return true;
        }
        PlayerSettings settings = settingsService.get(viewer);
        return settings == null || settings.receiveDuelChat();
    }
}
