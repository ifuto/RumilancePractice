package com.rumilance.practice.herobot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the reference mod's <b>entity-selector extensions</b> into plain vanilla lines.
 *
 * <p>The reference server gets {@code distanceH=…} / {@code distanceV=…} from a mixin pair
 * ({@code EntitySelectorOptionsMixin} registers the options, {@code EntitySelectorMixin} filters
 * the selector's result list by {@code dx²+dz²} against {@code matchesSqr} / {@code |dy|} against
 * {@code matches}, origin = the command source position). Paper has no mixin stage, so the port
 * does the same job one level up: a function line
 *
 * <pre>{@code execute <prefix> unless entity @a[tag=xlib_target,distanceH=..4] if score … run …}</pre>
 *
 * becomes two ordinary lines, evaluated in the same context:
 *
 * <pre>{@code
 * execute <prefix> store result score .qd quantum_tmp run hfilter @a[tag=xlib_target] h ..4
 * execute <prefix> if score .qd quantum_tmp matches 0 if score … run …}</pre>
 *
 * {@code hfilter} (see {@link HeroBotDistanceCommand}) applies exactly the reference's filter, so
 * the condition keeps the reference's meaning — {@code if} becomes {@code matches 1..},
 * {@code unless} becomes {@code matches 0}.
 *
 * <p>Both commands are side-effect free apart from the score, which is why the substitution is
 * safe even though the {@code execute} prefix is evaluated twice.
 */
public final class HeroBotLineRewriter {

    private static final Pattern DISTANCE_OPTION =
            Pattern.compile("(distanceH|distanceV)=([^\\s,\\]]+)");
    private static final Pattern CONDITION =
            Pattern.compile("\\b(if|unless)\\s+entity\\s*$");

    private HeroBotLineRewriter() {
    }

    /** One rewritten line, plus any note explaining why it could not be rewritten. */
    public record Result(List<String> lines, String note) {
    }

    /**
     * Rewrites a single magic-function line, or returns {@code null} when the line does not use a
     * mod-added selector option (the common case — this must stay cheap).
     */
    public static Result rewrite(String line) {
        if (!line.contains("distanceH=") && !line.contains("distanceV=")) {
            return null;
        }
        int start = findSelectorWithOption(line);
        if (start < 0) {
            return new Result(null, "selector option outside @<type>[...]");
        }
        int open = line.indexOf('[', start);
        int close = matchingBracket(line, open);
        if (close < 0) {
            return new Result(null, "unterminated selector");
        }
        String selector = line.substring(start, close + 1);
        List<String> horizontal = new ArrayList<>();
        List<String> vertical = new ArrayList<>();
        Matcher matcher = DISTANCE_OPTION.matcher(selector);
        while (matcher.find()) {
            (matcher.group(1).equals("distanceH") ? horizontal : vertical).add(matcher.group(2));
        }
        if (horizontal.size() > 1 || vertical.size() > 1) {
            return new Result(null, "repeated bounds in one selector");
        }
        Matcher condition = CONDITION.matcher(line.substring(0, start));
        if (!condition.find()) {
            return new Result(null, "selector option not used as 'if|unless entity'");
        }
        int conditionStart = condition.start();
        String prefix = line.substring(0, conditionStart);
        String trailer = line.substring(close + 1);
        if (prefix.contains("store ")) {
            return new Result(null, "'execute store' prefix cannot be evaluated twice");
        }
        String stripped = DISTANCE_OPTION.matcher(selector).replaceAll("");
        stripped = stripped.replace("[,", "[").replace(",,", ",").replace(",]", "]");
        if (stripped.endsWith("[]")) {
            stripped = stripped.substring(0, stripped.length() - 2);   // "@a[]" is not a selector
        }

        StringBuilder test = new StringBuilder(prefix);
        if (test.length() > 0 && test.charAt(test.length() - 1) != ' ') {
            test.append(' ');
        }
        test.append("store result score ").append(HeroBotDistanceCommand.TEMP_HOLDER).append(' ')
                .append(HeroBotDistanceCommand.TEMP_OBJECTIVE).append(" run hfilter ")
                .append(stripped);
        if (!horizontal.isEmpty()) {
            test.append(" h ").append(horizontal.get(0));
        }
        if (!vertical.isEmpty()) {
            test.append(" v ").append(vertical.get(0));
        }

        String kind = condition.group(1);
        String guard = prefix + "if score " + HeroBotDistanceCommand.TEMP_HOLDER + ' '
                + HeroBotDistanceCommand.TEMP_OBJECTIVE + " matches "
                + (kind.equals("if") ? "1.." : "0") + trailer;
        return new Result(List.of(test.toString(), guard), null);
    }

    /** Index of the {@code '@'} of the first selector that carries a distance option. */
    private static int findSelectorWithOption(String line) {
        int index = line.indexOf('@');
        while (index >= 0) {
            int open = line.indexOf('[', index);
            int space = line.indexOf(' ', index);
            if (open < 0 || (space >= 0 && space < open)) {
                // a plain @p/@s/@a/@e/@n without options cannot carry one
                index = line.indexOf('@', index + 1);
                continue;
            }
            int close = matchingBracket(line, open);
            if (close > 0) {
                String selector = line.substring(open, close + 1);
                if (selector.contains("distanceH=") || selector.contains("distanceV=")) {
                    return index;
                }
                index = line.indexOf('@', close + 1);
                continue;
            }
            index = line.indexOf('@', index + 1);
        }
        return -1;
    }

    /** Vanilla selectors do not nest brackets, but strings inside them can contain them. */
    private static int matchingBracket(String line, int open) {
        boolean quoted = false;
        for (int i = open + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ']' && !quoted) {
                return i;
            }
        }
        return -1;
    }
}
