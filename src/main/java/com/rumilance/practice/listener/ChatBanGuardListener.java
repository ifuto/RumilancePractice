package com.rumilance.practice.listener;

import com.rumilance.practice.punishment.ChatBanService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Hard chat-ban gate. Runs at {@link EventPriority#LOWEST} WITHOUT {@code ignoreCancelled}
 * so a banned player's message is cancelled before any other chat handling (whitelist
 * intercept, hide-chat viewers, other plugins) can let it through. The generic chat handler
 * in {@code PracticeSideListener} must never be the only gate.
 */
public final class ChatBanGuardListener implements Listener {

    private final ChatBanService chatBanService;

    public ChatBanGuardListener(ChatBanService chatBanService) {
        this.chatBanService = chatBanService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        if (player.hasPermission("rumilance.punishment.bypass")) {
            return;
        }
        if (!chatBanService.isChatBanned(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Component.text(
                "You are ChatBanned. /objection <reason>", NamedTextColor.RED));
    }
}
