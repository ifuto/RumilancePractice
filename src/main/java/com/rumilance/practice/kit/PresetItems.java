package com.rumilance.practice.kit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.util.ItemSerializer;
import com.rumilance.practice.util.PotionRules;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML-backed candidate pools for official-kit preset editors.
 *
 * <p>Each category maps a GUI slot (0..179) to either a legacy material / potion-effect key, or a
 * full NBT snapshot as {@code data:&lt;base64&gt;}. Japanese and English YAML keys are both
 * supported ({@link CategoryKeys#presetLoadKeys}).</p>
 */
public final class PresetItems {

    public static final List<String> CATEGORIES = CategoryKeys.PRESET;
    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_PAGES = 5;
    public static final int MAX_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final String DATA_PREFIX = "data:";

    /** YAML root for per-kit preset overrides: {@code kits.<kitId>.categories.<Cat>...}. */
    public static final String KITS_ROOT = "kits";
    /** One-shot migration marker: set once the legacy global pool has been copied per kit. */
    public static final String MIGRATION_KEY = "_per-kit-migrated";
    /** One-shot repair marker: set once per-kit overrides were re-synced from the global pool. */
    public static final String GLOBAL_RESYNC_KEY = "_global-resynced";

    private final ConfigService configService;
    private final Map<String, Map<Integer, String>> items = new ConcurrentHashMap<>();
    /** Disk YAML key used per canonical category (e.g. {@code 防具} for {@code Armor}). */
    private final Map<String, String> yamlKeyByCategory = new ConcurrentHashMap<>();
    /** Per-kit overrides: {@code kitId -> canonicalCategory -> slot -> value}. */
    private final Map<String, Map<String, Map<Integer, String>>> kitItems = new ConcurrentHashMap<>();
    /** Supplies the current kit ids so the one-time global->per-kit copy knows all targets. */
    private volatile java.util.function.Supplier<java.util.Collection<String>> kitIdProvider;

    public PresetItems(ConfigService configService) {
        this.configService = configService;
        reload();
    }

    /**
     * Wires the kit catalogue and immediately performs the one-time migration: if the YAML
     * has a global {@code categories} pool but no {@code kits.<id>.categories} and no migration
     * marker, the global pool is duplicated into <strong>every</strong> kit so each kit gets its
     * own independently-editable preset (previously every kit silently shared the global pool).
     */
    public void setKitIdProvider(java.util.function.Supplier<java.util.Collection<String>> kitIdProvider) {
        this.kitIdProvider = kitIdProvider;
        migrateGlobalToPerKit();
    }

    private synchronized void migrateGlobalToPerKit() {
        FileConfiguration yaml = configService.presetItems();
        if (yaml.getBoolean(MIGRATION_KEY, false)) {
            return;
        }
        ConfigurationSection kits = yaml.getConfigurationSection(KITS_ROOT);
        boolean anyKitSection = kits != null && !kits.getKeys(false).isEmpty();
        if (anyKitSection) {
            yaml.set(MIGRATION_KEY, true);
            configService.save(ConfigService.PRESET_ITEMS);
            return;
        }
        if (kitIdProvider == null) {
            return;
        }
        java.util.Collection<String> kitIds = kitIdProvider.get();
        if (kitIds == null || kitIds.isEmpty()) {
            return;
        }
        Map<String, Map<Integer, String>> global = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            Map<Integer, String> map = slots(category);
            if (!map.isEmpty()) {
                global.put(category, new TreeMap<>(map));
            }
        }
        if (global.isEmpty()) {
            yaml.set(MIGRATION_KEY, true);
            configService.save(ConfigService.PRESET_ITEMS);
            return;
        }
        for (String kitId : kitIds) {
            if (kitId == null || kitId.isBlank()) {
                continue;
            }
            String key = kitId.toLowerCase(java.util.Locale.ROOT);
            for (Map.Entry<String, Map<Integer, String>> e : global.entrySet()) {
                String canonical = e.getKey();
                String yamlCat = yamlKeyByCategory.getOrDefault(canonical, canonical);
                String base = KITS_ROOT + "." + key + ".categories." + yamlCat + ".slots";
                for (Map.Entry<Integer, String> slot : e.getValue().entrySet()) {
                    yaml.set(base + "." + slot.getKey(), slot.getValue());
                }
                kitItems.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                        .put(canonical, new TreeMap<>(e.getValue()));
            }
        }
        yaml.set(MIGRATION_KEY, true);
        configService.save(ConfigService.PRESET_ITEMS);
    }

    public void reload() {
        items.clear();
        yamlKeyByCategory.clear();
        kitItems.clear();
        FileConfiguration yaml = configService.presetItems();
        ConfigurationSection root = yaml.getConfigurationSection("categories");
        if (root != null) {
            for (String yamlKey : root.getKeys(false)) {
                Map<Integer, String> loaded = load(yaml, yamlKey);
                if (loaded.isEmpty()) {
                    continue;
                }
                String canonical = CategoryKeys.canonicalPreset(yamlKey);
                items.merge(canonical, loaded, (left, right) -> right.size() >= left.size() ? right : left);
                yamlKeyByCategory.putIfAbsent(canonical, yamlKey);
            }
        }
        // Old files merged weapons and armor into one combined key (武器/防具 / Weapons/Armor),
        // which canonicalPreset leaves as an orphan pool no preset tab ever reads — so the
        // Armor tab renders empty. Pull those entries into Armor/Gear where they belong.
        if (normalizeCombinedPools(items)) {
            for (String yamlKey : root == null ? List.<String>of() : root.getKeys(false)) {
                if (!CategoryKeys.PRESET.contains(CategoryKeys.canonicalPreset(yamlKey))
                        && !CategoryKeys.PRESET.contains(yamlKey)) {
                    yaml.set("categories." + yamlKey, null);
                }
            }
            yamlKeyByCategory.keySet().removeIf(key -> !CategoryKeys.PRESET.contains(key));
            configService.save(ConfigService.PRESET_ITEMS);
        }
        for (String category : CATEGORIES) {
            if (!items.containsKey(category) || items.get(category).isEmpty()) {
                for (String key : CategoryKeys.presetLoadKeys(category)) {
                    Map<Integer, String> loaded = load(yaml, key);
                    if (!loaded.isEmpty()) {
                        items.put(category, loaded);
                        yamlKeyByCategory.putIfAbsent(category, key);
                        break;
                    }
                }
            }
            items.putIfAbsent(category, Map.of());
        }
        // An empty Armor tab is essentially always a migration/config artifact — the armor
        // candidate pool should never legitimately be empty, and it is the reported "armor not
        // showing" bug. Seed the default armor list whenever the pool ends up empty, so the
        // Armor category always has content. (Admins remove individual items, not the category.)
        if (items.getOrDefault("Armor", Map.of()).isEmpty()) {
            Map<Integer, String> defaults = new TreeMap<>();
            for (int i = 0; i < DEFAULT_ARMOR_MATERIALS.length; i++) {
                defaults.put(i, DEFAULT_ARMOR_MATERIALS[i]);
            }
            items.put("Armor", defaults);
            yamlKeyByCategory.putIfAbsent("Armor", "Armor");
        }
        reloadKitOverrides(yaml);
        resyncKitOverridesFromGlobal(yaml);
    }

    /**
     * One-shot repair (marker: {@code _global-resynced}): per-kit overrides are seeded once by
     * the per-kit migration and the preset admin GUI only ever edits the global pool, so any
     * divergence is stale migration-era data. Re-copy every non-empty global category over the
     * existing per-kit overrides so what the admin sees is exactly what the ekit palette shows.
     * Kits overriding a category the global pool left empty keep their contents.
     */
    private void resyncKitOverridesFromGlobal(FileConfiguration yaml) {
        if (yaml.getBoolean(GLOBAL_RESYNC_KEY, false) || kitItems.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, Map<Integer, String>>> kitEntry : kitItems.entrySet()) {
            for (Map.Entry<String, Map<Integer, String>> catEntry : kitEntry.getValue().entrySet()) {
                Map<Integer, String> globalMap = items.get(catEntry.getKey());
                if (globalMap == null || globalMap.isEmpty()) {
                    continue;
                }
                catEntry.setValue(new TreeMap<>(globalMap));
                String yamlCat = findKitYamlKey(yaml, kitEntry.getKey(), catEntry.getKey());
                yaml.set(KITS_ROOT + "." + kitEntry.getKey() + ".categories." + yamlCat + ".slots",
                        serializeSlots(globalMap));
            }
        }
        // Persist the marker (and any resynced sections) so this repair runs exactly once.
        yaml.set(GLOBAL_RESYNC_KEY, true);
        configService.save(ConfigService.PRESET_ITEMS);
    }

    private static Map<String, Object> serializeSlots(Map<Integer, String> map) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : new TreeMap<>(map).entrySet()) {
            serialized.put(String.valueOf(e.getKey()), e.getValue());
        }
        return serialized;
    }

    /** Same armor list as the bundled default preset-items.yml. */
    private static final String[] DEFAULT_ARMOR_MATERIALS = {
            "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
            "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
            "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS"
    };

    /**
     * Loads per-kit preset overrides from {@code kits.<kitId>.categories...}. A kit override
     * replaces the global pool for that category <em>for that kit only</em>; categories the kit
     * does not override fall through to the global pool.
     */
    private void reloadKitOverrides(FileConfiguration yaml) {
        ConfigurationSection kits = yaml.getConfigurationSection(KITS_ROOT);
        if (kits == null) {
            return;
        }
        for (String kitId : kits.getKeys(false)) {
            ConfigurationSection categories = yaml.getConfigurationSection(KITS_ROOT + "." + kitId + ".categories");
            if (categories == null) {
                continue;
            }
            Map<String, Map<Integer, String>> byCategory = new ConcurrentHashMap<>();
            for (String yamlKey : categories.getKeys(false)) {
                Map<Integer, String> loaded = loadUnder(yaml, KITS_ROOT + "." + kitId + ".categories", yamlKey);
                if (!loaded.isEmpty()) {
                    byCategory.put(CategoryKeys.canonicalPreset(yamlKey), loaded);
                }
            }
            // Same combined-key normalization as the global pool (see reload()).
            if (normalizeCombinedPools(byCategory)) {
                for (String yamlKey : categories.getKeys(false)) {
                    if (!CategoryKeys.PRESET.contains(CategoryKeys.canonicalPreset(yamlKey))
                            && !CategoryKeys.PRESET.contains(yamlKey)) {
                        yaml.set(KITS_ROOT + "." + kitId + ".categories." + yamlKey, null);
                    }
                }
                configService.save(ConfigService.PRESET_ITEMS);
            }
            if (!byCategory.isEmpty()) {
                kitItems.put(kitId.toLowerCase(java.util.Locale.ROOT), byCategory);
            }
        }
    }

    /**
     * Repairs pools loaded from files that predate the Armor/Gear split: entries sitting in a
     * non-canonical (orphaned) pool — typically the old combined {@code 武器/防具} /
     * {@code Weapons/Armor} key — are merged into {@code Gear}, with armor pieces split off into
     * {@code Armor} when the Armor pool is empty. Returns true when the map was modified.
     */
    static boolean normalizeCombinedPools(Map<String, Map<Integer, String>> pools) {
        boolean changed = false;
        java.util.List<String> orphans = new java.util.ArrayList<>();
        for (String key : pools.keySet()) {
            if (!CategoryKeys.PRESET.contains(key)) {
                orphans.add(key);
            }
        }
        if (orphans.isEmpty()) {
            return false;
        }
        Map<Integer, String> armor = pools.computeIfAbsent("Armor", c -> new java.util.TreeMap<>());
        boolean armorEmpty = armor.isEmpty();
        Map<Integer, String> gear = pools.computeIfAbsent("Gear", c -> new java.util.TreeMap<>());
        for (String orphan : orphans) {
            Map<Integer, String> map = pools.remove(orphan);
            changed = true;
            if (map == null) {
                continue;
            }
            for (Map.Entry<Integer, String> e : map.entrySet()) {
                if (armorEmpty && isArmorEntry(e.getValue())) {
                    armor.putIfAbsent(e.getKey(), e.getValue());
                } else {
                    gear.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        return changed;
    }

    /** True when the entry resolves to an armor piece (helmet/chestplate/leggings/boots). */
    static boolean isArmorEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        if (entry.startsWith(DATA_PREFIX)) {
            ItemStack decoded = ItemSerializer.singleFromBase64(entry.substring(DATA_PREFIX.length()));
            return decoded != null && isArmorMaterial(decoded.getType());
        }
        return isArmorMaterial(Material.matchMaterial(entry));
    }

    static boolean isArmorMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private Map<Integer, String> load(FileConfiguration yaml, String category) {
        return loadUnder(yaml, "categories", category);
    }

    private Map<Integer, String> loadUnder(FileConfiguration yaml, String rootPath, String category) {
        Map<Integer, String> map = new TreeMap<>();
        String base = rootPath + "." + category;
        if (yaml.isList(base)) {
            List<String> list = yaml.getStringList(base);
            for (int i = 0; i < list.size() && i < MAX_SLOTS; i++) {
                if (list.get(i) != null && !list.get(i).isBlank()) {
                    map.put(i, list.get(i));
                }
            }
            return map;
        }
        ConfigurationSection slots = yaml.getConfigurationSection(base + ".slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    String value = slots.getString(key);
                    if (slot >= 0 && slot < MAX_SLOTS && value != null && !value.isBlank()) {
                        map.put(slot, value);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed keys
                }
            }
        }
        return map;
    }

    public Map<Integer, String> slots(String category) {
        return new TreeMap<>(items.getOrDefault(CategoryKeys.canonicalPreset(category), Map.of()));
    }

    /**
     * Slot map for {@code category} when editing {@code kitId}: the kit's own override if the
     * kit defines that category, otherwise the global pool.
     */
    public Map<Integer, String> slots(String kitId, String category) {
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<String, Map<Integer, String>> byCategory = kitOverride(kitId);
        if (byCategory != null) {
            Map<Integer, String> kitMap = byCategory.get(canonical);
            if (kitMap != null && !kitMap.isEmpty()) {
                return new TreeMap<>(kitMap);
            }
        }
        return slots(category);
    }

    public List<String> items(String category) {
        return List.copyOf(slots(category).values());
    }

    /** Per-kit variant of {@link #items(String)} (falls back to the global pool). */
    public List<String> items(String kitId, String category) {
        return List.copyOf(slots(kitId, category).values());
    }

    public String entryAt(String category, int slot) {
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<Integer, String> map = items.get(canonical);
        return map == null ? null : map.get(slot);
    }

    /** Per-kit variant of {@link #entryAt(String, int)} (falls back to the global pool). */
    public String entryAt(String kitId, String category, int slot) {
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<String, Map<Integer, String>> byCategory = kitOverride(kitId);
        if (byCategory != null) {
            Map<Integer, String> kitMap = byCategory.get(canonical);
            if (kitMap != null && kitMap.containsKey(slot)) {
                return kitMap.get(slot);
            }
        }
        return entryAt(category, slot);
    }

    /** True when {@code kitId} defines at least one preset category override. */
    public boolean hasKitOverride(String kitId) {
        return kitOverride(kitId) != null;
    }

    private Map<String, Map<Integer, String>> kitOverride(String kitId) {
        if (kitId == null) {
            return null;
        }
        return kitItems.get(kitId.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isPotionCategory(String category) {
        return CategoryKeys.isPresetPotion(category);
    }

    public void setSlot(String category, int slot, String value) {
        if (slot < 0 || slot >= MAX_SLOTS || value == null || value.isBlank()) {
            return;
        }
        String canonical = CategoryKeys.canonicalPreset(category);
        items.computeIfAbsent(canonical, c -> new TreeMap<>()).put(slot, value);
        persist(canonical);
    }

    public void setSlotItem(String category, int slot, ItemStack item) {
        String encoded = encodeItem(item);
        if (encoded == null) {
            return;
        }
        setSlot(category, slot, encoded);
    }

    public void replaceFromInventory(String category, ItemStack[] contents) {
        Map<Integer, String> map = new TreeMap<>();
        if (contents != null) {
            int limit = Math.min(MAX_SLOTS, contents.length);
            for (int i = 0; i < limit; i++) {
                String encoded = encodeItem(contents[i]);
                if (encoded != null) {
                    map.put(i, encoded);
                }
            }
        }
        String canonical = CategoryKeys.canonicalPreset(category);
        items.put(canonical, map);
        persist(canonical);
    }

    public void replacePageFromInventory(String category, int page, ItemStack[] contents) {
        String canonical = CategoryKeys.canonicalPreset(category);
        int p = Math.max(0, Math.min(MAX_PAGES - 1, page));
        int base = p * SLOTS_PER_PAGE;
        Map<Integer, String> map = items.computeIfAbsent(canonical, c -> new TreeMap<>());
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            map.remove(base + i);
        }
        if (contents != null) {
            int limit = Math.min(SLOTS_PER_PAGE, contents.length);
            for (int i = 0; i < limit; i++) {
                String encoded = encodeItem(contents[i]);
                if (encoded != null) {
                    map.put(base + i, encoded);
                }
            }
        }
        persist(canonical);
        propagatePageToKitOverrides(canonical, base);
    }

    /**
     * The preset admin GUI edits the global pool, but the ekit palette reads per-kit overrides
     * first — without mirroring, an admin save is never reflected in the kit editors (every kit
     * received a one-time copy at migration time and would keep showing that stale copy). Kits
     * that do not override the category fall through to the global pool on their own.
     */
    private void propagatePageToKitOverrides(String canonical, int base) {
        if (kitItems.isEmpty()) {
            return;
        }
        Map<Integer, String> globalMap = items.getOrDefault(canonical, Map.of());
        FileConfiguration yaml = configService.presetItems();
        boolean changed = false;
        for (Map.Entry<String, Map<String, Map<Integer, String>>> kitEntry : kitItems.entrySet()) {
            Map<Integer, String> kitMap = kitEntry.getValue().get(canonical);
            if (kitMap == null) {
                continue;
            }
            for (int i = 0; i < SLOTS_PER_PAGE; i++) {
                kitMap.remove(base + i);
            }
            for (Map.Entry<Integer, String> e : globalMap.entrySet()) {
                if (e.getKey() >= base && e.getKey() < base + SLOTS_PER_PAGE) {
                    kitMap.put(e.getKey(), e.getValue());
                }
            }
            String yamlCat = findKitYamlKey(yaml, kitEntry.getKey(), canonical);
            String slotBase = KITS_ROOT + "." + kitEntry.getKey() + ".categories." + yamlCat + ".slots";
            for (int i = 0; i < SLOTS_PER_PAGE; i++) {
                yaml.set(slotBase + "." + (base + i), null);
            }
            for (Map.Entry<Integer, String> e : kitMap.entrySet()) {
                if (e.getKey() >= base && e.getKey() < base + SLOTS_PER_PAGE) {
                    yaml.set(slotBase + "." + e.getKey(), e.getValue());
                }
            }
            changed = true;
        }
        if (changed) {
            configService.save(ConfigService.PRESET_ITEMS);
        }
    }

    /** The YAML key a kit's section actually uses for {@code canonical} (Japanese keys allowed). */
    private static String findKitYamlKey(FileConfiguration yaml, String kitKey, String canonical) {
        ConfigurationSection categories = yaml.getConfigurationSection(KITS_ROOT + "." + kitKey + ".categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                if (CategoryKeys.canonicalPreset(key).equals(canonical)) {
                    return key;
                }
            }
        }
        return canonical;
    }

    public void clearSlot(String category, int slot) {
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<Integer, String> map = items.get(canonical);
        if (map != null && map.remove(slot) != null) {
            persist(canonical);
        }
    }

    public int addAtFirstFree(String category, String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<Integer, String> map = items.computeIfAbsent(canonical, c -> new TreeMap<>());
        int slot = 0;
        while (slot < MAX_SLOTS && map.containsKey(slot)) {
            slot++;
        }
        if (slot >= MAX_SLOTS) {
            return -1;
        }
        map.put(slot, value);
        persist(canonical);
        return slot;
    }

    public int addItemAtFirstFree(String category, ItemStack item) {
        String encoded = encodeItem(item);
        if (encoded == null) {
            return -1;
        }
        return addAtFirstFree(category, encoded);
    }

    public boolean removeValue(String category, String value) {
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<Integer, String> map = items.get(canonical);
        if (map == null || value == null) {
            return false;
        }
        Integer target = null;
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            if (value.equalsIgnoreCase(e.getValue())) {
                target = e.getKey();
                break;
            }
        }
        if (target != null) {
            map.remove(target);
            persist(canonical);
            return true;
        }
        return false;
    }

    public ItemStack displayItem(String category, String entry) {
        if (entry == null || entry.isBlank()) {
            return new ItemStack(Material.STONE);
        }
        if (entry.startsWith(DATA_PREFIX)) {
            ItemStack decoded = ItemSerializer.singleFromBase64(entry.substring(DATA_PREFIX.length()));
            return decoded == null ? new ItemStack(Material.STONE) : decoded;
        }
        if (isPotionCategory(category)) {
            return PotionRules.buildPotion(entry, 1, false, "splash");
        }
        Material material = Material.matchMaterial(entry);
        return new ItemStack(material == null ? Material.STONE : material);
    }

    public static String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemStack one = item.clone();
        one.setAmount(Math.max(1, Math.min(64, one.getAmount())));
        String base64 = ItemSerializer.singleToBase64(one);
        return base64 == null ? null : DATA_PREFIX + base64;
    }

    private void persist(String category) {
        String canonical = CategoryKeys.canonicalPreset(category);
        Map<Integer, String> map = items.getOrDefault(canonical, Map.of());
        if (map.isEmpty()) {
            FileConfiguration existing = configService.presetItems();
            for (String key : CategoryKeys.presetLoadKeys(canonical)) {
                if (!load(existing, key).isEmpty()) {
                    return;
                }
            }
        }
        FileConfiguration yaml = configService.presetItems();
        String yamlKey = yamlKeyByCategory.getOrDefault(canonical, canonical);
        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : new TreeMap<>(map).entrySet()) {
            serialized.put(String.valueOf(e.getKey()), e.getValue());
        }
        yaml.set("categories." + yamlKey + ".slots", serialized);
        configService.save(ConfigService.PRESET_ITEMS);
    }
}
