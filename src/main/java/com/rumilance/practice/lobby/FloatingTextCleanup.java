package com.rumilance.practice.lobby;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Removes leftover "floating text" entities: {@link TextDisplay}s and invisible, named,
 * no-gravity {@link ArmorStand}s (the classic hologram trick), from this plugin or any other.
 *
 * <p><b>Why an event listener and not a one-shot loop:</b> since Minecraft 1.17, entities are
 * loaded <i>asynchronously after</i> their chunk (Paper issues #5872/#5921), so scanning
 * {@code world.getEntities()} right after enable sees almost nothing. The reliable API is
 * {@link EntitiesLoadEvent}, fired when a chunk's entities actually become available. We sweep
 * both everything already loaded at enable AND every {@code EntitiesLoadEvent} within the
 * configured startup window, so holograms saved in not-yet-loaded chunks are caught the moment
 * their chunk first loads.</p>
 */
public final class FloatingTextCleanup implements Listener {

    private final Plugin plugin;
    private final NamespacedKey wallTextKey;
    /** Epoch millis until which EntitiesLoadEvent sweeping stays active (Long.MAX_VALUE = forever). */
    private final long sweepUntilMillis;
    private int removed;

    private FloatingTextCleanup(Plugin plugin, long windowSeconds) {
        this.plugin = plugin;
        this.wallTextKey = new NamespacedKey(plugin, com.rumilance.practice.decor.WallTextService.MARKER);
        this.sweepUntilMillis = windowSeconds < 0
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + windowSeconds * 1000L;
    }

    /**
     * Starts the cleanup: immediate sweep of already-loaded entities plus an
     * {@link EntitiesLoadEvent} listener for {@code windowSeconds} (0 = only the immediate
     * sweep, negative = listen forever).
     */
    public static void start(Plugin plugin, long windowSeconds) {
        FloatingTextCleanup cleanup = new FloatingTextCleanup(plugin, windowSeconds);
        Bukkit.getPluginManager().registerEvents(cleanup, plugin);
        // Initial pass over whatever is already loaded (covers reloads where entities exist).
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    cleanup.maybeRemove(entity);
                }
            }
            cleanup.logIfAny();
        });
        // Periodic tally log so removals from late chunk loads are still visible in console.
        Bukkit.getScheduler().runTaskLater(plugin, cleanup::logIfAny, 20L * 60);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (System.currentTimeMillis() > sweepUntilMillis) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            maybeRemove(entity);
        }
    }

    private void maybeRemove(Entity entity) {
        if (isFloatingText(entity)) {
            entity.remove();
            removed++;
        }
    }

    private void logIfAny() {
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover floating-text entities (holograms).");
        }
    }

    private boolean isFloatingText(Entity entity) {
        if (entity instanceof TextDisplay display) {
            // Plugin-managed wall texts are respawned intentionally — never sweep them.
            return !display.getPersistentDataContainer().has(wallTextKey, PersistentDataType.STRING);
        }
        if (entity instanceof ArmorStand stand) {
            // Hologram signature: invisible + a shown custom name + no gravity. Anything with
            // equipment (real decorative stands) is left alone.
            boolean hologramLike = !stand.isVisible()
                    && stand.isCustomNameVisible()
                    && stand.customName() != null
                    && !stand.hasGravity();
            if (!hologramLike) {
                return false;
            }
            for (org.bukkit.inventory.ItemStack item : stand.getEquipment().getArmorContents()) {
                if (item != null && !item.getType().isAir()) {
                    return false;
                }
            }
            return stand.getEquipment().getItemInMainHand().getType().isAir()
                    && stand.getEquipment().getItemInOffHand().getType().isAir();
        }
        return false;
    }
}
