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

    /**
     * Practice rooms: no damage to the practicing player (anchor blasts included) — except
     * where fighting IS the point: sword-bot swings in SWORD rooms, and crystal blasts in
     * CRYSTAL rooms (the bot fights back there; your own mistimed crystals hurt too).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        // Totem of undying: a practice player holding a totem in a hand can never die, whatever
        // the room type is (crystal self-blasts and TNT carts are the lethal sources here). The
        // pop runs before every exemption below so the totem is consumed exactly like vanilla.
        if (com.rumilance.practice.combat.PracticeDeath.wouldDie(player, event)
                && com.rumilance.practice.combat.PracticeDeath.tryPopTotem(
                        player, practiceService.kitOf(session), event)) {
            return;
        }
        // Whoever fights back in this session: the combat bot for sword / crystal / nethpot /
        // cart, the mace dummy for MACE (it lunges, wind-charges and smashes like the map's bot).
        Mannequin combatBot = session.combatBot() != null ? session.combatBot() : session.maceBot();
        if (event instanceof EntityDamageByEntityEvent byEntity && combatBot != null) {
            Entity damager = byEntity.getDamager();
            boolean botHit = damager instanceof Mannequin bot
                    && bot.getUniqueId().equals(combatBot.getUniqueId());
            boolean botProjectile = damager instanceof org.bukkit.entity.Projectile proj
                    && proj.getShooter() instanceof Entity shooter
                    && shooter.getUniqueId().equals(combatBot.getUniqueId());
            if (botHit || botProjectile) {
                return; // sparring hit (melee swing or arrow volley): allow it
            }
        }
        if ((session.type() == PracticeType.CRYSTAL || session.type() == PracticeType.CART)
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                        || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            return; // blast damage is the point in crystal & cart rooms
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setVelocity(new Vector());
    }

    /** Cancel knockback applied to practice players — except in live bot fights. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        if (session.type().botMode()
                && (session.combatBot() != null || session.maceBot() != null)) {
            return; // sparring shoves, mace smashes, crystal blasts, TNT carts and arrows push
        }
        event.setCancelled(true);
        event.setKnockback(new Vector());
    }

    /**
     * Combat-bot damage routing (ITEM 41): sword hits, crystal blasts — anything landing on
     * the session's bot is managed by the service (pops, staggers, respawns).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBotDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin bot)) {
            return;
        }
        for (PracticeSession session : practiceService.sessionsWithCombatBot()) {
            Mannequin cb = session.combatBot();
            if (cb == null || !cb.getUniqueId().equals(bot.getUniqueId())) {
                continue;
            }
            Player player = org.bukkit.Bukkit.getPlayer(session.playerId());
            if (player != null) {
                practiceService.onCombatBotDamaged(player, session, event);
            }
            return;
        }
        // The mace dummy is tracked separately from the combat bots: dropping it wins the match.
        for (PracticeSession session : practiceService.activeSessions()) {
            Mannequin maceBot = session.maceBot();
            if (maceBot == null || !maceBot.getUniqueId().equals(bot.getUniqueId())) {
                continue;
            }
            Player player = org.bukkit.Bukkit.getPlayer(session.playerId());
            if (player != null) {
                practiceService.onMaceBotDamaged(player, session, event);
            }
            return;
        }
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

        if (session.phase() == PracticeSession.Phase.WAIT && action != null) {
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

        // Sword / crystal rooms: only the settings item is a menu button; the combat gear
        // (sword, crystals, obsidian, apples) keeps its vanilla behaviour.
        if ((session.type() == PracticeType.SWORD || session.type() == PracticeType.CRYSTAL)
                && PracticeItems.ACTION_BOT_SETTINGS.equals(action)) {
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
                    var plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(PracticeListener.class);
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

    /**
     * Crystal / anchor blasts must not eat the practice room terrain: inside any active
     * practice region the explosion keeps its entity damage but loses its block list
     * (shared templates are not disposable; clones reset anyway).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (insideAnyPracticeRegion(event.getLocation())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        if (insideAnyPracticeRegion(event.getBlock().getLocation())) {
            event.blockList().clear();
        }
    }

    private boolean insideAnyPracticeRegion(org.bukkit.Location at) {
        if (at == null) {
            return false;
        }
        for (PracticeSession session : practiceService.activeSessions()) {
            if (session.activeRegion() != null && session.activeRegion().contains(at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ITEM 41: the sword bot can actually down the practicing player. Keep it friendly —
     * no drops, no death screen lingering, respawn at the room spawn with the room loadout.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPracticeDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        var sessionOpt = practiceService.session(player.getUniqueId());
        if (sessionOpt.isEmpty()) {
            return;
        }
        PracticeSession session = sessionOpt.get();
        // The totem failsafe (TotemGuardListener, registered first) already turned this death
        // into a totem pop and owns the revive: don't run the practice death flow on top of it.
        if (event.isCancelled()) {
            return;
        }
        // Absolute guarantee: a death that reaches this point while the player still holds a
        // totem is turned into a totem pop instead of a loss (damage paths pop first; this is
        // the net for anything that arrives without a damage event we could intercept).
        if (com.rumilance.practice.combat.PracticeDeath.hasTotemInHand(player)) {
            var totemKit = practiceService.kitOf(session);
            if (totemKit == null || totemKit.totem()) {
                event.setCancelled(true);
                event.getDrops().clear();
                event.setKeepInventory(true);
                event.setShouldDropExperience(false);
                event.deathMessage(null);
                com.rumilance.practice.combat.PracticeDeath.consumeTotemFromHand(player);
                reviveWithTotem(player, session);
                return;
            }
        }
        event.getDrops().clear();
        event.setShouldDropExperience(false);
        event.deathMessage(null);
        session.setBotNextAttackMs(System.currentTimeMillis() + 2_000L);
        if (session.type().botMode() && session.phase() == PracticeSession.Phase.ACTIVE) {
            // A real match: death is the loss. endBotMatch handles result + lobby return.
            practiceService.endBotMatch(player, session, PracticeService.BotMatchResult.LOSE);
            return;
        }
        // Dying before the fight (waiting room / countdown): abort the countdown and go back
        // to the waiting hotbar — the bot never spawns outside ACTIVE, nothing can run wild.
        if (session.phase() == PracticeSession.Phase.COUNTDOWN) {
            session.cancelTimer();
            session.setPhase(PracticeSession.Phase.WAIT);
            session.setPlaceBlocked(false);
        }
        var plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(PracticeListener.class);
        if (plugin == null) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.spigot().respawn();
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                org.bukkit.Location spawn = session.activeSpawn();
                if (spawn != null) {
                    player.teleport(spawn);
                }
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(10.0f);
                player.setFireTicks(0);
                player.getInventory().clear();
                if (session.phase() == PracticeSession.Phase.WAIT) {
                    if (session.type() == PracticeType.ANKER) {
                        practiceService.giveWaitHotbar(player, session);
                    } else {
                        practiceService.giveBotWaitHotbar(player, session);
                    }
                } else {
                    switch (session.type()) {
                        case MACE -> practiceService.giveMaceLoadout(player, session);
                        case SWORD -> practiceService.giveSwordLoadout(player, session);
                        case CRYSTAL -> practiceService.giveCrystalLoadout(player, session);
                        case NETHERITE_POT -> practiceService.giveNethPotLoadout(player, session);
                        case CART -> practiceService.giveCartLoadout(player, session);
                        default -> { }
                    }
                }
                player.updateInventory();
            }, 2L);
        });
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

    /**
     * Brings a practice player back after a cancelled death: Paper keeps a cancelled death
     * downed until the health is restored, so top the health up first and force the respawn
     * when that was not enough, then put them back on their room spawn with their loadout and
     * the vanilla totem effects.
     */
    private void reviveWithTotem(Player player, PracticeSession session) {
        var plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(PracticeListener.class);
        if (plugin == null) {
            return;
        }
        com.rumilance.practice.combat.PracticeDeath.applyTotemEffects(player);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (player.isDead() || player.getHealth() <= 0.0d) {
                try {
                    player.spigot().respawn();
                } catch (IllegalStateException | IllegalArgumentException ignored) {
                    // The cancelled death was enough; the health top-up below finishes the job.
                }
            }
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (player.getHealth() <= 0.0d) {
                    player.setHealth(1.0d);
                }
                player.setFallDistance(0f);
                player.setFireTicks(0);
                player.setFoodLevel(20);
                player.setSaturation(10.0f);
                org.bukkit.Location spawn = session.activeSpawn();
                if (spawn != null && spawn.getWorld() != null) {
                    com.rumilance.practice.util.SafeTeleport.teleport(player, spawn);
                }
                if (session.phase() == PracticeSession.Phase.ACTIVE) {
                    practiceService.refreshBotLoadout(player, session);
                } else {
                    practiceService.giveBotWaitHotbar(player, session);
                }
                com.rumilance.practice.combat.PracticeDeath.applyTotemEffects(player);
                player.updateInventory();
            }, 2L);
        });
    }

    private static int anchorCharges(Block block) {
        if (block.getBlockData() instanceof RespawnAnchor anchor) {
            return anchor.getCharges();
        }
        return 0;
    }
}
