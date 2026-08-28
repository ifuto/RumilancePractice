package com.rumilance.practice.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/**
 * Join-only brand title: aqua {@code N Arena} with a ~50% white highlight that travels
 * left ↁEright. Minecraft titles cannot do real alpha, so the sweep is {@link TextColor#lerp}
 * from aqua toward white at 0.5 (the documented Adventure way to mix two colours).
 */
public final class WelcomeTitle {

    public static final String BRAND = "N Arena";
    public static final TextColor AQUA = NamedTextColor.AQUA;
    public static final TextColor SWEEP = TextColor.lerp(0.5f, AQUA, NamedTextColor.WHITE);

    private static final int FRAME_TICKS = 3;

    private WelcomeTitle() {
    }

    /** Brand-only title (no subtitle). Sweep then a longer hold so N Arena sticks. */
    public static void play(Plugin plugin, Player player) {
        play(plugin, player, Component.empty());
    }

    public static void play(Plugin plugin, Player player, Component subtitle) {
        Component sub = subtitle == null ? Component.empty() : subtitle;
        int last = BRAND.length() + 1;
        for (int frame = 0; frame <= last; frame++) {
            int highlight = frame;
            boolean hold = frame == last;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Title.Times times = hold
                        ? Title.Times.times(Duration.ZERO, Duration.ofMillis(3200), Duration.ofMillis(700))
                        : Title.Times.times(Duration.ZERO, Duration.ofMillis(280), Duration.ZERO);
                player.showTitle(Title.title(frame(highlight), sub, times));
            }, (long) frame * FRAME_TICKS);
        }
    }

    /**
     * Builds one sweep frame. {@code highlight} is the character index receiving the 50% white
     * mix; neighbours stay aqua. Out-of-range values render the whole word in aqua.
     */
    public static Component frame(int highlight) {
        Component out = Component.empty();
        for (int i = 0; i < BRAND.length(); i++) {
            TextColor color = i == highlight ? SWEEP : AQUA;
            out = out.append(Component.text(String.valueOf(BRAND.charAt(i)), color)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }
}
