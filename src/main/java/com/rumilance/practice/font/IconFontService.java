package com.rumilance.practice.font;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.rank.PlayerRank;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Renders the server resource-pack rank badges as custom-font glyphs. The shipped pack
 * ({@code resourcepack/} in the repository) registers the images under the
 * {@code rumilance:icons} font on unassigned Private-Use-Area codepoints:
 *
 * <pre>
 *   U+E001 admin badge   U+E002 VIP badge   U+E003 VIP+ badge
 * </pre>
 *
 * <p>Team identification during team fights is deliberately NOT a pack glyph — it is a plain
 * coloured {@code ●} (see the MatchTeamVisuals prefix resolver), so it works for everyone even
 * without the resource pack. Everything here is config-driven ({@code icons.*} in config.yml)
 * so glyphs can be remapped or the whole feature disabled without touching code. Players
 * without the resource pack see a missing-glyph box; enable {@code require-resource-pack}
 * server-side to avoid that.</p>
 */
public final class IconFontService {

    private final ConfigService configService;

    public IconFontService(ConfigService configService) {
        this.configService = configService;
    }

    public boolean enabled() {
        return configService.config().getBoolean("icons.enabled", true);
    }

    /** The custom font holding the icon glyphs ({@code rumilance:icons} by default). */
    public Key font() {
        String raw = configService.config().getString("icons.font", "rumilance:icons");
        String[] parts = raw == null ? new String[0] : raw.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Key.key("rumilance", "icons");
        }
        return Key.key(parts[0], parts[1]);
    }

    /** Rank badge for the player name prefix, or {@link Component#empty()} for NORM / disabled. */
    public Component rankIcon(PlayerRank rank) {
        if (!enabled() || rank == null) {
            return Component.empty();
        }
        String glyph = switch (rank) {
            case ADMIN -> glyph("icons.glyphs.admin", "\uE001");
            case VIP -> glyph("icons.glyphs.vip", "\uE002");
            case VIP_PLUS -> glyph("icons.glyphs.vip-plus", "\uE003");
            default -> null;
        };
        if (glyph == null || glyph.isEmpty()) {
            return Component.empty();
        }
        return icon(glyph);
    }

    private String glyph(String path, String fallback) {
        return configService.config().getString(path, fallback);
    }

    private Component icon(String glyph) {
        return Component.text(glyph).style(style -> style.font(font()));
    }
}
