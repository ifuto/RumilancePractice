package com.rumilance.practice.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Diagnostics for Bedrock/Floodgate joins. Never kicks — only logs so operators can see
 * whether a kick came from this plugin or from proxy/auth (Floodgate missing, etc.).
 */
public final class BedrockJoinListener implements Listener {

    private final Logger logger;

    public BedrockJoinListener(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (name != null && name.startsWith(".")) {
            logger.info("[Bedrock] pre-login " + name + " uuid=" + event.getUniqueId()
                    + " result=" + event.getLoginResult());
        }
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            logger.info("[Join] pre-login denied name=" + name + " uuid=" + event.getUniqueId()
                    + " result=" + event.getLoginResult()
                    + (event.kickMessage() == null ? "" : " reason=" + event.kickMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (PlayerPlatform.of(event.getPlayer()) != PlayerPlatform.BEDROCK) {
            return;
        }
        String name = event.getPlayer().getName();
        logger.info("[Bedrock] joined " + name + " len=" + name.length());
        if (name.length() > 16) {
            event.getPlayer().sendMessage(Component.text(
                    "Bedrock name length " + name.length() + " (Java limit was 16) — OK.",
                    NamedTextColor.DARK_AQUA));
        }
    }
}
