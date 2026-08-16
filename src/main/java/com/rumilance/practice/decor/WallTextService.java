package com.rumilance.practice.decor;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Wall-mounted text via {@link TextDisplay} entities: flat, glowing-free labels snapped onto
 * a block face (like a poster). Each display is tagged with a PDC marker so the startup
 * floating-text sweep skips plugin-managed labels, and all placements are persisted to
 * {@code walltext.yml} and respawned on enable (displays do not survive entity purges).
 *
 * <p>Text supports MiniMessage ({@code <aqua>N Arena</aqua>} etc.); the default styling is
 * the plugin's aqua accent. Scale is configurable per label.</p>
 */
public final class WallTextService {

    private static final String FILE = "walltext.yml";
    /** PDC marker key name; presence = managed by this service. */
    public static final String MARKER = "wall_text";

    private final Plugin plugin;
    private final Map<String, Placement> placements = new LinkedHashMap<>();
    private final Map<String, UUID> spawned = new LinkedHashMap<>();

    /** One persisted wall label. */
    public record Placement(String id, Location location, BlockFace face, String miniMessage, float scale) {
    }

    public WallTextService(Plugin plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey markerKey() {
        return new NamespacedKey(plugin, MARKER);
    }

    /** Loads walltext.yml and (re)spawns every stored label. Call once on enable. */
    public void load() {
        placements.clear();
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("labels");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            World world = Bukkit.getWorld(s.getString("world", "world"));
            if (world == null) {
                continue;
            }
            BlockFace face;
            try {
                face = BlockFace.valueOf(s.getString("face", "NORTH"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Location loc = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
            placements.put(id.toLowerCase(Locale.ROOT), new Placement(
                    id.toLowerCase(Locale.ROOT), loc, face,
                    s.getString("text", ""), (float) s.getDouble("scale", 2.0)));
        }
        respawnAll();
    }

    /** Removes any lingering spawned displays and spawns every placement fresh. */
    public void respawnAll() {
        // Kill any tagged displays first (e.g. duplicates left from a crash).
        NamespacedKey key = markerKey();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
        spawned.clear();
        for (Placement placement : placements.values()) {
            spawn(placement);
        }
    }

    /** Despawns every managed display (called on plugin disable). */
    public void despawnAll() {
        for (UUID id : spawned.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        spawned.clear();
    }

    /**
     * Creates (or replaces) a wall label on the block face the player is looking at.
     *
     * @return the placement, or null when the player is not looking at a block within 6 blocks.
     */
    public Placement placeAtSight(Player player, String id, String miniMessage, float scale) {
        RayTraceResult ray = player.rayTraceBlocks(6.0);
        if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) {
            return null;
        }
        Block block = ray.getHitBlock();
        BlockFace face = ray.getHitBlockFace();
        if (face != BlockFace.NORTH && face != BlockFace.SOUTH
                && face != BlockFace.EAST && face != BlockFace.WEST) {
            return null; // floors/ceilings unsupported (wall text only)
        }
        Location loc = faceCenter(block, face);
        String cleanId = id.toLowerCase(Locale.ROOT);
        remove(cleanId);
        Placement placement = new Placement(cleanId, loc, face, miniMessage, scale);
        placements.put(cleanId, placement);
        spawn(placement);
        save();
        return placement;
    }

    /** Deletes a label by id (despawn + forget + persist). */
    public boolean remove(String id) {
        String cleanId = id.toLowerCase(Locale.ROOT);
        Placement removed = placements.remove(cleanId);
        UUID entityId = spawned.remove(cleanId);
        if (entityId != null) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        if (removed != null) {
            save();
        }
        return removed != null;
    }

    public Map<String, Placement> all() {
        return Map.copyOf(placements);
    }

    // ------------------------------------------------------------------ internals

    private void spawn(Placement placement) {
        World world = placement.location().getWorld();
        if (world == null) {
            return;
        }
        Location loc = placement.location().clone();
        loc.setYaw(yawFor(placement.face()));
        loc.setPitch(0f);
        TextDisplay display = world.spawn(loc, TextDisplay.class, d -> {
            d.text(MiniMessage.miniMessage().deserialize(placement.miniMessage()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // fully transparent backdrop
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            float s = Math.max(0.25f, Math.min(16f, placement.scale()));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(s, s, s),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            d.setPersistent(true);
            d.getPersistentDataContainer().set(markerKey(), PersistentDataType.STRING, placement.id());
        });
        spawned.put(placement.id(), display.getUniqueId());
    }

    /** Point slightly off the face centre so the text never z-fights with the block. */
    private static Location faceCenter(Block block, BlockFace face) {
        double x = block.getX() + 0.5 + face.getModX() * 0.53;
        double y = block.getY() + 0.5;
        double z = block.getZ() + 0.5 + face.getModZ() * 0.53;
        return new Location(block.getWorld(), x, y, z);
    }

    /** TextDisplay faces the player when its yaw points *out* of the wall. */
    private static float yawFor(BlockFace face) {
        return switch (face) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Placement p : placements.values()) {
            String path = "labels." + p.id();
            yaml.set(path + ".world", p.location().getWorld() == null
                    ? "world" : p.location().getWorld().getName());
            yaml.set(path + ".x", p.location().getX());
            yaml.set(path + ".y", p.location().getY());
            yaml.set(path + ".z", p.location().getZ());
            yaml.set(path + ".face", p.face().name());
            yaml.set(path + ".text", p.miniMessage());
            yaml.set(path + ".scale", (double) p.scale());
        }
        try {
            yaml.save(new File(plugin.getDataFolder(), FILE));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + FILE + ": " + e.getMessage());
        }
    }
}
