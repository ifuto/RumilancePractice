package com.rumilance.practice.combat;

import com.rumilance.practice.model.KitDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <strong>Bed Explosion</strong> kit rule ({@code /kit} → item rules → "Bed Explosion").
 *
 * <p>With the rule on, a bed placed during the fight detonates when it is right-clicked —
 * exactly the "intentional game design" blast a bed produces in the Nether / the End, but in the
 * Overworld too (that is the point: End-style bed bombing on an End-style practice map).</p>
 *
 * <p>The blast is a real explosion with the vanilla bed power of 5 (71 raw damage point blank,
 * before armor), so it damages everyone nearby — including the player who clicked it. Vanilla
 * never damages the source of an explosion, so the clicker's own share is restored by
 * {@link ExplosionSelfDamageListener} (registered before this listener's blast is created), which
 * gives bed bombs the same self-damage / self-knockback trade-off as crystals and anchors.</p>
 *
 * <p>Terrain: the explosion is created with {@code breakBlocks = false} and {@code setFire =
 * false}, matching how practice crystals and creepers already behave — the map survives, the
 * fight does not.</p>
 */
public final class BedExplosionListener implements Listener {

    /** Vanilla bed blast power (minecraft.wiki: beds in the Nether/End = 5). */
    public static final float BED_POWER = ExplosionPhysics.BED_POWER;

    /**
     * One context the rule can fire in.
     *
     * @param inContext true while the player is fighting inside this context
     * @param kit       resolves the active kit (the rule is read from {@code kit.bedExplosion()})
     */
    public record Context(Predicate<UUID> inContext, Function<UUID, KitDefinition> kit) {
    }

    private final java.util.List<Context> contexts = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Optional hook so the blast can restore the clicker's skipped self-damage. */
    private volatile ExplosionSelfDamageListener selfDamage;

    public BedExplosionListener addContext(Context context) {
        if (context != null) {
            contexts.add(context);
        }
        return this;
    }

    public void setSelfDamage(ExplosionSelfDamageListener selfDamage) {
        this.selfDamage = selfDamage;
    }

    /** Kit rule lookup used by {@link com.rumilance.practice.util.KitBlockRules} callers/tests. */
    public static boolean enabledFor(KitDefinition kit) {
        return kit != null && kit.bedExplosion();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // off-hand pass would detonate twice
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Bed bed)) {
            return;
        }
        World world = block.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER
                || world.getEnvironment() == World.Environment.THE_END) {
            return; // vanilla already detonates beds there - don't stack two blasts
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        KitDefinition kit = null;
        for (Context context : contexts) {
            if (context.inContext() != null && context.inContext().test(id)) {
                kit = context.kit() == null ? null : context.kit().apply(id);
                break;
            }
        }
        if (!enabledFor(kit)) {
            return; // plain vanilla bed: sleep / set spawn
        }
        // Detonate instead of sleeping. Deny the interaction so the client drops its own
        // prediction (block state + held item) and resyncs from the server.
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        detonate(player, block, bed);
    }

    /** Removes both bed halves and runs a vanilla-strength bed explosion at the bed's centre. */
    private void detonate(Player player, Block block, Bed bed) {
        World world = block.getWorld();
        Block other = otherHalf(block, bed);
        Location center = block.getLocation().add(0.5d, 0.5d, 0.5d);
        if (other != null) {
            // Centre of the whole bed (both halves), like the vanilla block explosion.
            center = center.add(other.getLocation().add(0.5d, 0.5d, 0.5d)).multiply(0.5d);
            other.setType(Material.AIR, false);
        }
        block.setType(Material.AIR, false);
        if (selfDamage != null) {
            // Vanilla skips the explosion's source entity: remember the clicker so their own
            // blast damage + knockback is restored a tick later (crystal-style bed bombing).
            selfDamage.rememberPluginBlast(center, BED_POWER, player.getUniqueId());
        }
        world.createExplosion(center, BED_POWER, false, false, player);
    }

    /** Vanilla {@code BedBlock#getConnectedDirection}: head → behind, foot → ahead. */
    private static Block otherHalf(Block block, Bed bed) {
        BlockFace facing = bed.getFacing();
        BlockFace toward = bed.getPart() == Bed.Part.HEAD ? facing.getOppositeFace() : facing;
        Block other = block.getRelative(toward);
        return other.getBlockData() instanceof Bed ? other : null;
    }
}
