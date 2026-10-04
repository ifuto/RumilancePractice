package com.rumilance.practice.join;

import org.bukkit.event.player.PlayerKickEvent;

/**
 * Kick/ban leave silence only. Since 1.92.33 no join/quit line is shown anywhere any more
 * (user spec 2026-10-04) — the old {@code [+] name} / {@code [-] name} broadcast is gone, so
 * the only presence line this class still manages is the one following a kick screen: kicked
 * or banned players leave with no chat line at all.
 */
public final class JoinQuitMessages {

    private JoinQuitMessages() {
    }

    /** Kicked/banned players leave silently — no {@code [-] name} line in chat. */
    public static void apply(org.bukkit.event.player.PlayerKickEvent event) {
        event.leaveMessage(null);
    }
}
