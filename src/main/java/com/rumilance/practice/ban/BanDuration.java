package com.rumilance.practice.ban;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Staff duration tokens: {@code 7d}, {@code 2w}, {@code 1mo}. Empty / missing = permanent.
 */
public final class BanDuration {

    private static final Pattern TOKEN = Pattern.compile("(?i)^(\\d+)(d|w|mo)$");

    private BanDuration() {
    }

    public static boolean looksLike(String token) {
        return token != null && (token.equalsIgnoreCase("auto") || TOKEN.matcher(token).matches());
    }

    /**
     * Grim / staff {@code auto}: 1st = 2 weeks, 2nd = 2 months, 3rd = 3 months, 4th+ = permanent.
     * {@code offenseNumber} is 1-based (the ban being issued now).
     */
    public static Duration forOffenseNumber(int offenseNumber) {
        return switch (offenseNumber) {
            case 1 -> Duration.ofDays(14L);
            case 2 -> Duration.ofDays(60L);
            case 3 -> Duration.ofDays(90L);
            default -> null;
        };
    }

    public static String autoToken(int offenseNumber) {
        return switch (offenseNumber) {
            case 1 -> "2w";
            case 2 -> "2mo";
            case 3 -> "3mo";
            default -> null;
        };
    }

    public static Optional<Duration> parse(String token) {
        if (token == null || token.isBlank() || token.equalsIgnoreCase("auto")) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN.matcher(token.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0L) {
            return Optional.empty();
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        Duration duration = switch (unit) {
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(amount * 7L);
            case "mo" -> Duration.ofDays(amount * 30L);
            default -> null;
        };
        return Optional.ofNullable(duration);
    }

    public static String label(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "Permanent";
        }
        return labelFromToken(null, duration);
    }

    public static String labelFromToken(String token, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "Permanent";
        }
        if (token != null) {
            Matcher matcher = TOKEN.matcher(token.trim());
            if (matcher.matches()) {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2).toLowerCase(Locale.ROOT);
                return switch (unit) {
                    case "d" -> amount + (amount == 1L ? " day" : " days");
                    case "w" -> amount + (amount == 1L ? " week" : " weeks");
                    case "mo" -> amount + (amount == 1L ? " month" : " months");
                    default -> label(duration);
                };
            }
        }
        long days = duration.toDays();
        if (days > 0) {
            return days + (days == 1 ? " day" : " days");
        }
        return duration.toHours() + " hours";
    }

    public static String remaining(long expiresAtEpochMilli, long nowEpochMilli) {
        if (expiresAtEpochMilli <= 0L) {
            return "Permanent";
        }
        long left = expiresAtEpochMilli - nowEpochMilli;
        if (left <= 0L) {
            return "Expired";
        }
        long days = left / 86_400_000L;
        long hours = (left % 86_400_000L) / 3_600_000L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        long minutes = (left % 3_600_000L) / 60_000L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }
}
