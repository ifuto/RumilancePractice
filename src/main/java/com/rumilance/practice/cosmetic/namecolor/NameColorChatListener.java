package com.rumilance.practice.cosmetic.namecolor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Colors a VIP+ player's name in chat according to their name-color selection. Runs at LOW
 * priority so later handlers (team chat viewer filtering etc.) still see the styled renderer.
 */
public final class NameColorChatListener implements Listener {

    private final NameColorService nameColorService;

    public NameColorChatListener(NameColorService nameColorService) {
        this.nameColorService = nameColorService;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        if (!nameColorService.hasCustomColor(event.getPlayer().getUniqueId())) {
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty()
                .append(Component.text("<", NamedTextColor.WHITE))
                .append(nameColorService.styledName(source))
                .append(Component.text("> ", NamedTextColor.WHITE))
                .append(message));
    }
}
