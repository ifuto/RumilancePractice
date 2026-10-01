package com.rumilance.practice.kb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Loads knockback reproduction profiles from {@code plugins/n-arena/kb/*.json}.
 *
 * <p>A profile file is written exactly like the JSON the KB Probe mod copies to the
 * clipboard (server name is taken from the FILE name with {@code .json} stripped, so
 * {@code PvPClub.json} shows up in the duel-request KB selector as "PvPClub"):</p>
 *
 * <pre>{@code
 * {
 *   "name": "PvPClub",
 *   "horizontal": 1.05,
 *   "vertical": 0.63,
 *   "source": { "mod": "kb-probe", "samplesH": 128 }   // metadata, ignored
 * }
 * }</pre>
 *
 * <p>{@code horizontal}/{@code vertical} are multipliers applied on top of Paper's final
 * knockback vector (the same scaling layer as the global knockback tuning) — the mod's
 * exported values are that server's factors vs vanilla, which is precisely the number
 * needed here. Malformed files are skipped with a warning and never kill the others.</p>
 *
 * <p>Pure-JDK on purpose (local-test runnable); profile state is re-read on reload so
 * Dropping in a new json takes effect with /practiceadmin reload.</p>
 */
public final class KbProfileService {

    /** Display sentinel for the "既定 (default KB)" selector entry — maps to {@code null}. */
    public static final String CHOICE_DEFAULT = "\0default";
    /** Explicit "KBの変更無し" choice — match runs plain knockback.json rules, no profile. */
    public static final String CHOICE_NONE = "\0none";

    private final Path directory;
    private final Logger logger;
    private volatile Map<String, double[]> profiles = Map.of();
    private volatile String defaultProfileName = "";

    public KbProfileService(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public void configureDefault(String defaultProfileName) {
        this.defaultProfileName = defaultProfileName == null ? "" : defaultProfileName;
    }

    public String defaultProfileName() {
        return defaultProfileName;
    }

    /** (Re)reads every {@code *.json} in the kb directory. */
    public void reload() {
        Map<String, double[]> loaded = new LinkedHashMap<>();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            logger.log(Level.WARNING, "kb-profiles: cannot create " + directory, e);
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> candidates = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : candidates) {
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - ".json".length());
                if (name.isBlank()) {
                    continue;
                }
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Map<String, Object> json = SimpleJson.parseObject(text);
                    double h = number(json, "horizontal");
                    double v = number(json, "vertical");
                    if (h < 0 || h > 4 || v < 0 || v > 4) {
                        logger.warning("kb-profiles: " + file.getFileName()
                                + " has factors outside the 0..4 band — skipped");
                        continue;
                    }
                    loaded.put(name, new double[]{h, v});
                } catch (Exception e) {
                    logger.warning("kb-profiles: " + file.getFileName()
                            + " is not a valid profile (" + e.getMessage() + ") — skipped");
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "kb-profiles: cannot list " + directory, e);
        }
        profiles = Map.copyOf(loaded);
    }

    private static double number(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof Number n)) {
            throw new SimpleJson.JsonException("missing or non-numeric '" + key + "'");
        }
        return n.doubleValue();
    }

    /** Profile name list, sorted (display order for the duel-request selector). */
    public java.util.List<String> names() {
        return List.copyOf(profiles.keySet());
    }

    public boolean exists(String name) {
        return name != null && profiles.containsKey(name);
    }

    public Optional<double[]> find(String name) {
        double[] f = name == null ? null : profiles.get(name);
        return f == null ? Optional.empty() : Optional.of(f);
    }

    /**
     * Resolves a selection into a concrete profile: {@code null} (selector untouched /
     * "既定") resolves the operator's default profile, {@link #CHOICE_NONE} gives
     * explicit "変更無し" (empty Optional = no profile layer at all).
     */
    public Resolved resolveChoice(String choice) {
        if (CHOICE_NONE.equals(choice)) {
            return Resolved.noProfile();
        }
        String effective = CHOICE_DEFAULT.equals(choice) || choice == null || choice.isBlank()
                ? defaultProfileName
                : choice;
        if (effective == null || effective.isBlank()) {
            return Resolved.noProfile();
        }
        double[] factor = profiles.get(effective);
        if (factor == null) {
            return Resolved.noProfile();
        }
        return new Resolved(effective, factor[0], factor[1]);
    }

    /** The factor a STARTED match session uses (profile name already on the session). */
    public record Resolved(String name, double horizontal, double vertical) {
        private static final Resolved NO_PROFILE = new Resolved(null, 1.0, 1.0);

        static Resolved noProfile() {
            return NO_PROFILE;
        }

        public boolean hasProfile() {
            return name != null;
        }
    }
}
