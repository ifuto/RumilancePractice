package com.rumilance.practice.match;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Blocks every command while a player is in an active match flow except {@code /leave}.
 */
public final class MatchCommandGuardListener implements Listener {

    private final PlayerStateManager stateManager;
    private final MessageService messages;

    public MatchCommandGuardListener(PlayerStateManager stateManager, MessageService messages) {
        this.stateManager = stateManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("rumilance.admin")) {
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.PREPARING_MATCH
                && state != PlayerState.COUNTDOWN
                && state != PlayerState.FIGHTING) {
            return;
        }
        String raw = event.getMessage().trim();
        if (raw.isEmpty() || raw.charAt(0) != '/') {
            return;
        }
        String label = raw.substring(1).split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (label.equals("leave") || label.equals("le")) {
            return;
        }
        event.setCancelled(true);
        if (messages != null) {
            messages.send(player, "match.command-blocked");
        }
    }
}
