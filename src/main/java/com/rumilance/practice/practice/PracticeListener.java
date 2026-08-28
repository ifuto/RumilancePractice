package com.rumilance.practice.practice;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Interact / place / leave-region handling for practice rooms.
 */
public final class PracticeListener implements Listener {

    private final PracticeService practiceService;

    public PracticeListener(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        practiceService.onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        if (practiceService.isInJoinGrace(player.getUniqueId())) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        var roomOpt = practiceService.get(session.practiceId());
        if (roomOpt.isEmpty()) {
            return;
        }
        if (!practiceService.contains(session, roomOpt.orElse(null), event.getTo())) {
            practiceService.leave(player, true);
        }
    }

    /** Practice rooms: no damage to the practicing player (anchor blasts included). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (practiceService.session(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setVelocity(new Vector());
    }

    /** Cancel knockback applied to practice players (anchor / explosion / anything). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (practiceService.session(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        event.setKnockback(new Vector());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        ItemStack item = event.getItem();
        String action = PracticeItems.readAction(item);

        if (session.type() == PracticeType.ANKER && session.phase() == PracticeSession.Phase.WAIT
                && action != null) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                practiceService.handleWaitInteract(player, session, action);
            }
            return;
        }

        if (session.type() == PracticeType.MACE && action != null) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                practiceService.handleMaceInteract(player, session, action);
            }
            return;
        }

        if (session.type() == PracticeType.ANKER && session.phase() == PracticeSession.Phase.ACTIVE) {
            Block block = event.getClickedBlock();
            boolean right = event.getAction() == Action.RIGHT_CLICK_BLOCK;
            if (block != null && (block.getType() == Material.RESPAWN_ANCHOR
                    || block.getType() == Material.GLOWSTONE)) {
                int chargesBefore = anchorCharges(block);
                practiceService.onAnkerInteract(player, session, block, right);
                if (right && block.getType() == Material.RESPAWN_ANCHOR) {
                    var plugin = player.getServer().getPluginManager().getPlugin("RumilancePractice");
                    if (plugin != null) {
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                                practiceService.onAnchorChargeOrExplode(session, false, true);
                                return;
                            }
                            if (block.getType() != Material.RESPAWN_ANCHOR) {
                                practiceService.onAnchorChargeOrExplode(session, false, true);
                                return;
                            }
                            int after = anchorCharges(block);
                            if (after > chargesBefore) {
                                practiceService.onAnchorChargeOrExplode(session, true, false);
                            } else if (after < chargesBefore) {
                                practiceService.onAnchorChargeOrExplode(session, false, true);
                            }
                        }, 1L);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        if (session.placeBlocked() || session.phase() == PracticeSession.Phase.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        var roomOpt = practiceService.get(session.practiceId());
        if (roomOpt.isEmpty() || !practiceService.contains(session, roomOpt.get(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (session.type() == PracticeType.ANKER && session.phase() == PracticeSession.Phase.ACTIVE) {
            Material type = event.getBlock().getType();
            if (type == Material.RESPAWN_ANCHOR || type == Material.GLOWSTONE) {
                practiceService.onAnkerPlace(player, session, event.getBlock());
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE
                && session.type() == PracticeType.ANKER
                && session.phase() != PracticeSession.Phase.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof Mannequin)) {
            return;
        }
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        if (session.type() != PracticeType.MACE || session.maceBot() == null) {
            return;
        }
        if (!victim.getUniqueId().equals(session.maceBot().getUniqueId())) {
            return;
        }
        practiceService.onMaceHitBot(session);
    }

    private static int anchorCharges(Block block) {
        if (block.getBlockData() instanceof RespawnAnchor anchor) {
            return anchor.getCharges();
        }
        return 0;
    }
}
