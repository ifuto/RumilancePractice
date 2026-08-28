package com.rumilance.practice.scoreboard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a YAML template into a legacy-section string suitable for scoreboard team prefixes.
 * Supports MiniMessage tags and {@code &} color codes; {@code {placeholders}} are substituted first.
 */
public final class ScoreboardText {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)}", Pattern.CASE_INSENSITIVE);

    private ScoreboardText() {
    }

    public static String render(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) {
            return " ";
        }
        String filled = substitute(template, vars);
        if (filled.indexOf('<') >= 0) {
            try {
                return LEGACY.serialize(MM.deserialize(filled));
            } catch (Exception ignored) {
                // fall through to ampersand
            }
        }
        return ChatColor.translateAlternateColorCodes('&', filled);
    }

    public static String substitute(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        if (template.indexOf('{') < 0) {
            return template;
        }
        Map<String, String> map = vars == null ? Map.of() : vars;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase();
            String value = map.getOrDefault(key, "");
            if (value == null) {
                value = "";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
