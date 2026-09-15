package com.rumilance.practice.quantum;

import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A Quantum-style datapack as raw text: {@code data/<ns>/function/**.mcfunction} plus
 * {@code data/<ns>/tags/function/**.json} — read from a directory or straight out of the
 * original map zip, without touching the vanilla pack loader.
 *
 * <p>Why we do not simply drop the pack into {@code world/datapacks}: the vanilla loader parses
 * every function at server startup, i.e. <b>before</b> plugins can register the
 * {@code player …} verb, so every function that talks to the bot fails to load ("Unknown or
 * incomplete command") and the pack ends up half-registered. Reading the files ourselves and
 * compiling each line against the <i>live</i> dispatcher (with {@code player}/{@code herobot}
 * present) reproduces the reference server's state exactly. See
 * {@code tools/paper-bridge-spike/README.md} for the measurements behind that decision.</p>
 *
 * <p>Line handling mirrors {@code ServerFunctionLibrary#readLines} (all lines, verbatim) and
 * {@code CommandFunction#fromLines} (comments, blank lines, {@code \} continuations, {@code $}
 * macros) — we let that same vanilla method do the parsing, so semantics cannot drift.</p>
 */
public final class QuantumPack {

    /** Functions by id, lines exactly as on disk. */
    private final Map<Identifier, List<String>> functions = new LinkedHashMap<>();
    /** Function tags by id; members are function ids, with a leading '#' for nested tags. */
    private final Map<Identifier, List<String>> tagMembers = new LinkedHashMap<>();
    private final List<String> sources = new ArrayList<>();

    public Map<Identifier, List<String>> functions() {
        return this.functions;
    }

    public Map<Identifier, List<String>> tagMembers() {
        return this.tagMembers;
    }

    public List<String> sources() {
        return this.sources;
    }

    // ------------------------------------------------------------------ loading

    /** Reads every pack found under the given roots (directories and/or .zip files). */
    public static QuantumPack load(List<Path> roots) {
        QuantumPack pack = new QuantumPack();
        for (Path root : roots) {
            if (root == null || !Files.exists(root)) {
                continue;
            }
            if (Files.isDirectory(root)) {
                pack.readDirectory(root, "");
            } else if (root.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                pack.readZip(root);
            }
        }
        return pack;
    }

    /**
     * Reads every pack below {@code root}: the root itself may be a pack ({@code root/data/…}) or a
     * container of packs — which is the normal case on a server, where the map's own packs sit as
     * {@code world/datapacks/<PackName>/data/…}. Both shapes are accepted, so a whole world's
     * {@code datapacks} folder can be handed over as one root.
     */
    private void readDirectory(Path root, String prefix) {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                String packRelative = fromDataRoot(relative);
                if (packRelative == null) {
                    continue;
                }
                try {
                    this.accept(packRelative, Files.readString(path, StandardCharsets.UTF_8));
                    this.markSource(root.toString());
                } catch (IOException e) {
                    // unreadable file: skip it, the caller reports the counts
                }
            }
        } catch (IOException e) {
            // unreadable root: nothing to load from it
        }
    }

    /** {@code Practicebot/data/quantum/…} and {@code data/quantum/…} both become {@code data/quantum/…}. */
    private static String fromDataRoot(String relative) {
        if (relative.startsWith("data/")) {
            return relative;
        }
        int index = relative.indexOf("/data/");
        return index < 0 ? null : relative.substring(index + 1);
    }

    private void readZip(Path zip) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            List<? extends ZipEntry> entries = file.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> !entry.getName().startsWith("__MACOSX/"))
                    .filter(entry -> !entry.getName().endsWith("/.DS_Store"))
                    .toList();
            for (ZipEntry entry : entries) {
                String name = entry.getName();
                int dataIndex = name.indexOf("data/");
                if (dataIndex < 0 || !name.startsWith("data/", dataIndex)) {
                    continue;
                }
                String relative = name.substring(dataIndex);
                try (InputStream stream = file.getInputStream(entry)) {
                    this.accept(relative, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    this.markSource(zip.toString());
                } catch (IOException e) {
                    // skip individual unreadable entries
                }
            }
        } catch (IOException e) {
            this.markSource(zip + " (unreadable: " + e.getMessage() + ")");
        }
    }

    private void markSource(String source) {
        if (!this.sources.contains(source)) {
            this.sources.add(source);
        }
    }

    /** Accepts one relative {@code data/…} path; unknown paths are ignored silently. */
    private void accept(String relative, String content) {
        String[] parts = relative.split("/");
        if (parts.length < 4 || !parts[0].equals("data")) {
            return;
        }
        String namespace = parts[1];
        boolean functionDir = parts[2].equals("function") || parts[2].equals("functions");
        if (functionDir) {
            if (!relative.endsWith(".mcfunction")) {
                return;
            }
            String path = relative.substring(("data/" + namespace + "/" + parts[2] + "/").length());
            if (path.isEmpty()) {
                return;
            }
            this.functions.put(Identifier.fromNamespaceAndPath(namespace, path), splitLines(content));
            return;
        }
        if (!parts[2].equals("tags") || parts.length < 5
                || !(parts[3].equals("function") || parts[3].equals("functions"))) {
            return;
        }
        String path = relative.substring(("data/" + namespace + "/tags/" + parts[3] + "/").length());
        if (!path.endsWith(".json")) {
            return;
        }
        // Several packs of one map define the same tag (the Quantum map and our harness both add to
        // #minecraft:tick, for instance): vanilla unions them, so we must too — and de-duplicate,
        // because a root can be handed over twice (the world folder and its `datapacks` subfolder).
        Identifier id = Identifier.fromNamespaceAndPath(namespace,
                path.substring(0, path.length() - ".json".length()));
        List<String> members = new ArrayList<>(this.tagMembers.getOrDefault(id, List.of()));
        for (String member : readTagValues(content)) {
            if (!members.contains(member)) {
                members.add(member);
            }
        }
        this.tagMembers.put(id, members);
    }

    /** Vanilla reads every line; {@code CommandFunction.fromLines} filters comments and blanks. */
    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\r?\n", -1)) {
            lines.add(line);
        }
        // split() keeps a trailing empty element for a file ending in \n — vanilla's
        // BufferedReader#lines would not produce it, and an empty line is a no-op anyway.
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * Minimal function-tag reader: {@code {"values":["ns:fn","#ns:other",{"id":"ns:fn","required":false}]}}.
     * Nested tags keep their {@code #} prefix; unknown keys are ignored.
     */
    static List<String> readTagValues(String json) {
        List<String> values = new ArrayList<>();
        int index = json.indexOf("\"values\"");
        if (index < 0) {
            return values;
        }
        int cursor = json.indexOf('[', index);
        int end = json.lastIndexOf(']');
        if (cursor < 0 || end < cursor) {
            return values;
        }
        String body = json.substring(cursor + 1, end);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").matcher(body);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group(1));
        }
        // Keys such as "id"/"required" appear in object form; drop known key names.
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("id") || token.equals("required") || token.equals("values")) {
                continue;
            }
            if (token.startsWith("#") || token.contains(":")) {
                values.add(token);
            }
        }
        return values;
    }

    /** Function ids grouped by namespace — used by {@code /quantum list}. */
    public Map<String, Integer> countsByNamespace() {
        Map<String, Integer> counts = new HashMap<>();
        for (Identifier id : this.functions.keySet()) {
            counts.merge(id.getNamespace(), 1, Integer::sum);
        }
        return counts;
    }
}
