package com.rumilance.practice.gui;

import com.rumilance.practice.PluginIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders every registered GUI <b>offscreen</b> and writes the resulting grid to JSON, so the
 * screens can be diffed against the {@code docs/design/gui.json} mockups by
 * {@code tools/gui_diff.py}.
 *
 * <p>Reading the code cannot tell you the grid: {@code render()} builds it with loops, helper
 * methods, conditionals and runtime state (kit lists, party membership, queue depth). The only
 * trustworthy source is a live render, and that does <b>not</b> require opening an inventory —
 * {@link AbstractGui#renderPublic} just fills the {@link Inventory} we hand it, so this runs
 * without a single packet.</p>
 *
 * <p>Because a render is allowed to touch the player's own inventory (the KIT SELECT chip panel
 * borrows the bottom rows) the whole player inventory is stashed first and restored afterwards,
 * and the {@link HotbarVacator} is suspended for the duration.</p>
 */
public final class GuiSnapshotService {

    private final Plugin plugin;
    private final GuiSessionRegistry registry;
    private final Map<GuiType, AbstractGui> handlers;
    private final HotbarVacator vacator;

    public GuiSnapshotService(Plugin plugin, GuiSessionRegistry registry,
                              Map<GuiType, AbstractGui> handlers, HotbarVacator vacator) {
        this.plugin = plugin;
        this.registry = registry;
        this.handlers = handlers;
        this.vacator = vacator;
    }

    public Path outputDir() {
        return new java.io.File(PluginIdentity.dataFolder(plugin), "gui-snapshots").toPath();
    }

    /** Renders every registered screen (plus the EKIT_SELECT chooser/KIT SELECT variants). */
    public List<Path> dumpAll(Player operator) throws IOException {
        Path dir = prepare(outputDir());
        List<Path> written = new ArrayList<>();
        for (Map.Entry<GuiType, AbstractGui> entry : handlers.entrySet()) {
            GuiType type = entry.getKey();
            Path file = dump(operator, dir, type, null, 0);
            if (file != null) {
                written.add(file);
            }
            if (type == GuiType.EKIT_SELECT) {
                // The same class paints two mockups: the chooser (category == null) and the
                // KIT SELECT GUI (a category set). Snapshot both under distinct names.
                Path kitSelect = dump(operator, dir, type, "MAIN", 0, "EKIT_SELECT__KIT_SELECT");
                if (kitSelect != null) {
                    written.add(kitSelect);
                }
            }
        }
        return written;
    }

    public Path dump(Player operator, GuiType type, String category, int page) throws IOException {
        return dump(operator, prepare(outputDir()), type, category, page);
    }

    private Path dump(Player operator, Path dir, GuiType type, String category, int page) {
        return dump(operator, dir, type, category, page, type.name());
    }

    private Path dump(Player operator, Path dir, GuiType type, String category, int page,
                      String fileName) {
        AbstractGui gui = handlers.get(type);
        if (gui == null || operator == null || !operator.isOnline()) {
            return null;
        }
        InventorySnapshot before = stash(operator);
        boolean suspended = suspendVacator(true);
        try {
            GuiSession session = registry.open(operator.getUniqueId(), type, gui.rows());
            if (category != null) {
                session.setKitCategory(category);
            }
            session.setPage(Math.max(0, page));
            Inventory inventory = Bukkit.createInventory(
                    new PracticeGuiHolder(session.sessionId(), type, gui.rows()),
                    gui.rows() * 9,
                    Component.text(type.name()));
            gui.renderPublic(operator, session, inventory);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("guiType", type.name());
            root.put("file", fileName);
            root.put("rows", gui.rows());
            root.put("columns", 9);
            if (category != null) {
                root.put("kitCategory", category);
            }
            root.put("container", cells(inventory, gui.rows() * 9));
            root.put("main", cells(operator.getInventory(), 36));
            root.put("bottomOwned", gui instanceof BottomInventoryClickHandler);

            Path file = dir.resolve(fileName + ".json");
            Files.writeString(file, toJson(root) + "\n", StandardCharsets.UTF_8);
            registry.close(operator.getUniqueId());
            return file;
        } catch (Throwable t) {
            plugin.getLogger().warning("[gui-snapshot] " + type + " failed: " + t);
            return null;
        } finally {
            restore(operator, before);
            suspendVacator(suspended);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static List<Object> cells(Inventory inventory, int size) {
        List<Object> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack item = i < inventory.getSize() ? inventory.getItem(i) : null;
            out.add(item == null || item.getType().isAir() ? null : describe(item));
        }
        return out;
    }

    private static Map<String, Object> describe(ItemStack item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getType().getKey().toString());
        map.put("count", item.getAmount());
        String name = null;
        try {
            net.kyori.adventure.text.Component display = item.getItemMeta().displayName();
            if (display != null) {
                name = PlainTextComponentSerializer.plainText().serialize(display);
            }
        } catch (Throwable ignored) {
            // Undisplayable component: the id is what the differ compares anyway.
        }
        if (name != null) {
            map.put("name", name);
        }
        return map;
    }

    /**
     * Snapshot of everything a render is allowed to touch: the 36 storage slots, the four
     * armour slots and the offhand. Kept as three separate arrays on purpose —
     * {@code PlayerInventory#setContents} only accepts a 36 or 41 long array depending on the
     *Paper build, and silently dropping the armour on the wrong length is exactly the kind of
     * surprise a debugging tool must not have.
     */
    private record InventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
    }

    private static InventorySnapshot stash(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack[] storage = clone(inv.getStorageContents());
        ItemStack[] armor = clone(inv.getArmorContents());
        ItemStack offhand = inv.getItemInOffHand();
        return new InventorySnapshot(storage, armor,
                offhand.getType().isAir() ? null : offhand.clone());
    }

    private static void restore(Player player, InventorySnapshot saved) {
        if (saved == null || !player.isOnline()) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        inv.setStorageContents(saved.storage());
        inv.setArmorContents(saved.armor());
        inv.setItemInOffHand(saved.offhand());
    }

    private static ItemStack[] clone(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source == null ? 0 : source.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private boolean suspendVacator(boolean suspended) {
        if (vacator == null) {
            return false;
        }
        boolean previous = vacator.isSuspended();
        vacator.setSuspended(suspended);
        return previous;
    }

    private static Path prepare(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    /** Minimal JSON writer — JDK only, so the build cannot break on a library drift. */
    private static String toJson(Map<String, Object> root) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("  ").append(quote(entry.getKey())).append(": ");
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                sb.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    sb.append("    ").append(value(list.get(i)));
                    if (i < list.size() - 1) {
                        sb.append(',');
                    }
                    sb.append('\n');
                }
                sb.append("  ]");
            } else {
                sb.append(value(value));
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(quote(entry.getKey())).append(": ").append(value(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
