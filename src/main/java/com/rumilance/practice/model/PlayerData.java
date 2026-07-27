package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent profile record for a player, keyed by their Mojang UUID.
 */
public record PlayerData(UUID uuid, String username, Instant firstJoin, Instant lastSeen, String locale) {

    public PlayerData {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(firstJoin, "firstJoin");
        Objects.requireNonNull(lastSeen, "lastSeen");
    }

    public static PlayerData newProfile(UUID uuid, String username, String defaultLocale) {
        Instant now = Instant.now();
        return new PlayerData(uuid, username, now, now, defaultLocale);
    }

    public PlayerData withLastSeen(Instant newLastSeen) {
        return new PlayerData(uuid, username, firstJoin, newLastSeen, locale);
    }

    public PlayerData withLocale(String newLocale) {
        return new PlayerData(uuid, username, firstJoin, lastSeen, newLocale);
    }
}
