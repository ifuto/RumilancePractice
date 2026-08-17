package com.rumilance.practice.kit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.state.ArenaTerrain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
    private final Map<String, KitDefinition> kits = new ConcurrentHashMap<>();
    private final Map<String, Boolean> queueEnabled = new ConcurrentHashMap<>();
    /** Admin-defined display order (lower index first); kits not listed sort alphabetically after. */
    private final List<String> sortOrder = new java.util.concurrent.CopyOnWriteArrayList<>();

    public KitService(ConfigService configService) {
        this.configService = Objects.requireNonNull(configService);
        reload();
    }

    public void reload() {
        kits.clear();
        FileConfiguration yaml = configService.kits();
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
                    .ranked(section.getBoolean("ranked", true))
                    .ffaEnabled(section.getBoolean("ffa-enabled", true))
                    .maxHealth(section.getDouble("max-health", 20.0d))
                    .naturalHealthRegen(section.getBoolean("natural-health-regen", true))
                    .knockbackMultiplier(section.getDouble("knockback-multiplier", 1.0d))
                    .enabled(section.getBoolean("enabled", true))
                    .arenaTerrain(parseTerrain(section.getString("arena-terrain", "ANY")))
                    .autoFood(section.getBoolean("auto-food", false))
                    .swordShieldBreak(section.getBoolean("sword-shield-break", false))
                    .blockPlace(section.getBoolean("block-place", false))
                    .blockBreak(section.getBoolean("block-break", false))
                    .pearl(section.getBoolean("pearl", true))
                    .totem(section.getBoolean("totem", true))
                    .forceAdventure(section.getBoolean("adventure", false))
                    .timeoutSeconds(section.getInt("timeout-seconds", 0))
                    .arenaName(section.getString("arena", ""))
                    .canBreak(section.getStringList("can-break"));

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
                items.add(new KitItemEntry(slot, material, amount, null, data));
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
            kits.put(id.toLowerCase(Locale.ROOT), builder.build());
            queueEnabled.putIfAbsent(id.toLowerCase(Locale.ROOT), true);
        }
        sortOrder.clear();
        sortOrder.addAll(yaml.getStringList("kit-order").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(kits::containsKey)
                .toList());
    }

    public Optional<KitDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<KitDefinition> all() {
        return sorted(kits.values());
    }

    public List<KitDefinition> enabled() {
        return sorted(kits.values().stream().filter(KitDefinition::enabled).toList());
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
        // Materialise the full current order so unlisted kits become movable too.
        List<String> order = new ArrayList<>();
        for (KitDefinition kit : all()) {
            order.add(kit.name().toLowerCase(Locale.ROOT));
        }
        int index = order.indexOf(key);
        int target = up ? index - 1 : index + 1;
        if (index < 0 || target < 0 || target >= order.size()) {
            return false;
        }
        java.util.Collections.swap(order, index, target);
        sortOrder.clear();
        sortOrder.addAll(order);
        configService.kits().set("kit-order", order);
        configService.save(ConfigService.KITS);
        return true;
    }

    public void save(KitDefinition kit) {
        kits.put(kit.name().toLowerCase(Locale.ROOT), kit);
        persist(kit);
    }

    public boolean delete(String id) {
        KitDefinition removed = kits.remove(id.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        configService.kits().set("kits." + id, null);
        configService.save(ConfigService.KITS);
        return true;
    }

    /** Result of {@link #rename(String, String)}. */
    public enum RenameResult {
        OK, NOT_FOUND, TARGET_EXISTS
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
            configService.kits().set("kit-order", new ArrayList<>(sortOrder));
        }
        configService.kits().set("kits." + oldKey, null);
        persist(renamed);
        return RenameResult.OK;
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
     */
    public void apply(Player player, KitDefinition kit, ItemStack[] layout) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        if (layout != null && layout.length > 0) {
            for (int i = 0; i < Math.min(36, layout.length); i++) {
                ItemStack stack = layout[i];
                if (stack != null && !stack.getType().isAir() && stack.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                    inventory.setItem(i, stack.clone());
                }
            }
            if (layout.length > 36) {
                inventory.setHelmet(clean(layout[36]));
            }
            if (layout.length > 37) {
                inventory.setChestplate(clean(layout[37]));
            }
            if (layout.length > 38) {
                inventory.setLeggings(clean(layout[38]));
            }
            if (layout.length > 39) {
                inventory.setBoots(clean(layout[39]));
            }
            if (layout.length > 40) {
                inventory.setItemInOffHand(clean(layout[40]));
            }
        } else {
            for (KitItemEntry entry : kit.items()) {
                if (entry.slot() == OFFHAND_SLOT) {
                    inventory.setItemInOffHand(resolveItem(entry));
                    continue;
                }
                ItemStack stack = resolveItem(entry);
                if (stack != null) {
                    inventory.setItem(entry.slot(), stack);
                }
            }
            inventory.setHelmet(armorStack(kit.armor().get("helmet")));
            inventory.setChestplate(armorStack(kit.armor().get("chestplate")));
            inventory.setLeggings(armorStack(kit.armor().get("leggings")));
            inventory.setBoots(armorStack(kit.armor().get("boots")));
        }
        player.setHealth(Math.min(player.getMaxHealth(), kit.maxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        // Default to SURVIVAL so PvP kits behave normally; kits flagged adventure force ADVENTURE
        // (e.g. kits where block interaction should be fully disabled).
        player.setGameMode(kit.forceAdventure() ? GameMode.ADVENTURE : GameMode.SURVIVAL);
    }

    private static ItemStack clean(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return null;
        }
        return stack.clone();
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
        KitDefinition kit = KitDefinition.builder(key)
                .displayName(id)
                .icon(icon)
                .items(items)
                .armor(armor)
                .build();
        save(kit);
        return kit;
    }

    private void persist(KitDefinition kit) {
        String path = "kits." + kit.name();
        FileConfiguration yaml = configService.kits();
        yaml.set(path + ".display-name", kit.displayName());
        yaml.set(path + ".icon", kit.icon());
        yaml.set(path + ".ranked", kit.ranked());
        yaml.set(path + ".ffa-enabled", kit.ffaEnabled());
        yaml.set(path + ".max-health", kit.maxHealth());
        yaml.set(path + ".natural-health-regen", kit.naturalHealthRegen());
        yaml.set(path + ".knockback-multiplier", kit.knockbackMultiplier());
        yaml.set(path + ".enabled", kit.enabled());
        yaml.set(path + ".arena-terrain", kit.arenaTerrain().name());
        yaml.set(path + ".auto-food", kit.autoFood());
        yaml.set(path + ".sword-shield-break", kit.swordShieldBreak());
        yaml.set(path + ".block-place", kit.blockPlace());
        yaml.set(path + ".block-break", kit.blockBreak());
        yaml.set(path + ".pearl", kit.pearl());
        yaml.set(path + ".totem", kit.totem());
        yaml.set(path + ".adventure", kit.forceAdventure());
        yaml.set(path + ".timeout-seconds", kit.timeoutSeconds());
        yaml.set(path + ".arena", kit.arenaName());
        yaml.set(path + ".can-break", kit.canBreak());
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (KitItemEntry entry : kit.items()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", entry.slot());
            map.put("material", entry.material());
            map.put("amount", entry.amount());
            if (entry.hasSerializedItem()) {
                map.put("data", entry.itemDataBase64());
            }
            itemMaps.add(map);
        }
        yaml.set(path + ".items", itemMaps);
        for (Map.Entry<String, String> armor : kit.armor().entrySet()) {
            yaml.set(path + ".armor." + armor.getKey(), armor.getValue());
        }
        configService.save(ConfigService.KITS);
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

    /** Resolves a stored armor value: either a {@code data:<base64>} full item or a material name. */
    private static ItemStack armorStack(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith(ARMOR_DATA_PREFIX)) {
            ItemStack decoded = com.rumilance.practice.util.ItemSerializer
                    .singleFromBase64(value.substring(ARMOR_DATA_PREFIX.length()));
            if (decoded != null) {
                return decoded;
            }
        }
        Material material = Material.matchMaterial(value);
        return material == null || material.isAir() ? null : new ItemStack(material);
    }

    /** Rebuilds a kit item: prefers the full NBT snapshot, falls back to material+amount. */
    private static ItemStack resolveItem(KitItemEntry entry) {
        if (entry.hasSerializedItem()) {
            ItemStack decoded = com.rumilance.practice.util.ItemSerializer
                    .singleFromBase64(entry.itemDataBase64());
            if (decoded != null) {
                return decoded;
            }
        }
        Material material = Material.matchMaterial(entry.material());
        if (material == null || material.isAir()) {
            return null;
        }
        return new ItemStack(material, Math.max(1, entry.amount()));
    }

    private static ArenaTerrain parseTerrain(String raw) {
        if (raw == null) {
            return ArenaTerrain.ANY;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "false", "flat" -> ArenaTerrain.FLAT;
            case "true", "bumpy" -> ArenaTerrain.BUMPY;
            case "crystal" -> ArenaTerrain.CRYSTAL;
            case "neth", "netherite" -> ArenaTerrain.NETHERITE;
            default -> ArenaTerrain.ANY;
        };
    }
}
