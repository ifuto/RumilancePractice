package com.rumilance.practice.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Clickable chat actions for duel requests, party invites, and post-match links.
 * Soft modern chips — no plugin-name tags, light brackets only for click affordance.
 */
public final class ChatButtons {

    /** House accent (soft cyan). */
    public static final TextColor ACCENT = TextColor.color(0x5EEAD4);

    private ChatButtons() {
    }

    /** An aqua-accented label that runs {@code command} when clicked. */
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
        String cmd = command.startsWith("/") ? command : "/" + command;
        Component base = Component.text("[", TextColor.color(0x4B5563))
                .append(Component.text(label, color))
                .append(Component.text("]", TextColor.color(0x4B5563)))
                .decoration(TextDecoration.ITALIC, false)
                .clickEvent(ClickEvent.runCommand(cmd));
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
