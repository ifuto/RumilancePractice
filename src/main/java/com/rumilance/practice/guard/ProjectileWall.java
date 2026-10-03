package com.rumilance.practice.guard;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The play-area border acts as a solid block wall for PROJECTILES too. The per-player client
 * border and the server wall ({@code PlayAreaWall}) only stop PLAYERS — an ender pearl, wind
 * charge, arrow or snowball simply flew through the invisible wall, landing (and teleporting /
 * bursting) on the far side of an edge that is supposed to be a wall.
 *
 * <p>Projectiles thrown by a fighter inside a play region (duel instance / FFA arena) are
 * tracked; the first tick one is horizontally outside its region it has hit the wall, and the
 * wall plane responds like a block face:</p>
 *
 * <ul>
 *   <li><b>ender pearl</b> — removed and the thrower teleports to the impact point (the
 *       vanilla pearl-into-a-wall outcome), resting one player half-width inside the face,</li>
 *   <li><b>wind charge</b> — wind burst AT the wall face: GUST burst visual, wind-burst sound
 *       and the vanilla-style outward shove (same magnitudes as the AFK room's wind pop) to
 *       every living entity nearby, then removed,</li>
 *   <li><b>arrows / tridents</b> — velocity zeroed: they stick at the face and drop straight
 *       down at its base, like an arrow shot into a wall,</li>
 *   <li><b>everything else</b> (snowballs, eggs, splash potions, exp bottles) — a small break
 *       burst at the impact point and removed.</li>
 * </ul>
 *
 * <p>The wall is horizontally infinite (same as the world border it mirrors): a pearl arcing
 * above the arena still meets the plane. Projectiles hitting a real block/entity first hit
 * there ({@link ProjectileHitEvent} untracks); removed entities untrack themselves.</p>
 */
public final class ProjectileWall implements Listener {

    /** Skin thickness — must match PlayAreaWall so both agree on where the face is. */
    private static final double EPSILON = 1.0e-3;
    /** A pearl-teleported player's bounding box rests this far inside the hit face. */
    private static final double PEARL_STANDOFF = 0.3;
    /** Radius of the wind-burst shove at a wall face. */
    private static final double WIND_BURST_RADIUS = 4.0;
    /** Wind-burst shove strength (same family as AfkCrystalManager's wind pop). */
    private static final double WIND_BURST_KB = 1.6d;
    private static final double WIND_BURST_KB_Y = 0.35d;

    private final Plugin plugin;
    private final MatchService matchService;
    private final ArenaService arenaService;
    private final FfaService ffaService;

    private static final class Tracked {
        final Projectile projectile;
        final Cuboid region;

        Tracked(Projectile projectile, Cuboid region) {
            this.projectile = projectile;
            this.region = region;
        }
    }

    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ProjectileWall(Plugin plugin, MatchService matchService,
                          ArenaService arenaService, FfaService ffaService) {
        this.plugin = plugin;
        this.matchService = matchService;
        this.arenaService = arenaService;
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        Cuboid region = regionOf(shooter);
        if (region == null) {
            return;
        }
        tracked.put(projectile.getUniqueId(), new Tracked(projectile, region));
        ensureTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRealHit(ProjectileHitEvent event) {
        tracked.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoved(EntityRemoveEvent event) {
        tracked.remove(event.getEntity().getUniqueId());
    }

    /** The play region the shooter is fighting in, or {@code null} (untracked). */
    private Cuboid regionOf(Player shooter) {
        MatchSession session = matchService.registry().byPlayer(shooter.getUniqueId()).orElse(null);
        if (session != null && session.arenaInstanceId() != null) {
            ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
            if (instance != null) {
                return Cuboid.of(instance.template().world(),
                        instance.minX(), instance.minY(), instance.minZ(),
                        instance.maxX(), instance.maxY(), instance.maxZ());
            }
        }
        if (ffaService != null) {
            String arenaId = ffaService.arenaOf(shooter.getUniqueId()).orElse(null);
            if (arenaId != null) {
                FfaService.FfaArena arena = ffaService.get(arenaId).orElse(null);
                if (arena != null && arena.region() != null) {
                    return arena.region();
                }
            }
        }
        return null;
    }

    /** Runs a per-tick sweep only while at least one projectile is in flight. */
    private void ensureTask() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Tracked> it = tracked.values().iterator();
            boolean anyAlive = false;
            while (it.hasNext()) {
                Tracked t = it.next();
                Projectile p = t.projectile;
                if (!p.isValid() || p.isDead()) {
                    it.remove();
                    continue;
                }
                Location at = p.getLocation();
                double minX = t.region.minX() + EPSILON;
                double maxX = t.region.maxX() + 1.0d - EPSILON;
                double minZ = t.region.minZ() + EPSILON;
                double maxZ = t.region.maxZ() + 1.0d - EPSILON;
                double x = at.getX();
                double z = at.getZ();
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    anyAlive = true;
                    continue;
                }
                it.remove();
                Location impact = new Location(at.getWorld(),
                        Math.max(minX, Math.min(maxX, x)), at.getY(),
                        Math.max(minZ, Math.min(maxZ, z)));
                hitWall(p, t.region, x, z, impact);
            }
            if (!anyAlive && task != null) {
                task.cancel();
                task = null;
            }
        }, 1L, 1L);
    }

    private void hitWall(Projectile p, Cuboid region, double x, double z, Location impact) {
        if (p instanceof EnderPearl pearl) {
            pearl.remove();
            if (pearl.getShooter() instanceof Player shooter && shooter.isOnline() && !shooter.isDead()) {
                teleportShooterToWall(shooter, region, x, z, impact);
            }
            return;
        }
        if (p instanceof WindCharge charge) {
            charge.remove();
            windBurst(impact);
            return;
        }
        if (p instanceof AbstractArrow arrow) {
            // Stick at the face like an arrow shot into a wall, then drop at its base.
            arrow.setVelocity(new Vector(0, 0, 0));
            arrow.teleport(impact);
            return;
        }
        p.remove();
        impact.getWorld().spawnParticle(Particle.CRIT, impact, 6, 0.15, 0.15, 0.15, 0.05);
    }

    /** Vanilla pearl-into-wall: the thrower arrives at the impact, half a width off the face. */
    private void teleportShooterToWall(Player shooter, Cuboid region,
                                       double x, double z, Location impact) {
        double sx = impact.getX();
        double sz = impact.getZ();
        if (x > region.maxX() + 1.0d - EPSILON) {
            sx = region.maxX() + 1.0d - PEARL_STANDOFF;
        } else if (x < region.minX() + EPSILON) {
            sx = region.minX() + PEARL_STANDOFF;
        }
        if (z > region.maxZ() + 1.0d - EPSILON) {
            sz = region.maxZ() + 1.0d - PEARL_STANDOFF;
        } else if (z < region.minZ() + EPSILON) {
            sz = region.minZ() + PEARL_STANDOFF;
        }
        Location dest = new Location(impact.getWorld(), sx, impact.getY(), sz,
                shooter.getYaw(), shooter.getPitch());
        impact.getWorld().spawnParticle(Particle.PORTAL, impact, 20, 0.2, 0.2, 0.2, 0.5);
        impact.getWorld().playSound(impact, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        shooter.teleport(dest);
    }

    /** The wind-charge burst, at the wall face: shove + GUST visual + wind-burst sound. */
    private void windBurst(Location impact) {
        impact.getWorld().spawnParticle(Particle.GUST, impact, 1);
        impact.getWorld().playSound(impact, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        for (Entity entity : impact.getWorld().getNearbyEntities(impact,
                WIND_BURST_RADIUS, WIND_BURST_RADIUS, WIND_BURST_RADIUS,
                e -> e instanceof LivingEntity && !(e instanceof org.bukkit.entity.ArmorStand))) {
            Vector away = entity.getLocation().toVector().subtract(impact.toVector());
            away.setY(0);
            if (away.lengthSquared() < 1.0e-4d) {
                away = new Vector(0, 1, 0);
            } else {
                away.normalize().multiply(WIND_BURST_KB);
                away.setY(WIND_BURST_KB_Y);
            }
            Vector current = entity.getVelocity();
            entity.setVelocity(new Vector(
                    current.getX() + away.getX(), away.getY(), current.getZ() + away.getZ()));
        }
    }
}
