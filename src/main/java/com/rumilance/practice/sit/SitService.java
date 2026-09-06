package com.rumilance.practice.sit;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GSit-style seats in the lobby: right-click the TOP of a bottom stair or a bottom slab to
 * sit down on it. The seated player rides an invisible marker armour stand (vanilla riding
 * pose — the same "sitting" silhouette GSit produces); the stand is removed the moment the
 * player gets up, quits, the block breaks, or the player leaves the lobby state.
 *
 * <p>Rotation: stairs always face the stair's own direction (GSit behaviour); slabs let the
 * player face freely — the seat yaw is wherever the player was looking, and the seat position
 * is the exact clicked point on the slab top.</p>
 *
 * <p>Waterlogged stairs/slabs are rejected (no sitting in a water-filled step).</p>
 */
public final class SitService implements Listener {

    /** Marker-stand passenger offset is zero, so add a small lift above the seat surface. */
    private static final double SEAT_SURFACE_LIFT = 0.05d;
    /** GSit (MC ≥ 1.20.2) shifts stair seats down half a block relative to the block top. */
    private static final double STAIR_Y_OFFSET = 0.5d;
    /** GSit's stair seat shift toward the stair's facing direction. */
    private static final double STAIR_XZ_OFFSET = 0.123d;
    /** Keep slab click positions this far inside the block so the seat stays on the slab. */
    private static final double SLAB_INSET = 0.12d;

    private final Plugin plugin;
    private final PlayerStateManager stateManager;

    private final Map<UUID, Seat> seatsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Seat> seatsByStand = new ConcurrentHashMap<>();
    private final Set<Location> occupiedBlocks = ConcurrentHashMap.newKeySet();
    private BukkitTask guardTask;

    public record Seat(Player player, ArmorStand stand, Location blockLocation) {
    }

    public SitService(Plugin plugin, PlayerStateManager stateManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager");
    }

    public void start() {
        if (guardTask != null) {
            return;
        }
        guardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::guard, 10L, 10L);
    }

    public void shutdown() {
        if (guardTask != null) {
            guardTask.cancel();
            guardTask = null;
        }
        for (Seat seat : Map.copyOf(seatsByPlayer).values()) {
            removeSeat(seat);
        }
    }

    public boolean isSitting(Player player) {
        return player != null && seatsByPlayer.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getBlockFace() != BlockFace.UP) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        BlockData data = block.getBlockData();
        boolean stairs = Tag.STAIRS.isTagged(block.getType());
        boolean slab = Tag.SLABS.isTagged(block.getType());
        if (!stairs && !slab) {
            return;
        }
        if (stairs && ((Stairs) data).getHalf() != Bisected.Half.BOTTOM) {
            return;
        }
        if (slab && ((Slab) data).getType() != Slab.Type.BOTTOM) {
            return;
        }
        // Water in the step/notch — no seat.
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return;
        }
        if (!canSit(player, block)) {
            return;
        }
        Location seatLocation = stairs
                ? stairSeatLocation(block, (Stairs) data)
                : slabSeatLocation(block, player, event);
        if (seatLocation == null) {
            return;
        }
        event.setCancelled(true);
        sit(player, block, seatLocation);
    }

    private boolean canSit(Player player, Block block) {
        if (player.isSneaking() || !player.isValid()) {
            return false;
        }
        if (isSitting(player)) {
            return false;
        }
        if (stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY) {
            return false;
        }
        Location blockLocation = block.getLocation();
        if (occupiedBlocks.contains(blockLocation)) {
            return false;
        }
        // The seat needs open space above the step/slab.
        if (!block.getRelative(BlockFace.UP).isPassable()) {
            return false;
        }
        return true;
    }

    /** Stair seat: block centre, slightly toward the stair's facing; yaw = stair direction. */
    private Location stairSeatLocation(Block block, Stairs stairs) {
        BlockFace facing = stairs.getFacing();
        Vector shift = facing.getDirection().multiply(STAIR_XZ_OFFSET);
        float yaw = switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
        Location location = block.getLocation().add(0.5d + shift.getX(),
                blockSurfaceHeight(block) - STAIR_Y_OFFSET + SEAT_SURFACE_LIFT,
                0.5d + shift.getZ());
        location.setYaw(yaw);
        return location;
    }

    /**
     * The block's collision surface height relative to its own Y (GSit's {@code additionalOffset}):
     * 0.5 for bottom slabs, 1.0 for bottom stairs (their bounding box spans the full cube).
     * Zero-size shapes fall back to a full block so the seat never spawns inside geometry.
     */
    private double blockSurfaceHeight(Block block) {
        double height = block.getBoundingBox().getMaxY() - block.getY();
        return height <= 0.001d ? 1.0d : height;
    }

    /** Slab seat: exact clicked point (inset so the player stays on the slab); free yaw. */
    private Location slabSeatLocation(Block block, Player player, PlayerInteractEvent event) {
        Vector clicked = event.getClickedPosition();
        double relX;
        double relZ;
        if (clicked != null) {
            relX = clicked.getX() - block.getX();
            relZ = clicked.getZ() - block.getZ();
        } else {
            relX = 0.5d;
            relZ = 0.5d;
        }
        relX = Math.min(1.0d - SLAB_INSET, Math.max(SLAB_INSET, relX));
        relZ = Math.min(1.0d - SLAB_INSET, Math.max(SLAB_INSET, relZ));
        Location location = block.getLocation().add(relX,
                blockSurfaceHeight(block) + SEAT_SURFACE_LIFT, relZ);
        location.setYaw(player.getLocation().getYaw());
        return location;
    }

    private void sit(Player player, Block block, Location seatLocation) {
        Location blockLocation = block.getLocation();
        if (!occupiedBlocks.add(blockLocation)) {
            return;
        }
        try {
            ArmorStand stand = block.getWorld().spawn(seatLocation, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setSmall(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.setPersistent(false);
                as.setCanPickupItems(false);
                as.setBasePlate(false);
            });
            if (!stand.addPassenger(player)) {
                stand.remove();
                occupiedBlocks.remove(blockLocation);
                return;
            }
            Seat seat = new Seat(player, stand, blockLocation);
            seatsByPlayer.put(player.getUniqueId(), seat);
            seatsByStand.put(stand.getUniqueId(), seat);
        } catch (RuntimeException e) {
            occupiedBlocks.remove(blockLocation);
            plugin.getLogger().warning("Could not create seat at " + blockLocation + ": " + e);
        }
    }

    /** Player got up (sneak-jump): clean the seat entity up. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        Entity dismounted = event.getDismounted();
        if (!(dismounted instanceof ArmorStand)) {
            return;
        }
        Seat seat = seatsByStand.get(dismounted.getUniqueId());
        if (seat == null) {
            return;
        }
        cleanup(seat);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Seat seat = seatsByPlayer.get(event.getPlayer().getUniqueId());
        if (seat != null) {
            removeSeat(seat);
        }
    }

    /** Breaking a seat block ejects the sitter; the break itself still goes through. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Seat seat = findByBlock(event.getBlock());
        if (seat != null) {
            removeSeat(seat);
        }
    }

    private Seat findByBlock(Block block) {
        Location location = block.getLocation();
        if (!occupiedBlocks.contains(location)) {
            return null;
        }
        for (Seat seat : seatsByPlayer.values()) {
            if (seat.blockLocation().equals(location)) {
                return seat;
            }
        }
        return null;
    }

    /** Ejects the rider and removes the seat entity. */
    public void removeSeat(Seat seat) {
        if (seat == null) {
            return;
        }
        try {
            seat.stand().eject();
        } catch (RuntimeException ignored) {
            // already gone
        }
        cleanup(seat);
    }

    private void cleanup(Seat seat) {
        seatsByPlayer.remove(seat.player().getUniqueId(), seat);
        seatsByStand.remove(seat.stand().getUniqueId(), seat);
        occupiedBlocks.remove(seat.blockLocation());
        if (seat.stand().isValid()) {
            seat.stand().remove();
        }
    }

    /** Drops seats whose rider left the lobby (queued / matched / offline) or lost the stand. */
    private void guard() {
        for (Seat seat : Map.copyOf(seatsByPlayer).values()) {
            Player player = seat.player();
            boolean gone = !player.isOnline() || !player.isValid()
                    || !seat.stand().isValid()
                    || stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY;
            if (!gone && seat.stand().getPassengers().stream()
                    .noneMatch(passenger -> passenger.getUniqueId().equals(player.getUniqueId()))) {
                gone = true;
            }
            if (gone) {
                removeSeat(seat);
            }
        }
    }
}
