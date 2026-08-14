package com.rumilance.practice.cosmetic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import java.util.List;

/**
 * A cosmetic kill/win title shown as a screen-wide title when a player secures a kill or wins a
 * ranked match. Titles are unlocked by lifetime ranked wins or best-Elo thresholds; the player's
 * active selection is held in {@link TitleService}.
 */
public record KillTitle(
        String id,
        String displayName,
        Material icon,
        TextColor color,
        int requiredWins,
        int requiredElo
) {

    public static final KillTitle NONE = new KillTitle(
            "none", "None", Material.BARRIER, TextColor.color(0xAAAAAA), 0, 0);

    public KillTitle {
        if (requiredWins < 0) {
            throw new IllegalArgumentException("requiredWins must not be negative");
        }
        if (requiredElo < 0) {
            throw new IllegalArgumentException("requiredElo must not be negative");
        }
    }

    /** @return the title subtitle shown below the big screen text, e.g. "KILL!". */
    public Component mainTitle() {
        return Component.text(displayName, color).decoration(TextDecoration.ITALIC, false);
    }

    public Component subtitle(String context) {
        return Component.text(context, TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * The default, aqua-themed title ladder. Ordered from most to least easily unlocked so the
     * highest tier a player qualifies for can be found with a simple loop.
     */
    public static List<KillTitle> defaults() {
        return List.of(
                new KillTitle("rookie", "Rookie", Material.WOODEN_SWORD,
                        TextColor.color(0xAAAAAA), 0, 0),
                new KillTitle("warrior", "Warrior", Material.STONE_SWORD,
                        TextColor.color(0x55FFFF), 25, 1050),
                new KillTitle("duelist", "Duelist", Material.IRON_SWORD,
                        TextColor.color(0x55FFFF), 100, 1150),
                new KillTitle("gladiator", "Gladiator", Material.DIAMOND_SWORD,
                        TextColor.color(0x00E5FF), 250, 1300),
                new KillTitle("champion", "Champion", Material.NETHERITE_SWORD,
                        TextColor.color(0xFFD700), 500, 1500),
                new KillTitle("legend", "Legend", Material.NETHER_STAR,
                        TextColor.color(0xFF66FF), 1000, 1800)
        );
    }
}
