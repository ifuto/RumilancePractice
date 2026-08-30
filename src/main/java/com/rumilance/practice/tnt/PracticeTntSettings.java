package com.rumilance.practice.tnt;

import com.rumilance.practice.config.ConfigService;

/**
 * Live {@code practice-tnt.*} snapshot. Reads {@link ConfigService} on each call so
 * {@code /rpadmin reload} applies without reconstructing listeners.
 */
public final class PracticeTntSettings {

    private final ConfigService configService;

    public PracticeTntSettings(ConfigService configService) {
        this.configService = configService;
    }

    public boolean enabled() {
        return configService.config().getBoolean("practice-tnt.enabled", true);
    }

    public int tntFuseTicks() {
        return PracticeTnt.clampFuseTicks(
                configService.config().getInt("practice-tnt.tnt-fuse-ticks", PracticeTnt.DEFAULT_FUSE_TICKS));
    }

    public boolean creeperFromEgg() {
        return configService.config().getBoolean("practice-tnt.creeper-from-egg.enabled", true);
    }

    public int creeperFuseTicks() {
        return PracticeTnt.clampFuseTicks(
                configService.config().getInt("practice-tnt.creeper-from-egg.fuse-ticks", PracticeTnt.DEFAULT_FUSE_TICKS));
    }
}
