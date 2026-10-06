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
 *   U+E001 admin badge   U+E002 VIP badge   U+E003 VIP+ badge   U+E004 PRO badge
 * </pre>
 *
 * <p>The pack must NOT ship {@code assets/minecraft/font/default.json} /
 * {@code uniform.json}: those are reserved ids and override the vanilla font instead of
 * extending it.</p>
 *
 * <p>Team identification during team fights is deliberately NOT a pack glyph — it is a plain
 * coloured {@code ●} (see the MatchTeamVisuals prefix resolver), so it works for everyone even
 * without the resource pack. Everything here is config-driven ({@code icons.*} in config.yml)
 * so glyphs can be remapped or the whole feature disabled without touching code. The badges are
 * <strong>image-only</strong>: there is no text fallback, so a client that never applied the
 * pack simply sees no badge — the pack policy (required = kick on decline, recommended = join
 * anyway) is chosen in the admin GUI ({@code resource-pack.*} in config.yml,
 * {@link com.rumilance.practice.resourcepack.ResourcePackService}).</p>
 */
public final class IconFontService {

    private final ConfigService configService;

    public IconFontService(ConfigService configService) {
        this.configService = configService;
    }

    public boolean enabled() {
        return configService.config().getBoolean("icons.enabled", true);
    }

    /**
     * The font the icon glyphs render with.
     *
     * <p>{@code minecraft:default}, {@code minecraft:uniform} and {@code minecraft:alt} are
     * <strong>reserved</strong> ids: a resource pack that ships one of those font files
     * replaces the vanilla font outright rather than adding to it, so every ordinary glyph
     * (letters, digits, spaces) loses its definition and all server text renders blank.
     * The pack therefore only ever registers its glyphs under its own namespace,
     * {@code rumilance:icons}, which is additive and cannot break vanilla text.</p>
     *
     * <p>Blank / {@code default} / {@code minecraft:default} means "send no font attribute",
     * which only renders if the glyph happened to be merged into the vanilla font — it is
     * not how this pack works, but an admin is free to point this at any font id they have
     * registered themselves.</p>
     */
    public Key font() {
        String raw = configService.config().getString("icons.font", "rumilance:icons");
        if (raw == null || raw.isBlank() || "default".equalsIgnoreCase(raw.trim())
                || "minecraft:default".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String[] parts = raw.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return Key.key(parts[0], parts[1]);
    }

    /**
     * Rank badge shown in front of a player name, or {@link Component#empty()} for NORM /
     * disabled. Always the resource-pack glyph: the badges are image-only by design, so nothing
     * here ever degrades into text such as {@code OWNER} / {@code N} / {@code N+} (which used to
     * leak a "text-style" prefix into the TAB list and nametags).
     */
    public Component rankIcon(PlayerRank rank) {
        if (!enabled() || rank == null) {
            return Component.empty();
        }
        String glyph = switch (rank) {
            case ADMIN -> glyph("icons.glyphs.admin", "\uE001");
            case VIP -> glyph("icons.glyphs.vip", "\uE002");
            case VIP_PLUS -> glyph("icons.glyphs.vip-plus", "\uE003");
            case PRO -> glyph("icons.glyphs.pro", "\uE004");
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
        Key font = font();
        if (font == null) {
            return Component.text(glyph);
        }
        // The glyph is only defined in rumilance:icons, so the font has to be named
        // explicitly — without it the client looks the codepoint up in the vanilla
        // default font, where it does not exist.
        return Component.text(glyph).style(style -> style.font(font));
    }
}
