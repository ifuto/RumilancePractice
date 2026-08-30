package com.rumilance.practice.ffa;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code /ffa resettime} tokens such as {@code 30s}, {@code 5min}, {@code 2hour}, {@code off}.
 */
public final class FfaResetTimes {

    private static final Pattern TOKEN = Pattern.compile("(?i)^(\\d+)\\s*(sec|secs|s|min|mins|m|hour|hours|h)?$");

    private FfaResetTimes() {
    }

    public static OptionalInt parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        String token = raw.trim();
        if (token.equalsIgnoreCase("off") || token.equalsIgnoreCase("disable")
                || token.equalsIgnoreCase("none") || token.equals("0")) {
            return OptionalInt.of(0);
        }
        Matcher matcher = TOKEN.matcher(token);
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount < 0L) {
            return OptionalInt.empty();
        }
        String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.ROOT);
        long seconds = switch (unit) {
            case "m", "min", "mins" -> amount * 60L;
            case "h", "hour", "hours" -> amount * 3600L;
            default -> amount;
        };
        if (seconds > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) seconds);
    }

    public static String format(int seconds) {
        if (seconds <= 0) {
            return "off";
        }
        if (seconds % 3600 == 0) {
            int hours = seconds / 3600;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        if (seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
