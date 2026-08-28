package com.rumilance.practice.arena;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.state.ArenaType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ArenaTemplateStore {

    public enum RenameResult {
        OK, NOT_FOUND, TARGET_EXISTS
    }

    private final ConfigService configService;
    private final List<ArenaTemplate> templates = new CopyOnWriteArrayList<>();

    public ArenaTemplateStore(ConfigService configService) {
        this.configService = configService;
    }

    public void reload() {
        templates.clear();
        templates.addAll(load(configService.arenas()));
    }

    public List<ArenaTemplate> templates() {
        return List.copyOf(templates);
    }

    public Optional<ArenaTemplate> findExact(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return templates.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    public List<ArenaTemplate> partyArenas() {
        return templates.stream().filter(t -> t.party() && t.enabled()).toList();
    }

    public void upsert(ArenaTemplate template) {
        templates.removeIf(t -> t.name().equals(template.name()));
        templates.add(template);
        persistAll();
    }

    public void setEnabled(String name, boolean enabled) {
        findExact(name).ifPresent(t -> {
            int i = templates.indexOf(t);
            if (i >= 0) {
                templates.set(i, t.withEnabled(enabled));
                persistAll();
            }
        });
    }

    public void setParty(String name, boolean party) {
        findExact(name).ifPresent(t -> {
            int i = templates.indexOf(t);
            if (i >= 0) {
                templates.set(i, t.withParty(party));
                persistAll();
            }
        });
    }

    public void setIconMaterial(String name, String material) {
        findExact(name).ifPresent(t -> {
            int i = templates.indexOf(t);
            if (i >= 0) {
                templates.set(i, t.withIconMaterial(material));
                persistAll();
            }
        });
    }

    public void setType(String name, ArenaType type) {
        findExact(name).ifPresent(t -> {
            int i = templates.indexOf(t);
            if (i >= 0) {
                templates.set(i, t.withType(type));
                persistAll();
            }
        });
    }

    public RenameResult rename(String oldName, String newName) {
        if (oldName == null || newName == null || newName.isBlank()) {
            return RenameResult.NOT_FOUND;
        }
        ArenaTemplate existing = findExact(oldName).orElse(null);
        if (existing == null) {
            return RenameResult.NOT_FOUND;
        }
        if (!oldName.equals(newName) && findExact(newName).isPresent()) {
            return RenameResult.TARGET_EXISTS;
        }
        templates.remove(existing);
        templates.add(existing.withName(newName));
        configService.arenas().set("arenas." + oldName, null);
        persistAll();
        return RenameResult.OK;
    }

    public void delete(String name) {
        templates.removeIf(t -> t.name().equals(name));
        configService.arenas().set("arenas." + name, null);
        persistAll();
    }

    private void persistAll() {
        FileConfiguration yaml = configService.arenas();
        yaml.set("arenas", null);
        for (ArenaTemplate t : templates) {
            String path = "arenas." + t.name();
            yaml.set(path + ".id", t.id().toString());
            yaml.set(path + ".type", t.type().name());
            yaml.set(path + ".world", t.world());
            yaml.set(path + ".enabled", t.enabled());
            yaml.set(path + ".party", t.party());
            if (t.iconMaterial() != null) {
                yaml.set(path + ".icon", t.iconMaterial());
            }
            yaml.set(path + ".schematic", t.schematicPath());
            yaml.set(path + ".min.x", t.minX());
            yaml.set(path + ".min.y", t.minY());
            yaml.set(path + ".min.z", t.minZ());
            yaml.set(path + ".max.x", t.maxX());
            yaml.set(path + ".max.y", t.maxY());
            yaml.set(path + ".max.z", t.maxZ());
            String[] a = t.serializedSpawnA() == null ? new String[0] : t.serializedSpawnA().split(";");
            String[] b = t.serializedSpawnB() == null ? new String[0] : t.serializedSpawnB().split(";");
            if (a.length >= 6) {
                yaml.set(path + ".spawn-a.x", Double.parseDouble(a[1]));
                yaml.set(path + ".spawn-a.y", Double.parseDouble(a[2]));
                yaml.set(path + ".spawn-a.z", Double.parseDouble(a[3]));
                yaml.set(path + ".spawn-a.yaw", Float.parseFloat(a[4]));
                yaml.set(path + ".spawn-a.pitch", Float.parseFloat(a[5]));
            }
            if (b.length >= 6) {
                yaml.set(path + ".spawn-b.x", Double.parseDouble(b[1]));
                yaml.set(path + ".spawn-b.y", Double.parseDouble(b[2]));
                yaml.set(path + ".spawn-b.z", Double.parseDouble(b[3]));
                yaml.set(path + ".spawn-b.yaw", Float.parseFloat(b[4]));
                yaml.set(path + ".spawn-b.pitch", Float.parseFloat(b[5]));
            }
        }
        configService.save(ConfigService.ARENAS);
    }

    public static List<ArenaTemplate> load(FileConfiguration arenasConfig) {
        List<ArenaTemplate> result = new ArrayList<>();
        ConfigurationSection section = arenasConfig.getConfigurationSection("arenas");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            result.add(fromSection(key, entry));
        }
        return result;
    }

    private static ArenaTemplate fromSection(String name, ConfigurationSection entry) {
        UUID id = entry.contains("id") ? UUID.fromString(entry.getString("id")) : UUID.nameUUIDFromBytes(name.getBytes());
        ArenaType type = ArenaType.valueOf(entry.getString("type", "DUEL").toUpperCase(Locale.ROOT));
        String world = entry.getString("world", "world");
        int minX = entry.getInt("min.x", 0);
        int minY = entry.getInt("min.y", 0);
        int minZ = entry.getInt("min.z", 0);
        int maxX = entry.getInt("max.x", 0);
        int maxY = entry.getInt("max.y", 0);
        int maxZ = entry.getInt("max.z", 0);
        String spawnA = buildSpawn(entry.getConfigurationSection("spawn-a"), world);
        String spawnB = buildSpawn(entry.getConfigurationSection("spawn-b"), world);
        String schematic = entry.getString("schematic", "");
        boolean enabled = entry.getBoolean("enabled", true);
        boolean party = entry.getBoolean("party", false);
        String icon = entry.getString("icon", null);
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                spawnA, spawnB, schematic, enabled, party, icon);
    }

    private static String buildSpawn(ConfigurationSection spawn, String world) {
        double x = spawn != null ? spawn.getDouble("x", 0.5) : 0.5;
        double y = spawn != null ? spawn.getDouble("y", 65.0) : 65.0;
        double z = spawn != null ? spawn.getDouble("z", 0.5) : 0.5;
        float yaw = spawn != null ? (float) spawn.getDouble("yaw", 0.0) : 0.0f;
        float pitch = spawn != null ? (float) spawn.getDouble("pitch", 0.0) : 0.0f;
        return String.join(";", world,
                String.format(Locale.ROOT, "%.4f", x),
                String.format(Locale.ROOT, "%.4f", y),
                String.format(Locale.ROOT, "%.4f", z),
                String.format(Locale.ROOT, "%.4f", yaw),
                String.format(Locale.ROOT, "%.4f", pitch));
    }
}
