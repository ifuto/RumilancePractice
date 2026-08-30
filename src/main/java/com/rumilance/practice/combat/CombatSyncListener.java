package com.rumilance.practice.combat;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.sight.ViewControlService;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.util.TickHealth;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ping-aware combat netcode and anti-void transit:
 * <ul>
 *   <li>Attacker crits are rewound by compensated ping so falling hits are not lost.</li>
 *   <li>Bukkit's sprint-stop-on-attack desync is repaired from the client's toggle intent.</li>
 *   <li>{@code PlayerFailMoveEvent} swallows "moved too quickly" rubberbands; burst packets
 *       after a client freeze are clamped instead of dumped into the void.</li>
 * </ul>
 * Knockback shaping and ping/Y compensation are intentionally NOT handled here: they are
 * delegated to an external knockback plugin (KnockBackSync).
 */
public final class CombatSyncListener implements Listener {

    private final Plugin plugin;
    private final CombatNetTracker tracker;
    private final MatchService matchService;
    private final FfaService ffaService;
    private final LobbyService lobbyService;
    private final ArenaService arenaService;
    private final ViewControlService viewControl;
    private final KitService kitService;
    private final Set<UUID> voidTotemExpected = ConcurrentHashMap.newKeySet();

    public CombatSyncListener(
            Plugin plugin,
            CombatNetTracker tracker,
            MatchService matchService,
            FfaService ffaService,
            LobbyService lobbyService,
            ArenaService arenaService,
            ViewControlService viewControl,
            KitService kitService
    ) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.matchService = matchService;
        this.ffaService = ffaService;
        this.lobbyService = lobbyService;
        this.arenaService = arenaService;
        this.viewControl = viewControl;
        this.kitService = kitService;
    }

    public CombatNetTracker tracker() {
        return tracker;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sampleCombatants, 1L, 1L);
    }

    private void sampleCombatants() {
        if (TickHealth.lagging()) {
            return;
        }
        for (MatchSession session : matchService.registry().all()) {
            MatchState state = session.state();
            if (state != MatchState.ACTIVE && state != MatchState.COUNTDOWN && state != MatchState.ENDING) {
                continue;
            }
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    tracker.sample(player);
                }
            }
        }
        for (UUID id : ffaService.occupantIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                tracker.sample(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerTickEnd(ServerTickEndEvent event) {
        TickHealth.record(event.getTickDuration());
    }

    /**
     * Paper and freeze plugins sometimes cancel F-key swaps in combat. Practice PvP needs
     * vanilla offhand swap timing (gapples, totems, shields) so we uncancel for combatants.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled() && isCombatant(event.getPlayer().getUniqueId())) {
            event.setCancelled(false);
        }
    }

    /** Vanilla totem pop path — arm grace so Match/FFA lethal handlers do not fake-death. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && isCombatant(player.getUniqueId())) {
            voidTotemExpected.remove(player.getUniqueId());
            PracticeDeath.markResurrected(player);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        tracker.setWantsSprint(event.getPlayer().getUniqueId(), event.isSprinting());
    }

    /**
     * Restore sprint one tick after Bukkit clears it on attack. Only if the client is still
     * holding sprint (wantsSprint) and they are allowed to sprint  Ethis is the classic
     * Bukkit sprint-stop desync, not a sprint-reset macro.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeSprintDesync(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!isCombatant(attacker.getUniqueId()) && !isCombatant(victim.getUniqueId())) {
            return;
        }
        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();
        boolean restoreAttacker = tracker.wantsSprint(attackerId);
        boolean restoreVictim = tracker.wantsSprint(victimId);
        if (!restoreAttacker && !restoreVictim) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            restoreSprint(attackerId, restoreAttacker);
            restoreSprint(victimId, restoreVictim);
        });
    }

    private void restoreSprint(UUID playerId, boolean wanted) {
        if (!wanted) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || !tracker.wantsSprint(playerId)) {
            return;
        }
        if (player.getFoodLevel() <= 6 || player.hasPotionEffect(PotionEffectType.BLINDNESS)
                || player.isSneaking() || player.isInsideVehicle()) {
            return;
        }
        if (!player.isSprinting()) {
            player.setSprinting(true);
        }
    }

    /**
     * Rewind the attacker by compensated ping and, when the client-side snapshot is a crit
     * but the server did not flag one (or vice versa, except the intentional sprint-crit
     * desync), rescale the raw damage by 1.5.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCritSync(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (!isCombatant(attacker.getUniqueId())) {
            return;
        }
        CombatNetTracker.Snapshot snap = tracker.rewindAttacker(attacker);
        boolean clientCrit = CombatPhysics.isClientCritical(
                snap.onGround(),
                snap.fallDistance(),
                snap.sprinting(),
                snap.inWater(),
                snap.climbing(),
                snap.passenger(),
                snap.blindness(),
                attacker.getAttackCooldown()
        );
        boolean serverCrit = event.isCritical();
        // Keep the vanilla sprint-crit desync (MC-69459) when the client is holding sprint:
        // that is an intentional PvP mechanic. Only fill in *missed* falling crits, or strip
        // a server crit that the rewound snapshot says was on-ground with no fall.
        if (clientCrit && !serverCrit) {
            event.setDamage(event.getDamage() * 1.5d);
        } else if (!clientCrit && serverCrit && snap.onGround() && snap.fallDistance() <= 0.0f
                && !tracker.wantsSprint(attacker.getUniqueId())) {
            event.setDamage(event.getDamage() / 1.5d);
        }
    }

    // NOTE: Knockback is intentionally NOT touched here. Knockback shaping and ping/Y
    // compensation are delegated to an external plugin (KnockBackSync), so this listener
    // no longer rewrites PlayerVelocityEvent or scales melee velocity itself. Paper handles
    // the vanilla sprint-stop-on-attack knockback bonus natively; the only place this
    // plugin supplies a velocity for melee is PaperCombatCompatListener (shield-blocked /
    // post-stun hits where Paper omits knockback entirely), which fires PlayerVelocityEvent
    // so an external knockback plugin can still shape it.

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFailMove(PlayerFailMoveEvent event) {
        event.setLogWarning(false);
        Player player = event.getPlayer();
        if (!isCombatant(player.getUniqueId())) {
            // Paper's default is to reject. Lobby / creative / queue must never be rubberbanded.
            event.setAllowed(true);
            return;
        }
        Location to = event.getTo();
        boolean lookOnly = isLookOnly(event.getFrom(), to);
        if (CombatNetTracker.isVoidLike(to) || belowPlayFloor(player, to)) {
            event.setAllowed(false);
            if (!lookOnly) {
                rescue(player);
            }
            return;
        }
        if (!insidePlayArea(player, to)) {
            // Rejecting here rubberbands/freezes (Paper FailMove default). Horizontal walls
            // are handled by PlayAreaWall; only void/unloaded-chunk need a hard deny.
            event.setAllowed(true);
            return;
        }
        PlayerFailMoveEvent.FailReason reason = event.getFailReason();
        if (reason == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY
                || reason == PlayerFailMoveEvent.FailReason.MOVED_WRONGLY) {
            event.setAllowed(true);
        } else if (reason == PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK) {
            event.setAllowed(true);
        } else if (reason == PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK) {
            event.setAllowed(false);
            rescue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportGrace(PlayerTeleportEvent event) {
        tracker.markTeleport(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isCombatant(event.getPlayer().getUniqueId())) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Location from = event.getFrom();
        boolean lookOnly = isLookOnly(from, to);
        if (lookOnly) {
            return;
        }
        boolean blockChange = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (blockChange) {
            tracker.markMoved(player);
        }

        if (CombatNetTracker.isVoidLike(to) || belowPlayFloor(player, to)) {
            event.setCancelled(true);
            rescue(player);
            return;
        }

        if (!TickHealth.lagging() && !tracker.inKnockbackGrace(id)
                && tracker.inBurst(id) && blockChange) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double horiz = Math.hypot(dx, dz);
            double cap = CombatPhysics.maxHorizontalDisplacement(tracker.emaPing(id), true);
            if (horiz > cap) {
                double scale = cap / horiz;
                Location clamped = from.clone();
                clamped.setX(from.getX() + dx * scale);
                clamped.setY(to.getY());
                clamped.setZ(from.getZ() + dz * scale);
                clamped.setYaw(to.getYaw());
                clamped.setPitch(to.getPitch());
                if (!CombatNetTracker.isVoidLike(clamped) && insidePlayArea(player, clamped)) {
                    event.setTo(clamped);
                } else if (CombatNetTracker.isVoidLike(clamped) || belowPlayFloor(player, clamped)) {
                    event.setCancelled(true);
                    rescue(player);
                    return;
                } else {
                    event.setCancelled(true);
                }
            }
        }

        if (player.isOnGround() && insidePlayArea(player, to) && CombatNetTracker.isLoadedSafe(to)) {
            tracker.rememberSafe(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (isCombatant(player.getUniqueId())) {
            KitDefinition kit = kitForCombatant(player);
            if (PracticeDeath.shouldDeferTotemToVanilla(player, kit, event)) {
                voidTotemExpected.add(player.getUniqueId());
                return;
            }
        }
        voidTotemExpected.remove(player.getUniqueId());
        event.setCancelled(true);
        if (isCombatant(player.getUniqueId())) {
            rescue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVoidDamageMonitor(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!isCombatant(player.getUniqueId())) {
            return;
        }
        KitDefinition kit = kitForCombatant(player);
        if (PracticeDeath.shouldDeferTotemToVanilla(player, kit, event)) {
            voidTotemExpected.add(player.getUniqueId());
        } else if (!PracticeDeath.wouldDie(player, event)) {
            voidTotemExpected.remove(player.getUniqueId());
        }
    }

    private KitDefinition kitForCombatant(Player player) {
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.state() == MatchState.ACTIVE) {
            return kitService.get(session.kitFor(player.getUniqueId())).orElse(null);
        }
        if (ffaService.isInFfa(player.getUniqueId())) {
            return ffaService.arenaOf(player.getUniqueId())
                    .flatMap(ffaService::get)
                    .flatMap(arena -> kitService.get(arena.kitId()))
                    .orElse(null);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVoidDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getLastDamageCause() != null
                && player.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            event.getDrops().clear();
            event.setKeepInventory(true);
            if (voidTotemExpected.remove(player.getUniqueId())) {
                return;
            }
            if (isCombatant(player.getUniqueId())) {
                rescue(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        voidTotemExpected.remove(id);
        tracker.remove(id);
    }

    public void rescue(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setFallDistance(0f);
        player.setVelocity(new Vector());
        Location safe = tracker.lastSafe(player.getUniqueId());
        Location dest = null;
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.arenaInstanceId() != null) {
            ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
            if (instance != null) {
                dest = session.teamColor(player.getUniqueId()) == com.rumilance.practice.state.TeamColor.RED
                        ? arenaService.spawnA(instance)
                        : arenaService.spawnB(instance);
            }
        } else if (ffaService.isInFfa(player.getUniqueId())) {
            dest = ffaService.spawnDestination(player);
        } else if (lobbyService.spawn() != null) {
            dest = lobbyService.spawn();
            if (safe != null && CombatNetTracker.isLoadedSafe(safe) && insidePlayArea(player, safe)) {
                dest = safe;
            }
        } else if (safe != null && CombatNetTracker.isLoadedSafe(safe) && insidePlayArea(player, safe)) {
            dest = safe;
        }
        if (session != null && safe != null && CombatNetTracker.isLoadedSafe(safe) && insidePlayArea(player, safe)) {
            dest = safe;
        }
        if (dest == null) {
            return;
        }
        tracker.markTeleport(player);
        com.rumilance.practice.util.SafeTeleport.teleport(player, dest).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                reapplySight(player);
            }
        }));
    }

    private void reapplySight(Player player) {
        if (viewControl == null) {
            return;
        }
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.arenaInstanceId() != null) {
            viewControl.applyForMatch(player, session);
            return;
        }
        if (ffaService.isInFfa(player.getUniqueId())) {
            ffaService.applySight(player);
            return;
        }
        viewControl.clear(player);
    }

    private boolean isCombatant(UUID playerId) {
        MatchSession session = matchService.registry().byPlayer(playerId).orElse(null);
        if (session != null) {
            MatchState state = session.state();
            return state == MatchState.ACTIVE || state == MatchState.COUNTDOWN || state == MatchState.ENDING;
        }
        return ffaService.isInFfa(playerId);
    }

    private boolean insidePlayArea(Player player, Location location) {
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.arenaInstanceId() != null) {
            ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
            if (instance == null) {
                return true;
            }
            int x = location.getBlockX();
            int z = location.getBlockZ();
            return x >= instance.minX() && x <= instance.maxX()
                    && z >= instance.minZ() && z <= instance.maxZ();
        }
        if (ffaService.isInFfa(player.getUniqueId())) {
            return ffaService.arenaOf(player.getUniqueId())
                    .flatMap(ffaService::get)
                    .map(arena -> arena.region().containsHorizontal(location))
                    .orElse(true);
        }
        return true;
    }

    private static boolean isLookOnly(Location from, Location to) {
        return from != null && to != null
                && from.getX() == to.getX()
                && from.getY() == to.getY()
                && from.getZ() == to.getZ();
    }

    private boolean belowPlayFloor(Player player, Location location) {
        MatchSession session = matchService.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.arenaInstanceId() != null) {
            ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
            return instance != null && location.getY() < instance.minY() - 1;
        }
        if (ffaService.isInFfa(player.getUniqueId())) {
            return ffaService.arenaOf(player.getUniqueId())
                    .flatMap(ffaService::get)
                    .map(arena -> location.getY() < arena.region().minY() - 1)
                    .orElse(false);
        }
        return false;
    }
}
