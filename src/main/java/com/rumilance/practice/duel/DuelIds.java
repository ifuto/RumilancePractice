package com.rumilance.practice.duel;

/**
 * Five-character case-sensitive IDs in {@code 0-9A-Za-z} ({@code 62^5} unique values).
 */
public final class DuelIds {

    private static final char[] ABC =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = ABC.length;
    public static final int LENGTH = 5;

    private DuelIds() {
    }

    public static String encode(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("value");
        }
        char[] out = new char[LENGTH];
        long n = value;
        for (int i = LENGTH - 1; i >= 0; i--) {
            out[i] = ABC[(int) (n % BASE)];
            n /= BASE;
        }
        return new String(out);
    }

    public static long decode(String id) {
        if (!valid(id)) {
            return -1L;
        }
        long n = 0L;
        for (int i = 0; i < LENGTH; i++) {
            int digit = indexOf(id.charAt(i));
            if (digit < 0) {
                return -1L;
            }
            n = n * BASE + digit;
        }
        return n;
    }

    public static boolean valid(String id) {
        if (id == null || id.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            if (indexOf(id.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return 10 + (c - 'A');
        }
        if (c >= 'a' && c <= 'z') {
            return 36 + (c - 'a');
        }
        return -1;
    }
}
