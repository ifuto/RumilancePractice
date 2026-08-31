package com.rumilance.practice.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Lightweight, experience-neutral performance tuning.
 *
 * <p>A practice server never needs most of the survival simulation, but Paper still runs it for
 * every loaded world: random crop/fire/leaf ticks, natural weather and daylight cycles,
 * do-mob-spawning entity AI across the whole world, and a handful of per-tick block/entity
 * behaviours. Turning these off removes steady background CPU and entity-processing load with
 * zero effect on fights — combat, projectile physics, redstone that isn't driven by random
 * ticks, placements, and TNT all keep working. We apply the rules to every world a player can be
 * in (we never host survival content), including worlds that load after startup.</p>
 */
public final class WorldOptimizer implements Listener {

    private final Plugin plugin;
    private final Logger logger;

    public WorldOptimizer(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Applies the tuned game rules to every currently loaded world. Call once on enable. */
    public void optimizeLoadedWorlds() {
        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            apply(world);
            count++;
        }
        logger.info("[N Arena] Applied performance game rules to " + count + " world(s).");
    }

    /** Applies to a world the moment it loads (disposable arena worlds, template worlds, etc.). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        // Disable everything that only matters for survival/ambience and costs background ticks.
        bool(world, GameRule.DO_MOB_SPAWNING, false);        // no passive/hostile mobs + their AI
        bool(world, GameRule.DO_WEATHER_CYCLE, false);       // no thunder/rain changes (no PvP weather)
        intg(world, GameRule.RANDOM_TICK_SPEED, 0);          // no crop growth / fire spread / leaf decay
        bool(world, GameRule.MOB_GRIEFING, false);           // mobs/creatures can't alter blocks
        bool(world, GameRule.DO_PATROL_SPAWNING, false);
        bool(world, GameRule.DO_TRADER_SPAWNING, false);
        bool(world, GameRule.DO_INSOMNIA, false);            // no phantom spawning
        bool(world, GameRule.DO_WARDEN_SPAWNING, false);
        intg(world, GameRule.SPAWN_RADIUS, 0);
        // Keep the environment readable and predictable for fights: no weather darkness, and no
        // night mobs even if a world is somehow left at night.
        bool(world, GameRule.ANNOUNCE_ADVANCEMENTS, false);
        bool(world, GameRule.SPECTATORS_GENERATE_CHUNKS, false);
        // Player fights are unaffected by the daylight cycle; freezing it avoids per-world time
        // updates and weather transitions.
        bool(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        // Force a clear, bright sky so no fight ever runs in a storm/darkness.
        try {
            if (world.hasStorm()) {
                world.setStorm(false);
            }
            if (world.isThundering()) {
                world.setThundering(false);
            }
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setTime(6000L); // noon, brightest
            }
        } catch (RuntimeException ignored) {
        }

        // Cut the ambient mob caps to zero via spawn limits as an extra guard (even if a rule is
        // overridden by an admin world). These only control monster/animal spawns.
        try {
            world.setMonsterSpawnLimit(0);
            world.setAnimalSpawnLimit(0);
            world.setWaterAnimalSpawnLimit(0);
            world.setAmbientSpawnLimit(0);
        } catch (RuntimeException ignored) {
        }

        // Clear any stray ambient/survival living entities already present (we never need them).
        // Mob spawning is disabled above; this just sweeps mobs that existed before tuning.
        try {
            int removed = 0;
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                if (entity instanceof org.bukkit.entity.Monster
                        || entity instanceof org.bukkit.entity.Animals
                        || entity instanceof org.bukkit.entity.Ambient
                        || entity instanceof org.bukkit.entity.WaterMob
                        || entity instanceof org.bukkit.entity.Golem
                        || entity instanceof org.bukkit.entity.Villager) {
                    entity.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                logger.info("[N Arena] Removed " + removed + " ambient entities from " + world.getName());
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void bool(World world, GameRule<Boolean> rule, boolean value) {
        try {
            world.setGameRule(rule, value);
        } catch (RuntimeException ignored) {
            // Rule may differ across versions; ignore rather than fail the rest.
        }
    }

    private void intg(World world, GameRule<Integer> rule, int value) {
        try {
            world.setGameRule(rule, value);
        } catch (RuntimeException ignored) {
            // Rule may differ across versions; ignore rather than fail the rest.
        }
    }
}
