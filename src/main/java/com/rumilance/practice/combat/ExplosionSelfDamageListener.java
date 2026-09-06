package com.rumilance.practice.combat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * <p>Vanilla never damages the entity that is an explosion's <em>source</em>, and modern
 * Minecraft attributes crystal blasts to the detonator, creeper blasts to their igniter and
 * TNT blasts to the igniter. The net effect: punching your own crystal, igniting your own
 * TNT or creeper, or detonating your own respawn anchor / bed deals you <strong>no</strong>
 * damage — which breaks crystal-PvP fundamentals (self-blast damage/knockback is part of the
 * meta). Practice TNT ({@code World.createExplosion(..., source)}) has the same hole.</p>
 *
 * <p>Everything is covered: crystals, TNT (following primed-TNT ignition chains back to the
 * original player), creepers, plugin blasts, and block explosions (respawn anchors and beds
 * fire {@link BlockExplodeEvent} — the last right-clicker of the block is remembered briefly
 * and treated as the source).</p>
 *
 * <p>For every blast we resolve the source player, pre-compute the exact vanilla damage +
 * knockback that entity would have taken ({@link ExplosionPhysics}: raw
 * {@code (impact² + impact)/2 · 7 · (2·power) + 1}, exposure over the vanilla sample grid of the
 * player's bounding box — 3 x 5 x 3 = 45 rays standing — and knockback
 * {@code impact · multiplier · (1 - explosion_knockback_resistance)} along centre→eyes),
 * and apply it one tick later — but ONLY when vanilla
 * really skipped them for THIS blast, so a player who is legitimately hit by two crystals in the
 * same tick still gets both self-blasts and nobody is ever double-damaged.</p>
 *
 * <p>The applied damage is attributed to the player themselves
 * ({@code player.damage(amount, player)}), so the whole vanilla pipeline (difficulty, armor,
 * protection, absorption, i-frames) and our kill tracking treat it as a genuine self-inflicted
 * blast.</p>
 */
public final class ExplosionSelfDamageListener implements Listener {

    /** Charged creeper blast power (vanilla: 6, same as an end crystal). */
    private static final float CHARGED_CREEPER_POWER = 6.0f;
    /** How far back a primed-TNT ignition chain is followed to find the original player. */
    private static final int TNT_CHAIN_MAX = 8;
    /** How long a crystal keeps its last recorded detonator. */
    private static final long DETONATOR_TTL_MS = 5_000L;
    /** How long an anchor/bed remembers its last right-clicker. */
    private static final long BLOCK_INTERACT_TTL_MS = 10_000L;

    private final Plugin plugin;

    private record Detonator(UUID playerId, long atMillis) {
    }

    private final Map<UUID, Detonator> crystalDetonators = new ConcurrentHashMap<>();
    /** Last right-clicker of an anchor/bed, keyed "world|x|y|z" — BlockExplodeEvent source. */
    private final Map<String, Detonator> blockInteractions = new ConcurrentHashMap<>();
    /** Plugin-created blasts ({@code createExplosion}) carry no entity — match by tick+spot. */
    private final Deque<PluginBlast> pluginBlasts = new ArrayDeque<>();
    /**
     * Players vanilla already damaged with THIS blast (double-damage guard), keyed
     * {@code player -> blastKey}. A tick-wide flag used to suppress a second crystal in the same
     * tick, which is a real crystal-PvP pattern (double-pop), so the guard is per blast.
     */
    private final Map<UUID, BlastHit> vanillaBlastHits = new ConcurrentHashMap<>();
    /**
     * Last tick a player took a BLOCK explosion (bed / respawn anchor). Those events carry no
     * entity, so a block blast cannot be matched by identity - a short window is used instead.
     */
    private final Map<UUID, Integer> vanillaBlockBlastTick = new ConcurrentHashMap<>();

    private record PluginBlast(int tick, String world, double x, double y, double z,
                               float power, UUID source) {
    }

    private record PendingBlast(int tick, String blastKey, UUID playerId, World world,
                                double x, double y, double z, float power, boolean blockBlast) {
    }

    /** One blast vanilla actually applied to a player, remembered for a few ticks. */
    private record BlastHit(String blastKey, int tick) {
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

    /** Marks players vanilla actually damaged with a given blast (double-damage guard). */
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
        int tick = org.bukkit.Bukkit.getCurrentTick();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            vanillaBlockBlastTick.put(player.getUniqueId(), tick);
            if (vanillaBlockBlastTick.size() > 256) {
                int cutoff = tick - 100;
                vanillaBlockBlastTick.values().removeIf(t -> t < cutoff);
            }
        }
        String key = blastKeyOf(event);
        if (key == null) {
            return;
        }
        vanillaBlastHits.put(player.getUniqueId(), new BlastHit(key, tick));
        if (vanillaBlastHits.size() > 256) {
            int cutoff = tick - 100;
            vanillaBlastHits.values().removeIf(hit -> hit.tick() < cutoff);
        }
    }

    /**
     * Identifies the blast behind a damage event so the guard is per-explosion: two crystals
     * popping in the same tick are two different blasts and both may need self-damage restored.
     */
    private static String blastKeyOf(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() != null) {
            return "e:" + byEntity.getDamager().getUniqueId();
        }
        // Block explosions (bed / respawn anchor) carry no entity: fall back to the victim's own
        // position + tick, which is unique enough because a block blast hits every player once.
        Location at = event.getEntity().getLocation();
        return "b:" + at.getWorld().getName() + "|" + at.getBlockX() + "|" + at.getBlockY()
                + "|" + at.getBlockZ() + "|" + org.bukkit.Bukkit.getCurrentTick();
    }

    /**
     * Remembers who last right-clicked a respawn anchor or bed — those blocks explode via
     * {@link BlockExplodeEvent}, which carries no entity and therefore no vanilla source.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!isExplosiveBlock(event.getClickedBlock())) {
            return;
        }
        blockInteractions.put(blockKey(event.getClickedBlock()),
                new Detonator(event.getPlayer().getUniqueId(), System.currentTimeMillis()));
        if (blockInteractions.size() > 256) {
            long cutoff = System.currentTimeMillis() - BLOCK_INTERACT_TTL_MS;
            blockInteractions.values().removeIf(d -> d.atMillis() < cutoff);
        }
    }

    /** Respawn anchors and beds explode (no entity) — resolve the source from interactions. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isExplosiveBlock(event.getBlock())) {
            return;
        }
        Detonator interaction = blockInteractions.remove(blockKey(event.getBlock()));
        if (interaction == null
                || System.currentTimeMillis() - interaction.atMillis() > BLOCK_INTERACT_TTL_MS) {
            return;
        }
        Location center = event.getBlock().getLocation().add(0.5d, 0.5d, 0.5d);
        float power = event.getBlock().getType() == org.bukkit.Material.RESPAWN_ANCHOR
                ? ExplosionPhysics.ANCHOR_POWER : ExplosionPhysics.BED_POWER;
        scheduleSelfBlast(interaction.playerId(), center, power,
                "blk:" + blockKey(event.getBlock()), true);
    }

    private static boolean isExplosiveBlock(org.bukkit.block.Block block) {
        org.bukkit.Material type = block.getType();
        return type == org.bukkit.Material.RESPAWN_ANCHOR || block.getBlockData() instanceof Bed;
    }

    private static String blockKey(org.bukkit.block.Block block) {
        return block.getWorld().getName() + "|" + block.getX() + "|" + block.getY() + "|" + block.getZ();
    }

    /** Central dispatch: resolves the source player and schedules the self-damage tick. */
    private void scheduleSelfBlast(UUID sourceId, Location center, float power, String blastKey) {
        scheduleSelfBlast(sourceId, center, power, blastKey, false);
    }

    private void scheduleSelfBlast(UUID sourceId, Location center, float power, String blastKey,
                                   boolean blockBlast) {
        Player source = org.bukkit.Bukkit.getPlayer(sourceId);
        if (source == null || !source.isOnline() || source.getWorld() != center.getWorld()) {
            return;
        }
        PendingBlast pending = new PendingBlast(
                org.bukkit.Bukkit.getCurrentTick(), blastKey, sourceId, center.getWorld(),
                center.getX(), center.getY(), center.getZ(), power, blockBlast);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> applyIfSkipped(pending), 1L);
    }

    /** Vanilla damages entities AFTER the explode event, so we can resolve the blast here. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        UUID sourceId = null;
        float power = ExplosionPhysics.CRYSTAL_POWER;
        String blastKey = "e:" + (entity == null ? "plugin" : entity.getUniqueId().toString());
        if (entity instanceof EnderCrystal) {
            Detonator detonator = crystalDetonators.remove(entity.getUniqueId());
            if (detonator != null && System.currentTimeMillis() - detonator.atMillis() <= DETONATOR_TTL_MS) {
                sourceId = detonator.playerId();
            }
        } else if (entity instanceof TNTPrimed tnt) {
            // Follow primed-TNT ignition chains back to the player who started them.
            Entity current = tnt;
            for (int hop = 0; hop < TNT_CHAIN_MAX && current instanceof TNTPrimed primed; hop++) {
                if (primed.getSource() instanceof Player player) {
                    sourceId = player.getUniqueId();
                    break;
                }
                current = primed.getSource();
            }
            power = ExplosionPhysics.TNT_POWER;
        } else if (entity instanceof Creeper creeper) {
            if (creeper.getIgniter() instanceof Player player) {
                sourceId = player.getUniqueId();
            }
            // A charged creeper has the same power as an end crystal (6); a normal one 3.
            power = creeper.isPowered() ? CHARGED_CREEPER_POWER : ExplosionPhysics.CREEPER_POWER;
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
                        blastKey = "p:" + blast.world() + "|" + blast.x() + "|" + blast.y() + "|"
                                + blast.z() + "|" + blast.tick();
                        it.remove();
                        break;
                    }
                }
            }
        }
        if (sourceId == null) {
            return;
        }
        scheduleSelfBlast(sourceId, event.getLocation(), power, blastKey);
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
        BlastHit hit = vanillaBlastHits.get(player.getUniqueId());
        if (hit != null && hit.blastKey().equals(blast.blastKey()) && hit.tick() >= blast.tick()) {
            // Vanilla dealt THIS blast to them after all — nothing was skipped.
            return;
        }
        if (blast.blockBlast()) {
            Integer blockTick = vanillaBlockBlastTick.get(player.getUniqueId());
            if (blockTick != null && blockTick >= blast.tick()
                    && org.bukkit.Bukkit.getCurrentTick() - blockTick <= 2) {
                // Vanilla bed / anchor damage landed (no entity to match on): don't double it.
                return;
            }
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().equals(blast.world())) {
            return;
        }
        // Vanilla measures from the explosion centre to the entity's FEET position.
        double dx = loc.getX() - blast.x();
        double dy = loc.getY() - blast.y();
        double dz = loc.getZ() - blast.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!ExplosionPhysics.inRadius(distance, blast.power())) {
            return;
        }
        // Exposure may legitimately be 0 (fully walled off): vanilla still hurts everything inside
        // the radius for the formula's + 1, it just applies no knockback. Skipping here instead
        // would make a blocked own-blast free, which is not how the vanilla trade-off plays.
        double exposure = exposure(blast.world(), blast.x(), blast.y(), blast.z(), player);
        double impact = ExplosionPhysics.impact(distance, blast.power(), exposure);
        double damage = ExplosionPhysics.rawDamage(impact, blast.power());
        if (damage <= 0.0d) {
            return;
        }
        if (impact > 0.0d) {
            applyKnockback(player, blast, impact);
        }
        // Self-attributed: the player is their own damager, so every combat flow sees a
        // genuine self-inflicted blast (kill credit, death messages, totem handling).
        player.damage(damage, player);
    }

    /**
     * Vanilla {@code Explosion#finalizeExplosion} knockback: take the vector from the blast centre
     * to the player's EYES, scale it to {@code impact * knockbackMultiplier *
     * (1 - explosion_knockback_resistance)} and ADD it to the current velocity (Blast Protection
     * grants 0.15 resistance per level through that attribute since 1.21.2). The distance used for
     * the impact itself is measured to the FEET, exactly like vanilla.
     */
    private void applyKnockback(Player player, PendingBlast blast, double impact) {
        double dx = player.getEyeLocation().getX() - blast.x();
        double dy = player.getEyeLocation().getY() - blast.y();
        double dz = player.getEyeLocation().getZ() - blast.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6d) {
            return;
        }
        double magnitude = ExplosionPhysics.knockbackMagnitude(impact,
                ExplosionPhysics.KNOCKBACK_MULTIPLIER, explosionKnockbackResistance(player));
        if (magnitude <= 0.0d) {
            return;
        }
        player.setVelocity(player.getVelocity().add(new Vector(
                dx / len * magnitude, dy / len * magnitude, dz / len * magnitude)));
    }

    private static double explosionKnockbackResistance(Player player) {
        try {
            AttributeInstance attribute = player.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
            return attribute == null ? 0.0d : attribute.getValue();
        } catch (RuntimeException | NoClassDefFoundError | NoSuchFieldError e) {
            // Older API without the attribute: no resistance data, vanilla knockback.
            return 0.0d;
        }
    }

    /**
     * Vanilla exposure ({@code Explosion#getSeenPercent}): the bounding box is sampled on a
     * {@code ceil(2 * size + 1)} grid per axis — 3 x 5 x 3 = 45 rays for a standing player — and
     * a ray is cast between the blast centre and each sample point. The exposure is the fraction
     * of rays that no collision-shaped block stops. The samples start at the box minimum: vanilla
     * computes a centring offset and then never applies it, which is the known directional bias of
     * explosion exposure (MC-232355).
     */
    private double exposure(World world, double x, double y, double z, Player player) {
        Location origin = new Location(world, x, y, z);
        Location loc = player.getLocation();
        double width = player.getWidth();
        double height = player.getHeight();
        int stepsX = ExplosionPhysics.sampleSteps(width);
        int stepsY = ExplosionPhysics.sampleSteps(height);
        int stepsZ = ExplosionPhysics.sampleSteps(width);
        if (stepsX <= 0 || stepsY <= 0 || stepsZ <= 0) {
            return 0.0d; // vanilla: a non-positive step yields zero exposure
        }
        double minX = loc.getX() - width / 2.0d;
        double minY = loc.getY();
        double minZ = loc.getZ() - width / 2.0d;

        int total = 0;
        int clear = 0;
        for (int ix = 0; ix < stepsX; ix++) {
            double px = ExplosionPhysics.sampleCoordinate(minX, width, ix);
            for (int iy = 0; iy < stepsY; iy++) {
                double py = ExplosionPhysics.sampleCoordinate(minY, height, iy);
                for (int iz = 0; iz < stepsZ; iz++) {
                    double pz = ExplosionPhysics.sampleCoordinate(minZ, width, iz);
                    total++;
                    if (rayIsClear(world, origin, px, py, pz)) {
                        clear++;
                    }
                }
            }
        }
        return total == 0 ? 0.0d : (double) clear / total;
    }

    /** One vanilla exposure ray: {@code ClipContext.Block.COLLIDER} from the centre to a sample. */
    private boolean rayIsClear(World world, Location origin, double px, double py, double pz) {
        double dx = px - origin.getX();
        double dy = py - origin.getY();
        double dz = pz - origin.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4d) {
            return true;
        }
        Vector direction = new Vector(dx / length, dy / length, dz / length);
        RayTraceResult hit = world.rayTraceBlocks(
                origin, direction, length, FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }
}
