package com.rumilance.practice.ffa;

import com.rumilance.practice.combat.PracticeDeath;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.KitBlockRules;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.PlayerPlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * FFA combat loop: lethal handling, respawn, kit rules, region pushback, block diffs.
 */
public final class FfaListener implements Listener {

    private final FfaService ffaService;
    private final KitService kitService;
    private final PlayerStateManager stateManager;
    private final com.rumilance.practice.combat.CombatNetTracker combatNet;
    private final com.rumilance.practice.tnt.PracticeTntSettings practiceTnt;
    private final PlayerPlacedBlockTracker playerPlacedBlocks;

    public FfaListener(FfaService ffaService, KitService kitService, PlayerStateManager stateManager) {
        this(ffaService, kitService, stateManager, null, null, null);
    }

    public FfaListener(FfaService ffaService, KitService kitService, PlayerStateManager stateManager,
                       com.rumilance.practice.combat.CombatNetTracker combatNet,
                       com.rumilance.practice.tnt.PracticeTntSettings practiceTnt) {
        this(ffaService, kitService, stateManager, combatNet, practiceTnt, null);
    }

    public FfaListener(FfaService ffaService, KitService kitService, PlayerStateManager stateManager,
                       com.rumilance.practice.combat.CombatNetTracker combatNet,
                       com.rumilance.practice.tnt.PracticeTntSettings practiceTnt,
                       PlayerPlacedBlockTracker playerPlacedBlocks) {
        this.ffaService = ffaService;
        this.kitService = kitService;
        this.stateManager = stateManager;
        this.combatNet = combatNet;
        this.practiceTnt = practiceTnt;
        this.playerPlacedBlocks = playerPlacedBlocks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!ffaService.isInFfa(victim.getUniqueId())) {
            return;
        }
        KitDefinition kit = kitOf(victim.getUniqueId());
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            applyKitCombatRules(byEntity, victim, kit);
            UUID attackerId = resolveKiller(byEntity);
            if (attackerId != null) {
                ffaService.tagCombat(victim.getUniqueId(), attackerId);
            }
        }
        if (PracticeDeath.isInResurrectGrace(victim)) {
            event.setCancelled(true);
            event.setDamage(0);
            return;
        }
        if (PracticeDeath.shouldDeferTotemToVanilla(victim, kit, event)) {
            return;
        }
        double remaining = PracticeDeath.remainingAfter(victim, event);
        if (remaining > 0) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
        UUID killerId = resolveKiller(event);
        ffaService.handleLethal(victim, killerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (ffaService.isInFfa(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            event.getDrops().clear();
            event.setKeepInventory(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!ffaService.isInFfa(event.getPlayer().getUniqueId())) {
            return;
        }
        org.bukkit.Location dest = ffaService.respawnLocation(event.getPlayer());
        if (dest != null && dest.getWorld() != null) {
            event.setRespawnLocation(LocationUtil.safeTeleportLocation(dest, event.getPlayer()));
        }
        org.bukkit.plugin.Plugin plugin = JavaPlugin.getProvidingPlugin(FfaListener.class);
        if (plugin != null) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> ffaService.respawn(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null
                || (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        if (!ffaService.isInFfa(player.getUniqueId())) {
            return;
        }
        ffaService.arenaOf(player.getUniqueId()).flatMap(ffaService::get).ifPresent(arena -> {
            if (!arena.region().containsHorizontal(event.getTo())) {
                com.rumilance.practice.util.PlayAreaWall.constrain(event, arena.region(), player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ffaService.isInFfa(event.getPlayer().getUniqueId())) {
            return;
        }
        KitDefinition kit = kitOf(event.getPlayer().getUniqueId());
        if (!KitBlockRules.mayPlace(kit)) {
            event.setCancelled(true);
            return;
        }
        if (playerPlacedBlocks != null) {
            ffaService.arenaOf(event.getPlayer().getUniqueId()).ifPresent(arenaId ->
                    playerPlacedBlocks.mark(event.getBlock(), "ffa:" + arenaId));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!ffaService.isInFfa(event.getPlayer().getUniqueId())) {
            return;
        }
        KitDefinition kit = kitOf(event.getPlayer().getUniqueId());
        String scope = ffaService.arenaOf(event.getPlayer().getUniqueId())
                .map(arenaId -> "ffa:" + arenaId)
                .orElse("");
        boolean playerPlaced = playerPlacedBlocks != null
                && playerPlacedBlocks.isPlacedInScope(event.getBlock(), scope);
        if (!KitBlockRules.mayBreak(kit, event.getBlock().getType(), playerPlaced)) {
            event.setCancelled(true);
            return;
        }
        if (playerPlacedBlocks != null) {
            playerPlacedBlocks.unmark(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ffaService.isInFfa(player.getUniqueId())) {
            return;
        }
        KitDefinition kit = kitOf(player.getUniqueId());
        if (kit != null && kit.autoFood()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ffaService.isInFfa(player.getUniqueId())) {
            return;
        }
        KitDefinition kit = kitOf(player.getUniqueId());
        if (kit != null && !kit.naturalHealthRegen()
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) {
            return;
        }
        if (event.getItem().getType() != Material.ENDER_PEARL) {
            return;
        }
        if (!ffaService.isInFfa(event.getPlayer().getUniqueId())) {
            return;
        }
        KitDefinition kit = kitOf(event.getPlayer().getUniqueId());
        if (kit != null && !kit.pearl()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (ffaService.isInFfa(player.getUniqueId())
                || stateManager.getState(player.getUniqueId()) == PlayerState.FFA) {
            // Lobby / other guards must not block kit drops in FFA.
            event.setCancelled(false);
            player.setCanPickupItems(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (ffaService.isInFfa(event.getPlayer().getUniqueId())
                || stateManager.getState(event.getPlayer().getUniqueId()) == PlayerState.FFA) {
            ffaService.creditCombatLogout(event.getPlayer());
            ffaService.leave(event.getPlayer());
        }
    }

    private KitDefinition kitOf(UUID playerId) {
        return ffaService.arenaOf(playerId)
                .flatMap(ffaService::get)
                .flatMap(a -> kitService.get(a.kitId()))
                .orElse(null);
    }

    private void applyKitCombatRules(EntityDamageByEntityEvent event, Player victim, KitDefinition kit) {
        if (kit == null) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null) {
            return;
        }
        if (kit.swordShieldBreak() && victim.isBlocking() && isSword(attacker.getInventory().getItemInMainHand().getType())) {
            victim.setCooldown(Material.SHIELD, 100);
            victim.clearActiveItem();
        }
        if (Math.abs(kit.knockbackMultiplier() - 1.0d) > 0.001d) {
            double mult = kit.knockbackMultiplier();
            org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(FfaListener.class),
                    () -> {
                        Vector velocity = victim.getVelocity();
                        victim.setVelocity(new Vector(velocity.getX() * mult, velocity.getY(), velocity.getZ() * mult));
                    });
        }
    }

    private static boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }

    private static UUID resolveKiller(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player.getUniqueId();
        }
        if (byEntity.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
        }
        return null;
    }
}
