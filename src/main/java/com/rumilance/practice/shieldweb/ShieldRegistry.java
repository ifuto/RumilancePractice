package com.rumilance.practice.shieldweb;

import com.rumilance.practice.resourcepack.ResourcePackJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative list of shield artworks managed by the Shield Web UI.
 *
 * <p>Persisted as JSONL (one flat JSON object per line) to {@code shields.json} next to the
 * pack working copy: {@code {"cmd": 100, "name": "水色の盾", "created": 1766…}}.
 * Player assignments are NOT stored here — they live in {@code HiddenRankService}, because a
 * holder keeps their hidden rank even while their cmd points at nothing. The registry only
 * describes which cmd → artwork exists inside the pack.</p>
 *
 * <p>JSONL means a truncated/corrupt tail line (killed mid-write) only loses the last entry
 * instead of the whole file, and the {@code ResourcePackJson} flat reader is reused instead of
 * growing a second hand-rolled parser.</p>
 */
public final class ShieldRegistry {

    public record ShieldEntry(int cmd, String name, long createdEpochMillis) {
    }

    private final Path file;
    private final Map<Integer, ShieldEntry> entries = new LinkedHashMap<>();

    public ShieldRegistry(Path file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        entries.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
                continue;
            }
            Map<String, String> values = ResourcePackJson.parse(trimmed);
            try {
                int cmd = Integer.parseInt(values.getOrDefault("cmd", "0").trim());
                if (cmd <= 0) {
                    continue;
                }
                String name = values.getOrDefault("name", "");
                long created;
                try {
                    created = Long.parseLong(values.getOrDefault("created", "0").trim());
                } catch (NumberFormatException e) {
                    created = 0L;
                }
                entries.put(cmd, new ShieldEntry(cmd, name, created));
            } catch (NumberFormatException ignored) {
                // Skip the bad line only — never lose the whole registry.
            }
        }
    }

    public synchronized void save() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (ShieldEntry entry : entries.values()) {
            sb.append("{\"cmd\": ").append(entry.cmd())
                    .append(", \"name\": ").append(ResourcePackJson.quote(entry.name()))
                    .append(", \"created\": ").append(entry.createdEpochMillis())
                    .append("}\n");
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        // Write-then-rename so a crash mid-save never leaves a half-written registry.
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized List<ShieldEntry> list() {
        return new ArrayList<>(entries.values());
    }

    public synchronized List<Integer> cmdList() {
        return new ArrayList<>(entries.keySet());
    }

    public synchronized Optional<ShieldEntry> get(int cmd) {
        return Optional.ofNullable(entries.get(cmd));
    }

    /** Allocates the next free cmd (100+): low numbers stay reserved for hand-made assets. */
    public synchronized int nextCmd() {
        int next = 100;
        for (Integer cmd : entries.keySet()) {
            next = Math.max(next, cmd + 1);
        }
        return next;
    }

    public synchronized ShieldEntry upsert(int cmd, String name) {
        ShieldEntry existing = entries.get(cmd);
        long created = existing != null ? existing.createdEpochMillis() : System.currentTimeMillis();
        String finalName = name == null || name.isBlank()
                ? (existing != null ? existing.name() : "shield-" + cmd)
                : name.trim();
        ShieldEntry entry = new ShieldEntry(cmd, finalName, created);
        entries.put(cmd, entry);
        return entry;
    }

    public synchronized boolean remove(int cmd) {
        return entries.remove(cmd) != null;
    }
}
