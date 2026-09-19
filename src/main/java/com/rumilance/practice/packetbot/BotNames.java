package com.rumilance.practice.packetbot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Names for fake players. The display name (what players see — nametag, feed, scoreboard) is
 * the fixed brand name ({@code "NARENA BOT"}); the <b>profile</b> name — the one the server
 * player list, {@code @a[name=…]} selectors and any name-based lookup actually use — is that
 * same base with a unique 5-hex suffix, so <i>any number of bots can be alive at once</i>
 * without one shadowing another (the old random-2-digit scheme collided once ~10 bots shared
 * a server).
 *
 * <p>Profile names are constrained to 16 chars of {@code [A-Za-z0-9_]}, hence the 10-char
 * base cap ({@code NARENA_BOT} is exactly 10) + {@code _} + 5 hex.</p>
 */
public final class BotNames {

    /** Suffix space: 1M values, monotonically advancing — 10 concurrent bots never collide. */
    private static final AtomicLong COUNTER =
            new AtomicLong(ThreadLocalRandom.current().nextLong(0, 0xFFFFF));

    private BotNames() {
    }

    /** Sanitises a display name into a valid profile-name base (≤ 10 chars, alphanumeric/_). */
    public static String baseOf(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "NARENA_BOT";
        }
        String base = displayName.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (base.isEmpty()) {
            return "NARENA_BOT";
        }
        return base.length() > 10 ? base.substring(0, 10) : base;
    }

    /** A unique, server-safe profile name derived from the display name (≤ 16 chars total). */
    public static String uniqueProfileName(String displayName) {
        long n = COUNTER.getAndIncrement() & 0xFFFFF;
        return baseOf(displayName) + "_" + String.format("%05x", n);
    }
}
