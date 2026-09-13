package com.rumilance.practice.ffa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * The admin-managed FFA command gate (default OFF — {@code /practiceadmin ffacommand}).
 *
 * <p>While the gate is enabled, FFA occupants can only run the admin-whitelisted commands,
 * and only while they are <strong>not</strong> combat-tagged: the whole point is letting
 * players open menus / check stats between fights without handing escap routes to a fighter
 * mid-combat. Admins (permission or OP) bypass the gate. With the gate off, FFA command
 * behaviour is untouched.</p>
 */
public final class FfaCommandGateListener implements Listener {

    private final FfaService ffaService;

    public FfaCommandGateListener(FfaService ffaService) {
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!ffaService.commandGateEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!ffaService.isInFfa(player.getUniqueId())) {
            return;
        }
        if (player.hasPermission("rumilance.admin") || player.isOp()) {
            return;
        }
        String raw = event.getMessage().trim();
        if (raw.isEmpty() || raw.charAt(0) != '/') {
            return;
        }
        String label = raw.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1); // "/someplugin:spawn" counts as "spawn"
        }
        if (ffaService.inCombat(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You can't use commands while in combat.",
                    NamedTextColor.RED));
            return;
        }
        if (!ffaService.isCommandWhitelisted(label)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That command is not allowed in FFA.",
                    NamedTextColor.RED));
        }
    }
}
