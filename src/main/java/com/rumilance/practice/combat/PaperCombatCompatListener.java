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
 * <p>Item/attribute swapping is left entirely alone: this plugin does NOT touch Paper's
 * {@code unsupported-settings.update-equipment-on-player-actions}, does not reshape swap hits and
 * applies no damage/crit penalty to them. A swap hit lands with exactly the attributes the server
 * gives it (the old "vanilla item swap" tuning was removed because it made swap damage feel
 * wrong); server owners who want a different swap timing set that Paper key themselves.</p>
 */
public final class PaperCombatCompatListener implements Listener {

    private final Plugin plugin;
    private final Predicate<UUID> combatantTest;
    /**
     * Operator-tuned knockback coefficients ({@link KnockbackTuning}); the vanilla reproduction
     * below is kept exact, then the finished vector is scaled with the same factor the
     * {@link KnockbackTuningListener} applies to Paper's {@code EntityKnockbackEvent}, so both
     * paths behave identically. Null / fully-neutral → byte-identical vanilla behaviour.
     */
    private com.rumilance.practice.combat.KnockbackTuning knockbackTuning;
    /** Victim kit resolver shared with {@link KnockbackTuningListener} (duel kit / FFA kit). */
    private java.util.function.Function<java.util.UUID, String> kitResolverFn;
    /**
     * Live per-match KB profile (Duel Request の KB 選択), shared with {@link KnockbackTuningListener}
     * so the manual Paper-#13426 re-application scales with the SAME factor the event path uses —
     * without it, shield-surviving hits ignored the match's KB selection entirely.
     */
    private volatile java.util.function.Function<java.util.UUID, double[]> liveProfileResolver;

    public PaperCombatCompatListener(Plugin plugin, Predicate<UUID> combatantTest) {
        this.plugin = plugin;
        this.combatantTest = combatantTest;
    }

    public void setKnockbackTuning(com.rumilance.practice.combat.KnockbackTuning tuning,
                                   java.util.function.Function<java.util.UUID, String> kitResolver) {
        this.knockbackTuning = tuning;
        this.kitResolverFn = kitResolver;
    }

    public void setLiveProfileResolver(
            java.util.function.Function<java.util.UUID, double[]> liveProfileResolver) {
        this.liveProfileResolver = liveProfileResolver;
    }

    /**
     * kb-probe 0.7.0 staged profile for the shield re-application path — non-null rebuilds
     * the melee knockback with the full fitted model, same as {@code KnockbackTuningListener}.
     */
    private volatile java.util.function.Function<java.util.UUID,
            com.rumilance.practice.kb.StagedKnockback> stagedProfileResolver;

    public void setStagedProfileResolver(java.util.function.Function<java.util.UUID,
            com.rumilance.practice.kb.StagedKnockback> stagedProfileResolver) {
        this.stagedProfileResolver = stagedProfileResolver;
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
        // Never touch an ender-pearl / chorus-fruit move: those are in-fight mobility teleports
        // and lowering the shield or arming a cooldown there would add bogus input lag. The
        // MC-86252 desync is tied to dimension/plugin teleports, not to item teleports.
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
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
     * Restores the vanilla critical-arrow flag for a full bow draw. Paper's shot damage is left
     * entirely to vanilla: the final arrow damage is {@code velocity * arrowDamage} (velocity
     * already encodes the draw force, ~3.0 at full pull) and Power enchants add their bonus into
     * {@code arrowDamage}. We must NOT write the draw-force-scaled value into {@code setDamage}:
     * that field is the per-hit base DAMAGE, so setting it to 6.0 at full draw made the on-hit
     * damage {@code 3.0 * 6.0 = ~18} instead of the vanilla {@code 3.0 * 2.0 = 6} — the bug that
     * made bows/crossbows hit far too hard. Only the critical flag is aligned (bows can fire a
     * crit on a full pull; crossbow shots are never critical).
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
        // Crossbows (and anything not drawing a bow) never produce critical arrows in vanilla.
        if (event.getBow() != null && event.getBow().getType() == Material.CROSSBOW) {
            arrow.setCritical(false);
            return;
        }
        float force = Math.max(0.0f, Math.min(1.0f, event.getForce()));
        boolean full = force >= 0.9f;
        if (full != arrow.isCritical()) {
            arrow.setCritical(full);
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
        // Reproduce vanilla 1.21.1 knockback exactly (verified against LivingEntity#takeKnockback
        //   / #damage + PlayerEntity#attack decompiled sources):
        //   dir = normalised attacker→victim (pushes the victim AWAY), base impulse 0.4 from
        //   LivingEntity#damage, plus a Player#attack impulse k·0.5 where k = attack_knockback
        //   attribute (Knockback enchant = 1/level) + 1 for a charged sprint hit; every impulse
        //   is scaled by (1 − knockback_resistance) and grounded vertical is the vanilla
        //   min(0.4, vy/2 + strength), never a fixed 0.4.
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

        // kb-probe 0.7.0 staged profile: rebuild base + sprint/enchant stage with the fitted
        // model (directions are the same attacker→victim unit vector, stages mirror vanilla's
        // two knockback() calls) instead of scaling the vanilla formula.
        var staged = stagedProfileResolver == null
                ? null : stagedProfileResolver.apply(victim.getUniqueId());
        if (staged != null) {
            Vector v0 = victim.getVelocity();
            boolean grounded = victim.isOnGround();
            double[] base = staged.stageBase(v0.getX(), v0.getY(), v0.getZ(),
                    unitX, unitZ, resistScale, grounded);
            victim.setVelocity(new Vector(base[0], base[1], base[2]));
            int level = knockbackEnchantLevel(attacker);
            if (attacker.isSprinting() || level > 0) {
                double extraHBase = (attacker.isSprinting() ? staged.extraHorizontal() : 0.0d)
                        + level * staged.knockbackEnchant();
                double extraVBase = (attacker.isSprinting() ? staged.extraVertical() : 0.0d)
                        + level * staged.knockbackEnchant();
                Vector v1 = victim.getVelocity();
                double[] extra = staged.stageExtra(v1.getX(), v1.getY(), v1.getZ(),
                        unitX, unitZ, resistScale, grounded, extraHBase, extraVBase);
                victim.setVelocity(new Vector(extra[0], extra[1], extra[2]));
            }
            return;
        }

        // Base melee knockback applied by LivingEntity#hurtServer (strength 0.4).
        applyKnockbackBody(victim, unitX, unitZ, 0.4d * resistScale);

        // Player#attack adds knockback for sprint hits and the Knockback enchantment, each as a
        // further knockback(strength*0.5) call; replicate those extra calls so totals match.
        int bonus = (attacker.isSprinting() ? 1 : 0) + knockbackEnchantLevel(attacker);
        if (bonus > 0) {
            applyKnockbackBody(victim, unitX, unitZ, 0.5d * bonus * resistScale);
        }
    }

    /**
     * One LivingEntity#takeKnockback step (verified against vanilla 1.21.1 sources):
     * horizontal: newV = currentV/2 ± strength·dir (impulse magnitude == strength, CALLERS
     * already folded the (1 − knockback_resistance) scale into `strength`);
     * vertical: grounded → min(0.4, currentVy/2 + strength), airborne → untouched.
     */
    private void applyKnockbackBody(Player victim, double unitX, double unitZ, double strength) {
        Vector vel = victim.getVelocity();
        double newX = vel.getX() * 0.5d + unitX * strength;
        double newZ = vel.getZ() * 0.5d + unitZ * strength;
        double newY = vel.getY();
        if (victim.isOnGround()) {
            newY = Math.min(0.4d, vel.getY() * 0.5d + strength);
        }
        // バニラ再現の完成ベクトルに、運用者係数だけを乗せる（計算式には触れない）。
        // compatのノックバックは近接攻撃由来なので Cause は ENTITY_ATTACK として解釈する。
        // 優先度は KnockbackTuningListener と完全に揃える: ライブ試合プロファイル最優先、
        // その後 kit > cause > global。これがないと KB プロファイル試合の盾耐えヒットだけ
        // 別の KB になっていた。
        java.util.function.Function<java.util.UUID, double[]> resolverFn = liveProfileResolver;
        double[] live = resolverFn == null ? null : resolverFn.apply(victim.getUniqueId());
        if (live != null) {
            if (live[0] != 1.0d || live[1] != 1.0d) {
                newX *= live[0];
                newY *= live[1];
                newZ *= live[0];
            }
        } else {
            var tuning = knockbackTuning;
            if (tuning != null && !tuning.isNeutral()) {
                String kit = kitResolverFn != null ? kitResolverFn.apply(victim.getUniqueId()) : null;
                if (!tuning.isNeutralFor("ENTITY_ATTACK", kit)) {
                    double[] scaled = tuning.scale("ENTITY_ATTACK", kit, newX, newY, newZ);
                    newX = scaled[0];
                    newY = scaled[1];
                    newZ = scaled[2];
                }
            }
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
