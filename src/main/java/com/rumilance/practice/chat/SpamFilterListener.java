package com.rumilance.practice.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Runs the spam filter before the ChatBan check ({@link EventPriority#NORMAL} vs HIGH) so a
 * spammy message is cancelled and counted even before any ban applies.
 */
public final class SpamFilterListener implements Listener {

    private final SpamFilterService spamFilterService;

    public SpamFilterListener(SpamFilterService spamFilterService) {
        this.spamFilterService = spamFilterService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (spamFilterService.isSpam(player, message)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        spamFilterService.unload(event.getPlayer().getUniqueId());
    }
}
