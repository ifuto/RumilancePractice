package com.rumilance.practice.platform;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;

/**
 * Bedrock (Geyser/Floodgate) players join with a leading {@code .} on the name.
 * Queue matchmaking is platform-split; duels stay cross-platform.
 */
public enum PlayerPlatform {
    JAVA,
    BEDROCK;

    public static PlayerPlatform of(Player player) {
        Objects.requireNonNull(player, "player");
        return ofName(player.getName());
    }

    public static PlayerPlatform ofName(String name) {
        if (name != null && name.startsWith(".")) {
            return BEDROCK;
        }
        return JAVA;
    }

    public String queueToken() {
        return name().toLowerCase(Locale.ROOT);
    }
}
