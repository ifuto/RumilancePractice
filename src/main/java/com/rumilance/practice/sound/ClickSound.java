package com.rumilance.practice.sound;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The plain vanilla button click ({@code ui.button.click}), played DIRECTLY on the player —
 * deliberately NOT routed through {@link SoundService}/sounds.yml (user spec 2026-10-04:
 * 指定された音は sounds.yml 関係なく、そのまま鳴らす). Covers the interactions the owner
 * pinned to this exact sound: the KIT SELECT GUI chip actions (assign / Active / edit /
 * reset), functional ({@code /setfunc}) item right-click opens and the queue-join kit tap.
 */
public final class ClickSound {

    private ClickSound() {
    }

    /** Plays ui.button.click at volume 1.0 / pitch 1.0, unconditionally. */
    public static void play(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }
}
