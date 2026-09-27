package com.rumilance.practice.kit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.util.ItemSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 中キット (inner kits): named presets that live <b>inside</b> one official kit.
 *
 * <p>An {@code Axe} kit can carry {@code HQ Style Axe}, {@code Club Style Axe},
 * {@code Hatena Style Axe} … Each one is a full 41-slot loadout (main inventory, armour,
 * off-hand) that replaces the kit's own items — every other rule of the fight (max health,
 * pearls, block rules, arenas, start effects) still comes from the parent kit, because a
 * 中キット is a loadout, not a new kit.</p>
 *
 * <p><b>The default is not a stored preset and its contents cannot be changed.</b> What Queue
 * hands out — the kit's own items, {@link KitLoadout#fromOfficial} — is always the default, is
 * always listed first, cannot be created over, edited as a preset or removed, and no other
 * preset can be promoted to default. Its <b>label</b> is separate from the kit's name:
 * {@code /kit preset default axe HQ Style Axe} lists the kit {@code Axe} as
 * {@code HQ Style Axe [Default]}, stored as {@code inner-kits.default.display-name} — a label
 * only, and a {@code layout} written there is ignored. A 中キット can be chosen only where a
 * RIGHT click offers the list: Duel Request, Party Fight and Kit Edit. A LEFT click and every
 * queue path fight the default, exactly as before.</p>
 *
 * <p><b>A preset's contents are complete.</b> Applying one goes through
 * {@link KitService#applyExact}, never {@link KitLoadout#resolve}: slots the preset leaves empty
 * stay empty instead of being filled back from the kit, because a preset differs in the items
 * themselves, not only in their arrangement.</p>
 *
 * <p>Storage is {@code kits.<kit>.inner-kits.<id>} in kits.yml — {@code display-name},
 * {@code icon} and a {@code layout} map of {@code <slot>: <base64 item>} for the non-empty
 * slots, using the same {@link ItemSerializer} encoding the kit's own armour entries use.
 * Presets are created and removed with {@code /kit preset …} (admin) and their contents are
 * edited in the normal kit editor.</p>
 */
public final class InnerKitService {

    /**
     * The pseudo id of the kit itself. Never stored, never removable, never editable as a
     * preset: {@code /kit preset add axe default} is rejected so the default stays the kit.
     */
    public static final String DEFAULT_ID = "default";

    /** Suffix the pickers show on the default entry, e.g. {@code HQ Style Axe [Default]}. */
    public static final String DEFAULT_BADGE = "[Default]";

    /** One 中キット: id (storage key), display name, icon override and its loadout. */
    public record InnerKit(String id, String displayName, String icon, ItemStack[] layout) {

        public InnerKit {
            id = normalizeId(id);
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
            layout = copy(layout);
        }

        /** A copy with a different loadout (the editor's save path). */
        public InnerKit withLayout(ItemStack[] newLayout) {
            return new InnerKit(id, displayName, icon, newLayout);
        }
    }

    /** Outcome of {@link #create}, so the command can say exactly what went wrong. */
    public enum CreateResult {
        OK,
        NO_SUCH_KIT,
        BLANK_NAME,
        RESERVED_NAME,
        ALREADY_EXISTS
    }

    private final ConfigService configService;
    private final Logger logger;
    /** kit id (lowercase) -> presets in declaration order. */
    private final Map<String, Map<String, InnerKit>> byKit = new ConcurrentHashMap<>();
    /** kit id (lowercase) -> label of the default entry (absent = show the kit's own name). */
    private final Map<String, String> defaultNames = new ConcurrentHashMap<>();

    public InnerKitService(ConfigService configService, Logger logger) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.logger = logger;
        reload();
    }

    // ---------------------------------------------------------------- read

    /** Re-reads every {@code inner-kits} section (plugin reload, /kit reload, after edits). */
    public void reload() {
        byKit.clear();
        defaultNames.clear();
        FileConfiguration yaml = configService.kits();
        ConfigurationSection kits = yaml.getConfigurationSection("kits");
        if (kits == null) {
            return;
        }
        for (String kitKey : kits.getKeys(false)) {
            ConfigurationSection inner = kits.getConfigurationSection(kitKey + ".inner-kits");
            if (inner == null) {
                continue;
            }
            Map<String, InnerKit> loaded = new LinkedHashMap<>();
            for (String innerKey : inner.getKeys(false)) {
                ConfigurationSection section = inner.getConfigurationSection(innerKey);
                if (section == null) {
                    continue;
                }
                String id = normalizeId(innerKey);
                if (id.isEmpty()) {
                    continue;
                }
                if (isDefault(id)) {
                    // "default" is the kit itself: only its LABEL is configurable here, never a
                    // loadout. A hand-written layout is ignored so the default can never drift
                    // away from what Queue and every left-click hand out.
                    String label = section.getString("display-name", null);
                    if (label != null && !label.isBlank()) {
                        defaultNames.put(kitKey.toLowerCase(Locale.ROOT), label.trim());
                    }
                    if (section.getConfigurationSection("layout") != null) {
                        warn("[N Arena][InnerKit] ignoring 'layout' under inner-kits."
                                + DEFAULT_ID + " on kit " + kitKey
                                + " - the default's contents are the kit itself");
                    }
                    continue;
                }
                String display = section.getString("display-name", id);
                String icon = section.getString("icon", null);
                loaded.put(id, new InnerKit(id, display, icon, readLayout(section)));
            }
            if (!loaded.isEmpty()) {
                byKit.put(kitKey.toLowerCase(Locale.ROOT), loaded);
            }
        }
    }

    /** True when this kit has at least one 中キット (the default alone does not count). */
    public boolean has(String kitId) {
        return !list(kitId).isEmpty();
    }

    /** The kit's presets in declaration order, never including the implicit default. */
    public List<InnerKit> list(String kitId) {
        Map<String, InnerKit> map = kitId == null ? null : byKit.get(kitId.toLowerCase(Locale.ROOT));
        return map == null ? List.of() : List.copyOf(map.values());
    }

    /** One preset by id; empty for the default (the default is the kit, not a preset). */
    public Optional<InnerKit> get(String kitId, String innerId) {
        if (kitId == null || isDefault(innerId)) {
            return Optional.empty();
        }
        Map<String, InnerKit> map = byKit.get(kitId.toLowerCase(Locale.ROOT));
        return map == null
                ? Optional.empty()
                : Optional.ofNullable(map.get(normalizeId(innerId)));
    }

    /**
     * The loadout to fight with: the preset's items, or empty for the default / an unknown id so
     * the caller keeps the kit's own behaviour (including the player's personal rearrangement).
     */
    public Optional<ItemStack[]> layout(String kitId, String innerId) {
        return get(kitId, innerId).map(InnerKit::layout);
    }

    /**
     * The label the pickers show for the default entry: the one set with
     * {@code /kit preset default <kit> <name>}, or the kit's own display name when none is set —
     * always badged {@code [Default]} so the locked entry stays recognisable.
     */
    public Optional<String> defaultName(String kitId) {
        if (kitId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(defaultNames.get(kitId.toLowerCase(Locale.ROOT)));
    }

    /** Display name of a choice, for lore, tiles and the match start message. */
    public String displayOf(String kitId, String innerId, String kitDisplayName) {
        if (isDefault(innerId)) {
            String label = defaultName(kitId).orElse(null);
            if (label == null || label.isBlank()) {
                label = kitDisplayName == null || kitDisplayName.isBlank() ? kitId : kitDisplayName;
            }
            return withBadge(label);
        }
        return get(kitId, innerId).map(InnerKit::displayName)
                .orElse(kitDisplayName == null ? kitId : kitDisplayName);
    }

    // ---------------------------------------------------------------- write

    /** Creates a preset seeded with {@code seed}; the kit's own icon is shown in the picker. */
    public CreateResult create(String kitId, String name, ItemStack[] seed) {
        return create(kitId, name, seed, null);
    }

    /**
     * Creates a preset. {@code seed} is its complete contents — normally a snapshot of the
     * admin's inventory ({@link KitLoadout#fromPlayer}, like {@code /kit create}), a copy of the
     * kit's own items ({@link KitLoadout#fromOfficial}) when the preset starts as a variation, or
     * an empty grid. Whatever it is, it is authoritative: the kit never fills slots back in.
     * {@code icon} (nullable material name) is the picker tile; the kit's icon is used when null.
     */
    public CreateResult create(String kitId, String name, ItemStack[] seed, String icon) {
        if (!kitExists(kitId)) {
            return CreateResult.NO_SUCH_KIT;
        }
        String id = slug(name);
        if (id == null) {
            return CreateResult.BLANK_NAME;
        }
        if (isDefault(id)) {
            return CreateResult.RESERVED_NAME;
        }
        String key = kitId.toLowerCase(Locale.ROOT);
        Map<String, InnerKit> map = byKit.computeIfAbsent(key, k -> new LinkedHashMap<>());
        if (map.containsKey(id)) {
            return CreateResult.ALREADY_EXISTS;
        }
        String display = name.trim();
        ItemStack[] layout = seed != null ? copy(seed) : new ItemStack[KitLoadout.SIZE];
        String material = icon == null || icon.isBlank()
                ? null : icon.trim().toUpperCase(Locale.ROOT);
        InnerKit inner = new InnerKit(id, display, material, layout);
        map.put(id, inner);
        write(inner, kitId, display, material, layout);
        return CreateResult.OK;
    }

    /**
     * Sets the label of the default entry — the one name in the list that is not the kit's name,
     * so a kit {@code Axe} can be listed as {@code HQ Style Axe [Default]}. A blank name,
     * {@code -} or {@code reset} clears it and the kit's own display name shows again.
     *
     * <p>Contents are never touched: the default stays the kit's own loadout, which is what Queue
     * and every left-click hands out.</p>
     */
    public boolean setDefaultName(String kitId, String name) {
        if (!kitExists(kitId)) {
            return false;
        }
        ConfigurationSection parent = innerSection(kitId, true);
        if (parent == null) {
            return false;
        }
        String key = kitId.toLowerCase(Locale.ROOT);
        String label = name == null ? "" : name.trim();
        if (label.isEmpty() || label.equals("-") || label.equalsIgnoreCase("reset")) {
            defaultNames.remove(key);
            parent.set(DEFAULT_ID, null);
            configService.save(ConfigService.KITS);
            return true;
        }
        defaultNames.put(key, label);
        ConfigurationSection section = parent.getConfigurationSection(DEFAULT_ID);
        if (section == null) {
            section = parent.createSection(DEFAULT_ID);
        }
        section.set("display-name", label);
        configService.save(ConfigService.KITS);
        return true;
    }

    /**
     * Renames a preset — the label the pickers show. The storage id (and therefore the saved
     * layout) stays put, so renaming never loses contents. The default is not a preset: its label
     * is {@link #setDefaultName}.
     */
    public boolean setDisplayName(String kitId, String innerId, String name) {
        if (kitId == null || isDefault(innerId) || name == null || name.isBlank()) {
            return false;
        }
        String id = normalizeId(innerId);
        Map<String, InnerKit> map = byKit.get(kitId.toLowerCase(Locale.ROOT));
        InnerKit existing = map == null ? null : map.get(id);
        if (existing == null) {
            return false;
        }
        InnerKit renamed = new InnerKit(id, name.trim(), existing.icon(), existing.layout());
        map.put(id, renamed);
        write(renamed, kitId, renamed.displayName(), renamed.icon(), renamed.layout());
        return true;
    }

    /**
     * Sets the picker icon of a preset (a material name, e.g. what the admin holds in hand).
     * Null or blank clears it and the kit's own icon is used again.
     */
    public boolean setIcon(String kitId, String innerId, String materialName) {
        if (kitId == null || isDefault(innerId)) {
            return false;
        }
        String id = normalizeId(innerId);
        Map<String, InnerKit> map = byKit.get(kitId.toLowerCase(Locale.ROOT));
        InnerKit existing = map == null ? null : map.get(id);
        if (existing == null) {
            return false;
        }
        String icon = materialName == null || materialName.isBlank()
                ? null : materialName.trim().toUpperCase(Locale.ROOT);
        InnerKit updated = new InnerKit(id, existing.displayName(), icon, existing.layout());
        map.put(id, updated);
        write(updated, kitId, updated.displayName(), icon, updated.layout());
        return true;
    }

    /** Removes a preset. The default cannot be removed (it is the kit itself). */
    public boolean remove(String kitId, String innerId) {
        if (kitId == null || isDefault(innerId)) {
            return false;
        }
        String key = kitId.toLowerCase(Locale.ROOT);
        Map<String, InnerKit> map = byKit.get(key);
        if (map == null || map.remove(normalizeId(innerId)) == null) {
            return false;
        }
        if (map.isEmpty()) {
            byKit.remove(key);
        }
        ConfigurationSection section = innerSection(kitId, false);
        if (section != null) {
            section.set(normalizeId(innerId), null);
            configService.save(ConfigService.KITS);
        }
        return true;
    }

    /** Saves a preset's loadout (the kit editor's save button). The default is never writable. */
    public boolean saveLayout(String kitId, String innerId, ItemStack[] layout) {
        if (kitId == null || isDefault(innerId) || layout == null) {
            return false;
        }
        String id = normalizeId(innerId);
        Map<String, InnerKit> map = byKit.get(kitId.toLowerCase(Locale.ROOT));
        InnerKit existing = map == null ? null : map.get(id);
        if (existing == null) {
            return false;
        }
        ItemStack[] copy = copy(layout);
        map.put(id, existing.withLayout(copy));
        write(existing, kitId, existing.displayName(), existing.icon(), copy);
        return true;
    }

    // ---------------------------------------------------------------- storage

    /** {@code logger} is optional, so the service stays usable from tools and tests. */
    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    private void warn(String message, Throwable cause) {
        if (logger != null) {
            logger.log(Level.WARNING, message, cause);
        }
    }

    private boolean kitExists(String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return false;
        }
        ConfigurationSection kits = configService.kits().getConfigurationSection("kits");
        if (kits == null) {
            return false;
        }
        for (String key : kits.getKeys(false)) {
            if (key.equalsIgnoreCase(kitId)) {
                return true;
            }
        }
        return false;
    }

    /** The {@code kits.<kit>.inner-kits} section, creating it when {@code create} is true. */
    private ConfigurationSection innerSection(String kitId, boolean create) {
        FileConfiguration yaml = configService.kits();
        ConfigurationSection kits = yaml.getConfigurationSection("kits");
        if (kits == null) {
            return null;
        }
        String actual = null;
        for (String key : kits.getKeys(false)) {
            if (key.equalsIgnoreCase(kitId)) {
                actual = key;
                break;
            }
        }
        if (actual == null) {
            return null;
        }
        ConfigurationSection section = kits.getConfigurationSection(actual + ".inner-kits");
        if (section == null && create) {
            section = kits.createSection(actual + ".inner-kits");
        }
        return section;
    }

    private void write(InnerKit inner, String kitId, String display, String icon, ItemStack[] layout) {
        ConfigurationSection parent = innerSection(kitId, true);
        if (parent == null) {
            warn("[N Arena][InnerKit] cannot persist preset " + inner.id()
                    + ": kit section " + kitId + " is gone");
            return;
        }
        ConfigurationSection section = parent.getConfigurationSection(inner.id());
        if (section == null) {
            section = parent.createSection(inner.id());
        }
        section.set("display-name", display == null || display.isBlank() ? inner.id() : display);
        if (icon != null && !icon.isBlank()) {
            section.set("icon", icon);
        }
        section.set("layout", null);   // drop slots that are empty now
        Map<String, String> encoded = encodeLayout(layout);
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            section.set("layout." + entry.getKey(), entry.getValue());
        }
        configService.save(ConfigService.KITS);
    }

    /** {@code <slot> -> base64 item} for the non-empty slots of a 41-slot loadout. */
    private static Map<String, String> encodeLayout(ItemStack[] layout) {
        Map<String, String> out = new LinkedHashMap<>();
        if (layout == null) {
            return out;
        }
        for (int slot = 0; slot < layout.length && slot < KitLoadout.SIZE; slot++) {
            ItemStack item = layout[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String encoded = ItemSerializer.singleToBase64(item);
            if (encoded != null && !encoded.isBlank()) {
                out.put(Integer.toString(slot), encoded);
            }
        }
        return out;
    }

    /** Inverse of {@link #encodeLayout}; unreadable slots are skipped, never fatal. */
    private ItemStack[] readLayout(ConfigurationSection section) {
        ItemStack[] layout = new ItemStack[KitLoadout.SIZE];
        ConfigurationSection stored = section.getConfigurationSection("layout");
        if (stored == null) {
            return layout;
        }
        for (String key : stored.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key.trim());
            } catch (NumberFormatException notASlot) {
                continue;
            }
            if (slot < 0 || slot >= KitLoadout.SIZE) {
                continue;
            }
            String encoded = stored.getString(key, null);
            if (encoded == null || encoded.isBlank()) {
                continue;
            }
            try {
                layout[slot] = ItemSerializer.singleFromBase64(encoded);
            } catch (RuntimeException badItem) {
                warn("[N Arena][InnerKit] bad item in preset "
                        + section.getName() + " slot " + slot, badItem);
            }
        }
        return layout;
    }

    // ---------------------------------------------------------------- pure helpers

    /** True for {@code null}, blank and the reserved default id — i.e. "use the kit itself". */
    public static boolean isDefault(String innerId) {
        return innerId == null || innerId.isBlank()
                || DEFAULT_ID.equalsIgnoreCase(innerId.trim());
    }

    /** Appends {@link #DEFAULT_BADGE} unless the label already ends with it (any casing). */
    public static String withBadge(String label) {
        String text = label == null ? "" : label.trim();
        if (text.isEmpty()) {
            return DEFAULT_BADGE;
        }
        String badge = DEFAULT_BADGE.toLowerCase(Locale.ROOT);
        return text.toLowerCase(Locale.ROOT).endsWith(badge) ? text : text + " " + DEFAULT_BADGE;
    }

    /** Lowercases and trims an id ("" for null); the map key form used everywhere. */
    public static String normalizeId(String innerId) {
        return innerId == null ? "" : innerId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Storage key for one player's own arrangement of one 中キット, used against the same
     * kit_layouts table / cache that holds plain kit layouts. Composite on purpose: the
     * {@code #preset#} infix can never be produced by a crystal variant key ({@code axe#v3}) or by
     * a kit name, and both halves are lowercased so the GUI (session kit id) and match start
     * ({@code kit.name()}) always agree.
     */
    public static String layoutKey(String kitId, String innerId) {
        return (kitId == null ? "" : kitId.trim().toLowerCase(Locale.ROOT))
                + "#preset#" + normalizeId(innerId);
    }

    /**
     * Storage id for a preset name: lowercase, spaces and underscores become dashes, anything
     * that is not {@code a-z 0-9 -} is dropped, dashes collapse, max 48 chars. {@code null} when
     * nothing usable is left, so a name of only symbols cannot create a preset.
     */
    public static String slug(String name) {
        if (name == null) {
            return null;
        }
        String id = name.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (id.isEmpty()) {
            return null;
        }
        return id.length() > 48 ? id.substring(0, 48) : id;
    }

    /** Every preset id of every kit — used by /kit reload and the admin listing. */
    public List<String> kitIdsWithPresets() {
        return new ArrayList<>(byKit.keySet());
    }

    private static ItemStack[] copy(ItemStack[] layout) {
        ItemStack[] out = new ItemStack[KitLoadout.SIZE];
        if (layout == null) {
            return out;
        }
        for (int i = 0; i < layout.length && i < out.length; i++) {
            out[i] = layout[i] == null ? null : layout[i].clone();
        }
        return out;
    }
}
