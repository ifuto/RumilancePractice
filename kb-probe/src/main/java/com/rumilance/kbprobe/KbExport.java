package com.rumilance.kbprobe;

/**
 * Builds the knockback-reproduction JSON for one measured server — the exact document the
 * server-side plugin ({@code plugins/n-arena/kb/<Name>.json}) loads for duel-request KB
 * selection. Format contract (2026-10-01):
 *
 * <pre>{@code
 * {
 *   "name": "pvpclub.net",
 *   "horizontal": 1.2384,
 *   "vertical": 0.5123,
 *   "source": { "mod": "kb-probe 0.5.0", "samplesH": 128, "samplesV": 96 }
 * }
 * }</pre>
 *
 * The plugin only requires {@code horizontal}/{@code vertical} (multiplicative factors on
 * top of Paper's final knockback vector); the {@code source} block is provenance metadata.
 * Saved as {@code <Name>.json}, the selector entry shows the file name — so copy, save as
 * {@code PvPClub.json} and the duel-request KB menu lists "PvPClub".
 */
public final class KbExport {

    private KbExport() {
    }

    public static String export(String serverKey, ServerStats stats) {
        double fH = stats.hSamples > 0 ? stats.sumHF / stats.hSamples : Double.NaN;
        double fV = stats.vSamples > 0 ? stats.sumVF / stats.vSamples : Double.NaN;
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"name\": \"").append(escape(serverKey)).append("\",\n");
        out.append("  \"horizontal\": ").append(Double.isNaN(fH) ? "null" : round4(fH)).append(",\n");
        out.append("  \"vertical\": ").append(Double.isNaN(fV) ? "null" : round4(fV)).append(",\n");
        out.append("  \"source\": {\n");
        out.append("    \"mod\": \"kb-probe 0.5.0\",\n");
        out.append("    \"samplesH\": ").append(stats.hSamples).append(",\n");
        out.append("    \"samplesV\": ").append(stats.vSamples).append(",\n");
        out.append("    \"noKbEvents\": ").append(stats.noKbEvents).append(",\n");
        out.append("    \"contaminatedEvents\": ").append(stats.contaminatedEvents).append("\n");
        out.append("  }\n");
        out.append("}\n");
        return out.toString();
    }

    /** File-safe display name suggestion: keeps alphanumerics, '.', '-', '_', maps rest to '_'. */
    public static String fileSafeName(String serverKey) {
        StringBuilder out = new StringBuilder(serverKey.length());
        for (int i = 0; i < serverKey.length(); i++) {
            char c = serverKey.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_';
            out.append(ok ? c : '_');
        }
        // ':' (host:port) は '_' になるので "PvpClub_net_25565" 想定 — 先頭のトレースとして十分安全。
        return out.length() == 0 ? "server" : out.toString();
    }

    private static String round4(double value) {
        double scaled = Math.round(value * 10_000.0) / 10_000.0;
        // 末尾ゼロの揺れを抑えて再読込側の diff を安定させる
        return String.valueOf(scaled);
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
