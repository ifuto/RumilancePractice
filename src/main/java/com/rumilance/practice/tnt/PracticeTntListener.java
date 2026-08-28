package com.rumilance.practice.tnt;

import com.destroystokyo.paper.event.entity.CreeperIgniteEvent;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Practice TNT / creeper fuse. TNT primes on place (1s). Egg creepers ignite immediately
 * for 1s, stay ignited even if thrown out of sight, and are never charged.
 */
public final class PracticeTntListener implements Listener {

    private static final double NEARBY_COMBATANT_RANGE = 8.0d;

    private final PracticeTntSettings settings;
    private final MatchService matchService;
    private final FfaService ffaService;
    private final Plugin plugin;
    private final NamespacedKey lockedFuseKey;

    public PracticeTntListener(PracticeTntSettings settings, MatchService matchService, FfaService ffaService,
                               Plugin plugin) {
        this.settings = settings;
        this.matchService = matchService;
        this.ffaService = ffaService;
        this.plugin = plugin;
        this.lockedFuseKey = new NamespacedKey(plugin, "locked_creeper_fuse");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPlace(BlockPlaceEvent event) {
        if (!settings.enabled() || event.getBlock().getType() != Material.TNT) {
            return;
        }
        if (!isCombatant(event.getPlayer())) {
            return;
        }
        Location spawnAt = event.getBlock().getLocation().add(
                PracticeTnt.CENTER_OFFSET, 0.0d, PracticeTnt.CENTER_OFFSET);
        World world = spawnAt.getWorld();
        if (world == null) {
            return;
        }
        if (world.getDifficulty() != org.bukkit.Difficulty.HARD) {
            world.setDifficulty(org.bukkit.Difficulty.HARD);
        }
        Player placer = event.getPlayer();
        int fuse = settings.tntFuseTicks();
        world.spawn(spawnAt, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(fuse);
            tnt.setSource(placer);
        });
        event.getBlock().setType(Material.AIR, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreeperSpawn(CreatureSpawnEvent event) {
        if (!settings.enabled() || !settings.creeperFromEgg()) {
            return;
        }
        if (!(event.getEntity() instanceof Creeper creeper)) {
            return;
        }
        if (!PracticeTnt.isSpawnerEggReason(event.getSpawnReason().name())) {
            return;
        }
        Player combatant = nearbyCombatant(creeper.getLocation());
        if (combatant == null) {
            return;
        }
        lockFuse(creeper, combatant);
    }

    /**
     * Vanilla SwellGoal un-ignites when the target is lost (thrown behind a wall, too far,
     * no line of sight). Locked practice creepers must keep swelling until they explode.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreeperIgnite(CreeperIgniteEvent event) {
        if (!isLocked(event.getEntity()) || event.isIgnited()) {
            return;
        }
        event.setCancelled(true);
        event.setIgnited(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> com.rumilance.practice.util.MaterialFlags.isGlass(block.getType()));
        recordFfaExplosion(event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        // Respawn-anchor / bed blasts  Ekeep glass intact; record FFA originals for reset.
        event.blockList().removeIf(block -> com.rumilance.practice.util.MaterialFlags.isGlass(block.getType()));
        recordFfaExplosion(event.getBlock().getLocation(), event.blockList());
    }

    /**
     * Snapshot originals on the main thread before vanilla applies the break list.
     * Must stay synchronous — async put races with {@link FfaService#reset}.
     */
    private void recordFfaExplosion(Location at, List<Block> blocks) {
        if (at == null || blocks == null || blocks.isEmpty() || ffaService == null) {
            return;
        }
        for (Block block : blocks) {
            if (block == null) {
                continue;
            }
            ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
        }
    }

    private void lockFuse(Creeper creeper, Player igniter) {
        int maxFuse = Math.max(1, settings.creeperFuseTicks());
        creeper.getPersistentDataContainer().set(lockedFuseKey, PersistentDataType.BYTE, (byte) 1);
        creeper.setPowered(false);
        creeper.setMaxFuseTicks(maxFuse);
        creeper.setFuseTicks(0);
        creeper.ignite(igniter);
        creeper.setIgnited(true);
        // Goals (including SwellGoal.stop ↁEun-ignite) do not run while unaware.
        // Physics / knockback / fishing-rod velocity still apply.
        creeper.setAware(false);
        int delay = PracticeTnt.creeperExplodeDelayTicks(maxFuse, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (creeper.isValid() && !creeper.isDead()) {
                // Vanilla Hard-difficulty creeper blast. Custom logic is block-only (glass / FFA
                // restore); entity damage must stay vanilla  EcreateExplosion scales with difficulty.
                Location at = creeper.getLocation();
                World world = at.getWorld();
                Player source = creeper.getIgniter() instanceof Player p ? p : igniter;
                float power = creeper.isPowered() ? 6.0f : 3.0f;
                creeper.remove();
                if (world != null) {
                    if (world.getDifficulty() != org.bukkit.Difficulty.HARD) {
                        world.setDifficulty(org.bukkit.Difficulty.HARD);
                    }
                    world.createExplosion(at, power, false, true, source);
                }
            }
        }, delay);
    }

    private boolean isLocked(Creeper creeper) {
        return creeper.getPersistentDataContainer().has(lockedFuseKey, PersistentDataType.BYTE);
    }

    private boolean isCombatant(Player player) {
        if (ffaService.isInFfa(player.getUniqueId())) {
            return true;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        return session != null && session.state() == MatchState.ACTIVE;
    }

    private Player nearbyCombatant(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        double rangeSq = NEARBY_COMBATANT_RANGE * NEARBY_COMBATANT_RANGE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != world) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= rangeSq && isCombatant(player)) {
                return player;
            }
        }
        return null;
    }
}
