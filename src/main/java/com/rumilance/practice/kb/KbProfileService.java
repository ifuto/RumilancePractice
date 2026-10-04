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
 * <p>Two file formats coexist (the extension-only keys of either are ignored):</p>
 *
 * <p><b>Legacy multiplier file</b> (kb-probe ≤ 0.5.x clipboard): scales the FINAL knockback
 * vector vanilla produced.</p>
 *
 * <pre>{@code
 * {
 *   "name": "PvPClub",
 *   "horizontal": 1.05,
 *   "vertical": 0.63,
 *   "source": { "mod": "kb-probe 0.5.0", "samplesH": 128 }   // metadata, ignored
 * }
 * }</pre>
 *
 * <p><b>Staged file</b> (kb-probe 0.7.0 "staged-knockback" export): the full fitted physics
 * model, detected by any of the extra keys ({@code verticalLimit}, {@code extraHorizontal},
 * {@code extraVertical}, {@code frictionHorizontal}, {@code frictionVertical},
 * {@code airHorizontalMultiplier}, {@code airVerticalMultiplier}, {@code knockbackEnchant},
 * {@code extraReappliesFriction}). Values missing from the file fall back to the vanilla
 * constants — see {@link StagedKnockback}. The export's {@code attackerSlowdown},
 * {@code hitDelay} and {@code gravity} are measurement-side corrections and ignored.</p>
 *
 * <p>The profile name comes from the FILE name with {@code .json} stripped, so
 * {@code VeltHC.json} shows up in the duel-request KB selector as "VeltHC". Malformed files
 * are skipped with a warning and never kill the others.</p>
 *
 * <p>Pure-JDK on purpose (local-test runnable); profile state is re-read on reload so
 * dropping in a new json takes effect with /practiceadmin reload.</p>
 */
public final class KbProfileService {

    /** Display sentinel for the "既定 (default KB)" selector entry — maps to {@code null}. */
    public static final String CHOICE_DEFAULT = "\0default";
    /** Explicit "KBの変更無し" choice — match runs plain knockback.json rules, no profile. */
    public static final String CHOICE_NONE = "\0none";

    /** Keys whose presence marks a file as a 0.7.0 staged profile. */
    private static final String[] STAGED_KEYS = {
            "verticalLimit", "extraHorizontal", "extraVertical",
            "frictionHorizontal", "frictionVertical",
            "airHorizontalMultiplier", "airVerticalMultiplier",
            "knockbackEnchant", "extraReappliesFriction"};

    private final Path directory;
    private final Logger logger;
    private volatile Map<String, Object> profiles = Map.of();
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
        Map<String, Object> loaded = new LinkedHashMap<>();
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
                    if (isStagedFile(json)) {
                        loaded.put(name, readStaged(json));
                    } else {
                        loaded.put(name, new double[]{h, v});
                    }
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

    private static boolean isStagedFile(Map<String, Object> json) {
        for (String key : STAGED_KEYS) {
            if (json.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** Reads a 0.7.0 staged export; missing fields fall back to the vanilla constants. */
    private static StagedKnockback readStaged(Map<String, Object> json) {
        return new StagedKnockback(
                numberOr(json, "horizontal", StagedKnockback.VANILLA.horizontal()),
                numberOr(json, "vertical", StagedKnockback.VANILLA.vertical()),
                numberOr(json, "verticalLimit", StagedKnockback.VANILLA.verticalLimit()),
                numberOr(json, "extraHorizontal", StagedKnockback.VANILLA.extraHorizontal()),
                numberOr(json, "extraVertical", StagedKnockback.VANILLA.extraVertical()),
                numberOr(json, "frictionHorizontal", StagedKnockback.VANILLA.frictionHorizontal()),
                numberOr(json, "frictionVertical", StagedKnockback.VANILLA.frictionVertical()),
                numberOr(json, "airHorizontalMultiplier", StagedKnockback.VANILLA.airHorizontalMultiplier()),
                numberOr(json, "airVerticalMultiplier", StagedKnockback.VANILLA.airVerticalMultiplier()),
                numberOr(json, "knockbackEnchant", StagedKnockback.VANILLA.knockbackEnchant()),
                boolOr(json, "extraReappliesFriction", StagedKnockback.VANILLA.extraReappliesFriction()));
    }

    private static double numberOr(Map<String, Object> json, String key, double fallback) {
        Object value = json.get(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean boolOr(Map<String, Object> json, String key, boolean fallback) {
        Object value = json.get(key);
        return value instanceof Boolean b ? b : fallback;
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

    /** The raw profile: {@link double[]} for legacy multiplier files, {@link StagedKnockback} for staged ones. */
    public Optional<Object> find(String name) {
        Object f = name == null ? null : profiles.get(name);
        return Optional.ofNullable(f);
    }

    /** The legacy final-velocity multipliers — only for files without the staged keys. */
    public Optional<double[]> findFactor(String name) {
        return find(name).filter(double[].class::isInstance).map(double[].class::cast);
    }

    /** The staged model — only for kb-probe 0.7.0 "staged-knockback" files. */
    public Optional<StagedKnockback> findStaged(String name) {
        return find(name).filter(StagedKnockback.class::isInstance).map(StagedKnockback.class::cast);
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
        Object profile = profiles.get(effective);
        if (profile == null) {
            return Resolved.noProfile();
        }
        if (profile instanceof StagedKnockback staged) {
            return new Resolved(effective, staged.horizontal(), staged.vertical(), staged);
        }
        double[] factor = (double[]) profile;
        return new Resolved(effective, factor[0], factor[1], null);
    }

    /**
     * The factor a STARTED match session uses (profile name already on the session).
     * {@code staged} is non-null exactly when the profile file was a 0.7.0 staged export —
     * in that case the melee knockback is REBUILT by {@link StagedKnockback} and the
     * {@code horizontal}/{@code vertical} components are informational only.
     */
    public record Resolved(String name, double horizontal, double vertical, StagedKnockback staged) {
        private static final Resolved NO_PROFILE = new Resolved(null, 1.0, 1.0, null);

        static Resolved noProfile() {
            return NO_PROFILE;
        }

        public boolean hasProfile() {
            return name != null;
        }
    }
}
