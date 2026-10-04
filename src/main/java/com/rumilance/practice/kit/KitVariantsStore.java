package com.rumilance.practice.kit;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-kit K1..K4 variant selection — the generalised LT-Vanilla style kit slots.
 *
 * <p>Every playable kit has four numbered variant layouts a player can edit (K1..K4, stored
 * under {@link #variantKey} in {@code kit_layouts}) and exactly one ACTIVE slot. The active
 * slot's layout is what a fight hands out: duels/queue through {@code MatchService}, FFA
 * through {@code FfaService#applyKit} (Active Only — FFA has no per-fight switching, unlike
 * the Crystal FFA {@code /k} flow which keeps its own separate KIT1..9 system).</p>
 *
 * <p>This class is deliberately Bukkit-free ({@link Properties} on a flat file) so the
 * localtest suite can cover it directly.</p>
 */
public final class KitVariantsStore {

    /** Variant slots per kit (LT Vanilla parity: K1..K4). */
    public static final int SLOTS = 4;

    private static final String FILE_NAME = "kit-variants.properties";

    private final Path file;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> selections =
            new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public KitVariantsStore(Path file) {
        this.file = file;
    }

    /**
     * Layout storage key for variant {@code slot} (1..{@link #SLOTS}) of {@code kitId}:
     * {@code <kitId>#k<n>}. The {@code #k} key space is distinct from the Crystal FFA
     * {@code #v} slots, so a kit that is BOTH a crystal FFA kit and a normal kit keeps the
     * two variant systems apart.
     */
    public static String variantKey(String kitId, int slot) {
        return kitId + "#k" + clamp(slot);
    }

    /** Clamps a 1-based slot number into {@code 1..SLOTS}. */
    public static int clamp(int slot) {
        return Math.min(SLOTS, Math.max(1, slot));
    }

    /**
     * The player's active variant slot for {@code kitId}: {@code 1..SLOTS}, default 1 (K1
     * is active for every kit until the player activates another one).
     */
    public int selected(UUID playerId, String kitId) {
        if (playerId == null || kitId == null) {
            return 1;
        }
        loadIfAbsent();
        ConcurrentHashMap<String, Integer> mine = selections.get(playerId);
        Integer slot = mine == null ? null : mine.get(kitId);
        return slot == null ? 1 : clamp(slot);
    }

    /** Activates variant {@code slot} (1..{@link #SLOTS}) of {@code kitId} for the player. */
    public void select(UUID playerId, String kitId, int slot) {
        if (playerId == null || kitId == null) {
            return;
        }
        loadIfAbsent();
        selections.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(kitId, clamp(slot));
        save();
    }

    private void loadIfAbsent() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            Properties props = new Properties();
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                } catch (Exception ignored) {
                    // A broken file must not take the whole variants feature down; treat
                    // it as empty (the default K1 selection still works).
                }
            }
            for (String key : props.stringPropertyNames()) {
                // Row format: "<uuid>.<kitId>=<1..4>". Kit ids contain no dots; unknown
                // rows are skipped, not fatal.
                int dot = key.indexOf('.');
                if (dot <= 0 || dot == key.length() - 1) {
                    continue;
                }
                try {
                    UUID playerId = UUID.fromString(key.substring(0, dot));
                    selections.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                            .put(key.substring(dot + 1), clamp(Integer.parseInt(props.getProperty(key))));
                } catch (Exception ignored) {
                    // Malformed row: skip.
                }
            }
            loaded = true;
        }
    }

    private void save() {
        Properties props = new Properties();
        for (java.util.Map.Entry<UUID, ConcurrentHashMap<String, Integer>> player
                : selections.entrySet()) {
            for (java.util.Map.Entry<String, Integer> kit : player.getValue().entrySet()) {
                props.setProperty(player.getKey() + "." + kit.getKey(),
                        String.valueOf(clamp(kit.getValue())));
            }
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "Per-kit K1..K4 active variant slots");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            // The in-memory selection still governs this session; retry on the next select.
        }
    }
}
