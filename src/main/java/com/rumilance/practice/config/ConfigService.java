package com.rumilance.practice.config;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads, caches and reloads YAML resource files for RumilancePractice.
 */
public final class ConfigService {

    public static final String CONFIG = "config.yml";
    public static final String DATABASE = "database.yml";
    public static final String GUI = "gui.yml";
    public static final String SOUNDS = "sounds.yml";
    public static final String PROFILE = "profile.yml";
    public static final String KITS = "kits.yml";
    public static final String ARENAS = "arenas.yml";
    public static final String PRACTICES = "practices.yml";
    public static final String LOBBY = "lobby.yml";
    public static final String FFA = "ffa.yml";
    public static final String PLANS = "plans.yml";
    public static final String ARROW_EFFECTS = "arrow-effects.yml";
    public static final String KILL_EFFECTS = "kill-effects.yml";
    public static final String EKIT_ITEMS = "ekit-items.yml";
    public static final String PRESET_ITEMS = "preset-items.yml";
    public static final String SCOREBOARD = "scoreboard.yml";

    private static final List<String> RESOURCE_FILES = List.of(
            CONFIG, DATABASE, GUI, SOUNDS, PROFILE, KITS, ARENAS, PRACTICES, LOBBY, FFA, PLANS,
            ARROW_EFFECTS, KILL_EFFECTS, EKIT_ITEMS, PRESET_ITEMS, SCOREBOARD
    );

    /** A top-level {@code key:} line (column 0) of a config file. */
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_.-]+):(?:[ \\t].*)?$");

    /** The legacy icons.font default that fails to resolve on some 1.21.x clients. */
    private static final Pattern LEGACY_ICON_FONT =
            Pattern.compile("^(\\s*font:\\s*)\"?rumilance:icons\"?\\s*$", Pattern.MULTILINE);

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new LinkedHashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public void loadAll() {
        File dataFolder = PluginIdentity.dataFolder(plugin);
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder: " + dataFolder);
        }
        for (String fileName : RESOURCE_FILES) {
            configs.put(fileName, loadWithDefaults(fileName));
        }
    }

    private FileConfiguration loadWithDefaults(String fileName) {
        File target = new File(PluginIdentity.dataFolder(plugin), fileName);
        if (!target.exists()) {
            extractResource(fileName, target);
        }
        String jarText = readResourceText(fileName);
        YamlConfiguration jarDefaults = parseYamlText(jarText, "bundled " + fileName);

        YamlConfiguration onDisk = loadFileQuiet(target);
        if (onDisk == null && target.exists()) {
            // The on-disk file is not valid YAML (e.g. a stray indentation). First try to
            // auto-repair simple indentation slips so the owner's settings survive; if that
            // is impossible, keep the broken file and fall back to fresh defaults.
            String brokenText = readFile(target);
            String repaired = brokenText == null ? null : tryRepairYaml(brokenText);
            if (repaired != null) {
                File backup = backupFile(target, false);
                writeFile(target, repaired);
                plugin.getLogger().warning(fileName + " is not valid YAML — an indentation error was"
                        + " repaired automatically. The untouched original was saved as '"
                        + (backup == null ? "(backup failed)" : backup.getName()) + "'.");
                onDisk = parseYamlText(repaired, fileName);
            } else {
                File backup = backupFile(target, true);
                extractResource(fileName, target);
                plugin.getLogger().severe(fileName + " is not valid YAML — it was moved to '"
                        + (backup == null ? "(backup failed)" : backup.getName())
                        + "' and a fresh default file was written. Copy your settings over from the backup.");
                onDisk = jarDefaults;
            }
        } else if (onDisk == null) {
            onDisk = new YamlConfiguration();
        }

        if (CONFIG.equals(fileName) && jarText != null) {
            String synced = syncConfigFile(target, jarText, onDisk);
            if (synced != null) {
                YamlConfiguration reparsed = parseYamlText(synced, fileName);
                if (reparsed != null) {
                    onDisk = reparsed;
                }
            }
        }

        YamlConfiguration merged = deepMerge(jarDefaults, onDisk);
        merged.setDefaults(jarDefaults);
        return merged;
    }

    /** Loads a YAML file, or {@code null} when it cannot be parsed. */
    private YamlConfiguration loadFileQuiet(File target) {
        String text = readFile(target);
        if (text == null) {
            return null;
        }
        if (text.isBlank()) {
            return new YamlConfiguration();
        }
        try {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(text);
            return configuration;
        } catch (InvalidConfigurationException e) {
            return null;
        }
    }

    /**
     * Attempts to repair YAML whose only problem is an accidentally indented key line
     * (e.g. {@code " combat:"} instead of {@code "combat:"}). SnakeYAML names the offending
     * line, so we dedent it and retry — bounded to a handful of attempts.
     *
     * @return the repaired text, or {@code null} when the file cannot be repaired this way.
     */
    private String tryRepairYaml(String text) {
        String current = normalize(text);
        Pattern lineReference = Pattern.compile("line (\\d+)");
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                new YamlConfiguration().loadFromString(current);
                return current;
            } catch (InvalidConfigurationException e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                Matcher matcher = lineReference.matcher(message);
                int line = -1;
                while (matcher.find()) {
                    line = Integer.parseInt(matcher.group(1)); // the last reference is the problem line
                }
                if (line <= 0) {
                    return null;
                }
                String[] lines = current.split("\n", -1);
                int index = line - 1;
                if (index >= lines.length) {
                    return null;
                }
                String dedented = lines[index].stripLeading();
                if (dedented.equals(lines[index]) || dedented.isBlank()) {
                    return null; // nothing left to fix on that line
                }
                lines[index] = dedented;
                current = String.join("\n", lines);
            }
        }
        return null;
    }

    /** Copies (or moves) the file to {@code <name>.broken-<timestamp>} and returns the backup. */
    private File backupFile(File target, boolean move) {
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backup = new File(target.getParentFile(), target.getName() + ".broken-" + stamp);
        try {
            if (move) {
                Files.move(target.toPath(), backup.toPath());
            } else {
                Files.copy(target.toPath(), backup.toPath());
            }
            return backup;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not back up " + target.getName(), e);
            return null;
        }
    }

    private void writeFile(File target, String text) {
        try {
            Files.writeString(target.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write " + target.getName(), e);
        }
    }

    private String readResourceText(String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read bundled " + fileName, e);
            return null;
        }
    }

    private YamlConfiguration parseYamlText(String text, String label) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (text == null || text.isBlank()) {
            return configuration;
        }
        try {
            configuration.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse " + label, e);
        }
        return configuration;
    }

    /**
     * Keeps config.yml current WITHOUT touching user values:
     * <ul>
     *   <li>top-level sections that exist in the bundled defaults but not in the file are
     *       appended verbatim (comments included) — "adding new keys is fine",</li>
     *   <li>the {@code resource-pack} section is replaced with the bundled version — its
     *       url/sha1 must always match the pack the plugin is currently shipping, and the
     *       server owner explicitly allowed this section to be overwritten on startup,</li>
     *   <li>a one-time migration turns the legacy default {@code icons.font: "rumilance:icons"}
     *       into {@code "default"} (custom font ids fail to resolve on some 1.21.x clients);
     *       any other configured font stays untouched.</li>
     * </ul>
     *
     * @return the (possibly updated) file contents already written to disk, or {@code null}
     *         when nothing changed.
     */
    private String syncConfigFile(File target, String jarText, YamlConfiguration onDisk) {
        Map<String, String> jarBlocks = splitTopLevelBlocks(jarText);
        String diskText = readFile(target);
        if (diskText == null) {
            return null;
        }
        String normalized = normalize(diskText);

        // 1) The resource-pack block must always match the pack the plugin is shipping
        //    (a stale sha1/url would make clients reject the pack). Overwriting this one
        //    section on startup is explicitly allowed by the server owner.
        String packSynced = replaceTopLevelBlock(normalized, "resource-pack", jarBlocks.get("resource-pack"));
        boolean packChanged = !packSynced.equals(normalized);
        String updated = packSynced;

        // 2) Append every bundled top-level section the file does not have yet. Everything
        //    already present is left byte-for-byte untouched, so user values survive.
        Set<String> present = new HashSet<>();
        scanTopLevelBlocks(updated).forEach(block -> present.add(block.key()));
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String> jarBlock : jarBlocks.entrySet()) {
            String key = jarBlock.getKey();
            if (present.contains(key) || onDisk.contains(key)) {
                continue;
            }
            if (!updated.endsWith("\n")) {
                updated = updated + "\n";
            }
            updated = updated + "\n" + jarBlock.getValue().stripTrailing() + "\n";
            present.add(key);
            added.add(key);
        }

        // 3) One-time migration of the legacy icons.font default value. Any font other than
        //    the exact old default is considered a deliberate choice and stays untouched.
        Matcher legacyFont = LEGACY_ICON_FONT.matcher(updated);
        boolean migrated = legacyFont.find();
        if (migrated) {
            updated = legacyFont.replaceAll("$1\"default\"");
        }

        if (updated.equals(normalized)) {
            return null;
        }
        writeFile(target, updated);
        if (packChanged) {
            plugin.getLogger().info("config.yml: resource-pack section synced with the bundled pack"
                    + " (url/sha1 must match the distributed zip)");
        }
        if (!added.isEmpty()) {
            plugin.getLogger().info("config.yml: added missing sections: " + String.join(", ", added));
        }
        if (migrated) {
            plugin.getLogger().info("config.yml: icons.font migrated from the legacy default"
                    + " \"rumilance:icons\" to \"default\" (glyphs are merged into the default font)");
        }
        return updated;
    }

    private String readFile(File target) {
        try {
            return Files.readString(target.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read " + target.getName(), e);
            return null;
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** One top-level block: key plus the line range covering the block's leading comment
     *  header, the key line and every line up to (excluding) the next block's header. */
    private record TopLevelBlock(String key, int startLine, int endLine) {
    }

    /** Scans config text for top-level blocks, in file order. */
    private List<TopLevelBlock> scanTopLevelBlocks(String text) {
        String[] lines = normalize(text).split("\n", -1);
        List<TopLevelBlock> blocks = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = TOP_LEVEL_KEY.matcher(lines[i]);
            if (!matcher.matches()) {
                continue;
            }
            int start = i;
            while (start > 0 && lines[start - 1].trim().startsWith("#")) {
                start--;
            }
            if (!blocks.isEmpty()) {
                TopLevelBlock previous = blocks.get(blocks.size() - 1);
                blocks.set(blocks.size() - 1, new TopLevelBlock(previous.key(), previous.startLine(), start));
            }
            blocks.add(new TopLevelBlock(matcher.group(1), start, lines.length));
        }
        return blocks;
    }

    /**
     * Splits a config file into its top-level blocks (key -> verbatim text, comments
     * included). Insertion order follows the file.
     */
    private Map<String, String> splitTopLevelBlocks(String text) {
        String normalized = normalize(text);
        String[] lines = normalized.split("\n", -1);
        Map<String, String> blocks = new LinkedHashMap<>();
        for (TopLevelBlock block : scanTopLevelBlocks(normalized)) {
            StringBuilder builder = new StringBuilder();
            for (int i = block.startLine(); i < block.endLine(); i++) {
                builder.append(lines[i]).append('\n');
            }
            blocks.put(block.key(), builder.toString());
        }
        return blocks;
    }

    /**
     * Replaces an existing top-level block (including its header comment) in place, or
     * appends the replacement when the block is absent. All other bytes stay untouched.
     */
    private String replaceTopLevelBlock(String text, String key, String replacement) {
        if (replacement == null) {
            return text;
        }
        String normalized = normalize(text);
        TopLevelBlock target = null;
        for (TopLevelBlock block : scanTopLevelBlocks(normalized)) {
            if (block.key().equals(key)) {
                target = block;
                break;
            }
        }
        String replacementBlock = replacement.stripTrailing() + "\n\n";
        if (target == null) {
            String base = normalized.isEmpty() || normalized.endsWith("\n") ? normalized : normalized + "\n";
            return base + "\n" + replacementBlock;
        }
        String[] lines = normalized.split("\n", -1);
        int startOffset = 0;
        for (int i = 0; i < target.startLine(); i++) {
            startOffset += lines[i].length() + 1;
        }
        int endOffset = startOffset;
        for (int i = target.startLine(); i < target.endLine(); i++) {
            endOffset += lines[i].length() + 1;
        }
        endOffset = Math.min(endOffset, normalized.length());
        return normalized.substring(0, startOffset) + replacementBlock + normalized.substring(endOffset);
    }

    private void extractResource(String fileName, File target) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                return;
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Failed to create folder for " + fileName);
                return;
            }
            Files.copy(in, target.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to extract " + fileName, e);
        }
    }

    static YamlConfiguration deepMerge(YamlConfiguration base, YamlConfiguration overlay) {
        YamlConfiguration out = new YamlConfiguration();
        if (base != null) {
            for (String key : base.getKeys(true)) {
                if (!base.isConfigurationSection(key)) {
                    out.set(key, base.get(key));
                }
            }
        }
        if (overlay != null) {
            for (String key : overlay.getKeys(true)) {
                if (overlay.isConfigurationSection(key)) {
                    continue;
                }
                Object value = overlay.get(key);
                if (value instanceof List<?> list
                        && list.isEmpty()
                        && key.startsWith("layouts.")
                        && out.get(key) instanceof List<?> baseList
                        && !baseList.isEmpty()) {
                    continue;
                }
                if (value instanceof List<?> list
                        && list.isEmpty()
                        && key.startsWith("categories.")
                        && out.get(key) instanceof List<?> baseList
                        && !baseList.isEmpty()) {
                    continue;
                }
                if (value instanceof Map<?, ?> map
                        && map.isEmpty()
                        && key.endsWith(".slots")
                        && key.startsWith("categories.")
                        && out.get(key) instanceof Map<?, ?> baseMap
                        && !baseMap.isEmpty()) {
                    continue;
                }
                out.set(key, value);
            }
        }
        return out;
    }

    public void reload() {
        loadAll();
    }

    public void save(String fileName) {
        FileConfiguration configuration = configs.get(fileName);
        if (configuration == null) {
            return;
        }
        try {
            configuration.save(new File(PluginIdentity.dataFolder(plugin), fileName));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save " + fileName, e);
        }
    }

    public FileConfiguration get(String fileName) {
        FileConfiguration configuration = configs.get(fileName);
        if (configuration == null) {
            throw new IllegalStateException("Configuration '" + fileName + "' has not been loaded yet");
        }
        return configuration;
    }

    public FileConfiguration config() {
        return get(CONFIG);
    }

    public FileConfiguration database() {
        return get(DATABASE);
    }

    public FileConfiguration gui() {
        return get(GUI);
    }

    public FileConfiguration sounds() {
        return get(SOUNDS);
    }

    public FileConfiguration profile() {
        return get(PROFILE);
    }

    public FileConfiguration kits() {
        return get(KITS);
    }

    public FileConfiguration arenas() {
        return get(ARENAS);
    }

    public FileConfiguration practices() {
        return get(PRACTICES);
    }

    public FileConfiguration lobby() {
        return get(LOBBY);
    }

    public FileConfiguration ffa() {
        return get(FFA);
    }

    public FileConfiguration plans() {
        return get(PLANS);
    }

    public FileConfiguration arrowEffects() {
        return get(ARROW_EFFECTS);
    }

    public FileConfiguration killEffects() {
        return get(KILL_EFFECTS);
    }

    public FileConfiguration ekitItems() {
        return get(EKIT_ITEMS);
    }

    public FileConfiguration presetItems() {
        return get(PRESET_ITEMS);
    }

    public FileConfiguration scoreboard() {
        return get(SCOREBOARD);
    }
}
