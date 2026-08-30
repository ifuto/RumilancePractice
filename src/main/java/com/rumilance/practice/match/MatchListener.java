package com.rumilance.practice.match;

import com.rumilance.practice.combat.PracticeDeath;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.KitBlockRules;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.PlayerPlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Match combat rules, lethal interception, rematch items, kit toggles.
 */
public final class MatchListener implements Listener {

    private final MatchService matchService;
    private final KitService kitService;
    private final com.rumilance.practice.combat.CombatNetTracker combatNet;
    private final com.rumilance.practice.tnt.PracticeTntSettings practiceTnt;
    private final PlayerPlacedBlockTracker playerPlacedBlocks;

    public MatchListener(MatchService matchService, KitService kitService) {
        this(matchService, kitService, null, null, null);
    }

    public MatchListener(MatchService matchService, KitService kitService,
                         com.rumilance.practice.combat.CombatNetTracker combatNet,
                         com.rumilance.practice.tnt.PracticeTntSettings practiceTnt) {
        this(matchService, kitService, combatNet, practiceTnt, null);
    }

    public MatchListener(MatchService matchService, KitService kitService,
                         com.rumilance.practice.combat.CombatNetTracker combatNet,
                         com.rumilance.practice.tnt.PracticeTntSettings practiceTnt,
                         PlayerPlacedBlockTracker playerPlacedBlocks) {
        this.matchService = matchService;
        this.kitService = kitService;
        this.combatNet = combatNet;
        this.practiceTnt = practiceTnt;
        this.playerPlacedBlocks = playerPlacedBlocks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        MatchSession session = matchService.registry().byPlayer(victim.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        if (session.state() != MatchState.ACTIVE) {
            // PREPARING / WAITING / COUNTDOWN / ENDING: no damage (Match Found lobby punch-through,
            // countdown freeze, post-fight). Only ACTIVE participants trade hits.
            event.setCancelled(true);
            event.setDamage(0);
            return;
        }

        UUIDLikeAttacker attacker = resolveAttacker(event);
        if (attacker != null && attacker.playerId() != null
                && !session.isParticipant(attacker.playerId())) {
            event.setCancelled(true);
            return;
        }
        if (PracticeGuards.shouldBlockTeammateDamage(
                session.isTeamMatch(),
                attacker != null && attacker.playerId() != null
                        && session.areTeammates(attacker.playerId(), victim.getUniqueId()),
                session.friendlyFire())) {
            event.setCancelled(true);
            return;
        }

        KitDefinition kit = kitService.get(session.kitName()).orElse(null);
        if (kit != null && event instanceof EntityDamageByEntityEvent byEntity) {
            applyCombatRules(byEntity, victim, kit);
        }

        // Record non-lethal combat stats for the report card. Lethal hits are recorded by
        // MatchService.handleLethal once the outcome is known, so they still appear in the totals.
        double remaining = PracticeDeath.remainingAfter(victim, event);
        if (remaining > 0 && attacker != null && attacker.playerId() != null
                && session.isParticipant(attacker.playerId())
                && event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof org.bukkit.entity.Player attackerPlayer) {
            recordCombatHit(session, attackerPlayer, victim, byEntity, event.getFinalDamage());
        }

        // After a vanilla/manual totem pop, HP frames can look lethal and falsely fake-death
        // (which re-applies kits at full health).
        if (PracticeDeath.isInResurrectGrace(victim)) {
            return;
        }
        if (PracticeDeath.shouldDeferTotemToVanilla(victim, kit, event)) {
            return;
        }
        if (remaining > 0) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0);
        UUID attackerId = attacker == null ? null : attacker.playerId();
        matchService.handleLethal(session, victim.getUniqueId(), attackerId);
    }

    private void recordCombatHit(MatchSession session, Player attacker, Player victim,
                                 EntityDamageByEntityEvent byEntity, double finalDamage) {
        MatchCombatTracker tracker = matchService.combatTracker();
        if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player) {
            tracker.recordProjectileHit(session.id(), attacker.getUniqueId(), victim.getUniqueId(), finalDamage);
            return;
        }
        boolean crit = attacker.getFallDistance() > 0
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
                && attacker.getAttackCooldown() > 0.9f;
        tracker.recordHit(session.id(), attacker.getUniqueId(), victim.getUniqueId(), finalDamage, crit);
    }

    private void applyCombatRules(EntityDamageByEntityEvent event, Player victim, KitDefinition kit) {
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
        if (kit.swordShieldBreak() && victim.isBlocking()
                && attacker.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) {
            victim.setCooldown(Material.SHIELD, 100);
            victim.clearActiveItem();
            // Paper 1.20.6+ arms invulnerability frames on the shield hit so the follow-up
            // swing after a break is swallowed; clear the victim's i-frames (Paper #10742).
            com.rumilance.practice.combat.ShieldBreakStunFix.allowFollowUpHit(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(MatchListener.class), victim);
        }
        // Knockback coefficients (incl. per-kit horizontal scaling) are delegated to an
        // external knockback plugin (KnockBackSync). No velocity scaling here, otherwise it
        // would stack on top of the external plugin's shaped knockback.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (matchService.registry().byPlayer(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
            event.getDrops().clear();
            event.setKeepInventory(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        MatchSession session = matchService.registry().byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        if (session.state() == MatchState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        if (session.state() == MatchState.ACTIVE) {
            KitDefinition kit = kitService.get(session.kitName()).orElse(null);
            if (!KitBlockRules.mayPlace(kit)) {
                event.setCancelled(true);
                return;
            }
            if (playerPlacedBlocks != null) {
                playerPlacedBlocks.mark(event.getBlock(), session.id().toString());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        MatchSession session = matchService.registry().byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        if (session.state() == MatchState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        if (session.state() == MatchState.ACTIVE) {
            KitDefinition kit = kitService.get(session.kitName()).orElse(null);
            String scope = session.id().toString();
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
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        MatchSession session = matchService.registry().byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (session != null && (session.state() == MatchState.COUNTDOWN || session.state() == MatchState.ENDING)) {
            event.setCancelled(true);
        }
    }

    /** Countdown: walk + rearrange kit only — no bows / crossbows / eggs / pearls as projectiles. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.state() == MatchState.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.state() == MatchState.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != MatchState.ACTIVE) {
            return;
        }
        KitDefinition kit = kitService.get(session.kitName()).orElse(null);
        if (kit != null && kit.autoFood()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != MatchState.ACTIVE) {
            return;
        }
        KitDefinition kit = kitService.get(session.kitName()).orElse(null);
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
        MatchSession session = matchService.registry().byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        if (session.state() != MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        KitDefinition kit = kitService.get(session.kitName()).orElse(null);
        if (kit != null && !kit.pearl()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        matchService.handleDisconnect(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getItem() == null || !event.getItem().hasItemMeta()) {
            return;
        }
        var pdc = event.getItem().getItemMeta().getPersistentDataContainer();
        if (pdc.has(ItemKeys.rematch(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            matchService.requestRematch(event.getPlayer());
        } else if (pdc.has(ItemKeys.returnLobby(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            matchService.returnToLobby(event.getPlayer());
        } else if (pdc.has(ItemKeys.matchReport(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            matchService.openMatchReport(event.getPlayer());
        }
    }

    private UUIDLikeAttacker resolveAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return new UUIDLikeAttacker(null);
        }
        if (byEntity.getDamager() instanceof Player player) {
            return new UUIDLikeAttacker(player.getUniqueId());
        }
        if (byEntity.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return new UUIDLikeAttacker(player.getUniqueId());
            }
        }
        if (byEntity.getDamager() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return new UUIDLikeAttacker(player.getUniqueId());
        }
        if (byEntity.getDamager() instanceof EnderCrystal) {
            return new UUIDLikeAttacker(null);
        }
        return new UUIDLikeAttacker(null);
    }

    private record UUIDLikeAttacker(java.util.UUID playerId) {
    }
}
