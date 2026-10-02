package com.rumilance.practice.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Spawns floating entities in the lobby for visual guidance.
 * Queue join items bob and rotate (20s/rotation).
 * Sword FFA indicator has rotating diamond sword with aqua text below.
 *
 * Entities are persisted to lobby.yml under {@code floating-entities.*} and
 * respawned on every server restart (non-persistent entities vanish on shutdown).
 */
public final class LobbyFloatingEntitiesService {

    private static final String CONFIG_KEY = "floating-entities";

    private final Plugin plugin;
    private final List<ManagedEntity> managed = new ArrayList<>();
    private BukkitTask animTask;
    private long tickCount;
    private final java.util.Map<Integer, Double> baseY = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public LobbyFloatingEntitiesService(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---- persistence ----

    public void saveToConfig(FileConfiguration lobby) {
        lobby.set(CONFIG_KEY, null);
        int index = 0;
        for (ManagedEntity me : managed) {
            if (me.entity == null || me.entity.isDead()) continue;
            Location loc = me.entity.getLocation();
            String path = CONFIG_KEY + "." + index + ".";
            lobby.set(path + "world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
            lobby.set(path + "x", loc.getX());
            lobby.set(path + "y", loc.getY());
            lobby.set(path + "z", loc.getZ());
            lobby.set(path + "type", me.type.name());
            if (me.icon != null) lobby.set(path + "icon", me.icon.name());
            index++;
        }
    }

    public void loadFromConfig(FileConfiguration lobby) {
        if (!lobby.isConfigurationSection(CONFIG_KEY)) return;
        var section = lobby.getConfigurationSection(CONFIG_KEY);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = CONFIG_KEY + "." + key + ".";
            String worldName = lobby.getString(path + "world", "world");
            double x = lobby.getDouble(path + "x");
            double y = lobby.getDouble(path + "y");
            double z = lobby.getDouble(path + "z");
            String typeStr = lobby.getString(path + "type", "QUEUE");
            String iconStr = lobby.getString(path + "icon");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            Location loc = new Location(world, x, y, z);
            try {
                EntityType type = EntityType.valueOf(typeStr);
                Material icon = iconStr != null ? Material.matchMaterial(iconStr) : null;
                if (type == EntityType.QUEUE) {
                    spawnQueueItem(loc, icon != null ? icon : Material.DIAMOND_SWORD);
                } else {
                    spawnSwordFfaIndicator(loc);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to respawn floating entity at " + loc, e);
            }
        }
    }

    // ---- spawn ----

    public enum EntityType { QUEUE, SWORD_FFA }

    private static class ManagedEntity {
        Entity entity;
        EntityType type;
        Material icon;
        Location originalLocation;
    }

    public void spawnQueueItem(Location loc, Material icon) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        Location safeLoc = loc.clone();
        Material safeIcon = icon == null ? Material.DIAMOND_SWORD : icon;

        ItemDisplay display = world.spawn(safeLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(safeIcon));
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f(0, 0, 1, 0)));
            d.setBillboard(ItemDisplay.Billboard.CENTER);
            d.setPersistent(false);
        });
        baseY.put(display.getEntityId(), safeLoc.getY());

        ManagedEntity me = new ManagedEntity();
        me.entity = display;
        me.type = EntityType.QUEUE;
        me.icon = safeIcon;
        me.originalLocation = safeLoc.clone();
        managed.add(me);
        startAnimation();
    }

    public void spawnSwordFfaIndicator(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        Location safeLoc = loc.clone();

        Location swordLoc = safeLoc.clone().add(0, 1.8, 0);
        ItemDisplay sword = world.spawn(swordLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.DIAMOND_SWORD));
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f(0, 0, 1, 0)));
            d.setBillboard(ItemDisplay.Billboard.CENTER);
            d.setPersistent(false);
        });
        baseY.put(sword.getEntityId(), swordLoc.getY());

        Location textLoc = safeLoc.clone().add(0, 0.5, 0);
        TextDisplay text = world.spawn(textLoc, TextDisplay.class, t -> {
            t.text(Component.text("↓ SWORD FFA ↓", NamedTextColor.AQUA, TextDecoration.BOLD));
            t.setBillboard(TextDisplay.Billboard.CENTER);
            t.setLineWidth(200);
            t.setShadowed(true);
            t.setPersistent(false);
            t.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f(0, 0, 1, 0)));
        });

        ManagedEntity me = new ManagedEntity();
        me.entity = sword;
        me.type = EntityType.SWORD_FFA;
        me.originalLocation = safeLoc.clone();
        managed.add(me);

        // Track the TextDisplay too so removeAll() cleans it up.
        ManagedEntity textEntry = new ManagedEntity();
        textEntry.entity = text;
        textEntry.type = EntityType.SWORD_FFA;
        textEntry.originalLocation = textLoc.clone();
        managed.add(textEntry);

        startAnimation();
    }

    // ---- animation ----

    private synchronized void startAnimation() {
        if (animTask != null && !animTask.isCancelled()) return;
        tickCount = 0;
        animTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAnimation, 1L, 1L);
    }

    private synchronized void tickAnimation() {
        if (shuttingDown) {
            if (animTask != null) { animTask.cancel(); animTask = null; }
            return;
        }
        tickCount++;
        float yawDeg = (float) ((tickCount * 720.0) / 400.0) % 720.0f;
        float bobOffset = (float) (Math.sin(tickCount * Math.PI * 2.0 / 40.0) * 0.05);

        boolean anyAlive = false;
        for (ManagedEntity me : managed) {
            if (me.entity == null || me.entity.isDead() || !me.entity.isValid()) continue;
            anyAlive = true;
            if (me.entity instanceof ItemDisplay display && display.isValid()) {
                Double origY = baseY.get(display.getEntityId());
                if (origY == null) continue;
                Location base = me.originalLocation;
                display.teleport(new Location(
                        base.getWorld(), base.getX(), origY + bobOffset, base.getZ(), yawDeg, 0));
            }
        }
        if (!anyAlive && !managed.isEmpty()) {
            managed.clear();
            baseY.clear();
            if (animTask != null) { animTask.cancel(); animTask = null; }
        }
    }

    public void shutdown() {
        shuttingDown = true;
        if (animTask != null) { animTask.cancel(); animTask = null; }
    }

    public void removeAll() {
        if (animTask != null) { animTask.cancel(); animTask = null; }
        for (ManagedEntity me : managed) {
            if (me.entity != null && !me.entity.isDead()) me.entity.remove();
        }
        managed.clear();
        baseY.clear();
    }
}