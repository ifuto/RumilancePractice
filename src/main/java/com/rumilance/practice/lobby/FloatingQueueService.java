package com.rumilance.practice.lobby;

import com.rumilance.practice.match.MatchMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Per-kit floating queue items in the lobby.
 *
 * <p>Each one is the <strong>kit's own icon</strong> hanging in the air at the spot an admin
 * spawned it with {@code /float spawn queue <kit>}: it spins horizontally at a steady rate
 * (never tracking the player) with a slight vertical bob, stands roughly two blocks tall, lights
 * up white for whoever is looking at it, and joins that kit's <strong>unranked</strong> queue when
 * clicked.</p>
 *
 * <h3>Why it is built the way it is</h3>
 * <ul>
 *   <li><b>Billboard.FIXED</b> — the default {@code CENTER} billboard makes the item face the
 *       viewer, which cancels out any rotation of our own. The spin is applied to the
 *       transformation instead.</li>
 *   <li><b>A separate {@link Interaction} hitbox</b> — an {@code ItemDisplay} is decoration and
 *       cannot be right-clicked. The hitbox is invisible and sized to the item.</li>
 *   <li><b>A per-player glow overlay</b> — {@code Entity#setGlowing} is global, so the white
 *       outline is a second {@code ItemDisplay} that everyone has hidden and that is revealed
 *       only to the player currently hovering the item.</li>
 * </ul>
 *
 * <p>Everything is stored in {@code lobby.yml} under {@code floating-queue-items} and respawned on
 * restart. The queue join itself is handed to the caller through
 * {@link #setJoinUnranked(BiConsumer)} so this class stays free of queue internals.</p>
 */
public final class FloatingQueueService {

    private static final String CONFIG_KEY = "floating-queue-items";

    /** How often the spin/bob/hover state is advanced, in ticks. */
    private static final long TICK_PERIOD = 1L;

    /** Distance within which a player can hover/click the item. */
    private static final double REACH = 6.0d;
    /** Cosine of the largest angle between look direction and the item that still counts as hover. */
    private static final double HOVER_COS = 0.97d;

    private final Plugin plugin;
    private final List<Floating> items = new ArrayList<>();

    /** entityId -> what clicking it does; shared with FloatingEntityClickListener. */
    private final Map<Integer, Runnable> clickActions = new ConcurrentHashMap<>();
    /** Which players currently see the glow overlay of which item. */
    private final Map<UUID, Floating> glowingFor = new ConcurrentHashMap<>();

    private BukkitTask task;
    private long ticks;
    private volatile boolean shuttingDown;

    /** Seconds for one full horizontal turn. Tuned to feel like the Ready emerald. */
    private volatile double secondsPerTurn = 3.0d;
    /** Peak of the up/down bob, in blocks — deliberately tiny. */
    private volatile double bobAmplitude = 0.08d;
    /** Seconds for one full bob cycle. */
    private volatile double bobSeconds = 2.0d;
    /** Rendered height in blocks; the item is scaled to match. */
    private volatile double heightBlocks = 2.0d;

    private volatile BiConsumer<Player, String> joinUnranked;
    private volatile com.rumilance.practice.kit.KitService kitService;

    public FloatingQueueService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Called on click; receives the player and the kit name. */
    public void setJoinUnranked(BiConsumer<Player, String> joinUnranked) {
        this.joinUnranked = joinUnranked;
    }

    public void setKitService(com.rumilance.practice.kit.KitService kitService) {
        this.kitService = kitService;
    }

    /** The click map {@link FloatingEntityClickListener} should read. */
    public Map<Integer, Runnable> clickActions() {
        return clickActions;
    }

    public void setSecondsPerTurn(double value) {
        this.secondsPerTurn = value > 0.01d ? value : 3.0d;
    }

    public void setBobAmplitude(double value) {
        this.bobAmplitude = Math.max(0d, value);
    }

    public void setBobSeconds(double value) {
        this.bobSeconds = value > 0.01d ? value : 2.0d;
    }

    public void setHeightBlocks(double value) {
        this.heightBlocks = value > 0.1d ? value : 2.0d;
    }

    /**
     * Every kit that could be floated, whether or not it has an item yet.
     * Feeds {@code /float} tab-completion.
     */
    public java.util.Collection<String> kitIds() {
        com.rumilance.practice.kit.KitService kits = kitService;
        if (kits == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (com.rumilance.practice.model.KitDefinition kit : kits.enabled()) {
            if (kit != null && kit.name() != null) {
                out.add(kit.name());
            }
        }
        return out;
    }

    /** Names of every kit that currently has a floating item. */
    public List<String> kitNames() {
        List<String> out = new ArrayList<>();
        synchronized (items) {
            for (Floating f : items) {
                out.add(f.kitName);
            }
        }
        return out;
    }

    public int size() {
        synchronized (items) {
            return items.size();
        }
    }

    /**
     * Spawns the floating item for a kit.
     *
     * @return false when the kit is unknown, so the caller can say so instead of spawning a
     *         featureless stone block
     */
    public boolean spawn(Location location, String kitName) {
        if (location == null || location.getWorld() == null || kitName == null || kitName.isBlank()) {
            return false;
        }
        ItemStack icon = iconFor(kitName);
        if (icon == null) {
            return false;
        }
        Location base = location.clone();
        World world = base.getWorld();

        Floating floating = new Floating();
        floating.kitName = kitName;
        floating.base = base.clone();
        floating.icon = icon;

        floating.display = world.spawn(base.clone(), ItemDisplay.class, d -> {
            d.setItemStack(icon.clone());
            // FIXED, not CENTER: a billboard always turns the item towards the viewer and
            // would swallow the rotation we apply below.
            d.setBillboard(ItemDisplay.Billboard.FIXED);
            d.setPersistent(false);
        });
        floating.glow = world.spawn(base.clone(), ItemDisplay.class, d -> {
            d.setItemStack(icon.clone());
            d.setBillboard(ItemDisplay.Billboard.FIXED);
            d.setPersistent(false);
            d.setGlowing(true);
        });
        hideGlowFromEveryone(floating);

        // Invisible hitbox: the item itself cannot be clicked.
        floating.hitbox = world.spawn(base.clone().subtract(0, heightBlocks / 2d, 0),
                Interaction.class, i -> {
                    i.setInteractionWidth((float) Math.max(1.0d, heightBlocks * 0.75d));
                    i.setInteractionHeight((float) heightBlocks);
                    i.setResponsive(true);
                    i.setPersistent(false);
                });
        clickActions.put(floating.hitbox.getEntityId(), () -> {
            BiConsumer<Player, String> join = joinUnranked;
            if (join != null) {
                // The action carries no player; the hover map says who was pointing at it.
                for (Map.Entry<UUID, Floating> entry : glowingFor.entrySet()) {
                    if (entry.getValue() == floating) {
                        Player viewer = Bukkit.getPlayer(entry.getKey());
                        if (viewer != null && viewer.isOnline()) {
                            join.accept(viewer, floating.kitName);
                        }
                        return;
                    }
                }
            }
        });

        synchronized (items) {
            items.add(floating);
        }
        startTask();
        return true;
    }

    /** The item a kit is represented by, or null when the kit does not exist. */
    private ItemStack iconFor(String kitName) {
        com.rumilance.practice.kit.KitService kits = kitService;
        if (kits == null) {
            return null;
        }
        // KitDefinition.icon() is the material name the kit is represented by — that is the
        // "kit icon" the floating item should show, not the kit's display name.
        return kits.get(kitName)
                .map(com.rumilance.practice.model.KitDefinition::icon)
                .map(Material::matchMaterial)
                .filter(material -> !material.isAir())
                .map(ItemStack::new)
                .orElse(null);
    }

    private void hideGlowFromEveryone(Floating floating) {
        if (floating.glow == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, floating.glow);
        }
    }

    private synchronized void startTask() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, TICK_PERIOD);
    }

    private void tick() {
        if (shuttingDown) {
            return;
        }
        ticks++;
        // 20 ticks = 1 second.
        double turn = (ticks / 20.0d) / secondsPerTurn;
        float angle = (float) (turn * Math.PI * 2.0d);
        double bob = Math.sin((ticks / 20.0d) * Math.PI * 2.0d / bobSeconds) * bobAmplitude;
        // An ItemDisplay renders its item roughly 0.5 blocks tall at scale 1.
        float scale = (float) (heightBlocks / 0.5d);

        List<Floating> snapshot;
        synchronized (items) {
            snapshot = new ArrayList<>(items);
        }
        for (Floating f : snapshot) {
            if (!f.isAlive()) {
                continue;
            }
            Location at = f.base.clone().add(0, bob + heightBlocks / 2d, 0);
            f.display.teleport(at);
            f.glow.teleport(at);
            Transformation transform = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(angle, 0f, 1f, 0f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f));
            f.display.setTransformation(transform);
            f.glow.setTransformation(transform);
            updateHover(f, at);
        }
    }

    /**
     * Reveals the glow overlay to whoever is looking at this item and hides it from everyone
     * else, so the white outline is always private to the one hovering.
     */
    private void updateHover(Floating floating, Location at) {
        Player hoverer = null;
        for (Player player : at.getWorld().getPlayers()) {
            if (!isHovering(player, at)) {
                continue;
            }
            hoverer = player;
            break;
        }
        for (Map.Entry<UUID, Floating> entry : glowingFor.entrySet()) {
            if (entry.getValue() != floating) {
                continue;
            }
            Player previous = Bukkit.getPlayer(entry.getKey());
            if (previous != null && (hoverer == null || !previous.equals(hoverer))) {
                previous.hideEntity(plugin, floating.glow);
            }
        }
        glowingFor.values().removeIf(value -> value == floating);
        if (hoverer != null) {
            glowingFor.put(hoverer.getUniqueId(), floating);
            hoverer.showEntity(plugin, floating.glow);
        }
    }

    /** True when the player is close enough and the item is under their crosshair. */
    private static boolean isHovering(Player player, Location at) {
        Location eye = player.getEyeLocation();
        if (!eye.getWorld().equals(at.getWorld())) {
            return false;
        }
        org.bukkit.util.Vector toItem = at.toVector().subtract(eye.toVector());
        double distance = toItem.length();
        if (distance > REACH || distance < 0.0001d) {
            return false;
        }
        return toItem.normalize().dot(eye.getDirection()) >= HOVER_COS;
    }

    // ------------------------------------------------------------------ persistence

    public void saveToConfig(FileConfiguration lobby) {
        lobby.set(CONFIG_KEY, null);
        int index = 0;
        synchronized (items) {
            for (Floating f : items) {
                if (!f.isAlive()) {
                    continue;
                }
                // The AUTHORED spot, never the animated one: saving the bobbing position makes
                // every item drift a little further on each restart.
                Location loc = f.base;
                String path = CONFIG_KEY + "." + index + ".";
                lobby.set(path + "world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
                lobby.set(path + "x", loc.getX());
                lobby.set(path + "y", loc.getY());
                lobby.set(path + "z", loc.getZ());
                lobby.set(path + "kit", f.kitName);
                index++;
            }
        }
    }

    public void loadFromConfig(FileConfiguration lobby) {
        ConfigurationSection section = lobby.getConfigurationSection(CONFIG_KEY);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = CONFIG_KEY + "." + key + ".";
            World world = Bukkit.getWorld(lobby.getString(path + "world", "world"));
            String kitName = lobby.getString(path + "kit");
            if (world == null || kitName == null || kitName.isBlank()) {
                continue;
            }
            Location loc = new Location(world,
                    lobby.getDouble(path + "x"),
                    lobby.getDouble(path + "y"),
                    lobby.getDouble(path + "z"));
            try {
                spawn(loc, kitName);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[float] could not respawn '" + kitName + "' at " + loc
                        + ": " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public void removeAll() {
        synchronized (items) {
            for (Floating f : items) {
                f.destroy(plugin);
            }
            items.clear();
        }
        clickActions.clear();
        glowingFor.clear();
    }

    /** Removes every floating item bound to this kit. @return how many were removed */
    public int removeKit(String kitName) {
        int removed = 0;
        synchronized (items) {
            List<Floating> keep = new ArrayList<>();
            for (Floating f : items) {
                if (f.kitName.equalsIgnoreCase(kitName)) {
                    f.destroy(plugin);
                    removed++;
                } else {
                    keep.add(f);
                }
            }
            items.clear();
            items.addAll(keep);
        }
        return removed;
    }

    /** Drops the entities but keeps the list, so a save after shutdown still has the spots. */
    public void shutdown() {
        shuttingDown = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        synchronized (items) {
            for (Floating f : items) {
                f.destroy(plugin);
            }
        }
        clickActions.clear();
        glowingFor.clear();
    }

    /** One floating item: the visible icon, its private glow twin and the click hitbox. */
    private static final class Floating {
        String kitName;
        Location base;
        ItemStack icon;
        ItemDisplay display;
        ItemDisplay glow;
        Interaction hitbox;

        boolean isAlive() {
            return display != null && display.isValid() && !display.isDead()
                    && glow != null && glow.isValid() && !glow.isDead()
                    && hitbox != null && hitbox.isValid() && !hitbox.isDead();
        }

        void destroy(Plugin plugin) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
            if (glow != null && !glow.isDead()) {
                glow.remove();
            }
            if (hitbox != null && !hitbox.isDead()) {
                hitbox.remove();
            }
        }
    }
}
