package com.rumilance.practice.resourcepack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal flat-JSON reader/writer for {@code resource-pack.json} — the operator-owned file that
 * holds the pack URL ({@code plugins/NARENA/resource-pack.json}).
 *
 * <p>Deliberately dependency-free and flat: the file has a handful of scalar keys
 * ({@code url}, {@code prompt}, {@code sha1}, {@code required}, {@code min-client-protocol}),
 * so a strict JSON parser would be overkill. String escapes, numbers, booleans and {@code null}
 * are understood; nested objects/arrays are ignored rather than mis-parsed.</p>
 */
public final class ResourcePackJson {

    private ResourcePackJson() {
    }

    /**
     * Parses a flat JSON object into key -> raw value pairs (strings unescaped). Unknown nested
     * structures keep their raw text; a malformed document returns whatever was read so far, so
     * one stray character can never wipe the operator's settings.
     */
    public static Map<String, String> parse(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        if (json == null) {
            return values;
        }
        int i = 0;
        int length = json.length();
        int depth = 0;
        while (i < length) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                depth--;
                i++;
                continue;
            }
            if (c == ',') {
                i++;
                continue;
            }
            if (c == '"') {
                int[] end = new int[1];
                String key = readString(json, i, end);
                i = end[0];
                int colon = skipWhitespace(json, i);
                if (colon < length && json.charAt(colon) == ':' && depth == 1) {
                    i = skipWhitespace(json, colon + 1);
                    if (i < length && json.charAt(i) == '"') {
                        String value = readString(json, i, end);
                        values.put(key, value);
                        i = end[0];
                    } else if (i < length && (json.charAt(i) == '{' || json.charAt(i) == '[')) {
                        int close = skipNested(json, i);
                        values.put(key, json.substring(i, close));
                        i = close;
                    } else {
                        int stop = i;
                        while (stop < length && json.charAt(stop) != ',' && json.charAt(stop) != '}') {
                            stop++;
                        }
                        values.put(key, json.substring(i, stop).trim());
                        i = stop;
                    }
                    continue;
                }
                continue;
            }
            i++;
        }
        return values;
    }

    /** Pretty flat writer: one key per line, stable key order, values as given. */
    public static String write(Map<String, String> values) {
        StringBuilder out = new StringBuilder("{\n");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.append("  \"").append(escape(entry.getKey())).append("\": ");
            String raw = entry.getValue();
            out.append(raw == null ? "null" : raw);
            if (++index < values.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        return out.append("}\n").toString();
    }

    public static String quote(String text) {
        return text == null ? "null" : "\"" + escape(text) + "\"";
    }

    private static int skipWhitespace(String json, int from) {
        int i = from;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Index just past the object/array that starts at {@code from} (nesting aware). */
    private static int skipNested(String json, int from) {
        int depth = 0;
        boolean inString = false;
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return json.length();
    }

    /** Reads a quoted string starting at {@code start}; writes the index past it into {@code end}. */
    private static String readString(String json, int start, int[] end) {
        StringBuilder value = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            try {
                                value.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                // keep the literal backslash sequence
                            }
                        }
                    }
                    default -> value.append(next);
                }
                i += 2;
                continue;
            }
            if (c == '"') {
                end[0] = i + 1;
                return value.toString();
            }
            value.append(c);
            i++;
        }
        end[0] = json.length();
        return value.toString();
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
