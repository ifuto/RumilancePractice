package com.rumilance.practice.combat;

import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Plugin-side workarounds for Paper PvP behaviour regressions that affect our fights. All
 * handlers only act on active combatants (duel/team match or FFA) so lobby/kit-edit movement
 * is never touched.
 *
 * <ul>
 *   <li><b>Paper #10742 / SPIGOT-7732 / MC-268147 / #13838</b> — a shield hit arms i-frames so
 *       the follow-up swing (melee, or a trident jab after a charge) is swallowed for a few
 *       ticks; and the vanilla post-stun hit deals damage but no knockback. We clear the
 *       victim's i-frames across the stun window and restore the owed knockback.</li>
 *   <li><b>Paper #13426</b> — damage survived through a raised shield should still knock the
 *       blocker back; we re-apply vanilla melee knockback when {@code finalDamage &gt; 0}.</li>
 *   <li><b>Paper #13680 / MC-29519</b> — bow draw force was not being normalised into arrow
 *       damage/crit on some Paper builds; we re-derive the vanilla arrow damage + crit flag
 *       from the shoot draw force so partial/full pulls deal consistent damage.</li>
 * </ul>
 *
 * <p>Attribute swapping (e.g. swinging a sword after briefly holding a breach/density mace so the
 * mace's armor-piercing carries onto the sword hit) is intentionally left at vanilla timing and
 * power. {@code PaperCombatTuning} sets Paper's {@code update-equipment-on-player-actions=false}
 * to restore the vanilla swap window (MC-28289 behaviour), and we apply NO damage/crit penalty
 * to swap hits: a vanilla swap hit lands with the exact attributes vanilla gives it.</p>
 */
public final class PaperCombatCompatListener implements Listener {

    private final Plugin plugin;
    private final Predicate<UUID> combatantTest;

    public PaperCombatCompatListener(Plugin plugin, Predicate<UUID> combatantTest) {
        this.plugin = plugin;
        this.combatantTest = combatantTest;
    }

    private boolean combatant(Player player) {
        return player != null && combatantTest.test(player.getUniqueId());
    }

    /**
     * MC-86252 class of bug: a raised shield can remain "blocking" server-side after a
     * teleport / world change while the client is no longer blocking (an always-effective
     * shield that also lets the player attack). Our arenas never cross dimensions, but the same
     * desync can appear on our SafeTeleport moves. Drop any raised-shield state on teleport so
     * the server can never keep blocking for a player who isn't.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportClearShield(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!combatant(player)) {
            return;
        }
        if (player.isBlocking()) {
            player.clearActiveItem();
            try {
                player.setCooldown(Material.SHIELD, 2);
            } catch (RuntimeException ignored) {
                // Best effort.
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldedHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayerAttacker(event);
        if (attacker == null || !combatant(victim) || !combatant(attacker)) {
            return;
        }
        boolean wasBlocking = victim.isBlocking();
        double net = event.getFinalDamage();
        // A shield hit from an axe/sword/mace/trident arms i-frames and stuns the shield;
        // clear the victim's i-frames so the immediate follow-up swing/jab connects.
        if (wasBlocking && ShieldBreakStunFix.holdsShieldBreaker(attacker)) {
            ShieldBreakStunFix.allowFollowUpHit(plugin, victim);
            return;
        }
        // Post-stun follow-up (the hit just after the break): vanilla deals damage but applies
        // no knockback (MC-268147). Restore the owed knockback.
        if (net > 0.0d && ShieldBreakStunFix.recentShieldBreak(victim)) {
            applyVanillaMeleeKnockback(victim, attacker);
            return;
        }
        // Paper #13426: damage survived through a block should still knock the blocker.
        if (net > 0.0d && wasBlocking) {
            applyVanillaMeleeKnockback(victim, attacker);
        }
    }

    /**
     * Re-derives vanilla arrow damage/crit from the bow draw force so partial pulls deal less
     * and full pulls fire a critical (1.5-1.75x) arrow, independent of any Paper normalisation
     * gap (#13680). Only normalises when the projectile is a player-fired arrow in a fight.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void normalizeArrowDamage(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (!combatant(shooter)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        float force = Math.max(0.0f, Math.min(1.0f, event.getForce()));
        // Vanilla: base arrow damage 2.0 scaled by draw force, fully-drawn shots are critical.
        double base = 2.0d * (2.0d * force + force * force);
        boolean full = force >= 0.9f;
        arrow.setCritical(full);
        // Preserve Power-enchant bonus already on the arrow; only correct the base scaling when
        // the arrow is weaker than the draw force implies (partial pull underdamage bug).
        double current = arrow.getDamage();
        double minExpected = base;
        if (current < minExpected) {
            arrow.setDamage(base);
        }
    }

    private Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void applyVanillaMeleeKnockback(Player victim, Player attacker) {
        // Reproduce LivingEntity#knockback exactly:
        //   dir = normalised position from attacker to victim (pushes the victim AWAY),
        //   base melee knockback strength 0.4, plus a Player#attack extra when sprinting and
        //   per Knockback-enchant level; horizontal impulse scales with knockback resistance
        //   (so netherite with no resistance attribute = full, resistance potion/attribute =
        //   reduced), grounded targets get the vanilla 0.4 upward hop.
        double dx = victim.getLocation().getX() - attacker.getLocation().getX();
        double dz = victim.getLocation().getZ() - attacker.getLocation().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double unitX;
        double unitZ;
        if (horizontal < 1.0E-4) {
            float yaw = attacker.getLocation().getYaw();
            unitX = -Math.sin(Math.toRadians(yaw));
            unitZ = Math.cos(Math.toRadians(yaw));
            double len = Math.sqrt(unitX * unitX + unitZ * unitZ);
            unitX /= len;
            unitZ /= len;
        } else {
            unitX = dx / horizontal;
            unitZ = dz / horizontal;
        }
        double resistScale = knockbackResistanceScale(victim);

        // Base melee knockback applied by LivingEntity#hurtServer (strength 0.4).
        applyKnockbackBody(victim, unitX, unitZ, 0.4d * resistScale);

        // Player#attack adds knockback for sprint hits and the Knockback enchantment, each as a
        // further knockback(strength*0.5) call; replicate those extra calls so totals match.
        int bonus = (attacker.isSprinting() ? 1 : 0) + knockbackEnchantLevel(attacker);
        if (bonus > 0) {
            applyKnockbackBody(victim, unitX, unitZ, 0.5d * bonus * resistScale);
        }
    }

    /** One LivingEntity#knockback step: halve current horizontal speed, add directional impulse, hop if grounded. */
    private void applyKnockbackBody(Player victim, double unitX, double unitZ, double strength) {
        Vector vel = victim.getVelocity();
        double impulse = strength * 0.5d;
        double newX = vel.getX() * 0.5d + unitX * impulse;
        double newZ = vel.getZ() * 0.5d + unitZ * impulse;
        double newY = vel.getY();
        // LivingEntity#knockbackStrength: grounded targets get the fixed vertical hop.
        if (victim.isOnGround()) {
            newY = 0.4d;
        }
        victim.setVelocity(new Vector(newX, newY, newZ));
    }

    private static double knockbackResistanceScale(Player victim) {
        try {
            org.bukkit.attribute.AttributeInstance attr =
                    victim.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
            double resist = attr == null ? 0.0d : attr.getValue();
            // Vanilla: knockback strength is scaled by (1 - clamp(resistance)); netherite has no
            // knockback-resistance attribute, so it is unaffected unless a potion/modifier adds it.
            double scale = 1.0d - Math.max(0.0d, Math.min(1.0d, resist));
            return Math.max(0.0d, scale);
        } catch (RuntimeException e) {
            return 1.0d;
        }
    }

    private static int knockbackEnchantLevel(Player attacker) {
        try {
            org.bukkit.enchantments.Enchantment kb =
                    org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("knockback"));
            if (kb == null) {
                return 0;
            }
            return Math.max(
                    attacker.getInventory().getItemInMainHand().getEnchantmentLevel(kb),
                    attacker.getInventory().getItemInOffHand().getEnchantmentLevel(kb));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
