package com.rumilance.practice.command;

import com.rumilance.practice.duel.DuelRequestService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * Takes {@code /accept}, {@code /deny}, {@code /cancel} (and {@code /cansel}) before other
 * plugins such as Essentials TPA, but only when this player actually has a practice duel
 * request involved. Chat click callbacks also land here via {@code Player#performCommand}.
 */
public final class DuelChatInterceptListener implements Listener {

    private final DuelCommand duelCommand;
    private final DuelRequestService duelRequestService;

    public DuelChatInterceptListener(DuelCommand duelCommand, DuelRequestService duelRequestService) {
        this.duelCommand = duelCommand;
        this.duelRequestService = duelRequestService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (raw.isEmpty() || raw.charAt(0) != '/') {
            return;
        }
        String[] parts = raw.substring(1).split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        String arg = parts.length > 1 ? parts[1] : null;
        Player player = event.getPlayer();
        boolean handled = switch (label) {
            case "accept", "rpaccept" -> tryAccept(player, arg);
            case "deny", "rpdeny" -> tryDeny(player, arg);
            case "cancel", "cansel", "rpcancel" -> tryCancel(player);
            default -> false;
        };
        if (handled) {
            event.setCancelled(true);
        }
    }

    private boolean tryAccept(Player player, String fromName) {
        if (fromName == null || fromName.isBlank()) {
            if (duelRequestService.latestForTarget(player.getUniqueId()).isEmpty()) {
                return false;
            }
            duelCommand.handleAccept(player, null);
            return true;
        }
        Player from = resolveOnline(fromName);
        if (from == null || duelRequestService
                .latestFromSenderToTarget(from.getUniqueId(), player.getUniqueId()).isEmpty()) {
            return false;
        }
        duelCommand.handleAccept(player, from.getName());
        return true;
    }

    private boolean tryDeny(Player player, String fromName) {
        if (fromName != null && fromName.equalsIgnoreCase("all")) {
            if (duelRequestService.incoming(player.getUniqueId()).isEmpty()) {
                return false;
            }
            duelCommand.handleDeny(player, "all");
            return true;
        }
        if (fromName == null || fromName.isBlank()) {
            if (duelRequestService.latestForTarget(player.getUniqueId()).isEmpty()) {
                return false;
            }
            duelCommand.handleDeny(player, null);
            return true;
        }
        Player from = resolveOnline(fromName);
        if (from == null || duelRequestService
                .latestFromSenderToTarget(from.getUniqueId(), player.getUniqueId()).isEmpty()) {
            return false;
        }
        duelCommand.handleDeny(player, from.getName());
        return true;
    }

    private boolean tryCancel(Player player) {
        if (duelRequestService.latestOutgoing(player.getUniqueId()).isEmpty()
                && duelRequestService.latestForTarget(player.getUniqueId()).isEmpty()) {
            return false;
        }
        duelCommand.handleCancel(player);
        return true;
    }

    private static Player resolveOnline(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        return exact != null ? exact : Bukkit.getPlayer(name);
    }
}
