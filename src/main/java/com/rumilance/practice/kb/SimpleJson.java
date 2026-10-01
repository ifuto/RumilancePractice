package com.rumilance.practice.kb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON parser (objects / arrays / strings with escapes / numbers of
 * double precision / true / false / null). Written for the KB profile files, whose
 * content is authored by hand or pasted from the KB Probe mod — no streaming edge
 * cases are required, only correctness and useful error positions. Zero-dependency
 * on purpose: the plugin deliberately avoids adding a JSON library for one directory.
 * Local-test runnable (pure JDK).
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw p.error("trailing characters after JSON value");
        }
        return value;
    }

    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?> map)) {
            throw new JsonException("root value is not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) map;
        return out;
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        JsonException error(String what) {
            return new JsonException(what + " at offset " + i);
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String word, Object value) {
            if (s.startsWith(word, i)) {
                i += word.length();
                return value;
            }
            throw error("expected '" + word + "'");
        }

        private Map<String, Object> parseObject() {
            i++; // '{'
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            if (!atEnd() && s.charAt(i) == '}') {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                if (atEnd() || s.charAt(i) != '"') {
                    throw error("expected string key");
                }
                String key = parseString();
                skipWs();
                if (atEnd() || s.charAt(i) != ':') {
                    throw error("expected ':'");
                }
                i++;
                out.put(key, parseValue());
                skipWs();
                if (atEnd()) {
                    throw error("unexpected end of input in object");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    return out;
                }
                throw error("expected ',' or '}'");
            }
        }

        private List<Object> parseArray() {
            i++; // '['
            List<Object> out = new ArrayList<>();
            skipWs();
            if (!atEnd() && s.charAt(i) == ']') {
                i++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWs();
                if (atEnd()) {
                    throw error("unexpected end of input in array");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    return out;
                }
                throw error("expected ',' or ']'");
            }
        }

        private String parseString() {
            i++; // '"'
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("unterminated escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw error("truncated \\u escape");
                            }
                            String hex = s.substring(i, i + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException bad) {
                                throw error("invalid \\u escape '" + hex + "'");
                            }
                            i += 4;
                        }
                        default -> throw error("invalid escape '\\" + e + "'");
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private Object parseNumber() {
            int start = i;
            if (!atEnd() && s.charAt(i) == '-') {
                i++;
            }
            while (!atEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (!atEnd() && s.charAt(i) == '.') {
                i++;
                while (!atEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (!atEnd() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (!atEnd() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                while (!atEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (start == i) {
                throw error("expected a value");
            }
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException bad) {
                throw error("invalid number '" + s.substring(start, i) + "'");
            }
        }
    }
}
