package com.rumilance.practice.scoreboard;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.font.RankIconNameTags;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Plugin-owned TAB sorting and prefix definition loaded from {@code tab-layout.csv}.
 *
 * <p>This is intentionally a tiny CSV format rather than a dependency on an external TAB
 * plugin. Operators can change rank order and visible prefixes in the same kind of flat,
 * spreadsheet-friendly definition used by the server's shared GitHub tables.</p>
 */
public final class TabCustomizationConfig {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String RESOURCE = "tab-layout.csv";

    private final Map<String, Entry> entries;
    private final Entry fallback;

    private TabCustomizationConfig(Map<String, Entry> entries, Entry fallback) {
        this.entries = Map.copyOf(entries);
        this.fallback = fallback;
    }

    public record Entry(String group, int priority, Component prefix, boolean visible) {
    }

    /** Loads the bundled definition and creates an editable operator copy if needed. */
    public static TabCustomizationConfig load(Plugin plugin) {
        File file = new File(PluginIdentity.dataFolder(plugin), RESOURCE);
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            if (!file.isFile()) {
                try (InputStream in = plugin.getResource(RESOURCE)) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            }
            if (file.isFile()) {
                TabCustomizationConfig parsed = parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
                plugin.getLogger().info("Loaded internal TAB definition: " + file.getPath());
                return parsed;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not load " + RESOURCE + "; using the built-in TAB defaults.", e);
        }
        return defaults();
    }

    /** Rank sort order. Larger values are placed earlier in the client tab list. */
    public int order(Player player, RankService rankService) {
        Entry entry = entryFor(player, rankService);
        int tieBreaker = Math.floorMod(player.getName().toLowerCase(Locale.ROOT).hashCode(), 9_000);
        return 900_000 + (entry.priority() * 10_000) - tieBreaker;
    }

    /** Prefix configured for the player group; empty prefix means no extra CSV text. */
    public Component prefix(Player player, RankService rankService) {
        return entryFor(player, rankService).prefix();
    }

    public boolean visible(Player player, RankService rankService) {
        return entryFor(player, rankService).visible();
    }

    private Entry entryFor(Player player, RankService rankService) {
        String group = groupOf(player, rankService);
        return entries.getOrDefault(group, fallback);
    }

    /**
     * Display group of a player, taken from the <strong>stored rank</strong> (the same value the
     * rank badge uses) — never from permissions. Bukkit answers {@code true} for an unregistered
     * permission node on any OP, so a permission-based group silently turned every operator into
     * the CSV "owner" group (the reported "OP に自動で OWNER が付く"). OWNER is granted only by
     * {@code /rank <player> owner} (or {@code admin}).
     */
    private static String groupOf(Player player, RankService rankService) {
        PlayerRank rank = RankIconNameTags.effectiveRank(rankService, player);
        return switch (rank) {
            case ADMIN -> "admin";
            case VIP_PLUS -> "vipplus";
            case VIP -> "vip";
            case PRO -> "pro";
            default -> "default";
        };
    }

    private static TabCustomizationConfig parse(List<String> lines) {
        Map<String, Entry> entries = new HashMap<>();
        Entry fallback = defaults().fallback;
        for (String raw : lines) {
            if (raw == null || raw.isBlank() || raw.stripLeading().startsWith("#")) {
                continue;
            }
            List<String> columns = csv(raw);
            if (columns.size() < 5 || columns.get(0).equalsIgnoreCase("context")) {
                continue;
            }
            if (!columns.get(0).equalsIgnoreCase("all") && !columns.get(0).equalsIgnoreCase("lobby")) {
                continue;
            }
            try {
                String group = columns.get(2).trim().toLowerCase(Locale.ROOT);
                int priority = Integer.parseInt(columns.get(1).trim());
                Component prefix = LEGACY.deserialize(columns.get(3));
                boolean visible = Boolean.parseBoolean(columns.get(4).trim());
                Entry entry = new Entry(group, priority, prefix, visible);
                entries.put(group, entry);
                if (group.equals("default")) {
                    fallback = entry;
                }
            } catch (RuntimeException ignored) {
                // One malformed spreadsheet row must not prevent the plugin from enabling.
            }
        }
        if (entries.isEmpty()) {
            return defaults();
        }
        return new TabCustomizationConfig(entries, fallback);
    }

    /** Small RFC4180-compatible-enough parser for the five-column operator file. */
    private static List<String> csv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                result.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        result.add(field.toString().trim());
        return result;
    }

    private static TabCustomizationConfig defaults() {
        Map<String, Entry> values = new HashMap<>();
        values.put("owner", new Entry("owner", 500, LEGACY.deserialize("§c§lOWNER §f"), true));
        values.put("admin", new Entry("admin", 400, LEGACY.deserialize("§c§lADMIN §f"), true));
        values.put("mod", new Entry("mod", 300, LEGACY.deserialize("§6§lMOD §f"), true));
        values.put("helper", new Entry("helper", 250, LEGACY.deserialize("§eHELPER §f"), true));
        values.put("pro", new Entry("pro", 200, LEGACY.deserialize("§bPRO §f"), true));
        values.put("vipplus", new Entry("vipplus", 150, LEGACY.deserialize("§6N+ §f"), true));
        values.put("vip", new Entry("vip", 100, LEGACY.deserialize("§eN §f"), true));
        values.put("default", new Entry("default", 0, LEGACY.deserialize("§7"), true));
        return new TabCustomizationConfig(values, values.get("default"));
    }
}
