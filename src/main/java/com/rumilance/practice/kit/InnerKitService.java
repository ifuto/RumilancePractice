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
 * <p><b>The default is not a stored preset and cannot be changed.</b> What Queue hands out —
 * the kit's own items, {@link KitLoadout#fromOfficial} — is always the default, is always
 * listed first as {@code <kit> [Default]}, cannot be created over, renamed, edited as a
 * preset or removed, and no other preset can be promoted to default. Choosing a 中キット is
 * only possible where it is offered: Duel Request, Party Fight and Kit Edit. Queue ignores
 * the choice entirely and always fights the default.</p>
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

    public InnerKitService(ConfigService configService, Logger logger) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.logger = logger;
        reload();
    }

    // ---------------------------------------------------------------- read

    /** Re-reads every {@code inner-kits} section (plugin reload, /kit reload, after edits). */
    public void reload() {
        byKit.clear();
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
                if (id.isEmpty() || isDefault(id)) {
                    // A hand-written "default" section would shadow the kit itself: ignore it
                    // rather than pretending the default can be redefined.
                    logger.warning("[N Arena][InnerKit] ignoring reserved preset id '"
                            + innerKey + "' on kit " + kitKey);
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

    /** Display name of a choice, for lore and the match start message. */
    public String displayOf(String kitId, String innerId, String kitDisplayName) {
        if (isDefault(innerId)) {
            return (kitDisplayName == null || kitDisplayName.isBlank() ? kitId : kitDisplayName)
                    + " " + DEFAULT_BADGE;
        }
        return get(kitId, innerId).map(InnerKit::displayName)
                .orElse(kitDisplayName == null ? kitId : kitDisplayName);
    }

    // ---------------------------------------------------------------- write

    /**
     * Creates a preset. {@code seed} is the loadout it starts with — normally the kit's current
     * items ({@link KitLoadout#fromOfficial}) so the admin edits a copy instead of an empty grid.
     */
    public CreateResult create(String kitId, String name, ItemStack[] seed) {
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
        InnerKit inner = new InnerKit(id, display, null, layout);
        map.put(id, inner);
        write(inner, kitId, display, null, layout);
        return CreateResult.OK;
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
            logger.warning("[N Arena][InnerKit] cannot persist preset " + inner.id()
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
                logger.log(Level.WARNING, "[N Arena][InnerKit] bad item in preset "
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

    /** Lowercases and trims an id ("" for null); the map key form used everywhere. */
    public static String normalizeId(String innerId) {
        return innerId == null ? "" : innerId.trim().toLowerCase(Locale.ROOT);
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
