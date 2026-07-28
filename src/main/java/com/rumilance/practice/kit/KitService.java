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
                    .canBreak(section.getStringList("can-break"));

            List<KitItemEntry> items = new ArrayList<>();
            List<Map<?, ?>> itemMaps = section.getMapList("items");
            for (Map<?, ?> map : itemMaps) {
                Object slotObj = map.get("slot");
                Object materialObj = map.get("material");
                Object amountObj = map.get("amount");
                int slot = slotObj instanceof Number number ? number.intValue() : 0;
                String material = materialObj == null ? "STONE" : String.valueOf(materialObj);
                int amount = amountObj instanceof Number number ? number.intValue() : 1;
                items.add(new KitItemEntry(slot, material, amount));
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
    }

    public Optional<KitDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<KitDefinition> all() {
        return List.copyOf(kits.values());
    }

    public List<KitDefinition> enabled() {
        return kits.values().stream().filter(KitDefinition::enabled).toList();
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
                Material material = Material.matchMaterial(entry.material());
                if (material == null || material.isAir()) {
                    continue;
                }
                inventory.setItem(entry.slot(), new ItemStack(material, Math.max(1, entry.amount())));
            }
            inventory.setHelmet(materialOrNull(kit.armor().get("helmet")));
            inventory.setChestplate(materialOrNull(kit.armor().get("chestplate")));
            inventory.setLeggings(materialOrNull(kit.armor().get("leggings")));
            inventory.setBoots(materialOrNull(kit.armor().get("boots")));
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

    public KitDefinition createFromPlayer(Player player, String id) {
        List<KitItemEntry> items = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            items.add(new KitItemEntry(i, stack.getType().name(), stack.getAmount()));
        }
        Map<String, String> armor = new LinkedHashMap<>();
        putArmor(armor, "helmet", player.getInventory().getHelmet());
        putArmor(armor, "chestplate", player.getInventory().getChestplate());
        putArmor(armor, "leggings", player.getInventory().getLeggings());
        putArmor(armor, "boots", player.getInventory().getBoots());

        ItemStack hand = player.getInventory().getItemInMainHand();
        String icon = hand.getType().isAir() ? "DIAMOND_SWORD" : hand.getType().name();
        KitDefinition kit = KitDefinition.builder(id)
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
        yaml.set(path + ".can-break", kit.canBreak());
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (KitItemEntry entry : kit.items()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", entry.slot());
            map.put("material", entry.material());
            map.put("amount", entry.amount());
            itemMaps.add(map);
        }
        yaml.set(path + ".items", itemMaps);
        for (Map.Entry<String, String> armor : kit.armor().entrySet()) {
            yaml.set(path + ".armor." + armor.getKey(), armor.getValue());
        }
        configService.save(ConfigService.KITS);
    }

    private static void putArmor(Map<String, String> armor, String key, ItemStack stack) {
        if (stack != null && !stack.getType().isAir()) {
            armor.put(key, stack.getType().name());
        }
    }

    private static ItemStack materialOrNull(String name) {
        if (name == null) {
            return null;
        }
        Material material = Material.matchMaterial(name);
        return material == null || material.isAir() ? null : new ItemStack(material);
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
