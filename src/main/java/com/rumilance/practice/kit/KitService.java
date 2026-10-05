package com.rumilance.practice.kit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitCategory;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.model.KitStartEffect;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads / persists official kits and applies them to players.
 */
public final class KitService {

    private final ConfigService configService;
    /** In-memory kits.yml supplied by pure JUnit tests (no server or scheduler required). */
    private final FileConfiguration standaloneYaml;
    private final Map<String, KitDefinition> kits = new ConcurrentHashMap<>();
    private final Map<String, Boolean> queueEnabled = new ConcurrentHashMap<>();
    /** Admin-defined display order (lower index first); kits not listed sort alphabetically after. */
    private final List<String> sortOrder = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Optional DB copy hooks: installed after the database is ready, before any migration. */
    private java.util.function.BiConsumer<String, String> copyLayouts = (from, to) -> { };
    private java.util.function.BiConsumer<String, String> copyRankedStats = (from, to) -> { };

    /** Keep existing player layouts and ranked results when a kit becomes a folder. */
    public void setMigrationCallbacks(java.util.function.BiConsumer<String, String> layouts,
                                      java.util.function.BiConsumer<String, String> rankedStats) {
        this.copyLayouts = Objects.requireNonNull(layouts);
        this.copyRankedStats = Objects.requireNonNull(rankedStats);
    }

    /** For the one-time old inner-kit migration (those rows are full snapshots, not deltas). */
    public void copyPersonalLayouts(String from, String to) {
        copyLayouts.accept(from, to);
    }

    public KitService(ConfigService configService) {
        this.configService = Objects.requireNonNull(configService);
        this.standaloneYaml = null;
        reload();
    }

    /** Test seam: exercise real YAML round-trips without a running Paper server. */
    KitService(FileConfiguration kitsYaml) {
        this.configService = null;
        this.standaloneYaml = Objects.requireNonNull(kitsYaml);
        reload();
    }

    private FileConfiguration yaml() {
        return standaloneYaml == null ? configService.kits() : standaloneYaml;
    }

    private void saveKits() {
        if (configService != null) {
            configService.save(ConfigService.KITS);
        }
    }

    public void reload() {
        kits.clear();
        FileConfiguration yaml = yaml();
        ConfigurationSection root = yaml.getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            KitDefinition.Builder builder = KitDefinition.builder(id)
                    .displayName(section.getString("display-name", id))
                    .icon(section.getString("icon", "DIAMOND_SWORD"))
                    .category(KitCategory.parse(section.getString("category", "MAIN")))
                    .ranked(section.getBoolean("ranked", true))
                    .ffaEnabled(section.getBoolean("ffa-enabled", true))
                    .maxHealth(section.getDouble("max-health", 20.0d))
                    .naturalHealthRegen(section.getBoolean("natural-health-regen", true))
                    .knockbackMultiplier(section.getDouble("knockback-multiplier", 1.0d))
                    .enabled(section.getBoolean("enabled", true))
                    .autoFood(section.getBoolean("auto-food", false))
                    .swordShieldBreak(section.getBoolean("sword-shield-break", false))
                    .blockPlace(section.getBoolean("block-place", false))
                    .blockBreak(section.getBoolean("block-break", false))
                    .breakPlayerPlacedOnly(section.getBoolean("break-player-placed-only", false))
                    .pearl(section.getBoolean("pearl", true))
                    .totem(section.getBoolean("totem", true))
                    .forceAdventure(section.getBoolean("adventure", false))
                    .timeoutSeconds(section.getInt("timeout-seconds", 0))
                    .canBreak(section.getStringList("can-break"))
                    .presetEnabled(section.getBoolean("preset-enabled", false))
                    // "Bed Explosion" kit rule: beds detonate on right click like Nether/End beds.
                    .bedExplosion(section.getBoolean("bed-explosion", false))
                    // "Crystal FFA" declaration: THE crystal FFA kit gets the KIT1..9 variant editor.
                    .crystalFfa(section.getBoolean("crystal-ffa", false))
                    // Heart indicator (♥ HP readout under the nametag). ON unless a kit opts out.
                    .heartIndicator(section.getBoolean("heart-indicator", true))
                    // 中メニュー: `parent` = このキットは別キットの子メニューの中に入っている、
                    // `default-child` = フォルダ化した自分のタイルが使う子。どちらも普通のキット id。
                    .parent(section.getString("parent", null))
                    .defaultChild(section.getString("default-child", null));

            List<String> arenaList = section.getStringList("arenas");
            if (arenaList.isEmpty()) {
                String legacyArena = section.getString("arena", "");
                if (legacyArena != null && !legacyArena.isBlank()) {
                    arenaList = List.of(legacyArena);
                }
            }
            builder.arenas(arenaList);
            builder.partyArenas(section.getStringList("party-arenas"));

            List<KitItemEntry> items = new ArrayList<>();
            List<Map<?, ?>> itemMaps = section.getMapList("items");
            for (Map<?, ?> map : itemMaps) {
                Object slotObj = map.get("slot");
                Object materialObj = map.get("material");
                Object amountObj = map.get("amount");
                Object dataObj = map.get("data");
                int slot = slotObj instanceof Number number ? number.intValue() : 0;
                String material = materialObj == null ? "STONE" : String.valueOf(materialObj);
                int amount = amountObj instanceof Number number ? number.intValue() : 1;
                // "data" carries the full serialized ItemStack (enchantments, potion effects,
                // custom names, ...) so kits created from a live inventory keep their NBT.
                String data = dataObj == null ? null : String.valueOf(dataObj);
                // Readable enchantments for hand-written kits: `enchantments: {sharpness: 5}`.
                Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
                Object enchObj = map.get("enchantments");
                if (enchObj instanceof Map<?, ?> enchMap) {
                    for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                        if (e.getKey() == null) {
                            continue;
                        }
                        int level = 1;
                        if (e.getValue() instanceof Number number) {
                            level = number.intValue();
                        } else if (e.getValue() != null) {
                            try {
                                level = Integer.parseInt(String.valueOf(e.getValue()).trim());
                            } catch (NumberFormatException ignored) {
                                level = 1;
                            }
                        }
                        if (level > 0) {
                            enchants.put(String.valueOf(e.getKey()), level);
                        }
                    }
                }
                // 読み書きできる形での `unbreakable: true`(参照パックの装備はほぼ全て
                // unbreakable なので、これが無いと再現できない)。
                boolean unbreakable = Boolean.parseBoolean(String.valueOf(map.get("unbreakable")));
                items.add(new KitItemEntry(slot, material, amount, null, data, enchants, unbreakable));
            }
            builder.items(items);

            Map<String, String> armor = new LinkedHashMap<>();
            ConfigurationSection armorSection = section.getConfigurationSection("armor");
            if (armorSection != null) {
                for (String key : armorSection.getKeys(false)) {
                    String value = armorSection.getString(key);
                    if (value != null && !"null".equalsIgnoreCase(value)) {
                        armor.put(key, value);
                    }
                }
            }
            builder.armor(armor);
            builder.startCommands(section.getStringList("start-commands"));
            builder.startEffects(parseStartEffects(section.getMapList("start-effects")));
            kits.put(id.toLowerCase(Locale.ROOT), builder.build());
            queueEnabled.putIfAbsent(id.toLowerCase(Locale.ROOT), true);
        }
        sortOrder.clear();
        sortOrder.addAll(yaml.getStringList("kit-order").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(kits::containsKey)
                .toList());
        enforceCrystalFfaExclusivity();
    }

    /**
     * At most one kit is THE crystal FFA kit. Hand-edited configs with several flagged kits
     * keep the first (kit-order order) and silently unflag the rest.
     */
    private void enforceCrystalFfaExclusivity() {
        KitDefinition winner = null;
        for (String id : orderedIds()) {
            KitDefinition kit = kits.get(id);
            if (kit == null || !kit.crystalFfa()) {
                continue;
            }
            if (winner == null) {
                winner = kit;
            } else {
                save(kit.toBuilder().crystalFfa(false).build());
            }
        }
    }

    /** All kit ids in display order, then any unlisted ones (stable). */
    private java.util.List<String> orderedIds() {
        java.util.List<String> out = new ArrayList<>(sortOrder);
        for (String id : kits.keySet()) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public Optional<KitDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Every kit, children included — admin lookups and tab-completion, not the player lists. */
    public List<KitDefinition> all() {
        return sorted(kits.values());
    }

    /**
     * The kits a player list shows: enabled top-level kits. A child kit (中メニューの中身) never
     * appears here — it is reached through its parent's tile, so every picker, the queue and the
     * kit tab-completion stay exactly as wide as they were before sub-menus existed.
     */
    public List<KitDefinition> enabled() {
        return sorted(kits.values().stream()
                .filter(KitDefinition::enabled)
                .filter(kit -> !hasValidParent(kit))
                .toList());
    }

    /** Enabled top-level kits of one category, in the admin-defined display order. */
    public List<KitDefinition> enabled(KitCategory category) {
        return sorted(kits.values().stream()
                .filter(KitDefinition::enabled)
                .filter(kit -> !hasValidParent(kit))
                .filter(k -> k.category() == category)
                .toList());
    }

    // ------------------------------------------------------------------ 中メニュー (sub-menus)

    /**
     * Top-level kits — the ones a kit list shows. A child kit lives inside its parent's sub-menu
     * and is only reachable from there. An invalid/missing parent is treated as top-level, rather
     * than making the kit disappear because somebody hand-edited kits.yml incorrectly.
     */
    public List<KitDefinition> topLevel() {
        return sorted(kits.values().stream().filter(kit -> !hasValidParent(kit)).toList());
    }

    private boolean hasValidParent(KitDefinition kit) {
        if (kit == null || !kit.isChild() || kit.name().equalsIgnoreCase(kit.parent())) {
            return false;
        }
        KitDefinition parent = kits.get(kit.parent());
        return parent != null && !parent.isChild();
    }

    /** The kits stored inside {@code parentId}'s sub-menu, in display order. */
    public List<KitDefinition> children(String parentId) {
        String key = parentId == null ? "" : parentId.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return List.of();
        }
        KitDefinition parent = kits.get(key);
        if (parent == null || parent.isChild()) {
            return List.of();
        }
        return sorted(kits.values().stream()
                .filter(k -> key.equals(k.parent()) && hasValidParent(k)).toList());
    }

    /** True when {@code kitId} holds a sub-menu, i.e. its tile is a folder rather than a kit. */
    public boolean isFolder(String kitId) {
        return !children(kitId).isEmpty();
    }

    /**
     * The child a folder's own tile uses: the admin's {@code default-child} while it still points at
     * a real child, otherwise the first one. Empty for a kit without a sub-menu.
     */
    public Optional<KitDefinition> defaultChild(String kitId) {
        List<KitDefinition> kids = children(kitId);
        if (kids.isEmpty()) {
            return Optional.empty();
        }
        String wanted = get(kitId).map(KitDefinition::defaultChild).orElse(null);
        if (wanted != null) {
            for (KitDefinition kid : kids) {
                if (kid.name().equalsIgnoreCase(wanted)) {
                    return Optional.of(kid);
                }
            }
        }
        return Optional.of(kids.getFirst());
    }

    /**
     * The kit that is actually used when somebody picks {@code kitId}: a folder resolves to its
     * default child, anything else is itself. Every "fight with this kit" path (queue, duel, party,
     * hotbar, commands) goes through this so a folder is never handed to the match engine.
     */
    public Optional<KitDefinition> playable(String kitId) {
        return get(kitId).map(kit -> defaultChild(kit.name()).orElse(kit));
    }

    /** The kit behind a parent tile (default child when it is a folder, otherwise the kit). */
    public KitDefinition tile(KitDefinition kit) {
        return kit == null ? null : defaultChild(kit.name()).orElse(kit);
    }

    /** {@link #playable(String)} as an id — the input id when it is not a folder. */
    public String playableId(String kitId) {
        return playable(kitId).map(KitDefinition::name).orElse(kitId);
    }

    /** Points a folder's own tile at one of its DIRECT children (Admin's「デフォルト」choice). */
    public boolean setDefaultChild(String parentId, String childId) {
        KitDefinition parent = get(parentId).orElse(null);
        KitDefinition child = get(childId).orElse(null);
        if (parent == null || parent.isChild() || child == null
                || !parent.name().equalsIgnoreCase(child.parent())) {
            return false;
        }
        save(parent.toBuilder().defaultChild(child.name()).build());
        return true;
    }

    /**
     * Turn an existing normal kit into a folder without destroying its kit settings or everyone's
     * layouts/ranked stats. The kit's own contents and rules are copied to {@code <kit>-default}
     * as the first child. Its old id stays as the menu button, and its old data stays as a backup
     * so removing the last child can turn it back into an ordinary kit. Idempotent.
     *
     * <p>The DB copies happen BEFORE the YAML change. If they fail, the parent remains a playable
     * ordinary kit and no player is sent into an empty folder. Repeating the operation copies only
     * missing rows, so partial copies cannot overwrite new scores or layouts.</p>
     */
    public Optional<KitDefinition> ensureFolder(String parentId) {
        KitDefinition parent = get(parentId).orElse(null);
        if (parent == null || parent.isChild()) {
            return Optional.empty();
        }
        Optional<KitDefinition> existing = defaultChild(parent.name());
        if (existing.isPresent()) {
            if (!existing.get().name().equalsIgnoreCase(parent.defaultChild())) {
                save(parent.toBuilder().defaultChild(existing.get().name()).build());
            }
            return existing;
        }
        String base = parent.name().toLowerCase(Locale.ROOT) + "-default";
        String id = base;
        for (int index = 2; kits.containsKey(id); index++) {
            id = base + "-" + index;
        }
        // An identical baseline is important: delta-encoded personal layouts copied from the
        // parent can then decode against the child's kit definition without losing any item.
        copyLayouts.accept(parent.name(), id);
        copyRankedStats.accept(parent.name(), id);
        KitDefinition child = parent.toBuilder().name(id)
                .parent(parent.name()).defaultChild(null).crystalFfa(false).build();
        save(child);
        appendToOrder(id);
        save(parent.toBuilder().defaultChild(id).build());
        return Optional.of(child);
    }

    /**
     * File any EXISTING kit under a folder, keeping its id, contents, config, layouts and stats.
     * Null or blank parent lifts a child back out. Folders cannot themselves be children: one
     * level only. On the first move into an ordinary kit, its original items are automatically
     * saved as its default child. A move away repairs the old folder's default pointer.
     */
    public boolean setParent(String kitId, String parentId) {
        KitDefinition child = get(kitId).orElse(null);
        if (child == null) {
            return false;
        }
        String oldParent = child.parent();
        if (parentId == null || parentId.isBlank()) {
            if (oldParent == null) {
                return true;
            }
            save(child.toBuilder().parent(null).build());
            repairDefault(oldParent);
            return true;
        }
        KitDefinition newParent = get(parentId).orElse(null);
        if (newParent == null || newParent.isChild()
                || child.name().equalsIgnoreCase(newParent.name()) || isFolder(child.name())) {
            return false;
        }
        if (newParent.name().equalsIgnoreCase(oldParent)) {
            return true;
        }
        if (ensureFolder(newParent.name()).isEmpty()) {
            return false;
        }
        save(child.toBuilder().parent(newParent.name()).defaultChild(null).build());
        if (oldParent != null) {
            repairDefault(oldParent);
        }
        return true;
    }

    /** Clears an invalid default or advances it to the next child after removal / moving out. */
    private void repairDefault(String parentId) {
        KitDefinition parent = get(parentId).orElse(null);
        if (parent == null) {
            return;
        }
        List<KitDefinition> kids = children(parent.name());
        String next = kids.isEmpty() ? null
                : kids.stream().anyMatch(k -> k.name().equalsIgnoreCase(parent.defaultChild()))
                ? parent.defaultChild() : kids.getFirst().name();
        if (!Objects.equals(parent.defaultChild(), next)) {
            save(parent.toBuilder().defaultChild(next).build());
        }
    }

    /** Applies the admin-defined kit order; unlisted kits follow alphabetically. */
    private List<KitDefinition> sorted(java.util.Collection<KitDefinition> input) {
        List<KitDefinition> out = new ArrayList<>(input);
        out.sort((a, b) -> {
            int ia = sortOrder.indexOf(a.name().toLowerCase(Locale.ROOT));
            int ib = sortOrder.indexOf(b.name().toLowerCase(Locale.ROOT));
            if (ia < 0 && ib < 0) {
                return a.name().compareToIgnoreCase(b.name());
            }
            if (ia < 0) {
                return 1;
            }
            if (ib < 0) {
                return -1;
            }
            return Integer.compare(ia, ib);
        });
        return out;
    }

    /** Moves a kit one step earlier/later in the display order and persists it. */
    public boolean move(String kitId, boolean up) {
        String key = kitId.toLowerCase(Locale.ROOT);
        if (!kits.containsKey(key)) {
            return false;
        }
        // Materialise the full current order so unlisted kits become movable too, then
        // swap with the NEAREST KIT OF THE SAME CATEGORY: Main and Sub keep two independent
        // orders while sharing one persisted kit-order list.
        KitDefinition moving = kits.get(key);
        KitCategory category = moving == null ? KitCategory.MAIN : moving.category();
        List<String> order = new ArrayList<>();
        for (KitDefinition kit : all()) {
            order.add(kit.name().toLowerCase(Locale.ROOT));
        }
        int index = order.indexOf(key);
        if (index < 0) {
            return false;
        }
        int target = up ? index - 1 : index + 1;
        while (target >= 0 && target < order.size()) {
            KitDefinition other = kits.get(order.get(target));
            if (other != null && other.category() == category) {
                break;
            }
            target = up ? target - 1 : target + 1;
        }
        if (target < 0 || target >= order.size()) {
            return false;
        }
        java.util.Collections.swap(order, index, target);
        sortOrder.clear();
        sortOrder.addAll(order);
        yaml().set("kit-order", order);
        saveKits();
        return true;
    }

    /**
     * Puts {@code kitId} at the end of the persisted display order. Sub-menu children are not
     * reordered by hand, so their order is simply the order they were created in — and listing them
     * keeps that order stable across reloads instead of falling back to alphabetical.
     */
    public void appendToOrder(String kitId) {
        String key = kitId == null ? "" : kitId.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || sortOrder.contains(key) || !kits.containsKey(key)) {
            return;
        }
        sortOrder.add(key);
        yaml().set("kit-order", new ArrayList<>(sortOrder));
        saveKits();
    }

    /** Moves a kit between the Main and Sub sections and persists it. */
    public boolean setCategory(String kitId, KitCategory category) {
        return get(kitId).map(kit -> {
            save(kit.toBuilder().category(category).build());
            return true;
        }).orElse(false);
    }

    /**
     * Declares (or undeclares) THE crystal FFA kit. Declaring one kit un-declares every
     * other kit — the flag is exclusive by definition.
     */
    public boolean setCrystalFfa(String kitId, boolean value) {
        return get(kitId).map(kit -> {
            save(kit.toBuilder().crystalFfa(value).build());
            if (value) {
                clearCrystalFfaExcept(kit.name());
            }
            return true;
        }).orElse(false);
    }

    /** Un-flags every crystal FFA kit except {@code keepId} (persists each change). */
    public void clearCrystalFfaExcept(String keepId) {
        String keep = keepId == null ? "" : keepId.toLowerCase(Locale.ROOT);
        for (KitDefinition kit : List.copyOf(kits.values())) {
            if (kit.crystalFfa() && !kit.name().toLowerCase(Locale.ROOT).equals(keep)) {
                save(kit.toBuilder().crystalFfa(false).build());
            }
        }
    }

    /** The declared crystal FFA kit, if the server has one. */
    public Optional<KitDefinition> crystalFfaKit() {
        return kits.values().stream().filter(KitDefinition::crystalFfa).findFirst();
    }

    public void save(KitDefinition kit) {
        kits.put(kit.name().toLowerCase(Locale.ROOT), kit);
        persist(kit);
    }

    public boolean delete(String id) {
        KitDefinition removed = get(id).orElse(null);
        if (removed == null) {
            return false;
        }
        // Capture children BEFORE removing their parent; a missing parent is not a valid folder.
        List<KitDefinition> kids = children(removed.name());
        kits.remove(removed.name().toLowerCase(Locale.ROOT));
        yaml().set("kits." + removed.name(), null);
        sortOrder.remove(removed.name().toLowerCase(Locale.ROOT));
        queueEnabled.remove(removed.name().toLowerCase(Locale.ROOT));
        // Removing a folder promotes all of its children; never silently destroy them.
        for (KitDefinition child : kids) {
            save(child.toBuilder().parent(null).build());
        }
        if (removed.parent() != null) {
            repairDefault(removed.parent());
        }
        yaml().set("kit-order", new ArrayList<>(sortOrder));
        saveKits();
        return true;
    }

    /** Result of {@link #rename(String, String)}. */
    public enum RenameResult {
        OK, NOT_FOUND, TARGET_EXISTS, MIGRATION_FAILED
    }

    /**
     * Renames a kit: the storage key becomes {@code newName} lowercased, the display name
     * takes {@code newName}'s exact casing (KEEP style shows it verbatim), and the kit keeps
     * its position in the admin display order. The old kits.yml section is removed.
     */
    public RenameResult rename(String oldName, String newName) {
        String oldKey = oldName.toLowerCase(Locale.ROOT);
        String newKey = newName.toLowerCase(Locale.ROOT);
        KitDefinition existing = kits.get(oldKey);
        if (existing == null) {
            return RenameResult.NOT_FOUND;
        }
        if (!oldKey.equals(newKey) && kits.containsKey(newKey)) {
            return RenameResult.TARGET_EXISTS;
        }
        if (!oldKey.equals(newKey)) {
            try {
                // Rename must not strand saved personal arrangements or ranked history at the
                // old id. Copy first, then change kits.yml; failure leaves the old kit playable.
                copyLayouts.accept(oldKey, newKey);
                copyRankedStats.accept(oldKey, newKey);
                for (int variant = 1; variant <= CrystalFfaStore.SLOTS; variant++) {
                    copyLayouts.accept(CrystalFfaStore.variantKey(oldKey, variant),
                            CrystalFfaStore.variantKey(newKey, variant));
                }
            } catch (RuntimeException failure) {
                return RenameResult.MIGRATION_FAILED;
            }
        }
        List<KitDefinition> childRefs = children(oldKey);
        KitDefinition renamed = existing.toBuilder()
                .name(newKey)
                .displayName(newName)
                .build();
        kits.remove(oldKey);
        kits.put(newKey, renamed);
        // Preserve queue toggle and display order position under the new key.
        Boolean queueFlag = queueEnabled.remove(oldKey);
        if (queueFlag != null) {
            queueEnabled.put(newKey, queueFlag);
        }
        int orderIndex = sortOrder.indexOf(oldKey);
        if (orderIndex >= 0) {
            sortOrder.set(orderIndex, newKey);
            yaml().set("kit-order", new ArrayList<>(sortOrder));
        }
        // 中キット (inner kits) live INSIDE the kit's own section, so a rename has to carry them
        // across: dropping the old section without this would silently delete every preset.
        ConfigurationSection inner = yaml()
                .getConfigurationSection("kits." + oldKey + ".inner-kits");
        Map<String, Object> innerSnapshot = inner == null ? null : new LinkedHashMap<>(inner.getValues(true));
        yaml().set("kits." + oldKey, null);
        persist(renamed);
        if (innerSnapshot != null && !innerSnapshot.isEmpty()) {
            for (Map.Entry<String, Object> entry : innerSnapshot.entrySet()) {
                yaml().set("kits." + newKey + ".inner-kits." + entry.getKey(), entry.getValue());
            }
            saveKits();
        }
        // 中メニューの親子関係はキット id で持つので、改名したら「子の parent」と
        // 「誰かのデフォルトの子」も新しい id へ置き換える。
        for (KitDefinition child : childRefs) {
            save(child.toBuilder().parent(newKey).build());
        }
        for (KitDefinition kit : List.copyOf(kits.values())) {
            if (oldKey.equalsIgnoreCase(kit.defaultChild() == null ? "" : kit.defaultChild())) {
                save(kit.toBuilder().defaultChild(newKey).build());
            }
        }
        return RenameResult.OK;
    }

    /** Returns the display name of a kit, falling back to the id itself. */
    public String displayName(String kitId) {
        return get(kitId).map(KitDefinition::displayName).orElse(kitId);
    }

    public void setQueueEnabled(String kitId, boolean enabled) {
        queueEnabled.put(kitId.toLowerCase(Locale.ROOT), enabled);
    }

    public boolean isQueueEnabled(String kitId) {
        return queueEnabled.getOrDefault(kitId.toLowerCase(Locale.ROOT), true);
    }

    public void apply(Player player, KitDefinition kit) {
        apply(player, kit, null);
    }

    /**
     * Applies the official kit, optionally overlaying a player-saved layout (slots 0-40).
     * Always uses {@link KitLoadout#give} so loadout indices 36-39 map to helmet/chest/legs/boots
     * via setHelmet/setChestplate/... — never Bukkit raw {@code setItem(36-39)} (boots/legs/chest/helmet).
     */
    public void apply(Player player, KitDefinition kit, ItemStack[] layout) {
        applyResolved(player, kit, KitLoadout.resolve(kit, layout));
    }

    /**
     * Applies an <b>authoritative</b> loadout — a 中キット (inner kit) preset.
     *
     * <p>A preset specifies its contents completely, so this never goes through
     * {@link KitLoadout#resolve}: nothing is filled back from the kit and the player's personal
     * rearrangement is irrelevant. A slot the preset leaves empty stays empty in the fight —
     * that is the point of a preset, whose items differ from the kit's, not just their order.
     * Only {@link KitLoadout#sanitize} runs, to strip editor placeholders and move armour that
     * cannot be worn in the slot it sits in.</p>
     */
    public void applyExact(Player player, KitDefinition kit, ItemStack[] loadout) {
        applyResolved(player, kit, KitLoadout.sanitize(loadout));
    }

    /** Shared tail of both apply paths: hand out {@code resolved}, then set the kit's rules. */
    private void applyResolved(Player player, KitDefinition kit, ItemStack[] resolved) {
        KitLoadout.give(player.getInventory(), resolved);
        applyCustomShield(player);
        // Reflect the kit's max-health on the player's MAX_HEALTH attribute; without this the
        // attribute stays at the vanilla 20 so a >20 HP kit is clamped to 20 and a kit's custom
        // max never takes effect. Set the base value first, then fill to the (possibly raised) max.
        double maxHealth = kit.maxHealth() <= 0.0d ? 20.0d : kit.maxHealth();
        org.bukkit.attribute.AttributeInstance maxAttr =
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxAttr != null) {
            maxAttr.setBaseValue(maxHealth);
        }
        player.setHealth(Math.min(player.getMaxHealth(), maxHealth));
        player.setFoodLevel(20);
        player.setSaturation(0f);
        player.setExhaustion(0f);
        if (kit.totem()) {
            PracticeGuards.enforceTotemCap(player, 14);
        }
        // Default to SURVIVAL so PvP kits behave normally; kits flagged adventure force ADVENTURE
        // (e.g. kits where block interaction should be fully disabled). Real ADVENTURE blocks
        // place/dig CLIENT-side before any packet leaves, so the shulker-box exception is not a
        // listener rule there — it rides on the item NBT (1.20.5+ data components can_break /
        // can_place_on, known pre-1.20.5 as the CanDestroy / CanPlaceOn tags), attached below.
        player.setGameMode(kit.forceAdventure() ? GameMode.ADVENTURE : GameMode.SURVIVAL);
        if (kit.forceAdventure()) {
            com.rumilance.practice.kit.AdventureShulkerCompat.tag(player);
        }
    }

    /** Hidden custom_shield rank holders (OP-assigned model data) — never displayed. */
    private com.rumilance.practice.hiddenrank.HiddenRankService hiddenRanks;

    public void setHiddenRanks(com.rumilance.practice.hiddenrank.HiddenRankService hiddenRanks) {
        this.hiddenRanks = hiddenRanks;
    }

    /**
     * Re-runs the hidden-rank shield pass on a live inventory. The Shield Web UI calls this
     * right after linking an artwork to an online player so the assignment shows immediately,
     * without waiting for the next kit application.
     */
    public void refreshCustomShield(Player player) {
        applyCustomShield(player);
        player.updateInventory();
    }

    /**
     * Strips the Custom Model Data off every shield the player is currently carrying — used
     * when a holder is unassigned (or their artwork is deleted) from the Shield Web UI. The
     * VIP+ loom patterns are untouched; only the exclusive-artwork override comes off.
     */
    public void clearCustomShield(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.SHIELD) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasCustomModelData()) {
                    meta.setCustomModelData(null);
                    item.setItemMeta(meta);
                }
            }
        }
        player.updateInventory();
    }

    /**
     * Gives the hidden-rank custom shield: every shield the kit handed out receives the
     * operator-assigned Custom Model Data, which the resource pack renders as the holder's
     * high-resolution artwork. No-op for players without the hidden rank / model data.
     */
    private void applyCustomShield(Player player) {
        if (hiddenRanks == null || !hiddenRanks.hasCustomShield(player.getUniqueId())) {
            return;
        }
        int cmd = hiddenRanks.shieldModelData(player.getUniqueId());
        if (cmd <= 0) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.SHIELD) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(cmd);
                    if (meta instanceof org.bukkit.inventory.meta.BannerMeta bannerMeta) {
                        // The exclusive artwork is the shield's only look: drop any loom
                        // patterns a VIP+ editor session may have left on the layout shield.
                        bannerMeta.setPatterns(java.util.List.of());
                    }
                    item.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Creates a kit from the player's live inventory. The storage key is the lowercased id,
     * but the ORIGINAL casing of {@code id} is preserved as the display name so the
     * {@code gui.kit-name-case: KEEP} style can render it exactly as typed.
     */
    public KitDefinition createFromPlayer(Player player, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        List<KitItemEntry> items = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            // Full NBT snapshot: enchantments, potion effects, custom names, attributes...
            items.add(new KitItemEntry(i, stack.getType().name(), stack.getAmount(), null,
                    com.rumilance.practice.util.ItemSerializer.singleToBase64(stack)));
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            items.add(new KitItemEntry(OFFHAND_SLOT, offhand.getType().name(), offhand.getAmount(), null,
                    com.rumilance.practice.util.ItemSerializer.singleToBase64(offhand)));
        }
        Map<String, String> armor = new LinkedHashMap<>();
        putArmor(armor, "helmet", player.getInventory().getHelmet());
        putArmor(armor, "chestplate", player.getInventory().getChestplate());
        putArmor(armor, "leggings", player.getInventory().getLeggings());
        putArmor(armor, "boots", player.getInventory().getBoots());

        ItemStack hand = player.getInventory().getItemInMainHand();
        String icon = hand.getType().isAir() ? "DIAMOND_SWORD" : hand.getType().name();
        // Storage key is lowercase; display name keeps the admin's original casing.
        // Re-saving a layout must NOT reset the kit's rules (totem, pearl, block place/break,
        // bed explosion, ...): only the contents, icon and display name come from the inventory.
        KitDefinition.Builder builder = kits.get(key) != null
                ? kits.get(key).toBuilder()
                : KitDefinition.builder(key);
        KitDefinition kit = builder
                .displayName(id)
                .icon(icon)
                .items(items)
                .armor(armor)
                .build();
        save(kit);
        return kit;
    }

    /**
     * Creates a child kit — one entry of a folder's 中メニュー — from a 41-slot layout
     * ({@link KitLoadout#SIZE}; {@code null} / all-air creates an empty child).
     *
     * <p>A child is a normal kit, so it gets its own contents, icon, name, personal layouts and
     * stats. What it takes from the folder is the rule set — HP, knockback, block rules, arenas,
     * start effects, timeouts — because a sub-menu exists to offer several loadouts of the SAME
     * fight, and because that is exactly what the preset carry-over produced. The first child of a
     * folder becomes its {@code default-child} automatically.</p>
     *
     * @return the created kit, or {@code null} when the id is taken/blank or the parent is missing
     */
    public KitDefinition createChild(String childId, String parentId, ItemStack[] layout,
                                     String icon, String displayName) {
        String key = childId == null ? "" : childId.trim().toLowerCase(Locale.ROOT);
        String parentKey = parentId == null ? "" : parentId.trim().toLowerCase(Locale.ROOT);
        KitDefinition parent = kits.get(parentKey);
        if (!key.matches("[a-z0-9_-]+") || parent == null || parent.isChild() || kits.containsKey(key)
                || ensureFolder(parentKey).isEmpty() || kits.containsKey(key)) {
            return null;
        }
        // Each child has independent rules. New children start with a copy of the default
        // child's current rules, so settings changed since the folder was made are respected.
        KitDefinition source = defaultChild(parentKey).orElse(parent);
        ItemStack[] contents = layout == null ? new ItemStack[KitLoadout.SIZE] : layout;
        String childIcon = icon == null || icon.isBlank() ? source.icon() : icon;
        KitDefinition child = KitDefinition.builder(key)
                .displayName(displayName == null || displayName.isBlank()
                        ? com.rumilance.practice.util.KitNames.pretty(key) : displayName.trim())
                .icon(childIcon)
                .category(source.category())
                .items(InnerKitService.entriesFromLayout(contents))
                // 41スロットのレイアウトは防具もアイテム枠として持つので armor マップは使わない。
                .armor(Map.of())
                .ranked(source.ranked())
                .ffaEnabled(source.ffaEnabled())
                .maxHealth(source.maxHealth())
                .naturalHealthRegen(source.naturalHealthRegen())
                .knockbackMultiplier(source.knockbackMultiplier())
                .enabled(source.enabled())
                .autoFood(source.autoFood())
                .swordShieldBreak(source.swordShieldBreak())
                .blockPlace(source.blockPlace())
                .blockBreak(source.blockBreak())
                .breakPlayerPlacedOnly(source.breakPlayerPlacedOnly())
                .canBreak(source.canBreak())
                .pearl(source.pearl())
                .totem(source.totem())
                .forceAdventure(source.forceAdventure())
                .timeoutSeconds(source.timeoutSeconds())
                .arenas(source.arenas())
                .partyArenas(source.partyArenas())
                .startCommands(source.startCommands())
                .startEffects(source.startEffects())
                .presetEnabled(source.presetEnabled())
                .bedExplosion(source.bedExplosion())
                // Crystal FFA is exclusive; do not duplicate its flag on a new child.
                .crystalFfa(false)
                .heartIndicator(source.heartIndicator())
                .parent(parentKey)
                .build();
        save(child);
        appendToOrder(key);
        // 最初の子は自動的に既定になる(既定未設定のフォルダもここで直る)。
        if (get(parentKey).map(KitDefinition::defaultChild).orElse(null) == null) {
            setDefaultChild(parentKey, key);
        }
        return child;
    }

    /**
     * Admin-only caller saves the SHARED 41-slot contents of one normal child/kit, not the
     * editor player's personal layout. Leave every rule and every other kit unchanged.
     */
    public boolean setOfficialLoadout(String kitId, ItemStack[] layout) {
        KitDefinition kit = get(kitId).orElse(null);
        if (kit == null || layout == null || isFolder(kit.name())) {
            return false;
        }
        ItemStack[] sanitized = KitLoadout.sanitize(layout);
        save(kit.toBuilder().items(InnerKitService.entriesFromLayout(sanitized))
                .armor(Map.of()).build());
        return true;
    }

    private void persist(KitDefinition kit) {
        String path = "kits." + kit.name();
        FileConfiguration yaml = yaml();
        yaml.set(path + ".display-name", kit.displayName());
        yaml.set(path + ".icon", kit.icon());
        yaml.set(path + ".category", kit.category().name());
        yaml.set(path + ".ranked", kit.ranked());
        yaml.set(path + ".ffa-enabled", kit.ffaEnabled());
        yaml.set(path + ".max-health", kit.maxHealth());
        yaml.set(path + ".natural-health-regen", kit.naturalHealthRegen());
        yaml.set(path + ".knockback-multiplier", kit.knockbackMultiplier());
        yaml.set(path + ".enabled", kit.enabled());
        yaml.set(path + ".auto-food", kit.autoFood());
        yaml.set(path + ".sword-shield-break", kit.swordShieldBreak());
        yaml.set(path + ".block-place", kit.blockPlace());
        yaml.set(path + ".block-break", kit.blockBreak());
        yaml.set(path + ".break-player-placed-only", kit.breakPlayerPlacedOnly());
        yaml.set(path + ".pearl", kit.pearl());
        yaml.set(path + ".totem", kit.totem());
        yaml.set(path + ".adventure", kit.forceAdventure());
        yaml.set(path + ".timeout-seconds", kit.timeoutSeconds());
        yaml.set(path + ".arenas", kit.arenas());
        yaml.set(path + ".party-arenas", kit.partyArenas());
        yaml.set(path + ".preset-enabled", kit.presetEnabled());
        yaml.set(path + ".bed-explosion", kit.bedExplosion());
        yaml.set(path + ".crystal-ffa", kit.crystalFfa());
        yaml.set(path + ".heart-indicator", kit.heartIndicator());
        // 中メニューの親子関係（未設定のときはキーごと消す）。
        yaml.set(path + ".parent", kit.parent());
        yaml.set(path + ".default-child", kit.defaultChild());
        yaml.set(path + ".can-break", kit.canBreak());
        yaml.set(path + ".start-commands", kit.startCommands());
        List<Map<String, Object>> startEffectMaps = new ArrayList<>();
        for (KitStartEffect effect : kit.startEffects()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", effect.potionEffectKey().toUpperCase(Locale.ROOT));
            map.put("amplifier", effect.amplifier());
            if (effect.durationTicks() != KitStartEffect.DURATION_FROM_POTION_TABLE) {
                map.put("duration-ticks", effect.durationTicks());
            }
            startEffectMaps.add(map);
        }
        yaml.set(path + ".start-effects", startEffectMaps);
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (KitItemEntry entry : kit.items()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", entry.slot());
            map.put("material", entry.material());
            map.put("amount", entry.amount());
            if (entry.hasSerializedItem()) {
                map.put("data", entry.itemDataBase64());
            }
            if (!entry.enchantments().isEmpty()) {
                map.put("enchantments", new LinkedHashMap<>(entry.enchantments()));
            }
            if (entry.unbreakable()) {
                map.put("unbreakable", true);
            }
            itemMaps.add(map);
        }
        yaml.set(path + ".items", itemMaps);
        // A full 41-slot kit stores armor in `items`. When replacing an older `armor` map,
        // clear stale pieces first — otherwise deleted armor would silently reappear on reload.
        yaml.set(path + ".armor", null);
        for (Map.Entry<String, String> armor : kit.armor().entrySet()) {
            yaml.set(path + ".armor." + armor.getKey(), armor.getValue());
        }
        saveKits();
    }

    /**
     * Parses {@code start-effects} entries supporting either
     * {@code {type, amplifier}} (0-based) or {@code {effect, level}} (1-based).
     */
    private static List<KitStartEffect> parseStartEffects(List<Map<?, ?>> maps) {
        List<KitStartEffect> out = new ArrayList<>();
        if (maps == null) {
            return out;
        }
        for (Map<?, ?> map : maps) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            Object typeObj = map.containsKey("type") ? map.get("type") : map.get("effect");
            if (typeObj == null) {
                continue;
            }
            String key = String.valueOf(typeObj).trim();
            if (key.isEmpty()) {
                continue;
            }
            int amplifier = 0;
            Object ampObj = map.get("amplifier");
            Object levelObj = map.get("level");
            if (ampObj instanceof Number number) {
                amplifier = Math.max(0, number.intValue());
            } else if (levelObj instanceof Number number) {
                amplifier = Math.max(0, number.intValue() - 1);
            }
            int durationTicks = KitStartEffect.DURATION_FROM_POTION_TABLE;
            Object durObj = map.get("duration-ticks");
            if (durObj instanceof Number number) {
                durationTicks = Math.max(0, number.intValue());
            }
            try {
                out.add(new KitStartEffect(key, amplifier, durationTicks));
            } catch (IllegalArgumentException ignored) {
                // skip blank / invalid
            }
        }
        return out;
    }

    /** Virtual slot index used for the off-hand item inside {@link KitItemEntry}. */
    public static final int OFFHAND_SLOT = 40;
    /** Prefix marking an armor value that stores a full serialized item, not just a material. */
    private static final String ARMOR_DATA_PREFIX = "data:";

    private static void putArmor(Map<String, String> armor, String key, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        // Keep enchantments/trims by serializing the whole stack; plain pieces stay readable.
        if (stack.hasItemMeta()) {
            String encoded = com.rumilance.practice.util.ItemSerializer.singleToBase64(stack);
            if (encoded != null) {
                armor.put(key, ARMOR_DATA_PREFIX + encoded);
                return;
            }
        }
        armor.put(key, stack.getType().name());
    }

}
