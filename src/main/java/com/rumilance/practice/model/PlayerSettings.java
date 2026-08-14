package com.rumilance.practice.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Personal, player-configurable preferences (see {@code profile.yml} for server-wide defaults).
 * Backs the {@code /setting} GUI implemented in {@code com.rumilance.practice.settings}.
 */
public record PlayerSettings(
        UUID uuid,
        boolean soundsEnabled,
        boolean scoreboardEnabled,
        String arrowEffect,
        boolean spectateVisible,
        boolean acceptDuelRequests,
        boolean autoRequeue,
        boolean hideOtherChat,
        Set<String> chatWhitelist,
        String locale,
        String selectedTitle,
        boolean showMatchReport
) {

    public PlayerSettings {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(arrowEffect, "arrowEffect");
        chatWhitelist = chatWhitelist == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(chatWhitelist));
        selectedTitle = selectedTitle == null ? "none" : selectedTitle;
    }

    public PlayerSettings(
            UUID uuid, boolean soundsEnabled, boolean scoreboardEnabled, String arrowEffect,
            boolean spectateVisible, boolean acceptDuelRequests, boolean autoRequeue,
            boolean hideOtherChat, Set<String> chatWhitelist, String locale, String selectedTitle
    ) {
        this(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale, selectedTitle, false);
    }

    public static PlayerSettings defaultsFor(UUID uuid, String defaultLocale) {
        return new PlayerSettings(uuid, true, true, "none", true, true, false, false,
                Set.of(), defaultLocale, "none", false);
    }

    public PlayerSettings withLocale(String newLocale) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, newLocale);
    }

    public PlayerSettings withArrowEffect(String newArrowEffect) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, newArrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale);
    }

    public PlayerSettings withSoundsEnabled(boolean enabled) {
        return new PlayerSettings(uuid, enabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale);
    }

    public PlayerSettings withScoreboardEnabled(boolean enabled) {
        return new PlayerSettings(uuid, soundsEnabled, enabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale);
    }

    public PlayerSettings withSpectateVisible(boolean visible) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, visible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale);
    }

    public PlayerSettings withAcceptDuelRequests(boolean accept) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                accept, autoRequeue, hideOtherChat, chatWhitelist, locale);
    }

    public PlayerSettings withAutoRequeue(boolean enabled) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, enabled, hideOtherChat, chatWhitelist, locale);
    }

    public PlayerSettings withHideOtherChat(boolean enabled) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, enabled, chatWhitelist, locale);
    }

    public PlayerSettings withChatWhitelist(Set<String> newWhitelist) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, newWhitelist, locale);
    }

    public PlayerSettings withChatWhitelistAdded(String name) {
        Set<String> updated = new LinkedHashSet<>(chatWhitelist);
        updated.add(name.toLowerCase(java.util.Locale.ROOT));
        return withChatWhitelist(updated);
    }

    public PlayerSettings withChatWhitelistRemoved(String name) {
        Set<String> updated = new LinkedHashSet<>(chatWhitelist);
        updated.remove(name.toLowerCase(java.util.Locale.ROOT));
        return withChatWhitelist(updated);
    }

    public PlayerSettings withSelectedTitle(String titleId) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                titleId == null ? "none" : titleId, showMatchReport);
    }

    public PlayerSettings withShowMatchReport(boolean show) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, show);
    }
}
