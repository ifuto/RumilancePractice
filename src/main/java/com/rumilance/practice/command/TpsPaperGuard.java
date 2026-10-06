package com.rumilance.practice.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Keeps every {@code /tps} spelling pointing at this plugin's own readout.
 *
 * <p>Declaring {@code tps} in {@code plugin.yml} already wins the bare {@code /tps} — plugin
 * commands shadow the server's. Paper's built-in chart command is still reachable through its
 * namespace ({@code /paper:tps}, {@code /minecraft:tps}, …), so those are cancelled here.</p>
 *
 * <p>The intent: a normal player may read the TPS percentage (the point of our implementation),
 * but must not reach Paper's admin-oriented output. Staff keep both.</p>
 */
public final class TpsPaperGuard implements Listener {

    private static final Set<String> NAMESPACED =
            Set.of("minecraft:tps", "paper:tps", "bukkit:tps", "spigot:tps");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("rumilance.admin")) {
            return;
        }
        String raw = event.getMessage().trim();
        if (raw.isEmpty() || raw.charAt(0) != '/') {
            return;
        }
        String label = raw.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!NAMESPACED.contains(label)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Component.text("Use /tps instead.", NamedTextColor.YELLOW));
    }
}
