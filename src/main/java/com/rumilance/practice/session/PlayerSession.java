package com.rumilance.practice.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime, in-memory view of a currently online player's practice-related state. Backed by
 * (but distinct from) their persisted {@code PlayerData}/{@code PlayerSettings} rows.
 */
public final class PlayerSession {

    private final UUID uuid;
    private volatile String locale;
    private volatile String selectedKit;
    private volatile String arrowEffect;
    private volatile UUID currentMatchId;
    private volatile boolean soundsEnabled;
    private volatile boolean scoreboardEnabled;

    public PlayerSession(UUID uuid, String locale) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.arrowEffect = "none";
        this.soundsEnabled = true;
        this.scoreboardEnabled = true;
    }

    public UUID uuid() {
        return uuid;
    }

    public String locale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    public String selectedKit() {
        return selectedKit;
    }

    public void setSelectedKit(String selectedKit) {
        this.selectedKit = selectedKit;
    }

    public String arrowEffect() {
        return arrowEffect;
    }

    public void setArrowEffect(String arrowEffect) {
        this.arrowEffect = Objects.requireNonNull(arrowEffect, "arrowEffect");
    }

    public UUID currentMatchId() {
        return currentMatchId;
    }

    public void setCurrentMatchId(UUID currentMatchId) {
        this.currentMatchId = currentMatchId;
    }

    public boolean inMatch() {
        return currentMatchId != null;
    }

    public boolean soundsEnabled() {
        return soundsEnabled;
    }

    public void setSoundsEnabled(boolean soundsEnabled) {
        this.soundsEnabled = soundsEnabled;
    }

    public boolean scoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean scoreboardEnabled) {
        this.scoreboardEnabled = scoreboardEnabled;
    }
}
