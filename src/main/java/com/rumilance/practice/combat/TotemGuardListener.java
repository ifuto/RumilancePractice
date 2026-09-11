package com.rumilance.practice.combat;

import com.rumilance.practice.model.KitDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Totem handling in the death-catch world is <strong>vanilla-first</strong>: lethal damage
 * reaches {@code LivingEntity#die}, vanilla consumes the totem, fires
 * {@link org.bukkit.event.entity.EntityResurrectEvent}, and restores the player with the exact
 * vanilla effects and invulnerability window. This listener only polices the edges:
 *
 * <ol>
 *   <li>{@code EntityResurrectEvent} (MONITOR): a successful vanilla pop marks the resurrect
 *       grace window so no lethal-frame bookkeeping can misread it.</li>
 *   <li>{@code EntityResurrectEvent} (HIGHEST, kit rule): a kit that forbids totems must veto
 *       the vanilla resurrect — vanilla cannot see kit settings — so the death proceeds to the
 *       death catch and is scored even while the player holds a totem.</li>
 *   <li>{@code PlayerDeathEvent} failsafe: a death that still arrives with a totem in hand
 *       (resurrect denied by something else, or a path that never produced a damage event we
 *       could observe) is cancelled, one totem consumed, and the player revived — a totem
 *       holder can never lose.</li>
 * </ol>
 */
public final class TotemGuardListener implements Listener {

    /**
     * Per-context wiring.
     *
     * @param kit      resolves the active kit for a player id ({@code null} = no kit rules,
     *                 totems allowed), or {@code null} when the context does not apply
     * @param inContext true while the player is inside this context (match / FFA / practice)
     * @param safeLocation where the player should stand after a rescued death
     * @param recover    optional extra recovery hook (e.g. FFA respawn: kit + stats + sight)
     */
    public record Context(
            Function<UUID, KitDefinition> kit,
            java.util.function.Predicate<UUID> inContext,
            Function<UUID, Location> safeLocation,
            Consumer<Player> recover
    ) {
        public static Context of(Function<UUID, KitDefinition> kit,
                                 java.util.function.Predicate<UUID> inContext,
                                 Function<UUID, Location> safeLocation) {
            return new Context(kit, inContext, safeLocation, null);
        }
    }

    private final Plugin plugin;
    private final java.util.List<Context> contexts = new java.util.concurrent.CopyOnWriteArrayList<>();

    public TotemGuardListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public TotemGuardListener addContext(Context context) {
        if (context != null) {
            contexts.add(context);
        }
        return this;
    }

    /** Kit rules for a player across every registered context; {@code null} when unmanaged. */
    public KitDefinition kitFor(UUID playerId) {
        for (Context context : contexts) {
            if (context.inContext() == null || !context.inContext().test(playerId)) {
                continue;
            }
            if (context.kit() == null) {
                return null;
            }
            return context.kit().apply(playerId);
        }
        return null;
    }

    private Context contextOf(UUID playerId) {
        for (Context context : contexts) {
            if (context.inContext() != null && context.inContext().test(playerId)) {
                return context;
            }
        }
        return null;
    }

    /** True when this player is inside any guarded practice context. */
    public boolean guards(UUID playerId) {
        return contextOf(playerId) != null;
    }

    /** Vanilla popped one for us (another plugin / a path we did not cover): stay consistent. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            PracticeDeath.markResurrected(player);
        }
    }

    /**
     * Kit-rule enforcement for the death-catch world: when damage now flows all the way to
     * {@code LivingEntity#die}, vanilla will resurrect anyone holding a totem regardless of
     * kit settings (the kit rule is invisible to vanilla). A kit that forbids totems must
     * therefore veto the resurrect, letting the death proceed to the death catch.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrectKitRule(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        Context context = contextOf(id);
        if (context == null || context.kit() == null) {
            return;
        }
        KitDefinition kit = context.kit().apply(id);
        if (kit != null && !kit.totem()) {
            event.setCancelled(true);
            Bukkit.getLogger().warning("[N Arena][TotemGuard] kit forbids totems; vanilla resurrect"
                    + " denied, death proceeds to the death catch: " + player.getName());
        }
    }

    // ------------------------------------------------------------------- death failsafe

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUniqueId();
        Context context = contextOf(id);
        if (context == null) {
            return;
        }
        KitDefinition kit = context.kit() == null ? null : context.kit().apply(id);
        if (!PracticeDeath.hasTotemInHand(player) || (kit != null && !kit.totem())) {
            return;
        }
        event.setCancelled(true);
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setShouldDropExperience(false);
        event.deathMessage(null);
        Bukkit.getLogger().warning("[N Arena][TotemGuard] cancelled a death with a totem in hand: "
                + player.getName() + " cause="
                + (player.getLastDamageCause() == null ? "?" : player.getLastDamageCause().getCause()));
        PracticeDeath.consumeTotemFromHand(player);
        revive(player, context);
    }

    /**
     * Brings the player back after a cancelled death. Paper keeps a cancelled death downed
     * until the health is restored, so: top the health up first and, if the player is still
     * dead on the next tick, force the respawn and put them back where they were fighting.
     */
    private void revive(Player player, Context context) {
        Location dest = null;
        if (context.safeLocation() != null) {
            dest = context.safeLocation().apply(player.getUniqueId());
        }
        if (dest == null || dest.getWorld() == null) {
            dest = player.getLocation();
        }
        final Location home = dest;
        PracticeDeath.applyTotemEffects(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (player.isDead() || player.getHealth() <= 0.0d) {
                try {
                    player.spigot().respawn();
                } catch (IllegalStateException | IllegalArgumentException e) {
                    // Not flagged dead server-side (the cancelled death was enough): fall
                    // through, the health top-up below is all that is missing.
                    plugin.getLogger().fine("[N Arena][TotemGuard] respawn not needed/possible: " + e.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (player.getHealth() <= 0.0d) {
                    player.setHealth(1.0d);
                }
                player.setFallDistance(0f);
                player.setFireTicks(0);
                player.setFreezeTicks(0);
                if (home.getWorld() != null) {
                    com.rumilance.practice.util.SafeTeleport.teleport(player, home);
                }
                PracticeDeath.applyTotemEffects(player);
                if (context.recover() != null) {
                    try {
                        context.recover().accept(player);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "[N Arena][TotemGuard] recovery hook failed", e);
                    }
                }
            });
        });
    }

    /** Convenience for contexts without a kit rule set (totems always allowed). */
    public static Function<UUID, KitDefinition> alwaysTotems() {
        return id -> null;
    }
}
