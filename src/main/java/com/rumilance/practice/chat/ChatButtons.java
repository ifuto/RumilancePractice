package com.rumilance.practice.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Tiny factory for the plugin's clickable chat buttons — the aqua-themed
 * {@code [Label]} components used across duel requests, team invites and
 * post-match actions. Keeping them here guarantees every button looks and
 * behaves the same (hover tooltip + run-command click) instead of each call
 * site hand-rolling its own style.
 */
public final class ChatButtons {

    /** House accent (aqua/water). */
    public static final TextColor ACCENT = TextColor.color(0x55FFFF);

    private ChatButtons() {
    }

    /** An aqua-accented {@code [Label]} that runs {@code command} when clicked. */
    public static Component button(String label, String command, String hover) {
        return button(label, command, hover, ACCENT);
    }

    /** A green "positive" button (accept / join / yes). */
    public static Component accept(String label, String command, String hover) {
        return button(label, command, hover, NamedTextColor.GREEN);
    }

    /** A red "negative" button (decline / leave / no). */
    public static Component decline(String label, String command, String hover) {
        return button(label, command, hover, NamedTextColor.RED);
    }

    /** A muted secondary button (info / optional actions). */
    public static Component subtle(String label, String command, String hover) {
        return button(label, command, hover, NamedTextColor.GRAY);
    }

    public static Component button(String label, String command, String hover, TextColor color) {
        Component base = Component.text("[" + label + "]", color)
                .decoration(TextDecoration.ITALIC, false)
                .clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isBlank()) {
            base = base.hoverEvent(HoverEvent.showText(
                    Component.text(hover, NamedTextColor.GRAY)));
        }
        return base;
    }

    /** Joins several buttons with a single space between them. */
    public static Component row(Component... buttons) {
        Component out = Component.empty();
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) {
                out = out.append(Component.text(" "));
            }
            out = out.append(buttons[i]);
        }
        return out;
    }
}
