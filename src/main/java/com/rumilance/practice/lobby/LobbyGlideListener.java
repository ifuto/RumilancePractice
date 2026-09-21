package com.rumilance.practice.lobby;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gliding in the hub: the firework-style boost and the mace-smash landing.
 *
 * <p>While gliding inside the lobby, a right-click (on a block or on nothing at all)
 * fires a rocket boost along the line of sight. It works once every
 * {@link #BOOST_COOLDOWN_MS} — and during the cooldown the click produces <em>nothing</em>:
 * no sound, no particles, no chat or action bar line. The click is consumed either way, so
 * gliding players never open a chest or use an item by accident.</p>
 *
 * <p>Landing after a fall of {@link #SMASH_MIN_FALL} blocks or more plays the mace smash:
 * a flat ring of crits, a dust puff and the 1.21 mace impact sound (the heavy variant for
 * really long falls).</p>
 */
public final class LobbyGlideListener implements Listener {

    /** ロケット花火風の加速の間隔。クールダウン中の右クリックは一切出力しない。 */
    private static final long BOOST_COOLDOWN_MS = 2000L;
    /** Boost impulse added along the look direction, blocks/tick. */
    private static final double BOOST_POWER = 1.15d;
    /** Hard ceiling so repeated boosts cannot run away to an unplayable speed. */
    private static final double MAX_SPEED = 2.4d;
    /** Fall distance that counts as 高所. */
    private static final double SMASH_MIN_FALL = 5.0d;
    /** Above this the heavy smash variant plays. */
    private static final double SMASH_HEAVY_FALL = 12.0d;

    private final Plugin plugin;
    private final LobbyWearService lobbyWear;
    private final Map<UUID, Long> lastBoostAt = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastFallDistance = new ConcurrentHashMap<>();

    public LobbyGlideListener(Plugin plugin, LobbyWearService lobbyWear) {
        this.plugin = plugin;
        this.lobbyWear = lobbyWear;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isGliding() || !lobbyWear.isInLobby(player)) {
            return;
        }
        // The click belongs to the boost: no container opens, no item is used while gliding.
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        long now = System.currentTimeMillis();
        Long last = lastBoostAt.get(player.getUniqueId());
        if (last != null && now - last < BOOST_COOLDOWN_MS) {
            return; // cooling down: completely silent
        }
        lastBoostAt.put(player.getUniqueId(), now);

        Vector direction = player.getLocation().getDirection();
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }
        Vector velocity = player.getVelocity().add(direction.normalize().multiply(BOOST_POWER));
        if (velocity.length() > MAX_SPEED) {
            velocity = velocity.normalize().multiply(MAX_SPEED);
        }
        player.setVelocity(velocity);

        Location at = player.getEyeLocation();
        player.getWorld().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.1f);
        player.getWorld().spawnParticle(Particle.CLOUD, at, 16, 0.25, 0.25, 0.25, 0.02);
        player.getWorld().spawnParticle(Particle.CRIT, at, 10, 0.25, 0.25, 0.25, 0.05);
    }

    /**
     * Fall tracking: Bukkit resets {@code fallDistance} on landing, so "it was big and now it
     * is zero" is the landing itself. Only lobby landings get the show.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        float previous = lastFallDistance.getOrDefault(id, 0f);
        float current = player.getFallDistance();
        lastFallDistance.put(id, current);
        if (previous < SMASH_MIN_FALL || current > 0.01f) {
            return;
        }
        lastFallDistance.put(id, 0f);
        if (!lobbyWear.isInLobby(player)) {
            return;
        }
        smash(player, previous);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastBoostAt.remove(event.getPlayer().getUniqueId());
        lastFallDistance.remove(event.getPlayer().getUniqueId());
    }

    /** Mace-smash landing: crit ring + dust puff + the 1.21 mace impact sound. */
    private void smash(Player player, float fallDistance) {
        Location center = player.getLocation();
        org.bukkit.World world = player.getWorld();
        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2.0 / 24.0);
            Location ring = center.clone().add(Math.cos(angle) * 1.3, 0.15, Math.sin(angle) * 1.3);
            world.spawnParticle(Particle.CRIT, ring, 1, 0.02, 0.02, 0.02, 0.0);
        }
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.2, 0), 26, 0.55, 0.18, 0.55, 0.02);
        world.playSound(center,
                fallDistance >= SMASH_HEAVY_FALL
                        ? Sound.ITEM_MACE_SMASH_GROUND_HEAVY
                        : Sound.ITEM_MACE_SMASH_GROUND,
                1.0f, 1.0f);
    }

    /** Exposed for tests/tools: how long a boost stays on cooldown. */
    public static long boostCooldownMillis() {
        return BOOST_COOLDOWN_MS;
    }

    /** Exposed so the bootstrap can log/verify the wiring without touching internals. */
    public Plugin plugin() {
        return plugin;
    }
}
