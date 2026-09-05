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
        boolean showMatchReport,
        boolean teamGlow,
        boolean teamColoredArmor,
        String killEffect
) {

    /**
     * Sentinel locale value meaning "the player has never explicitly chosen a language".
     * Fresh settings rows carry it; on join the language picker opens for such players and
     * the runtime locale falls back to the client locale until a choice is made. Message
     * lookups for {@code auto} fall through to the default locale, so it is safe anywhere.
     */
    public static final String LOCALE_AUTO = "auto";

    /** True when the stored locale means "not chosen yet". */
    public static boolean isLocaleUnset(String locale) {
        return locale == null || locale.isBlank() || LOCALE_AUTO.equalsIgnoreCase(locale.trim());
    }

    public PlayerSettings {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(arrowEffect, "arrowEffect");
        chatWhitelist = chatWhitelist == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(chatWhitelist));
        selectedTitle = selectedTitle == null ? "none" : selectedTitle;
        killEffect = killEffect == null ? "none" : killEffect;
    }

    /** Legacy constructor used by older call sites (match report off, team glow on, leather on). */
    public PlayerSettings(
            UUID uuid, boolean soundsEnabled, boolean scoreboardEnabled, String arrowEffect,
            boolean spectateVisible, boolean acceptDuelRequests, boolean autoRequeue,
            boolean hideOtherChat, Set<String> chatWhitelist, String locale, String selectedTitle,
            boolean showMatchReport
    ) {
        this(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale, selectedTitle,
                showMatchReport, true, true, "none");
    }

    public PlayerSettings(
            UUID uuid, boolean soundsEnabled, boolean scoreboardEnabled, String arrowEffect,
            boolean spectateVisible, boolean acceptDuelRequests, boolean autoRequeue,
            boolean hideOtherChat, Set<String> chatWhitelist, String locale, String selectedTitle
    ) {
        this(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale, selectedTitle,
                false, true, true, "none");
    }

    public static PlayerSettings defaultsFor(UUID uuid, String defaultLocale) {
        // Locale starts as the LOCALE_AUTO sentinel: the join flow shows the language picker
        // to players who never chose one. The parameter stays for signature compatibility.
        return new PlayerSettings(uuid, true, true, "none", true, true, false, false,
                Set.of(), LOCALE_AUTO, "none", false, true, true, "none");
    }

    private PlayerSettings copy(
            boolean soundsEnabled, boolean scoreboardEnabled, String arrowEffect,
            boolean spectateVisible, boolean acceptDuelRequests, boolean autoRequeue,
            boolean hideOtherChat, Set<String> chatWhitelist, String locale, String selectedTitle,
            boolean showMatchReport, boolean teamGlow, boolean teamColoredArmor, String killEffect
    ) {
        return new PlayerSettings(uuid, soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withKillEffect(String newKillEffect) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor,
                newKillEffect == null ? "none" : newKillEffect);
    }

    public PlayerSettings withLocale(String newLocale) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, newLocale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withArrowEffect(String newArrowEffect) {
        return copy(soundsEnabled, scoreboardEnabled, newArrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withSoundsEnabled(boolean enabled) {
        return copy(enabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withScoreboardEnabled(boolean enabled) {
        return copy(soundsEnabled, enabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withSpectateVisible(boolean visible) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, visible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withAcceptDuelRequests(boolean accept) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                accept, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withAutoRequeue(boolean enabled) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, enabled, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withHideOtherChat(boolean enabled) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, enabled, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withChatWhitelist(Set<String> newWhitelist) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, newWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, teamColoredArmor, killEffect);
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
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                titleId == null ? "none" : titleId, showMatchReport, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withShowMatchReport(boolean show) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, show, teamGlow, teamColoredArmor, killEffect);
    }

    public PlayerSettings withTeamGlow(boolean enabled) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, enabled, teamColoredArmor, killEffect);
    }

    public PlayerSettings withTeamColoredArmor(boolean enabled) {
        return copy(soundsEnabled, scoreboardEnabled, arrowEffect, spectateVisible,
                acceptDuelRequests, autoRequeue, hideOtherChat, chatWhitelist, locale,
                selectedTitle, showMatchReport, teamGlow, enabled, killEffect);
    }
}
