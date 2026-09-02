package com.rumilance.practice.combat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores vanilla-style <strong>self-damage</strong> from explosions.
 *
 * <p>Vanilla never damages the entity that is an explosion's <em>source</em> (Paper #11167 —
 * "works as intended"), and modern Minecraft attributes end-crystal blasts to the player who
 * detonated them and creeper blasts to their igniter. The net effect on 1.21.x: punching your
 * own crystal, or detonating a creeper you ignited, deals you <strong>no</strong> damage —
 * which breaks crystal-PvP fundamentals (self-blast damage/knockback is part of the meta).
 * Practice TNT ({@code World.createExplosion(..., source)}) has the same hole.</p>
 *
 * <p>For every blast we resolve the source player, pre-compute the exact vanilla damage +
 * knockback that entity would have taken, and apply it one tick later — but ONLY when vanilla
 * really skipped them (players who took a normal blast this tick are marked and skipped), so
 * this can never double-damage on a server/version where vanilla does apply it.</p>
 */
public final class ExplosionSelfDamageListener implements Listener {

    /** Crystal blast power (vanilla EndCrystal explosion). */
    private static final float CRYSTAL_POWER = 6.0f;
    /** How long a crystal keeps its last recorded detonator. */
    private static final long DETONATOR_TTL_MS = 5_000L;
    /** Exposure raycast sample points on the player body. */
    private static final double[][] SAMPLE_OFFSETS = {
            {0.0d, 0.2d, 0.0d},
            {0.0d, 0.9d, 0.0d},
            {0.0d, 1.62d, 0.0d},
            {0.3d, 0.9d, 0.0d},
            {-0.3d, 0.9d, 0.0d},
            {0.0d, 0.9d, 0.3d},
            {0.0d, 0.9d, -0.3d},
    };

    private final Plugin plugin;
    private record Detonator(UUID playerId, long atMillis) {
    }

    private final Map<UUID, Detonator> crystalDetonators = new ConcurrentHashMap<>();
    /** Plugin-created blasts ({@code createExplosion}) carry no entity — match by tick+spot. */
    private final Deque<PluginBlast> pluginBlasts = new ArrayDeque<>();
    /** Players vanilla already damaged with an explosion this tick (double-damage guard). */
    private final Map<UUID, Integer> vanillaBlastTick = new ConcurrentHashMap<>();

    private record PluginBlast(int tick, String world, double x, double y, double z,
                               float power, UUID source) {
    }

    private record PendingBlast(int tick, UUID playerId, World world, double x, double y, double z,
                                float power) {
    }

    public ExplosionSelfDamageListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Records who is about to trigger a plugin-created explosion (call BEFORE createExplosion). */
    public void rememberPluginBlast(Location at, float power, UUID sourcePlayerId) {
        if (at == null || at.getWorld() == null || sourcePlayerId == null) {
            return;
        }
        synchronized (pluginBlasts) {
            pluginBlasts.addLast(new PluginBlast(
                    org.bukkit.Bukkit.getCurrentTick(), at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ(), power, sourcePlayerId));
            while (pluginBlasts.size() > 64) {
                pluginBlasts.removeFirst();
            }
        }
    }

    /** Tracks the last player to hit a crystal — vanilla attributes the blast to them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        UUID attackerId = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            attackerId = player.getUniqueId();
        } else if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                attackerId = player.getUniqueId();
            }
        } else if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            attackerId = player.getUniqueId();
        }
        if (attackerId == null) {
            return;
        }
        crystalDetonators.put(crystal.getUniqueId(), new Detonator(attackerId, System.currentTimeMillis()));
        if (crystalDetonators.size() > 512) {
            long cutoff = System.currentTimeMillis() - DETONATOR_TTL_MS;
            crystalDetonators.values().removeIf(d -> d.atMillis() < cutoff);
        }
    }

    /** Marks players vanilla actually damaged this tick (double-damage guard). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        vanillaBlastTick.put(player.getUniqueId(), org.bukkit.Bukkit.getCurrentTick());
    }

    /** Vanilla damages entities AFTER the explode event, so we can resolve the blast here. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        UUID sourceId = null;
        float power = CRYSTAL_POWER;
        if (entity instanceof EnderCrystal) {
            Detonator detonator = crystalDetonators.remove(entity.getUniqueId());
            if (detonator != null && System.currentTimeMillis() - detonator.atMillis() <= DETONATOR_TTL_MS) {
                sourceId = detonator.playerId();
            }
        } else if (entity instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player player) {
                sourceId = player.getUniqueId();
            }
            power = 4.0f;
        } else if (entity instanceof Creeper creeper) {
            if (creeper.getIgniter() instanceof Player player) {
                sourceId = player.getUniqueId();
            }
            power = creeper.isPowered() ? 6.0f : 3.0f;
        } else if (entity == null) {
            // Plugin-created explosion (createExplosion fires with no entity).
            int tick = org.bukkit.Bukkit.getCurrentTick();
            Location at = event.getLocation();
            synchronized (pluginBlasts) {
                Iterator<PluginBlast> it = pluginBlasts.iterator();
                while (it.hasNext()) {
                    PluginBlast blast = it.next();
                    if (blast.tick() < tick) {
                        it.remove();
                        continue;
                    }
                    if (!blast.world().equals(at.getWorld().getName())) {
                        continue;
                    }
                    double dx = blast.x() - at.getX();
                    double dy = blast.y() - at.getY();
                    double dz = blast.z() - at.getZ();
                    if (dx * dx + dy * dy + dz * dz <= 1.0d) {
                        sourceId = blast.source();
                        power = blast.power();
                        it.remove();
                        break;
                    }
                }
            }
        }
        if (sourceId == null) {
            return;
        }
        Player source = org.bukkit.Bukkit.getPlayer(sourceId);
        if (source == null || !source.isOnline() || source.getWorld() != event.getLocation().getWorld()) {
            return;
        }
        Location center = event.getLocation();
        PendingBlast pending = new PendingBlast(
                org.bukkit.Bukkit.getCurrentTick(), sourceId, center.getWorld(),
                center.getX(), center.getY(), center.getZ(), power);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> applyIfSkipped(pending), 1L);
    }

    private void applyIfSkipped(PendingBlast blast) {
        Player player = org.bukkit.Bukkit.getPlayer(blast.playerId());
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        Integer damagedTick = vanillaBlastTick.get(player.getUniqueId());
        if (damagedTick != null && damagedTick >= blast.tick()) {
            // Vanilla dealt the blast to them after all — nothing was skipped.
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().equals(blast.world())) {
            return;
        }
        double dist = Math.sqrt(
                (loc.getX() - blast.x()) * (loc.getX() - blast.x())
                        + (loc.getY() - blast.y()) * (loc.getY() - blast.y())
                        + (loc.getZ() - blast.z()) * (loc.getZ() - blast.z()));
        float diameter = blast.power() * 2.0f;
        double scaled = dist / diameter;
        if (scaled > 1.0d) {
            return;
        }
        double exposure = exposure(blast.world(), blast.x(), blast.y(), blast.z(), player);
        if (exposure <= 0.0d) {
            return;
        }
        double impact = (1.0d - scaled) * exposure;
        if (impact <= 0.0d) {
            return;
        }
        double damage = Math.max(0.0d,
                (int) ((impact * impact + impact) / 2.0d * 7.0d * diameter + 1.0d));
        if (damage <= 0.0d) {
            return;
        }
        // Direction uses the player's eye height, exactly like vanilla explosion knockback.
        double dx = loc.getX() - blast.x();
        double dy = player.getEyeLocation().getY() - blast.y();
        double dz = loc.getZ() - blast.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Vector knockback = new Vector();
        if (len > 1.0e-6d) {
            double kbScale = impact * (1.0d - 0.15d * blastProtectionLevel(player));
            if (kbScale > 0.0d) {
                knockback = new Vector(dx / len * kbScale, dy / len * kbScale, dz / len * kbScale);
            }
        }
        // Knockback first so a surviving victim is pushed exactly like vanilla; the damage
        // call below may eliminate them (team elim / opponent win) mid-way.
        if (knockback.lengthSquared() > 0.0d) {
            player.setVelocity(player.getVelocity().add(knockback));
        }
        // No damager: the blast is attributed as self-inflicted/environmental, which every
        // practice flow already handles (own-crystal deaths are opponent wins / plain deaths).
        player.damage(damage);
    }

    /** Fraction of sample rays from the blast centre to the player body that are unobstructed. */
    private double exposure(World world, double x, double y, double z, Player player) {
        Location origin = new Location(world, x, y, z);
        double px = player.getLocation().getX();
        double py = player.getLocation().getY();
        double pz = player.getLocation().getZ();
        int clear = 0;
        for (double[] offset : SAMPLE_OFFSETS) {
            Location target = new Location(world, px + offset[0], py + offset[1], pz + offset[2]);
            double maxDist = origin.distance(target);
            if (maxDist < 1.0e-4d) {
                clear++;
                continue;
            }
            Vector direction = target.toVector().subtract(origin.toVector());
            RayTraceResult hit = world.rayTraceBlocks(
                    origin, direction, maxDist + 0.1d, FluidCollisionMode.NEVER, true);
            if (hit == null || hit.getHitBlock() == null) {
                clear++;
            }
        }
        return (double) clear / SAMPLE_OFFSETS.length;
    }

    private int blastProtectionLevel(Player player) {
        int max = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) {
                continue;
            }
            try {
                int level = armor.getEnchantmentLevel(Enchantment.BLAST_PROTECTION);
                if (level > max) {
                    max = level;
                }
            } catch (RuntimeException ignored) {
                // Enchantment registry hiccup: fall back to no reduction.
            }
        }
        return max;
    }
}
