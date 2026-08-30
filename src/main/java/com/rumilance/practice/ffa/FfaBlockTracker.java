package com.rumilance.practice.ffa;

import com.rumilance.practice.util.MaterialFlags;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.List;

/**
 * Records terrain diffs inside enabled FFA arena regions for {@link FfaService#reset}.
 * Player place/break is recorded at MONITOR after {@link FfaListener} kit gates pass.
 */
public final class FfaBlockTracker implements Listener {

    private final FfaService ffaService;

    public FfaBlockTracker(FfaService ffaService) {
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ffaService.isInFfaRegion(event.getBlock().getLocation())) {
            return;
        }
        ffaService.recordBlockChangeAt(
                event.getBlock().getLocation(),
                event.getBlockReplacedState().getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location at = event.getBlock().getLocation();
        if (!ffaService.isInFfaRegion(at)) {
            return;
        }
        ffaService.recordBlockChangeAt(at, event.getBlock().getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block == null || !ffaService.isInFfaRegion(block.getLocation())) {
            return;
        }
        ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Block block = event.getBlock();
        if (block == null || !ffaService.isInFfaRegion(block.getLocation())) {
            return;
        }
        ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        recordBlocks(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        recordBlocks(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (to == null || !ffaService.isInFfaRegion(to.getLocation())) {
            return;
        }
        ffaService.recordBlockChangeAt(to.getLocation(), to.getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (block == null || !ffaService.isInFfaRegion(block.getLocation())) {
            return;
        }
        ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (block == null || !ffaService.isInFfaRegion(block.getLocation())) {
            return;
        }
        ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> MaterialFlags.isGlass(block.getType()));
        recordExplosion(event.getBlock().getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> MaterialFlags.isGlass(block.getType()));
        recordExplosion(event.getLocation(), event.blockList());
    }

    private void recordExplosion(Location at, List<Block> blocks) {
        if (at == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        for (Block block : blocks) {
            if (block == null || !ffaService.isInFfaRegion(block.getLocation())) {
                continue;
            }
            ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
        }
    }

    private void recordBlocks(List<Block> blocks) {
        if (blocks == null) {
            return;
        }
        for (Block block : blocks) {
            if (block == null || !ffaService.isInFfaRegion(block.getLocation())) {
                continue;
            }
            ffaService.recordBlockChangeAt(block.getLocation(), block.getBlockData().getAsString());
        }
    }
}
