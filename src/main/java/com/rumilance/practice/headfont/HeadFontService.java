package com.rumilance.practice.headfont;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Renders a real player-head icon inline in any text component (chat, action bar, title, …)
 * using the <strong>vanilla player-sprite object component</strong> exposed by MiniMessage's
 * {@code <head:…>} tag (Adventure 4.25+, client 1.21.9+).
 *
 * <p>No resource pack, font or HTTP hosting is required: the client resolves the skin from the
 * player name/UUID itself, exactly like the vanilla chat profile hover. We simply emit
 * {@code <head:uuid>} for each fighter.</p>
 */
public final class HeadFontService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public HeadFontService() {
    }

    /** Inline player-head for the given online player (hat/overlay layer enabled). */
    public Component head(Player player) {
        return head(player.getUniqueId());
    }

    /** Inline player-head for a UUID (the client resolves the skin). */
    public Component head(UUID uuid) {
        try {
            return MM.deserialize("<head:" + uuid + ">");
        } catch (RuntimeException e) {
            // Older/edge client: fall back to a small space so layout stays intact.
            return Component.text(" ");
        }
    }

    /** Static convenience for callers that don't need a service instance. */
    public static Component of(UUID uuid) {
        try {
            return MM.deserialize("<head:" + uuid + ">");
        } catch (RuntimeException e) {
            return Component.text(" ");
        }
    }
}
