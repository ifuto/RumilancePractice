package com.rumilance.practice.combat;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Loads operator knockback presets from {@code plugins/RumilancePractice/kb/*.json}
 * and keeps the active profile for melee velocity rewrites.
 */
public final class KnockbackService {

    public static final String DEFAULT_FILE = "Straykb.json";
    public static final String OFF_NAME = "off";
    public static final String VANILLA_FILE = "Vanillakb.json";
    public static final String CLUB_FILE = "Clubkb.json";
    public static final String CLUB_ARCHIVE_FILE = "Clubkb-archive.json";
    public static final String STRAY_FILE = "Straykb.json";
    public static final String STRAY_ARCHIVE_FILE = "Straykb-archive.json";
    public static final String LUNAR_FILE = "Lunarkb.json";
    public static final String VELT_FILE = "Veltkb.json";

    private static final List<String> BUNDLED = List.of(
            VANILLA_FILE,
            CLUB_FILE,
            CLUB_ARCHIVE_FILE,
            STRAY_FILE,
            STRAY_ARCHIVE_FILE,
            LUNAR_FILE,
            VELT_FILE);

    private final Plugin plugin;
    private final File folder;
    private final File activeFile;
    private volatile KnockbackProfile profile = KnockbackProfile.STRAY;
    private volatile String activeName = DEFAULT_FILE;
    private volatile boolean rewriteEnabled = true;
    private volatile boolean syncEnabled = true;

    public KnockbackService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folder = new File(plugin.getDataFolder(), "kb");
        this.activeFile = new File(folder, "active.txt");
    }

    public void load() {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create kb/ folder");
        }
        migrateLegacyStrayDefault();
        migrateOcmFormulaPresets();
        for (String name : BUNDLED) {
            copyResource(name, false);
        }
        String wanted = readActiveName();
        if (isOffName(wanted)) {
            disable();
            return;
        }
        if ("sync".equalsIgnoreCase(wanted) || "knockbacksync".equalsIgnoreCase(wanted)) {
            syncOnly();
            return;
        }
        if (!apply(wanted)) {
            apply(DEFAULT_FILE);
        }
    }

    public boolean apply(String fileName) {
        if (isOffName(fileName)) {
            disable();
            return true;
        }
        if (isSyncOnlyName(fileName)) {
            syncOnly();
            return true;
        }
        File file = resolve(fileName);
        if (file == null || !file.isFile()) {
            return false;
        }
        try {
            profile = KnockbackProfile.load(file);
            activeName = file.getName();
            rewriteEnabled = true;
            syncEnabled = true;
            writeActiveName(activeName);
            plugin.getLogger().info("Knockback profile: " + activeName + " (rewrite+sync)");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load knockback profile " + file.getName(), e);
            return false;
        }
    }

    /**
     * Leave Paper's melee knockback untouched and disable KnockbackSync-style Y sync.
     */
    public void disable() {
        rewriteEnabled = false;
        syncEnabled = false;
        activeName = OFF_NAME;
        writeActiveName(OFF_NAME);
        plugin.getLogger().info("Knockback profile: off (Paper vanilla, no ping sync)");
    }

    /**
     * Paper melee numbers + KnockbackSync-style Y compensation only (no preset rewrite).
     */
    public void syncOnly() {
        rewriteEnabled = false;
        syncEnabled = true;
        activeName = "sync";
        writeActiveName("sync");
        plugin.getLogger().info("Knockback profile: sync-only (Paper + KnockbackSync Y)");
    }

    public boolean rewriteEnabled() {
        return rewriteEnabled;
    }

    public boolean syncEnabled() {
        return syncEnabled;
    }

    public KnockbackProfile profile() {
        return profile;
    }

    public String activeName() {
        return activeName;
    }

    public File folder() {
        return folder;
    }

    public List<String> listProfiles() {
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return (lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml"))
                    && !name.equalsIgnoreCase("active.txt");
        });
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public File resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.trim();
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        File direct = new File(folder, name);
        if (direct.isFile()) {
            return direct;
        }
        if (!name.contains(".")) {
            File json = new File(folder, name + ".json");
            if (json.isFile()) {
                return json;
            }
        }
        return direct;
    }

    public static boolean isOffName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        return name.equals(OFF_NAME)
                || name.equals("paper")
                || name.equals("none")
                || name.equals("passthrough");
    }

    public static boolean isSyncOnlyName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        return name.equals("sync") || name.equals("knockbacksync") || name.equals("kbsync");
    }

    /**
     * 1.2.3 shipped vanilla coefficients as {@code Straykb.json}. Keep that file as
     * {@code Vanillakb.json} and replace Straykb with the volunteer stray fit.
     */
    private void migrateLegacyStrayDefault() {
        File stray = new File(folder, STRAY_FILE);
        if (!stray.isFile()) {
            return;
        }
        try {
            KnockbackProfile existing = KnockbackProfile.load(stray);
            if (!existing.sameCoefficients(KnockbackProfile.VANILLA)) {
                return;
            }
            File vanilla = new File(folder, VANILLA_FILE);
            if (!vanilla.isFile()) {
                Files.copy(stray.toPath(), vanilla.toPath());
            }
            copyResource(STRAY_FILE, true);
            plugin.getLogger().info("Moved legacy Straykb.json (vanilla coefficients) to " + VANILLA_FILE);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not migrate legacy Straykb.json", e);
        }
    }

    /**
     * Older presets omitted air-vertical / vertical-limit and baked sprint into relative push.
     * Refresh bundled Club/Vanilla/Stray once so operators get the OCM + KBM layout.
     */
    private void migrateOcmFormulaPresets() {
        File marker = new File(folder, ".ocm-formula-v2");
        if (marker.isFile()) {
            return;
        }
        for (String name : List.of(CLUB_FILE, CLUB_ARCHIVE_FILE, VANILLA_FILE, STRAY_FILE)) {
            copyResource(name, true);
        }
        try {
            Files.writeString(marker.toPath(), "1\n", StandardCharsets.UTF_8);
            plugin.getLogger().info("Refreshed kb presets to OCM/KBM formula (Club air-V + look-dir sprint).");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write kb migration marker", e);
        }
    }

    private void copyResource(String fileName, boolean overwrite) {
        File dest = new File(folder, fileName);
        if (dest.isFile() && !overwrite) {
            return;
        }
        try (InputStream in = plugin.getResource("kb/" + fileName)) {
            if (in == null) {
                if (!dest.isFile()) {
                    Files.writeString(dest.toPath(), fallbackJson(fileName), StandardCharsets.UTF_8);
                }
                return;
            }
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + fileName, e);
        }
    }

    private String readActiveName() {
        if (!activeFile.isFile()) {
            return DEFAULT_FILE;
        }
        try {
            String name = Files.readString(activeFile.toPath(), StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? DEFAULT_FILE : name;
        } catch (IOException e) {
            return DEFAULT_FILE;
        }
    }

    private void writeActiveName(String name) {
        try {
            Files.writeString(activeFile.toPath(), name + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best-effort persist
        }
    }

    private static String fallbackJson(String fileName) {
        if (CLUB_ARCHIVE_FILE.equalsIgnoreCase(fileName)) {
            return clubJson(0.3608d);
        }
        if (STRAY_ARCHIVE_FILE.equalsIgnoreCase(fileName)) {
            return coefficientsJson(0.35d, 0.35d, 0.0d, 0.425d, 0.1d, 0.4d, 0.5d, 0.5d, 3.5d);
        }
        if (LUNAR_FILE.equalsIgnoreCase(fileName)) {
            return coefficientsJson(0.54d, 0.361735d, 0.0d, 0.38d, 0.1d, 0.675d, 0.0d, 0.6849d, 4.0d);
        }
        if (VELT_FILE.equalsIgnoreCase(fileName)) {
            return coefficientsJson(0.325d, 0.36d, 0.0d, 0.5d, 0.1d, 0.675d, 0.0d, 0.1d, 4.0d);
        }
        if (CLUB_FILE.equalsIgnoreCase(fileName)) {
            return clubJson(0.36075d);
        }
        if (VANILLA_FILE.equalsIgnoreCase(fileName)) {
            return coefficientsJson(0.4d, 0.4d, 0.0d, 0.5d, 0.1d, 0.4d, 0.5d, 0.5d, 3.5d);
        }
        return coefficientsJson(0.38d, 0.4d, 0.0d, 0.425d, 0.1d, 0.4d, 0.5d, 0.5d, 3.5d);
    }

    private static String clubJson(double vertical) {
        return coefficientsJson(0.4d, vertical, 0.24775d, 0.5d, 0.1d, 0.675d, 0.0d, 0.5d, 4.0d);
    }

    private static String coefficientsJson(
            double horizontal, double vertical, double airVertical, double sprint,
            double sprintVertical, double verticalLimit,
            double attackerInfluence, double targetVelocity, double clamp
    ) {
        return """
                {
                  "attack-knockback": 1.0,
                  "horizontal-kb": %s,
                  "vertical-kb": %s,
                  "air-vertical-kb": %s,
                  "sprint-kb": %s,
                  "sprint-vertical-kb": %s,
                  "vertical-limit": %s,
                  "knockback-resistance": 0.0,
                  "attacker-velocity-influence": %s,
                  "target-velocity": %s,
                  "knockback-direction": "relative",
                  "velocity-clamp": %s
                }
                """.formatted(horizontal, vertical, airVertical, sprint, sprintVertical,
                verticalLimit, attackerInfluence, targetVelocity, clamp);
    }
}
